import * as Crypto from 'expo-crypto';
import {Directory, File as NativeFile, Paths} from 'expo-file-system';
import {Unzip, UnzipInflate} from 'fflate';
import type {DatasetDescriptor, DatasetSource} from './datasetCatalog.ts';
import {
  MAX_EXPORT_ARCHIVE_ENTRIES,
  isIgnoredArchiveMetadataPath,
  localPackVersionLabel,
  requireSafeArchivePath,
  type LocalPackManifestSummary,
} from './localPackArchive.ts';
import type {LocalPackArchiveFile} from './localPackInspection.ts';
import type {LocalPackDelta, LocalPackDeltaFile} from './localPackDelta.ts';

const ARCHIVE_READ_CHUNK_BYTES = 1024 * 1024;
const MAX_LOCAL_FILE_BYTES = 128 * 1024 * 1024;
const MAX_LOCAL_PACKS = 24;
const LOCAL_CATALOG_FORMAT = 1;
const LOCAL_INVENTORY_FORMAT = 1;
const ROOT_DIRECTORY_NAME = 'minecraft-recipe-tree';
const PACKS_DIRECTORY_NAME = 'local-packs';
const CATALOG_FILE_NAME = 'catalog.json';
const INVENTORY_FILE_NAME = 'inventory.json';

export const LOCAL_PACK_CATALOG_CHANGED_EVENT = 'mrt:local-pack-catalog-changed';

interface LocalPackRecord extends DatasetDescriptor {
  storedAt: number;
}

interface LocalPackCatalog {
  format: typeof LOCAL_CATALOG_FORMAT;
  packs: LocalPackRecord[];
}

interface LocalPackInventory {
  format: typeof LOCAL_INVENTORY_FORMAT;
  paths: string[];
}

export interface InstalledLocalPack {
  descriptor: DatasetDescriptor;
  viewerHref: string;
}

export type LocalPackInstallProgress =
  | {
      phase: 'reading';
      fraction: number;
      completedBytes: number;
      totalBytes: number;
      discoveredFiles: number;
    }
  | {phase: 'saving'; fraction: number; completedFiles: number; totalFiles: number}
  | {phase: 'finalizing'};

export function isLocalPackDescriptor(descriptor: DatasetDescriptor): boolean {
  return (
    /^local-[a-f0-9]{16}$/u.test(descriptor.slug) &&
    descriptor.previewAssetSetId === descriptor.publicationId
  );
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
  return {format: LOCAL_CATALOG_FORMAT, packs: value.packs};
}

function packsDirectory(): Directory {
  return new Directory(Paths.document, ROOT_DIRECTORY_NAME, PACKS_DIRECTORY_NAME);
}

function packDirectory(publicationId: string): Directory {
  return new Directory(packsDirectory(), publicationId);
}

function exportsDirectory(publicationId: string): Directory {
  return new Directory(packDirectory(publicationId), 'exports');
}

function catalogFile(): NativeFile {
  return new NativeFile(packsDirectory(), CATALOG_FILE_NAME);
}

function inventoryFile(publicationId: string): NativeFile {
  return new NativeFile(packDirectory(publicationId), INVENTORY_FILE_NAME);
}

function emptyCatalog(): LocalPackCatalog {
  return {format: LOCAL_CATALOG_FORMAT, packs: []};
}

async function readCatalog(): Promise<LocalPackCatalog> {
  const file = catalogFile();
  if (!file.exists) return emptyCatalog();
  try {
    return requireLocalPackCatalog(JSON.parse(await file.text()));
  } catch (error) {
    console.error('The local modpack list could not be read.', error);
    return emptyCatalog();
  }
}

function writeCatalog(catalog: LocalPackCatalog): void {
  packsDirectory().create({idempotent: true, intermediates: true});
  const file = catalogFile();
  file.create({intermediates: true, overwrite: true});
  file.write(JSON.stringify(catalog));
}

async function readInventory(publicationId: string): Promise<readonly string[] | null> {
  const file = inventoryFile(publicationId);
  if (!file.exists) return null;
  try {
    const value = JSON.parse(await file.text()) as unknown;
    if (
      !isRecord(value) ||
      value.format !== LOCAL_INVENTORY_FORMAT ||
      !Array.isArray(value.paths) ||
      value.paths.length > MAX_EXPORT_ARCHIVE_ENTRIES ||
      !value.paths.every(path => typeof path === 'string' && requireSafeArchivePath(path) === path)
    ) {
      throw new Error('The saved pack file list is unreadable.');
    }
    return [...new Set(value.paths as string[])];
  } catch (error) {
    console.error('A local pack file list could not be read.', error);
    return null;
  }
}

function writeInventory(directory: Directory, paths: Iterable<string>): void {
  const inventory: LocalPackInventory = {
    format: LOCAL_INVENTORY_FORMAT,
    paths: [...new Set(paths)].sort(),
  };
  const file = new NativeFile(directory, INVENTORY_FILE_NAME);
  file.create({intermediates: true, overwrite: true});
  file.write(JSON.stringify(inventory));
}

function fileAt(root: Directory, relativePath: string): NativeFile {
  return new NativeFile(root, ...relativePath.split('/'));
}

function rootPrefix(manifestPath: string): string {
  const separator = manifestPath.indexOf('/');
  return separator === -1 ? '' : manifestPath.slice(0, separator + 1);
}

function relativeExportPath(path: string, prefix: string): string | null {
  if (prefix === '') return path;
  return path.startsWith(prefix) ? path.slice(prefix.length) : null;
}

function joinChunks(chunks: readonly Uint8Array[], totalBytes: number): Uint8Array {
  const combined = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    combined.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return combined;
}

function hex(bytes: Uint8Array): string {
  return [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

async function sha256ForBytes(bytes: Uint8Array): Promise<string> {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return hex(new Uint8Array(await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, copy)));
}

export async function registerLocalPackServiceWorker(): Promise<void> {
  packsDirectory().create({idempotent: true, intermediates: true});
}

export async function listLocalPackDescriptors(): Promise<readonly DatasetDescriptor[]> {
  const catalog = await readCatalog();
  return [...catalog.packs]
    .sort((left, right) => right.storedAt - left.storedAt)
    .filter(pack => exportsDirectory(pack.publicationId).exists)
    .map(({storedAt: _storedAt, ...descriptor}) => descriptor);
}

export async function removeLocalPack(slug: string): Promise<boolean> {
  if (!/^local-[a-f0-9]{16}$/u.test(slug)) {
    throw new Error('Only a saved local pack can be deleted.');
  }
  const catalog = await readCatalog();
  const record = catalog.packs.find(pack => pack.slug === slug);
  if (!record) return false;
  writeCatalog({
    format: LOCAL_CATALOG_FORMAT,
    packs: catalog.packs.filter(pack => pack.slug !== slug),
  });
  const directory = packDirectory(record.publicationId);
  if (directory.exists) directory.delete();
  return true;
}

export function localDatasetSource(descriptor: DatasetDescriptor): DatasetSource {
  return {
    descriptor,
    base: exportsDirectory(descriptor.publicationId).uri.replace(/\/$/u, ''),
    previewBase: '',
  };
}

export async function installLocalPackArchive(
  file: LocalPackArchiveFile,
  manifestPath: string,
  manifestBytes: Uint8Array,
  manifest: unknown,
  summary: LocalPackManifestSummary,
  onProgress: (progress: LocalPackInstallProgress) => void,
  delta: LocalPackDelta | null = null,
): Promise<InstalledLocalPack> {
  await registerLocalPackServiceWorker();
  const publicationId = await sha256ForBytes(manifestBytes);
  const descriptor: DatasetDescriptor = {
    slug: `local-${publicationId.slice(0, 16)}`,
    displayName: summary.packName,
    minecraftVersion: summary.minecraftVersion,
    packVersion: localPackVersionLabel(summary.packVersion),
    publicationId,
    previewAssetSetId: publicationId,
    isDefault: false,
  };
  const initialCatalog = await readCatalog();
  if (
    initialCatalog.packs.some(pack => pack.publicationId === publicationId) &&
    exportsDirectory(publicationId).exists
  ) {
    return {descriptor, viewerHref: descriptor.slug};
  }
  if (delta !== null && delta.resultPublicationId !== publicationId) {
    throw new Error('The update ZIP result does not match its manifest.');
  }

  const deltaBase = delta === null
    ? null
    : initialCatalog.packs.find(pack => pack.publicationId === delta.basePublicationId) ?? null;
  if (delta !== null && deltaBase === null) {
    throw new Error(`Install the full ${delta.packName} export before adding this update ZIP.`);
  }
  if (
    delta !== null &&
    deltaBase !== null &&
    (deltaBase.displayName !== delta.packName ||
      deltaBase.minecraftVersion !== delta.minecraftVersion ||
      (delta.baseVersion !== null && deltaBase.packVersion !== delta.baseVersion) ||
      summary.packName !== delta.packName ||
      summary.minecraftVersion !== delta.minecraftVersion ||
      (delta.resultVersion !== null && summary.packVersion !== delta.resultVersion))
  ) {
    throw new Error('The update ZIP does not match the installed modpack.');
  }

  const basePaths = delta === null ? null : await readInventory(delta.basePublicationId);
  if (delta !== null && basePaths === null) {
    throw new Error(`Re-add the full ${delta.packName} export once before using update ZIPs.`);
  }

  const prefix = rootPrefix(manifestPath);
  const staging = new Directory(packsDirectory(), `.incoming-${publicationId}`);
  if (staging.exists) staging.delete();
  staging.create({intermediates: true});
  const stagingExports = new Directory(staging, 'exports');
  if (delta !== null) {
    await exportsDirectory(delta.basePublicationId).copy(stagingExports);
  } else {
    stagingExports.create();
  }

  const deltaFiles = delta === null
    ? null
    : new Map(delta.files.map(entry => [entry.path, entry] as const));
  const storedPaths = new Set(basePaths ?? []);
  if (delta !== null) {
    for (const deletedPath of delta.deletedPaths) {
      if (!storedPaths.delete(deletedPath)) {
        staging.delete();
        throw new Error(`The installed full export does not contain ${deletedPath}.`);
      }
      const deletedFile = fileAt(stagingExports, deletedPath);
      if (deletedFile.exists) deletedFile.delete();
    }
  }

  let entryCount = 0;
  let archiveError: Error | null = null;
  const writeJobs: Promise<void>[] = [];
  const archivePaths = new Set<string>();
  let completedWrites = 0;
  const unzip = new Unzip(entry => {
    entryCount += 1;
    if (entryCount > MAX_EXPORT_ARCHIVE_ENTRIES) {
      archiveError = new Error('This ZIP contains too many files to open safely.');
      return;
    }
    let safePath: string;
    try {
      safePath = requireSafeArchivePath(entry.name);
    } catch (error) {
      archiveError = error instanceof Error ? error : new Error(String(error));
      return;
    }
    if (entry.name.endsWith('/')) return;
    const relativePath = relativeExportPath(safePath, prefix);
    if (relativePath === null || relativePath.length === 0) return;
    if (isIgnoredArchiveMetadataPath(relativePath)) return;
    if (delta !== null && relativePath === 'delta.json') return;
    const expected: LocalPackDeltaFile | null = deltaFiles?.get(relativePath) ?? null;
    if (delta !== null && expected === null) {
      archiveError = new Error(`The update ZIP contains an undeclared file: ${relativePath}.`);
      return;
    }
    if (archivePaths.has(relativePath)) {
      archiveError = new Error(`The ZIP contains the same file twice: ${relativePath}`);
      return;
    }
    archivePaths.add(relativePath);
    storedPaths.add(relativePath);
    if (entry.originalSize !== undefined && entry.originalSize > MAX_LOCAL_FILE_BYTES) {
      archiveError = new Error(`The file ${relativePath} is too large to open in the viewer.`);
      return;
    }

    let byteCount = 0;
    let chunks: Uint8Array[] = [];
    entry.ondata = (error, data, final) => {
      if (error) {
        archiveError = new Error(`The ZIP could not read ${relativePath}.`);
        return;
      }
      byteCount += data.byteLength;
      if (byteCount > MAX_LOCAL_FILE_BYTES) {
        archiveError = new Error(`The file ${relativePath} is too large to open in the viewer.`);
        chunks = [];
        return;
      }
      const copy = new Uint8Array(data.byteLength);
      copy.set(data);
      chunks.push(copy);
      if (!final) return;
      if (relativePath === 'manifest.json') {
        chunks = [];
        return;
      }
      const bytes = joinChunks(chunks, byteCount);
      chunks = [];
      const job = (async () => {
        if (expected !== null) {
          if (bytes.byteLength !== expected.size) {
            throw new Error(`The update ZIP has the wrong size for ${relativePath}.`);
          }
          if (await sha256ForBytes(bytes) !== expected.sha256) {
            throw new Error(`The update ZIP failed its integrity check for ${relativePath}.`);
          }
        }
        const target = fileAt(stagingExports, relativePath);
        target.create({intermediates: true, overwrite: true});
        target.write(bytes);
        completedWrites += 1;
        onProgress({
          phase: 'saving',
          fraction: completedWrites / Math.max(1, storedPaths.size - 1),
          completedFiles: completedWrites,
          totalFiles: Math.max(completedWrites, storedPaths.size - 1),
        });
      })();
      writeJobs.push(job);
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
      onProgress({
        phase: 'reading',
        fraction: end / file.size,
        completedBytes: end,
        totalBytes: file.size,
        discoveredFiles: storedPaths.size,
      });
    }
    await Promise.all(writeJobs);
    if (delta !== null && deltaFiles !== null) {
      for (const expectedPath of deltaFiles.keys()) {
        if (!archivePaths.has(expectedPath)) {
          throw new Error(`The update ZIP is missing ${expectedPath}.`);
        }
      }
      if (storedPaths.size !== delta.counts.resultFiles) {
        throw new Error('The update ZIP does not match the installed export file list.');
      }
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
    const localManifest = fileAt(stagingExports, 'manifest.json');
    localManifest.create({intermediates: true, overwrite: true});
    localManifest.write(JSON.stringify({...manifest, publicationId}));
    writeInventory(staging, storedPaths);

    const finalDirectory = packDirectory(publicationId);
    if (finalDirectory.exists) finalDirectory.delete();
    await staging.move(finalDirectory);

    const superseded = initialCatalog.packs.filter(
      pack =>
        pack.publicationId !== publicationId &&
        pack.displayName === descriptor.displayName &&
        pack.minecraftVersion === descriptor.minecraftVersion,
    );
    const retained = initialCatalog.packs.filter(
      pack =>
        pack.publicationId !== publicationId &&
        !superseded.some(oldPack => oldPack.publicationId === pack.publicationId),
    );
    const nextPacks = [{...descriptor, storedAt: Date.now()}, ...retained]
      .sort((left, right) => right.storedAt - left.storedAt)
      .slice(0, MAX_LOCAL_PACKS);
    writeCatalog({format: LOCAL_CATALOG_FORMAT, packs: nextPacks});

    const retainedIds = new Set(nextPacks.map(pack => pack.publicationId));
    for (const oldPack of [...superseded, ...retained.slice(MAX_LOCAL_PACKS - 1)]) {
      if (retainedIds.has(oldPack.publicationId)) continue;
      const oldDirectory = packDirectory(oldPack.publicationId);
      if (oldDirectory.exists) oldDirectory.delete();
    }
    return {descriptor, viewerHref: descriptor.slug};
  } catch (error) {
    if (staging.exists) staging.delete();
    throw error;
  }
}
