import {CORE_DATASET_PUBLICATION_ID_PATTERN} from './coreDatasetContract.ts';
import {DATASET_SLUG_PATTERN, ensureDatasetSchema} from './datasetRegistry.ts';
import {
  type D1Database,
  type DatasetRuntime,
  methodNotAllowed,
  noStoreJson,
  sha256Hex,
} from './datasetRuntime.ts';
import {currentUser, ensureUserAccountSchema} from './userAccounts.ts';

export const RECIPE_FAVORITES_ROUTE = '/api/recipe-favorites';

const MAX_BODY_BYTES = 4 * 1024;
const MAX_ITEM_KEY_LENGTH = 512;
const MAX_CLIENT_ID_LENGTH = 128;
const MAX_GROUPED_FAVORITES = 50_000;
const LEADERBOARD_LIMIT = 100;
const UNSAFE_TEXT_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

const legacySchemaStatements = [
  `CREATE TABLE IF NOT EXISTS recipe_favorites (
    pack_slug TEXT NOT NULL,
    publication_id TEXT NOT NULL,
    item_key TEXT NOT NULL,
    client_hash TEXT NOT NULL,
    recipe_category INTEGER NOT NULL,
    recipe_index INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )`,
  `CREATE UNIQUE INDEX IF NOT EXISTS recipe_favorites_user_item_idx
   ON recipe_favorites (pack_slug, publication_id, item_key, client_hash)`,
  `CREATE INDEX IF NOT EXISTS recipe_favorites_ranking_idx
   ON recipe_favorites (pack_slug, publication_id, item_key, recipe_category, recipe_index)`,
] as const;

const initializedDatabases = new WeakMap<object, Promise<void>>();

export function ensureRecipeFavoritesSchema(db: D1Database): Promise<void> {
  const cached = initializedDatabases.get(db as object);
  if (cached) return cached;
  const operation = Promise.all([
    ensureDatasetSchema(db),
    ensureUserAccountSchema(db),
    db.batch(legacySchemaStatements.map(statement => db.prepare(statement))).then(results => {
      if (results.some(result => !result.success)) {
        throw new Error('D1 reported an unsuccessful legacy recipe-favorites schema statement.');
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
  if (!origin) return false;
  try {
    return new URL(origin).origin === url.origin;
  } catch {
    return false;
  }
}

async function parseJsonBody(request: Request): Promise<unknown | Response> {
  const contentLength = request.headers.get('content-length');
  if (contentLength && (!/^\d+$/u.test(contentLength) || Number(contentLength) > MAX_BODY_BYTES)) {
    return noStoreJson({error: 'Favorite update is too large.'}, 413);
  }
  try {
    const body = await request.text();
    if (new TextEncoder().encode(body).byteLength > MAX_BODY_BYTES) {
      return noStoreJson({error: 'Favorite update is too large.'}, 413);
    }
    return JSON.parse(body) as unknown;
  } catch {
    return noStoreJson({error: 'Favorite update must be valid JSON.'}, 400);
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

function packIdentity(url: URL): {packSlug: string; publicationId: string} | null {
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
    return null;
  }
  return {packSlug, publicationId};
}

interface FavoriteRow {
  item_key: string;
  recipe_category: number;
  recipe_index: number;
  favorite_count: number;
}

interface PersonalFavoriteRow {
  item_key: string;
  recipe_category: number;
  recipe_index: number;
  updated_at: number;
}

function validFavoriteIdentity(row: {
  item_key: string;
  recipe_category: number;
  recipe_index: number;
}): boolean {
  return (
    boundedText(row.item_key, MAX_ITEM_KEY_LENGTH) &&
    Number.isSafeInteger(row.recipe_category) &&
    row.recipe_category >= 0 &&
    Number.isSafeInteger(row.recipe_index) &&
    row.recipe_index >= 0
  );
}

function validFavoriteRow(row: FavoriteRow): boolean {
  return (
    validFavoriteIdentity(row) &&
    Number.isSafeInteger(row.favorite_count) &&
    row.favorite_count >= 1
  );
}

async function validateRequestedPack(db: D1Database, url: URL) {
  const identity = packIdentity(url);
  if (!identity) return {response: noStoreJson({error: 'A current saved pack is required.'}, 400)};
  if (!(await requireCurrentPack(db, identity.packSlug, identity.publicationId))) {
    return {response: noStoreJson({error: 'That saved pack version is not available.'}, 404)};
  }
  return identity;
}

async function getCommunityFavorites(db: D1Database, url: URL): Promise<Response> {
  const pack = await validateRequestedPack(db, url);
  if ('response' in pack) return pack.response;
  const result = await db
    .prepare(
      `SELECT item_key, recipe_category, recipe_index, COUNT(*) AS favorite_count
       FROM account_recipe_favorites
       WHERE pack_slug = ? AND publication_id = ?
       GROUP BY item_key, recipe_category, recipe_index
       ORDER BY item_key ASC, favorite_count DESC, recipe_category ASC, recipe_index ASC
       LIMIT ?`,
    )
    .bind(pack.packSlug, pack.publicationId, MAX_GROUPED_FAVORITES)
    .all<FavoriteRow>();
  if (!result.success) throw new Error('D1 reported an unsuccessful community-favorites query.');

  const favorites: Array<{itemKey: string; recipeRef: [number, number]; count: number}> = [];
  const selectedItems = new Set<string>();
  for (const row of result.results ?? []) {
    if (!validFavoriteRow(row)) {
      throw new Error('Account recipe-favorites storage contains invalid aggregate data.');
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

async function getLeaderboard(db: D1Database, url: URL): Promise<Response> {
  const pack = await validateRequestedPack(db, url);
  if ('response' in pack) return pack.response;
  const result = await db
    .prepare(
      `SELECT item_key, recipe_category, recipe_index, COUNT(*) AS favorite_count
       FROM account_recipe_favorites
       WHERE pack_slug = ? AND publication_id = ?
       GROUP BY item_key, recipe_category, recipe_index
       ORDER BY favorite_count DESC, item_key ASC, recipe_category ASC, recipe_index ASC
       LIMIT ?`,
    )
    .bind(pack.packSlug, pack.publicationId, LEADERBOARD_LIMIT)
    .all<FavoriteRow>();
  if (!result.success) throw new Error('D1 reported an unsuccessful favorite-leaderboard query.');
  const entries = (result.results ?? []).map(row => {
    if (!validFavoriteRow(row)) {
      throw new Error('Favorite leaderboard storage contains invalid aggregate data.');
    }
    return {
      itemKey: row.item_key,
      recipeRef: [row.recipe_category, row.recipe_index] as [number, number],
      count: row.favorite_count,
    };
  });
  return noStoreJson({entries});
}

async function getPersonalFavorites(
  request: Request,
  db: D1Database,
  url: URL,
): Promise<Response> {
  const user = await currentUser(request, db);
  if (!user) return noStoreJson({error: 'Sign in to sync favorites.'}, 401);
  const pack = await validateRequestedPack(db, url);
  if ('response' in pack) return pack.response;
  const result = await db
    .prepare(
      `SELECT item_key, recipe_category, recipe_index, updated_at
       FROM account_recipe_favorites
       WHERE user_id = ? AND pack_slug = ? AND publication_id = ?
       ORDER BY item_key ASC
       LIMIT ?`,
    )
    .bind(user.id, pack.packSlug, pack.publicationId, MAX_GROUPED_FAVORITES)
    .all<PersonalFavoriteRow>();
  if (!result.success) throw new Error('D1 reported an unsuccessful personal-favorites query.');
  const favorites = (result.results ?? []).map(row => {
    if (!validFavoriteIdentity(row) || !Number.isSafeInteger(row.updated_at) || row.updated_at < 0) {
      throw new Error('Personal recipe-favorites storage contains invalid data.');
    }
    return {
      itemKey: row.item_key,
      recipeRef: [row.recipe_category, row.recipe_index] as [number, number],
      updatedAt: row.updated_at,
    };
  });
  return noStoreJson({favorites});
}

async function updateFavorite(request: Request, db: D1Database, url: URL): Promise<Response> {
  if (!requestOriginAllowed(request, url)) {
    console.warn('A cross-origin recipe-favorite write was refused.', {
      origin: request.headers.get('origin'),
    });
    return noStoreJson({error: 'Cross-origin favorite updates are not allowed.'}, 403);
  }
  const parsed = await parseJsonBody(request);
  if (parsed instanceof Response) return parsed;
  if (
    !isRecord(parsed) ||
    !hasExactKeys(parsed, ['packSlug', 'publicationId', 'clientId', 'itemKey', 'recipeRef'])
  ) {
    return noStoreJson({error: 'Favorite update has an invalid shape.'}, 400);
  }
  const ref = recipeRef(parsed.recipeRef);
  if (
    !boundedText(parsed.packSlug, 80) ||
    !DATASET_SLUG_PATTERN.test(parsed.packSlug) ||
    typeof parsed.publicationId !== 'string' ||
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(parsed.publicationId) ||
    !boundedText(parsed.clientId, MAX_CLIENT_ID_LENGTH) ||
    !/^[a-f0-9-]{32,128}$/iu.test(parsed.clientId) ||
    !boundedText(parsed.itemKey, MAX_ITEM_KEY_LENGTH) ||
    ref === undefined
  ) {
    return noStoreJson({error: 'Favorite update contains invalid values.'}, 400);
  }
  if (!(await requireCurrentPack(db, parsed.packSlug, parsed.publicationId))) {
    return noStoreJson({error: 'That saved pack version is not available.'}, 404);
  }

  const user = await currentUser(request, db);
  const now = Date.now();
  if (user) {
    const result = ref === null
      ? await db
          .prepare(
            `DELETE FROM account_recipe_favorites
             WHERE user_id = ? AND pack_slug = ? AND publication_id = ? AND item_key = ?`,
          )
          .bind(user.id, parsed.packSlug, parsed.publicationId, parsed.itemKey)
          .run()
      : await db
          .prepare(
            `INSERT INTO account_recipe_favorites
               (user_id, pack_slug, publication_id, item_key,
                recipe_category, recipe_index, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)
             ON CONFLICT (user_id, pack_slug, publication_id, item_key)
             DO UPDATE SET recipe_category = excluded.recipe_category,
                           recipe_index = excluded.recipe_index,
                           updated_at = excluded.updated_at`,
          )
          .bind(
            user.id,
            parsed.packSlug,
            parsed.publicationId,
            parsed.itemKey,
            ref[0],
            ref[1],
            now,
          )
          .run();
    if (!result.success) throw new Error('D1 reported an unsuccessful account favorite update.');
    return noStoreJson({saved: ref !== null, synced: true});
  }

  const clientHash = await sha256Hex(new TextEncoder().encode(parsed.clientId));
  const result = ref === null
    ? await db
        .prepare(
          `DELETE FROM recipe_favorites
           WHERE pack_slug = ? AND publication_id = ? AND item_key = ? AND client_hash = ?`,
        )
        .bind(parsed.packSlug, parsed.publicationId, parsed.itemKey, clientHash)
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
          parsed.packSlug,
          parsed.publicationId,
          parsed.itemKey,
          clientHash,
          ref[0],
          ref[1],
          now,
        )
        .run();
  if (!result.success) throw new Error('D1 reported an unsuccessful anonymous favorite update.');
  return noStoreJson({saved: ref !== null, synced: false});
}

async function claimAnonymousFavorites(
  request: Request,
  db: D1Database,
  url: URL,
): Promise<Response> {
  if (!requestOriginAllowed(request, url)) {
    console.warn('A cross-origin favorite claim was refused.', {origin: request.headers.get('origin')});
    return noStoreJson({error: 'Cross-origin favorite updates are not allowed.'}, 403);
  }
  const user = await currentUser(request, db);
  if (!user) return noStoreJson({error: 'Sign in to sync favorites.'}, 401);
  const parsed = await parseJsonBody(request);
  if (parsed instanceof Response) return parsed;
  if (
    !isRecord(parsed) ||
    !hasExactKeys(parsed, ['clientId']) ||
    !boundedText(parsed.clientId, MAX_CLIENT_ID_LENGTH) ||
    !/^[a-f0-9-]{32,128}$/iu.test(parsed.clientId)
  ) {
    return noStoreJson({error: 'Favorite claim contains invalid values.'}, 400);
  }
  const clientHash = await sha256Hex(new TextEncoder().encode(parsed.clientId));
  const results = await db.batch([
    db
      .prepare(
        `INSERT INTO account_recipe_favorites
           (user_id, pack_slug, publication_id, item_key,
            recipe_category, recipe_index, updated_at)
         SELECT ?, pack_slug, publication_id, item_key,
                recipe_category, recipe_index, updated_at
         FROM recipe_favorites
         WHERE client_hash = ?
         ON CONFLICT (user_id, pack_slug, publication_id, item_key)
         DO UPDATE SET recipe_category = excluded.recipe_category,
                       recipe_index = excluded.recipe_index,
                       updated_at = excluded.updated_at
         WHERE excluded.updated_at > account_recipe_favorites.updated_at`,
      )
      .bind(user.id, clientHash),
    db.prepare('DELETE FROM recipe_favorites WHERE client_hash = ?').bind(clientHash),
  ]);
  if (results.some(result => !result.success)) {
    throw new Error('D1 reported an unsuccessful anonymous favorite claim.');
  }
  return noStoreJson({claimed: results[0]?.meta?.changes ?? 0});
}

export async function handleRecipeFavorites(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  const db = runtime.DB;
  if (!db) return noStoreJson({error: 'Recipe favorites are unavailable.'}, 503);
  try {
    await ensureRecipeFavoritesSchema(db);
    if (url.pathname === RECIPE_FAVORITES_ROUTE) {
      if (request.method === 'GET') return getCommunityFavorites(db, url);
      if (request.method === 'PUT') return updateFavorite(request, db, url);
      return methodNotAllowed('GET, PUT');
    }
    if (url.pathname === `${RECIPE_FAVORITES_ROUTE}/leaderboard`) {
      if (request.method !== 'GET') return methodNotAllowed('GET');
      return getLeaderboard(db, url);
    }
    if (url.pathname === `${RECIPE_FAVORITES_ROUTE}/mine`) {
      if (request.method !== 'GET') return methodNotAllowed('GET');
      return getPersonalFavorites(request, db, url);
    }
    if (url.pathname === `${RECIPE_FAVORITES_ROUTE}/claim`) {
      if (request.method !== 'POST') return methodNotAllowed('POST');
      return claimAnonymousFavorites(request, db, url);
    }
    return noStoreJson({error: 'Not found.'}, 404);
  } catch (error) {
    console.error('Recipe-favorites request failed.', {path: url.pathname, error});
    return noStoreJson({error: 'Recipe favorites are unavailable.'}, 503);
  }
}
