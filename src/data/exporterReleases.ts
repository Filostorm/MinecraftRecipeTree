export const EXPORTER_RELEASE_MANIFEST_PATH = '/exporters/manifest.json';
export const EXPORTER_RELEASE_MANIFEST_FORMAT = 'mrt-exporter-releases-v2';

const TOP_LEVEL_KEYS = ['format', 'generatedAt', 'releases'] as const;
const RELEASE_KEYS = [
  'id',
  'minecraftVersion',
  'recipeViewer',
  'loader',
  'version',
  'filename',
  'sha256',
  'bytes',
  'qualityProfiles',
  'compatibility',
] as const;
const RELEASE_ID_PATTERN = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/;
const RELEASE_FILENAME_PATTERN = /^[a-z0-9][a-z0-9.-]{0,198}\.jar$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const UNSAFE_TEXT_PATTERN =
  /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

export interface ExporterRelease {
  readonly id: string;
  readonly minecraftVersion: string;
  readonly recipeViewer: string;
  readonly loader: string;
  readonly version: string;
  readonly filename: string;
  readonly sha256: string;
  readonly bytes: number;
  readonly qualityProfiles: readonly string[];
  readonly compatibility: string;
}

export interface ExporterReleaseManifest {
  readonly format: typeof EXPORTER_RELEASE_MANIFEST_FORMAT;
  readonly generatedAt: string;
  readonly releases: readonly ExporterRelease[];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, expected: readonly string[]): boolean {
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  return (
    actual.length === required.length &&
    actual.every((key, index) => key === required[index])
  );
}

function isBoundedSafeText(value: unknown, maximumCodePoints: number): value is string {
  if (typeof value !== 'string' || value.trim() !== value || UNSAFE_TEXT_PATTERN.test(value)) {
    return false;
  }
  const codePoints = [...value].length;
  return codePoints > 0 && codePoints <= maximumCodePoints;
}

function isCanonicalIsoTimestamp(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  try {
    return new Date(value).toISOString() === value;
  } catch {
    return false;
  }
}

function requireRelease(value: unknown, index: number): ExporterRelease {
  if (!isRecord(value) || !hasExactKeys(value, RELEASE_KEYS)) {
    throw new Error(`Exporter release ${index} must satisfy the exact release contract.`);
  }
  if (
    !isBoundedSafeText(value.id, 80) ||
    !RELEASE_ID_PATTERN.test(value.id) ||
    !isBoundedSafeText(value.minecraftVersion, 40) ||
    !isBoundedSafeText(value.recipeViewer, 80) ||
    !isBoundedSafeText(value.loader, 80) ||
    !isBoundedSafeText(value.version, 80) ||
    !isBoundedSafeText(value.compatibility, 320)
  ) {
    throw new Error(`Exporter release ${index} contains invalid or unbounded identity text.`);
  }
  if (
    typeof value.filename !== 'string' ||
    !RELEASE_FILENAME_PATTERN.test(value.filename) ||
    /(?:-dev|-sources)\.jar$/i.test(value.filename)
  ) {
    throw new Error(`Exporter release ${index} has an unsafe release JAR filename.`);
  }
  if (typeof value.sha256 !== 'string' || !SHA256_PATTERN.test(value.sha256)) {
    throw new Error(`Exporter release ${index} sha256 must be lowercase 64-hex SHA-256.`);
  }
  if (!Number.isSafeInteger(value.bytes) || (value.bytes as number) <= 0) {
    throw new Error(`Exporter release ${index} bytes must be a positive safe integer.`);
  }
  if (
    !Array.isArray(value.qualityProfiles) ||
    value.qualityProfiles.length < 1 ||
    value.qualityProfiles.length > 16
  ) {
    throw new Error(`Exporter release ${index} must declare between 1 and 16 quality profiles.`);
  }
  const qualityProfiles = value.qualityProfiles.map((profile, profileIndex) => {
    if (
      !isBoundedSafeText(profile, 80) ||
      !RELEASE_ID_PATTERN.test(profile)
    ) {
      throw new Error(
        `Exporter release ${index} quality profile ${profileIndex} is not a canonical profile ID.`,
      );
    }
    return profile;
  });
  if (new Set(qualityProfiles).size !== qualityProfiles.length) {
    throw new Error(`Exporter release ${index} repeats a quality profile.`);
  }

  return Object.freeze({
    id: value.id,
    minecraftVersion: value.minecraftVersion,
    recipeViewer: value.recipeViewer,
    loader: value.loader,
    version: value.version,
    filename: value.filename,
    sha256: value.sha256,
    bytes: value.bytes as number,
    qualityProfiles: Object.freeze(qualityProfiles),
    compatibility: value.compatibility,
  });
}

/**
 * Validates the complete public release index before any field can influence a download link.
 * Unknown fields and malformed URLs are rejected so contract drift is visible rather than
 * becoming an implicit or cross-origin fallback.
 */
export function requireExporterReleaseManifest(value: unknown): ExporterReleaseManifest {
  if (!isRecord(value) || !hasExactKeys(value, TOP_LEVEL_KEYS)) {
    throw new Error('Exporter release manifest must satisfy the exact top-level contract.');
  }
  if (value.format !== EXPORTER_RELEASE_MANIFEST_FORMAT) {
    throw new Error(
      `Exporter release manifest format must be ${EXPORTER_RELEASE_MANIFEST_FORMAT}.`,
    );
  }
  if (!isCanonicalIsoTimestamp(value.generatedAt)) {
    throw new Error('Exporter release manifest generatedAt must be a canonical ISO timestamp.');
  }
  if (!Array.isArray(value.releases) || value.releases.length < 1 || value.releases.length > 16) {
    throw new Error('Exporter release manifest must contain between 1 and 16 releases.');
  }

  const releases = value.releases.map(requireRelease);
  const uniqueFields = [
    ['id', releases.map(release => release.id)],
    ['filename', releases.map(release => release.filename)],
  ] as const;
  for (const [field, values] of uniqueFields) {
    if (new Set(values).size !== values.length) {
      throw new Error(`Exporter release manifest repeats ${field}.`);
    }
  }

  return Object.freeze({
    format: EXPORTER_RELEASE_MANIFEST_FORMAT,
    generatedAt: value.generatedAt,
    releases: Object.freeze(releases),
  });
}
