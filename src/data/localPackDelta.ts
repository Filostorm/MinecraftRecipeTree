import {
  MAX_EXPORT_ARCHIVE_ENTRIES,
  isIgnoredArchiveMetadataPath,
  requireSafeArchivePath,
} from './localPackArchive.ts';

const SHA256_PATTERN = /^[a-f0-9]{64}$/u;
const SAFE_TEXT_PATTERN = /^[^\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]+$/u;
const MINECRAFT_VERSION_PATTERN = /^[0-9A-Za-z][0-9A-Za-z.+_-]{0,39}$/u;

export const LOCAL_PACK_DELTA_FORMAT = 'mrt-export-delta-v1';
export const MAX_EXPORT_DELTA_BYTES = 32 * 1024 * 1024;

export interface LocalPackDeltaFile {
  readonly path: string;
  readonly size: number;
  readonly sha256: string;
}

export interface LocalPackDelta {
  readonly format: typeof LOCAL_PACK_DELTA_FORMAT;
  readonly createdAt: string;
  readonly basePublicationId: string;
  readonly resultPublicationId: string;
  readonly minecraftVersion: string;
  readonly packName: string;
  readonly baseVersion: string | null;
  readonly resultVersion: string | null;
  readonly files: readonly LocalPackDeltaFile[];
  readonly deletedPaths: readonly string[];
  readonly counts: Readonly<{
    changedFiles: number;
    deletedFiles: number;
    unchangedFiles: number;
    resultFiles: number;
    changedBytes: number;
    resultBytes: number;
  }>;
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
    throw new Error(`${label} is unreadable.`);
  }
  return value;
}

function optionalBoundedText(value: unknown, label: string, maximum: number): string | null {
  return value === null || value === undefined ? null : boundedText(value, label, maximum);
}

function nonNegativeInteger(value: unknown, label: string): number {
  if (!Number.isSafeInteger(value) || Number(value) < 0) {
    throw new Error(`${label} must be a non-negative safe integer.`);
  }
  return Number(value);
}

function publicationId(value: unknown, label: string): string {
  if (typeof value !== 'string' || !SHA256_PATTERN.test(value)) {
    throw new Error(`${label} must be a SHA-256 identifier.`);
  }
  return value;
}

function filePath(value: unknown, label: string): string {
  if (typeof value !== 'string') throw new Error(`${label} must be a path.`);
  const path = requireSafeArchivePath(value);
  if (
    path !== value ||
    path.endsWith('/') ||
    path === 'delta.json' ||
    isIgnoredArchiveMetadataPath(path)
  ) {
    throw new Error(`${label} must name a canonical export file.`);
  }
  return path;
}

export function requireLocalPackDelta(value: unknown): LocalPackDelta {
  if (!isRecord(value) || value.format !== LOCAL_PACK_DELTA_FORMAT) {
    throw new Error(`delta.json format must be ${LOCAL_PACK_DELTA_FORMAT}.`);
  }
  const createdAt = boundedText(value.createdAt, 'delta.json createdAt', 80);
  if (Number.isNaN(Date.parse(createdAt))) {
    throw new Error('delta.json createdAt must be a valid timestamp.');
  }
  const basePublicationId = publicationId(
    value.basePublicationId,
    'delta.json basePublicationId',
  );
  const resultPublicationId = publicationId(
    value.resultPublicationId,
    'delta.json resultPublicationId',
  );
  if (basePublicationId === resultPublicationId) {
    throw new Error('delta.json must update a different export snapshot.');
  }
  const minecraftVersion = boundedText(value.minecraft, 'delta.json minecraft', 40);
  if (!MINECRAFT_VERSION_PATTERN.test(minecraftVersion)) {
    throw new Error('delta.json minecraft is not a canonical Minecraft version.');
  }
  if (!isRecord(value.pack)) throw new Error('delta.json pack must contain an object.');
  const packName = boundedText(value.pack.name, 'delta.json pack.name', 120);
  const baseVersion = optionalBoundedText(
    value.pack.baseVersion,
    'delta.json pack.baseVersion',
    80,
  );
  const resultVersion = optionalBoundedText(
    value.pack.resultVersion,
    'delta.json pack.resultVersion',
    80,
  );

  if (!Array.isArray(value.files) || value.files.length > MAX_EXPORT_ARCHIVE_ENTRIES) {
    throw new Error('delta.json files must contain a bounded array.');
  }
  const seenFiles = new Set<string>();
  const files = value.files.map((entry, index): LocalPackDeltaFile => {
    if (!isRecord(entry)) throw new Error(`delta.json files[${index}] must be an object.`);
    const path = filePath(entry.path, `delta.json files[${index}].path`);
    if (seenFiles.has(path)) throw new Error(`delta.json lists ${path} more than once.`);
    seenFiles.add(path);
    return Object.freeze({
      path,
      size: nonNegativeInteger(entry.size, `delta.json files[${index}].size`),
      sha256: publicationId(entry.sha256, `delta.json files[${index}].sha256`),
    });
  });
  const manifestFile = files.find(file => file.path === 'manifest.json');
  if (!manifestFile || manifestFile.sha256 !== resultPublicationId) {
    throw new Error('delta.json must include the result manifest and its SHA-256 identifier.');
  }

  if (
    !Array.isArray(value.deletedPaths) ||
    value.deletedPaths.length > MAX_EXPORT_ARCHIVE_ENTRIES
  ) {
    throw new Error('delta.json deletedPaths must contain a bounded array.');
  }
  const deletedSet = new Set<string>();
  const deletedPaths = value.deletedPaths.map((entry, index) => {
    const path = filePath(entry, `delta.json deletedPaths[${index}]`);
    if (deletedSet.has(path)) throw new Error(`delta.json deletes ${path} more than once.`);
    if (seenFiles.has(path)) throw new Error(`delta.json both changes and deletes ${path}.`);
    deletedSet.add(path);
    return path;
  });

  if (!isRecord(value.counts)) throw new Error('delta.json counts must contain an object.');
  const counts = Object.freeze({
    changedFiles: nonNegativeInteger(value.counts.changedFiles, 'delta.json counts.changedFiles'),
    deletedFiles: nonNegativeInteger(value.counts.deletedFiles, 'delta.json counts.deletedFiles'),
    unchangedFiles: nonNegativeInteger(
      value.counts.unchangedFiles,
      'delta.json counts.unchangedFiles',
    ),
    resultFiles: nonNegativeInteger(value.counts.resultFiles, 'delta.json counts.resultFiles'),
    changedBytes: nonNegativeInteger(value.counts.changedBytes, 'delta.json counts.changedBytes'),
    resultBytes: nonNegativeInteger(value.counts.resultBytes, 'delta.json counts.resultBytes'),
  });
  const summedChangedBytes = files.reduce((sum, file) => sum + file.size, 0);
  if (
    counts.changedFiles !== files.length ||
    counts.deletedFiles !== deletedPaths.length ||
    counts.resultFiles !== counts.changedFiles + counts.unchangedFiles ||
    counts.resultFiles > MAX_EXPORT_ARCHIVE_ENTRIES ||
    counts.changedBytes !== summedChangedBytes ||
    counts.resultBytes < counts.changedBytes
  ) {
    throw new Error('delta.json counts do not match its file inventory.');
  }

  return Object.freeze({
    format: LOCAL_PACK_DELTA_FORMAT,
    createdAt,
    basePublicationId,
    resultPublicationId,
    minecraftVersion,
    packName,
    baseVersion,
    resultVersion,
    files: Object.freeze(files),
    deletedPaths: Object.freeze(deletedPaths),
    counts,
  });
}
