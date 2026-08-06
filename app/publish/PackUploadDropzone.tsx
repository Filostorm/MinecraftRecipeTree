'use client';

import {
  type CSSProperties,
  type ChangeEvent,
  type DragEvent,
  useRef,
  useState,
} from 'react';
import {Unzip, UnzipInflate} from 'fflate';
import {
  isExportManifestPath,
  isIgnoredArchiveMetadataPath,
  MAX_EXPORT_ARCHIVE_ENTRIES,
  MAX_EXPORT_MANIFEST_BYTES,
  requireLocalPackManifest,
  requireSafeArchivePath,
  type LocalPackManifestSummary,
} from '../../src/data/localPackArchive';
import {
  MAX_EXPORT_DELTA_BYTES,
  requireLocalPackDelta,
  type LocalPackDelta,
} from '../../src/data/localPackDelta';
import {installLocalPackArchive} from '../../src/data/localPackStorage';
import {
  buildExportFailureReport,
  sendExportFailureReport,
  type ExportFailureReport,
} from '../../src/data/exportFailureReport';
import {localPackUploadErrorMessage} from '../../src/data/localPackUploadError';
import styles from './publish.module.css';

// Keep each synchronous fflate push small enough for archives containing long
// runs of tiny entries. Larger batches can recurse through thousands of local
// file headers before returning and overflow the browser call stack.
const ARCHIVE_READ_CHUNK_BYTES = 1024 * 1024;
const MAX_FAILURE_DOCUMENT_BYTES = 16 * 1024 * 1024;
const MAX_EXPORTER_BUILD_BYTES = 16 * 1024;

const OPTIONAL_REPORT_DOCUMENT_LIMITS = new Map([
  ['failures.json', MAX_FAILURE_DOCUMENT_BYTES],
  ['export-errors.json', MAX_FAILURE_DOCUMENT_BYTES],
  ['exporter-build.json', MAX_EXPORTER_BUILD_BYTES],
  ['delta.json', MAX_EXPORT_DELTA_BYTES],
]);

type UploadState =
  | {status: 'idle'}
  | {
      status: 'checking';
      filename: string;
      progress: number;
      phase: 'checking' | 'adding' | 'saving' | 'finalizing';
      completedFiles?: number;
      totalFiles?: number;
    }
  | {
      status: 'ready';
      filename: string;
      bytes: number;
      summary: LocalPackManifestSummary;
      viewerHref: string;
      saved: boolean;
      findings: readonly string[];
      issueUrl: string | null;
      fileUrl: string | null;
      reportStatus: 'sending' | 'sent' | 'duplicate' | 'failed' | null;
      reportAvailable: boolean;
      isDelta: boolean;
    }
  | {status: 'error'; filename: string | null; message: string};

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

function reportDocumentName(path: string): string | null {
  const match = /^(?:[^/]+\/)?(failures\.json|export-errors\.json|exporter-build\.json|delta\.json)$/u.exec(path);
  return match?.[1] ?? null;
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

async function inspectPackArchive(
  file: File,
  onProgress: (fraction: number) => void,
  isCurrent: () => boolean,
): Promise<{
  manifestPath: string;
  manifestBytes: Uint8Array;
  manifest: unknown;
  summary: LocalPackManifestSummary;
  failures: unknown | null;
  exportErrors: unknown | null;
  exporterBuild: unknown | null;
  delta: LocalPackDelta | null;
}> {
  if (file.size === 0) throw new Error('The selected ZIP file is empty.');
  if (!Number.isSafeInteger(file.size)) throw new Error('The selected file size is invalid.');

  let archiveError: Error | null = null;
  let entryCount = 0;
  let manifestPath: string | null = null;
  let manifestBytes = 0;
  let manifestChunks: Uint8Array[] = [];
  const reportDocuments = new Map<string, {
    path: string;
    bytes: number;
    chunks: Uint8Array[];
    skipped: boolean;
  }>();
  let lastReportedPercent = 0;

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

    const optionalDocumentName = reportDocumentName(safePath);
    if (!isExportManifestPath(safePath) && optionalDocumentName === null) return;
    if (optionalDocumentName !== null) {
      if (reportDocuments.has(optionalDocumentName)) {
        archiveError = new Error(`The ZIP contains more than one ${optionalDocumentName}.`);
        return;
      }
      const maximum = OPTIONAL_REPORT_DOCUMENT_LIMITS.get(optionalDocumentName) ?? 0;
      const document = {
        path: safePath,
        bytes: 0,
        chunks: [] as Uint8Array[],
        skipped: entry.originalSize !== undefined && entry.originalSize > maximum,
      };
      reportDocuments.set(optionalDocumentName, document);
      entry.ondata = (error, data) => {
        if (error) {
          archiveError = error instanceof Error ? error : new Error(String(error));
          return;
        }
        if (document.skipped) return;
        document.bytes += data.byteLength;
        if (document.bytes > maximum) {
          document.skipped = true;
          document.chunks = [];
          return;
        }
        document.chunks.push(data);
      };
      try {
        entry.start();
      } catch (error) {
        archiveError = error instanceof Error ? error : new Error(String(error));
      }
      return;
    }
    if (manifestPath !== null) {
      archiveError = new Error(
        `The ZIP contains more than one exporter manifest (${manifestPath} and ${safePath}).`,
      );
      return;
    }
    if (
      entry.originalSize !== undefined &&
      entry.originalSize > MAX_EXPORT_MANIFEST_BYTES
    ) {
      archiveError = new Error(
        `manifest.json exceeds the ${MAX_EXPORT_MANIFEST_BYTES.toLocaleString()}-byte limit.`,
      );
      return;
    }

    manifestPath = safePath;
    entry.ondata = (error, data, final) => {
      if (error) {
        archiveError = error instanceof Error ? error : new Error(String(error));
        return;
      }
      manifestBytes += data.byteLength;
      if (manifestBytes > MAX_EXPORT_MANIFEST_BYTES) {
        archiveError = new Error(
          `manifest.json exceeds the ${MAX_EXPORT_MANIFEST_BYTES.toLocaleString()}-byte limit.`,
        );
        return;
      }
      manifestChunks.push(data);
      if (final && manifestBytes === 0) {
        archiveError = new Error('manifest.json is empty.');
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
    if (!isCurrent()) throw new Error('Archive check was replaced by a newer file.');
    const end = Math.min(offset + ARCHIVE_READ_CHUNK_BYTES, file.size);
    const chunk = new Uint8Array(await file.slice(offset, end).arrayBuffer());
    try {
      unzip.push(chunk, end === file.size);
    } catch (error) {
      throw new Error(`The selected file is not a readable ZIP archive: ${errorMessage(error)}`);
    }
    if (archiveError !== null) throw archiveError;
    const progress = end / file.size;
    const percent = Math.floor(progress * 100);
    if (percent > lastReportedPercent || end === file.size) {
      lastReportedPercent = percent;
      onProgress(progress);
    }
  }

  if (manifestPath === null) {
    throw new Error(
      'No exporter manifest.json was found at the ZIP root or inside one top-level folder.',
    );
  }
  if (manifestBytes === 0) throw new Error('manifest.json is empty.');

  const manifestData = joinChunks(manifestChunks, manifestBytes);
  const manifest = parseJsonDocument(manifestData, 'manifest.json');
  manifestChunks = [];
  const resolvedManifestPath = manifestPath as string;
  const manifestPrefix = resolvedManifestPath.includes('/')
    ? resolvedManifestPath.slice(0, resolvedManifestPath.indexOf('/') + 1)
    : '';
  const optionalDocument = (name: string): unknown | null => {
    const document = reportDocuments.get(name);
    if (!document || document.path !== `${manifestPrefix}${name}` || document.skipped) return null;
    try {
      return parseJsonDocument(joinChunks(document.chunks, document.bytes), name);
    } catch (error) {
      console.warn(`The optional ${name} report could not be read; pack loading will continue.`, error);
      return null;
    }
  };
  let delta: LocalPackDelta | null = null;
  const deltaDocument = reportDocuments.get('delta.json');
  if (deltaDocument) {
    if (deltaDocument.path !== `${manifestPrefix}delta.json` || deltaDocument.skipped) {
      throw new Error('delta.json is too large or is outside the exporter folder.');
    }
    delta = requireLocalPackDelta(parseJsonDocument(
      joinChunks(deltaDocument.chunks, deltaDocument.bytes),
      'delta.json',
    ));
  }
  return {
    manifestPath: resolvedManifestPath,
    manifestBytes: manifestData,
    manifest,
    summary: requireLocalPackManifest(manifest),
    failures: optionalDocument('failures.json'),
    exportErrors: optionalDocument('export-errors.json'),
    exporterBuild: optionalDocument('exporter-build.json'),
    delta,
  };
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KiB', 'MiB', 'GiB', 'TiB'];
  let value = bytes / 1024;
  let unit = units[0];
  for (let index = 1; index < units.length && value >= 1024; index += 1) {
    value /= 1024;
    unit = units[index];
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ${unit}`;
}

export function PackUploadDropzone() {
  const inputRef = useRef<HTMLInputElement>(null);
  const operationRef = useRef(0);
  const pendingFailureReportRef = useRef<ExportFailureReport | null>(null);
  const reportSubmissionRef = useRef(false);
  const [dragging, setDragging] = useState(false);
  const [state, setState] = useState<UploadState>({status: 'idle'});

  const addFile = async (file: File | undefined) => {
    if (!file) return;
    setDragging(false);
    if (!file.name.toLowerCase().endsWith('.zip')) {
      setState({
        status: 'error',
        filename: file.name,
        message: 'Choose the ZIP made from your completed jei-exports folder.',
      });
      return;
    }

    const operation = operationRef.current + 1;
    operationRef.current = operation;
    pendingFailureReportRef.current = null;
    reportSubmissionRef.current = false;
    setState({status: 'checking', filename: file.name, progress: 0, phase: 'checking'});
    try {
      const result = await inspectPackArchive(
        file,
        fraction => {
          if (operationRef.current !== operation) return;
          setState({
            status: 'checking',
            filename: file.name,
            progress: fraction,
            phase: 'checking',
          });
        },
        () => operationRef.current === operation,
      );
      if (operationRef.current !== operation) return;
      let viewerHref = '/';
      let saved = false;
      let findings = result.summary.findings;
      let issueUrl: string | null = null;
      let fileUrl: string | null = null;
      let reportStatus: 'sending' | 'sent' | 'duplicate' | 'failed' | null = null;
      let reportAvailable = false;
      if (result.summary.readyForHandoff) {
        setState({status: 'checking', filename: file.name, progress: 0, phase: 'adding'});
        try {
          const installed = await installLocalPackArchive(
            file,
            result.manifestPath,
            result.manifestBytes,
            result.manifest,
            result.summary,
            progress => {
              if (operationRef.current !== operation) return;
              if (progress.phase === 'saving') {
                setState({
                  status: 'checking',
                  filename: file.name,
                  progress: progress.fraction,
                  phase: 'saving',
                  completedFiles: progress.completedFiles,
                  totalFiles: progress.totalFiles,
                });
                return;
              }
              if (progress.phase === 'finalizing') {
                setState({
                  status: 'checking',
                  filename: file.name,
                  progress: 1,
                  phase: 'finalizing',
                });
                return;
              }
              setState({
                status: 'checking',
                filename: file.name,
                progress: progress.fraction,
                phase: 'adding',
              });
            },
            result.delta,
          );
          viewerHref = installed.viewerHref;
          saved = true;
          if (result.summary.counts.failures > 0 && result.failures !== null) {
            try {
              pendingFailureReportRef.current = buildExportFailureReport({
                manifest: result.manifest,
                failures: result.failures,
                exportErrors: result.exportErrors ?? undefined,
                exporterBuild: result.exporterBuild ?? undefined,
              });
              reportAvailable = true;
            } catch (error) {
              console.error('The pack loaded, but its exporter errors could not be prepared.', error);
              findings = Object.freeze([
                ...findings,
                'The pack was added, but its exporter errors could not be prepared for sharing.',
              ]);
            }
          }
        } catch (error) {
          console.error('The checked pack could not be added to the viewer.', error);
          findings = Object.freeze([...findings, localPackUploadErrorMessage(error)]);
        }
      }
      if (operationRef.current !== operation) return;
      setState({
        status: 'ready',
        filename: file.name,
        bytes: file.size,
        summary: result.summary,
        viewerHref,
        saved,
        findings,
        issueUrl,
        fileUrl,
        reportStatus,
        reportAvailable,
        isDelta: result.delta !== null,
      });
    } catch (error) {
      if (operationRef.current !== operation) return;
      console.error('The exporter ZIP check failed.', error);
      setState({status: 'error', filename: file.name, message: localPackUploadErrorMessage(error)});
    } finally {
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const onInput = (event: ChangeEvent<HTMLInputElement>) => {
    void addFile(event.target.files?.[0]);
  };

  const onDrop = (event: DragEvent<HTMLLabelElement>) => {
    event.preventDefault();
    if (state.status === 'checking') return;
    void addFile(event.dataTransfer.files?.[0]);
  };

  const shareExporterErrors = async () => {
    const report = pendingFailureReportRef.current;
    const operation = operationRef.current;
    if (
      !report ||
      reportSubmissionRef.current ||
      state.status !== 'ready' ||
      state.reportStatus === 'sent' ||
      state.reportStatus === 'duplicate'
    ) return;
    reportSubmissionRef.current = true;
    setState(current => current.status === 'ready'
      ? {...current, reportStatus: 'sending'}
      : current);
    try {
      const submitted = await sendExportFailureReport(report);
      if (operationRef.current !== operation) return;
      setState(current => current.status === 'ready'
        ? {
            ...current,
            issueUrl: submitted.issueUrl,
            fileUrl: submitted.fileUrl,
            reportStatus: submitted.duplicate ? 'duplicate' : 'sent',
          }
        : current);
    } catch (error) {
      if (operationRef.current !== operation) return;
      console.error('The exporter error report could not be shared.', error);
      setState(current => current.status === 'ready'
        ? {...current, reportStatus: 'failed'}
        : current);
    } finally {
      if (operationRef.current === operation) reportSubmissionRef.current = false;
    }
  };

  return (
    <div className={styles.uploadPanel}>
      <label
        className={[
          styles.dropzone,
          dragging ? styles.dropzoneDragging : '',
          state.status === 'checking' ? styles.dropzoneBusy : '',
        ].filter(Boolean).join(' ')}
        aria-disabled={state.status === 'checking'}
        onDragEnter={event => {
          event.preventDefault();
          if (state.status !== 'checking') setDragging(true);
        }}
        onDragOver={event => {
          event.preventDefault();
          event.dataTransfer.dropEffect = 'copy';
        }}
        onDragLeave={event => {
          if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
            setDragging(false);
          }
        }}
        onDrop={onDrop}>
        <input
          ref={inputRef}
          className={styles.fileInput}
          type="file"
          accept=".zip,application/zip"
          onChange={onInput}
          disabled={state.status === 'checking'}
          aria-label="Import a local exporter ZIP archive"
        />
        <span className={styles.uploadIcon} aria-hidden="true">↑</span>
        <strong>
          {state.status === 'checking'
            ? state.phase === 'finalizing'
                ? `Preparing ${state.filename}`
                : state.phase === 'saving'
                  ? `Saving ${state.filename}`
                  : state.phase === 'adding'
                    ? `Adding ${state.filename} locally`
                    : `Checking ${state.filename}`
            : dragging
              ? 'Drop the exporter ZIP here'
              : 'Drag and drop a local exporter ZIP'}
        </strong>
        <span>
          {state.status === 'checking'
            ? state.phase === 'finalizing'
                ? 'Preparing it for the viewer…'
                : state.phase === 'saving'
                  ? `${state.completedFiles?.toLocaleString() ?? 0} of ${
                      state.totalFiles?.toLocaleString() ?? 0
                    } files saved`
                  : `${Math.round(state.progress * 100)}% ${
                      state.phase === 'adding' ? 'read' : 'checked'
                    }`
            : 'or tap to add a file'}
        </span>
        {state.status === 'checking' && (
          <span
            className={[
              styles.uploadProgress,
              state.phase === 'finalizing'
                ? styles.uploadProgressWaiting
                : '',
            ].filter(Boolean).join(' ')}
            style={{'--upload-progress': `${state.progress * 100}%`} as CSSProperties}
            role="progressbar"
            aria-label={
              state.phase === 'finalizing'
                  ? 'Preparing pack for the viewer'
                  : state.phase === 'saving'
                    ? 'Saving pack files'
                    : state.phase === 'adding'
                      ? 'Reading pack files'
                      : 'Checking pack'
            }
            aria-valuemin={
              state.phase === 'finalizing' ? undefined : 0
            }
            aria-valuemax={
              state.phase === 'finalizing' ? undefined : 100
            }
            aria-valuenow={
              state.phase === 'finalizing'
                ? undefined
                : Math.round(state.progress * 100)
            }
          />
        )}
      </label>

      <div className={styles.uploadResult} aria-live="polite">
        {state.status === 'ready' && (
          <article
            className={
              state.saved
                ? styles.uploadReady
                : styles.uploadNeedsAttention
            }>
            <div className={styles.uploadResultTopline}>
              <span>
                {state.saved
                  ? state.isDelta
                    ? 'UPDATE READY IN VIEWER'
                    : 'READY IN VIEWER'
                  : 'WE FOUND A PROBLEM'}
              </span>
              <a href={state.viewerHref}>Return to viewer</a>
            </div>
            <h3>{state.summary.packName}</h3>
            <p className={styles.uploadFilename}>
              {state.filename} · {formatBytes(state.bytes)}
            </p>
            <dl className={styles.uploadFacts}>
              <div>
                <dt>Pack version</dt>
                <dd>{state.summary.packVersion ?? 'Missing'}</dd>
              </div>
              <div>
                <dt>Minecraft</dt>
                <dd>{state.summary.minecraftVersion}</dd>
              </div>
              <div>
                <dt>Items</dt>
                <dd>{state.summary.counts.items.toLocaleString()}</dd>
              </div>
              <div>
                <dt>Recipes</dt>
                <dd>{state.summary.counts.recipes.toLocaleString()}</dd>
              </div>
              <div>
                <dt>Categories</dt>
                <dd>{state.summary.counts.categories.toLocaleString()}</dd>
              </div>
              <div>
                <dt>Failures</dt>
                <dd>{state.summary.counts.failures.toLocaleString()}</dd>
              </div>
            </dl>
            {state.findings.length > 0 ? (
              <ul className={styles.uploadFindings}>
                {state.findings.map(finding => <li key={finding}>{finding}</li>)}
              </ul>
            ) : (
              <p className={styles.uploadSuccessCopy}>
                No errors found. This pack is now in your local modpack list.
              </p>
            )}
            {state.saved && state.summary.counts.failures > 0 && state.reportAvailable && (
              <div className={styles.reportAction}>
                <button
                  type="button"
                  onClick={() => void shareExporterErrors()}
                  disabled={state.reportStatus === 'sending' || state.reportStatus === 'sent' || state.reportStatus === 'duplicate'}>
                  {state.reportStatus === 'sending'
                    ? 'Sharing exporter errors…'
                    : state.reportStatus === 'failed'
                      ? 'Try sharing exporter errors again'
                      : state.reportStatus === 'sent' || state.reportStatus === 'duplicate'
                        ? 'Exporter errors shared'
                        : 'Share exporter errors'}
                </button>
                <span>Send exporter diagnostics to the Recipe Tree GitHub issue tracker.</span>
              </div>
            )}
            {state.reportStatus && (
              <p className={styles.uploadSuccessCopy}>
                {state.reportStatus === 'sending'
                  ? 'Preparing the deduplicated errors report.'
                  : state.reportStatus === 'failed'
                    ? 'The errors were not shared. You can try again.'
                    : state.reportStatus === 'duplicate'
                    ? 'This pack or mod version already had a report, so its errors file was updated.'
                    : 'The exporter failures were saved to errors.json and shared with GitHub.'}
                {state.issueUrl && (
                  <> <a href={state.issueUrl} target="_blank" rel="noreferrer">View GitHub issue</a></>
                )}
                {state.fileUrl && (
                  <> <a href={state.fileUrl} target="_blank" rel="noreferrer">Download errors.json</a></>
                )}
              </p>
            )}
          </article>
        )}

        {state.status === 'error' && (
          <div className={styles.uploadError} role="alert">
            <div>
              <strong>That file could not be added.</strong>
              {state.filename && <span>{state.filename}</span>}
              <p>{state.message}</p>
            </div>
            <a href="/">Return to viewer</a>
          </div>
        )}
      </div>
    </div>
  );
}
