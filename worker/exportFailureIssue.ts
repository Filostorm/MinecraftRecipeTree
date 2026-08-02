import {
  methodNotAllowed,
  noStoreJson,
  type D1Database,
  type DatasetRuntime,
} from './datasetRuntime.ts';
import {GITHUB_REPOSITORY} from '../src/components/githubIssues.ts';

export const EXPORT_FAILURE_ROUTE = '/api/export-failures';

const REPORT_FORMAT = 'mrt-export-failure-report-v1';
const REPORT_FILE_FORMAT = 'mrt-export-failure-file-v1';
const REPORT_BRANCH = 'export-failure-reports';
const GITHUB_API = `https://api.github.com/repos/${GITHUB_REPOSITORY}`;
const GITHUB_API_VERSION = '2022-11-28';
const MAX_REQUEST_BYTES = 8 * 1024 * 1024;
const MAX_FAILURES = 20_000;
const MAX_FAILURE_TEXT = 16_000;
const RATE_LIMIT_WINDOW_MS = 24 * 60 * 60 * 1000;
const RATE_LIMIT_REPORTS = 5;
const PENDING_REPORT_TIMEOUT_MS = 10 * 60 * 1000;
const UNKNOWN_VERSION = /^(?:unknown|missing|unspecified|n\/a|null)$/iu;
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
  modVersions: Record<string, string>;
  failures: ExportFailure[];
}

type ReportDedupeIdentity =
  | {
      kind: 'pack-version';
      packName: string;
      packVersion: string;
      minecraftVersion: string;
    }
  | {
      kind: 'mod-versions';
      minecraftVersion: string;
      mods: Record<string, string>;
    }
  | {
      kind: 'exporter-build';
      packName: string;
      minecraftVersion: string;
      exporterId: string;
      exporterVersion: string;
      exporterBuild: string | null;
    };

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

interface GitHubFile {
  htmlUrl: string;
  downloadUrl: string;
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

function parseModVersions(
  value: unknown,
  failures: readonly ExportFailure[],
): Record<string, string> | null {
  if (value !== undefined && !isRecord(value)) return null;
  const source = isRecord(value) ? value : {};
  const modIds = [...new Set(
    failures.map(failure => failure.modId).filter((modId): modId is string => modId !== null),
  )].sort();
  const versions: Record<string, string> = {};
  for (const modId of modIds) {
    const candidate = source[modId];
    if (candidate === undefined) {
      versions[modId] = 'Unknown';
      continue;
    }
    const version = requiredText(candidate, 120);
    if (!version) return null;
    versions[modId] = version;
  }
  return versions;
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
  const uniqueFailures = [...unique.values()].sort((left, right) =>
    JSON.stringify(left).localeCompare(JSON.stringify(right)));
  const modVersions = parseModVersions(value.modVersions, uniqueFailures);
  if (!modVersions) return null;
  return {
    format: REPORT_FORMAT,
    packName,
    packVersion,
    minecraftVersion,
    exporterId,
    exporterVersion,
    exporterBuild,
    generatedAt,
    modVersions,
    failures: uniqueFailures,
  };
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

export function reportDedupeIdentity(report: ExportFailureReport): ReportDedupeIdentity {
  if (!UNKNOWN_VERSION.test(report.packVersion)) {
    return {
      kind: 'pack-version',
      packName: report.packName,
      packVersion: report.packVersion,
      minecraftVersion: report.minecraftVersion,
    };
  }
  if (Object.keys(report.modVersions).length > 0) {
    return {
      kind: 'mod-versions',
      minecraftVersion: report.minecraftVersion,
      mods: Object.fromEntries(Object.entries(report.modVersions).sort(([left], [right]) =>
        left.localeCompare(right))),
    };
  }
  return {
    kind: 'exporter-build',
    packName: report.packName,
    minecraftVersion: report.minecraftVersion,
    exporterId: report.exporterId,
    exporterVersion: report.exporterVersion,
    exporterBuild: report.exporterBuild,
  };
}

async function reportFingerprint(report: ExportFailureReport): Promise<string> {
  return sha256(JSON.stringify(reportDedupeIdentity(report)));
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

async function githubRequest(
  githubFetch: GitHubFetch,
  token: string,
  url: string,
  init: RequestInit = {},
): Promise<Response> {
  return githubFetch(url, {
    ...init,
    headers: {...githubHeaders(token), ...init.headers},
  });
}

async function githubError(response: Response): Promise<Error> {
  const diagnostic = (await response.text()).slice(0, 1000);
  return new Error(`GitHub returned HTTP ${response.status}: ${diagnostic}`);
}

async function githubJson<T>(
  githubFetch: GitHubFetch,
  token: string,
  url: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await githubRequest(githubFetch, token, url, init);
  if (!response.ok) throw await githubError(response);
  return response.json() as Promise<T>;
}

function issueMarker(fingerprint: string): string {
  return `<!-- mrt-export-failure:${fingerprint} -->`;
}

function reportFilePath(fingerprint: string): string {
  return `export-failure-reports/${fingerprint}/errors.json`;
}

function encodePath(path: string): string {
  return path.split('/').map(segment => encodeURIComponent(segment)).join('/');
}

function singleLine(value: string): string {
  return value.replaceAll(/[\r\n\t]+/gu, ' ').replaceAll(/\s{2,}/gu, ' ').trim();
}

function inlineCode(value: string): string {
  return `\`${singleLine(value).replaceAll('`', 'ˋ')}\``;
}

function toBase64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  const chunkBytes = 32 * 1024;
  for (let offset = 0; offset < bytes.byteLength; offset += chunkBytes) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkBytes));
  }
  return btoa(binary);
}

async function gitBlobSha(value: string): Promise<string> {
  const content = new TextEncoder().encode(value);
  const header = new TextEncoder().encode(`blob ${content.byteLength}\0`);
  const input = new Uint8Array(header.byteLength + content.byteLength);
  input.set(header);
  input.set(content, header.byteLength);
  const digest = await crypto.subtle.digest('SHA-1', input);
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

async function ensureReportBranch(
  githubFetch: GitHubFetch,
  token: string,
): Promise<void> {
  const reportRefUrl = `${GITHUB_API}/git/ref/heads/${REPORT_BRANCH}`;
  const existing = await githubRequest(githubFetch, token, reportRefUrl);
  if (existing.ok) return;
  if (existing.status !== 404) throw await githubError(existing);

  const repository = await githubJson<{default_branch: string}>(
    githubFetch,
    token,
    GITHUB_API,
  );
  const baseRef = await githubJson<{object: {sha: string}}>(
    githubFetch,
    token,
    `${GITHUB_API}/git/ref/heads/${encodeURIComponent(repository.default_branch)}`,
  );
  const created = await githubRequest(githubFetch, token, `${GITHUB_API}/git/refs`, {
    method: 'POST',
    body: JSON.stringify({
      ref: `refs/heads/${REPORT_BRANCH}`,
      sha: baseRef.object.sha,
    }),
  });
  if (created.ok) return;
  if (created.status === 422) {
    const raced = await githubRequest(githubFetch, token, reportRefUrl);
    if (raced.ok) return;
  }
  throw await githubError(created);
}

function reportFileBody(
  report: ExportFailureReport,
  identity: ReportDedupeIdentity,
  fingerprint: string,
): string {
  return `${JSON.stringify({
    format: REPORT_FILE_FORMAT,
    dedupeFingerprint: fingerprint,
    dedupeIdentity: identity,
    report,
  }, null, 2)}\n`;
}

async function writeReportFile(
  report: ExportFailureReport,
  identity: ReportDedupeIdentity,
  fingerprint: string,
  token: string,
  githubFetch: GitHubFetch,
): Promise<GitHubFile> {
  await ensureReportBranch(githubFetch, token);
  const path = reportFilePath(fingerprint);
  const contentUrl = `${GITHUB_API}/contents/${encodePath(path)}`;
  const body = reportFileBody(report, identity, fingerprint);
  const bodySha = await gitBlobSha(body);
  let existingSha: string | undefined;
  const existing = await githubRequest(
    githubFetch,
    token,
    `${contentUrl}?ref=${encodeURIComponent(REPORT_BRANCH)}`,
  );
  if (existing.ok) {
    const payload = await existing.json() as {
      sha?: unknown;
      html_url?: unknown;
      download_url?: unknown;
    };
    if (typeof payload.sha !== 'string') {
      throw new Error('GitHub returned an exporter report file without a blob SHA.');
    }
    existingSha = payload.sha;
    if (
      existingSha === bodySha &&
      typeof payload.html_url === 'string' &&
      typeof payload.download_url === 'string'
    ) {
      return {htmlUrl: payload.html_url, downloadUrl: payload.download_url};
    }
  } else if (existing.status !== 404) {
    throw await githubError(existing);
  }

  const write = await githubJson<{
    content?: {html_url?: string | null; download_url?: string | null};
  }>(githubFetch, token, contentUrl, {
    method: 'PUT',
    body: JSON.stringify({
      message: singleLine(
        `${existingSha ? 'Update' : 'Add'} exporter errors for ${report.packName} ${report.packVersion}`,
      ).slice(0, 240),
      content: toBase64(body),
      branch: REPORT_BRANCH,
      ...(existingSha ? {sha: existingSha} : {}),
    }),
  });
  const htmlUrl = write.content?.html_url;
  const downloadUrl = write.content?.download_url;
  if (!htmlUrl || !downloadUrl) {
    throw new Error('GitHub did not return links for the exporter errors file.');
  }
  return {htmlUrl, downloadUrl};
}

function dedupeDescription(identity: ReportDedupeIdentity): string {
  if (identity.kind === 'pack-version') {
    return `pack version ${inlineCode(`${identity.packName} ${identity.packVersion}`)}`;
  }
  if (identity.kind === 'mod-versions') {
    const mods = Object.entries(identity.mods);
    const shown = mods.slice(0, 40)
      .map(([modId, version]) => inlineCode(`${modId} ${version}`))
      .join(', ');
    return `affected mod versions ${shown}` +
      (mods.length > 40 ? ` and ${mods.length - 40} more in errors.json` : '');
  }
  return `exporter build ${inlineCode(`${identity.exporterId} ${identity.exporterVersion}`)}`;
}

function issueTitle(report: ExportFailureReport, identity: ReportDedupeIdentity): string {
  if (identity.kind === 'pack-version') {
    return singleLine(`[Exporter] ${report.packName} ${report.packVersion} export failures`).slice(0, 240);
  }
  const mods = Object.entries(report.modVersions)
    .slice(0, 4)
    .map(([modId, version]) => `${modId} ${version}`)
    .join(', ');
  return singleLine(`[Exporter] ${mods || report.packName} export failures`).slice(0, 240);
}

function issueBody(
  report: ExportFailureReport,
  identity: ReportDedupeIdentity,
  fingerprint: string,
  file: GitHubFile,
): string {
  const affectedMods = Object.entries(report.modVersions);
  const affectedModSummary = affectedMods.slice(0, 40)
    .map(([id, version]) => inlineCode(`${id} ${version}`))
    .join(', ') + (affectedMods.length > 40
      ? ` and ${affectedMods.length - 40} more in errors.json`
      : '');
  return `${issueMarker(fingerprint)}\n` +
    `An uploaded pack completed with recipe failures. The usable recipes were still loaded into Recipe Tree.\n\n` +
    `- Pack: ${inlineCode(report.packName)}\n` +
    `- Pack version: ${inlineCode(report.packVersion)}\n` +
    `- Minecraft: ${inlineCode(report.minecraftVersion)}\n` +
    `- Exporter: ${inlineCode(`${report.exporterId} ${report.exporterVersion}`)}\n` +
    (report.exporterBuild ? `- Exporter build: ${inlineCode(report.exporterBuild)}\n` : '') +
    (report.generatedAt ? `- Exported at: ${inlineCode(report.generatedAt)}\n` : '') +
    `- Unique failures in latest report: **${report.failures.length}**\n` +
    (affectedMods.length > 0
      ? `- Affected mods: ${affectedModSummary}\n`
      : '') +
    `- Deduplicated by: ${dedupeDescription(identity)}\n\n` +
    `## Errors file\n\n` +
    `[Download errors.json](${file.downloadUrl}) · [View file history](${file.htmlUrl})\n\n` +
    `Repeating this report for the same dedupe identity updates the file and this issue instead of adding comments.`;
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
  identity: ReportDedupeIdentity,
  fingerprint: string,
  token: string,
  githubFetch: GitHubFetch,
  issueHint: GitHubIssue | null,
): Promise<{issue: GitHubIssue; file: GitHubFile; duplicate: boolean}> {
  const file = await writeReportFile(report, identity, fingerprint, token, githubFetch);
  let issue = issueHint ?? await findExistingIssue(githubFetch, token, fingerprint);
  const duplicate = issue !== null;
  const title = issueTitle(report, identity);
  const body = issueBody(report, identity, fingerprint, file);
  if (!issue) {
    issue = await githubJson<GitHubIssue>(githubFetch, token, `${GITHUB_API}/issues`, {
      method: 'POST',
      body: JSON.stringify({title, body}),
    });
  } else {
    issue = await githubJson<GitHubIssue>(
      githubFetch,
      token,
      `${GITHUB_API}/issues/${issue.number}`,
      {method: 'PATCH', body: JSON.stringify({title, body, state: 'open'})},
    );
  }
  return {issue, file, duplicate};
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
  const identity = reportDedupeIdentity(report);
  const fingerprint = await reportFingerprint(report);
  const clientHash = await sha256(`${address}\n${(request.headers.get('user-agent') ?? '').slice(0, 512)}`);
  const now = Date.now();
  const db = runtime.DB;
  let stored: StoredReport | null = null;
  let restoreReportedReservation = false;

  if (db) {
    await ensureExportFailureSchema(db);
    stored = await db.prepare(
      `SELECT fingerprint, issue_number, issue_url, status, updated_at
       FROM export_failure_reports WHERE fingerprint = ?`,
    ).bind(fingerprint).first<StoredReport>();
    if (stored?.status === 'pending' && stored.updated_at > now - PENDING_REPORT_TIMEOUT_MS) {
      return noStoreJson({error: 'This failure report is already being processed.'}, 409);
    }
    if (stored?.status === 'reported') {
      restoreReportedReservation = true;
      const reserved = await db.prepare(
        `UPDATE export_failure_reports SET status = 'pending', updated_at = ?
         WHERE fingerprint = ? AND status = 'reported'`,
      ).bind(now, fingerprint).run();
      if ((reserved.meta?.changes ?? 0) !== 1) {
        return noStoreJson({error: 'This failure report is already being processed.'}, 409);
      }
    } else {
      if (stored) {
        await db.prepare(
          `DELETE FROM export_failure_reports WHERE fingerprint = ? AND status = 'pending'`,
        ).bind(fingerprint).run();
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
  }

  const issueHint = stored?.issue_number && stored.issue_url
    ? {number: stored.issue_number, html_url: stored.issue_url}
    : null;
  try {
    const {issue, file, duplicate} = await publishFailureIssue(
      report,
      identity,
      fingerprint,
      token,
      githubFetch,
      issueHint,
    );
    if (db) {
      await db.prepare(
        `UPDATE export_failure_reports
         SET issue_number = ?, issue_url = ?, status = 'reported', updated_at = ?
         WHERE fingerprint = ?`,
      ).bind(issue.number, issue.html_url, Date.now(), fingerprint).run();
    }
    return noStoreJson(
      {issueUrl: issue.html_url, fileUrl: file.downloadUrl, duplicate},
      duplicate ? 200 : 201,
    );
  } catch (error) {
    console.error('Automatic exporter GitHub issue reporting failed.', {fingerprint, error});
    if (db) {
      const cleanup = restoreReportedReservation
        ? db.prepare(
            `UPDATE export_failure_reports SET status = 'reported', updated_at = ?
             WHERE fingerprint = ? AND status = 'pending'`,
          ).bind(Date.now(), fingerprint).run()
        : db.prepare(
            `DELETE FROM export_failure_reports WHERE fingerprint = ? AND status = 'pending'`,
          ).bind(fingerprint).run();
      await cleanup.catch(cleanupError =>
        console.error('Could not clear a failed exporter report reservation.', cleanupError));
    }
    return noStoreJson({error: 'Automatic exporter issue reporting failed; the pack was still installed.'}, 502);
  }
}
