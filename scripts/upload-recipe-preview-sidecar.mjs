import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateLocalRecipePreviewSidecar} from './verify-recipe-preview-sidecar-remote.mjs';

const DATASET_ID_PATTERN = /^[a-f0-9]{64}$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const DEFAULT_CONCURRENCY = 8;
const MAX_CONCURRENCY = 32;
const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_TIMEOUT_MS = 120_000;
const OBJECT_HEAD_RETRY_DELAYS_MS = Object.freeze([250, 500, 1_000, 2_000]);
const MAX_TOKEN_BYTES = 8 * 1024;
const IMMUTABLE_CACHE_CONTROL = 'public, max-age=31536000, immutable, no-transform';
const SHA256_HEADER = 'x-mrt-content-sha256';
const CONTENT_BYTES_HEADER = 'x-mrt-content-bytes';
const TRANSPORT_RETRY_DELAYS_MS = Object.freeze([250, 1_000, 3_000]);
const DATASET_HEADER = 'x-mrt-dataset-publication-id';
const PUBLICATION_STATE_HEADER = 'x-mrt-publication-state';
const MANIFEST_BYTES_HEADER = 'x-mrt-manifest-bytes';
const DATA_ONLY_SIDECAR_FORMAT = 'mrt-recipe-preview-sidecar-v2';
const GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY = 'gtnh-structured-data-only-v1';
const GTNH_STRUCTURED_DATA_ONLY_EXCLUSION_REASON =
  'third-party-artwork-rights-not-cleared';

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function contentTypeForPath(path) {
  if (path.endsWith('.json')) return 'application/json; charset=utf-8';
  if (path.endsWith('.bin')) return 'application/octet-stream';
  throw new Error(`Sidecar upload refuses an unsupported object path: ${path}.`);
}

function validateToken(token) {
  if (
    typeof token !== 'string' ||
    token.length < 32 ||
    Buffer.byteLength(token, 'utf8') > MAX_TOKEN_BYTES ||
    /[\s\u0000-\u001f\u007f]/.test(token)
  ) {
    throw new Error(
      'The preview-ingestion bearer token must be 32-8192 bytes with no whitespace or control characters.',
    );
  }
  return token;
}

export async function readPreviewIngestToken({
  tokenFile,
  env = process.env,
} = {}) {
  if (tokenFile !== undefined) {
    if (typeof tokenFile !== 'string' || tokenFile.length === 0) {
      throw new Error('--token-file must name a non-empty path.');
    }
    const path = resolve(tokenFile);
    let info;
    try {
      info = await lstat(path);
    } catch (error) {
      throw new Error(`Preview-ingestion token file could not be inspected at ${path}: ${error.message}`, {
        cause: error,
      });
    }
    if (info.isSymbolicLink() || !info.isFile()) {
      throw new Error(`Preview-ingestion token file must be a plain file: ${path}.`);
    }
    if (process.platform !== 'win32' && (info.mode & 0o077) !== 0) {
      throw new Error(
        `Preview-ingestion token file must not be readable or writable by group/other users: ${path}.`,
      );
    }
    if (info.size <= 0 || info.size > MAX_TOKEN_BYTES + 2) {
      throw new Error(`Preview-ingestion token file has an invalid size: ${info.size} bytes.`);
    }
    let value;
    try {
      value = (await readFile(path, 'utf8')).replace(/\r?\n$/, '');
    } catch (error) {
      throw new Error(`Preview-ingestion token file could not be read at ${path}: ${error.message}`, {
        cause: error,
      });
    }
    return validateToken(value);
  }
  const token = env?.PREVIEW_UPLOAD_TOKEN;
  if (token === undefined) {
    throw new Error(
      'Set PREVIEW_UPLOAD_TOKEN or provide --token-file; credentials are never accepted in URLs.',
    );
  }
  return validateToken(token);
}

function normalizeIngestBaseUrl(value, allowHttpForTests) {
  let url;
  try {
    url = new URL(value);
  } catch (error) {
    throw new Error(`ingestBaseUrl must be an absolute URL: ${error.message}`, {cause: error});
  }
  if (url.protocol !== 'https:' && !(allowHttpForTests && url.protocol === 'http:')) {
    throw new Error(`Preview ingestion requires HTTPS; received ${url.protocol}.`);
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error(
      'Preview ingestion URL must not contain credentials, query parameters, or a fragment.',
    );
  }
  url.pathname = url.pathname.replace(/\/+$/, '');
  return url;
}

function childUrl(baseUrl, segments) {
  const encoded = segments.map(segment => encodeURIComponent(segment)).join('/');
  return new URL(`${baseUrl.pathname}/${encoded}`, baseUrl.origin);
}

function assetSetUrl(baseUrl, assetSetId, operation) {
  return childUrl(baseUrl, [assetSetId, operation]);
}

function objectUrl(baseUrl, assetSetId, path) {
  return childUrl(baseUrl, [assetSetId, 'objects', ...path.split('/')]);
}

async function cancelResponse(response, logger, label) {
  if (!response.body) return;
  try {
    await response.body.cancel();
  } catch (error) {
    logger.warn(`${label} response body could not be cancelled: ${error.message}`);
  }
}

async function request(fetchImpl, url, init, timeoutMs, label) {
  try {
    return await fetchImpl(url, {
      ...init,
      cache: 'no-store',
      redirect: 'error',
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (error) {
    const authorization = new Headers(init.headers).get('authorization') ?? '';
    const token = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
    const rawDetail = error instanceof Error ? error.message : String(error);
    const detail = token ? rawDetail.split(token).join('[REDACTED]') : rawDetail;
    // Do not retain the original exception as Error.cause. A transport implementation can
    // include request headers in its diagnostic, so preserving the raw cause would bypass the
    // bearer-token redaction above when structured telemetry serializes an exception chain.
    throw new Error(`${label} request failed for ${url.origin}${url.pathname}: ${detail}`);
  }
}

async function requestWithTransportRetry(
  fetchImpl,
  url,
  init,
  timeoutMs,
  label,
  logger,
  sleepImpl,
) {
  const attempts = TRANSPORT_RETRY_DELAYS_MS.length + 1;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      return await request(fetchImpl, url, init, timeoutMs, label);
    } catch (error) {
      if (attempt >= TRANSPORT_RETRY_DELAYS_MS.length) throw error;
      const delayMs = TRANSPORT_RETRY_DELAYS_MS[attempt];
      logger.warn(
        `${label} hit a redacted transport failure; retrying the immutable conditional request ` +
          `in ${delayMs} ms (attempt ${attempt + 2}/${attempts}).`,
      );
      await sleepImpl(delayMs);
    }
  }
  throw new Error(`${label} exhausted its bounded transport retry window.`);
}

function authorizationHeaders(token) {
  return {authorization: `Bearer ${token}`};
}

function defaultSleep(delayMs) {
  return new Promise(resolveSleep => setTimeout(resolveSleep, delayMs));
}

function exactHeader(response, name, expected, label) {
  const actual = response.headers.get(name);
  if (actual !== expected) {
    throw new Error(
      `${label} returned ${name}=${JSON.stringify(actual)}; expected ${JSON.stringify(expected)}.`,
    );
  }
}

function exactObjectBytes(response, expected, label) {
  const proxyStableBytes = response.headers.get(CONTENT_BYTES_HEADER);
  const contentLength = response.headers.get('content-length');
  if (proxyStableBytes === null && contentLength === null) {
    throw new Error(
      `${label} returned neither ${CONTENT_BYTES_HEADER} nor content-length; expected ${JSON.stringify(expected)}.`,
    );
  }
  if (proxyStableBytes !== null) {
    exactHeader(response, CONTENT_BYTES_HEADER, expected, label);
    return;
  }
  exactHeader(response, 'content-length', expected, label);
}

function statusError(response, label) {
  if (response.status === 401 || response.status === 403) {
    return new Error(`${label} rejected the preview-ingestion bearer token (HTTP ${response.status}).`);
  }
  return new Error(`${label} returned unexpected HTTP ${response.status}.`);
}

async function postPhase({
  baseUrl,
  assetSetId,
  phase,
  token,
  body,
  manifestSha256,
  datasetPublicationId,
  timeoutMs,
  fetchImpl,
  logger,
}) {
  const headers = {
    ...authorizationHeaders(token),
    [SHA256_HEADER]: manifestSha256,
    [DATASET_HEADER]: datasetPublicationId,
  };
  if (body) {
    headers['content-length'] = String(body.length);
    headers['content-type'] = contentTypeForPath('manifest.json');
    headers['cache-control'] = IMMUTABLE_CACHE_CONTROL;
  } else {
    headers['content-length'] = '0';
  }
  const label = `Preview-ingestion ${phase}`;
  const response = await request(
    fetchImpl,
    assetSetUrl(baseUrl, assetSetId, phase),
    {method: 'POST', headers, body},
    timeoutMs,
    label,
  );
  if (response.status !== 200 && response.status !== 201) {
    const error = statusError(response, label);
    await cancelResponse(response, logger, label);
    throw error;
  }
  await cancelResponse(response, logger, label);
  return response.status;
}

async function readPublicationStatus({
  baseUrl,
  assetSetId,
  token,
  manifestRecord,
  datasetPublicationId,
  timeoutMs,
  fetchImpl,
  logger,
}) {
  const label = 'Preview-ingestion publication status';
  const response = await request(
    fetchImpl,
    assetSetUrl(baseUrl, assetSetId, 'status'),
    {method: 'HEAD', headers: authorizationHeaders(token)},
    timeoutMs,
    label,
  );
  if (response.status !== 200) {
    const error = statusError(response, label);
    await cancelResponse(response, logger, label);
    throw error;
  }
  exactHeader(response, SHA256_HEADER, manifestRecord.sha256, label);
  exactHeader(response, MANIFEST_BYTES_HEADER, String(manifestRecord.bytes), label);
  exactHeader(response, DATASET_HEADER, datasetPublicationId, label);
  const state = response.headers.get(PUBLICATION_STATE_HEADER);
  if (state !== 'staged' && state !== 'committed') {
    throw new Error(
      `${label} returned ${PUBLICATION_STATE_HEADER}=${JSON.stringify(state)}; ` +
        'expected staged or committed.',
    );
  }
  await cancelResponse(response, logger, label);
  return state;
}

async function headObject({
  baseUrl,
  assetSetId,
  record,
  token,
  datasetPublicationId,
  timeoutMs,
  fetchImpl,
  logger,
  sleepImpl,
}, {
  retryNotFound = false,
} = {}) {
  const label = `Preview object HEAD ${record.path}`;
  const attempts = OBJECT_HEAD_RETRY_DELAYS_MS.length + 1;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const response = await requestWithTransportRetry(
      fetchImpl,
      objectUrl(baseUrl, assetSetId, record.path),
      {method: 'HEAD', headers: authorizationHeaders(token)},
      timeoutMs,
      label,
      logger,
      sleepImpl,
    );
    if (response.status === 200) {
      exactObjectBytes(response, String(record.bytes), label);
      exactHeader(response, SHA256_HEADER, record.sha256, label);
      exactHeader(response, DATASET_HEADER, datasetPublicationId, label);
      await cancelResponse(response, logger, label);
      return true;
    }
    const transientConflict = response.status === 409;
    const transientNotFound = response.status === 404 && retryNotFound;
    const canRetry = attempt < OBJECT_HEAD_RETRY_DELAYS_MS.length;
    if ((transientConflict || transientNotFound) && canRetry) {
      const delayMs = OBJECT_HEAD_RETRY_DELAYS_MS[attempt];
      await cancelResponse(response, logger, label);
      logger.warn(
        `${label} returned transient HTTP ${response.status}; retrying exact verification ` +
          `in ${delayMs} ms (attempt ${attempt + 2}/${attempts}).`,
      );
      await sleepImpl(delayMs);
      continue;
    }
    if (response.status === 404) {
      await cancelResponse(response, logger, label);
      return false;
    }
    const error = statusError(response, label);
    await cancelResponse(response, logger, label);
    throw error;
  }
  throw new Error(`${label} exhausted its bounded consistency retry window.`);
}

function resolveRecordPath(root, relativePath) {
  const path = resolve(root, ...relativePath.split('/'));
  const prefix = root.endsWith(sep) ? root : `${root}${sep}`;
  if (!path.startsWith(prefix)) {
    throw new Error(`Validated sidecar record resolves outside its root: ${relativePath}.`);
  }
  return path;
}

async function readVerifiedRecord(root, record) {
  const path = resolveRecordPath(root, record.path);
  let bytes;
  try {
    bytes = await readFile(path);
  } catch (error) {
    throw new Error(`Sidecar object changed or became unreadable at ${path}: ${error.message}`, {
      cause: error,
    });
  }
  const digest = sha256(bytes);
  if (bytes.length !== record.bytes || digest !== record.sha256) {
    throw new Error(
      `Sidecar object changed after validation: ${record.path} is ${bytes.length} bytes/${digest}; ` +
        `expected ${record.bytes} bytes/${record.sha256}.`,
    );
  }
  return bytes;
}

async function ensureObject(options) {
  if (await headObject(options)) return 'reused';
  const {record, root, token, datasetPublicationId, timeoutMs, fetchImpl, logger, sleepImpl} = options;
  const bytes = await readVerifiedRecord(root, record);
  const label = `Preview object PUT ${record.path}`;
  const response = await requestWithTransportRetry(
    fetchImpl,
    objectUrl(options.baseUrl, options.assetSetId, record.path),
    {
      method: 'PUT',
      headers: {
        ...authorizationHeaders(token),
        'if-none-match': '*',
        'content-length': String(record.bytes),
        'content-type': contentTypeForPath(record.path),
        'cache-control': IMMUTABLE_CACHE_CONTROL,
        [SHA256_HEADER]: record.sha256,
        [DATASET_HEADER]: datasetPublicationId,
      },
      body: bytes,
    },
    timeoutMs,
    label,
    logger,
    sleepImpl,
  );
  if (![200, 201, 204, 409, 412].includes(response.status)) {
    const error = statusError(response, label);
    await cancelResponse(response, logger, label);
    throw error;
  }
  const raced = response.status === 409 || response.status === 412;
  await cancelResponse(response, logger, label);
  if (!(await headObject(options, {retryNotFound: true}))) {
    throw new Error(`${label} succeeded or raced, but the exact object is still absent.`);
  }
  if (raced) logger.info(`${record.path} already appeared concurrently and matched exactly.`);
  return raced ? 'reused' : 'uploaded';
}

function errorDetail(error) {
  return error instanceof Error ? error.message : String(error);
}

function logDrainedFailure(logger, label, message) {
  try {
    logger.error(message);
  } catch {
    console.error(`${label} configured logger failed; emitting the drained failure directly.`);
    console.error(message);
  }
}

async function mapConcurrent(values, concurrency, operation, logger, label) {
  const results = new Array(values.length);
  let next = 0;
  let stopped = false;
  let firstFailure = null;
  const secondaryFailures = [];
  async function worker() {
    for (;;) {
      if (stopped) return;
      const index = next;
      next += 1;
      if (index >= values.length) return;
      try {
        results[index] = await operation(values[index], index);
      } catch (error) {
        if (firstFailure === null) {
          stopped = true;
          firstFailure = {error, index};
        } else {
          secondaryFailures.push({error, index});
        }
        return;
      }
    }
  }
  await Promise.all(Array.from({length: Math.min(concurrency, values.length)}, () => worker()));
  if (firstFailure !== null) {
    if (secondaryFailures.length > 0) {
      logDrainedFailure(
        logger,
        label,
        `${label} drained ${secondaryFailures.length} secondary failure(s) after stopping on ` +
          `record index ${firstFailure.index}.`,
      );
      for (const failure of secondaryFailures) {
        logDrainedFailure(
          logger,
          label,
          `${label} secondary failure at record index ${failure.index}: ${errorDetail(failure.error)}`,
        );
      }
    }
    throw firstFailure.error;
  }
  return results;
}

function validateOptions({
  local,
  ingestBaseUrl,
  token,
  concurrency,
  timeoutMs,
  logger,
  fetchImpl,
  localValidator,
  allowHttpForTests,
  sleepImpl,
}) {
  if (typeof local !== 'string' || local.length === 0) {
    throw new Error('local must be a non-empty sidecar directory path.');
  }
  if (!Number.isSafeInteger(concurrency) || concurrency <= 0 || concurrency > MAX_CONCURRENCY) {
    throw new Error(`concurrency must be within 1..${MAX_CONCURRENCY}.`);
  }
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || timeoutMs > MAX_TIMEOUT_MS) {
    throw new Error(`timeoutMs must be within 1..${MAX_TIMEOUT_MS}.`);
  }
  if (!isRecord(logger) || !['info', 'warn', 'error'].every(name => typeof logger[name] === 'function')) {
    throw new Error('logger must provide info, warn, and error functions.');
  }
  if (typeof fetchImpl !== 'function') throw new Error('fetchImpl must be a function.');
  if (typeof localValidator !== 'function') throw new Error('localValidator must be a function.');
  if (typeof sleepImpl !== 'function') throw new Error('sleepImpl must be a function.');
  return {
    baseUrl: normalizeIngestBaseUrl(ingestBaseUrl, allowHttpForTests),
    token: validateToken(token),
  };
}

export async function uploadRecipePreviewSidecar({
  local,
  ingestBaseUrl,
  token,
  concurrency = DEFAULT_CONCURRENCY,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  logger = console,
  fetchImpl = globalThis.fetch,
  localValidator = validateLocalRecipePreviewSidecar,
  allowHttpForTests = false,
  sleepImpl = defaultSleep,
}) {
  const validated = validateOptions({
    local,
    ingestBaseUrl,
    token,
    concurrency,
    timeoutMs,
    logger,
    fetchImpl,
    localValidator,
    allowHttpForTests,
    sleepImpl,
  });
  const root = resolve(local);
  logger.info(`Validating local recipe preview sidecar at ${root}.`);
  try {
    const localState = await localValidator(local, concurrency);
    const {manifest, manifestBytes} = localState;
    if (manifest.format === DATA_ONLY_SIDECAR_FORMAT) {
      if (
        manifest.publicationPolicy !== GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY ||
        manifest.exclusionReason !== GTNH_STRUCTURED_DATA_ONLY_EXCLUSION_REASON ||
        !Array.isArray(manifest.packs) ||
        manifest.packs.length !== 0 ||
        !Array.isArray(manifest.categoryDocuments) ||
        manifest.categoryDocuments.length !== 0 ||
        !Number.isSafeInteger(manifest.counts?.categories) ||
        manifest.counts.categories <= 0 ||
        !Number.isSafeInteger(manifest.counts?.recipes) ||
        manifest.counts.recipes <= 0 ||
        manifest.counts?.previews !== 0 ||
        manifest.counts?.missing !== manifest.counts?.recipes ||
        manifest.counts?.uniqueImages !== 0 ||
        manifest.counts?.duplicates !== 0 ||
        manifest.counts?.packs !== 0 ||
        manifest.counts?.inputBytes !== 0 ||
        !Number.isSafeInteger(manifest.counts?.hostedOmittedPngBytes) ||
        manifest.counts.hostedOmittedPngBytes < 0 ||
        manifest.counts?.encodedBytes !== 0 ||
        manifest.counts?.storedBytes !== 0 ||
        manifest.counts?.packIndexBytes !== 0 ||
        manifest.mapping?.documents !== 0 ||
        manifest.mapping?.parts !== 0 ||
        manifest.mapping?.bytes !== 0
      ) {
        throw new Error(
          'Validated v2 sidecar drifted from the exact manifest-only GTNH rights-exclusion contract.',
        );
      }
      logger.warn(
        `Rights policy ${GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY} excludes all recipe ` +
          'preview objects; upload will stage and commit only the content-addressed manifest.',
      );
    } else if (
      manifest.format === 'mrt-recipe-preview-sidecar-v1' &&
      (!Array.isArray(manifest.packs) || manifest.packs.length === 0)
    ) {
      throw new Error(
        'Ordinary v1 preview sidecars cannot use the manifest-only data-only upload branch.',
      );
    }
    if (!DATASET_ID_PATTERN.test(manifest.assetSetId ?? '')) {
      throw new Error('Validated sidecar assetSetId is not a lowercase SHA-256 digest.');
    }
    if (!DATASET_ID_PATTERN.test(manifest.datasetPublicationId ?? '')) {
      throw new Error('Validated sidecar datasetPublicationId is not a lowercase SHA-256 digest.');
    }
    if (!Buffer.isBuffer(manifestBytes) || manifestBytes.length <= 0) {
      throw new Error('Validated sidecar manifest bytes are unavailable.');
    }
    const manifestRecord = {
      path: 'manifest.json',
      bytes: manifestBytes.length,
      sha256: sha256(manifestBytes),
    };
    if (!SHA256_PATTERN.test(manifestRecord.sha256)) {
      throw new Error('Validated sidecar manifest SHA-256 could not be computed.');
    }
    const records = [
      ...manifest.packs.map(pack => ({path: pack.path, bytes: pack.bytes, sha256: pack.sha256})),
      ...manifest.packs.map(pack => pack.index),
      ...manifest.categoryDocuments,
    ].sort((left, right) => left.path < right.path ? -1 : left.path > right.path ? 1 : 0);

    logger.info(
      `Beginning immutable preview publication ${manifest.assetSetId}: ` +
        `${records.length} content objects plus one manifest commit marker.`,
    );
    await postPhase({
      baseUrl: validated.baseUrl,
      assetSetId: manifest.assetSetId,
      phase: 'begin',
      token: validated.token,
      body: manifestBytes,
      manifestSha256: manifestRecord.sha256,
      datasetPublicationId: manifest.datasetPublicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    const initialState = await readPublicationStatus({
      baseUrl: validated.baseUrl,
      assetSetId: manifest.assetSetId,
      token: validated.token,
      manifestRecord,
      datasetPublicationId: manifest.datasetPublicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    if (initialState === 'committed') {
      logger.info(
        `Recipe preview sidecar ${manifest.assetSetId} is already committed with the exact ` +
          'manifest digest and dataset identity; no object writes are required.',
      );
      return {
        assetSetId: manifest.assetSetId,
        datasetPublicationId: manifest.datasetPublicationId,
        objects: records.length,
        uploaded: 0,
        reused: records.length,
        committed: true,
      };
    }
    logger.info(`Preview publication ${manifest.assetSetId} is ${initialState}; verifying objects.`);

    let completed = 0;
    const outcomes = await mapConcurrent(
      records,
      concurrency,
      async record => {
        const outcome = await ensureObject({
          baseUrl: validated.baseUrl,
          assetSetId: manifest.assetSetId,
          record,
          root: localState.root,
          token: validated.token,
          datasetPublicationId: manifest.datasetPublicationId,
          timeoutMs,
          fetchImpl,
          logger,
          sleepImpl,
        });
        completed += 1;
        if (completed % 50 === 0 || completed === records.length) {
          logger.info(`Verified ${completed}/${records.length} immutable preview objects.`);
        }
        return outcome;
      },
      logger,
      'Recipe preview upload worker pool',
    );
    const uploaded = outcomes.filter(value => value === 'uploaded').length;
    const reused = outcomes.length - uploaded;
    logger.info(
      `All ${records.length} manifest-declared objects passed remote SHA-256/size verification; ` +
        `${uploaded} uploaded and ${reused} resumed. Committing manifest last.`,
    );

    await postPhase({
      baseUrl: validated.baseUrl,
      assetSetId: manifest.assetSetId,
      phase: 'commit',
      token: validated.token,
      manifestSha256: manifestRecord.sha256,
      datasetPublicationId: manifest.datasetPublicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    const finalState = await readPublicationStatus({
      baseUrl: validated.baseUrl,
      assetSetId: manifest.assetSetId,
      token: validated.token,
      manifestRecord,
      datasetPublicationId: manifest.datasetPublicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    if (finalState !== 'committed') {
      throw new Error(
        `Preview publication ${manifest.assetSetId} remained ${finalState} after commit.`,
      );
    }
    logger.info(
      `Recipe preview sidecar ${manifest.assetSetId} committed with manifest-last semantics.`,
    );
    return {
      assetSetId: manifest.assetSetId,
      datasetPublicationId: manifest.datasetPublicationId,
      objects: records.length,
      uploaded,
      reused,
      committed: true,
    };
  } catch (error) {
    logger.error(`Recipe preview upload failed before a verified commit: ${error.message}`);
    throw error;
  }
}

function parseCliArgs(argv) {
  const options = {};
  const names = new Map([
    ['--local', 'local'],
    ['--ingest-base-url', 'ingestBaseUrl'],
    ['--token-file', 'tokenFile'],
    ['--concurrency', 'concurrency'],
    ['--timeout-ms', 'timeoutMs'],
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const name = names.get(flag);
    if (!name) throw new Error(`Unknown argument ${flag}.`);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`Argument ${flag} requires a value.`);
    }
    if (options[name] !== undefined) throw new Error(`${flag} was provided more than once.`);
    options[name] = name === 'concurrency' || name === 'timeoutMs' ? Number(value) : value;
    index += 1;
  }
  if (!options.local || !options.ingestBaseUrl) {
    throw new Error(
      'Usage: node scripts/upload-recipe-preview-sidecar.mjs ' +
        '--local <sidecar-root> ' +
        '--ingest-base-url <https://app/api/admin/preview-assets> ' +
        '[--token-file <mode-0600-file>] [--concurrency <1-32>] [--timeout-ms <1-120000>]. ' +
        'Without --token-file, set PREVIEW_UPLOAD_TOKEN.',
    );
  }
  return options;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    const options = parseCliArgs(process.argv.slice(2));
    options.token = await readPreviewIngestToken({tokenFile: options.tokenFile});
    delete options.tokenFile;
    await uploadRecipePreviewSidecar(options);
  } catch (error) {
    console.error(`Recipe preview uploader terminated: ${error.message}`);
    process.exitCode = 1;
  }
}
