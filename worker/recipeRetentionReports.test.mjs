import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {
  RECIPE_RETENTION_REPORTS_ROUTE,
  handleRecipeRetentionReports,
} from './recipeRetentionReports.ts';

const ORIGIN = 'https://viewer.example';
const PACK = 'meatballcraft';
const PUBLICATION = 'a'.repeat(64);
const CLIENT_ID = '123e4567-e89b-12d3-a456-426614174000';

function database(currentPackAvailable = true) {
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
          if (call.sql.includes('FROM dataset_channels')) {
            return currentPackAvailable ? {slug: PACK} : null;
          }
          throw new Error(`Unexpected first query: ${call.sql}`);
        },
        async run() {
          return {success: true, meta: {changes: 1}};
        },
      };
    },
  };
}

function reportRequest(overrides = {}) {
  const body = {
    packSlug: PACK,
    publicationId: PUBLICATION,
    clientId: CLIENT_ID,
    recipeRef: [4, 12],
    itemKey: 'item|projecte:philosophers_stone',
    reusable: true,
    ...overrides,
  };
  return new Request(`${ORIGIN}${RECIPE_RETENTION_REPORTS_ROUTE}`, {
    method: 'PUT',
    headers: {'Content-Type': 'application/json', Origin: ORIGIN},
    body: JSON.stringify(body),
  });
}

test('stores one privacy-preserving manual retention report per browser and recipe input', async () => {
  const DB = database();
  const request = reportRequest();
  const response = await handleRecipeRetentionReports(request, {DB}, new URL(request.url));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {reported: true});
  const write = DB.calls.find(call => call.sql.startsWith('INSERT INTO recipe_retention_reports'));
  assert.ok(write);
  assert.deepEqual(write.values.slice(0, 5), [
    PACK,
    PUBLICATION,
    4,
    12,
    'item|projecte:philosophers_stone',
  ]);
  assert.equal(
    write.values[5],
    createHash('sha256').update(`browser:${CLIENT_ID}`).digest('hex'),
  );
  assert.equal(write.values[6], 1);
  assert.match(write.sql, /ON CONFLICT/u);
});

test('records a consumed correction without discarding the earlier report identity', async () => {
  const DB = database();
  const request = reportRequest({reusable: false});
  const response = await handleRecipeRetentionReports(request, {DB}, new URL(request.url));

  assert.equal(response.status, 200);
  const write = DB.calls.find(call => call.sql.startsWith('INSERT INTO recipe_retention_reports'));
  assert.equal(write.values[6], 0);
});

test('rejects cross-origin and malformed retention reports', async () => {
  const crossOrigin = reportRequest();
  crossOrigin.headers.set('Origin', 'https://attacker.example');
  assert.equal(
    (await handleRecipeRetentionReports(crossOrigin, {DB: database()}, new URL(crossOrigin.url))).status,
    403,
  );

  const malformed = reportRequest({recipeRef: [-1, 2]});
  assert.equal(
    (await handleRecipeRetentionReports(malformed, {DB: database()}, new URL(malformed.url))).status,
    400,
  );
});

test('rejects reports for pack versions that are not available', async () => {
  const request = reportRequest();
  const response = await handleRecipeRetentionReports(
    request,
    {DB: database(false)},
    new URL(request.url),
  );
  assert.equal(response.status, 404);
});
