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
  MAX_EXPORT_ARCHIVE_ENTRIES,
  MAX_EXPORT_MANIFEST_BYTES,
  requireLocalPackManifest,
  requireSafeArchivePath,
  type LocalPackManifestSummary,
} from '../../src/data/localPackArchive';
import {installLocalPackArchive} from '../../src/data/localPackStorage';
import styles from './publish.module.css';

// Keep each synchronous fflate push small enough for archives containing long
// runs of tiny entries. Larger batches can recurse through thousands of local
// file headers before returning and overflow the browser call stack.
const ARCHIVE_READ_CHUNK_BYTES = 1024 * 1024;

type UploadState =
  | {status: 'idle'}
  | {
      status: 'checking';
      filename: string;
      progress: number;
      phase: 'checking' | 'adding';
    }
  | {
      status: 'ready';
      filename: string;
      bytes: number;
      summary: LocalPackManifestSummary;
      viewerHref: string;
      saved: boolean;
      findings: readonly string[];
    }
  | {status: 'error'; filename: string | null; message: string};

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function plainUploadError(error: unknown): string {
  const message = errorMessage(error);
  if (message.includes('empty')) {
    return 'This ZIP is empty. Run the exporter again and choose the new ZIP.';
  }
  if (message.includes('manifest.json') || message.includes('exporter information')) {
    return 'We could not find the pack information in this ZIP. Run the exporter again and choose the new ZIP.';
  }
  if (
    message.includes('too many files') ||
    message.includes('too large') ||
    message.includes('browser storage')
  ) {
    return message;
  }
  if (
    message.includes('unsafe file path') ||
    message.includes('file path that cannot be opened safely')
  ) {
    return 'This ZIP contains a file we cannot open safely. Make a fresh export and try again.';
  }
  if (message.includes('not a readable ZIP') || message.includes('could not be opened')) {
    return 'This file is not a readable ZIP. Choose the ZIP made by the exporter.';
  }
  if (message.startsWith('The ZIP is missing ')) return message;
  console.error('The exporter ZIP could not be prepared for the viewer.', error);
  return 'We could not read this export. Run the exporter again and try the new ZIP.';
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

async function inspectPackArchive(
  file: File,
  onProgress: (fraction: number) => void,
  isCurrent: () => boolean,
): Promise<{
  manifestPath: string;
  manifestBytes: Uint8Array;
  manifest: unknown;
  summary: LocalPackManifestSummary;
}> {
  if (file.size === 0) throw new Error('The selected ZIP file is empty.');
  if (!Number.isSafeInteger(file.size)) throw new Error('The selected file size is invalid.');

  let archiveError: Error | null = null;
  let entryCount = 0;
  let manifestPath: string | null = null;
  let manifestBytes = 0;
  let manifestChunks: Uint8Array[] = [];
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

    if (!isExportManifestPath(safePath)) return;
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
  let manifestText: string;
  try {
    manifestText = new TextDecoder('utf-8', {fatal: true}).decode(manifestData);
  } catch (error) {
    throw new Error(`manifest.json is not valid UTF-8: ${errorMessage(error)}`);
  } finally {
    manifestChunks = [];
  }

  let manifest: unknown;
  try {
    manifest = JSON.parse(manifestText);
  } catch (error) {
    throw new Error(`manifest.json is not valid JSON: ${errorMessage(error)}`);
  }
  return {
    manifestPath,
    manifestBytes: manifestData,
    manifest,
    summary: requireLocalPackManifest(manifest),
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
      if (result.summary.readyForHandoff) {
        setState({status: 'checking', filename: file.name, progress: 0, phase: 'adding'});
        try {
          const installed = await installLocalPackArchive(
            file,
            result.manifestPath,
            result.manifestBytes,
            result.manifest,
            result.summary,
            fraction => {
              if (operationRef.current !== operation) return;
              setState({
                status: 'checking',
                filename: file.name,
                progress: fraction,
                phase: 'adding',
              });
            },
          );
          viewerHref = installed.viewerHref;
          saved = true;
        } catch (error) {
          console.error('The checked pack could not be added to the viewer.', error);
          findings = Object.freeze([...findings, plainUploadError(error)]);
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
      });
    } catch (error) {
      if (operationRef.current !== operation) return;
      console.error('The exporter ZIP check failed.', error);
      setState({status: 'error', filename: file.name, message: plainUploadError(error)});
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
          aria-label="Add an exporter ZIP archive"
          aria-describedby="upload-help"
        />
        <span className={styles.uploadIcon} aria-hidden="true">↑</span>
        <strong>
          {state.status === 'checking'
            ? state.phase === 'adding'
              ? `Adding ${state.filename} to your viewer`
              : `Checking ${state.filename}`
            : dragging
              ? 'Drop the exporter ZIP here'
              : 'Drag and drop your exporter ZIP'}
        </strong>
        <span>
          {state.status === 'checking'
            ? `${Math.round(state.progress * 100)}% ${
                state.phase === 'adding' ? 'saved' : 'checked'
              }`
            : 'or tap to add a file'}
        </span>
        {state.status === 'checking' && (
          <span
            className={styles.uploadProgress}
            style={{'--upload-progress': `${state.progress * 100}%`} as CSSProperties}
            aria-hidden="true"
          />
        )}
      </label>

      <p className={styles.uploadHelp} id="upload-help">
        Your ZIP stays on this device. Nothing is sent to a server.
      </p>

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
                {state.saved ? 'READY IN VIEWER' : 'WE FOUND A PROBLEM'}
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
                No errors found. This pack is now in your modpack list.
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
