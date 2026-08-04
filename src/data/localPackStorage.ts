import {Unzip, UnzipInflate} from 'fflate';
import type {DatasetDescriptor, DatasetSource} from './datasetCatalog.ts';
import {
  MAX_EXPORT_ARCHIVE_ENTRIES,
  requireSafeArchivePath,
  type LocalPackManifestSummary,
} from './localPackArchive.ts';

const LOCAL_PACK_CACHE = 'minecraft-recipe-tree-local-packs-v1';
const LOCAL_PACK_CATALOG_PATH = '/__local-packs/catalog.json';
const LOCAL_PACK_ROUTE_PREFIX = '/__local-packs/';
const ARCHIVE_READ_CHUNK_BYTES = 1024 * 1024;
const MAX_LOCAL_PACKS = 24;
const MAX_LOCAL_FILE_BYTES = 128 * 1024 * 1024;
const LOCAL_CATALOG_FORMAT = 1;

interface LocalPackRecord extends DatasetDescriptor {
  storedAt: number;
}

interface LocalPackCatalog {
  format: typeof LOCAL_CATALOG_FORMAT;
  packs: LocalPackRecord[];
}

export interface InstalledLocalPack {
  descriptor: DatasetDescriptor;
  viewerHref: string;
}

export type LocalPackInstallProgress =
  | {phase: 'reading'; fraction: number}
  | {phase: 'saving'; fraction: number; completedFiles: number; totalFiles: number}
  | {phase: 'finalizing'};

export function isLocalPackDescriptor(descriptor: DatasetDescriptor): boolean {
  return (
    /^local-[a-f0-9]{16}$/u.test(descriptor.slug) &&
    descriptor.previewAssetSetId === descriptor.publicationId
  );
}

function browserOrigin(): string {
  if (typeof window === 'undefined') {
    throw new Error('Local packs are only available in a web browser.');
  }
  return window.location.origin;
}

function cacheApi(): CacheStorage {
  if (typeof caches === 'undefined') {
    throw new Error('This browser cannot keep the pack for the viewer.');
  }
  return caches;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function isLocalPackRecord(value: unknown): value is LocalPackRecord {
  return (
    isRecord(value) &&
    typeof value.slug === 'string' &&
    /^local-[a-f0-9]{16}$/u.test(value.slug) &&
    typeof value.displayName === 'string' &&
    value.displayName.length > 0 &&
    typeof value.minecraftVersion === 'string' &&
    value.minecraftVersion.length > 0 &&
    typeof value.packVersion === 'string' &&
    value.packVersion.length > 0 &&
    typeof value.publicationId === 'string' &&
    /^[a-f0-9]{64}$/u.test(value.publicationId) &&
    value.previewAssetSetId === value.publicationId &&
    value.isDefault === false &&
    typeof value.storedAt === 'number' &&
    Number.isSafeInteger(value.storedAt) &&
    value.storedAt > 0
  );
}

function requireLocalPackCatalog(value: unknown): LocalPackCatalog {
  if (
    !isRecord(value) ||
    value.format !== LOCAL_CATALOG_FORMAT ||
    !Array.isArray(value.packs) ||
    value.packs.length > MAX_LOCAL_PACKS ||
    !value.packs.every(isLocalPackRecord)
  ) {
    throw new Error('The saved pack list is unreadable.');
  }
  return {
    format: LOCAL_CATALOG_FORMAT,
    packs: value.packs,
  };
}

function emptyCatalog(): LocalPackCatalog {
  return {format: LOCAL_CATALOG_FORMAT, packs: []};
}

function catalogRequest(): Request {
  return new Request(`${browserOrigin()}${LOCAL_PACK_CATALOG_PATH}`);
}

async function readCatalog(cache: Cache): Promise<LocalPackCatalog> {
  const response = await cache.match(catalogRequest(), {ignoreSearch: true});
  if (!response) return emptyCatalog();
  try {
    return requireLocalPackCatalog(await response.json());
  } catch (error) {
    console.error('The local modpack list could not be read.', error);
    return emptyCatalog();
  }
}

async function writeCatalog(cache: Cache, catalog: LocalPackCatalog): Promise<void> {
  await cache.put(
    catalogRequest(),
    new Response(JSON.stringify(catalog), {
      headers: {
        'Cache-Control': 'no-store',
        'Content-Type': 'application/json; charset=utf-8',
      },
    }),
  );
}

function localPackPath(publicationId: string, relativePath: string): string {
  return `${LOCAL_PACK_ROUTE_PREFIX}${publicationId}/exports/${relativePath}`;
}

function localPackRequest(publicationId: string, relativePath: string): Request {
  return new Request(`${browserOrigin()}${localPackPath(publicationId, relativePath)}`);
}

function contentType(path: string): string {
  const normalized = path.toLowerCase();
  if (normalized.endsWith('.json')) return 'application/json; charset=utf-8';
  if (normalized.endsWith('.png')) return 'image/png';
  if (normalized.endsWith('.webp')) return 'image/webp';
  if (normalized.endsWith('.jpg') || normalized.endsWith('.jpeg')) return 'image/jpeg';
  if (normalized.endsWith('.gif')) return 'image/gif';
  return 'application/octet-stream';
}

function rootPrefix(manifestPath: string): string {
  const separator = manifestPath.indexOf('/');
  return separator === -1 ? '' : manifestPath.slice(0, separator + 1);
}

function relativeExportPath(path: string, prefix: string): string | null {
  if (prefix === '') return path;
  return path.startsWith(prefix) ? path.slice(prefix.length) : null;
}

function hex(bytes: Uint8Array): string {
  return [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

async function publicationIdForManifest(manifestBytes: Uint8Array): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('This browser cannot identify the pack safely.');
  }
  const digestInput = new Uint8Array(manifestBytes.byteLength);
  digestInput.set(manifestBytes);
  return hex(new Uint8Array(await globalThis.crypto.subtle.digest('SHA-256', digestInput.buffer)));
}

async function deletePublication(cache: Cache, publicationId: string): Promise<void> {
  const prefix = `${browserOrigin()}${LOCAL_PACK_ROUTE_PREFIX}${publicationId}/`;
  const requests = await cache.keys();
  await Promise.all(
    requests
      .filter(request => request.url.startsWith(prefix))
      .map(request => cache.delete(request)),
  );
}

export async function registerLocalPackServiceWorker(): Promise<void> {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
    throw new Error('This browser cannot open saved packs in the viewer.');
  }
  let timeout = 0;
  let onControllerChange: (() => void) | null = null;
  try {
    await Promise.race([
      (async () => {
        await navigator.serviceWorker.register('/local-pack-sw.js', {scope: '/'});
        await navigator.serviceWorker.ready;
        if (navigator.serviceWorker.controller) return;
        await new Promise<void>(resolve => {
          onControllerChange = () => {
            resolve();
          };
          navigator.serviceWorker.addEventListener('controllerchange', onControllerChange);
        });
      })(),
      new Promise<never>((_resolve, reject) => {
        timeout = window.setTimeout(
          () => reject(new Error('The viewer could not finish preparing local pack support.')),
          5_000,
        );
      }),
    ]);
  } finally {
    window.clearTimeout(timeout);
    if (onControllerChange) {
      navigator.serviceWorker.removeEventListener('controllerchange', onControllerChange);
    }
  }
}

export async function listLocalPackDescriptors(): Promise<readonly DatasetDescriptor[]> {
  if (typeof window === 'undefined' || typeof caches === 'undefined') return [];
  const cache = await cacheApi().open(LOCAL_PACK_CACHE);
  const catalog = await readCatalog(cache);
  return [...catalog.packs]
    .sort((left, right) => right.storedAt - left.storedAt)
    .map(({storedAt: _storedAt, ...descriptor}) => descriptor);
}

export function localDatasetSource(descriptor: DatasetDescriptor): DatasetSource {
  return {
    descriptor,
    base: localPackPath(descriptor.publicationId, '').replace(/\/$/u, ''),
    previewBase: '',
  };
}

export async function installLocalPackArchive(
  file: File,
  manifestPath: string,
  manifestBytes: Uint8Array,
  manifest: unknown,
  summary: LocalPackManifestSummary,
  onProgress: (progress: LocalPackInstallProgress) => void,
): Promise<InstalledLocalPack> {
  await registerLocalPackServiceWorker();
  const publicationId = await publicationIdForManifest(manifestBytes);
  const descriptor: DatasetDescriptor = {
    slug: `local-${publicationId.slice(0, 16)}`,
    displayName: summary.packName,
    minecraftVersion: summary.minecraftVersion,
    packVersion: summary.packVersion ?? 'Unknown',
    publicationId,
    previewAssetSetId: publicationId,
    isDefault: false,
  };
  const prefix = rootPrefix(manifestPath);
  const cache = await cacheApi().open(LOCAL_PACK_CACHE);
  const storedPaths = new Set<string>();
  let entryCount = 0;
  let archiveError: Error | null = null;
  let writeError: Error | null = null;
  let writeQueue = Promise.resolve();
  let lastReportedPercent = 0;
  let queuedWrites = 0;
  let completedWrites = 0;
  let archiveReadComplete = false;
  let lastReportedSavePercent = -1;

  const reportSaveProgress = () => {
    if (!archiveReadComplete) return;
    const fraction = queuedWrites === 0 ? 1 : completedWrites / queuedWrites;
    const percent = Math.floor(fraction * 100);
    if (percent === lastReportedSavePercent && completedWrites !== queuedWrites) return;
    lastReportedSavePercent = percent;
    onProgress({
      phase: 'saving',
      fraction,
      completedFiles: completedWrites,
      totalFiles: queuedWrites,
    });
  };

  const unzip = new Unzip(entry => {
    entryCount += 1;
    if (entryCount > MAX_EXPORT_ARCHIVE_ENTRIES) {
      archiveError = new Error('This ZIP contains too many files to open safely.');
      return;
    }

    let safePath: string;
    try {
      safePath = requireSafeArchivePath(entry.name);
    } catch {
      archiveError = new Error('This ZIP contains a file path that cannot be opened safely.');
      return;
    }
    if (entry.name.endsWith('/')) return;
    const relativePath = relativeExportPath(safePath, prefix);
    if (relativePath === null || relativePath.length === 0) return;
    if (storedPaths.has(relativePath)) {
      archiveError = new Error(`The ZIP contains the same file twice: ${relativePath}`);
      return;
    }
    storedPaths.add(relativePath);

    if (entry.originalSize !== undefined && entry.originalSize > MAX_LOCAL_FILE_BYTES) {
      archiveError = new Error(`The file ${relativePath} is too large to open in the viewer.`);
      return;
    }

    let bytes = 0;
    let chunks: ArrayBuffer[] = [];
    entry.ondata = (error, data, final) => {
      if (error) {
        archiveError = new Error(`The ZIP could not read ${relativePath}.`);
        return;
      }
      bytes += data.byteLength;
      if (bytes > MAX_LOCAL_FILE_BYTES) {
        archiveError = new Error(`The file ${relativePath} is too large to open in the viewer.`);
        chunks = [];
        return;
      }
      const copied = new Uint8Array(data.byteLength);
      copied.set(data);
      chunks.push(copied.buffer);
      if (!final) return;
      if (relativePath === 'manifest.json') {
        chunks = [];
        return;
      }
      const body = new Blob(chunks, {type: contentType(relativePath)});
      chunks = [];
      queuedWrites += 1;
      writeQueue = writeQueue
        .then(async () => {
          await cache.put(
            localPackRequest(publicationId, relativePath),
            new Response(body, {
              headers: {
                'Cache-Control': 'no-store',
                'Content-Type': contentType(relativePath),
              },
            }),
          );
          completedWrites += 1;
          reportSaveProgress();
        })
        .catch(error => {
          writeError = error instanceof Error ? error : new Error(String(error));
        });
    };
    try {
      entry.start();
    } catch {
      archiveError = new Error(`The ZIP could not read ${relativePath}.`);
    }
  });
  unzip.register(UnzipInflate);

  try {
    for (let offset = 0; offset < file.size; offset += ARCHIVE_READ_CHUNK_BYTES) {
      const end = Math.min(offset + ARCHIVE_READ_CHUNK_BYTES, file.size);
      const chunk = new Uint8Array(await file.slice(offset, end).arrayBuffer());
      try {
        unzip.push(chunk, end === file.size);
      } catch {
        throw new Error('The ZIP could not be opened.');
      }
      if (archiveError !== null) throw archiveError;
      const percent = Math.floor((end / file.size) * 100);
      if (percent > lastReportedPercent || end === file.size) {
        lastReportedPercent = percent;
        onProgress({phase: 'reading', fraction: end / file.size});
      }
    }
    archiveReadComplete = true;
    reportSaveProgress();
    await writeQueue;
    if (writeError !== null) {
      console.error('A local pack file could not be saved.', writeError);
      throw new Error('There is not enough browser storage to keep this pack.');
    }

    onProgress({phase: 'finalizing'});

    for (const requiredPath of ['manifest.json', 'items.json', 'categories.json', 'index.json']) {
      if (!storedPaths.has(requiredPath)) {
        throw new Error(`The ZIP is missing ${requiredPath}. Run the exporter again.`);
      }
    }

    if (!isRecord(manifest)) {
      throw new Error('The exporter information in this ZIP is unreadable.');
    }
    const localManifest = {...manifest, publicationId};
    await cache.put(
      localPackRequest(publicationId, 'manifest.json'),
      new Response(JSON.stringify(localManifest), {
        headers: {
          'Cache-Control': 'no-store',
          'Content-Type': 'application/json; charset=utf-8',
        },
      }),
    );

    const current = await readCatalog(cache);
    const superseded = current.packs.filter(
      pack =>
        pack.publicationId !== publicationId &&
        pack.displayName === descriptor.displayName &&
        pack.minecraftVersion === descriptor.minecraftVersion,
    );
    const nextRecord: LocalPackRecord = {...descriptor, storedAt: Date.now()};
    const retained = current.packs.filter(
      pack =>
        pack.publicationId !== publicationId &&
        !superseded.some(oldPack => oldPack.publicationId === pack.publicationId),
    );
    const nextPacks = [nextRecord, ...retained]
      .sort((left, right) => right.storedAt - left.storedAt)
      .slice(0, MAX_LOCAL_PACKS);
    await writeCatalog(cache, {format: LOCAL_CATALOG_FORMAT, packs: nextPacks});

    const retainedIds = new Set(nextPacks.map(pack => pack.publicationId));
    await Promise.all(
      [...superseded, ...retained.slice(MAX_LOCAL_PACKS - 1)]
        .filter(pack => !retainedIds.has(pack.publicationId))
        .map(pack => deletePublication(cache, pack.publicationId)),
    );

    return {
      descriptor,
      viewerHref: `/?pack=${encodeURIComponent(descriptor.slug)}`,
    };
  } catch (error) {
    await deletePublication(cache, publicationId).catch(cleanupError => {
      console.error('An incomplete local pack could not be removed.', cleanupError);
    });
    throw error;
  }
}
