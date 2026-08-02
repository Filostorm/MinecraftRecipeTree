export const EXPORT_FAILURE_REPORT_FORMAT = 'mrt-export-failure-report-v1';
export const EXPORT_ERRORS_FILE_FORMAT = 'mrt-export-errors-v1';
export const MAX_EXPORT_FAILURES = 20_000;
export const MAX_FAILURE_TEXT_LENGTH = 16_000;

export interface ExportFailureDetail {
  readonly scope: string;
  readonly modId: string | null;
  readonly categoryId: string | null;
  readonly recipeId: string | null;
  readonly recipeIndex: number | null;
  readonly recipeClass: string | null;
  readonly errorType: string | null;
  readonly message: string;
  readonly details: string | null;
}

export interface ExportFailureReport {
  readonly format: typeof EXPORT_FAILURE_REPORT_FORMAT;
  readonly packName: string;
  readonly packVersion: string;
  readonly minecraftVersion: string;
  readonly exporterId: string;
  readonly exporterVersion: string;
  readonly exporterBuild: string | null;
  readonly generatedAt: string | null;
  readonly failures: readonly ExportFailureDetail[];
}

interface ReportInputs {
  manifest: unknown;
  failures: unknown;
  exportErrors?: unknown;
  exporterBuild?: unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function text(value: unknown, fallback: string, maximum = 256): string {
  if (typeof value !== 'string') return fallback;
  const normalized = value.trim();
  if (!normalized || normalized.length > maximum || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(normalized)) {
    return fallback;
  }
  return normalized;
}

function optionalText(value: unknown, maximum = 512): string | null {
  const normalized = text(value, '', maximum);
  return normalized || null;
}

function fallbackFailure(messageValue: unknown): ExportFailureDetail {
  const message = text(messageValue, 'Unknown exporter failure', MAX_FAILURE_TEXT_LENGTH);
  const resourceId = /(?:^|\s)([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\s|#|$)/iu.exec(message)?.[1] ?? null;
  const categoryDirectory = /recipe\s+recipes\/([a-z0-9_.-]+)_([a-z0-9_./-]+)\s+#(\d+)/iu.exec(message);
  const categoryMessage = /^(?:category\s+(?:title|recipes|icon|catalysts)|recipe\s+registry\s+id)\s+([a-z0-9_.-]+:[a-z0-9_./-]+)/iu.exec(message);
  const recipeIndexMatch = /(?:recipe[^#\n]*|sourceIndex=)#(\d+)/iu.exec(message)
    ?? /recipe\s+recipes\/[a-z0-9_.-]+_[a-z0-9_./-]+\s+#(\d+)/iu.exec(message);
  const recipeId = /^recipe\s+(?!registry\s+id)/iu.test(message) ? resourceId : null;
  const categoryId = categoryDirectory
    ? `${categoryDirectory[1]}:${categoryDirectory[2]}`
    : categoryMessage?.[1] ?? null;
  const modId = recipeId?.split(':')[0] ?? categoryId?.split(':')[0] ?? resourceId?.split(':')[0] ?? null;
  return Object.freeze({
    scope: message.toLowerCase().startsWith('recipe ') ? 'recipe' : 'export',
    modId,
    categoryId,
    recipeId,
    recipeIndex: recipeIndexMatch ? Number(recipeIndexMatch[1] ?? recipeIndexMatch[3]) : null,
    recipeClass: null,
    errorType: null,
    message,
    details: null,
  });
}

function structuredFailure(value: unknown): ExportFailureDetail | null {
  if (!isRecord(value)) return null;
  const message = text(value.message, '', MAX_FAILURE_TEXT_LENGTH);
  if (!message) return null;
  const fallback = fallbackFailure(message);
  const recipeIndex = value.recipeIndex;
  return Object.freeze({
    scope: text(value.scope, 'export', 40),
    modId: optionalText(value.modId, 120) ?? fallback.modId,
    categoryId: optionalText(value.categoryId, 240) ?? fallback.categoryId,
    recipeId: optionalText(value.recipeId, 300) ?? fallback.recipeId,
    recipeIndex: Number.isSafeInteger(recipeIndex) && Number(recipeIndex) >= 0
      ? Number(recipeIndex)
      : fallback.recipeIndex,
    recipeClass: optionalText(value.recipeClass, 400),
    errorType: optionalText(value.errorType, 400),
    message,
    details: optionalText(value.details, MAX_FAILURE_TEXT_LENGTH),
  });
}

function dedupeFailures(failures: readonly ExportFailureDetail[]): readonly ExportFailureDetail[] {
  const unique = new Map<string, ExportFailureDetail>();
  for (const failure of failures) {
    const key = JSON.stringify(failure);
    if (!unique.has(key)) unique.set(key, failure);
  }
  return Object.freeze([...unique.values()]);
}

export function buildExportFailureReport({
  manifest,
  failures,
  exportErrors,
  exporterBuild,
}: ReportInputs): ExportFailureReport {
  if (!isRecord(manifest) || !isRecord(manifest.pack)) {
    throw new Error('The exporter manifest cannot identify the failed pack.');
  }
  if (!Array.isArray(failures)) throw new Error('failures.json must contain an array.');
  if (failures.length > MAX_EXPORT_FAILURES) {
    throw new Error(`failures.json contains more than ${MAX_EXPORT_FAILURES.toLocaleString()} entries.`);
  }

  const richFailures = isRecord(exportErrors) &&
      exportErrors.format === EXPORT_ERRORS_FILE_FORMAT &&
      Array.isArray(exportErrors.failures)
    ? exportErrors.failures.map(structuredFailure).filter((failure): failure is ExportFailureDetail => failure !== null)
    : [];
  const parsedFailures = richFailures.length > 0
    ? richFailures
    : failures.map(fallbackFailure);
  const uniqueFailures = dedupeFailures(parsedFailures);
  if (uniqueFailures.length === 0) {
    throw new Error('The export does not contain reportable failures.');
  }

  const manifestExporter = isRecord(manifest.exporter) ? manifest.exporter : {};
  const richExporter = isRecord(exportErrors) && isRecord(exportErrors.exporter)
    ? exportErrors.exporter
    : {};
  const build = isRecord(exporterBuild) ? exporterBuild : {};
  return Object.freeze({
    format: EXPORT_FAILURE_REPORT_FORMAT,
    packName: text(manifest.pack.name, 'Unknown pack', 120),
    packVersion: text(manifest.pack.version, 'Unknown', 80),
    minecraftVersion: text(manifest.minecraft, 'Unknown', 40),
    exporterId: text(richExporter.id ?? manifestExporter.id ?? build.exporterId, 'unknown', 80),
    exporterVersion: text(richExporter.version ?? manifestExporter.version, 'Unknown', 80),
    exporterBuild: optionalText(build.payloadSha256, 64),
    generatedAt: optionalText(manifest.generatedAt, 80),
    failures: uniqueFailures,
  });
}

export async function sendExportFailureReport(
  report: ExportFailureReport,
): Promise<{issueUrl: string; duplicate: boolean}> {
  const response = await fetch('/api/export-failures', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(report),
  });
  const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
  if (!response.ok || typeof payload.issueUrl !== 'string') {
    throw new Error(
      typeof payload.error === 'string'
        ? payload.error
        : `Failure report request returned HTTP ${response.status}.`,
    );
  }
  return {issueUrl: payload.issueUrl, duplicate: payload.duplicate === true};
}
