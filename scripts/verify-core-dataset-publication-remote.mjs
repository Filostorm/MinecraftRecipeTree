import {open, readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateLocalCoreDatasetPublication} from './build-core-dataset-publication.mjs';
import {parsePackedImageAuthorizationIndex} from './packed-image-authorization.mjs';

const DEFAULT_CONCURRENCY = 8;
const MAX_CONCURRENCY = 32;
const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_TIMEOUT_MS = 120_000;

function requireBoundedInteger(value, minimum, maximum, label) {
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${label} must be within ${minimum}..${maximum}.`);
  }
  return value;
}

function normalizeBaseUrl(value, allowHttpForTests) {
  let url;
  try {
    url = new URL(value);
  } catch (error) {
    throw new Error(`baseUrl must be an absolute URL: ${error.message}`);
  }
  if (url.protocol !== 'https:' && !(allowHttpForTests && url.protocol === 'http:')) {
    throw new Error(`Public core verification requires HTTPS; received ${url.protocol}.`);
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('Public core verification URL must not contain credentials, query, or a fragment.');
  }
  url.pathname = url.pathname.replace(/\/+$/, '');
  if (url.pathname !== '/dataset/publications') {
    throw new Error('baseUrl must end at the exact /dataset/publications route.');
  }
  return url;
}

function publicObjectUrl(baseUrl, publicationId, path) {
  const encodedPath = path.split('/').map(encodeURIComponent).join('/');
  const url = new URL(
    `${baseUrl.pathname}/${publicationId}/exports/${encodedPath}`,
    baseUrl.origin,
  );
  url.search = `?dataset=${publicationId}`;
  return url;
}

async function cancelBody(response, logger, label) {
  if (!response.body) return;
  try {
    await response.body.cancel();
  } catch (error) {
    logger.warn(`${label} response-body cancellation failed during verifier cleanup: ${error.message}`);
  }
}

async function fetchWithTimeout(fetchImpl, url, init, timeoutMs, label) {
  try {
    return await fetchImpl(url, {
      ...init,
      cache: 'no-store',
      redirect: 'error',
      signal: AbortSignal.timeout(timeoutMs),
      headers: {'accept-encoding': 'identity', 'cache-control': 'no-cache', ...init.headers},
    });
  } catch (error) {
    throw new Error(`${label} request failed for ${url}: ${error.message}`);
  }
}

function assertResponseHeaders(response, {bytes, contentType, noTransform, label}) {
  const length = response.headers.get('content-length');
  if (!/^(0|[1-9]\d*)$/.test(length ?? '') || Number(length) !== bytes) {
    throw new Error(`${label} Content-Length is ${JSON.stringify(length)}; expected ${bytes}.`);
  }
  const actualType = response.headers.get('content-type')?.split(';', 1)[0].trim().toLowerCase();
  if (actualType !== contentType) {
    throw new Error(`${label} Content-Type is ${JSON.stringify(actualType)}; expected ${contentType}.`);
  }
  const encoding = response.headers.get('content-encoding');
  if (encoding && encoding.toLowerCase() !== 'identity') {
    throw new Error(`${label} returned Content-Encoding ${encoding}; exact stored bytes are required.`);
  }
  const cacheDirectives = (response.headers.get('cache-control') ?? '')
    .toLowerCase()
    .split(',')
    .map(value => value.trim());
  if (!cacheDirectives.includes('public') || !cacheDirectives.includes('immutable')) {
    throw new Error(`${label} omitted the required public immutable cache contract.`);
  }
  if (noTransform && !cacheDirectives.includes('no-transform')) {
    throw new Error(`${label} omitted the required no-transform image directive.`);
  }
}

async function readExactBody(response, expected, label) {
  if (!response.body) throw new Error(`${label} returned no response body.`);
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    for (;;) {
      const {done, value} = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > expected) {
        await reader.cancel();
        throw new Error(`${label} exceeded its declared ${expected}-byte bound.`);
      }
      chunks.push(Buffer.from(value.buffer, value.byteOffset, value.byteLength));
    }
  } finally {
    reader.releaseLock();
  }
  if (total !== expected) throw new Error(`${label} returned ${total}/${expected} bytes.`);
  return Buffer.concat(chunks, total);
}

async function mapConcurrent(values, concurrency, operation) {
  let next = 0;
  const workers = Array.from({length: Math.min(concurrency, values.length)}, async () => {
    for (;;) {
      const index = next;
      next += 1;
      if (index >= values.length) return;
      await operation(values[index], index);
    }
  });
  await Promise.all(workers);
}

function sampleEntryIndexes(entryCount) {
  return [...new Set([0, Math.floor((entryCount - 1) / 2), entryCount - 1])];
}

async function readLocalRange(path, offset, length) {
  const file = await open(path, 'r');
  try {
    const bytes = Buffer.allocUnsafe(length);
    const result = await file.read(bytes, 0, length, offset);
    if (result.bytesRead !== length) {
      throw new Error(`Local pack ${path} returned ${result.bytesRead}/${length} sample bytes.`);
    }
    return bytes;
  } finally {
    await file.close();
  }
}

export async function verifyPublicCoreDatasetPublication({
  exportRoot,
  publication,
  baseUrl,
  concurrency = DEFAULT_CONCURRENCY,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  logger = console,
  fetchImpl = globalThis.fetch,
  allowHttpForTests = false,
  localValidator = validateLocalCoreDatasetPublication,
}) {
  const boundedConcurrency = requireBoundedInteger(
    concurrency,
    1,
    MAX_CONCURRENCY,
    'concurrency',
  );
  const boundedTimeout = requireBoundedInteger(timeoutMs, 1, MAX_TIMEOUT_MS, 'timeoutMs');
  if (!logger || !['info', 'warn', 'error'].every(name => typeof logger[name] === 'function')) {
    throw new Error('logger must provide info, warn, and error functions.');
  }
  if (typeof fetchImpl !== 'function' || typeof localValidator !== 'function') {
    throw new Error('fetchImpl and localValidator must be functions.');
  }
  const normalizedBaseUrl = normalizeBaseUrl(baseUrl, allowHttpForTests);

  try {
    const local = await localValidator({
      exportRoot,
      publication,
      concurrency: boundedConcurrency,
      logger,
    });
    const publicationId = local.publicationId;
    const records = new Map(local.records.map(record => [record.path, record]));
    const documents = local.manifest.documents;

    await mapConcurrent(documents, boundedConcurrency, async record => {
      const label = `HEAD ${record.path}`;
      const response = await fetchWithTimeout(
        fetchImpl,
        publicObjectUrl(normalizedBaseUrl, publicationId, record.path),
        {method: 'HEAD'},
        boundedTimeout,
        label,
      );
      if (response.status !== 200) {
        await cancelBody(response, logger, label);
        throw new Error(`${label} returned HTTP ${response.status}.`);
      }
      assertResponseHeaders(response, {
        bytes: record.bytes,
        contentType: 'application/json',
        noTransform: false,
        label,
      });
      await cancelBody(response, logger, label);
    });

    const localManifest = records.get('manifest.json');
    if (!localManifest) throw new Error('Local validated publication omitted manifest.json.');
    const manifestLabel = 'GET manifest.json';
    const manifestResponse = await fetchWithTimeout(
      fetchImpl,
      publicObjectUrl(normalizedBaseUrl, publicationId, 'manifest.json'),
      {method: 'GET'},
      boundedTimeout,
      manifestLabel,
    );
    if (manifestResponse.status !== 200) {
      await cancelBody(manifestResponse, logger, manifestLabel);
      throw new Error(`${manifestLabel} returned HTTP ${manifestResponse.status}.`);
    }
    assertResponseHeaders(manifestResponse, {
      bytes: localManifest.bytes,
      contentType: 'application/json',
      noTransform: false,
      label: manifestLabel,
    });
    const [remoteManifestBytes, localManifestBytes] = await Promise.all([
      readExactBody(manifestResponse, localManifest.bytes, manifestLabel),
      readFile(localManifest.localPath),
    ]);
    if (!remoteManifestBytes.equals(localManifestBytes)) {
      throw new Error('Public manifest.json is not byte-for-byte identical to the local publication.');
    }

    const sampleJobs = [];
    for (const [packNumber, pack] of local.manifest.packs.entries()) {
      const indexRecord = records.get(pack.index.path);
      const packRecord = records.get(pack.path);
      if (!indexRecord || !packRecord) {
        throw new Error(`Local validated publication omitted pack ${packNumber} paths.`);
      }
      const index = parsePackedImageAuthorizationIndex(await readFile(indexRecord.localPath), {
        expectedPackNumber: packNumber,
        expectedPackBytes: pack.bytes,
      });
      for (const entryIndex of sampleEntryIndexes(index.entries.length)) {
        const [offset, length] = index.entries[entryIndex];
        sampleJobs.push({packNumber, offset, length, packPath: packRecord.localPath});
      }
    }

    await mapConcurrent(sampleJobs, boundedConcurrency, async sample => {
      const coordinate = `${String(sample.packNumber).padStart(3, '0')}-${sample.offset}-${sample.length}`;
      const path = `assets/s/${coordinate}.webp`;
      const label = `GET ${path}`;
      const response = await fetchWithTimeout(
        fetchImpl,
        publicObjectUrl(normalizedBaseUrl, publicationId, path),
        {method: 'GET'},
        boundedTimeout,
        label,
      );
      if (response.status !== 200) {
        await cancelBody(response, logger, label);
        throw new Error(`${label} returned HTTP ${response.status}.`);
      }
      assertResponseHeaders(response, {
        bytes: sample.length,
        contentType: 'image/webp',
        noTransform: true,
        label,
      });
      const [remote, localBytes] = await Promise.all([
        readExactBody(response, sample.length, label),
        readLocalRange(sample.packPath, sample.offset, sample.length),
      ]);
      if (!remote.equals(localBytes)) throw new Error(`${label} differs from the local pack range.`);
    });

    logger.info(
      `Public core publication ${publicationId} verified: ${documents.length} JSON objects and ` +
        `${sampleJobs.length} MRPI-authorized image samples across ${local.manifest.packs.length} packs.`,
    );
    return {
      publicationId,
      documents: documents.length,
      packs: local.manifest.packs.length,
      imageSamples: sampleJobs.length,
    };
  } catch (error) {
    logger.error(`Public core publication verification failed closed: ${error.message}`);
    throw error;
  }
}

function parseCliArguments(argv) {
  const names = new Map([
    ['--root', 'exportRoot'],
    ['--publication', 'publication'],
    ['--base-url', 'baseUrl'],
    ['--concurrency', 'concurrency'],
    ['--timeout-ms', 'timeoutMs'],
  ]);
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const name = names.get(flag);
    if (!name) throw new Error(`Unknown public core verifier argument: ${flag}.`);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`${flag} requires a value.`);
    if (options[name] !== undefined) throw new Error(`${flag} was provided more than once.`);
    options[name] = name === 'concurrency' || name === 'timeoutMs' ? Number(value) : value;
    index += 1;
  }
  if (!options.exportRoot || !options.publication || !options.baseUrl) {
    throw new Error(
      'Usage: node scripts/verify-core-dataset-publication-remote.mjs ' +
        '--root <packed-export-root> --publication <bundle/publication.json> ' +
        '--base-url <https://app/dataset/publications> [--concurrency <1-32>] ' +
        '[--timeout-ms <1-120000>].',
    );
  }
  return options;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    await verifyPublicCoreDatasetPublication(parseCliArguments(process.argv.slice(2)));
  } catch (error) {
    console.error(`Public core publication verifier terminated: ${error.message}`);
    process.exitCode = 1;
  }
}
