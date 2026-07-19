import handler from 'vinext/server/app-router-entry';
import {
  datasetIdentityFromManifest,
  isDatasetPublicationId,
} from '../src/data/datasetIdentity';
import {
  MAX_PREVIEW_MANIFEST_BYTES,
  PREVIEW_ASSET_SET_PATTERN,
  PREVIEW_CATEGORY_ROUTE,
  PREVIEW_PACK_INDEX_ENTRY_BYTES,
  PREVIEW_PACK_INDEX_HEADER_BYTES,
  type PreviewManifest,
  type PreviewPackRecord,
  type ValidatedPreviewManifest,
  requirePreviewManifest,
} from './previewAssetContract.ts';
import {
  PREVIEW_UPLOAD_BASE_PATH,
  type PreviewUploadR2Bucket,
  type PreviewUploadRuntime,
  handlePreviewAssetUpload,
} from './previewAssetUpload.ts';

const MAX_PACK_BYTES = 1024 * 1024;
const MAX_WHOLE_PACK_CACHE_ENTRIES = 16;
const MAX_PUBLICATION_CACHE_ENTRIES = 8;
const MAX_PREVIEW_PACK_INDEX_CACHE_BYTES = 2 * 1024 * 1024;
const MAX_PREVIEW_PACK_INDEX_CACHE_ENTRIES = 64;
const PREVIEW_PACK_INDEX_MAGIC = 0x4d525049;
const PREVIEW_PACK_INDEX_VERSION = 1;
const PACKED_IMAGE_ROUTE = /^assets\/s\/(\d+)-(\d+)-(\d+)\.webp$/;
const PREVIEW_BOOTSTRAP_QUERY = /^\?dataset=([a-f0-9]{64})$/;
const PREVIEW_IMMUTABLE_QUERY =
  /^\?dataset=([a-f0-9]{64})&preview=([a-f0-9]{64})$/;
const PHYSICAL_EXPORT_BASE_PATH = '/exports/';
const VIRTUAL_EXPORT_BASE_PATH = '/dataset/exports/';
const VIRTUAL_PREVIEW_BASE_PATH = '/dataset/previews/';

interface PackedAssetCoordinate {
  packNumber: number;
  offset: number;
  length: number;
}

interface PublicationCacheEntry {
  promise: Promise<string>;
}

interface WholePackCacheEntry {
  promise: Promise<Uint8Array>;
}

interface PreviewPackIndex {
  view: DataView;
  entries: number;
}

interface PreviewManifestCacheEntry {
  promise: Promise<ValidatedPreviewManifest>;
}

interface PreviewPackIndexCacheEntry {
  promise: Promise<PreviewPackIndex>;
  bytes: number;
}

const publicationCache = new Map<string, PublicationCacheEntry>();
const wholePackCache = new Map<string, WholePackCacheEntry>();
const previewManifestCache = new Map<string, PreviewManifestCacheEntry>();
const previewPackIndexCache = new Map<string, PreviewPackIndexCacheEntry>();
let previewPackIndexCacheBytes = 0;

interface D1Result<T = unknown> {
  results?: T[];
  success: boolean;
}

interface D1PreparedStatement {
  bind(...values: unknown[]): D1PreparedStatement;
  all<T = unknown>(): Promise<D1Result<T>>;
  first<T = unknown>(): Promise<T | null>;
  run(): Promise<D1Result>;
}

interface D1Database {
  prepare(sql: string): D1PreparedStatement;
  batch(statements: D1PreparedStatement[]): Promise<D1Result[]>;
}

/** Read-only subset of Cloudflare's R2 range metadata used by preview delivery. */
interface PreviewR2Range {
  offset?: number;
  length?: number;
  suffix?: number;
}

/** Read-only subset of Cloudflare's R2ObjectBody used by preview delivery. */
interface PreviewR2ObjectBody {
  key: string;
  /** Full object size, including when `range` limits the returned body. */
  size: number;
  range?: PreviewR2Range;
  arrayBuffer(): Promise<ArrayBuffer>;
}

/** Read-only subset of the native Cloudflare R2Bucket binding. */
interface PreviewR2Bucket extends PreviewUploadR2Bucket {
  get(
    key: string,
    options?: {range?: {offset: number; length: number}},
  ): Promise<PreviewR2ObjectBody | null>;
}

interface RuntimeEnv extends PreviewUploadRuntime {
  ASSETS?: {fetch(request: Request): Promise<Response>};
  DB?: D1Database;
  /** Native R2 binding shared by validated delivery and authenticated ingestion. */
  PREVIEW_ASSETS?: PreviewR2Bucket;
}

interface PreviewConfiguration {
  assetSetId: string;
  bucket: PreviewR2Bucket;
  cacheNamespace: number;
}

const previewBucketCacheNamespaces = new WeakMap<object, number>();
let nextPreviewBucketCacheNamespace = 1;

async function readJsonResponse(response: Response, description: string): Promise<unknown> {
  if (!response.ok) {
    throw new Error(`${description} returned HTTP ${response.status}: ${response.url}`);
  }
  try {
    return await response.json();
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${description} contains invalid JSON: ${detail}`);
  }
}

function getExportAssetFetcher(env: Parameters<typeof handler.fetch>[1]) {
  const assets = (env as RuntimeEnv | undefined)?.ASSETS;
  if (!assets) {
    throw new Error(
      'The required ASSETS binding is unavailable; export files cannot be verified or served.',
    );
  }
  return (assetRequest: Request) => assets.fetch(assetRequest);
}

function physicalExportUrl(request: Request, assetKey: string): URL {
  const url = new URL(`${PHYSICAL_EXPORT_BASE_PATH}${assetKey}`, request.url);
  url.search = '';
  return url;
}

function previewConfiguration(env: Parameters<typeof handler.fetch>[1]): PreviewConfiguration {
  const runtime = (env ?? {}) as RuntimeEnv;
  const assetSetId = runtime.PREVIEW_ASSET_SET_ID;
  const bucket = runtime.PREVIEW_ASSETS;
  if (!assetSetId || !bucket) {
    throw new Error(
      'PREVIEW_ASSET_SET_ID and the PREVIEW_ASSETS native R2 binding are required for JEI layout previews.',
    );
  }
  if (!PREVIEW_ASSET_SET_PATTERN.test(assetSetId)) {
    throw new Error('PREVIEW_ASSET_SET_ID must be a lowercase SHA-256 digest.');
  }
  if (typeof bucket !== 'object' || typeof bucket.get !== 'function') {
    throw new Error('PREVIEW_ASSETS must be a native Cloudflare R2 bucket binding.');
  }
  let cacheNamespace = previewBucketCacheNamespaces.get(bucket);
  if (cacheNamespace === undefined) {
    cacheNamespace = nextPreviewBucketCacheNamespace;
    nextPreviewBucketCacheNamespace += 1;
    previewBucketCacheNamespaces.set(bucket, cacheNamespace);
  }
  return {assetSetId, bucket, cacheNamespace};
}

function previewObjectKey(configuration: PreviewConfiguration, assetKey: string): string {
  return `${configuration.assetSetId}/${assetKey}`;
}

function getPreviewManifest(
  configuration: PreviewConfiguration,
): Promise<ValidatedPreviewManifest> {
  const cacheKey = `${configuration.cacheNamespace}:${configuration.assetSetId}`;
  const cached = previewManifestCache.get(cacheKey);
  if (cached) {
    previewManifestCache.delete(cacheKey);
    previewManifestCache.set(cacheKey, cached);
    return cached.promise;
  }

  const entry = {} as PreviewManifestCacheEntry;
  entry.promise = (async () => {
    const objectKey = previewObjectKey(configuration, 'manifest.json');
    const object = await configuration.bucket.get(objectKey);
    if (!object) {
      throw new Error(`Recipe-preview manifest is missing from R2 at ${objectKey}.`);
    }
    if (object.key !== objectKey) {
      throw new Error(
        `Recipe-preview manifest R2 read returned key ${JSON.stringify(object.key)}; ` +
          `expected ${JSON.stringify(objectKey)}.`,
      );
    }
    if (
      !Number.isSafeInteger(object.size) ||
      object.size <= 0 ||
      object.size > MAX_PREVIEW_MANIFEST_BYTES
    ) {
      throw new Error(
        `Recipe-preview manifest R2 object has invalid size ${object.size}; expected ` +
          `1-${MAX_PREVIEW_MANIFEST_BYTES}.`,
      );
    }
    const bytes = new Uint8Array(await object.arrayBuffer());
    if (bytes.byteLength !== object.size) {
      throw new Error(
        `Recipe-preview manifest R2 body has ${bytes.byteLength} bytes; object metadata ` +
          `declares ${object.size}.`,
      );
    }
    let value: unknown;
    try {
      value = JSON.parse(new TextDecoder().decode(bytes)) as unknown;
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      throw new Error(`Recipe-preview manifest R2 object contains invalid JSON: ${detail}`);
    }
    return requirePreviewManifest(value, configuration.assetSetId);
  })().catch(error => {
    if (previewManifestCache.get(cacheKey) === entry) previewManifestCache.delete(cacheKey);
    console.error('Recipe-preview manifest could not be loaded.', error);
    throw error;
  });
  previewManifestCache.set(cacheKey, entry);
  trimOldest(previewManifestCache, MAX_PUBLICATION_CACHE_ENTRIES);
  return entry.promise;
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const isolated = bytes.slice().buffer as ArrayBuffer;
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', isolated));
  return [...digest].map(value => value.toString(16).padStart(2, '0')).join('');
}

function requirePreviewPackIndex(
  bytes: Uint8Array,
  packNumber: number,
  pack: PreviewPackRecord,
): PreviewPackIndex {
  const record = pack.index;
  if (
    bytes.byteLength !== record.bytes ||
    bytes.byteLength !==
      PREVIEW_PACK_INDEX_HEADER_BYTES + record.entries * PREVIEW_PACK_INDEX_ENTRY_BYTES
  ) {
    throw new Error(
      `Preview pack ${packNumber} index returned ${bytes.byteLength} bytes; expected ${record.bytes}.`,
    );
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (
    view.getUint32(0) !== PREVIEW_PACK_INDEX_MAGIC ||
    view.getUint16(4) !== PREVIEW_PACK_INDEX_VERSION ||
    view.getUint16(6) !== PREVIEW_PACK_INDEX_HEADER_BYTES ||
    view.getUint32(8) !== packNumber ||
    view.getUint32(12) !== pack.bytes ||
    view.getUint32(16) !== record.entries
  ) {
    throw new Error(`Preview pack ${packNumber} index header is invalid.`);
  }
  let cursor = 0;
  for (let entry = 0; entry < record.entries; entry += 1) {
    const position = PREVIEW_PACK_INDEX_HEADER_BYTES + entry * PREVIEW_PACK_INDEX_ENTRY_BYTES;
    const offset = view.getUint32(position);
    const length = view.getUint32(position + 4);
    if (offset !== cursor || length <= 0 || cursor + length > pack.bytes) {
      throw new Error(
        `Preview pack ${packNumber} index entry ${entry} is not a canonical contiguous range.`,
      );
    }
    cursor += length;
  }
  if (cursor !== pack.bytes) {
    throw new Error(
      `Preview pack ${packNumber} index covers ${cursor}/${pack.bytes} declared bytes.`,
    );
  }
  return {view, entries: record.entries};
}

function removePreviewPackIndexCacheEntry(
  cacheKey: string,
  expected?: PreviewPackIndexCacheEntry,
): void {
  const current = previewPackIndexCache.get(cacheKey);
  if (!current || (expected && current !== expected)) return;
  previewPackIndexCache.delete(cacheKey);
  previewPackIndexCacheBytes -= current.bytes;
}

function trimPreviewPackIndexCache(): void {
  while (
    previewPackIndexCache.size > MAX_PREVIEW_PACK_INDEX_CACHE_ENTRIES ||
    previewPackIndexCacheBytes > MAX_PREVIEW_PACK_INDEX_CACHE_BYTES
  ) {
    const oldestKey = previewPackIndexCache.keys().next().value as string | undefined;
    if (oldestKey === undefined) break;
    removePreviewPackIndexCacheEntry(oldestKey);
  }
}

function getPreviewPackIndex(
  configuration: PreviewConfiguration,
  packNumber: number,
  pack: PreviewPackRecord,
): Promise<PreviewPackIndex> {
  const cacheKey =
    `${configuration.cacheNamespace}:${configuration.assetSetId}:${packNumber}:${pack.index.sha256}`;
  const cached = previewPackIndexCache.get(cacheKey);
  if (cached) {
    previewPackIndexCache.delete(cacheKey);
    previewPackIndexCache.set(cacheKey, cached);
    return cached.promise;
  }

  const entry = {} as PreviewPackIndexCacheEntry;
  entry.bytes = pack.index.bytes;
  entry.promise = (async () => {
    const objectKey = previewObjectKey(configuration, pack.index.path);
    const object = await configuration.bucket.get(objectKey);
    if (!object) {
      throw new Error(
        `Preview pack ${packNumber} authorization index is missing from R2 at ${objectKey}.`,
      );
    }
    if (object.key !== objectKey) {
      throw new Error(
        `Preview pack ${packNumber} R2 index read returned key ` +
          `${JSON.stringify(object.key)}; expected ${JSON.stringify(objectKey)}.`,
      );
    }
    if (object.size !== pack.index.bytes) {
      throw new Error(
        `Preview pack ${packNumber} R2 index size is ${object.size}; ` +
          `expected ${pack.index.bytes}.`,
      );
    }
    const bytes = new Uint8Array(await object.arrayBuffer());
    if (bytes.byteLength !== pack.index.bytes) {
      throw new Error(
        `Preview pack ${packNumber} R2 index body has ${bytes.byteLength} bytes; ` +
          `expected ${pack.index.bytes}.`,
      );
    }
    const digest = await sha256Hex(bytes);
    if (digest !== pack.index.sha256) {
      throw new Error(
        `Preview pack ${packNumber} index SHA-256 is ${digest}; expected ${pack.index.sha256}.`,
      );
    }
    return requirePreviewPackIndex(bytes, packNumber, pack);
  })().catch(error => {
    removePreviewPackIndexCacheEntry(cacheKey, entry);
    console.error('Recipe-preview pack authorization index could not be loaded.', {
      packNumber,
      error,
    });
    throw error;
  });
  previewPackIndexCache.set(cacheKey, entry);
  previewPackIndexCacheBytes += entry.bytes;
  trimPreviewPackIndexCache();
  return entry.promise;
}

function packIndexAuthorizes(
  index: PreviewPackIndex,
  offset: number,
  length: number,
): boolean {
  let low = 0;
  let high = index.entries - 1;
  while (low <= high) {
    const middle = low + Math.floor((high - low) / 2);
    const position = PREVIEW_PACK_INDEX_HEADER_BYTES + middle * PREVIEW_PACK_INDEX_ENTRY_BYTES;
    const candidateOffset = index.view.getUint32(position);
    if (candidateOffset < offset) {
      low = middle + 1;
    } else if (candidateOffset > offset) {
      high = middle - 1;
    } else {
      return index.view.getUint32(position + 4) === length;
    }
  }
  return false;
}

function isSafeJsonAssetKey(assetKey: string): boolean {
  if (
    !assetKey.endsWith('.json') ||
    assetKey.startsWith('/') ||
    !/^[A-Za-z0-9._/-]+$/.test(assetKey)
  ) {
    return false;
  }
  return assetKey
    .split('/')
    .every(segment => segment.length > 0 && segment !== '.' && segment !== '..');
}

async function servePhysicalExportAsset(
  request: Request,
  env: Parameters<typeof handler.fetch>[1],
  assetKey: string,
  cacheControl: string,
): Promise<Response> {
  let response: Response;
  try {
    response = await getExportAssetFetcher(env)(
      new Request(physicalExportUrl(request, assetKey), {
        method: request.method,
        cache: cacheControl === 'no-store' ? 'no-store' : undefined,
      }),
    );
  } catch (error) {
    console.error('A gated export request cannot access its physical static asset.', {
      assetKey,
      error,
    });
    return new Response('Export assets unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const headers = new Headers(response.headers);
  headers.set('X-Content-Type-Options', 'nosniff');
  if (!response.ok) {
    console.error('A gated export request received a static-asset error response.', {
      assetKey,
      status: response.status,
    });
    headers.set('Cache-Control', 'no-store');
    return new Response(request.method === 'HEAD' ? null : response.body, {
      status: response.status,
      statusText: response.statusText,
      headers,
    });
  }
  headers.set('Cache-Control', cacheControl);
  return new Response(request.method === 'HEAD' ? null : response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

async function readCurrentPublicationIdentity(
  request: Request,
  env: Parameters<typeof handler.fetch>[1],
): Promise<string> {
  const manifestUrl = physicalExportUrl(request, 'manifest.json');
  return datasetIdentityFromManifest(
    await readJsonResponse(
      await getExportAssetFetcher(env)(new Request(manifestUrl, {cache: 'no-store'})),
      'Export manifest',
    ),
  );
}

function trimOldest<K, V>(cache: Map<K, V>, maxEntries: number): void {
  while (cache.size > maxEntries) {
    const oldestKey = cache.keys().next().value as K | undefined;
    if (oldestKey === undefined) break;
    cache.delete(oldestKey);
  }
}

function getPublicationCacheEntry(
  request: Request,
  env: Parameters<typeof handler.fetch>[1],
  exportBasePath: string,
): PublicationCacheEntry {
  const cached = publicationCache.get(exportBasePath);
  if (cached) {
    publicationCache.delete(exportBasePath);
    publicationCache.set(exportBasePath, cached);
    return cached;
  }

  const entry = {} as PublicationCacheEntry;
  entry.promise = readCurrentPublicationIdentity(request, env).catch(error => {
    if (publicationCache.get(exportBasePath) === entry) {
      publicationCache.delete(exportBasePath);
    }
    console.error('Export publication identity could not be loaded.', error);
    throw error;
  });
  publicationCache.set(exportBasePath, entry);
  trimOldest(publicationCache, MAX_PUBLICATION_CACHE_ENTRIES);
  return entry;
}

function parsePackedAssetCoordinate(assetKey: string): PackedAssetCoordinate | null {
  const match = PACKED_IMAGE_ROUTE.exec(assetKey);
  if (!match) return null;
  const [packText, offsetText, lengthText] = match.slice(1);
  const packNumber = Number(packText);
  const offset = Number(offsetText);
  const length = Number(lengthText);
  if (
    !Number.isSafeInteger(packNumber) ||
    packNumber < 0 ||
    String(packNumber).padStart(3, '0') !== packText ||
    !Number.isSafeInteger(offset) ||
    offset < 0 ||
    String(offset) !== offsetText ||
    !Number.isSafeInteger(length) ||
    length <= 0 ||
    String(length) !== lengthText ||
    !Number.isSafeInteger(offset + length) ||
    offset + length > MAX_PACK_BYTES
  ) {
    return null;
  }
  return {packNumber, offset, length};
}

function getWholePack(
  request: Request,
  env: Parameters<typeof handler.fetch>[1],
  exportBasePath: string,
  datasetIdentity: string,
  packNumber: number,
): Promise<Uint8Array> {
  const cacheKey = `${exportBasePath}:${datasetIdentity}:${packNumber}`;
  const cached = wholePackCache.get(cacheKey);
  if (cached) {
    wholePackCache.delete(cacheKey);
    wholePackCache.set(cacheKey, cached);
    return cached.promise;
  }

  const entry = {} as WholePackCacheEntry;
  entry.promise = (async () => {
    const packName = `pack-${String(packNumber).padStart(3, '0')}.bin`;
    const packUrl = physicalExportUrl(request, `assets/${packName}`);
    const response = await getExportAssetFetcher(env)(new Request(packUrl));
    if (response.status !== 200) {
      throw new Error(`${packName} returned HTTP ${response.status}.`);
    }
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.byteLength <= 0 || bytes.byteLength > MAX_PACK_BYTES) {
      throw new Error(
        `${packName} returned ${bytes.byteLength} bytes; expected 1-${MAX_PACK_BYTES}.`,
      );
    }
    return bytes;
  })().catch(error => {
    if (wholePackCache.get(cacheKey) === entry) wholePackCache.delete(cacheKey);
    throw error;
  });
  wholePackCache.set(cacheKey, entry);
  trimOldest(wholePackCache, MAX_WHOLE_PACK_CACHE_ENTRIES);
  return entry.promise;
}

interface SnapshotInput {
  minecraftVersion: string;
  mods: {id: string; name: string; itemCount: number}[];
  counts: {items: number; recipes: number; mobs: number};
}

interface ModpackRow {
  id: string;
  name: string;
  minecraft_version: string;
  snapshot_json: string;
  revision: number;
  created_at: number;
  updated_at: number;
}

const modpackTableSql = `
  CREATE TABLE IF NOT EXISTS modpacks (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    minecraft_version TEXT NOT NULL,
    snapshot_json TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )
`;

function json(data: unknown, status = 200): Response {
  return Response.json(data, {status, headers: {'Cache-Control': 'no-store'}});
}

function validSnapshot(value: unknown): value is SnapshotInput {
  if (!value || typeof value !== 'object') return false;
  const snapshot = value as Partial<SnapshotInput>;
  const validCount = (count: unknown) =>
    typeof count === 'number' && Number.isSafeInteger(count) && count >= 0;
  return (
    typeof snapshot.minecraftVersion === 'string' &&
    snapshot.minecraftVersion.length > 0 &&
    snapshot.minecraftVersion.length <= 40 &&
    Array.isArray(snapshot.mods) &&
    snapshot.mods.length <= 5000 &&
    snapshot.mods.every(
      mod =>
        mod &&
        typeof mod.id === 'string' &&
        mod.id.length > 0 &&
        mod.id.length <= 160 &&
        typeof mod.name === 'string' &&
        mod.name.length > 0 &&
        mod.name.length <= 160 &&
        validCount(mod.itemCount),
    ) &&
    !!snapshot.counts &&
    validCount(snapshot.counts.items) &&
    validCount(snapshot.counts.recipes) &&
    validCount(snapshot.counts.mobs)
  );
}

function serializeRow(row: ModpackRow) {
  try {
    return {
      id: row.id,
      name: row.name,
      minecraftVersion: row.minecraft_version,
      snapshot: JSON.parse(row.snapshot_json) as SnapshotInput,
      revision: row.revision,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    };
  } catch (error) {
    console.error(`Saved modpack ${row.id} contains invalid snapshot JSON`, error);
    throw error;
  }
}

async function handleModpackApi(
  request: Request,
  env: RuntimeEnv,
  pathname: string,
): Promise<Response> {
  const db = env.DB;
  if (!db) {
    console.error('Modpack API failed: the DB binding is unavailable.');
    return json({error: 'Modpack storage is unavailable.'}, 503);
  }

  const contentLength = Number(request.headers.get('content-length') ?? 0);
  if (contentLength > 512 * 1024) {
    return json({error: 'The modpack snapshot is too large.'}, 413);
  }

  await db.prepare(modpackTableSql).run();
  const id = pathname.startsWith('/api/modpacks/')
    ? decodeURIComponent(pathname.slice('/api/modpacks/'.length))
    : null;

  if (request.method === 'GET' && !id) {
    const rows = await db
      .prepare('SELECT * FROM modpacks ORDER BY updated_at DESC')
      .all<ModpackRow>();
    return json({modpacks: (rows.results ?? []).map(serializeRow)});
  }

  if (request.method === 'POST' && !id) {
    const body = (await request.json().catch(() => null)) as
      | {name?: unknown; snapshot?: unknown}
      | null;
    const name = typeof body?.name === 'string' ? body.name.trim() : '';
    if (!name || name.length > 80 || !validSnapshot(body?.snapshot)) {
      return json({error: 'A valid name and modpack snapshot are required.'}, 400);
    }
    const now = Date.now();
    const newId = crypto.randomUUID();
    await db
      .prepare(
        `INSERT INTO modpacks
          (id, name, minecraft_version, snapshot_json, revision, created_at, updated_at)
         VALUES (?, ?, ?, ?, 1, ?, ?)`,
      )
      .bind(
        newId,
        name,
        body.snapshot.minecraftVersion,
        JSON.stringify(body.snapshot),
        now,
        now,
      )
      .run();
    const row = await db.prepare('SELECT * FROM modpacks WHERE id = ?').bind(newId).first<ModpackRow>();
    return json({modpack: row ? serializeRow(row) : null}, 201);
  }

  if (!id) return json({error: 'Not found.'}, 404);

  if (request.method === 'PATCH') {
    const body = (await request.json().catch(() => null)) as
      | {name?: unknown; snapshot?: unknown}
      | null;
    const existing = await db.prepare('SELECT * FROM modpacks WHERE id = ?').bind(id).first<ModpackRow>();
    if (!existing) return json({error: 'Saved modpack not found.'}, 404);

    const name =
      body?.name === undefined
        ? existing.name
        : typeof body.name === 'string'
          ? body.name.trim()
          : '';
    const snapshot =
      body?.snapshot === undefined
        ? (JSON.parse(existing.snapshot_json) as SnapshotInput)
        : body.snapshot;
    if (!name || name.length > 80 || !validSnapshot(snapshot)) {
      return json({error: 'The modpack update is invalid.'}, 400);
    }

    const now = Date.now();
    await db
      .prepare(
        `UPDATE modpacks
         SET name = ?, minecraft_version = ?, snapshot_json = ?,
             revision = revision + 1, updated_at = ?
         WHERE id = ?`,
      )
      .bind(name, snapshot.minecraftVersion, JSON.stringify(snapshot), now, id)
      .run();
    const row = await db.prepare('SELECT * FROM modpacks WHERE id = ?').bind(id).first<ModpackRow>();
    return json({modpack: row ? serializeRow(row) : null});
  }

  if (request.method === 'DELETE') {
    const existing = await db.prepare('SELECT id FROM modpacks WHERE id = ?').bind(id).first();
    if (!existing) return json({error: 'Saved modpack not found.'}, 404);
    await db.prepare('DELETE FROM modpacks WHERE id = ?').bind(id).run();
    return json({deleted: true});
  }

  return json({error: 'Method not allowed.'}, 405);
}

async function servePackedAsset(
  request: Request,
  env: Parameters<typeof handler.fetch>[1],
  coordinate: PackedAssetCoordinate,
  exportBasePath: string,
  datasetIdentity: string,
): Promise<Response> {
  const {packNumber, offset, length} = coordinate;
  let pack: Uint8Array;
  try {
    pack = await getWholePack(
      request,
      env,
      exportBasePath,
      datasetIdentity,
      packNumber,
    );
  } catch (error) {
    console.error('Packed image could not retrieve its whole 1 MiB pack.', {
      packNumber,
      error,
    });
    return new Response('Asset pack unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (offset + length > pack.byteLength) {
    console.error('Packed image coordinate extends beyond its pack.', {
      packNumber,
      offset,
      length,
      packBytes: pack.byteLength,
    });
    return new Response('Asset coordinate is out of bounds', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const image = pack.slice(offset, offset + length);
  return new Response(request.method === 'HEAD' ? null : image, {
    status: 200,
    headers: {
      'Cache-Control': 'public, max-age=31536000, immutable, no-transform',
      'Content-Length': String(length),
      'Content-Type': 'image/webp',
      'X-Content-Type-Options': 'nosniff',
    },
  });
}

async function requirePreviewDataset(
  request: Request,
  env: Parameters<typeof handler.fetch>[1],
  url: URL,
  cacheKey: string,
  requestedIdentity: string,
): Promise<string | Response> {
  let currentIdentity: string;
  try {
    currentIdentity = await getPublicationCacheEntry(request, env, cacheKey).promise;
  } catch (error) {
    console.error('Recipe-preview request could not load the dataset identity.', error);
    return new Response('Dataset identity unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (requestedIdentity !== currentIdentity) {
    console.warn('Recipe-preview request used a stale or unknown dataset identity.', {
      path: url.pathname,
      requestedIdentity,
      currentIdentity,
    });
    return new Response('Dataset version mismatch', {
      status: 409,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  return requestedIdentity;
}

let previewCacheUnavailableLogged = false;

function getPreviewEdgeCache(): Cache | null {
  const storage = globalThis.caches as (CacheStorage & {default?: Cache}) | undefined;
  const cache = storage?.default;
  if (!cache && !previewCacheUnavailableLogged) {
    previewCacheUnavailableLogged = true;
    console.error(
      'The Workers Cache API is unavailable; validated JEI previews will continue from R2 ' +
        'without edge-cache acceleration.',
    );
  }
  return cache ?? null;
}

function previewEdgeCacheKey(request: Request): Request {
  // HEAD and GET share the canonical immutable GET representation. Request headers are excluded
  // so browser-specific headers cannot fragment the edge cache.
  return new Request(request.url, {method: 'GET'});
}

function responseForPreviewRequest(request: Request, response: Response): Response {
  if (request.method === 'GET') return response;
  return new Response(null, {
    status: response.status,
    statusText: response.statusText,
    headers: response.headers,
  });
}

async function matchValidatedPreviewResponse(
  request: Request,
  assetKey: string,
): Promise<Response | null> {
  const cache = getPreviewEdgeCache();
  if (!cache) return null;
  try {
    const response = await cache.match(previewEdgeCacheKey(request));
    if (!response) return null;
    const headers = new Headers(response.headers);
    headers.set('X-MRT-Preview-Cache', 'HIT');
    return new Response(request.method === 'HEAD' ? null : response.body, {
      status: response.status,
      statusText: response.statusText,
      headers,
    });
  } catch (error) {
    console.error('Validated JEI preview edge-cache lookup failed; continuing from R2.', {
      assetKey,
      error,
    });
    return null;
  }
}

function storeValidatedPreviewResponse(
  request: Request,
  response: Response,
  assetKey: string,
  ctx: Parameters<typeof handler.fetch>[2],
): void {
  // A HEAD response intentionally carries no body, so only a fully validated GET can populate
  // the shared representation. Error responses never reach this function.
  if (request.method !== 'GET' || response.status !== 200) return;
  const cache = getPreviewEdgeCache();
  if (!cache) return;
  const operation = cache.put(previewEdgeCacheKey(request), response.clone()).catch(error => {
    console.error('Validated JEI preview could not be stored in the edge cache.', {
      assetKey,
      error,
    });
  });
  if (ctx && typeof ctx.waitUntil === 'function') {
    ctx.waitUntil(operation);
    return;
  }
  console.error(
    'The Worker execution context has no waitUntil method; JEI preview cache persistence may be incomplete.',
    {assetKey},
  );
  void operation;
}

async function servePreviewJson(
  request: Request,
  configuration: PreviewConfiguration,
  assetKey: string,
  state: ValidatedPreviewManifest,
  ctx: Parameters<typeof handler.fetch>[2],
): Promise<Response> {
  const {manifest} = state;
  if (assetKey === 'manifest.json') {
    const body = `${JSON.stringify(manifest)}\n`;
    return new Response(request.method === 'HEAD' ? null : body, {
      status: 200,
      headers: {
        // The client discovers the configured assetSetId from this bootstrap document.
        // Every subsequent immutable URL includes that identity in its cache key.
        'Cache-Control': 'no-store',
        'Content-Length': String(new TextEncoder().encode(body).byteLength),
        'Content-Type': 'application/json; charset=utf-8',
        'X-Content-Type-Options': 'nosniff',
      },
    });
  }
  if (!PREVIEW_CATEGORY_ROUTE.test(assetKey)) {
    console.error('Recipe-preview JSON request has an unsupported path.', {assetKey});
    return new Response('Preview metadata path is malformed', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const declaredDocument = state.categoryDocumentsByPath.get(assetKey);
  if (!declaredDocument) {
    console.error('Recipe-preview metadata path is not declared by the immutable manifest.', {
      assetKey,
    });
    return new Response('Preview metadata path is not published', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }

  // Cache access occurs only after the canonical route/query, current dataset, configured
  // asset-set, validated manifest, and declared metadata inventory have all been checked.
  const cached = await matchValidatedPreviewResponse(request, assetKey);
  if (cached) return cached;

  let object: PreviewR2ObjectBody | null;
  const objectKey = previewObjectKey(configuration, assetKey);
  try {
    object = await configuration.bucket.get(objectKey);
  } catch (error) {
    console.error('Recipe-preview metadata R2 read failed.', {assetKey, objectKey, error});
    return new Response('Preview metadata unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (!object) {
    console.error('Recipe-preview metadata declared by the manifest is missing from R2.', {
      assetKey,
      objectKey,
    });
    return new Response('Preview metadata unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (object.key !== objectKey || object.size !== declaredDocument.bytes) {
    console.error('Recipe-preview metadata R2 object has invalid metadata.', {
      assetKey,
      objectKey,
      returnedKey: object.key,
      objectSize: object.size,
      expectedBytes: declaredDocument.bytes,
    });
    return new Response('Preview metadata is invalid', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  let bytes: Uint8Array<ArrayBuffer>;
  try {
    bytes = new Uint8Array(await object.arrayBuffer());
  } catch (error) {
    console.error('Recipe-preview metadata R2 body could not be read.', {
      assetKey,
      objectKey,
      error,
    });
    return new Response('Preview metadata unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (bytes.byteLength !== declaredDocument.bytes) {
    console.error('Recipe-preview metadata has an invalid byte length.', {
      assetKey,
      bytes: bytes.byteLength,
      expected: declaredDocument.bytes,
    });
    return new Response('Preview metadata is invalid', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const digest = await sha256Hex(bytes);
  if (digest !== declaredDocument.sha256) {
    console.error('Recipe-preview metadata failed its manifest SHA-256 check.', {
      assetKey,
      digest,
      expected: declaredDocument.sha256,
    });
    return new Response('Preview metadata is invalid', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const result = new Response(bytes, {
    status: 200,
    headers: {
      'Cache-Control': 'public, max-age=31536000, immutable',
      'Content-Length': String(bytes.byteLength),
      'Content-Type': 'application/json; charset=utf-8',
      'X-MRT-Preview-Cache': 'MISS',
      'X-Content-Type-Options': 'nosniff',
    },
  });
  storeValidatedPreviewResponse(request, result, assetKey, ctx);
  return responseForPreviewRequest(request, result);
}

async function servePreviewPackedAsset(
  request: Request,
  configuration: PreviewConfiguration,
  coordinate: PackedAssetCoordinate,
  manifest: PreviewManifest,
  assetKey: string,
  ctx: Parameters<typeof handler.fetch>[2],
): Promise<Response> {
  const {packNumber, offset, length} = coordinate;
  const pack = manifest.packs[packNumber];
  if (!pack || offset + length > pack.bytes) {
    console.error('Recipe-preview coordinate is outside its declared pack inventory.', {
      packNumber,
      offset,
      length,
      declaredPackBytes: pack?.bytes,
    });
    return new Response('Preview asset coordinate is outside the declared pack', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  let index: PreviewPackIndex;
  try {
    index = await getPreviewPackIndex(configuration, packNumber, pack);
  } catch (error) {
    console.error('Recipe-preview coordinate could not be authorized.', {
      packNumber,
      offset,
      length,
      error,
    });
    return new Response('Preview authorization index unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (!packIndexAuthorizes(index, offset, length)) {
    console.warn('Recipe-preview coordinate is not a published image boundary.', {
      packNumber,
      offset,
      length,
    });
    return new Response('Preview asset coordinate is not published', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }

  // Exact pack-boundary authorization precedes cache lookup, so an arbitrary coordinate can
  // never read or populate a cached byte range even if it uses a syntactically valid URL.
  const cached = await matchValidatedPreviewResponse(request, assetKey);
  if (cached) return cached;

  let object: PreviewR2ObjectBody | null;
  const objectKey = previewObjectKey(configuration, pack.path);
  try {
    object = await configuration.bucket.get(objectKey, {range: {offset, length}});
  } catch (error) {
    console.error('Recipe-preview R2 byte-range read failed.', {
      packNumber,
      offset,
      length,
      objectKey,
      error,
    });
    return new Response('Preview asset unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (!object) {
    console.error('Recipe-preview pack declared by the manifest is missing from R2.', {
      packNumber,
      offset,
      length,
      objectKey,
    });
    return new Response('Preview asset unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (
    object.key !== objectKey ||
    object.size !== pack.bytes ||
    object.range?.offset !== offset ||
    object.range?.length !== length ||
    object.range?.suffix !== undefined
  ) {
    console.error('Recipe-preview R2 binding returned invalid range metadata.', {
      packNumber,
      objectKey,
      returnedKey: object.key,
      objectSize: object.size,
      returnedRange: object.range,
      expectedPackBytes: pack.bytes,
      expectedRange: {offset, length},
    });
    return new Response('Preview R2 binding returned invalid range metadata', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  let image: Uint8Array<ArrayBuffer>;
  try {
    image = new Uint8Array(await object.arrayBuffer());
  } catch (error) {
    console.error('Recipe-preview R2 range body could not be read.', {
      packNumber,
      offset,
      length,
      objectKey,
      error,
    });
    return new Response('Preview asset unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (image.byteLength !== length) {
    console.error('Recipe-preview R2 binding returned the wrong range-body length.', {
      packNumber,
      offset,
      expected: length,
      received: image.byteLength,
    });
    return new Response('Preview R2 binding returned an incomplete byte range', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const result = new Response(image, {
    status: 200,
    headers: {
      'Cache-Control': 'public, max-age=31536000, immutable, no-transform',
      'Content-Length': String(length),
      'Content-Type': 'image/webp',
      'X-MRT-Preview-Cache': 'MISS',
      'X-Content-Type-Options': 'nosniff',
    },
  });
  storeValidatedPreviewResponse(request, result, assetKey, ctx);
  return responseForPreviewRequest(request, result);
}

async function handlePreviewRequest(
  request: Request,
  env: Parameters<typeof handler.fetch>[1],
  url: URL,
  markerIndex: number,
  ctx: Parameters<typeof handler.fetch>[2],
): Promise<Response> {
  const previewBasePath = url.pathname.slice(0, markerIndex + '/previews/'.length);
  const rawAssetKey = url.pathname.slice(markerIndex + '/previews/'.length);
  let assetKey: string;
  try {
    assetKey = decodeURIComponent(rawAssetKey);
  } catch (error) {
    console.error('Recipe-preview request contains malformed percent encoding.', {
      path: url.pathname,
      error,
    });
    return new Response('Malformed preview path', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (assetKey !== rawAssetKey) {
    console.error('Recipe-preview request path is not in canonical ASCII form.', {
      path: url.pathname,
    });
    return new Response('Preview path must use canonical encoding', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }

  const bootstrap = assetKey === 'manifest.json';
  const categoryDocument = PREVIEW_CATEGORY_ROUTE.test(assetKey);
  const coordinate = bootstrap || categoryDocument
    ? null
    : parsePackedAssetCoordinate(assetKey);
  if (!bootstrap && !categoryDocument && !coordinate) {
    console.error('Recipe-preview request has an unsupported or malformed path.', {assetKey});
    return new Response('Preview asset path is malformed', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }

  const versionMatch = (bootstrap ? PREVIEW_BOOTSTRAP_QUERY : PREVIEW_IMMUTABLE_QUERY).exec(
    url.search,
  );
  if (!versionMatch) {
    console.error('Recipe-preview request query is not exactly canonical.', {
      assetKey,
      expected: bootstrap ? '?dataset=<sha256>' : '?dataset=<sha256>&preview=<sha256>',
    });
    return new Response('Preview query string must use the exact canonical form', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const requestedDatasetIdentity = versionMatch[1];
  const requestedAssetSetId = bootstrap ? null : versionMatch[2];

  let configuration: PreviewConfiguration;
  try {
    configuration = previewConfiguration(env);
  } catch (error) {
    console.error('Recipe-preview storage is not configured.', error);
    return new Response('Recipe-preview storage unavailable', {
      status: 503,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (requestedAssetSetId !== null && requestedAssetSetId !== configuration.assetSetId) {
    console.warn('Recipe-preview request used a stale asset-set identity.', {
      assetKey,
      requestedAssetSetId,
      currentAssetSetId: configuration.assetSetId,
    });
    return new Response('Preview asset-set version mismatch', {
      status: 409,
      headers: {'Cache-Control': 'no-store'},
    });
  }

  const requestedIdentity = await requirePreviewDataset(
    request,
    env,
    url,
    `${url.origin}${previewBasePath}`,
    requestedDatasetIdentity,
  );
  if (requestedIdentity instanceof Response) return requestedIdentity;

  let state: ValidatedPreviewManifest;
  try {
    state = await getPreviewManifest(configuration);
  } catch (error) {
    console.error('Recipe-preview manifest failed validation.', error);
    return new Response('Recipe-preview storage unavailable', {
      status: 503,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const {manifest} = state;
  if (manifest.datasetPublicationId !== requestedIdentity) {
    console.error('Recipe-preview sidecar targets a different dataset publication.', {
      requestedIdentity,
      sidecarIdentity: manifest.datasetPublicationId,
      assetSetId: configuration.assetSetId,
    });
    return new Response('Recipe-preview dataset mismatch', {
      status: 409,
      headers: {'Cache-Control': 'no-store'},
    });
  }

  if (bootstrap || categoryDocument) {
    return servePreviewJson(request, configuration, assetKey, state, ctx);
  }
  return servePreviewPackedAsset(request, configuration, coordinate!, manifest, assetKey, ctx);
}

const worker = {
  async fetch(
    request: Request,
    env: Parameters<typeof handler.fetch>[1],
    ctx: Parameters<typeof handler.fetch>[2],
  ): Promise<Response> {
    try {
      const url = new URL(request.url);
      const runtime = (env ?? {}) as RuntimeEnv;
      if (url.pathname.startsWith(PREVIEW_UPLOAD_BASE_PATH)) {
        return await handlePreviewAssetUpload(request, runtime, url);
      }
      if (url.pathname === '/api/modpacks' || url.pathname.startsWith('/api/modpacks/')) {
        return await handleModpackApi(request, runtime, url.pathname);
      }
      if (
        (request.method === 'GET' || request.method === 'HEAD') &&
        url.pathname.startsWith(VIRTUAL_PREVIEW_BASE_PATH)
      ) {
        const markerIndex = VIRTUAL_PREVIEW_BASE_PATH.indexOf('/previews/');
        return await handlePreviewRequest(request, env, url, markerIndex, ctx);
      }
      if (
        (request.method === 'GET' || request.method === 'HEAD') &&
        url.pathname.startsWith(VIRTUAL_EXPORT_BASE_PATH)
      ) {
        const markerIndex = VIRTUAL_EXPORT_BASE_PATH.indexOf('/exports/');
        const exportBasePath = `${url.origin}${VIRTUAL_EXPORT_BASE_PATH}`;
        let assetKey: string;
        try {
          assetKey = decodeURIComponent(
            url.pathname.slice(markerIndex + '/exports/'.length),
          );
        } catch (error) {
          console.error('Export request contains malformed percent encoding.', {
            path: url.pathname,
            error,
          });
          return new Response('Malformed export path', {
            status: 400,
            headers: {'Cache-Control': 'no-store'},
          });
        }
        const datasetParameters = url.searchParams.getAll('dataset');

        // This is the only unversioned export request. It discovers the current publication and
        // must never enter browser or edge caches because every subsequent document is versioned.
        if (assetKey === 'manifest.json' && datasetParameters.length === 0) {
          return await servePhysicalExportAsset(
            request,
            env,
            'manifest.json',
            'no-store',
          );
        }

        const requestedIdentity = datasetParameters[0];
        if (
          datasetParameters.length !== 1 ||
          !isDatasetPublicationId(requestedIdentity)
        ) {
          console.error('Export request has a missing, duplicated, or malformed dataset identity.', {
            path: url.pathname,
            parameterCount: datasetParameters.length,
          });
          return new Response('One valid dataset publication identity is required', {
            status: 400,
            headers: {'Cache-Control': 'no-store'},
          });
        }

        const observedEntry = getPublicationCacheEntry(request, env, exportBasePath);
        let currentIdentity: string;
        try {
          currentIdentity = await observedEntry.promise;
        } catch (error) {
          console.error('Export request could not load the publication identity.', error);
          return new Response('Dataset identity unavailable', {
            status: 502,
            headers: {'Cache-Control': 'no-store'},
          });
        }

        // Cloudflare deploys Worker code, static assets, and bindings as one immutable version.
        // The cache therefore cannot cross a deployment. The requested identity is still checked
        // on every request so a browser routed to a newer version fails closed with 409 instead of
        // combining its older bootstrap data with newer JSON or packed images. Avoiding a redundant
        // manifest subrequest here is important for exports with hundreds of visible item icons.
        if (requestedIdentity !== currentIdentity) {
          console.warn('Export request used a stale or unknown dataset identity.', {
            path: url.pathname,
            requestedIdentity,
            currentIdentity,
          });
          return new Response('Dataset version mismatch', {
            status: 409,
            headers: {'Cache-Control': 'no-store'},
          });
        }

        // JSON, manifests, and shards are fetched from the physical /exports tree only after the
        // virtual /dataset/exports route passes the publication check. Sites serves existing
        // physical files before Worker code in production, so pointing the client directly at
        // /exports would silently bypass this gate even when run_worker_first is configured.
        if (!url.pathname.endsWith('.webp')) {
          if (!isSafeJsonAssetKey(assetKey)) {
            console.error('Gated export request has an unsupported or unsafe asset path.', {
              assetKey,
            });
            return new Response('Export asset path is malformed', {
              status: 400,
              headers: {'Cache-Control': 'no-store'},
            });
          }
          return await servePhysicalExportAsset(
            request,
            env,
            assetKey,
            'public, max-age=31536000, immutable',
          );
        }

        const coordinate = parsePackedAssetCoordinate(assetKey);
        if (!coordinate) {
          console.error('Packed image request has a malformed coordinate route.', {
            assetKey,
          });
          return new Response('Packed asset coordinate is malformed', {
            status: 400,
            headers: {'Cache-Control': 'no-store'},
          });
        }
        return await servePackedAsset(
          request,
          env,
          coordinate,
          exportBasePath,
          requestedIdentity,
        );
      }

      const response = await handler.fetch(request, env, ctx);
      if (response.headers.has('cache-control')) return response;
      const headers = new Headers(response.headers);
      headers.set('Cache-Control', 'no-store');
      return new Response(request.method === 'HEAD' ? null : response.body, {
        status: response.status,
        statusText: response.statusText,
        headers,
      });
    } catch (error) {
      console.error('Minecraft Recipe Tree request failed', error);
      throw error;
    }
  },
};

export default worker;
