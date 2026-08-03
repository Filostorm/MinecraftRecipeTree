const SAFE_TEXT_PATTERN = /^[^\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]+$/u;
const MINECRAFT_VERSION_PATTERN = /^[0-9A-Za-z][0-9A-Za-z.+_-]{0,39}$/u;

export const MAX_EXPORT_MANIFEST_BYTES = 256 * 1024;
export const MAX_EXPORT_ARCHIVE_ENTRIES = 1_000_000;

export type LocalPackCountKey =
  | 'items'
  | 'recipes'
  | 'categories'
  | 'mobs'
  | 'blockDrops'
  | 'failures';

export interface LocalPackManifestSummary {
  readonly packName: string;
  readonly packVersion: string | null;
  readonly identitySource: string | null;
  readonly minecraftVersion: string;
  readonly generatedAt: string | null;
  readonly counts: Readonly<Record<LocalPackCountKey, number>>;
  readonly warningEvents: number;
  readonly readyForHandoff: boolean;
  readonly findings: readonly string[];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function boundedText(value: unknown, label: string, maximum: number): string {
  if (
    typeof value !== 'string' ||
    value.length === 0 ||
    [...value].length > maximum ||
    value.trim() !== value ||
    !SAFE_TEXT_PATTERN.test(value)
  ) {
    throw new Error(
      `${label} must be trimmed, non-empty, at most ${maximum} characters, and contain no control characters.`,
    );
  }
  return value;
}

function optionalBoundedText(
  value: unknown,
  label: string,
  maximum: number,
): string | null {
  return value === undefined ? null : boundedText(value, label, maximum);
}

function nonNegativeInteger(value: unknown, label: string): number {
  if (!Number.isSafeInteger(value) || Number(value) < 0) {
    throw new Error(`${label} must be a non-negative safe integer.`);
  }
  return Number(value);
}

function optionalNonNegativeInteger(value: unknown, label: string): number {
  return value === undefined ? 0 : nonNegativeInteger(value, label);
}

export function isExportManifestPath(path: string): boolean {
  return path === 'manifest.json' || /^[^/]+\/manifest\.json$/u.test(path);
}

function unsafeArchivePath(path: string, reason: string): never {
  throw new Error(
    `The ZIP entry ${JSON.stringify(path)} cannot be opened safely: ${reason}.`,
  );
}

export function requireSafeArchivePath(path: string): string {
  if (path.length === 0) unsafeArchivePath(path, 'its name is empty');
  if (path.length > 1024) unsafeArchivePath(path, 'its name exceeds 1,024 characters');
  if (path.startsWith('/')) unsafeArchivePath(path, 'it uses an absolute path');
  if (path.includes('\\')) unsafeArchivePath(path, 'it uses a Windows path separator');
  if (path.includes('\u0000')) unsafeArchivePath(path, 'it contains a null character');

  const directory = path.endsWith('/');
  const pathWithoutDirectoryMarker = directory ? path.slice(0, -1) : path;
  const segments = pathWithoutDirectoryMarker.split('/');
  if (segments.some(segment => segment === '..')) {
    unsafeArchivePath(path, 'it tries to leave the export folder');
  }
  if (segments.some(segment => segment.length === 0)) {
    unsafeArchivePath(path, 'it contains an empty folder name');
  }

  // Finder, Explorer, and command-line ZIP tools can add harmless `.` segments even when the
  // selected export folder is valid. Canonicalize those segments while retaining the traversal,
  // absolute-path, and duplicate-entry protections applied to the normalized path.
  const normalized = segments.filter(segment => segment !== '.').join('/');
  if (normalized.length === 0) {
    if (directory) return '';
    unsafeArchivePath(path, 'it does not name a file or folder');
  }
  return directory ? `${normalized}/` : normalized;
}

export function requireLocalPackManifest(value: unknown): LocalPackManifestSummary {
  if (!isRecord(value)) throw new Error('manifest.json must contain a JSON object.');
  if (value.format !== 1) throw new Error('manifest.json format must be 1.');
  if (typeof value.aborted !== 'boolean') {
    throw new Error('manifest.json aborted must be true or false.');
  }

  const pack = value.pack;
  if (!isRecord(pack)) throw new Error('manifest.json pack must contain an object.');
  const packName = boundedText(pack.name, 'manifest.json pack.name', 120);
  const packVersion = optionalBoundedText(pack.version, 'manifest.json pack.version', 80);
  const identitySource = optionalBoundedText(
    pack.identitySource,
    'manifest.json pack.identitySource',
    40,
  );

  const minecraftVersion = boundedText(value.minecraft, 'manifest.json minecraft', 40);
  if (!MINECRAFT_VERSION_PATTERN.test(minecraftVersion)) {
    throw new Error('manifest.json minecraft is not a canonical Minecraft version.');
  }

  const generatedAt = optionalBoundedText(
    value.generatedAt,
    'manifest.json generatedAt',
    80,
  );
  if (generatedAt !== null && Number.isNaN(Date.parse(generatedAt))) {
    throw new Error('manifest.json generatedAt must be a valid timestamp.');
  }

  const countsValue = value.counts;
  if (!isRecord(countsValue)) {
    throw new Error('manifest.json counts must contain an object.');
  }
  const counts = Object.freeze({
    items: nonNegativeInteger(countsValue.items, 'manifest.json counts.items'),
    recipes: nonNegativeInteger(countsValue.recipes, 'manifest.json counts.recipes'),
    categories: nonNegativeInteger(
      countsValue.categories,
      'manifest.json counts.categories',
    ),
    mobs: nonNegativeInteger(countsValue.mobs, 'manifest.json counts.mobs'),
    blockDrops: nonNegativeInteger(
      countsValue.blockDrops,
      'manifest.json counts.blockDrops',
    ),
    failures: nonNegativeInteger(countsValue.failures, 'manifest.json counts.failures'),
  });

  const diagnostics = value.diagnostics;
  if (diagnostics !== undefined && !isRecord(diagnostics)) {
    throw new Error('manifest.json diagnostics must contain an object when present.');
  }
  const warningEvents = optionalNonNegativeInteger(
    diagnostics?.warningEvents,
    'manifest.json diagnostics.warningEvents',
  );

  const findings: string[] = [];
  if (value.aborted) {
    findings.push('The export stopped before it finished. Run it again and upload the new ZIP.');
  }
  if (value.qualitySample !== undefined) {
    findings.push('This ZIP only contains a small test. Run a full export and upload that ZIP.');
  }
  if (packVersion === null) {
    findings.push('The pack version is missing. Open the pack through CurseForge and export it again.');
  }
  if (identitySource === 'game-directory') {
    findings.push('The pack name could not be confirmed. Open it through CurseForge and export it again.');
  }
  if (counts.failures > 0) {
    findings.push(
      `The exporter recorded ${counts.failures.toLocaleString()} issue${
        counts.failures === 1 ? '' : 's'
      }. The rest of the pack can still be opened, and the failure report will be sent automatically.`,
    );
  }
  if (warningEvents > 0) {
    findings.push(
      `${warningEvents.toLocaleString()} warning${
        warningEvents === 1 ? '' : 's'
      } appeared while exporting. Some recipes may be missing.`,
    );
  }

  return Object.freeze({
    packName,
    packVersion,
    identitySource,
    minecraftVersion,
    generatedAt,
    counts,
    warningEvents,
    readyForHandoff:
      !value.aborted &&
      value.qualitySample === undefined &&
      packVersion !== null &&
      identitySource !== 'game-directory',
    findings: Object.freeze(findings),
  });
}
