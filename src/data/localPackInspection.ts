import {Unzip, UnzipInflate} from 'fflate';
import {
  isExportManifestPath,
  isIgnoredArchiveMetadataPath,
  MAX_EXPORT_ARCHIVE_ENTRIES,
  MAX_EXPORT_MANIFEST_BYTES,
  requireLocalPackManifest,
  requireSafeArchivePath,
  type LocalPackManifestSummary,
} from './localPackArchive.ts';
import {
  MAX_EXPORT_DELTA_BYTES,
  requireLocalPackDelta,
  type LocalPackDelta,
} from './localPackDelta.ts';

const ARCHIVE_READ_CHUNK_BYTES = 1024 * 1024;

export interface LocalPackArchiveFile {
  readonly name: string;
  readonly size: number;
  slice(start: number, end: number): Blob;
}

export interface InspectedLocalPack {
  manifestPath: string;
  manifestBytes: Uint8Array;
  manifest: unknown;
  summary: LocalPackManifestSummary;
  delta: LocalPackDelta | null;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function joinChunks(chunks: readonly Uint8Array[], totalBytes: number): Uint8Array {
  const combined = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    combined.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return combined;
}

function parseJsonDocument(bytes: Uint8Array, path: string): unknown {
  let source: string;
  try {
    source = new TextDecoder('utf-8', {fatal: true}).decode(bytes);
  } catch (error) {
    throw new Error(`${path} is not valid UTF-8: ${errorMessage(error)}`);
  }
  try {
    return JSON.parse(source);
  } catch (error) {
    throw new Error(`${path} is not valid JSON: ${errorMessage(error)}`);
  }
}

export async function inspectLocalPackArchive(
  file: LocalPackArchiveFile,
  onProgress: (fraction: number) => void,
): Promise<InspectedLocalPack> {
  if (file.size === 0) throw new Error('The selected ZIP file is empty.');
  if (!Number.isSafeInteger(file.size)) throw new Error('The selected file size is invalid.');

  let archiveError: Error | null = null;
  let entryCount = 0;
  let manifestPath: string | null = null;
  let manifestByteCount = 0;
  let manifestChunks: Uint8Array[] = [];
  let deltaPath: string | null = null;
  let deltaByteCount = 0;
  let deltaChunks: Uint8Array[] = [];

  const unzip = new Unzip(entry => {
    entry.ondata = error => {
      if (error) archiveError = error instanceof Error ? error : new Error(String(error));
    };
    entryCount += 1;
    if (entryCount > MAX_EXPORT_ARCHIVE_ENTRIES) {
      archiveError = new Error(
        `The ZIP contains more than ${MAX_EXPORT_ARCHIVE_ENTRIES.toLocaleString()} entries.`,
      );
      return;
    }

    let safePath: string;
    try {
      safePath = requireSafeArchivePath(entry.name);
    } catch (error) {
      archiveError = error instanceof Error ? error : new Error(String(error));
      return;
    }
    if (isIgnoredArchiveMetadataPath(safePath)) return;

    const isManifest = isExportManifestPath(safePath);
    const isDelta = /^(?:[^/]+\/)?delta\.json$/u.test(safePath);
    if (!isManifest && !isDelta) return;
    if (isManifest && manifestPath !== null) {
      archiveError = new Error(
        `The ZIP contains more than one exporter manifest (${manifestPath} and ${safePath}).`,
      );
      return;
    }
    if (isDelta && deltaPath !== null) {
      archiveError = new Error('The ZIP contains more than one delta.json.');
      return;
    }

    const maximum = isManifest ? MAX_EXPORT_MANIFEST_BYTES : MAX_EXPORT_DELTA_BYTES;
    if (entry.originalSize !== undefined && entry.originalSize > maximum) {
      archiveError = new Error(
        `${isManifest ? 'manifest.json' : 'delta.json'} exceeds the ${maximum.toLocaleString()}-byte limit.`,
      );
      return;
    }
    if (isManifest) manifestPath = safePath;
    else deltaPath = safePath;

    entry.ondata = (error, data, final) => {
      if (error) {
        archiveError = error instanceof Error ? error : new Error(String(error));
        return;
      }
      if (isManifest) {
        manifestByteCount += data.byteLength;
        if (manifestByteCount > maximum) {
          archiveError = new Error(
            `manifest.json exceeds the ${maximum.toLocaleString()}-byte limit.`,
          );
          manifestChunks = [];
          return;
        }
        manifestChunks.push(data);
        if (final && manifestByteCount === 0) archiveError = new Error('manifest.json is empty.');
      } else {
        deltaByteCount += data.byteLength;
        if (deltaByteCount > maximum) {
          archiveError = new Error(
            `delta.json exceeds the ${maximum.toLocaleString()}-byte limit.`,
          );
          deltaChunks = [];
          return;
        }
        deltaChunks.push(data);
      }
    };
    try {
      entry.start();
    } catch (error) {
      archiveError = error instanceof Error ? error : new Error(String(error));
    }
  });
  unzip.register(UnzipInflate);

  for (let offset = 0; offset < file.size; offset += ARCHIVE_READ_CHUNK_BYTES) {
    const end = Math.min(offset + ARCHIVE_READ_CHUNK_BYTES, file.size);
    const chunk = new Uint8Array(await file.slice(offset, end).arrayBuffer());
    try {
      unzip.push(chunk, end === file.size);
    } catch (error) {
      throw new Error(`The selected file is not a readable ZIP archive: ${errorMessage(error)}`);
    }
    if (archiveError !== null) throw archiveError;
    onProgress(end / file.size);
  }

  if (manifestPath === null) {
    throw new Error(
      'No exporter manifest.json was found at the ZIP root or inside one top-level folder.',
    );
  }
  if (manifestByteCount === 0) throw new Error('manifest.json is empty.');

  const manifestBytes = joinChunks(manifestChunks, manifestByteCount);
  const manifest = parseJsonDocument(manifestBytes, 'manifest.json');
  manifestChunks = [];
  const resolvedManifestPath = manifestPath as string;
  const prefix = resolvedManifestPath.includes('/')
    ? resolvedManifestPath.slice(0, resolvedManifestPath.indexOf('/') + 1)
    : '';
  let delta: LocalPackDelta | null = null;
  if (deltaPath !== null) {
    if (deltaPath !== `${prefix}delta.json`) {
      throw new Error('delta.json is outside the exporter folder.');
    }
    delta = requireLocalPackDelta(parseJsonDocument(
      joinChunks(deltaChunks, deltaByteCount),
      'delta.json',
    ));
  }
  return {
    manifestPath: resolvedManifestPath,
    manifestBytes,
    manifest,
    summary: requireLocalPackManifest(manifest),
    delta,
  };
}
