import assert from 'node:assert/strict';
import test from 'node:test';

const {
  AUTH_ROUTE_PREFIX,
  SESSION_COOKIE_NAME,
  handleUserAccounts,
} = await import('./userAccounts.ts');

const ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';
const runtimeConfiguration = {
  PUBLIC_APP_ORIGIN: ORIGIN,
  DISCORD_CLIENT_ID: '123456789012345678',
  DISCORD_CLIENT_SECRET: 's'.repeat(48),
};

function database({session = null} = {}) {
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
          if (call.sql.includes('FROM user_sessions')) return session;
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

test('anonymous session response exposes no user data', async () => {
  const request = new Request(`${ORIGIN}${AUTH_ROUTE_PREFIX}session`);
  const response = await handleUserAccounts(request, {...runtimeConfiguration, DB: database()}, new URL(request.url));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {user: null});
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('valid sessions expose only the display name', async () => {
  const token = 'a'.repeat(64);
  const DB = database({
    session: {id: 'user-1', display_name: 'Recipe Builder', expires_at: Date.now() + 60_000},
  });
  const request = new Request(`${ORIGIN}${AUTH_ROUTE_PREFIX}session`, {
    headers: {Cookie: `${SESSION_COOKIE_NAME}=${token}`},
  });
  const response = await handleUserAccounts(request, {...runtimeConfiguration, DB}, new URL(request.url));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {user: {displayName: 'Recipe Builder'}});
  const lookup = DB.calls.find(call => call.sql.includes('FROM user_sessions'));
  assert.ok(lookup);
  assert.match(lookup.values[0], /^[a-f0-9]{64}$/u);
  assert.notEqual(lookup.values[0], token);
});

test('Discord sign-in uses PKCE, a one-time state cookie, and the configured callback', async () => {
  const DB = database();
  const request = new Request(`${ORIGIN}${AUTH_ROUTE_PREFIX}discord/start?returnTo=%2F%3Fpack%3Dmeatballcraft`);
  const response = await handleUserAccounts(request, {...runtimeConfiguration, DB}, new URL(request.url));

  assert.equal(response.status, 302);
  const destination = new URL(response.headers.get('location'));
  assert.equal(destination.origin, 'https://discord.com');
  assert.equal(destination.searchParams.get('client_id'), runtimeConfiguration.DISCORD_CLIENT_ID);
  assert.equal(destination.searchParams.get('redirect_uri'), `${ORIGIN}${AUTH_ROUTE_PREFIX}discord/callback`);
  assert.equal(destination.searchParams.get('code_challenge_method'), 'S256');
  assert.match(destination.searchParams.get('code_challenge'), /^[A-Za-z0-9_-]{40,128}$/u);
  assert.match(response.headers.get('set-cookie'), /HttpOnly; Secure; SameSite=Lax/u);
  const stateWrite = DB.calls.find(call => call.sql.startsWith('INSERT INTO oauth_login_states'));
  assert.ok(stateWrite);
  assert.match(stateWrite.values[0], /^[a-f0-9]{64}$/u);
  assert.equal(stateWrite.values[2], '/?pack=meatballcraft');
});

test('sign-out rejects requests without a same-origin Origin header', async () => {
  const request = new Request(`${ORIGIN}${AUTH_ROUTE_PREFIX}signout`, {method: 'POST'});
  const response = await handleUserAccounts(request, {...runtimeConfiguration, DB: database()}, new URL(request.url));

  assert.equal(response.status, 403);
});

test('sign-in fails closed when the public origin does not match', async () => {
  const request = new Request(`https://attacker.example${AUTH_ROUTE_PREFIX}discord/start`);
  const response = await handleUserAccounts(request, {...runtimeConfiguration, DB: database()}, new URL(request.url));

  assert.equal(response.status, 503);
});
