import assert from 'node:assert/strict';
import test from 'node:test';
import {
  TEST_ORIGIN,
  TEST_USER_ID,
  supabaseTestAuthentication,
} from './testSupabaseAuth.mjs';

const {
  AUTH_ROUTE_PREFIX,
  deleteSupabaseAuthUser,
  discordAvatarUrlFor,
  handleUserAccounts,
} = await import('./userAccounts.ts');
const authentication = await supabaseTestAuthentication();

function database() {
  const calls = [];
  return {
    calls,
    batch(statements) {
      return Promise.resolve(statements.map(() => ({success: true, meta: {changes: 1}})));
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
          throw new Error(`Unexpected first query: ${call.sql}`);
        },
        async all() {
          return {success: true, results: []};
        },
        async run() {
          return {success: true, meta: {changes: 1}};
        },
      };
    },
  };
}

test.after(() => authentication.restoreFetch());

test('anonymous session response exposes no user data', async () => {
  const request = new Request(`${TEST_ORIGIN}${AUTH_ROUTE_PREFIX}session`);
  const response = await handleUserAccounts(
    request,
    {...authentication.runtime, DB: database()},
    new URL(request.url),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {user: null});
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('verified Supabase sessions upsert a D1 profile and expose only its display name', async () => {
  const DB = database();
  const request = new Request(`${TEST_ORIGIN}${AUTH_ROUTE_PREFIX}session`, {
    headers: {Authorization: authentication.authorization},
  });
  const response = await handleUserAccounts(
    request,
    {...authentication.runtime, DB},
    new URL(request.url),
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {user: {displayName: 'Recipe Builder'}});
  const write = DB.calls.find(call => call.sql.startsWith('INSERT INTO users'));
  assert.ok(write);
  assert.deepEqual(write.values.slice(0, 3), [TEST_USER_ID, TEST_USER_ID, 'Recipe Builder']);
  assert.deepEqual(write.values.slice(3, 5), [null, null]);
});

test('Discord avatars are accepted only from the Discord CDN and normalized', () => {
  assert.equal(discordAvatarUrlFor({
    app_metadata: {provider: 'discord'},
    user_metadata: {
      avatar_url: 'https://cdn.discordapp.com/avatars/123456789/avatar_hash.webp?size=128',
    },
  }), 'https://cdn.discordapp.com/avatars/123456789/avatar_hash.webp');
  assert.equal(discordAvatarUrlFor({
    app_metadata: {provider: 'email'},
    user_metadata: {
      avatar_url: 'https://cdn.discordapp.com/avatars/123456789/avatar_hash.webp',
    },
  }), null);
});

test('avatar delivery proxies a stored Discord image without exposing its CDN URL', async () => {
  const avatarKey = 'a'.repeat(64);
  const DB = database();
  DB.prepare = sql => {
    const call = {sql: sql.replace(/\s+/gu, ' ').trim(), values: []};
    DB.calls.push(call);
    return {
      bind(...values) {
        call.values = values;
        return this;
      },
      async first() {
        if (call.sql.startsWith('SELECT avatar_url FROM users')) {
          return {avatar_url: 'https://cdn.discordapp.com/avatars/123456789/avatar_hash.webp'};
        }
        throw new Error(`Unexpected first query: ${call.sql}`);
      },
      async all() { return {success: true, results: []}; },
      async run() { return {success: true, meta: {changes: 1}}; },
    };
  };
  const previousFetch = globalThis.fetch;
  let upstreamUrl;
  globalThis.fetch = async input => {
    upstreamUrl = input instanceof Request ? input.url : String(input);
    return new Response(new Uint8Array([1, 2, 3]), {
      headers: {'Content-Type': 'image/webp'},
    });
  };
  try {
    const request = new Request(`${TEST_ORIGIN}${AUTH_ROUTE_PREFIX}avatar/${avatarKey}`);
    const response = await handleUserAccounts(request, {DB}, new URL(request.url));
    assert.equal(response.status, 200);
    assert.equal(upstreamUrl, 'https://cdn.discordapp.com/avatars/123456789/avatar_hash.webp');
    assert.equal(response.headers.get('cache-control'), 'public, max-age=31536000, immutable');
    assert.equal(response.headers.get('content-type'), 'image/webp');
    assert.deepEqual([...new Uint8Array(await response.arrayBuffer())], [1, 2, 3]);
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test('authenticated account requests fail closed without the configured Supabase project', async () => {
  const request = new Request(`${TEST_ORIGIN}${AUTH_ROUTE_PREFIX}session`, {
    headers: {Authorization: authentication.authorization},
  });
  const response = await handleUserAccounts(
    request,
    {DB: database()},
    new URL(request.url),
  );

  assert.equal(response.status, 503);
});

test('account deletion calls only the authenticated Supabase admin user endpoint', async () => {
  const requests = [];
  await deleteSupabaseAuthUser({
    SUPABASE_URL: 'https://example-project.supabase.co',
    SUPABASE_SECRET_KEY: `sb_secret_${'a'.repeat(40)}`,
  }, TEST_USER_ID, async (input, init) => {
    requests.push({input, init});
    return new Response(null, {status: 204});
  });

  assert.equal(requests.length, 1);
  assert.equal(
    requests[0].input,
    `https://example-project.supabase.co/auth/v1/admin/users/${TEST_USER_ID}`,
  );
  assert.equal(requests[0].init.method, 'DELETE');
  assert.match(requests[0].init.headers.Authorization, /^Bearer sb_secret_/u);
});
