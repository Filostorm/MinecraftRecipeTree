import {
  methodNotAllowed,
  noStoreJson,
  type D1Database,
  type DatasetRuntime,
} from './datasetRuntime.ts';
import {GITHUB_REPOSITORY} from '../src/components/githubIssues.ts';

export const EXPORT_FAILURE_ROUTE = '/api/export-failures';

const REPORT_FORMAT = 'mrt-export-failure-report-v1';
const GITHUB_API = `https://api.github.com/repos/${GITHUB_REPOSITORY}`;
const GITHUB_API_VERSION = '2022-11-28';
const MAX_REQUEST_BYTES = 8 * 1024 * 1024;
const MAX_FAILURES = 20_000;
const MAX_FAILURE_TEXT = 16_000;
const COMMENT_TARGET_BYTES = 48_000;
const RATE_LIMIT_WINDOW_MS = 24 * 60 * 60 * 1000;
const RATE_LIMIT_REPORTS = 5;
const PENDING_REPORT_TIMEOUT_MS = 10 * 60 * 1000;
const ACCEPTED_ORIGINS = new Set([
  'https://minecraftrecipetree.craftsmannsoftware.com',
  'https://minecraft-recipe-tree.gtjoe51.chatgpt.site',
  'https://minecraft-recipe-tree-beta.gtjoe51.chatgpt.site',
]);
const initializedDatabases = new WeakMap<object, Promise<void>>();

function ensureExportFailureSchema(db: D1Database): Promise<void> {
  const cached = initializedDatabases.get(db as object);
  if (cached) return cached;
  const operation = db.batch([
    db.prepare(
      `CREATE TABLE IF NOT EXISTS export_failure_reports (
        fingerprint text PRIMARY KEY NOT NULL,
        issue_number integer,
        issue_url text,
        status text NOT NULL,
        client_hash text NOT NULL,
        created_at integer NOT NULL,
        updated_at integer NOT NULL
      )`,
    ),
    db.prepare(
      `CREATE INDEX IF NOT EXISTS export_failure_reports_rate_limit_idx
       ON export_failure_reports (client_hash, created_at)`,
    ),
    db.prepare(
      `CREATE INDEX IF NOT EXISTS export_failure_reports_created_at_idx
       ON export_failure_reports (created_at)`,
    ),
  ]).then(results => {
    if (results.some(result => !result.success)) {
      throw new Error('D1 reported an unsuccessful exporter failure schema statement.');
    }
  }).catch(error => {
    initializedDatabases.delete(db as object);
    throw error;
  });
  initializedDatabases.set(db as object, operation);
  return operation;
}

interface ExportFailure {
  scope: string;
  modId: string | null;
  categoryId: string | null;
  recipeId: string | null;
  recipeIndex: number | null;
  recipeClass: string | null;
  errorType: string | null;
  message: string;
  details: string | null;
}

interface ExportFailureReport {
  format: typeof REPORT_FORMAT;
  packName: string;
  packVersion: string;
  minecraftVersion: string;
  exporterId: string;
  exporterVersion: string;
  exporterBuild: string | null;
  generatedAt: string | null;
  failures: ExportFailure[];
}

interface StoredReport {
  fingerprint: string;
  issue_number: number | null;
  issue_url: string | null;
  status: string;
  updated_at: number;
}

interface GitHubIssue {
  number: number;
  html_url: string;
  body?: string | null;
}

type GitHubFetch = typeof fetch;

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function requiredText(value: unknown, maximum: number): string | null {
  if (typeof value !== 'string') return null;
  const normalized = value.trim();
  if (
    !normalized ||
    normalized.length > maximum ||
    /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(normalized)
  ) return null;
  return normalized;
}

function optionalText(value: unknown, maximum: number): string | null | undefined {
  if (value === null || value === undefined || value === '') return null;
  return requiredText(value, maximum) ?? undefined;
}

function parseFailure(value: unknown): ExportFailure | null {
  if (!isRecord(value)) return null;
  const scope = requiredText(value.scope, 40);
  const message = requiredText(value.message, MAX_FAILURE_TEXT);
  const modId = optionalText(value.modId, 120);
  const categoryId = optionalText(value.categoryId, 240);
  const recipeId = optionalText(value.recipeId, 300);
  const recipeClass = optionalText(value.recipeClass, 400);
  const errorType = optionalText(value.errorType, 400);
  const details = optionalText(value.details, MAX_FAILURE_TEXT);
  const recipeIndex = value.recipeIndex === null || value.recipeIndex === undefined
    ? null
    : Number.isSafeInteger(value.recipeIndex) && Number(value.recipeIndex) >= 0
      ? Number(value.recipeIndex)
      : undefined;
  if (
    !scope || !message || modId === undefined || categoryId === undefined ||
    recipeId === undefined || recipeClass === undefined || errorType === undefined ||
    details === undefined || recipeIndex === undefined
  ) return null;
  return {scope, modId, categoryId, recipeId, recipeIndex, recipeClass, errorType, message, details};
}

function parseReport(value: unknown): ExportFailureReport | null {
  if (!isRecord(value) || value.format !== REPORT_FORMAT || !Array.isArray(value.failures)) {
    return null;
  }
  if (value.failures.length === 0 || value.failures.length > MAX_FAILURES) return null;
  const packName = requiredText(value.packName, 120);
  const packVersion = requiredText(value.packVersion, 80);
  const minecraftVersion = requiredText(value.minecraftVersion, 40);
  const exporterId = requiredText(value.exporterId, 80);
  const exporterVersion = requiredText(value.exporterVersion, 80);
  const exporterBuild = optionalText(value.exporterBuild, 128);
  const generatedAt = optionalText(value.generatedAt, 80);
  const failures = value.failures.map(parseFailure);
  if (
    !packName || !packVersion || !minecraftVersion || !exporterId || !exporterVersion ||
    exporterBuild === undefined || generatedAt === undefined || failures.some(failure => !failure)
  ) return null;
  const unique = new Map<string, ExportFailure>();
  for (const failure of failures as ExportFailure[]) {
    const canonical = JSON.stringify(failure);
    if (!unique.has(canonical)) unique.set(canonical, failure);
  }
  return {
    format: REPORT_FORMAT,
    packName,
    packVersion,
    minecraftVersion,
    exporterId,
    exporterVersion,
    exporterBuild,
    generatedAt,
    failures: [...unique.values()].sort((left, right) =>
      JSON.stringify(left).localeCompare(JSON.stringify(right))),
  };
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

async function reportFingerprint(report: ExportFailureReport): Promise<string> {
  return sha256(JSON.stringify({
    packName: report.packName,
    packVersion: report.packVersion,
    minecraftVersion: report.minecraftVersion,
    exporterId: report.exporterId,
    exporterVersion: report.exporterVersion,
    exporterBuild: report.exporterBuild,
    failures: report.failures,
  }));
}

function clientAddress(request: Request, url: URL): string | null {
  const connectingIp = request.headers.get('cf-connecting-ip')?.trim();
  if (connectingIp) return connectingIp;
  const forwardedIp = request.headers.get('x-forwarded-for')?.split(',')[0]?.trim();
  if (forwardedIp) return forwardedIp;
  return url.hostname === 'localhost' || url.hostname === '127.0.0.1'
    ? 'local-development'
    : null;
}

function githubHeaders(token: string): HeadersInit {
  return {
    Accept: 'application/vnd.github+json',
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    'User-Agent': 'minecraft-recipe-tree-exporter-reporter',
    'X-GitHub-Api-Version': GITHUB_API_VERSION,
  };
}

async function githubJson<T>(
  githubFetch: GitHubFetch,
  token: string,
  url: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await githubFetch(url, {
    ...init,
    headers: {...githubHeaders(token), ...init.headers},
  });
  if (!response.ok) {
    const diagnostic = (await response.text()).slice(0, 1000);
    throw new Error(`GitHub returned HTTP ${response.status}: ${diagnostic}`);
  }
  return response.json() as Promise<T>;
}

function issueMarker(fingerprint: string): string {
  return `<!-- mrt-export-failure:${fingerprint} -->`;
}

function escapeFence(value: string): string {
  return value.replaceAll('```', '`\u200b``');
}

function failureMarkdown(failure: ExportFailure, index: number): string {
  const identity = failure.recipeId ??
    (failure.recipeIndex === null ? null : `recipe index ${failure.recipeIndex}`) ??
    `failure ${index + 1}`;
  const context = [
    failure.modId ? `- Mod: \`${failure.modId}\`` : null,
    failure.categoryId ? `- Category: \`${failure.categoryId}\`` : null,
    failure.recipeId ? `- Recipe: \`${failure.recipeId}\`` : null,
    failure.recipeIndex === null ? null : `- Recipe index: \`${failure.recipeIndex}\``,
    failure.recipeClass ? `- Recipe class: \`${failure.recipeClass}\`` : null,
    failure.errorType ? `- Error type: \`${failure.errorType}\`` : null,
    `- Scope: \`${failure.scope}\``,
  ].filter(Boolean).join('\n');
  const details = failure.details
    ? `\n\n<details><summary>Error details</summary>\n\n\`\`\`text\n${escapeFence(failure.details)}\n\`\`\`\n</details>`
    : '';
  return `### ${index + 1}. ${identity}\n\n${context}\n\n${failure.message}${details}`;
}

export function buildFailureCommentBodies(
  report: ExportFailureReport,
  fingerprint: string,
): string[] {
  const entries = report.failures.map(failureMarkdown);
  const groups: string[][] = [];
  let current: string[] = [];
  let currentBytes = 0;
  for (const entry of entries) {
    const entryBytes = new TextEncoder().encode(entry).byteLength;
    if (current.length > 0 && currentBytes + entryBytes + 4 > COMMENT_TARGET_BYTES) {
      groups.push(current);
      current = [];
      currentBytes = 0;
    }
    current.push(entry);
    currentBytes += entryBytes + 4;
  }
  if (current.length > 0) groups.push(current);
  return groups.map((group, index) =>
    `<!-- mrt-export-failure-part:${fingerprint}:${index + 1}/${groups.length} -->\n` +
    `## Export failures (${index + 1}/${groups.length})\n\n${group.join('\n\n---\n\n')}`,
  );
}

function issueBody(report: ExportFailureReport, fingerprint: string, parts: number): string {
  return `${issueMarker(fingerprint)}\n` +
    `An uploaded pack completed with recipe failures. The usable recipes were still loaded into Recipe Tree.\n\n` +
    `- Pack: **${report.packName}**\n` +
    `- Pack version: \`${report.packVersion}\`\n` +
    `- Minecraft: \`${report.minecraftVersion}\`\n` +
    `- Exporter: \`${report.exporterId} ${report.exporterVersion}\`\n` +
    (report.exporterBuild ? `- Exporter build: \`${report.exporterBuild}\`\n` : '') +
    (report.generatedAt ? `- Exported at: \`${report.generatedAt}\`\n` : '') +
    `- Unique failures: **${report.failures.length}**\n\n` +
    `All deduplicated failures and their error details are included in ${parts} comment${parts === 1 ? '' : 's'} below.`;
}

async function findExistingIssue(
  githubFetch: GitHubFetch,
  token: string,
  fingerprint: string,
): Promise<GitHubIssue | null> {
  const query = encodeURIComponent(`repo:${GITHUB_REPOSITORY} is:issue in:body "${issueMarker(fingerprint)}"`);
  const result = await githubJson<{items?: GitHubIssue[]}>(
    githubFetch,
    token,
    `https://api.github.com/search/issues?q=${query}&per_page=1`,
  );
  return result.items?.[0] ?? null;
}

async function publishFailureIssue(
  report: ExportFailureReport,
  fingerprint: string,
  token: string,
  githubFetch: GitHubFetch,
): Promise<{issue: GitHubIssue; duplicate: boolean}> {
  const comments = buildFailureCommentBodies(report, fingerprint);
  let issue = await findExistingIssue(githubFetch, token, fingerprint);
  const duplicate = issue !== null;
  if (!issue) {
    issue = await githubJson<GitHubIssue>(githubFetch, token, `${GITHUB_API}/issues`, {
      method: 'POST',
      body: JSON.stringify({
        title: `[Exporter] ${report.packName} ${report.packVersion}: ${report.failures.length} unique failure${report.failures.length === 1 ? '' : 's'}`,
        body: issueBody(report, fingerprint, comments.length),
      }),
    });
  }
  const existingComments: Array<{body?: string | null}> = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageComments = await githubJson<Array<{body?: string | null}>>(
      githubFetch,
      token,
      `${GITHUB_API}/issues/${issue.number}/comments?per_page=100&page=${page}`,
    );
    existingComments.push(...pageComments);
    if (pageComments.length < 100) break;
  }
  for (let index = 0; index < comments.length; index += 1) {
    const marker = `<!-- mrt-export-failure-part:${fingerprint}:${index + 1}/${comments.length} -->`;
    if (existingComments.some(comment => comment.body?.includes(marker))) continue;
    await githubJson(githubFetch, token, `${GITHUB_API}/issues/${issue.number}/comments`, {
      method: 'POST',
      body: JSON.stringify({body: comments[index]}),
    });
  }
  return {issue, duplicate};
}

function isSameOrigin(request: Request, requestUrl: URL): boolean {
  const origin = request.headers.get('origin');
  return origin === requestUrl.origin || (origin !== null && ACCEPTED_ORIGINS.has(origin));
}

export async function handleExportFailureIssue(
  request: Request,
  runtime: DatasetRuntime,
  requestUrl: URL,
  githubFetch: GitHubFetch = fetch,
): Promise<Response> {
  if (request.method !== 'POST') return methodNotAllowed('POST');
  if (!isSameOrigin(request, requestUrl)) {
    return noStoreJson({error: 'Failure reports must come from Recipe Tree.'}, 403);
  }
  if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) {
    return noStoreJson({error: 'Failure reports must use JSON.'}, 415);
  }
  const declaredLength = Number(request.headers.get('content-length') ?? 0);
  if (Number.isFinite(declaredLength) && declaredLength > MAX_REQUEST_BYTES) {
    return noStoreJson({error: 'Failure report is too large.'}, 413);
  }
  const rawBody = await request.text();
  if (new TextEncoder().encode(rawBody).byteLength > MAX_REQUEST_BYTES) {
    return noStoreJson({error: 'Failure report is too large.'}, 413);
  }
  let rawReport: unknown;
  try {
    rawReport = JSON.parse(rawBody);
  } catch {
    return noStoreJson({error: 'Failure report could not be read.'}, 400);
  }
  const report = parseReport(rawReport);
  if (!report) return noStoreJson({error: 'Failure report fields are invalid.'}, 400);
  const token = runtime.GITHUB_ISSUES_TOKEN;
  if (!token || token.length < 32 || /[\s\u0000-\u001f\u007f]/u.test(token)) {
    console.error('Exporter issue reporting is disabled because GITHUB_ISSUES_TOKEN is unset or invalid.');
    return noStoreJson({error: 'Automatic exporter issue reporting is unavailable.'}, 503);
  }
  const address = clientAddress(request, requestUrl);
  if (!address) return noStoreJson({error: 'Automatic exporter issue reporting is unavailable.'}, 503);
  const fingerprint = await reportFingerprint(report);
  const clientHash = await sha256(`${address}\n${(request.headers.get('user-agent') ?? '').slice(0, 512)}`);
  const now = Date.now();
  const db = runtime.DB;

  if (db) {
    await ensureExportFailureSchema(db);
    const stored = await db.prepare(
      `SELECT fingerprint, issue_number, issue_url, status, updated_at
       FROM export_failure_reports WHERE fingerprint = ?`,
    ).bind(fingerprint).first<StoredReport>();
    if (stored?.status === 'reported' && stored.issue_url) {
      return noStoreJson({issueUrl: stored.issue_url, duplicate: true});
    }
    if (stored) {
      if (stored.updated_at > now - PENDING_REPORT_TIMEOUT_MS) {
        return noStoreJson({error: 'This failure report is already being processed.'}, 409);
      }
      await db.prepare(
        `DELETE FROM export_failure_reports WHERE fingerprint = ? AND status = 'pending' AND updated_at = ?`,
      ).bind(fingerprint, stored.updated_at).run();
    }
    const recent = await db.prepare(
      `SELECT COUNT(*) AS count FROM export_failure_reports
       WHERE client_hash = ? AND created_at >= ?`,
    ).bind(clientHash, now - RATE_LIMIT_WINDOW_MS).first<{count: number}>();
    if ((recent?.count ?? 0) >= RATE_LIMIT_REPORTS) {
      return new Response(`${JSON.stringify({error: 'Please wait before reporting another export.'})}\n`, {
        status: 429,
        headers: {
          'Cache-Control': 'no-store',
          'Content-Type': 'application/json; charset=utf-8',
          'Retry-After': String(Math.ceil(RATE_LIMIT_WINDOW_MS / 1000)),
        },
      });
    }
    const inserted = await db.prepare(
      `INSERT OR IGNORE INTO export_failure_reports
        (fingerprint, issue_number, issue_url, status, client_hash, created_at, updated_at)
       VALUES (?, NULL, NULL, 'pending', ?, ?, ?)`,
    ).bind(fingerprint, clientHash, now, now).run();
    if ((inserted.meta?.changes ?? 0) !== 1) {
      return noStoreJson({error: 'This failure report is already being processed.'}, 409);
    }
  }

  try {
    const {issue, duplicate} = await publishFailureIssue(report, fingerprint, token, githubFetch);
    if (db) {
      await db.prepare(
        `UPDATE export_failure_reports
         SET issue_number = ?, issue_url = ?, status = 'reported', updated_at = ?
         WHERE fingerprint = ?`,
      ).bind(issue.number, issue.html_url, Date.now(), fingerprint).run();
    }
    return noStoreJson({issueUrl: issue.html_url, duplicate}, duplicate ? 200 : 201);
  } catch (error) {
    console.error('Automatic exporter GitHub issue reporting failed.', {fingerprint, error});
    if (db) {
      await db.prepare(
        `DELETE FROM export_failure_reports WHERE fingerprint = ? AND status = 'pending'`,
      ).bind(fingerprint).run().catch(cleanupError =>
        console.error('Could not clear a failed exporter report reservation.', cleanupError));
    }
    return noStoreJson({error: 'Automatic exporter issue reporting failed; the pack was still installed.'}, 502);
  }
}
