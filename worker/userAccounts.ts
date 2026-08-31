import {createRemoteJWKSet, jwtVerify, type JWTPayload} from 'jose';
import {
  type D1Database,
  type DatasetRuntime,
  methodNotAllowed,
  noStoreJson,
  sha256Hex,
} from './datasetRuntime.ts';

export const AUTH_ROUTE_PREFIX = '/api/auth/';
const initializedDatabases = new WeakMap<object, Promise<void>>();
const jwksByOrigin = new Map<string, ReturnType<typeof createRemoteJWKSet>>();
const USER_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;
const UNSAFE_DISPLAY_NAME_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;
const AVATAR_KEY_PATTERN = /^[a-f0-9]{64}$/u;
const DISCORD_AVATAR_PATH_PATTERN = /^\/(?:avatars\/[0-9]{1,24}\/[A-Za-z0-9_-]{2,128}\.(?:png|jpe?g|webp|gif)|embed\/avatars\/[0-5]\.png)$/u;

const schemaStatements = [
  `CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY NOT NULL,
    provider TEXT NOT NULL,
    provider_user_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    avatar_key TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )`,
  `CREATE UNIQUE INDEX IF NOT EXISTS users_provider_identity_idx
   ON users (provider, provider_user_id)`,
  `CREATE UNIQUE INDEX IF NOT EXISTS users_avatar_key_idx
   ON users (avatar_key)`,
  `CREATE TABLE IF NOT EXISTS account_recipe_favorites (
    user_id TEXT NOT NULL,
    pack_slug TEXT NOT NULL,
    publication_id TEXT NOT NULL,
    item_key TEXT NOT NULL,
    recipe_category INTEGER NOT NULL,
    recipe_index INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,
  `CREATE UNIQUE INDEX IF NOT EXISTS account_recipe_favorites_user_item_idx
   ON account_recipe_favorites (user_id, pack_slug, publication_id, item_key)`,
  `CREATE INDEX IF NOT EXISTS account_recipe_favorites_ranking_idx
   ON account_recipe_favorites
     (pack_slug, publication_id, item_key, recipe_category, recipe_index)`,
  `CREATE INDEX IF NOT EXISTS account_recipe_favorites_user_leaderboard_idx
   ON account_recipe_favorites (pack_slug, publication_id, user_id)`,
  'PRAGMA optimize',
] as const;

export interface AuthenticatedUser {
  id: string;
  displayName: string;
}

interface SqliteTableRow {
  name: string;
}

export function supabaseProjectUrl(value: string | undefined): string {
  if (!value) throw new Error('SUPABASE_URL is required for account sign-in.');
  const parsed = new URL(value);
  if (
    parsed.protocol !== 'https:' ||
    parsed.username ||
    parsed.password ||
    parsed.pathname !== '/' ||
    parsed.search ||
    parsed.hash
  ) {
    throw new Error('SUPABASE_URL must be an HTTPS origin without credentials or a path.');
  }
  if (!parsed.hostname.endsWith('.supabase.co')) {
    throw new Error('SUPABASE_URL must use a hosted Supabase project origin.');
  }
  return parsed.origin;
}

function bearerToken(request: Request): string | null {
  const authorization = request.headers.get('authorization');
  if (!authorization) return null;
  const match = /^Bearer ([A-Za-z0-9._~-]{80,4096})$/u.exec(authorization);
  return match?.[1] ?? null;
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

function supabaseSecretKey(value: string | undefined): string {
  if (
    !value ||
    value.length < 32 ||
    value.length > 4096 ||
    /[\s\u0000-\u001f\u007f]/u.test(value) ||
    (!value.startsWith('sb_secret_') && !value.startsWith('eyJ'))
  ) {
    throw new Error('SUPABASE_SECRET_KEY is missing or invalid.');
  }
  return value;
}

function displayNameFor(payload: JWTPayload): string {
  const metadata = payload.user_metadata;
  const record = metadata && typeof metadata === 'object' && !Array.isArray(metadata)
    ? metadata as Record<string, unknown>
    : {};
  const candidates = [
    record.display_name,
    record.full_name,
    record.name,
    record.user_name,
    record.preferred_username,
    payload.email,
  ];
  const displayName = candidates.find(value => typeof value === 'string' && value.trim()) as
    | string
    | undefined;
  const normalized = displayName?.trim() ?? 'Recipe Tree user';
  if (normalized.length > 80 || UNSAFE_DISPLAY_NAME_PATTERN.test(normalized)) {
    throw new Error('Supabase session contains an invalid display name.');
  }
  return normalized;
}

function metadataRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

export function discordAvatarUrlFor(payload: JWTPayload): string | null {
  const appMetadata = metadataRecord(payload.app_metadata);
  const providers = Array.isArray(appMetadata.providers) ? appMetadata.providers : [];
  const isDiscord = appMetadata.provider === 'discord' || providers.includes('discord');
  if (!isDiscord) return null;
  const userMetadata = metadataRecord(payload.user_metadata);
  const candidate = [userMetadata.avatar_url, userMetadata.picture]
    .find(value => typeof value === 'string' && value.trim()) as string | undefined;
  if (!candidate) return null;
  try {
    const parsed = new URL(candidate);
    if (
      parsed.protocol !== 'https:' ||
      parsed.hostname !== 'cdn.discordapp.com' ||
      parsed.port ||
      parsed.username ||
      parsed.password ||
      !DISCORD_AVATAR_PATH_PATTERN.test(parsed.pathname)
    ) {
      console.warn('Discord supplied an avatar URL outside the allowed CDN path.');
      return null;
    }
    return `https://cdn.discordapp.com${parsed.pathname}`;
  } catch {
    console.warn('Discord supplied an invalid avatar URL.');
    return null;
  }
}

function projectJwks(projectUrl: string) {
  const cached = jwksByOrigin.get(projectUrl);
  if (cached) return cached;
  const jwks = createRemoteJWKSet(
    new URL(`${projectUrl}/auth/v1/.well-known/jwks.json`),
    {cooldownDuration: 30_000, cacheMaxAge: 10 * 60 * 1000},
  );
  jwksByOrigin.set(projectUrl, jwks);
  return jwks;
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
  runtime: DatasetRuntime,
): Promise<AuthenticatedUser | null> {
  const token = bearerToken(request);
  if (!token) return null;
  const db = runtime.DB;
  if (!db) throw new Error('D1 is required to save authenticated users.');
  const projectUrl = supabaseProjectUrl(runtime.SUPABASE_URL);
  let payload: JWTPayload;
  try {
    ({payload} = await jwtVerify(token, projectJwks(projectUrl), {
      issuer: `${projectUrl}/auth/v1`,
      audience: 'authenticated',
      algorithms: ['ES256', 'RS256'],
    }));
  } catch (error) {
    console.warn('A Supabase access token failed verification.', error);
    return null;
  }
  if (
    typeof payload.sub !== 'string' ||
    !USER_ID_PATTERN.test(payload.sub) ||
    payload.role !== 'authenticated'
  ) {
    console.warn('A verified Supabase token did not contain an authenticated user identity.');
    return null;
  }
  const displayName = displayNameFor(payload);
  const avatarUrl = discordAvatarUrlFor(payload);
  const avatarKey = avatarUrl
    ? await sha256Hex(new TextEncoder().encode(avatarUrl))
    : null;
  const now = Date.now();
  const saved = await db
    .prepare(
      `INSERT INTO users
         (id, provider, provider_user_id, display_name, avatar_url, avatar_key, created_at, updated_at)
       VALUES (?, 'supabase', ?, ?, ?, ?, ?, ?)
       ON CONFLICT (id)
       DO UPDATE SET display_name = excluded.display_name,
                     avatar_url = excluded.avatar_url,
                     avatar_key = excluded.avatar_key,
                     updated_at = excluded.updated_at
       WHERE users.display_name <> excluded.display_name
          OR users.avatar_url IS NOT excluded.avatar_url
          OR users.avatar_key IS NOT excluded.avatar_key`,
    )
    .bind(payload.sub, payload.sub, displayName, avatarUrl, avatarKey, now, now)
    .run();
  if (!saved.success) throw new Error('D1 could not save the Supabase user profile.');
  return {id: payload.sub, displayName};
}

async function avatarResponse(
  request: Request,
  db: D1Database,
  url: URL,
): Promise<Response> {
  if (request.method !== 'GET') return methodNotAllowed('GET');
  const avatarKey = url.pathname.slice(`${AUTH_ROUTE_PREFIX}avatar/`.length);
  if (!AVATAR_KEY_PATTERN.test(avatarKey)) return noStoreJson({error: 'Not found.'}, 404);
  const row = await db
    .prepare('SELECT avatar_url FROM users WHERE avatar_key = ? LIMIT 1')
    .bind(avatarKey)
    .first<{avatar_url: string}>();
  if (!row?.avatar_url) return noStoreJson({error: 'Not found.'}, 404);
  const verifiedUrl = discordAvatarUrlFor({
    app_metadata: {provider: 'discord'},
    user_metadata: {avatar_url: row.avatar_url},
  });
  if (!verifiedUrl) {
    console.error('A stored Discord avatar URL failed validation.', {avatarKey});
    return noStoreJson({error: 'Avatar unavailable.'}, 502);
  }
  const upstream = await fetch(verifiedUrl, {
    headers: {Accept: 'image/avif,image/webp,image/png,image/jpeg,image/gif'},
  });
  if (!upstream.ok || !upstream.body) {
    console.warn('Discord avatar delivery failed.', {avatarKey, status: upstream.status});
    return noStoreJson({error: 'Avatar unavailable.'}, 502);
  }
  const contentType = upstream.headers.get('content-type')?.split(';', 1)[0]?.trim() ?? '';
  if (!/^image\/(?:avif|gif|jpeg|png|webp)$/u.test(contentType)) {
    console.error('Discord returned an unexpected avatar content type.', {avatarKey, contentType});
    return noStoreJson({error: 'Avatar unavailable.'}, 502);
  }
  return new Response(upstream.body, {
    headers: {
      'Cache-Control': 'public, max-age=31536000, immutable',
      'Content-Type': contentType,
      'X-Content-Type-Options': 'nosniff',
    },
  });
}

async function sessionResponse(request: Request, runtime: DatasetRuntime): Promise<Response> {
  const user = await currentUser(request, runtime);
  return noStoreJson({user: user ? {displayName: user.displayName} : null});
}

export async function deleteSupabaseAuthUser(
  runtime: DatasetRuntime,
  userId: string,
  fetcher: typeof fetch = fetch,
): Promise<void> {
  const projectUrl = supabaseProjectUrl(runtime.SUPABASE_URL);
  const secret = supabaseSecretKey(runtime.SUPABASE_SECRET_KEY);
  const response = await fetcher(`${projectUrl}/auth/v1/admin/users/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
    headers: {
      apikey: secret,
      Authorization: `Bearer ${secret}`,
    },
  });
  if (!response.ok) {
    console.error('Supabase refused an authenticated account deletion.', {
      status: response.status,
      userId,
    });
    throw new Error('Supabase could not delete the account.');
  }
}

async function deleteAccount(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
  db: D1Database,
): Promise<Response> {
  if (!requestOriginAllowed(request, url)) {
    console.warn('A cross-origin account deletion was refused.', {
      origin: request.headers.get('origin'),
    });
    return noStoreJson({error: 'Cross-origin account deletion is not allowed.'}, 403);
  }
  const user = await currentUser(request, runtime);
  if (!user) return noStoreJson({error: 'Your account session is invalid or expired.'}, 401);
  await deleteSupabaseAuthUser(runtime, user.id);

  const donationTable = await db.prepare(
    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'donation_contributions' LIMIT 1",
  ).first<SqliteTableRow>();
  const statements = [
    db.prepare('DELETE FROM account_recipe_favorites WHERE user_id = ?').bind(user.id),
    db.prepare('DELETE FROM users WHERE id = ?').bind(user.id),
  ];
  if (donationTable?.name === 'donation_contributions') {
    statements.push(
      db.prepare(
        `UPDATE donation_contributions
         SET donor_key = ?, public_name = NULL, updated_at = ?
         WHERE donor_key = ?`,
      ).bind(`deleted:${crypto.randomUUID()}`, Date.now(), `user:${user.id}`),
    );
  }
  const results = await db.batch(statements);
  if (results.some(result => !result.success)) {
    console.error('Supabase deleted an account, but D1 cleanup did not complete.', {userId: user.id});
    throw new Error('Account identity cleanup did not complete.');
  }
  return noStoreJson({deleted: true});
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
    if (url.pathname.startsWith(`${AUTH_ROUTE_PREFIX}avatar/`)) {
      return await avatarResponse(request, db, url);
    }
    if (url.pathname === `${AUTH_ROUTE_PREFIX}session`) {
      if (request.method !== 'GET') return methodNotAllowed('GET');
      return await sessionResponse(request, runtime);
    }
    if (url.pathname === `${AUTH_ROUTE_PREFIX}account`) {
      if (request.method !== 'DELETE') return methodNotAllowed('DELETE');
      return await deleteAccount(request, runtime, url, db);
    }
    return noStoreJson({error: 'Not found.'}, 404);
  } catch (error) {
    console.error('User-account request failed.', {path: url.pathname, error});
    return noStoreJson({error: 'User accounts are unavailable.'}, 503);
  }
}
