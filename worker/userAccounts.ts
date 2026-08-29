import {
  type D1Database,
  type DatasetRuntime,
  methodNotAllowed,
  noStoreJson,
  sha256Hex,
} from './datasetRuntime.ts';

export const AUTH_ROUTE_PREFIX = '/api/auth/';
export const SESSION_COOKIE_NAME = '__Host-mrt-session';
const OAUTH_COOKIE_NAME = '__Host-mrt-oauth';
const SESSION_DURATION_SECONDS = 60 * 60 * 24 * 30;
const OAUTH_DURATION_SECONDS = 60 * 10;
const DISCORD_API_ORIGIN = 'https://discord.com';
const initializedDatabases = new WeakMap<object, Promise<void>>();

const schemaStatements = [
  `CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY NOT NULL,
    provider TEXT NOT NULL,
    provider_user_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )`,
  `CREATE UNIQUE INDEX IF NOT EXISTS users_provider_identity_idx
   ON users (provider, provider_user_id)`,
  `CREATE TABLE IF NOT EXISTS user_sessions (
    token_hash TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS user_sessions_user_idx ON user_sessions (user_id)`,
  `CREATE INDEX IF NOT EXISTS user_sessions_expiry_idx ON user_sessions (expires_at)`,
  `CREATE TABLE IF NOT EXISTS oauth_login_states (
    state_hash TEXT PRIMARY KEY NOT NULL,
    code_verifier TEXT NOT NULL,
    return_to TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
  )`,
  `CREATE INDEX IF NOT EXISTS oauth_login_states_expiry_idx ON oauth_login_states (expires_at)`,
  `CREATE TABLE IF NOT EXISTS account_recipe_favorites (
    user_id TEXT NOT NULL,
    pack_slug TEXT NOT NULL,
    publication_id TEXT NOT NULL,
    item_key TEXT NOT NULL,
    recipe_category INTEGER NOT NULL,
    recipe_index INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, pack_slug, publication_id, item_key),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS account_recipe_favorites_ranking_idx
   ON account_recipe_favorites
     (pack_slug, publication_id, item_key, recipe_category, recipe_index)`,
] as const;

export interface AuthenticatedUser {
  id: string;
  displayName: string;
}

interface SessionRow {
  id: string;
  display_name: string;
  expires_at: number;
}

function randomToken(byteLength = 32): string {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength));
  return base64Url(bytes);
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/u, '');
}

async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

function cookieValue(request: Request, name: string): string | null {
  for (const segment of (request.headers.get('cookie') ?? '').split(';')) {
    const separator = segment.indexOf('=');
    if (separator < 0 || segment.slice(0, separator).trim() !== name) continue;
    return segment.slice(separator + 1).trim() || null;
  }
  return null;
}

function sessionCookie(value: string, maxAge: number): string {
  return `${SESSION_COOKIE_NAME}=${value}; Path=/; Max-Age=${maxAge}; HttpOnly; Secure; SameSite=Lax`;
}

function oauthCookie(value: string, maxAge: number): string {
  return `${OAUTH_COOKIE_NAME}=${value}; Path=/; Max-Age=${maxAge}; HttpOnly; Secure; SameSite=Lax`;
}

function canonicalOrigin(runtime: DatasetRuntime, requestUrl: URL): string {
  if (!runtime.PUBLIC_APP_ORIGIN) {
    throw new Error('PUBLIC_APP_ORIGIN is required for account sign-in.');
  }
  const configured = new URL(runtime.PUBLIC_APP_ORIGIN);
  if (
    configured.protocol !== 'https:' ||
    configured.username ||
    configured.password ||
    configured.pathname !== '/' ||
    configured.search ||
    configured.hash
  ) {
    throw new Error('PUBLIC_APP_ORIGIN must be an HTTPS origin without credentials or a path.');
  }
  if (requestUrl.origin !== configured.origin) {
    throw new Error(`Account request origin ${requestUrl.origin} does not match PUBLIC_APP_ORIGIN.`);
  }
  return configured.origin;
}

function oauthConfiguration(runtime: DatasetRuntime, requestUrl: URL) {
  const origin = canonicalOrigin(runtime, requestUrl);
  if (!runtime.DISCORD_CLIENT_ID || !/^\d{5,32}$/u.test(runtime.DISCORD_CLIENT_ID)) {
    throw new Error('DISCORD_CLIENT_ID is missing or invalid.');
  }
  if (!runtime.DISCORD_CLIENT_SECRET || runtime.DISCORD_CLIENT_SECRET.length < 32) {
    throw new Error('DISCORD_CLIENT_SECRET is missing or invalid.');
  }
  return {
    origin,
    clientId: runtime.DISCORD_CLIENT_ID,
    clientSecret: runtime.DISCORD_CLIENT_SECRET,
    redirectUri: `${origin}${AUTH_ROUTE_PREFIX}discord/callback`,
  };
}

function safeReturnTo(value: string | null): string {
  if (!value) return '/';
  if (
    value.length > 512 ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    /[\u0000-\u001f\u007f]/u.test(value)
  ) {
    throw new Error('The account return path is invalid.');
  }
  return value;
}

function sameOriginWrite(request: Request, url: URL): boolean {
  const origin = request.headers.get('origin');
  if (!origin) return false;
  try {
    return new URL(origin).origin === url.origin;
  } catch {
    return false;
  }
}

export function ensureUserAccountSchema(db: D1Database): Promise<void> {
  const cached = initializedDatabases.get(db as object);
  if (cached) return cached;
  const operation = db
    .batch(schemaStatements.map(statement => db.prepare(statement)))
    .then(results => {
      if (results.some(result => !result.success)) {
        throw new Error('D1 reported an unsuccessful user-account schema statement.');
      }
    })
    .catch(error => {
      initializedDatabases.delete(db as object);
      console.error('User-account schema initialization failed.', error);
      throw error;
    });
  initializedDatabases.set(db as object, operation);
  return operation;
}

export async function currentUser(
  request: Request,
  db: D1Database,
): Promise<AuthenticatedUser | null> {
  const token = cookieValue(request, SESSION_COOKIE_NAME);
  if (!token || !/^[A-Za-z0-9_-]{40,128}$/u.test(token)) return null;
  const tokenHash = await sha256Hex(new TextEncoder().encode(token));
  const row = await db
    .prepare(
      `SELECT users.id, users.display_name, user_sessions.expires_at
       FROM user_sessions
       JOIN users ON users.id = user_sessions.user_id
       WHERE user_sessions.token_hash = ? AND user_sessions.expires_at > ?
       LIMIT 1`,
    )
    .bind(tokenHash, Date.now())
    .first<SessionRow>();
  if (!row) return null;
  if (
    !row.id ||
    !row.display_name ||
    !Number.isSafeInteger(row.expires_at) ||
    row.expires_at <= Date.now()
  ) {
    throw new Error('User session storage contains invalid data.');
  }
  return {id: row.id, displayName: row.display_name};
}

async function startDiscordSignIn(
  request: Request,
  runtime: DatasetRuntime,
  db: D1Database,
  url: URL,
): Promise<Response> {
  if ([...url.searchParams.keys()].some(key => key !== 'returnTo')) {
    return noStoreJson({error: 'Sign-in request has unsupported parameters.'}, 400);
  }
  const configuration = oauthConfiguration(runtime, url);
  const returnTo = safeReturnTo(url.searchParams.get('returnTo'));
  const state = randomToken();
  const verifier = randomToken(48);
  const stateHash = await sha256Hex(new TextEncoder().encode(state));
  const now = Date.now();
  const result = await db.batch([
    db.prepare('DELETE FROM oauth_login_states WHERE expires_at <= ?').bind(now),
    db
      .prepare(
        `INSERT INTO oauth_login_states
           (state_hash, code_verifier, return_to, created_at, expires_at)
         VALUES (?, ?, ?, ?, ?)`,
      )
      .bind(stateHash, verifier, returnTo, now, now + OAUTH_DURATION_SECONDS * 1000),
  ]);
  if (result.some(entry => !entry.success)) {
    throw new Error('D1 could not create the Discord sign-in state.');
  }
  const authorize = new URL('/oauth2/authorize', DISCORD_API_ORIGIN);
  authorize.search = new URLSearchParams({
    response_type: 'code',
    client_id: configuration.clientId,
    scope: 'identify',
    redirect_uri: configuration.redirectUri,
    state,
    code_challenge: await pkceChallenge(verifier),
    code_challenge_method: 'S256',
  }).toString();
  return new Response(null, {
    status: 302,
    headers: {
      'Cache-Control': 'no-store',
      Location: authorize.href,
      'Set-Cookie': oauthCookie(state, OAUTH_DURATION_SECONDS),
    },
  });
}

interface OAuthStateRow {
  code_verifier: string;
  return_to: string;
  expires_at: number;
}

interface DiscordTokenResponse {
  access_token?: unknown;
  token_type?: unknown;
}

interface DiscordUserResponse {
  id?: unknown;
  global_name?: unknown;
  username?: unknown;
}

async function finishDiscordSignIn(
  request: Request,
  runtime: DatasetRuntime,
  db: D1Database,
  url: URL,
): Promise<Response> {
  if ([...url.searchParams.keys()].some(key => !['code', 'state'].includes(key))) {
    return noStoreJson({error: 'Discord callback has unsupported parameters.'}, 400);
  }
  const configuration = oauthConfiguration(runtime, url);
  const state = url.searchParams.get('state');
  const code = url.searchParams.get('code');
  const cookieState = cookieValue(request, OAUTH_COOKIE_NAME);
  if (
    !state ||
    !code ||
    !cookieState ||
    state !== cookieState ||
    !/^[A-Za-z0-9_-]{40,128}$/u.test(state) ||
    code.length > 1024
  ) {
    console.warn('Discord sign-in callback failed state validation.');
    return noStoreJson({error: 'Discord sign-in could not be verified.'}, 400);
  }
  const stateHash = await sha256Hex(new TextEncoder().encode(state));
  const stateRow = await db
    .prepare(
      `SELECT code_verifier, return_to, expires_at
       FROM oauth_login_states
       WHERE state_hash = ? AND expires_at > ?
       LIMIT 1`,
    )
    .bind(stateHash, Date.now())
    .first<OAuthStateRow>();
  if (!stateRow) {
    console.warn('Discord sign-in callback used an expired or unknown state.');
    return noStoreJson({error: 'Discord sign-in expired. Please try again.'}, 400);
  }
  if (
    !/^[A-Za-z0-9_-]{40,128}$/u.test(stateRow.code_verifier) ||
    !Number.isSafeInteger(stateRow.expires_at) ||
    stateRow.expires_at <= Date.now()
  ) {
    throw new Error('Discord sign-in state storage contains invalid data.');
  }
  const returnTo = safeReturnTo(stateRow.return_to);
  const consumed = await db
    .prepare('DELETE FROM oauth_login_states WHERE state_hash = ?')
    .bind(stateHash)
    .run();
  if (!consumed.success) throw new Error('D1 could not consume the Discord sign-in state.');

  const tokenResponse = await fetch(`${DISCORD_API_ORIGIN}/api/oauth2/token`, {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json'},
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: configuration.clientId,
      client_secret: configuration.clientSecret,
      code,
      redirect_uri: configuration.redirectUri,
      code_verifier: stateRow.code_verifier,
    }),
  });
  if (!tokenResponse.ok) {
    console.error('Discord token exchange failed.', {status: tokenResponse.status});
    return noStoreJson({error: 'Discord sign-in could not be completed.'}, 502);
  }
  const token = (await tokenResponse.json()) as DiscordTokenResponse;
  if (
    typeof token.access_token !== 'string' ||
    token.access_token.length < 20 ||
    token.token_type !== 'Bearer'
  ) {
    throw new Error('Discord token response has an invalid shape.');
  }
  const profileResponse = await fetch(`${DISCORD_API_ORIGIN}/api/users/@me`, {
    headers: {Authorization: `Bearer ${token.access_token}`, Accept: 'application/json'},
  });
  if (!profileResponse.ok) {
    console.error('Discord profile request failed.', {status: profileResponse.status});
    return noStoreJson({error: 'Discord profile could not be loaded.'}, 502);
  }
  const profile = (await profileResponse.json()) as DiscordUserResponse;
  const displayName = typeof profile.global_name === 'string' && profile.global_name.trim()
    ? profile.global_name.trim()
    : typeof profile.username === 'string'
      ? profile.username.trim()
      : '';
  if (
    typeof profile.id !== 'string' ||
    !/^\d{5,32}$/u.test(profile.id) ||
    !displayName ||
    displayName.length > 80 ||
    /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u.test(displayName)
  ) {
    throw new Error('Discord profile response has invalid identity fields.');
  }
  const now = Date.now();
  const proposedUserId = crypto.randomUUID();
  const upsert = await db
    .prepare(
      `INSERT INTO users
         (id, provider, provider_user_id, display_name, created_at, updated_at)
       VALUES (?, 'discord', ?, ?, ?, ?)
       ON CONFLICT (provider, provider_user_id)
       DO UPDATE SET display_name = excluded.display_name, updated_at = excluded.updated_at`,
    )
    .bind(proposedUserId, profile.id, displayName, now, now)
    .run();
  if (!upsert.success) throw new Error('D1 could not save the Discord user profile.');
  const user = await db
    .prepare(
      `SELECT id, display_name FROM users
       WHERE provider = 'discord' AND provider_user_id = ? LIMIT 1`,
    )
    .bind(profile.id)
    .first<{id: string; display_name: string}>();
  if (!user?.id || !user.display_name) throw new Error('Saved Discord user could not be read back.');

  const sessionToken = randomToken(48);
  const sessionHash = await sha256Hex(new TextEncoder().encode(sessionToken));
  const sessionWrite = await db.batch([
    db.prepare('DELETE FROM user_sessions WHERE expires_at <= ?').bind(now),
    db
      .prepare(
        `INSERT INTO user_sessions (token_hash, user_id, created_at, expires_at)
         VALUES (?, ?, ?, ?)`,
      )
      .bind(sessionHash, user.id, now, now + SESSION_DURATION_SECONDS * 1000),
  ]);
  if (sessionWrite.some(result => !result.success)) {
    throw new Error('D1 could not create the user session.');
  }
  return new Response(null, {
    status: 302,
    headers: [
      ['Cache-Control', 'no-store'],
      ['Location', returnTo],
      ['Set-Cookie', oauthCookie('', 0)],
      ['Set-Cookie', sessionCookie(sessionToken, SESSION_DURATION_SECONDS)],
    ],
  });
}

async function sessionResponse(request: Request, db: D1Database): Promise<Response> {
  const user = await currentUser(request, db);
  return noStoreJson({user: user ? {displayName: user.displayName} : null});
}

async function signOut(request: Request, db: D1Database, url: URL): Promise<Response> {
  if (!sameOriginWrite(request, url)) {
    console.warn('A cross-origin account sign-out was refused.', {origin: request.headers.get('origin')});
    return noStoreJson({error: 'Cross-origin account updates are not allowed.'}, 403);
  }
  const token = cookieValue(request, SESSION_COOKIE_NAME);
  if (token) {
    const tokenHash = await sha256Hex(new TextEncoder().encode(token));
    const result = await db
      .prepare('DELETE FROM user_sessions WHERE token_hash = ?')
      .bind(tokenHash)
      .run();
    if (!result.success) throw new Error('D1 could not remove the user session.');
  }
  return new Response(`${JSON.stringify({signedOut: true})}\n`, {
    status: 200,
    headers: {
      'Cache-Control': 'no-store',
      'Content-Type': 'application/json; charset=utf-8',
      'Set-Cookie': sessionCookie('', 0),
      'X-Content-Type-Options': 'nosniff',
    },
  });
}

export async function handleUserAccounts(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
): Promise<Response> {
  const db = runtime.DB;
  if (!db) return noStoreJson({error: 'User accounts are unavailable.'}, 503);
  try {
    await ensureUserAccountSchema(db);
    if (url.pathname === `${AUTH_ROUTE_PREFIX}session`) {
      if (request.method !== 'GET') return methodNotAllowed('GET');
      return await sessionResponse(request, db);
    }
    if (url.pathname === `${AUTH_ROUTE_PREFIX}discord/start`) {
      if (request.method !== 'GET') return methodNotAllowed('GET');
      return await startDiscordSignIn(request, runtime, db, url);
    }
    if (url.pathname === `${AUTH_ROUTE_PREFIX}discord/callback`) {
      if (request.method !== 'GET') return methodNotAllowed('GET');
      return await finishDiscordSignIn(request, runtime, db, url);
    }
    if (url.pathname === `${AUTH_ROUTE_PREFIX}signout`) {
      if (request.method !== 'POST') return methodNotAllowed('POST');
      return await signOut(request, db, url);
    }
    return noStoreJson({error: 'Not found.'}, 404);
  } catch (error) {
    console.error('User-account request failed.', {path: url.pathname, error});
    return noStoreJson({error: 'User accounts are unavailable.'}, 503);
  }
}
