import {CORE_DATASET_PUBLICATION_ID_PATTERN} from './coreDatasetContract.ts';
import {betaCatalogIncludesPublication} from './betaDataProxy.ts';
import {DATASET_SLUG_PATTERN, ensureDatasetSchema} from './datasetRegistry.ts';
import {
  type D1Database,
  type DatasetRuntime,
  methodNotAllowed,
  noStoreJson,
  sha256Hex,
} from './datasetRuntime.ts';
import {currentUser, ensureUserAccountSchema} from './userAccounts.ts';

export const RECIPE_RETENTION_REPORTS_ROUTE = '/api/recipe-retention-reports';

const MAX_BODY_BYTES = 4 * 1024;
const MAX_ITEM_KEY_LENGTH = 512;
const MAX_CLIENT_ID_LENGTH = 128;
const UNSAFE_TEXT_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

const schemaStatements = [
  `CREATE TABLE IF NOT EXISTS recipe_retention_reports (
    pack_slug TEXT NOT NULL,
    publication_id TEXT NOT NULL,
    recipe_category INTEGER NOT NULL,
    recipe_index INTEGER NOT NULL,
    item_key TEXT NOT NULL,
    reporter_hash TEXT NOT NULL,
    reusable INTEGER NOT NULL CHECK (reusable IN (0, 1)),
    updated_at INTEGER NOT NULL
  )`,
  `CREATE UNIQUE INDEX IF NOT EXISTS recipe_retention_reports_reporter_idx
   ON recipe_retention_reports
      (pack_slug, publication_id, recipe_category, recipe_index, item_key, reporter_hash)`,
  `CREATE INDEX IF NOT EXISTS recipe_retention_reports_ranking_idx
   ON recipe_retention_reports
      (pack_slug, publication_id, reusable, recipe_category, recipe_index, item_key)`,
] as const;

const initializedDatabases = new WeakMap<object, Promise<void>>();

export function ensureRecipeRetentionReportsSchema(db: D1Database): Promise<void> {
  const cached = initializedDatabases.get(db as object);
  if (cached) return cached;
  const operation = Promise.all([
    ensureDatasetSchema(db),
    ensureUserAccountSchema(db),
    db.batch(schemaStatements.map(statement => db.prepare(statement))).then(results => {
      if (results.some(result => !result.success)) {
        throw new Error('D1 reported an unsuccessful recipe-retention schema statement.');
      }
    }),
  ])
    .then(() => undefined)
    .catch(error => {
      initializedDatabases.delete(db as object);
      console.error('Recipe-retention report schema initialization failed.', error);
      throw error;
    });
  initializedDatabases.set(db as object, operation);
  return operation;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, expected: readonly string[]): boolean {
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function boundedText(value: unknown, maximum: number): value is string {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    value.length <= maximum &&
    value.trim() === value &&
    !UNSAFE_TEXT_PATTERN.test(value)
  );
}

function sameOrigin(request: Request, url: URL): boolean {
  const origin = request.headers.get('origin');
  if (!origin) return false;
  try {
    return new URL(origin).origin === url.origin;
  } catch {
    return false;
  }
}

async function currentPackExists(
  runtime: DatasetRuntime,
  db: D1Database,
  packSlug: string,
  publicationId: string,
): Promise<boolean> {
  const row = await db
    .prepare(
      `SELECT slug FROM dataset_channels
       WHERE slug = ? AND publication_id = ?
       LIMIT 1`,
    )
    .bind(packSlug, publicationId)
    .first<{slug: string}>();
  if (row?.slug === packSlug) return true;
  return (await betaCatalogIncludesPublication(runtime, packSlug, publicationId)) === true;
}

async function readBody(request: Request): Promise<unknown | Response> {
  const contentLength = request.headers.get('content-length');
  if (contentLength && (!/^\d+$/u.test(contentLength) || Number(contentLength) > MAX_BODY_BYTES)) {
    return noStoreJson({error: 'Retention report is too large.'}, 413);
  }
  try {
    const body = await request.text();
    if (new TextEncoder().encode(body).byteLength > MAX_BODY_BYTES) {
      return noStoreJson({error: 'Retention report is too large.'}, 413);
    }
    return JSON.parse(body) as unknown;
  } catch {
    return noStoreJson({error: 'Retention report must be valid JSON.'}, 400);
  }
}

async function updateReport(
  request: Request,
  runtime: DatasetRuntime,
  db: D1Database,
  url: URL,
): Promise<Response> {
  if (!sameOrigin(request, url)) {
    console.warn('A cross-origin recipe-retention report was refused.', {
      origin: request.headers.get('origin'),
    });
    return noStoreJson({error: 'Cross-origin retention reports are not allowed.'}, 403);
  }
  if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) {
    return noStoreJson({error: 'Retention reports must use JSON.'}, 415);
  }
  const parsed = await readBody(request);
  if (parsed instanceof Response) return parsed;
  if (
    !isRecord(parsed) ||
    !hasExactKeys(parsed, [
      'clientId',
      'itemKey',
      'packSlug',
      'publicationId',
      'recipeRef',
      'reusable',
    ]) ||
    !boundedText(parsed.packSlug, 80) ||
    !DATASET_SLUG_PATTERN.test(parsed.packSlug) ||
    typeof parsed.publicationId !== 'string' ||
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(parsed.publicationId) ||
    !boundedText(parsed.clientId, MAX_CLIENT_ID_LENGTH) ||
    !/^[a-f0-9-]{32,128}$/iu.test(parsed.clientId) ||
    !boundedText(parsed.itemKey, MAX_ITEM_KEY_LENGTH) ||
    !Array.isArray(parsed.recipeRef) ||
    parsed.recipeRef.length !== 2 ||
    !parsed.recipeRef.every(part => Number.isSafeInteger(part) && part >= 0) ||
    typeof parsed.reusable !== 'boolean'
  ) {
    return noStoreJson({error: 'Retention report contains invalid values.'}, 400);
  }
  if (!(await currentPackExists(runtime, db, parsed.packSlug, parsed.publicationId))) {
    return noStoreJson({error: 'That saved pack version is not available.'}, 404);
  }
  const user = await currentUser(request, runtime);
  if (!user && request.headers.has('authorization')) {
    return noStoreJson({error: 'Your account session is invalid or expired.'}, 401);
  }
  const reporterIdentity = user ? `account:${user.id}` : `browser:${parsed.clientId}`;
  const reporterHash = await sha256Hex(new TextEncoder().encode(reporterIdentity));
  const recipeRef = parsed.recipeRef as [number, number];
  const result = await db
    .prepare(
      `INSERT INTO recipe_retention_reports
         (pack_slug, publication_id, recipe_category, recipe_index,
          item_key, reporter_hash, reusable, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT
         (pack_slug, publication_id, recipe_category, recipe_index, item_key, reporter_hash)
       DO UPDATE SET reusable = excluded.reusable, updated_at = excluded.updated_at`,
    )
    .bind(
      parsed.packSlug,
      parsed.publicationId,
      recipeRef[0],
      recipeRef[1],
      parsed.itemKey,
      reporterHash,
      parsed.reusable ? 1 : 0,
      Date.now(),
    )
    .run();
  if (!result.success) throw new Error('D1 reported an unsuccessful recipe-retention update.');
  console.log('Manual recipe retention report stored.', {
    packSlug: parsed.packSlug,
    publicationId: parsed.publicationId,
    recipeRef,
    itemKey: parsed.itemKey,
    reusable: parsed.reusable,
    reporterType: user ? 'account' : 'browser',
  });
  return noStoreJson({reported: true});
}

export async function handleRecipeRetentionReports(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (url.pathname !== RECIPE_RETENTION_REPORTS_ROUTE) {
    return noStoreJson({error: 'Not found.'}, 404);
  }
  if (request.method !== 'PUT') return methodNotAllowed('PUT');
  const db = runtime.DB;
  if (!db) return noStoreJson({error: 'Recipe retention reporting is unavailable.'}, 503);
  try {
    await ensureRecipeRetentionReportsSchema(db);
    return updateReport(request, runtime, db, url);
  } catch (error) {
    console.error('Recipe-retention report request failed.', error);
    return noStoreJson({error: 'Recipe retention reporting is unavailable.'}, 503);
  }
}
