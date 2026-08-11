import {
  methodNotAllowed,
  noStoreJson,
  tokensEqual,
  type DatasetRuntime,
} from './datasetRuntime.ts';

export const FEEDBACK_ROUTE = '/api/feedback';

const MAX_REQUEST_BYTES = 16 * 1024;
const MAX_TITLE_LENGTH = 120;
const MAX_MESSAGE_LENGTH = 2000;
const MAX_CONTACT_LENGTH = 254;
const MAX_CONTEXT_LENGTH = 160;
const MAX_PAGE_LENGTH = 512;
const RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000;
const RATE_LIMIT_MAX_SUBMISSIONS = 3;
const PRODUCTION_ORIGINS = new Set([
  'https://minecraftrecipetree.craftsmannsoftware.com',
  'https://minecraft-recipe-tree.gtjoe51.chatgpt.site',
]);

const GITHUB_ISSUES_API = 'https://api.github.com/repos/Filostorm/MinecraftRecipeTree/issues';
const DIAGNOSTIC_KEYS = [
  'packVersion',
  'minecraftVersion',
  'publicationId',
  'previewAssetSetId',
  'exportGeneratedAt',
  'exportFormat',
  'itemCount',
  'recipeCount',
  'categoryCount',
  'modCount',
  'activeTab',
  'openItemKey',
  'graphRootKey',
  'graphDirection',
  'interfaceZoom',
  'platform',
  'userAgent',
  'viewport',
  'language',
  'online',
] as const;
const OPTIONAL_DIAGNOSTIC_KEYS = ['contentZoom'] as const;

type FeedbackKind = 'bug' | 'feedback' | 'feature';
type SubmittedFeedbackKind = 'bug' | 'feedback';
type GitHubFetch = typeof fetch;

type FeedbackDiagnostics = Record<(typeof DIAGNOSTIC_KEYS)[number], string> &
  Partial<Record<(typeof OPTIONAL_DIAGNOSTIC_KEYS)[number], string>>;

interface FeedbackPayload {
  kind?: unknown;
  title?: unknown;
  message?: unknown;
  contact?: unknown;
  packSlug?: unknown;
  packName?: unknown;
  page?: unknown;
  website?: unknown;
  diagnostics?: unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function parseDiagnostics(value: unknown): FeedbackDiagnostics | null {
  if (!isRecord(value)) return null;
  const keys = Object.keys(value).sort();
  const required = new Set<string>(DIAGNOSTIC_KEYS);
  const allowed = new Set<string>([
    ...DIAGNOSTIC_KEYS,
    ...OPTIONAL_DIAGNOSTIC_KEYS,
  ]);
  if (
    DIAGNOSTIC_KEYS.some(key => !Object.hasOwn(value, key)) ||
    keys.some(key => !allowed.has(key)) ||
    keys.length < required.size
  ) {
    return null;
  }
  const diagnostics = {} as FeedbackDiagnostics;
  for (const key of DIAGNOSTIC_KEYS) {
    const maximum = key === 'userAgent' ? 512 : key.endsWith('Id') ? 128 : 200;
    const parsed = textField(value[key], maximum);
    if (parsed === null) return null;
    diagnostics[key] = parsed;
  }
  for (const key of OPTIONAL_DIAGNOSTIC_KEYS) {
    if (!Object.hasOwn(value, key)) continue;
    const parsed = textField(value[key], 200);
    if (parsed === null) return null;
    diagnostics[key] = parsed;
  }
  return diagnostics;
}

function inlineCode(value: string): string {
  return `\`${value.replace(/`/gu, 'ˋ')}\``;
}

function issueBody(
  id: string,
  kind: SubmittedFeedbackKind,
  message: string,
  packSlug: string,
  packName: string,
  page: string,
  diagnostics: FeedbackDiagnostics,
  userAgent: string,
): string {
  return [
    `<!-- mrt-user-report:${id} -->`,
    kind === 'bug' ? '## What happened?' : '## Feedback',
    message,
    '',
    '## Diagnostics',
    `- Modpack: ${inlineCode(packName || 'Unavailable')} (${inlineCode(packSlug || 'Unavailable')})`,
    `- Pack version: ${inlineCode(diagnostics.packVersion || 'Unavailable')}`,
    `- Minecraft: ${inlineCode(diagnostics.minecraftVersion || 'Unavailable')}`,
    `- Dataset publication: ${inlineCode(diagnostics.publicationId || 'Unavailable')}`,
    `- Preview set: ${inlineCode(diagnostics.previewAssetSetId || 'Unavailable')}`,
    `- Export generated: ${inlineCode(diagnostics.exportGeneratedAt || 'Unavailable')}`,
    `- Export format: ${inlineCode(diagnostics.exportFormat || 'Unavailable')}`,
    `- Catalog: ${inlineCode(`${diagnostics.itemCount || '?'} items · ${diagnostics.recipeCount || '?'} recipes · ${diagnostics.categoryCount || '?'} categories · ${diagnostics.modCount || '?'} mods`)}`,
    `- Active view: ${inlineCode(`${diagnostics.activeTab || 'Unavailable'} · ${diagnostics.graphDirection || 'Unavailable'}`)}`,
    `- Open item: ${inlineCode(diagnostics.openItemKey || 'None')}`,
    `- Graph root: ${inlineCode(diagnostics.graphRootKey || 'None')}`,
    `- Page: ${inlineCode(page || 'Unavailable')}`,
    `- Interface zoom: ${inlineCode(diagnostics.interfaceZoom || 'Unavailable')}`,
    `- Recipe/items zoom: ${inlineCode(diagnostics.contentZoom || 'Unavailable')}`,
    `- Platform: ${inlineCode(diagnostics.platform || 'Unavailable')}`,
    `- Viewport: ${inlineCode(diagnostics.viewport || 'Unavailable')}`,
    `- Language: ${inlineCode(diagnostics.language || 'Unavailable')}`,
    `- Online: ${inlineCode(diagnostics.online || 'Unavailable')}`,
    `- Browser: ${inlineCode(userAgent || diagnostics.userAgent || 'Unavailable')}`,
  ].join('\n');
}

async function publishGitHubIssue(
  id: string,
  kind: SubmittedFeedbackKind,
  title: string,
  message: string,
  packSlug: string,
  packName: string,
  page: string,
  diagnostics: FeedbackDiagnostics,
  userAgent: string,
  token: string,
  githubFetch: GitHubFetch,
): Promise<string> {
  const response = await githubFetch(GITHUB_ISSUES_API, {
    method: 'POST',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'User-Agent': 'Minecraft-Recipe-Tree-Feedback',
      'X-GitHub-Api-Version': '2022-11-28',
    },
    body: JSON.stringify({
      title: `${kind === 'bug' ? '[Bug]' : '[Feedback]'} ${title}`.slice(0, 240),
      body: issueBody(
        id,
        kind,
        message,
        packSlug,
        packName,
        page,
        diagnostics,
        userAgent,
      ),
      labels: [kind === 'bug' ? 'bug' : 'enhancement'],
    }),
  });
  if (!response.ok) {
    throw new Error(`GitHub issue creation failed with status ${response.status}.`);
  }
  const value = await response.json() as {html_url?: unknown};
  if (typeof value.html_url !== 'string' || !value.html_url.startsWith('https://github.com/')) {
    throw new Error('GitHub issue creation returned an invalid issue URL.');
  }
  return value.html_url;
}

interface FeedbackReportRecord {
  id: string;
  kind: FeedbackKind;
  title: string;
  message: string;
  contact: string | null;
  pack_slug: string | null;
  pack_name: string | null;
  page_url: string | null;
  user_agent: string | null;
  created_at: number;
}

function textField(value: unknown, maximumLength: number): string | null {
  if (typeof value !== 'string') return null;
  const normalized = value.trim();
  if (normalized.length > maximumLength) return null;
  return normalized;
}

function clientAddress(request: Request, requestUrl: URL): string | null {
  const connectingIp = request.headers.get('cf-connecting-ip')?.trim();
  if (connectingIp) return connectingIp;
  const forwardedIp = request.headers.get('x-forwarded-for')?.split(',')[0]?.trim();
  if (forwardedIp) return forwardedIp;
  if (requestUrl.hostname === 'localhost' || requestUrl.hostname === '127.0.0.1') {
    return 'local-development';
  }
  return null;
}

async function clientFingerprint(address: string, userAgent: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(`${address}\n${userAgent}`),
  );
  return Array.from(new Uint8Array(digest), byte =>
    byte.toString(16).padStart(2, '0'),
  ).join('');
}

function isSameOrigin(request: Request, requestUrl: URL): boolean {
  const origin = request.headers.get('origin');
  return origin === requestUrl.origin || (origin !== null && PRODUCTION_ORIGINS.has(origin));
}

async function authorizeFeedbackInbox(
  request: Request,
  configuredToken: string | undefined,
): Promise<Response | null> {
  if (
    !configuredToken ||
    configuredToken.length < 32 ||
    new TextEncoder().encode(configuredToken).byteLength > 8192 ||
    /[\s\u0000-\u001f\u007f]/.test(configuredToken)
  ) {
    console.error('Feedback inbox access is disabled because FEEDBACK_ADMIN_TOKEN is misconfigured.');
    return noStoreJson({error: 'Feedback inbox access is unavailable.'}, 503);
  }
  const authorization = request.headers.get('authorization');
  const candidate = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (
    !candidate ||
    candidate.length > 8192 ||
    /[\s\u0000-\u001f\u007f]/.test(candidate) ||
    !(await tokensEqual(candidate, configuredToken))
  ) {
    console.warn('A feedback inbox request failed bearer-token authentication.', {
      path: new URL(request.url).pathname,
    });
    return new Response(`${JSON.stringify({error: 'Feedback inbox authentication failed.'})}\n`, {
      status: 401,
      headers: {
        'Cache-Control': 'no-store',
        'Content-Type': 'application/json; charset=utf-8',
        'WWW-Authenticate': 'Bearer realm="feedback-inbox"',
        'X-Content-Type-Options': 'nosniff',
      },
    });
  }
  return null;
}

async function listFeedback(
  request: Request,
  runtime: DatasetRuntime,
): Promise<Response> {
  const authorizationFailure = await authorizeFeedbackInbox(
    request,
    runtime.FEEDBACK_ADMIN_TOKEN,
  );
  if (authorizationFailure) return authorizationFailure;
  if (!runtime.DB) {
    console.error('Feedback reports cannot be read because the DB binding is unavailable.');
    return noStoreJson({error: 'Feedback storage is unavailable.'}, 503);
  }
  try {
    const result = await runtime.DB
      .prepare(
        `SELECT id, kind, title, message, contact, pack_slug, pack_name, page_url, user_agent, created_at
         FROM feedback_reports
         ORDER BY created_at DESC
         LIMIT 200`,
      )
      .all<FeedbackReportRecord>();
    if (!result.success) throw new Error('D1 reported an unsuccessful feedback query.');
    const reports = (result.results ?? []).map(report => ({
      id: report.id,
      kind: report.kind,
      title: report.title,
      message: report.message,
      contact: report.contact,
      packSlug: report.pack_slug,
      packName: report.pack_name,
      page: report.page_url,
      userAgent: report.user_agent,
      createdAt: report.created_at,
    }));
    console.log('Feedback inbox reports loaded.', {count: reports.length});
    return noStoreJson({reports});
  } catch (error) {
    console.error('Feedback reports could not be read.', error);
    return noStoreJson({error: 'Feedback storage is unavailable.'}, 503);
  }
}

export async function handleFeedback(
  request: Request,
  runtime: DatasetRuntime,
  requestUrl: URL,
  githubFetch: GitHubFetch = fetch,
): Promise<Response> {
  if (request.method === 'GET') return listFeedback(request, runtime);
  if (request.method !== 'POST') return methodNotAllowed('GET, POST');
  if (!isSameOrigin(request, requestUrl)) {
    console.warn('A cross-origin feedback submission was refused.', {
      origin: request.headers.get('origin'),
    });
    return noStoreJson({error: 'Feedback must be submitted from Recipe Tree.'}, 403);
  }
  if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) {
    return noStoreJson({error: 'Feedback must use JSON.'}, 415);
  }
  const declaredLength = Number(request.headers.get('content-length') ?? 0);
  if (Number.isFinite(declaredLength) && declaredLength > MAX_REQUEST_BYTES) {
    return noStoreJson({error: 'Feedback is too large.'}, 413);
  }

  const rawBody = await request.text();
  if (new TextEncoder().encode(rawBody).byteLength > MAX_REQUEST_BYTES) {
    return noStoreJson({error: 'Feedback is too large.'}, 413);
  }

  let payload: FeedbackPayload;
  try {
    payload = JSON.parse(rawBody) as FeedbackPayload;
  } catch (error) {
    console.warn('A feedback submission contained invalid JSON.', {error});
    return noStoreJson({error: 'Feedback could not be read.'}, 400);
  }

  const website = textField(payload.website, 200);
  if (website === null) return noStoreJson({error: 'Feedback could not be read.'}, 400);
  if (website) {
    console.warn('A feedback honeypot field was populated.');
    return noStoreJson({submitted: true}, 202);
  }

  const kind: SubmittedFeedbackKind | null = payload.kind === 'bug'
    ? 'bug'
    : payload.kind === 'feedback' || payload.kind === 'feature'
      ? 'feedback'
      : null;
  const title = textField(payload.title, MAX_TITLE_LENGTH);
  const message = textField(payload.message, MAX_MESSAGE_LENGTH);
  const contact = textField(payload.contact, MAX_CONTACT_LENGTH);
  const packSlug = textField(payload.packSlug, MAX_CONTEXT_LENGTH);
  const packName = textField(payload.packName, MAX_CONTEXT_LENGTH);
  const page = textField(payload.page, MAX_PAGE_LENGTH);
  const diagnostics = parseDiagnostics(payload.diagnostics);
  if (
    !kind ||
    title === null ||
    title.length < 3 ||
    message === null ||
    message.length < 10 ||
    contact === null ||
    packSlug === null ||
    packName === null ||
    page === null ||
    diagnostics === null ||
    (contact && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact)) ||
    (page && !page.startsWith('/'))
  ) {
    return noStoreJson({error: 'Check the feedback fields and try again.'}, 400);
  }

  const db = runtime.DB;
  if (!db) {
    console.error('Feedback cannot be stored because the DB binding is unavailable.');
    return noStoreJson({error: 'Feedback storage is unavailable.'}, 503);
  }
  const address = clientAddress(request, requestUrl);
  if (!address) {
    console.error('Feedback cannot be rate limited because the client address is unavailable.');
    return noStoreJson({error: 'Feedback storage is unavailable.'}, 503);
  }

  const userAgent = (request.headers.get('user-agent') ?? '').slice(0, 512);
  const fingerprint = await clientFingerprint(address, userAgent);
  const createdAt = Date.now();
  const token = runtime.GITHUB_ISSUES_TOKEN;
  if (!token || token.length < 32 || /[\s\u0000-\u001f\u007f]/u.test(token)) {
    console.error('Feedback issue reporting is disabled because GITHUB_ISSUES_TOKEN is unset or invalid.');
    return noStoreJson({error: 'GitHub issue reporting is unavailable.'}, 503);
  }
  let id = '';
  try {
    const recent = await db
      .prepare(
        `SELECT COUNT(*) AS count
         FROM feedback_reports
         WHERE fingerprint_hash = ? AND created_at >= ?`,
      )
      .bind(fingerprint, createdAt - RATE_LIMIT_WINDOW_MS)
      .first<{count: number}>();
    if ((recent?.count ?? 0) >= RATE_LIMIT_MAX_SUBMISSIONS) {
      console.warn('A feedback client reached the submission cooldown.', {fingerprint});
      return new Response(
        `${JSON.stringify({error: 'Please wait before sending more feedback.'})}\n`,
        {
          status: 429,
          headers: {
            'Cache-Control': 'no-store',
            'Content-Type': 'application/json; charset=utf-8',
            'Retry-After': String(Math.ceil(RATE_LIMIT_WINDOW_MS / 1000)),
            'X-Content-Type-Options': 'nosniff',
          },
        },
      );
    }

    id = crypto.randomUUID();
    const result = await db
      .prepare(
        `INSERT INTO feedback_reports
          (id, kind, title, message, contact, pack_slug, pack_name, page_url, user_agent,
           fingerprint_hash, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .bind(
        id,
        kind,
        title,
        message,
        contact || null,
        packSlug || null,
        packName || null,
        page || null,
        userAgent || null,
        fingerprint,
        createdAt,
      )
      .run();
    if (!result.success) throw new Error('D1 reported an unsuccessful feedback insert.');
    console.log('Feedback submission stored.', {id, kind, packSlug: packSlug || null});
  } catch (error) {
    console.error('Feedback submission could not be stored.', error);
    return noStoreJson({error: 'Feedback storage is unavailable.'}, 503);
  }

  try {
    const issueUrl = await publishGitHubIssue(
      id,
      kind,
      title,
      message,
      packSlug,
      packName,
      page,
      diagnostics,
      userAgent,
      token,
      githubFetch,
    );
    console.log('Feedback submission published to GitHub.', {id, kind, issueUrl});
    return noStoreJson({submitted: true, issueUrl}, 201);
  } catch (error) {
    console.error('Feedback submission could not be published to GitHub.', {id, kind, error});
    try {
      await db.prepare('DELETE FROM feedback_reports WHERE id = ?').bind(id).run();
    } catch (cleanupError) {
      console.error('A failed GitHub feedback reservation could not be removed.', {id, cleanupError});
    }
    return noStoreJson({error: 'GitHub issue reporting failed. Please try again.'}, 502);
  }
}
