#!/usr/bin/env node
import {createHash, randomUUID} from 'node:crypto';
import {
  createReadStream,
  createWriteStream,
  promises as fs,
} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {Readable, Transform} from 'node:stream';
import {pipeline} from 'node:stream/promises';

const MIGRATION_PATH = '/api/admin/migration/';
const TABLES = [
  ['dataset_publications', ['publication_id', 'manifest_sha256', 'object_count', 'stored_bytes', 'committed_at']],
  ['dataset_channels', ['slug', 'display_name', 'minecraft_version', 'pack_version', 'publication_id', 'preview_asset_set_id', 'is_default', 'revision', 'activated_at']],
  ['modpacks', ['id', 'name', 'minecraft_version', 'snapshot_json', 'revision', 'created_at', 'updated_at']],
  ['feedback_reports', ['id', 'kind', 'title', 'message', 'contact', 'pack_slug', 'pack_name', 'page_url', 'user_agent', 'fingerprint_hash', 'created_at']],
  ['export_failure_reports', ['fingerprint', 'issue_number', 'issue_url', 'status', 'client_hash', 'created_at', 'updated_at']],
  ['recipe_favorites', ['pack_slug', 'publication_id', 'item_key', 'client_hash', 'recipe_category', 'recipe_index', 'updated_at']],
];
const METADATA_HEADERS = [
  'x-mrt-migration-bytes',
  'x-mrt-migration-custom-metadata',
  'x-mrt-migration-http-metadata',
  'x-mrt-migration-storage-class',
];
const TRANSIENT_UPLOAD_STATUSES = new Set([429, 500, 502, 503, 504]);
const MAX_UPLOAD_ATTEMPTS = 4;
const MAX_READ_ATTEMPTS = 4;

function usage(message) {
  if (message) console.error(message);
  console.error(`Usage:
  node scripts/migrate-sites-storage.mjs export-db --source <url> --source-token-file <path> --output <sql>
  node scripts/migrate-sites-storage.mjs copy-r2 --source <url> --destination <url> --source-token-file <path> --destination-token-file <path> [--concurrency 4]
  node scripts/migrate-sites-storage.mjs verify-r2 --source <url> --destination <url> --source-token-file <path> --destination-token-file <path>`);
  process.exitCode = 2;
}

function optionsFrom(argv) {
  const [command, ...rest] = argv;
  const options = {};
  for (let index = 0; index < rest.length; index += 2) {
    const flag = rest[index];
    const value = rest[index + 1];
    if (!flag?.startsWith('--') || value === undefined) throw new Error(`Invalid argument near ${flag ?? '(end)'}.`);
    const name = flag.slice(2);
    if (Object.hasOwn(options, name)) throw new Error(`Duplicate --${name}.`);
    options[name] = value;
  }
  return {command, options};
}

function required(options, name) {
  const value = options[name];
  if (!value) throw new Error(`Missing --${name}.`);
  return value;
}

function migrationOrigin(value, name) {
  const url = new URL(value);
  if (url.protocol !== 'https:' && url.hostname !== 'localhost' && url.hostname !== '127.0.0.1') {
    throw new Error(`--${name} must use HTTPS.`);
  }
  url.pathname = '/';
  url.search = '';
  url.hash = '';
  return url;
}

async function tokenFromFile(path) {
  const absolute = resolve(path);
  const stat = await fs.stat(absolute);
  if (!stat.isFile()) throw new Error(`Token path is not a file: ${absolute}`);
  if ((stat.mode & 0o077) !== 0) throw new Error(`Token file must not be accessible by group or other users: ${absolute}`);
  const token = (await fs.readFile(absolute, 'utf8')).trim();
  if (token.length < 32 || /\s/u.test(token)) throw new Error(`Token file is invalid: ${absolute}`);
  return token;
}

function endpoint(origin, route, search = {}) {
  const url = new URL(`${MIGRATION_PATH}${route}`, origin);
  for (const [key, value] of Object.entries(search)) {
    if (value !== null && value !== undefined && value !== '') url.searchParams.set(key, value);
  }
  return url;
}

async function checkedFetch(url, token, init = {}) {
  const method = init.method ?? 'GET';
  const retryable = method === 'GET' || method === 'HEAD';
  for (let attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt += 1) {
    let response;
    try {
      response = await fetch(url, {
        ...init,
        headers: {Authorization: `Bearer ${token}`, ...init.headers},
      });
    } catch (error) {
      if (!retryable || attempt === MAX_READ_ATTEMPTS) throw error;
      const delayMs = 250 * (2 ** (attempt - 1));
      console.warn(
        `Transient ${method} network failure for ${url.pathname}; ` +
        `retrying attempt ${attempt + 1}/${MAX_READ_ATTEMPTS} in ${delayMs}ms.`,
      );
      await new Promise(resolveDelay => setTimeout(resolveDelay, delayMs));
      continue;
    }
    if (response.ok) return response;
    const detail = (await response.text()).slice(0, 500);
    if (
      !retryable ||
      !TRANSIENT_UPLOAD_STATUSES.has(response.status) ||
      attempt === MAX_READ_ATTEMPTS
    ) {
      throw new Error(`${method} ${url.pathname} failed with ${response.status}: ${detail}`);
    }
    const delayMs = 250 * (2 ** (attempt - 1));
    console.warn(
      `Transient ${method} ${response.status} for ${url.pathname}; ` +
      `retrying attempt ${attempt + 1}/${MAX_READ_ATTEMPTS} in ${delayMs}ms.`,
    );
    await new Promise(resolveDelay => setTimeout(resolveDelay, delayMs));
  }
  throw new Error(`${method} ${url.pathname} exhausted its retry loop unexpectedly.`);
}

async function uploadMigrationObject(url, token, headers, tempPath) {
  for (let attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt += 1) {
    const response = await fetch(url, {
      method: 'PUT',
      headers: {Authorization: `Bearer ${token}`, ...headers},
      body: createReadStream(tempPath),
      duplex: 'half',
    });
    if (response.ok) return response;
    const detail = (await response.text()).slice(0, 500);
    if (!TRANSIENT_UPLOAD_STATUSES.has(response.status) || attempt === MAX_UPLOAD_ATTEMPTS) {
      throw new Error(`PUT ${url.pathname} failed with ${response.status}: ${detail}`);
    }
    const delayMs = 250 * (2 ** (attempt - 1));
    console.warn(
      `Transient PUT ${response.status} for ${url.searchParams.get('key')}; ` +
      `retrying attempt ${attempt + 1}/${MAX_UPLOAD_ATTEMPTS} in ${delayMs}ms.`,
    );
    await new Promise(resolveDelay => setTimeout(resolveDelay, delayMs));
  }
  throw new Error('Migration upload exhausted its retry loop unexpectedly.');
}

function sqlValue(value) {
  if (value === null) return 'NULL';
  if (typeof value === 'string') return `'${value.replaceAll("'", "''")}'`;
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  throw new Error(`Unsupported database value type: ${typeof value}`);
}

async function writeChunk(stream, value) {
  if (!stream.write(value)) await new Promise(resolveDrain => stream.once('drain', resolveDrain));
}

async function exportDatabase({source, sourceTokenFile, output}) {
  const origin = migrationOrigin(source, 'source');
  const token = await tokenFromFile(sourceTokenFile);
  const outputPath = resolve(output);
  const summaryResponse = await checkedFetch(endpoint(origin, 'database-summary'), token);
  const summary = await summaryResponse.json();
  if (summary.format !== 'mrt-storage-migration-database-v1') throw new Error('Source database summary has an unknown format.');
  const tempPath = `${outputPath}.${randomUUID()}.tmp`;
  const stream = createWriteStream(tempPath, {flags: 'wx', mode: 0o600});
  const exportedCounts = {};
  try {
    // Wrangler's remote D1 file importer supplies the atomic rollback boundary and rejects
    // explicit BEGIN/COMMIT statements.
    await writeChunk(stream, '-- Minecraft Recipe Tree standalone Cloudflare migration\n');
    for (const [table, columns] of [...TABLES].reverse()) await writeChunk(stream, `DELETE FROM ${table};\n`);
    for (const [table, columns] of TABLES) {
      let after = '';
      let count = 0;
      do {
        const response = await checkedFetch(endpoint(origin, 'database', {table, after}), token);
        const page = await response.json();
        if (page.format !== 'mrt-storage-migration-database-v1' || page.table !== table) throw new Error(`Source returned an invalid ${table} page.`);
        if (JSON.stringify(page.columns) !== JSON.stringify(columns)) throw new Error(`Source ${table} columns do not match the migration contract.`);
        for (const row of page.rows) {
          if (Object.keys(row).some(column => !columns.includes(column))) throw new Error(`Source ${table} row has an unexpected column.`);
          await writeChunk(stream, `INSERT INTO ${table} (${columns.join(', ')}) VALUES (${columns.map(column => sqlValue(row[column])).join(', ')});\n`);
          count += 1;
        }
        after = page.nextAfter ?? '';
      } while (after);
      exportedCounts[table] = count;
      if (summary.counts[table] !== count) throw new Error(`${table} changed during export (${summary.counts[table]} expected, ${count} read). Retry during the cutover write freeze.`);
      console.log(`Exported ${table}: ${count} rows`);
    }
    await new Promise((resolveEnd, rejectEnd) => stream.end(error => error ? rejectEnd(error) : resolveEnd()));
    await fs.rename(tempPath, outputPath);
    console.log(`Database export complete: ${outputPath}`);
  } catch (error) {
    stream.destroy();
    await fs.unlink(tempPath).catch(() => {});
    throw error;
  }
}

async function listObjects(origin, token) {
  const objects = new Map();
  let cursor = '';
  do {
    const response = await checkedFetch(endpoint(origin, 'objects', {cursor}), token);
    const page = await response.json();
    if (page.format !== 'mrt-storage-migration-objects-v1' || !Array.isArray(page.objects)) throw new Error('Source object inventory has an unknown format.');
    for (const object of page.objects) {
      if (objects.has(object.key)) throw new Error(`Object inventory repeated ${object.key}.`);
      objects.set(object.key, object);
    }
    cursor = page.truncated ? page.cursor : '';
    if (page.truncated && !cursor) throw new Error('Object inventory omitted its next cursor.');
  } while (cursor);
  return objects;
}

function comparableObject(object) {
  return JSON.stringify({
    key: object.key,
    size: object.size,
    customMetadata: object.customMetadata ?? {},
    httpMetadata: object.httpMetadata ?? {},
    storageClass: object.storageClass ?? 'Standard',
  });
}

async function downloadObject(origin, token, key, tempPath) {
  const response = await checkedFetch(endpoint(origin, 'object', {key}), token);
  if (!response.body) throw new Error(`Source omitted the body for ${key}.`);
  const expected = Number(response.headers.get('x-mrt-migration-bytes'));
  if (!Number.isSafeInteger(expected) || expected < 0) throw new Error(`Source returned an invalid size for ${key}.`);
  const hash = createHash('sha256');
  let bytes = 0;
  const counter = new Transform({transform(chunk, _encoding, callback) { bytes += chunk.length; hash.update(chunk); callback(null, chunk); }});
  await pipeline(Readable.fromWeb(response.body), counter, createWriteStream(tempPath, {flags: 'wx', mode: 0o600}));
  if (bytes !== expected) throw new Error(`Source body size changed for ${key}: expected ${expected}, received ${bytes}.`);
  return {bytes, sha256: hash.digest('hex'), headers: response.headers};
}

async function copyObject(sourceOrigin, sourceToken, destinationOrigin, destinationToken, object, tempDirectory) {
  const tempPath = join(tempDirectory, `${randomUUID()}.object`);
  try {
    const downloaded = await downloadObject(sourceOrigin, sourceToken, object.key, tempPath);
    const headers = {'Content-Length': String(downloaded.bytes), 'x-mrt-migration-sha256': downloaded.sha256};
    for (const name of METADATA_HEADERS) {
      const value = downloaded.headers.get(name);
      if (value === null) throw new Error(`Source omitted ${name} for ${object.key}.`);
      headers[name] = value;
    }
    const response = await uploadMigrationObject(
      endpoint(destinationOrigin, 'object', {key: object.key}),
      destinationToken,
      headers,
      tempPath,
    );
    const result = await response.json();
    return {bytes: downloaded.bytes, reused: result.reused === true};
  } finally {
    await fs.unlink(tempPath).catch(() => {});
  }
}

async function concurrentMap(values, concurrency, callback) {
  let index = 0;
  const workers = Array.from({length: Math.min(concurrency, values.length)}, async () => {
    while (true) {
      const current = index++;
      if (current >= values.length) return;
      await callback(values[current], current);
    }
  });
  await Promise.all(workers);
}

async function copyR2({source, destination, sourceTokenFile, destinationTokenFile, concurrency = '4'}) {
  const sourceOrigin = migrationOrigin(source, 'source');
  const destinationOrigin = migrationOrigin(destination, 'destination');
  const sourceToken = await tokenFromFile(sourceTokenFile);
  const destinationToken = await tokenFromFile(destinationTokenFile);
  const parallel = Number(concurrency);
  if (!Number.isSafeInteger(parallel) || parallel < 1 || parallel > 8) throw new Error('--concurrency must be an integer from 1 through 8.');
  const [sourceObjects, destinationObjects] = await Promise.all([
    listObjects(sourceOrigin, sourceToken),
    listObjects(destinationOrigin, destinationToken),
  ]);
  const destinationOnly = [...destinationObjects.keys()].filter(key => !sourceObjects.has(key));
  if (destinationOnly.length) {
    throw new Error(
      `Destination contains ${destinationOnly.length} object(s) absent from the source; ` +
      `first unexpected key: ${destinationOnly[0]}`,
    );
  }
  const values = [...sourceObjects.values()];
  const tempDirectory = await fs.mkdtemp(join(tmpdir(), 'mrt-storage-migration-'));
  let completed = 0;
  let copiedBytes = 0;
  let reused = 0;
  const recordProgress = (bytes, wasReused) => {
    completed += 1;
    copiedBytes += bytes;
    if (wasReused) reused += 1;
    if (completed % 100 === 0 || completed === values.length) {
      console.log(`Objects: ${completed}/${values.length}; bytes accounted: ${copiedBytes}; resumed: ${reused}`);
    }
  };
  try {
    console.log(`Copying ${values.length} immutable objects with concurrency ${parallel}.`);
    await concurrentMap(values, parallel, async object => {
      const existing = destinationObjects.get(object.key);
      if (existing) {
        if (comparableObject(existing) !== comparableObject(object)) {
          throw new Error(`Destination metadata conflicts with the migration source: ${object.key}`);
        }
        // The destination bucket was created empty for this migration, and every prior write passed
        // the import route's SHA-256 check. Matching immutable inventory entries can therefore be
        // resumed without downloading and uploading their bodies again.
        recordProgress(object.size, true);
        return;
      }
      const result = await copyObject(sourceOrigin, sourceToken, destinationOrigin, destinationToken, object, tempDirectory);
      recordProgress(result.bytes, result.reused);
    });
  } finally {
    await fs.rmdir(tempDirectory).catch(() => {});
  }
  await verifyR2WithCredentials(sourceOrigin, sourceToken, destinationOrigin, destinationToken);
}

async function verifyR2WithCredentials(sourceOrigin, sourceToken, destinationOrigin, destinationToken) {
  const [sourceObjects, destinationObjects] = await Promise.all([
    listObjects(sourceOrigin, sourceToken), listObjects(destinationOrigin, destinationToken),
  ]);
  const failures = [];
  for (const [key, source] of sourceObjects) {
    const destination = destinationObjects.get(key);
    if (!destination) failures.push(`missing destination object: ${key}`);
    else if (comparableObject(source) !== comparableObject(destination)) failures.push(`metadata mismatch: ${key}`);
  }
  for (const key of destinationObjects.keys()) if (!sourceObjects.has(key)) failures.push(`destination-only object: ${key}`);
  if (failures.length) throw new Error(`R2 inventory verification failed:\n${failures.slice(0, 20).join('\n')}${failures.length > 20 ? `\n...and ${failures.length - 20} more` : ''}`);
  const bytes = [...sourceObjects.values()].reduce((sum, object) => sum + object.size, 0);
  console.log(`R2 verification complete: ${sourceObjects.size} objects, ${bytes} bytes, metadata exact.`);
}

async function verifyR2({source, destination, sourceTokenFile, destinationTokenFile}) {
  return verifyR2WithCredentials(
    migrationOrigin(source, 'source'), await tokenFromFile(sourceTokenFile),
    migrationOrigin(destination, 'destination'), await tokenFromFile(destinationTokenFile),
  );
}

async function main() {
  let parsed;
  try { parsed = optionsFrom(process.argv.slice(2)); } catch (error) { usage(error.message); return; }
  const {command, options} = parsed;
  if (!command) { usage(); return; }
  if (command === 'export-db') return exportDatabase({source: required(options, 'source'), sourceTokenFile: required(options, 'source-token-file'), output: required(options, 'output')});
  if (command === 'copy-r2') return copyR2({source: required(options, 'source'), destination: required(options, 'destination'), sourceTokenFile: required(options, 'source-token-file'), destinationTokenFile: required(options, 'destination-token-file'), concurrency: options.concurrency});
  if (command === 'verify-r2') return verifyR2({source: required(options, 'source'), destination: required(options, 'destination'), sourceTokenFile: required(options, 'source-token-file'), destinationTokenFile: required(options, 'destination-token-file')});
  usage(`Unknown command: ${command}`);
}

main().catch(error => { console.error(error instanceof Error ? error.message : error); process.exitCode = 1; });
