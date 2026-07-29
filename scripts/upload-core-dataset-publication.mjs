import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateLocalCoreDatasetPublication} from './build-core-dataset-publication.mjs';
import {
  CORE_DATASET_PUBLICATION_ID_PATTERN,
  GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY,
  coreDatasetContentRecords,
  requireCanonicalCoreDatasetPublicationBytes,
  requireCoreDatasetPublicationManifest,
} from './core-dataset-publication-contract.mjs';

const DEFAULT_CONCURRENCY = 8;
const MAX_CONCURRENCY = 32;
const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_TIMEOUT_MS = 120_000;
const MAX_TOKEN_BYTES = 8 * 1024;
const OBJECT_HEAD_RETRY_DELAYS_MS = Object.freeze([250, 500, 1_000, 2_000]);
const IMMUTABLE_CACHE_CONTROL = 'public, max-age=31536000, immutable, no-transform';
const SHA256_HEADER = 'x-mrt-content-sha256';
const CONTENT_BYTES_HEADER = 'x-mrt-content-bytes';
const TRANSPORT_RETRY_DELAYS_MS = Object.freeze([250, 1_000, 3_000]);
const PUBLICATION_HEADER = 'x-mrt-dataset-publication-id';
const PUBLICATION_STATE_HEADER = 'x-mrt-publication-state';
const MANIFEST_BYTES_HEADER = 'x-mrt-manifest-bytes';

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function contentTypeForPath(path) {
  if (path.endsWith('.json')) return 'application/json; charset=utf-8';
  if (path.endsWith('.bin')) return 'application/octet-stream';
  throw new Error(`Core dataset upload refuses unsupported object path ${path}.`);
}

function validateToken(token) {
  if (
    typeof token !== 'string' ||
    token.length < 32 ||
    Buffer.byteLength(token, 'utf8') > MAX_TOKEN_BYTES ||
    /[\s\u0000-\u001f\u007f]/.test(token)
  ) {
    throw new Error(
      'The core-dataset ingestion bearer token must be 32-8192 bytes with no whitespace or control characters.',
    );
  }
  return token;
}

export async function readCoreDatasetIngestToken({tokenFile, env = process.env} = {}) {
  if (tokenFile !== undefined) {
    if (typeof tokenFile !== 'string' || tokenFile.length === 0) {
      throw new Error('--token-file must name a non-empty path.');
    }
    const path = resolve(tokenFile);
    let info;
    try {
      info = await lstat(path);
    } catch (error) {
      throw new Error(`Core-dataset token file could not be inspected at ${path}: ${error.message}`, {
        cause: error,
      });
    }
    if (info.isSymbolicLink() || !info.isFile()) {
      throw new Error(`Core-dataset token file must be a plain file: ${path}.`);
    }
    if (process.platform !== 'win32' && (info.mode & 0o077) !== 0) {
      throw new Error(
        `Core-dataset token file must not be readable or writable by group/other users: ${path}.`,
      );
    }
    if (info.size <= 0 || info.size > MAX_TOKEN_BYTES + 2) {
      throw new Error(`Core-dataset token file has invalid size ${info.size} bytes.`);
    }
    let value;
    try {
      value = (await readFile(path, 'utf8')).replace(/\r?\n$/, '');
    } catch (error) {
      throw new Error(`Core-dataset token file could not be read at ${path}: ${error.message}`, {
        cause: error,
      });
    }
    return validateToken(value);
  }
  const token = env?.CORE_DATASET_UPLOAD_TOKEN;
  if (token === undefined) {
    throw new Error(
      'Set CORE_DATASET_UPLOAD_TOKEN or provide --token-file; credentials are never accepted in URLs.',
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
    throw new Error(`Core dataset ingestion requires HTTPS; received ${url.protocol}.`);
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error(
      'Core dataset ingestion URL must not contain credentials, query parameters, or a fragment.',
    );
  }
  url.pathname = url.pathname.replace(/\/+$/, '');
  return url;
}

function childUrl(baseUrl, segments) {
  const encoded = segments.map(segment => encodeURIComponent(segment)).join('/');
  return new URL(`${baseUrl.pathname}/${encoded}`, baseUrl.origin);
}

function phaseUrl(baseUrl, phase) {
  return childUrl(baseUrl, [phase]);
}

function objectUrl(baseUrl, path) {
  return childUrl(baseUrl, ['object', ...path.split('/')]);
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
    const detail = error instanceof Error ? error.message : String(error);
    const redacted = token ? detail.split(token).join('[REDACTED]') : detail;
    const target = `${url.origin}${url.pathname}`;
    const redactedTarget = token ? target.split(token).join('[REDACTED]') : target;
    // Never retain the raw transport exception as Error.cause: fetch implementations may
    // serialize request headers into it and thereby bypass bearer-token redaction.
    throw new Error(`${label} request failed for ${redactedTarget}: ${redacted}`);
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

function authorizationHeaders(token, publicationId) {
  return {
    authorization: `Bearer ${token}`,
    [PUBLICATION_HEADER]: publicationId,
  };
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
  }
  if (contentLength !== null) {
    exactHeader(response, 'content-length', expected, label);
  }
}

function statusError(response, label) {
  if (response.status === 401 || response.status === 403) {
    return new Error(`${label} rejected the core-dataset bearer token (HTTP ${response.status}).`);
  }
  return new Error(`${label} returned unexpected HTTP ${response.status}.`);
}

async function postPhase({
  baseUrl,
  phase,
  token,
  body,
  manifestSha256,
  publicationId,
  timeoutMs,
  fetchImpl,
  logger,
}) {
  const headers = {
    ...authorizationHeaders(token, publicationId),
    [SHA256_HEADER]: manifestSha256,
  };
  if (body) {
    headers['content-length'] = String(body.length);
    headers['content-type'] = 'application/json; charset=utf-8';
    headers['cache-control'] = IMMUTABLE_CACHE_CONTROL;
  } else {
    headers['content-length'] = '0';
  }
  const label = `Core-dataset ingestion ${phase}`;
  const response = await request(
    fetchImpl,
    phaseUrl(baseUrl, phase),
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
  token,
  manifestBytes,
  manifestSha256,
  publicationId,
  timeoutMs,
  fetchImpl,
  logger,
}) {
  const label = 'Core-dataset publication status';
  const response = await request(
    fetchImpl,
    phaseUrl(baseUrl, 'status'),
    {method: 'HEAD', headers: authorizationHeaders(token, publicationId)},
    timeoutMs,
    label,
  );
  if (response.status !== 200) {
    const error = statusError(response, label);
    await cancelResponse(response, logger, label);
    throw error;
  }
  exactHeader(response, SHA256_HEADER, manifestSha256, label);
  exactHeader(response, MANIFEST_BYTES_HEADER, String(manifestBytes.length), label);
  exactHeader(response, PUBLICATION_HEADER, publicationId, label);
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

async function headObject(options, {retryNotFound = false} = {}) {
  const {
    baseUrl,
    record,
    token,
    publicationId,
    timeoutMs,
    fetchImpl,
    logger,
    sleepImpl,
  } = options;
  const label = `Core dataset object HEAD ${record.path}`;
  const attempts = OBJECT_HEAD_RETRY_DELAYS_MS.length + 1;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const response = await request(
      fetchImpl,
      objectUrl(baseUrl, record.path),
      {method: 'HEAD', headers: authorizationHeaders(token, publicationId)},
      timeoutMs,
      label,
    );
    if (response.status === 200) {
      exactObjectBytes(response, String(record.bytes), label);
      exactHeader(response, SHA256_HEADER, record.sha256, label);
      exactHeader(response, PUBLICATION_HEADER, publicationId, label);
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

async function readVerifiedRecord(record) {
  let info;
  try {
    info = await lstat(record.localPath);
  } catch (error) {
    throw new Error(
      `Core dataset object changed or became unreadable at ${record.localPath}: ${error.message}`,
      {cause: error},
    );
  }
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`Core dataset object must remain a plain file: ${record.localPath}.`);
  }
  const bytes = await readFile(record.localPath);
  const digest = sha256(bytes);
  if (bytes.length !== record.bytes || digest !== record.sha256) {
    throw new Error(
      `Core dataset object changed after validation: ${record.path} is ` +
        `${bytes.length} bytes/${digest}; expected ${record.bytes}/${record.sha256}.`,
    );
  }
  return bytes;
}

async function ensureObject(options) {
  if (await headObject(options)) return 'reused';
  const {record, token, publicationId, timeoutMs, fetchImpl, logger} = options;
  const bytes = await readVerifiedRecord(record);
  const label = `Core dataset object PUT ${record.path}`;
  const response = await requestWithTransportRetry(
    fetchImpl,
    objectUrl(options.baseUrl, record.path),
    {
      method: 'PUT',
      headers: {
        ...authorizationHeaders(token, publicationId),
        'if-none-match': '*',
        'content-length': String(record.bytes),
        'content-type': contentTypeForPath(record.path),
        'cache-control': IMMUTABLE_CACHE_CONTROL,
        [SHA256_HEADER]: record.sha256,
      },
      body: bytes,
    },
    timeoutMs,
    label,
    logger,
    options.sleepImpl,
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
  if (raced) logger.info(`${record.path} appeared concurrently and matched exactly.`);
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
  await Promise.all(Array.from({length: Math.min(concurrency, values.length)}, worker));
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
  exportRoot,
  publication,
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
  if (typeof exportRoot !== 'string' || exportRoot.length === 0) {
    throw new Error('exportRoot must be a non-empty packed-export directory path.');
  }
  if (typeof publication !== 'string' || publication.length === 0) {
    throw new Error('publication must be a non-empty publication.json path.');
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

function requireValidatedLocalState(state) {
  if (!isRecord(state)) throw new Error('Local core publication validator returned invalid state.');
  const manifest = requireCoreDatasetPublicationManifest(state.manifest);
  if (!Buffer.isBuffer(state.manifestBytes)) {
    throw new Error('Local core publication validator did not return manifest bytes.');
  }
  requireCanonicalCoreDatasetPublicationBytes(state.manifestBytes, manifest.publicationId);
  if (!Array.isArray(state.records)) {
    throw new Error('Local core publication validator did not return content records.');
  }
  const declared = coreDatasetContentRecords(manifest);
  if (state.records.length !== declared.length) {
    throw new Error('Local core publication content-record count differs from publication.json.');
  }
  for (const [index, expected] of declared.entries()) {
    const record = state.records[index];
    if (
      !isRecord(record) ||
      record.path !== expected.path ||
      record.bytes !== expected.bytes ||
      record.sha256 !== expected.sha256 ||
      typeof record.localPath !== 'string' ||
      record.localPath.length === 0
    ) {
      throw new Error(`Local core publication record ${index} differs from publication.json.`);
    }
  }
  return {manifest, manifestBytes: state.manifestBytes, records: state.records};
}

export async function uploadCoreDatasetPublication({
  exportRoot,
  publication,
  ingestBaseUrl,
  token,
  concurrency = DEFAULT_CONCURRENCY,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  logger = console,
  fetchImpl = globalThis.fetch,
  localValidator = validateLocalCoreDatasetPublication,
  allowHttpForTests = false,
  sleepImpl = defaultSleep,
}) {
  const validated = validateOptions({
    exportRoot,
    publication,
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
  logger.info(`Validating local core dataset publication at ${resolve(publication)}.`);
  try {
    const localState = requireValidatedLocalState(
      await localValidator({exportRoot, publication, concurrency, logger}),
    );
    const {manifest, manifestBytes, records} = localState;
    if (manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY) {
      logger.warn(
        `Rights policy ${GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY} excludes every core ` +
          'image pack; uploading structured JSON documents and the zero-pack control manifest only.',
      );
    }
    const publicationId = manifest.publicationId;
    if (!CORE_DATASET_PUBLICATION_ID_PATTERN.test(publicationId)) {
      throw new Error('Validated core dataset publicationId is not a lowercase SHA-256 digest.');
    }
    const manifestSha256 = sha256(manifestBytes);
    logger.info(
      `Beginning immutable core publication ${publicationId}: ${records.length} content ` +
        'objects plus one publication.json commit marker.',
    );
    await postPhase({
      baseUrl: validated.baseUrl,
      phase: 'begin',
      token: validated.token,
      body: manifestBytes,
      manifestSha256,
      publicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    const initialState = await readPublicationStatus({
      baseUrl: validated.baseUrl,
      token: validated.token,
      manifestBytes,
      manifestSha256,
      publicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    if (initialState === 'committed') {
      logger.info(
        `Core dataset ${publicationId} is already committed with the exact control-manifest ` +
          'digest; no object writes are required. Replaying the idempotent commit to reconcile ' +
          'its D1 registry row.',
      );
      // R2 publication.json is written before the D1 registry transaction. A prior request can
      // therefore lose connectivity after the R2 commit while D1 still needs reconciliation.
      // Core uploads replay this phase instead of short-circuiting solely on the R2 marker.
      await postPhase({
        baseUrl: validated.baseUrl,
        phase: 'commit',
        token: validated.token,
        manifestSha256,
        publicationId,
        timeoutMs,
        fetchImpl,
        logger,
      });
      const reconciledState = await readPublicationStatus({
        baseUrl: validated.baseUrl,
        token: validated.token,
        manifestBytes,
        manifestSha256,
        publicationId,
        timeoutMs,
        fetchImpl,
        logger,
      });
      if (reconciledState !== 'committed') {
        throw new Error(
          `Core publication ${publicationId} became ${reconciledState} during registry reconciliation.`,
        );
      }
      return {
        publicationId,
        manifestSha256,
        objects: records.length,
        uploaded: 0,
        reused: records.length,
        committed: true,
      };
    }

    let completed = 0;
    const outcomes = await mapConcurrent(
      records,
      concurrency,
      async record => {
        const outcome = await ensureObject({
          baseUrl: validated.baseUrl,
          record,
          token: validated.token,
          publicationId,
          timeoutMs,
          fetchImpl,
          logger,
          sleepImpl,
        });
        completed += 1;
        if (completed % 50 === 0 || completed === records.length) {
          logger.info(`Verified ${completed}/${records.length} immutable core dataset objects.`);
        }
        return outcome;
      },
      logger,
      'Core dataset upload worker pool',
    );
    const uploaded = outcomes.filter(value => value === 'uploaded').length;
    const reused = outcomes.length - uploaded;
    logger.info(
      `All ${records.length} objects passed remote SHA-256/size verification; ${uploaded} ` +
        `uploaded and ${reused} resumed. Committing publication.json last.`,
    );
    await postPhase({
      baseUrl: validated.baseUrl,
      phase: 'commit',
      token: validated.token,
      manifestSha256,
      publicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    const finalState = await readPublicationStatus({
      baseUrl: validated.baseUrl,
      token: validated.token,
      manifestBytes,
      manifestSha256,
      publicationId,
      timeoutMs,
      fetchImpl,
      logger,
    });
    if (finalState !== 'committed') {
      throw new Error(`Core publication ${publicationId} remained ${finalState} after commit.`);
    }
    logger.info(`Core dataset ${publicationId} committed with manifest-last semantics.`);
    return {
      publicationId,
      manifestSha256,
      objects: records.length,
      uploaded,
      reused,
      committed: true,
    };
  } catch (error) {
    logger.error(`Core dataset upload failed before a verified commit: ${error.message}`);
    throw error;
  }
}

function parseCliArguments(argv) {
  const options = {};
  const names = new Map([
    ['--root', 'exportRoot'],
    ['--publication', 'publication'],
    ['--ingest-base-url', 'ingestBaseUrl'],
    ['--token-file', 'tokenFile'],
    ['--concurrency', 'concurrency'],
    ['--timeout-ms', 'timeoutMs'],
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const name = names.get(flag);
    if (!name) throw new Error(`Unknown core publication uploader argument: ${flag}.`);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`${flag} requires a value.`);
    }
    if (options[name] !== undefined) throw new Error(`${flag} was provided more than once.`);
    options[name] = name === 'concurrency' || name === 'timeoutMs' ? Number(value) : value;
    index += 1;
  }
  if (!options.exportRoot || !options.publication || !options.ingestBaseUrl) {
    throw new Error(
      'Usage: node scripts/upload-core-dataset-publication.mjs ' +
        '--root <packed-export-root> --publication <bundle/publication.json> ' +
        '--ingest-base-url <https://app/api/admin/core-datasets> ' +
        '[--token-file <mode-0600-file>] [--concurrency <1-32>] [--timeout-ms <1-120000>]. ' +
        'Without --token-file, set CORE_DATASET_UPLOAD_TOKEN.',
    );
  }
  return options;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    const options = parseCliArguments(process.argv.slice(2));
    options.token = await readCoreDatasetIngestToken({tokenFile: options.tokenFile});
    delete options.tokenFile;
    await uploadCoreDatasetPublication(options);
  } catch (error) {
    console.error(`Core dataset uploader terminated: ${error.message}`);
    process.exitCode = 1;
  }
}
