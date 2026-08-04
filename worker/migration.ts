import {
  type DatasetRuntime,
  methodNotAllowed,
  noStoreJson,
  tokensEqual,
} from './datasetRuntime.ts';

export const MIGRATION_BASE_PATH = '/api/admin/migration/';

const DATABASE_ROUTE = `${MIGRATION_BASE_PATH}database`;
const DATABASE_SUMMARY_ROUTE = `${MIGRATION_BASE_PATH}database-summary`;
const OBJECTS_ROUTE = `${MIGRATION_BASE_PATH}objects`;
const OBJECT_ROUTE = `${MIGRATION_BASE_PATH}object`;
const PAGE_LIMIT = 200;
const OBJECT_LIST_LIMIT = 250;
const MAX_TOKEN_BYTES = 8192;
const MAX_CURSOR_LENGTH = 4096;
const MAX_OBJECT_KEY_LENGTH = 1024;
const MAX_MIGRATION_OBJECT_BYTES = 128 * 1024 * 1024;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const OBJECT_BYTES_HEADER = 'x-mrt-migration-bytes';
const OBJECT_SHA256_HEADER = 'x-mrt-migration-sha256';
const CUSTOM_METADATA_HEADER = 'x-mrt-migration-custom-metadata';
const HTTP_METADATA_HEADER = 'x-mrt-migration-http-metadata';
const STORAGE_CLASS_HEADER = 'x-mrt-migration-storage-class';

const TABLES = Object.freeze({
  dataset_publications: Object.freeze({
    key: 'publication_id',
    columns: Object.freeze([
      'publication_id',
      'manifest_sha256',
      'object_count',
      'stored_bytes',
      'committed_at',
    ]),
  }),
  dataset_channels: Object.freeze({
    key: 'slug',
    columns: Object.freeze([
      'slug',
      'display_name',
      'minecraft_version',
      'pack_version',
      'publication_id',
      'preview_asset_set_id',
      'is_default',
      'revision',
      'activated_at',
    ]),
  }),
  modpacks: Object.freeze({
    key: 'id',
    columns: Object.freeze([
      'id',
      'name',
      'minecraft_version',
      'snapshot_json',
      'revision',
      'created_at',
      'updated_at',
    ]),
  }),
  feedback_reports: Object.freeze({
    key: 'id',
    columns: Object.freeze([
      'id',
      'kind',
      'title',
      'message',
      'contact',
      'pack_slug',
      'pack_name',
      'page_url',
      'user_agent',
      'fingerprint_hash',
      'created_at',
    ]),
  }),
  export_failure_reports: Object.freeze({
    key: 'fingerprint',
    columns: Object.freeze([
      'fingerprint',
      'issue_number',
      'issue_url',
      'status',
      'client_hash',
      'created_at',
      'updated_at',
    ]),
  }),
});

type MigrationTable = keyof typeof TABLES;

function missingOptionalTable(error: unknown, table: MigrationTable): boolean {
  return (
    table === 'export_failure_reports' &&
    error instanceof Error &&
    /no such table:\s*export_failure_reports/iu.test(error.message)
  );
}

interface MigrationHttpMetadata {
  contentType?: string;
  contentLanguage?: string;
  contentDisposition?: string;
  contentEncoding?: string;
  cacheControl?: string;
  cacheExpiry?: Date | string;
}

interface MigrationChecksums {
  sha256?: ArrayBuffer;
}

interface MigrationR2Object {
  key: string;
  size: number;
  etag?: string;
  httpEtag?: string;
  uploaded?: Date;
  customMetadata?: Record<string, string>;
  httpMetadata?: MigrationHttpMetadata;
  storageClass?: 'Standard' | 'InfrequentAccess';
  checksums?: MigrationChecksums;
  body?: ReadableStream<Uint8Array>;
}

interface MigrationR2Objects {
  objects: MigrationR2Object[];
  truncated: boolean;
  cursor?: string;
}

interface MigrationR2Bucket {
  head(key: string): Promise<MigrationR2Object | null>;
  get(key: string): Promise<MigrationR2Object | null>;
  put(
    key: string,
    body: ReadableStream<Uint8Array>,
    options: {
      onlyIf: {etagDoesNotMatch: string};
      sha256: string;
      customMetadata: Record<string, string>;
      httpMetadata: MigrationHttpMetadata;
      storageClass?: 'Standard' | 'InfrequentAccess';
    },
  ): Promise<MigrationR2Object | null>;
  list(options: {
    limit: number;
    cursor?: string;
    include: ['customMetadata', 'httpMetadata'];
  }): Promise<MigrationR2Objects>;
}

function migrationBucket(runtime: DatasetRuntime): MigrationR2Bucket | null {
  return (runtime.PREVIEW_ASSETS as unknown as MigrationR2Bucket | undefined) ?? null;
}

function validToken(token: string | undefined): token is string {
  return (
    typeof token === 'string' &&
    token.length >= 32 &&
    new TextEncoder().encode(token).byteLength <= MAX_TOKEN_BYTES &&
    !/[\s\u0000-\u001f\u007f]/u.test(token)
  );
}

async function authorize(
  request: Request,
  configuredToken: string | undefined,
  realm: string,
): Promise<Response | null> {
  if (!validToken(configuredToken)) {
    console.error('Storage migration access is disabled because its token is unavailable.');
    return noStoreJson({error: 'Storage migration is unavailable.'}, 503);
  }
  const authorization = request.headers.get('authorization');
  const candidate = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (!validToken(candidate) || !(await tokensEqual(candidate, configuredToken))) {
    console.warn('A storage migration request failed authentication.', {
      method: request.method,
      path: new URL(request.url).pathname,
    });
    return new Response('Unauthorized', {
      status: 401,
      headers: {
        'Cache-Control': 'no-store',
        'WWW-Authenticate': `Bearer realm="${realm}"`,
      },
    });
  }
  return null;
}

function exactQuery(url: URL, allowed: readonly string[]): boolean {
  const keys = [...url.searchParams.keys()];
  return (
    keys.every(key => allowed.includes(key)) &&
    allowed.every(key => url.searchParams.getAll(key).length <= 1)
  );
}

function tableFromUrl(url: URL): MigrationTable | null {
  const table = url.searchParams.get('table');
  return table && Object.hasOwn(TABLES, table) ? (table as MigrationTable) : null;
}

async function exportDatabaseSummary(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (request.method !== 'GET') return methodNotAllowed('GET');
  if (url.search !== '') return noStoreJson({error: 'Unexpected query parameters.'}, 400);
  const auth = await authorize(request, runtime.MIGRATION_EXPORT_TOKEN, 'storage-migration-export');
  if (auth) return auth;
  if (!runtime.DB) return noStoreJson({error: 'Database binding is unavailable.'}, 503);
  try {
    const counts: Partial<Record<MigrationTable, number>> = {};
    for (const table of Object.keys(TABLES) as MigrationTable[]) {
      let row: {count: number} | null;
      try {
        row = await runtime.DB
          .prepare(`SELECT COUNT(*) AS count FROM ${table}`)
          .first<{count: number}>();
      } catch (error) {
        if (!missingOptionalTable(error, table)) throw error;
        row = {count: 0};
      }
      if (!row || !Number.isSafeInteger(row.count) || row.count < 0) {
        throw new Error(`D1 returned an invalid row count for ${table}.`);
      }
      counts[table] = row.count;
    }
    return noStoreJson({format: 'mrt-storage-migration-database-v1', counts});
  } catch (error) {
    console.error('Storage migration could not summarize D1.', error);
    return noStoreJson({error: 'Database export failed.'}, 500);
  }
}

async function exportDatabasePage(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (request.method !== 'GET') return methodNotAllowed('GET');
  if (!exactQuery(url, ['table', 'after'])) {
    return noStoreJson({error: 'Unexpected query parameters.'}, 400);
  }
  const auth = await authorize(request, runtime.MIGRATION_EXPORT_TOKEN, 'storage-migration-export');
  if (auth) return auth;
  const table = tableFromUrl(url);
  if (!table) return noStoreJson({error: 'Unknown migration table.'}, 400);
  const after = url.searchParams.get('after') ?? '';
  if (after.length > 1024 || /[\u0000-\u001f\u007f]/u.test(after)) {
    return noStoreJson({error: 'Invalid migration cursor.'}, 400);
  }
  if (!runtime.DB) return noStoreJson({error: 'Database binding is unavailable.'}, 503);
  const spec = TABLES[table];
  try {
    const result = await runtime.DB
      .prepare(
        `SELECT ${spec.columns.join(', ')} FROM ${table} ` +
          `WHERE ${spec.key} > ? ORDER BY ${spec.key} LIMIT ?`,
      )
      .bind(after, PAGE_LIMIT)
      .all<Record<string, unknown>>();
    if (!result.success) throw new Error('D1 returned an unsuccessful export query.');
    const rows = result.results ?? [];
    const last = rows.at(-1)?.[spec.key];
    if (last !== undefined && typeof last !== 'string') {
      throw new Error(`D1 returned an invalid ${table} primary key.`);
    }
    return noStoreJson({
      format: 'mrt-storage-migration-database-v1',
      table,
      columns: spec.columns,
      rows,
      nextAfter: rows.length === PAGE_LIMIT ? last : null,
    });
  } catch (error) {
    if (missingOptionalTable(error, table)) {
      return noStoreJson({
        format: 'mrt-storage-migration-database-v1',
        table,
        columns: spec.columns,
        rows: [],
        nextAfter: null,
      });
    }
    console.error('Storage migration could not export a D1 page.', {table, error});
    return noStoreJson({error: 'Database export failed.'}, 500);
  }
}

function normalizedHttpMetadata(metadata: MigrationHttpMetadata | undefined) {
  if (!metadata) return {};
  return {
    ...(metadata.contentType ? {contentType: metadata.contentType} : {}),
    ...(metadata.contentLanguage ? {contentLanguage: metadata.contentLanguage} : {}),
    ...(metadata.contentDisposition ? {contentDisposition: metadata.contentDisposition} : {}),
    ...(metadata.contentEncoding ? {contentEncoding: metadata.contentEncoding} : {}),
    ...(metadata.cacheControl ? {cacheControl: metadata.cacheControl} : {}),
    ...(metadata.cacheExpiry
      ? {
          cacheExpiry:
            metadata.cacheExpiry instanceof Date
              ? metadata.cacheExpiry.toISOString()
              : metadata.cacheExpiry,
        }
      : {}),
  };
}

function restoredHttpMetadata(value: unknown): MigrationHttpMetadata | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const source = value as Record<string, unknown>;
  const allowed = new Set([
    'contentType',
    'contentLanguage',
    'contentDisposition',
    'contentEncoding',
    'cacheControl',
    'cacheExpiry',
  ]);
  if (Object.keys(source).some(key => !allowed.has(key))) return null;
  for (const [key, field] of Object.entries(source)) {
    if (typeof field !== 'string' || field.length > 4096 || /[\u0000-\u001f\u007f]/u.test(field)) {
      return null;
    }
    if (key === 'cacheExpiry' && Number.isNaN(Date.parse(field))) return null;
  }
  return {
    ...(source as Omit<MigrationHttpMetadata, 'cacheExpiry'>),
    ...(typeof source.cacheExpiry === 'string'
      ? {cacheExpiry: new Date(source.cacheExpiry)}
      : {}),
  };
}

function restoredCustomMetadata(value: unknown): Record<string, string> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const entries = Object.entries(value as Record<string, unknown>);
  if (entries.length > 128) return null;
  const metadata: Record<string, string> = {};
  for (const [key, field] of entries) {
    if (
      key.length === 0 ||
      key.length > 1024 ||
      typeof field !== 'string' ||
      field.length > 4096 ||
      /[\u0000-\u001f\u007f]/u.test(key + field)
    ) {
      return null;
    }
    metadata[key] = field;
  }
  return metadata;
}

function base64UrlEncode(value: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/u, '');
}

function base64UrlDecode(value: string | null): unknown {
  if (!value || value.length > 24 * 1024 || !/^[A-Za-z0-9_-]+$/u.test(value)) {
    throw new Error('Migration metadata header is missing or malformed.');
  }
  const padded = value.replaceAll('-', '+').replaceAll('_', '/') + '='.repeat((4 - (value.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes)) as unknown;
}

function validObjectKey(value: string | null): value is string {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    value.length <= MAX_OBJECT_KEY_LENGTH &&
    !/[\u0000-\u001f\u007f]/u.test(value)
  );
}

function serializedObject(object: MigrationR2Object) {
  return {
    key: object.key,
    size: object.size,
    etag: object.etag ?? null,
    customMetadata: object.customMetadata ?? {},
    httpMetadata: normalizedHttpMetadata(object.httpMetadata),
    storageClass: object.storageClass ?? 'Standard',
  };
}

async function exportObjectList(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (request.method !== 'GET') return methodNotAllowed('GET');
  if (!exactQuery(url, ['cursor'])) return noStoreJson({error: 'Unexpected query parameters.'}, 400);
  const auth = await authorize(request, runtime.MIGRATION_EXPORT_TOKEN, 'storage-migration-export');
  if (auth) return auth;
  const cursor = url.searchParams.get('cursor');
  if (cursor !== null && (cursor.length === 0 || cursor.length > MAX_CURSOR_LENGTH)) {
    return noStoreJson({error: 'Invalid object cursor.'}, 400);
  }
  const bucket = migrationBucket(runtime);
  if (!bucket) return noStoreJson({error: 'Object binding is unavailable.'}, 503);
  try {
    const page = await bucket.list({
      limit: OBJECT_LIST_LIMIT,
      include: ['customMetadata', 'httpMetadata'],
      ...(cursor === null ? {} : {cursor}),
    });
    if (page.truncated && !page.cursor) throw new Error('R2 omitted the next cursor.');
    return noStoreJson({
      format: 'mrt-storage-migration-objects-v1',
      objects: page.objects.map(serializedObject),
      truncated: page.truncated,
      cursor: page.truncated ? page.cursor : null,
    });
  } catch (error) {
    console.error('Storage migration could not list R2.', error);
    return noStoreJson({error: 'Object export failed.'}, 500);
  }
}

function objectExportHeaders(object: MigrationR2Object): Headers {
  return new Headers({
    'Cache-Control': 'no-store',
    'Content-Length': String(object.size),
    [OBJECT_BYTES_HEADER]: String(object.size),
    [CUSTOM_METADATA_HEADER]: base64UrlEncode(object.customMetadata ?? {}),
    [HTTP_METADATA_HEADER]: base64UrlEncode(normalizedHttpMetadata(object.httpMetadata)),
    [STORAGE_CLASS_HEADER]: object.storageClass ?? 'Standard',
    ...(object.etag ? {ETag: `"${object.etag.replace(/^"|"$/gu, '')}"`} : {}),
  });
}

function checksumHex(value: ArrayBuffer | undefined): string | null {
  if (!value) return null;
  return [...new Uint8Array(value)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

function metadataEqual(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

async function exportObject(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return methodNotAllowed('GET, HEAD, PUT');
  }
  if (!exactQuery(url, ['key'])) return noStoreJson({error: 'Unexpected query parameters.'}, 400);
  const auth = await authorize(request, runtime.MIGRATION_EXPORT_TOKEN, 'storage-migration-export');
  if (auth) return auth;
  const key = url.searchParams.get('key');
  if (!validObjectKey(key)) return noStoreJson({error: 'Invalid object key.'}, 400);
  const bucket = migrationBucket(runtime);
  if (!bucket) return noStoreJson({error: 'Object binding is unavailable.'}, 503);
  try {
    const object = request.method === 'HEAD' ? await bucket.head(key) : await bucket.get(key);
    if (!object) return noStoreJson({error: 'Object not found.'}, 404);
    if (!Number.isSafeInteger(object.size) || object.size < 0 || object.size > MAX_MIGRATION_OBJECT_BYTES) {
      throw new Error('R2 returned an invalid object size.');
    }
    if (request.method === 'GET' && !object.body) throw new Error('R2 omitted the object body.');
    return new Response(request.method === 'HEAD' ? null : object.body, {
      status: 200,
      headers: objectExportHeaders(object),
    });
  } catch (error) {
    console.error('Storage migration could not export an R2 object.', {key, error});
    return noStoreJson({error: 'Object export failed.'}, 500);
  }
}

async function importObject(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (request.method !== 'PUT') return methodNotAllowed('GET, HEAD, PUT');
  if (!exactQuery(url, ['key'])) return noStoreJson({error: 'Unexpected query parameters.'}, 400);
  const auth = await authorize(request, runtime.MIGRATION_IMPORT_TOKEN, 'storage-migration-import');
  if (auth) return auth;
  const key = url.searchParams.get('key');
  if (!validObjectKey(key)) return noStoreJson({error: 'Invalid object key.'}, 400);
  const rawBytes = request.headers.get(OBJECT_BYTES_HEADER);
  const contentLength = request.headers.get('content-length');
  const sha256 = request.headers.get(OBJECT_SHA256_HEADER);
  if (
    !rawBytes ||
    !/^(?:0|[1-9]\d*)$/u.test(rawBytes) ||
    rawBytes !== contentLength ||
    !SHA256_PATTERN.test(sha256 ?? '')
  ) {
    return noStoreJson({error: 'Object size or SHA-256 is missing or invalid.'}, 400);
  }
  const bytes = Number(rawBytes);
  if (!Number.isSafeInteger(bytes) || bytes < 0 || bytes > MAX_MIGRATION_OBJECT_BYTES) {
    return noStoreJson({error: 'Object size is outside the migration bound.'}, 413);
  }
  let customMetadata: Record<string, string> | null;
  let httpMetadata: MigrationHttpMetadata | null;
  try {
    customMetadata = restoredCustomMetadata(base64UrlDecode(request.headers.get(CUSTOM_METADATA_HEADER)));
    httpMetadata = restoredHttpMetadata(base64UrlDecode(request.headers.get(HTTP_METADATA_HEADER)));
  } catch (error) {
    console.warn('Storage migration rejected malformed object metadata.', {key, error});
    return noStoreJson({error: 'Object metadata is malformed.'}, 400);
  }
  if (!customMetadata || !httpMetadata) {
    return noStoreJson({error: 'Object metadata is invalid.'}, 400);
  }
  const rawStorageClass = request.headers.get(STORAGE_CLASS_HEADER) ?? 'Standard';
  if (rawStorageClass !== 'Standard' && rawStorageClass !== 'InfrequentAccess') {
    return noStoreJson({error: 'Object storage class is invalid.'}, 400);
  }
  if (!request.body) return noStoreJson({error: 'Object body is missing.'}, 400);
  const bucket = migrationBucket(runtime);
  if (!bucket) return noStoreJson({error: 'Object binding is unavailable.'}, 503);
  try {
    const existing = await bucket.head(key);
    if (existing) {
      const existingSha256 = checksumHex(existing.checksums?.sha256);
      if (
        existing.size === bytes &&
        existingSha256 === sha256 &&
        metadataEqual(existing.customMetadata ?? {}, customMetadata) &&
        metadataEqual(normalizedHttpMetadata(existing.httpMetadata), normalizedHttpMetadata(httpMetadata)) &&
        (existing.storageClass ?? 'Standard') === rawStorageClass
      ) {
        return noStoreJson({stored: true, reused: true, key, bytes, sha256});
      }
      return noStoreJson({error: 'Destination object conflicts with the migration source.'}, 409);
    }
    const stored = await bucket.put(key, request.body, {
      onlyIf: {etagDoesNotMatch: '*'},
      sha256: sha256 as string,
      customMetadata,
      httpMetadata,
      storageClass: rawStorageClass,
    });
    if (!stored) return noStoreJson({error: 'Destination object was created concurrently.'}, 409);
    if (stored.size !== bytes) throw new Error('R2 stored an unexpected object size.');
    return noStoreJson({stored: true, reused: false, key, bytes, sha256}, 201);
  } catch (error) {
    console.error('Storage migration could not import an R2 object.', {key, error});
    return noStoreJson({error: 'Object import failed.'}, 500);
  }
}

export async function handleStorageMigration(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (url.pathname === DATABASE_SUMMARY_ROUTE) {
    return exportDatabaseSummary(request, runtime, url);
  }
  if (url.pathname === DATABASE_ROUTE) {
    return exportDatabasePage(request, runtime, url);
  }
  if (url.pathname === OBJECTS_ROUTE) {
    return exportObjectList(request, runtime, url);
  }
  if (url.pathname === OBJECT_ROUTE) {
    return request.method === 'PUT'
      ? importObject(request, runtime, url)
      : exportObject(request, runtime, url);
  }
  return noStoreJson({error: 'Migration route not found.'}, 404);
}
