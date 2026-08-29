import assert from 'node:assert/strict';
import test from 'node:test';

const {RECIPE_FAVORITES_ROUTE, handleRecipeFavorites} = await import('./recipeFavorites.ts');

const ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';
const PACK = 'meatballcraft';
const PUBLICATION = 'a'.repeat(64);
const CLIENT_ID = '123e4567-e89b-12d3-a456-426614174000';

function database(favoriteRows = [], session = null) {
  const calls = [];
  return {
    calls,
    batch(statements) {
      return Promise.resolve(statements.map(() => ({success: true})));
    },
    prepare(sql) {
      const call = {sql: sql.replace(/\s+/gu, ' ').trim(), values: []};
      calls.push(call);
      return {
        bind(...values) {
          call.values = values;
          return this;
        },
        async first() {
          if (call.sql.includes('FROM dataset_channels')) return {slug: PACK};
          if (call.sql.includes('FROM user_sessions')) return session;
          throw new Error(`Unexpected first query: ${call.sql}`);
        },
        async all() {
          if (call.sql.includes('FROM account_recipe_favorites')) {
            return {success: true, results: structuredClone(favoriteRows)};
          }
          throw new Error(`Unexpected all query: ${call.sql}`);
        },
        async run() {
          return {success: true, meta: {changes: 1}};
        },
      };
    },
  };
}

function getRequest(search = `packSlug=${PACK}&publicationId=${PUBLICATION}`, suffix = '') {
  return new Request(`${ORIGIN}${RECIPE_FAVORITES_ROUTE}${suffix}?${search}`);
}

function putRequest(recipeRef, cookie) {
  return new Request(`${ORIGIN}${RECIPE_FAVORITES_ROUTE}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Origin: ORIGIN,
      ...(cookie ? {Cookie: cookie} : {}),
    },
    body: JSON.stringify({
      packSlug: PACK,
      publicationId: PUBLICATION,
      clientId: CLIENT_ID,
      itemKey: 'item|minecraft:iron_ingot',
      recipeRef,
    }),
  });
}

test('returns the highest-count recipe for each item and requires at least one favorite', async () => {
  const DB = database([
    {item_key: 'item|a', recipe_category: 2, recipe_index: 8, favorite_count: 4},
    {item_key: 'item|a', recipe_category: 3, recipe_index: 1, favorite_count: 2},
    {item_key: 'item|b', recipe_category: 1, recipe_index: 9, favorite_count: 1},
  ]);
  const response = await handleRecipeFavorites(
    getRequest(),
    {DB},
    new URL(getRequest().url),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    favorites: [
      {itemKey: 'item|a', recipeRef: [2, 8], count: 4},
      {itemKey: 'item|b', recipeRef: [1, 9], count: 1},
    ],
  });
});

test('leaderboard ranks authenticated-user favorites by count', async () => {
  const DB = database([
    {item_key: 'item|b', recipe_category: 1, recipe_index: 9, favorite_count: 2},
    {item_key: 'item|a', recipe_category: 2, recipe_index: 8, favorite_count: 7},
  ]);
  const request = getRequest(undefined, '/leaderboard');
  const response = await handleRecipeFavorites(request, {DB}, new URL(request.url));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    entries: [
      {itemKey: 'item|b', recipeRef: [1, 9], count: 2},
      {itemKey: 'item|a', recipeRef: [2, 8], count: 7},
    ],
  });
  const query = DB.calls.find(call => call.sql.includes('FROM account_recipe_favorites'));
  assert.ok(query);
  assert.match(query.sql, /ORDER BY favorite_count DESC/u);
});

test('signed-in favorite writes use the account identity instead of the anonymous client hash', async () => {
  const session = {id: 'user-1', display_name: 'Builder', expires_at: Date.now() + 60_000};
  const DB = database([], session);
  const request = putRequest([4, 5], `${'__Host-mrt-session'}=${'a'.repeat(64)}`);
  const response = await handleRecipeFavorites(request, {DB}, new URL(request.url));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {saved: true, synced: true});
  const write = DB.calls.find(call => call.sql.startsWith('INSERT INTO account_recipe_favorites'));
  assert.ok(write);
  assert.deepEqual(write.values.slice(0, 4), [
    'user-1',
    PACK,
    PUBLICATION,
    'item|minecraft:iron_ingot',
  ]);
});

test('stores one anonymous recipe vote per pack version and item', async () => {
  const DB = database();
  const request = putRequest([7, 12]);
  const response = await handleRecipeFavorites(request, {DB}, new URL(request.url));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {saved: true, synced: false});
  const write = DB.calls.find(call => call.sql.startsWith('INSERT INTO recipe_favorites'));
  assert.ok(write);
  assert.equal(write.values[0], PACK);
  assert.equal(write.values[1], PUBLICATION);
  assert.equal(write.values[2], 'item|minecraft:iron_ingot');
  assert.match(write.values[3], /^[a-f0-9]{64}$/u);
  assert.notEqual(write.values[3], CLIENT_ID);
  assert.deepEqual(write.values.slice(4, 6), [7, 12]);
});

test('clearing a preferred recipe removes that client vote', async () => {
  const DB = database();
  const request = putRequest(null);
  const response = await handleRecipeFavorites(request, {DB}, new URL(request.url));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {saved: false, synced: false});
  const deletion = DB.calls.find(call => call.sql.startsWith('DELETE FROM recipe_favorites'));
  assert.ok(deletion);
  assert.equal(deletion.values.length, 4);
});

test('rejects duplicate query parameters and cross-origin writes', async () => {
  const duplicate = getRequest(`packSlug=${PACK}&packSlug=${PACK}&publicationId=${PUBLICATION}`);
  assert.equal(
    (await handleRecipeFavorites(duplicate, {DB: database()}, new URL(duplicate.url))).status,
    400,
  );

  const crossOrigin = putRequest([1, 2]);
  crossOrigin.headers.set('Origin', 'https://attacker.example');
  assert.equal(
    (await handleRecipeFavorites(crossOrigin, {DB: database()}, new URL(crossOrigin.url))).status,
    403,
  );
});
