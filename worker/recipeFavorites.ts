import {CORE_DATASET_PUBLICATION_ID_PATTERN} from './coreDatasetContract.ts';
import {
  DATASET_SLUG_PATTERN,
  ensureDatasetSchema,
} from './datasetRegistry.ts';
import {
  type D1Database,
  type DatasetRuntime,
  methodNotAllowed,
  noStoreJson,
  sha256Hex,
} from './datasetRuntime.ts';

export const RECIPE_FAVORITES_ROUTE = '/api/recipe-favorites';

const MAX_BODY_BYTES = 4 * 1024;
const MAX_ITEM_KEY_LENGTH = 512;
const MAX_CLIENT_ID_LENGTH = 128;
const MAX_GROUPED_FAVORITES = 50_000;
const UNSAFE_TEXT_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

const createFavoritesTableSql = `
  CREATE TABLE IF NOT EXISTS recipe_favorites (
    pack_slug TEXT NOT NULL,
    publication_id TEXT NOT NULL,
    item_key TEXT NOT NULL,
    client_hash TEXT NOT NULL,
    recipe_category INTEGER NOT NULL,
    recipe_index INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )
`;

const createUserItemIndexSql = `
  CREATE UNIQUE INDEX IF NOT EXISTS recipe_favorites_user_item_idx
  ON recipe_favorites (pack_slug, publication_id, item_key, client_hash)
`;

const createRankingIndexSql = `
  CREATE INDEX IF NOT EXISTS recipe_favorites_ranking_idx
  ON recipe_favorites (pack_slug, publication_id, item_key, recipe_category, recipe_index)
`;

const initializedDatabases = new WeakMap<object, Promise<void>>();

export function ensureRecipeFavoritesSchema(db: D1Database): Promise<void> {
  const cached = initializedDatabases.get(db as object);
  if (cached) return cached;
  const operation = Promise.all([
    ensureDatasetSchema(db),
    db
      .batch([
        db.prepare(createFavoritesTableSql),
        db.prepare(createUserItemIndexSql),
        db.prepare(createRankingIndexSql),
      ])
      .then(results => {
        if (results.some(result => !result.success)) {
          throw new Error('D1 reported an unsuccessful recipe-favorites schema statement.');
        }
      }),
  ])
    .then(() => undefined)
    .catch(error => {
      initializedDatabases.delete(db as object);
      console.error('Recipe-favorites schema initialization failed.', error);
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

function recipeRef(value: unknown): [number, number] | null | undefined {
  if (value === null) return null;
  if (
    !Array.isArray(value) ||
    value.length !== 2 ||
    !value.every(part => Number.isSafeInteger(part) && part >= 0)
  ) {
    return undefined;
  }
  return [value[0] as number, value[1] as number];
}

function requestOriginAllowed(request: Request, url: URL): boolean {
  const origin = request.headers.get('origin');
  if (!origin) return true;
  try {
    return new URL(origin).origin === url.origin;
  } catch {
    return false;
  }
}

async function requireCurrentPack(
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
  return row?.slug === packSlug;
}

interface FavoriteRow {
  item_key: string;
  recipe_category: number;
  recipe_index: number;
  favorite_count: number;
}

function validFavoriteRow(row: FavoriteRow): boolean {
  return (
    boundedText(row.item_key, MAX_ITEM_KEY_LENGTH) &&
    Number.isSafeInteger(row.recipe_category) &&
    row.recipe_category >= 0 &&
    Number.isSafeInteger(row.recipe_index) &&
    row.recipe_index >= 0 &&
    Number.isSafeInteger(row.favorite_count) &&
    row.favorite_count >= 1
  );
}

async function getFavorites(db: D1Database, url: URL): Promise<Response> {
  const packSlug = url.searchParams.get('packSlug');
  const publicationId = url.searchParams.get('publicationId');
  if (
    !packSlug ||
    !DATASET_SLUG_PATTERN.test(packSlug) ||
    packSlug.length > 80 ||
    !publicationId ||
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(publicationId) ||
    [...url.searchParams.keys()].some(key => key !== 'packSlug' && key !== 'publicationId') ||
    url.searchParams.getAll('packSlug').length !== 1 ||
    url.searchParams.getAll('publicationId').length !== 1
  ) {
    return noStoreJson({error: 'A current saved pack is required.'}, 400);
  }
  if (!(await requireCurrentPack(db, packSlug, publicationId))) {
    return noStoreJson({error: 'That saved pack version is not available.'}, 404);
  }

  const result = await db
    .prepare(
      `SELECT item_key, recipe_category, recipe_index, COUNT(*) AS favorite_count
       FROM recipe_favorites
       WHERE pack_slug = ? AND publication_id = ?
       GROUP BY item_key, recipe_category, recipe_index
       ORDER BY item_key ASC, favorite_count DESC, recipe_category ASC, recipe_index ASC
       LIMIT ?`,
    )
    .bind(packSlug, publicationId, MAX_GROUPED_FAVORITES)
    .all<FavoriteRow>();
  if (!result.success) throw new Error('D1 reported an unsuccessful recipe-favorites query.');

  const favorites: Array<{itemKey: string; recipeRef: [number, number]; count: number}> = [];
  const selectedItems = new Set<string>();
  for (const row of result.results ?? []) {
    if (!validFavoriteRow(row)) {
      throw new Error('Recipe-favorites storage contains invalid aggregate data.');
    }
    if (selectedItems.has(row.item_key)) continue;
    selectedItems.add(row.item_key);
    favorites.push({
      itemKey: row.item_key,
      recipeRef: [row.recipe_category, row.recipe_index],
      count: row.favorite_count,
    });
  }
  return noStoreJson({favorites});
}

async function updateFavorite(
  request: Request,
  db: D1Database,
  url: URL,
): Promise<Response> {
  if (!requestOriginAllowed(request, url)) {
    console.warn('A cross-origin recipe-favorite write was refused.', {origin: request.headers.get('origin')});
    return noStoreJson({error: 'Cross-origin favorite updates are not allowed.'}, 403);
  }
  const contentLength = request.headers.get('content-length');
  if (contentLength && (!/^\d+$/.test(contentLength) || Number(contentLength) > MAX_BODY_BYTES)) {
    return noStoreJson({error: 'Favorite update is too large.'}, 413);
  }
  let value: unknown;
  try {
    const body = await request.text();
    if (new TextEncoder().encode(body).byteLength > MAX_BODY_BYTES) {
      return noStoreJson({error: 'Favorite update is too large.'}, 413);
    }
    value = JSON.parse(body) as unknown;
  } catch {
    return noStoreJson({error: 'Favorite update must be valid JSON.'}, 400);
  }
  if (!isRecord(value) || !hasExactKeys(value, [
    'packSlug',
    'publicationId',
    'clientId',
    'itemKey',
    'recipeRef',
  ])) {
    return noStoreJson({error: 'Favorite update has an invalid shape.'}, 400);
  }
  const ref = recipeRef(value.recipeRef);
  if (
    !boundedText(value.packSlug, 80) ||
    !DATASET_SLUG_PATTERN.test(value.packSlug) ||
    typeof value.publicationId !== 'string' ||
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(value.publicationId) ||
    !boundedText(value.clientId, MAX_CLIENT_ID_LENGTH) ||
    !/^[a-f0-9-]{32,128}$/i.test(value.clientId) ||
    !boundedText(value.itemKey, MAX_ITEM_KEY_LENGTH) ||
    ref === undefined
  ) {
    return noStoreJson({error: 'Favorite update contains invalid values.'}, 400);
  }
  if (!(await requireCurrentPack(db, value.packSlug, value.publicationId))) {
    return noStoreJson({error: 'That saved pack version is not available.'}, 404);
  }

  const clientHash = await sha256Hex(new TextEncoder().encode(value.clientId));
  const now = Date.now();
  const result = ref === null
    ? await db
        .prepare(
          `DELETE FROM recipe_favorites
           WHERE pack_slug = ? AND publication_id = ? AND item_key = ? AND client_hash = ?`,
        )
        .bind(value.packSlug, value.publicationId, value.itemKey, clientHash)
        .run()
    : await db
        .prepare(
          `INSERT INTO recipe_favorites
             (pack_slug, publication_id, item_key, client_hash,
              recipe_category, recipe_index, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT (pack_slug, publication_id, item_key, client_hash)
           DO UPDATE SET recipe_category = excluded.recipe_category,
                         recipe_index = excluded.recipe_index,
                         updated_at = excluded.updated_at`,
        )
        .bind(
          value.packSlug,
          value.publicationId,
          value.itemKey,
          clientHash,
          ref[0],
          ref[1],
          now,
        )
        .run();
  if (!result.success) throw new Error('D1 reported an unsuccessful recipe-favorite update.');
  return noStoreJson({saved: ref !== null});
}

export async function handleRecipeFavorites(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  if (request.method !== 'GET' && request.method !== 'PUT') return methodNotAllowed('GET, PUT');
  const db = runtime.DB;
  if (!db) return noStoreJson({error: 'Recipe favorites are unavailable.'}, 503);
  try {
    await ensureRecipeFavoritesSchema(db);
    return request.method === 'GET'
      ? await getFavorites(db, url)
      : await updateFavorite(request, db, url);
  } catch (error) {
    console.error('Recipe-favorites request failed.', error);
    return noStoreJson({error: 'Recipe favorites are unavailable.'}, 503);
  }
}
