#!/usr/bin/env node

import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {createServer as createHttpServer, request as httpRequest} from 'node:http';
import {createServer as createNetServer} from 'node:net';
import {cpus, arch, platform, release} from 'node:os';
import {
  access,
  lstat,
  mkdir,
  mkdtemp,
  open,
  readFile,
  readdir,
  realpath,
  rm,
  writeFile,
} from 'node:fs/promises';
import {constants as fsConstants} from 'node:fs';
import {basename, dirname, isAbsolute, join, parse, relative, resolve, sep} from 'node:path';
import {tmpdir} from 'node:os';
import {performance as nodePerformance} from 'node:perf_hooks';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import {validateLocalCoreDatasetPublication} from './build-core-dataset-publication.mjs';
import {GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY} from './core-dataset-publication-contract.mjs';
import {parsePackedImageAuthorizationIndex} from './packed-image-authorization.mjs';
import {validateLocalRecipePreviewSidecar} from './verify-recipe-preview-sidecar-remote.mjs';

const SCRIPT_PATH = fileURLToPath(import.meta.url);
const VIEWER_ROOT = resolve(dirname(SCRIPT_PATH), '..');
const MIB = 1024 * 1024;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const SAFE_DOCUMENT_PATH = /^[A-Za-z0-9._/-]+\.json$/;
const PACKED_IMAGE_PATH = /^assets\/s\/(\d+)-(\d+)-(\d+)\.webp$/;
const DATASET_READY_MARK = 'mrt:dataset-ready';
const DATASET_STATE_ATTRIBUTE = 'data-mrt-dataset-state';
const DATASET_PUBLICATION_ATTRIBUTE = 'data-mrt-dataset-publication-id';
export const MIN_ACTIVATION_BENCHMARK_RUNS = 3;
const DEFAULT_RUNS = MIN_ACTIVATION_BENCHMARK_RUNS;
const DEFAULT_TIMEOUT_MS = 30_000;
const CLEANUP_TIMEOUT_MS = 5_000;
const NETWORK_IDLE_MS = 750;
const HEAP_SAMPLE_INTERVAL_MS = 25;
const PROCESS_LOG_LIMIT_BYTES = 256 * 1024;
const MAX_DIST_FILES = 20_000;
const MAX_DIST_BYTES = 512 * MIB;
const MAX_TRAFFIC_OBJECTS = 20_000;
const GTNH_PROFILE = 'gtnh-1.7.10';
const GTNH_SLUG = 'gt-new-horizons';
export const COLD_DATASET_REPORT_SCHEMA_VERSION = 1;
export const COLD_BROWSER_DEFINITION =
  'fresh Chrome process/profile per run; HTTP cache disabled/cleared; shared warm local Worker and operating-system file cache';
const APPLICATION_UPSTREAM_MEDIA_TYPES = Object.freeze({
  '.avif': Object.freeze(['image/avif']),
  '.css': Object.freeze(['text/css']),
  '.gif': Object.freeze(['image/gif']),
  '.ico': Object.freeze(['image/x-icon', 'image/vnd.microsoft.icon']),
  '.jpeg': Object.freeze(['image/jpeg']),
  '.jpg': Object.freeze(['image/jpeg']),
  '.js': Object.freeze(['application/javascript', 'application/x-javascript', 'text/javascript']),
  '.mjs': Object.freeze(['application/javascript', 'application/x-javascript', 'text/javascript']),
  '.png': Object.freeze(['image/png']),
  '.svg': Object.freeze(['image/svg+xml']),
  '.wasm': Object.freeze(['application/wasm']),
  '.webp': Object.freeze(['image/webp']),
  '.woff': Object.freeze(['application/font-woff', 'font/woff']),
  '.woff2': Object.freeze(['font/woff2']),
});

export const ACTIVATION_THRESHOLDS = Object.freeze({
  eligible: Object.freeze({
    combinedDatasetBootstrapBytes: 72 * MIB,
    indexBootstrapBytes: 40 * MIB,
    bootstrapDocumentCount: 12,
    settledHeapBytes: 300 * MIB,
    peakHeapBytes: 400 * MIB,
    readyMs: 8_000,
  }),
  review: Object.freeze({
    combinedDatasetBootstrapBytes: 80 * MIB,
    indexBootstrapBytes: 48 * MIB,
    bootstrapDocumentCount: 16,
    settledHeapBytes: 320 * MIB,
    // These are hard ceilings, not a review band.
    peakHeapBytes: 400 * MIB,
    readyMs: 8_000,
  }),
});

function errorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

function delay(milliseconds) {
  return new Promise(resolveDelay => setTimeout(resolveDelay, milliseconds));
}

export class MonotonicDeadline {
  constructor(timeoutMs, now = () => nodePerformance.now()) {
    assertPositiveInteger(timeoutMs, 'Monotonic deadline timeout', 120_000);
    if (typeof now !== 'function') throw new Error('Monotonic deadline requires a clock function.');
    this.now = now;
    this.startedAt = now();
    if (!Number.isFinite(this.startedAt)) throw new Error('Monotonic clock returned an invalid start time.');
    this.expiresAt = this.startedAt + timeoutMs;
  }

  remainingMilliseconds(label) {
    const remaining = this.expiresAt - this.now();
    if (!Number.isFinite(remaining) || remaining <= 0) {
      throw new Error(`Cold-browser run deadline expired during ${label}.`);
    }
    return Math.max(1, Math.ceil(remaining));
  }

  async wait(promise, label) {
    const remaining = this.remainingMilliseconds(label);
    let timer;
    try {
      return await Promise.race([
        Promise.resolve(promise).then(value => {
          this.remainingMilliseconds(label);
          return value;
        }),
        new Promise((_, rejectTimeout) => {
          timer = setTimeout(
            () => rejectTimeout(new Error(`Cold-browser run deadline expired during ${label}.`)),
            remaining,
          );
        }),
      ]);
    } finally {
      if (timer !== undefined) clearTimeout(timer);
    }
  }

  async pause(milliseconds, label) {
    if (!Number.isFinite(milliseconds) || milliseconds < 0) {
      throw new Error(`Monotonic deadline pause must be a non-negative duration; received ${milliseconds}.`);
    }
    const remaining = this.remainingMilliseconds(label);
    if (milliseconds >= remaining) {
      throw new Error(`Cold-browser run deadline cannot fit ${label}.`);
    }
    await this.wait(delay(milliseconds), label);
  }
}

function assertPositiveInteger(value, label, maximum = Number.MAX_SAFE_INTEGER) {
  if (!Number.isSafeInteger(value) || value <= 0 || value > maximum) {
    throw new Error(`${label} must be a positive integer no greater than ${maximum}; received ${value}.`);
  }
  return value;
}

function assertBoundedText(value, label, maximum) {
  if (
    typeof value !== 'string' ||
    value.length === 0 ||
    value.length > maximum ||
    value.trim() !== value ||
    /[\u0000-\u001f\u007f]/u.test(value)
  ) {
    throw new Error(`${label} must be non-empty, trimmed text no longer than ${maximum} characters.`);
  }
  return value;
}

function parseOptionMap(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith('--') || token === '--') {
      throw new Error(`Unexpected positional argument ${JSON.stringify(token)}.`);
    }
    const equal = token.indexOf('=');
    const name = equal === -1 ? token.slice(2) : token.slice(2, equal);
    if (!/^[a-z][a-z0-9-]*$/.test(name)) {
      throw new Error(`Invalid option name ${JSON.stringify(token)}.`);
    }
    if (values.has(name)) throw new Error(`Option --${name} may be supplied only once.`);
    let value;
    if (equal !== -1) {
      value = token.slice(equal + 1);
    } else {
      value = argv[index + 1];
      if (value === undefined || value.startsWith('--')) {
        throw new Error(`Option --${name} requires a value.`);
      }
      index += 1;
    }
    if (value.length === 0) throw new Error(`Option --${name} cannot be empty.`);
    values.set(name, value);
  }
  return values;
}

function requireOption(values, name) {
  const value = values.get(name);
  if (value === undefined) throw new Error(`Missing required option --${name}.`);
  return value;
}

export function parseBenchmarkArguments(argv) {
  const values = parseOptionMap(argv);
  const supported = new Set([
    'chrome',
    'concurrency',
    'dist',
    'export-root',
    'output',
    'preview-sidecar',
    'publication',
    'runs',
    'slug',
    'timeout-ms',
  ]);
  for (const name of values.keys()) {
    if (!supported.has(name)) throw new Error(`Unsupported option --${name}.`);
  }
  const slug = requireOption(values, 'slug');
  if (!SLUG_PATTERN.test(slug) || slug.length > 80) {
    throw new Error('--slug must be a lowercase canonical dataset slug no longer than 80 characters.');
  }
  const runs = assertPositiveInteger(Number(values.get('runs') ?? DEFAULT_RUNS), '--runs', 10);
  if (runs < MIN_ACTIVATION_BENCHMARK_RUNS) {
    throw new Error(
      `--runs must be at least ${MIN_ACTIVATION_BENCHMARK_RUNS} for an activation-authorizing report.`,
    );
  }
  return {
    chrome: resolve(requireOption(values, 'chrome')),
    concurrency: assertPositiveInteger(
      Number(values.get('concurrency') ?? '8'),
      '--concurrency',
      32,
    ),
    dist: resolve(requireOption(values, 'dist')),
    exportRoot: resolve(requireOption(values, 'export-root')),
    output: resolve(requireOption(values, 'output')),
    previewSidecar: resolve(requireOption(values, 'preview-sidecar')),
    publication: resolve(requireOption(values, 'publication')),
    runs,
    slug,
    timeoutMs: assertPositiveInteger(
      Number(values.get('timeout-ms') ?? DEFAULT_TIMEOUT_MS),
      '--timeout-ms',
      120_000,
    ),
  };
}

async function assertPlainDirectory(path, label) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    throw new Error(`${label} is unavailable at ${path}: ${errorMessage(error)}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isDirectory()) {
    throw new Error(`${label} must be a plain directory: ${path}.`);
  }
  return info;
}

async function assertPlainFile(path, label, {executable = false} = {}) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    throw new Error(`${label} is unavailable at ${path}: ${errorMessage(error)}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`${label} must be a plain file: ${path}.`);
  }
  if (executable) {
    try {
      await access(path, fsConstants.X_OK);
    } catch (error) {
      throw new Error(`${label} is not executable at ${path}: ${errorMessage(error)}`, {cause: error});
    }
  }
  return info;
}

function isPathInside(root, candidate) {
  const relation = relative(root, candidate);
  return relation === '' || (relation !== '..' && !relation.startsWith(`..${sep}`) && !isAbsolute(relation));
}

async function canonicalPlainDirectory(path, label) {
  await assertPlainDirectory(path, label);
  return realpath(path);
}

export async function resolveIsolatedOutputTarget(options) {
  const outputParent = await canonicalPlainDirectory(dirname(options.output), 'Benchmark output parent');
  const output = resolve(outputParent, basename(options.output));
  let outputExists = false;
  try {
    await lstat(output);
    outputExists = true;
  } catch (error) {
    if (error?.code !== 'ENOENT') {
      throw new Error(`Benchmark output target could not be inspected: ${errorMessage(error)}`, {cause: error});
    }
  }
  if (outputExists) {
    throw new Error(`Benchmark output already exists; refusing to overwrite ${output}.`);
  }

  const protectedRoots = [
    ['production build', options.dist],
    ['core export', options.exportRoot],
    ['core publication bundle', dirname(options.publication)],
    ['preview sidecar', options.previewSidecar],
  ];
  for (const [label, path] of protectedRoots) {
    const root = await canonicalPlainDirectory(path, `Protected ${label} root`);
    if (isPathInside(root, output)) {
      throw new Error(`Benchmark output must be outside the canonical ${label} root ${root}.`);
    }
  }
  return output;
}

function framedHashUpdate(hash, value) {
  const bytes = Buffer.isBuffer(value) ? value : Buffer.from(value, 'utf8');
  const length = Buffer.allocUnsafe(8);
  length.writeBigUInt64BE(BigInt(bytes.length));
  hash.update(length).update(bytes);
}

async function listPlainFiles(root, current = root, output = []) {
  const entries = await readdir(current, {withFileTypes: true});
  for (const entry of entries) {
    const path = resolve(current, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`Build inventory refuses symbolic link ${path}.`);
    if (entry.isDirectory()) {
      await listPlainFiles(root, path, output);
    } else if (entry.isFile()) {
      output.push(path);
      if (output.length > MAX_DIST_FILES) {
        throw new Error(`Build inventory exceeds the ${MAX_DIST_FILES}-file benchmark bound.`);
      }
    } else {
      throw new Error(`Build inventory refuses special filesystem entry ${path}.`);
    }
  }
  return output;
}

function sameStableFilesystemState(left, right) {
  return [
    'dev',
    'ino',
    'mode',
    'nlink',
    'size',
    'mtimeNs',
    'ctimeNs',
  ].every(name => left?.[name] === right?.[name]);
}

function sameFilesystemIdentity(left, right) {
  return ['dev', 'ino', 'mode'].every(name => left?.[name] === right?.[name]);
}

function requireStableFilesystemState(before, after, label) {
  if (!sameStableFilesystemState(before, after)) {
    throw new Error(`${label} changed while the production build was being digested.`);
  }
}

async function lstatBigInt(path, label) {
  try {
    return await lstat(path, {bigint: true});
  } catch (error) {
    throw new Error(`${label} could not be inspected without following links at ${path}: ${errorMessage(error)}`, {
      cause: error,
    });
  }
}

async function requireBuildDirectory(path, label) {
  const info = await lstatBigInt(path, label);
  if (info.isSymbolicLink() || !info.isDirectory()) {
    throw new Error(`${label} must be a no-follow plain directory: ${path}.`);
  }
  return info;
}

async function requireBuildFile(path, label) {
  const info = await lstatBigInt(path, label);
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`${label} must be a no-follow plain regular file: ${path}.`);
  }
  if (info.nlink !== 1n) {
    throw new Error(`${label} must not be hard-linked: ${path}.`);
  }
  return info;
}

async function requireCanonicalNoSymlinkBuildRoot(value) {
  if (typeof value !== 'string' || value.length === 0 || value.includes('\0')) {
    throw new Error('Production build directory must be a non-empty filesystem path.');
  }
  const requested = resolve(value);
  const filesystemRoot = parse(requested).root;
  const pathComponents = [{
    path: filesystemRoot,
    snapshot: await requireBuildDirectory(filesystemRoot, 'Production build filesystem root'),
  }];
  let current = filesystemRoot;
  const remainder = relative(filesystemRoot, requested);
  for (const component of remainder === '' ? [] : remainder.split(sep)) {
    current = join(current, component);
    pathComponents.push({
      path: current,
      snapshot: await requireBuildDirectory(current, 'Production build path component'),
    });
  }
  const canonical = await realpath(requested);
  if (canonical !== requested) {
    throw new Error(
      `Production build directory must use its canonical no-symlink path; requested=${requested}, canonical=${canonical}.`,
    );
  }
  for (const component of pathComponents) {
    const after = await requireBuildDirectory(component.path, 'Production build path component');
    if (!sameFilesystemIdentity(component.snapshot, after)) {
      throw new Error(`Production build path component changed during canonicalization: ${component.path}.`);
    }
  }
  return {path: requested, pathComponents};
}

async function inventoryStableBuildTree(root) {
  const files = [];
  const directories = [];
  const visit = async directory => {
    const before = await requireBuildDirectory(directory, 'Production build directory');
    directories.push({path: directory, snapshot: before});
    let entries;
    try {
      entries = await readdir(directory, {withFileTypes: true});
    } catch (error) {
      throw new Error(`Production build directory could not be enumerated at ${directory}: ${errorMessage(error)}`, {
        cause: error,
      });
    }
    for (const entry of entries) {
      const path = join(directory, entry.name);
      const info = await lstatBigInt(path, 'Production build entry');
      if (info.isSymbolicLink()) {
        throw new Error(`Build inventory refuses symbolic link ${path}.`);
      }
      if (info.isDirectory()) {
        await visit(path);
      } else if (info.isFile()) {
        if (info.nlink !== 1n) {
          throw new Error(`Production build file must not be hard-linked: ${path}.`);
        }
        files.push({
          path,
          relativePath: relative(root, path).split(sep).join('/'),
          snapshot: info,
        });
        if (files.length > MAX_DIST_FILES) {
          throw new Error(`Build inventory exceeds the ${MAX_DIST_FILES}-file benchmark bound.`);
        }
      } else {
        throw new Error(`Build inventory refuses special filesystem entry ${path}.`);
      }
    }
    const after = await requireBuildDirectory(directory, 'Production build directory');
    requireStableFilesystemState(before, after, `Production build directory ${directory}`);
  };
  await visit(root);
  files.sort((left, right) => (
    left.relativePath < right.relativePath ? -1 : left.relativePath > right.relativePath ? 1 : 0
  ));
  return {directories, files};
}

function requireDigestBuildTreeOptions(options) {
  if (options === undefined) return Object.freeze({});
  if (!options || typeof options !== 'object' || Array.isArray(options)) {
    throw new Error('Build-tree digest options must be an object.');
  }
  const keys = Object.keys(options);
  if (keys.some(key => key !== 'afterFileOpen')) {
    throw new Error('Build-tree digest options contain an unsupported field.');
  }
  if (options.afterFileOpen !== undefined && typeof options.afterFileOpen !== 'function') {
    throw new Error('Build-tree digest afterFileOpen hook must be a function.');
  }
  return options;
}

async function readStableBuildFile(record, afterFileOpen) {
  const label = `Production build file ${record.path}`;
  const before = await requireBuildFile(record.path, label);
  requireStableFilesystemState(record.snapshot, before, label);
  if (!Number.isSafeInteger(fsConstants.O_NOFOLLOW)) {
    throw new Error('Production build cannot be digested securely because O_NOFOLLOW is unavailable.');
  }
  let handle;
  try {
    handle = await open(record.path, fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW);
  } catch (error) {
    throw new Error(`${label} could not be opened without following links: ${errorMessage(error)}`, {
      cause: error,
    });
  }
  let body;
  let opened;
  try {
    opened = await handle.stat({bigint: true});
    if (!opened.isFile() || opened.nlink !== 1n) {
      throw new Error(`${label} descriptor is not a single-link regular file.`);
    }
    requireStableFilesystemState(before, opened, label);
    await afterFileOpen?.({path: record.path, relativePath: record.relativePath});
    const size = Number(opened.size);
    if (!Number.isSafeInteger(size) || size < 0 || size > MAX_DIST_BYTES) {
      throw new Error(`${label} has an invalid bounded byte length ${opened.size}.`);
    }
    body = Buffer.alloc(size);
    let offset = 0;
    while (offset < body.length) {
      const {bytesRead} = await handle.read(body, offset, body.length - offset, offset);
      if (bytesRead === 0) throw new Error(`${label} was truncated while its descriptor was read.`);
      offset += bytesRead;
    }
    const probe = Buffer.allocUnsafe(1);
    const trailing = await handle.read(probe, 0, 1, body.length);
    const afterRead = await handle.stat({bigint: true});
    if (trailing.bytesRead !== 0) {
      throw new Error(`${label} grew while its descriptor was read.`);
    }
    requireStableFilesystemState(opened, afterRead, label);
  } finally {
    await handle.close();
  }
  const afterPath = await requireBuildFile(record.path, label);
  requireStableFilesystemState(opened, afterPath, label);
  return body;
}

async function digestBuildTreeSecurely(root, options) {
  const {afterFileOpen} = requireDigestBuildTreeOptions(options);
  const canonical = await requireCanonicalNoSymlinkBuildRoot(root);
  const canonicalRoot = canonical.path;
  const {directories, files} = await inventoryStableBuildTree(canonicalRoot);
  if (files.length === 0) throw new Error('Production build directory is empty.');
  let bytes = 0;
  for (const record of files) {
    const size = Number(record.snapshot.size);
    if (!Number.isSafeInteger(size) || size < 0) {
      throw new Error(`Production build file has invalid byte length ${record.snapshot.size}: ${record.path}.`);
    }
    bytes += size;
    if (!Number.isSafeInteger(bytes) || bytes > MAX_DIST_BYTES) {
      throw new Error(`Production build exceeds the ${MAX_DIST_BYTES}-byte benchmark bound.`);
    }
  }
  const hash = createHash('sha256');
  hash.update('mrt-cold-browser-build-tree-v1\0');
  for (const record of files) {
    const body = await readStableBuildFile(record, afterFileOpen);
    framedHashUpdate(hash, record.relativePath);
    framedHashUpdate(hash, body);
  }
  for (const directory of [...directories].reverse()) {
    const after = await requireBuildDirectory(directory.path, 'Production build directory');
    requireStableFilesystemState(
      directory.snapshot,
      after,
      `Production build directory ${directory.path}`,
    );
  }
  for (const component of canonical.pathComponents) {
    const after = await requireBuildDirectory(component.path, 'Production build path component');
    if (!sameFilesystemIdentity(component.snapshot, after)) {
      throw new Error(`Production build path component changed during digest: ${component.path}.`);
    }
  }
  return {sha256: hash.digest('hex'), files: files.length, bytes};
}

export async function digestBuildTree(root, options) {
  return digestBuildTreeSecurely(root, options);
}

export async function digestColdDatasetBenchmarkSource() {
  return createHash('sha256').update(await readFile(SCRIPT_PATH)).digest('hex');
}

export function assertSameBuildTree(before, after) {
  if (
    !before ||
    !after ||
    before.sha256 !== after.sha256 ||
    before.files !== after.files ||
    before.bytes !== after.bytes
  ) {
    throw new Error(
      `Production build changed during cold-browser measurement: ` +
        `before=${JSON.stringify(before)}, after=${JSON.stringify(after)}.`,
    );
  }
}

export async function verifyReadinessContractInBuild(dist) {
  const client = resolve(dist, 'client');
  await assertPlainDirectory(client, 'Production client build');
  const scripts = (await listPlainFiles(client)).filter(path => path.endsWith('.js'));
  let stateAttribute = false;
  let publicationAttribute = false;
  let readyMark = false;
  for (const path of scripts) {
    const source = await readFile(path, 'utf8');
    stateAttribute ||= source.includes(DATASET_STATE_ATTRIBUTE);
    publicationAttribute ||= source.includes(DATASET_PUBLICATION_ATTRIBUTE);
    readyMark ||= source.includes(DATASET_READY_MARK);
  }
  if (!stateAttribute || !publicationAttribute || !readyMark) {
    throw new Error(
      'Production client build is missing the exact dataset readiness contract; run npm run build before benchmarking.',
    );
  }
}

function parseJson(bytes, label) {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`${label} is invalid JSON: ${errorMessage(error)}`, {cause: error});
  }
}

function requireRecord(recordsByPath, path, label) {
  const record = recordsByPath.get(path);
  if (!record) throw new Error(`${label} is absent from the validated core publication: ${path}.`);
  return record;
}

function descriptorPartPaths(value, kind, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
  if (value.format !== 'mrt-sharded-json-v1') return [];
  if (value.kind !== kind || !Array.isArray(value.parts) || value.parts.length === 0) {
    throw new Error(`${label} has a malformed sharded-document descriptor.`);
  }
  return value.parts.map((part, index) => {
    if (
      !part ||
      typeof part !== 'object' ||
      Array.isArray(part) ||
      typeof part.path !== 'string' ||
      !SAFE_DOCUMENT_PATH.test(part.path) ||
      !Number.isSafeInteger(part.bytes) ||
      part.bytes <= 0
    ) {
      throw new Error(`${label}.parts[${index}] is not a canonical bounded shard descriptor.`);
    }
    return part.path;
  });
}

async function measureLogicalDocument(recordsByPath, rootPath, kind, {includeParts = true} = {}) {
  const rootRecord = requireRecord(recordsByPath, rootPath, `Bootstrap ${rootPath}`);
  const rootBytes = await readFile(rootRecord.localPath);
  if (rootBytes.length !== rootRecord.bytes) {
    throw new Error(`Validated bootstrap document changed before measurement: ${rootPath}.`);
  }
  const partPaths = descriptorPartPaths(parseJson(rootBytes, rootPath), kind, rootPath);
  const paths = includeParts ? [rootPath, ...partPaths] : [rootPath];
  let bytes = 0;
  for (const path of paths) bytes += requireRecord(recordsByPath, path, `Bootstrap shard ${path}`).bytes;
  if (!Number.isSafeInteger(bytes)) throw new Error(`${rootPath} bootstrap bytes exceed the safe integer range.`);
  return {bytes, paths};
}

export async function computeBootstrapMetrics(core, preview) {
  const recordsByPath = new Map(core.records.map(record => [record.path, record]));
  if (recordsByPath.size !== core.records.length) {
    throw new Error('Validated core publication unexpectedly contains duplicate object paths.');
  }
  const items = await measureLogicalDocument(recordsByPath, 'items.json', 'array');
  // The reverse-index descriptor is authenticated during bootstrap, but its bounded shards are
  // intentionally deferred until the first recipe or graph interaction.
  const index = await measureLogicalDocument(
    recordsByPath,
    'index.json',
    'object',
    {includeParts: false},
  );
  const fixedCorePaths = ['manifest.json', 'categories.json', 'mobs.json', 'blockdrops.json'];
  for (const path of fixedCorePaths) requireRecord(recordsByPath, path, `Required bootstrap document ${path}`);
  const applicationManifestRecord = requireRecord(
    recordsByPath,
    'manifest.json',
    'Required bootstrap document manifest.json',
  );
  const applicationManifestBytes = await readFile(applicationManifestRecord.localPath);
  if (applicationManifestBytes.length !== applicationManifestRecord.bytes) {
    throw new Error('Validated bootstrap manifest changed before measurement.');
  }
  const applicationManifest = parseJson(applicationManifestBytes, 'manifest.json');
  const publicationPolicy = core.manifest?.publicationPolicy;
  if (applicationManifest?.publicationPolicy !== publicationPolicy) {
    throw new Error(
      'Application and core-publication manifests disagree about the visual-assets policy.',
    );
  }
  const previewManifestRequired =
    publicationPolicy !== GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY &&
    applicationManifest?.web?.recipeImages?.mode === 'omitted';
  const previewBootstrapPaths = previewManifestRequired ? ['manifest.json'] : [];
  const uniqueDocuments = new Set([
    'catalog:/api/datasets',
    ...fixedCorePaths.map(path => `core:${path}`),
    ...items.paths.map(path => `core:${path}`),
    ...index.paths.map(path => `core:${path}`),
    ...previewBootstrapPaths.map(path => `preview:${path}`),
  ]);
  const combinedDatasetBootstrapBytes = items.bytes + index.bytes;
  if (!Number.isSafeInteger(combinedDatasetBootstrapBytes)) {
    throw new Error('Combined item/index bootstrap bytes exceed the safe integer range.');
  }
  return {
    itemsBootstrapBytes: items.bytes,
    indexBootstrapBytes: index.bytes,
    combinedDatasetBootstrapBytes,
    bootstrapDocumentCount: uniqueDocuments.size,
    coreBootstrapPaths: [...new Set([...fixedCorePaths, ...items.paths, ...index.paths])].sort(),
    previewBootstrapPaths,
    previewManifestBytes: previewManifestRequired ? preview.manifestBytes.length : 0,
  };
}

function maximumRunMetric(runs, name, select = run => run[name]) {
  if (!Array.isArray(runs) || runs.length === 0) throw new Error('Gate classification requires benchmark runs.');
  const values = runs.map((run, index) => {
    const value = select(run);
    if (!Number.isFinite(value) || value < 0) {
      throw new Error(`Benchmark run ${index + 1} has invalid ${name}: ${value}.`);
    }
    return value;
  });
  return Math.max(...values);
}

function thresholdViolations(staticMetrics, aggregate, threshold) {
  return [
    ['combinedDatasetBootstrapBytes', staticMetrics.combinedDatasetBootstrapBytes],
    ['indexBootstrapBytes', staticMetrics.indexBootstrapBytes],
    ['bootstrapDocumentCount', aggregate.worstBootstrapDocumentCount],
    ['settledHeapBytes', aggregate.worstSettledHeapBytes],
    ['peakHeapBytes', aggregate.worstPeakHeapBytes],
    ['readyMs', aggregate.worstReadyMs],
  ]
    .filter(([name, value]) => value > threshold[name])
    .map(([name, value]) => ({name, observed: value, limit: threshold[name]}));
}

export function classifyActivationGate(staticMetrics, runs, thresholds = ACTIVATION_THRESHOLDS) {
  const aggregate = {
    worstBootstrapDocumentCount: maximumRunMetric(
      runs,
      'proxyTraffic.bootstrapDocumentCount',
      run => run.proxyTraffic?.bootstrapDocumentCount,
    ),
    worstReadyMs: maximumRunMetric(runs, 'readyMs'),
    worstPeakHeapBytes: maximumRunMetric(runs, 'peakHeapBytes'),
    worstSettledHeapBytes: maximumRunMetric(runs, 'settledHeapBytes'),
  };
  const eligibleViolations = thresholdViolations(staticMetrics, aggregate, thresholds.eligible);
  if (eligibleViolations.length === 0) {
    return {decision: 'current-storage-eligible', aggregate, violations: []};
  }
  const reviewViolations = thresholdViolations(staticMetrics, aggregate, thresholds.review);
  if (reviewViolations.length === 0) {
    return {decision: 'operator-review-required', aggregate, violations: eligibleViolations};
  }
  return {decision: 'lazy-index-required', aggregate, violations: reviewViolations};
}

function requireExactReportObject(value, keys, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be an object.`);
  }
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
    throw new Error(`${label} violates the exact benchmark-report contract.`);
  }
  return value;
}

function requireReportInteger(value, label, {positive = false} = {}) {
  if (!Number.isSafeInteger(value) || value < (positive ? 1 : 0)) {
    throw new Error(`${label} must be a ${positive ? 'positive' : 'non-negative'} safe integer.`);
  }
  return value;
}

function requireReportNumber(value, label) {
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${label} must be a positive finite number.`);
  }
  return value;
}

function requireReportText(value, label, maximum = 4096) {
  return assertBoundedText(value, label, maximum);
}

function requireReportPathArray(value, label, {allowEmpty = false} = {}) {
  if (!Array.isArray(value) || (!allowEmpty && value.length === 0)) {
    throw new Error(`${label} must be ${allowEmpty ? 'an' : 'a non-empty'} array.`);
  }
  const paths = value.map((path, index) => requireReportText(path, `${label}[${index}]`, 1024));
  if (new Set(paths).size !== paths.length || !isDeepStrictEqual(paths, [...paths].sort())) {
    throw new Error(`${label} must contain unique paths in canonical sort order.`);
  }
  return paths;
}

function requireReportBuild(value, label) {
  const build = requireExactReportObject(value, ['bytes', 'files', 'sha256'], label);
  if (!SHA256_PATTERN.test(build.sha256)) throw new Error(`${label}.sha256 must be canonical SHA-256.`);
  requireReportInteger(build.files, `${label}.files`, {positive: true});
  requireReportInteger(build.bytes, `${label}.bytes`, {positive: true});
  return build;
}

function requireReportDataset(value, label) {
  const dataset = requireExactReportObject(value, [
    'displayName',
    'isDefault',
    'minecraftVersion',
    'packVersion',
    'previewAssetSetId',
    'publicationId',
    'slug',
  ], label);
  if (!SLUG_PATTERN.test(dataset.slug) || dataset.slug.length > 80) {
    throw new Error(`${label}.slug must be a canonical dataset slug.`);
  }
  requireReportText(dataset.displayName, `${label}.displayName`, 120);
  requireReportText(dataset.minecraftVersion, `${label}.minecraftVersion`, 40);
  requireReportText(dataset.packVersion, `${label}.packVersion`, 80);
  if (!SHA256_PATTERN.test(dataset.publicationId) || !SHA256_PATTERN.test(dataset.previewAssetSetId)) {
    throw new Error(`${label} publication identities must be canonical SHA-256 values.`);
  }
  if (dataset.isDefault !== true) {
    throw new Error(`${label}.isDefault must be true for the benchmark's isolated one-dataset catalog.`);
  }
  return dataset;
}

function requireReportStaticMetrics(value, label) {
  const metrics = requireExactReportObject(value, [
    'bootstrapDocumentCount',
    'combinedDatasetBootstrapBytes',
    'coreBootstrapPaths',
    'indexBootstrapBytes',
    'itemsBootstrapBytes',
    'previewBootstrapPaths',
    'previewManifestBytes',
  ], label);
  for (const name of [
    'bootstrapDocumentCount',
    'combinedDatasetBootstrapBytes',
    'indexBootstrapBytes',
    'itemsBootstrapBytes',
  ]) {
    requireReportInteger(metrics[name], `${label}.${name}`, {positive: true});
  }
  requireReportInteger(metrics.previewManifestBytes, `${label}.previewManifestBytes`);
  if (metrics.combinedDatasetBootstrapBytes !== metrics.itemsBootstrapBytes + metrics.indexBootstrapBytes) {
    throw new Error(`${label}.combinedDatasetBootstrapBytes does not equal its item/index inputs.`);
  }
  requireReportPathArray(metrics.coreBootstrapPaths, `${label}.coreBootstrapPaths`);
  requireReportPathArray(
    metrics.previewBootstrapPaths,
    `${label}.previewBootstrapPaths`,
    {allowEmpty: true},
  );
  if (
    (metrics.previewBootstrapPaths.length === 0 && metrics.previewManifestBytes !== 0) ||
    (metrics.previewBootstrapPaths.length > 0 && metrics.previewManifestBytes === 0)
  ) {
    throw new Error(
      `${label}.previewManifestBytes must be zero exactly when the browser does not fetch a preview manifest.`,
    );
  }
  if (
    metrics.bootstrapDocumentCount !==
      1 + metrics.coreBootstrapPaths.length + metrics.previewBootstrapPaths.length
  ) {
    throw new Error(`${label}.bootstrapDocumentCount does not match its exact document paths.`);
  }
  return metrics;
}

function requireReportTraffic(value, label) {
  const traffic = requireExactReportObject(value, [
    'bootstrapDocumentCount',
    'catalogRequests',
    'datasetRequests',
    'documentBodyBytes',
    'documentRequests',
    'imageBodyBytes',
    'imageRequests',
    'requests',
    'servedObjects',
    'totalDatasetBodyBytes',
  ], label);
  for (const name of [
    'bootstrapDocumentCount',
    'catalogRequests',
    'datasetRequests',
    'documentBodyBytes',
    'documentRequests',
    'imageBodyBytes',
    'imageRequests',
    'requests',
    'totalDatasetBodyBytes',
  ]) {
    requireReportInteger(traffic[name], `${label}.${name}`);
  }
  if (
    traffic.requests <= 0 ||
    traffic.requests < traffic.datasetRequests ||
    traffic.catalogRequests <= 0 ||
    traffic.catalogRequests > traffic.datasetRequests ||
    traffic.datasetRequests <= 0 ||
    traffic.documentRequests <= 0 ||
    traffic.documentBodyBytes <= 0 ||
    traffic.datasetRequests !== traffic.documentRequests + traffic.imageRequests ||
    traffic.totalDatasetBodyBytes !== traffic.documentBodyBytes + traffic.imageBodyBytes
  ) {
    throw new Error(`${label} contains internally inconsistent request or byte totals.`);
  }
  if (!Array.isArray(traffic.servedObjects) || traffic.servedObjects.length === 0) {
    throw new Error(`${label}.servedObjects must be a non-empty array.`);
  }
  const servedKeys = [];
  let documentCount = 0;
  let servedDatasetRequests = 0;
  let servedDocumentRequests = 0;
  let servedImageRequests = 0;
  let servedDocumentBodyBytes = 0;
  let servedImageBodyBytes = 0;
  for (const [index, candidate] of traffic.servedObjects.entries()) {
    const object = requireExactReportObject(
      candidate,
      ['key', 'kind', 'requests', 'responseBodyBytes'],
      `${label}.servedObjects[${index}]`,
    );
    servedKeys.push(requireReportText(object.key, `${label}.servedObjects[${index}].key`, 2048));
    if (object.kind !== 'document' && object.kind !== 'image') {
      throw new Error(`${label}.servedObjects[${index}].kind must be document or image.`);
    }
    documentCount += object.kind === 'document' ? 1 : 0;
    const requests = requireReportInteger(
      object.requests,
      `${label}.servedObjects[${index}].requests`,
      {positive: true},
    );
    const responseBodyBytes = requireReportInteger(
      object.responseBodyBytes,
      `${label}.servedObjects[${index}].responseBodyBytes`,
    );
    servedDatasetRequests += requests;
    if (object.kind === 'document') {
      servedDocumentRequests += requests;
      servedDocumentBodyBytes += responseBodyBytes;
    } else {
      servedImageRequests += requests;
      servedImageBodyBytes += responseBodyBytes;
    }
  }
  if (![
    servedDatasetRequests,
    servedDocumentRequests,
    servedImageRequests,
    servedDocumentBodyBytes,
    servedImageBodyBytes,
  ].every(Number.isSafeInteger)) {
    throw new Error(`${label} per-object evidence exceeds safe integer accounting.`);
  }
  if (new Set(servedKeys).size !== servedKeys.length || !isDeepStrictEqual(servedKeys, [...servedKeys].sort())) {
    throw new Error(`${label}.servedObjects must have unique keys in canonical sort order.`);
  }
  if (traffic.bootstrapDocumentCount !== documentCount) {
    throw new Error(`${label}.bootstrapDocumentCount does not match served document evidence.`);
  }
  if (
    traffic.datasetRequests !== servedDatasetRequests ||
    traffic.documentRequests !== servedDocumentRequests ||
    traffic.imageRequests !== servedImageRequests ||
    traffic.documentBodyBytes !== servedDocumentBodyBytes ||
    traffic.imageBodyBytes !== servedImageBodyBytes
  ) {
    throw new Error(`${label} totals do not match its per-object evidence.`);
  }
  return traffic;
}

function requireReportRun(value, index, staticMetrics) {
  const label = `Cold benchmark report.runs[${index}]`;
  const run = requireExactReportObject(value, [
    'cdpTraffic',
    'heapSampleCount',
    'peakHeapBytes',
    'proxyTraffic',
    'readyMs',
    'run',
    'settledHeapBytes',
    'settledHeapSamples',
  ], label);
  if (run.run !== index + 1) throw new Error(`${label}.run must be ${index + 1}.`);
  requireReportNumber(run.readyMs, `${label}.readyMs`);
  requireReportInteger(run.peakHeapBytes, `${label}.peakHeapBytes`, {positive: true});
  requireReportInteger(run.settledHeapBytes, `${label}.settledHeapBytes`, {positive: true});
  requireReportInteger(run.heapSampleCount, `${label}.heapSampleCount`, {positive: true});
  if (!Array.isArray(run.settledHeapSamples) || run.settledHeapSamples.length !== 3) {
    throw new Error(`${label}.settledHeapSamples must contain exactly three readings.`);
  }
  run.settledHeapSamples.forEach((sample, sampleIndex) => {
    requireReportInteger(sample, `${label}.settledHeapSamples[${sampleIndex}]`, {positive: true});
  });
  if (run.settledHeapBytes !== Math.max(...run.settledHeapSamples)) {
    throw new Error(`${label}.settledHeapBytes must equal the worst settled sample.`);
  }
  const cdp = requireExactReportObject(run.cdpTraffic, [
    'datasetCompletedRequests',
    'datasetDecodedBytes',
    'datasetEncodedBytes',
    'datasetRequests',
  ], `${label}.cdpTraffic`);
  for (const name of Object.keys(cdp)) {
    requireReportInteger(cdp[name], `${label}.cdpTraffic.${name}`, {positive: true});
  }
  if (cdp.datasetCompletedRequests !== cdp.datasetRequests) {
    throw new Error(`${label}.cdpTraffic did not complete every dataset request.`);
  }
  const proxy = requireReportTraffic(run.proxyTraffic, `${label}.proxyTraffic`);
  if (proxy.bootstrapDocumentCount !== staticMetrics.bootstrapDocumentCount) {
    throw new Error(`${label}.proxyTraffic does not match the static bootstrap document count.`);
  }
  return run;
}

export function requireCurrentStorageEligibleBenchmarkReport(value, {
  expectedBuild,
  expectedDataset,
  expectedSourceSha256,
}) {
  const report = requireExactReportObject(value, [
    'aggregate',
    'benchmark',
    'build',
    'chrome',
    'dataset',
    'decision',
    'generatedAt',
    'platform',
    'runs',
    'schemaVersion',
    'staticMetrics',
    'thresholds',
    'violations',
  ], 'Cold benchmark report');
  if (report.schemaVersion !== COLD_DATASET_REPORT_SCHEMA_VERSION) {
    throw new Error(
      `Cold benchmark report.schemaVersion must be ${COLD_DATASET_REPORT_SCHEMA_VERSION}.`,
    );
  }
  const generatedAt = requireReportText(report.generatedAt, 'Cold benchmark report.generatedAt', 64);
  let canonicalGeneratedAt;
  try {
    canonicalGeneratedAt = new Date(generatedAt).toISOString();
  } catch {
    canonicalGeneratedAt = null;
  }
  if (canonicalGeneratedAt !== generatedAt) {
    throw new Error('Cold benchmark report.generatedAt must be a canonical ISO-8601 instant.');
  }
  const benchmark = requireExactReportObject(
    report.benchmark,
    ['coldDefinition', 'runs', 'sourceSha256'],
    'Cold benchmark report.benchmark',
  );
  if (benchmark.coldDefinition !== COLD_BROWSER_DEFINITION) {
    throw new Error('Cold benchmark report uses a different cold-browser definition.');
  }
  if (!SHA256_PATTERN.test(expectedSourceSha256) || benchmark.sourceSha256 !== expectedSourceSha256) {
    throw new Error('Cold benchmark report source SHA-256 does not match the current benchmark implementation.');
  }
  requireReportInteger(benchmark.runs, 'Cold benchmark report.benchmark.runs', {positive: true});
  if (benchmark.runs < MIN_ACTIVATION_BENCHMARK_RUNS || benchmark.runs > 10) {
    throw new Error(
      `Cold benchmark report must contain between ${MIN_ACTIVATION_BENCHMARK_RUNS} and 10 runs.`,
    );
  }
  const platformEvidence = requireExactReportObject(report.platform, [
    'arch',
    'logicalCpuCount',
    'node',
    'platform',
    'release',
  ], 'Cold benchmark report.platform');
  for (const name of ['arch', 'node', 'platform', 'release']) {
    requireReportText(platformEvidence[name], `Cold benchmark report.platform.${name}`, 256);
  }
  requireReportInteger(
    platformEvidence.logicalCpuCount,
    'Cold benchmark report.platform.logicalCpuCount',
    {positive: true},
  );
  const chrome = requireExactReportObject(report.chrome, [
    'jsVersion',
    'product',
    'protocolVersion',
    'revision',
    'userAgent',
  ], 'Cold benchmark report.chrome');
  for (const name of Object.keys(chrome)) {
    requireReportText(chrome[name], `Cold benchmark report.chrome.${name}`, 2048);
  }
  const build = requireReportBuild(report.build, 'Cold benchmark report.build');
  const requiredBuild = requireReportBuild(expectedBuild, 'Expected production build');
  if (!isDeepStrictEqual(build, requiredBuild)) {
    throw new Error('Cold benchmark report build identity does not match the production dist tree.');
  }
  const dataset = requireReportDataset(report.dataset, 'Cold benchmark report.dataset');
  const requiredDataset = requireReportDataset(expectedDataset, 'Expected publication dataset');
  if (!isDeepStrictEqual(dataset, requiredDataset)) {
    throw new Error('Cold benchmark report dataset identity does not match the prepared publication plan.');
  }
  const staticMetrics = requireReportStaticMetrics(
    report.staticMetrics,
    'Cold benchmark report.staticMetrics',
  );
  if (!isDeepStrictEqual(report.thresholds, ACTIVATION_THRESHOLDS)) {
    throw new Error('Cold benchmark report thresholds do not match the current activation thresholds.');
  }
  if (!Array.isArray(report.runs) || report.runs.length !== benchmark.runs) {
    throw new Error('Cold benchmark report run evidence does not match benchmark.runs.');
  }
  report.runs.forEach((run, index) => requireReportRun(run, index, staticMetrics));
  const recomputed = classifyActivationGate(staticMetrics, report.runs);
  if (
    report.decision !== 'current-storage-eligible' ||
    !isDeepStrictEqual(report.aggregate, recomputed.aggregate) ||
    !isDeepStrictEqual(report.violations, recomputed.violations) ||
    report.decision !== recomputed.decision
  ) {
    throw new Error('Cold benchmark report does not reproduce a current-storage-eligible activation decision.');
  }
  return report;
}

function requireCanonicalRequestPath(path) {
  let decoded;
  try {
    decoded = decodeURIComponent(path);
  } catch (error) {
    throw new Error(`Request path has malformed percent encoding: ${path}.`, {cause: error});
  }
  if (
    decoded !== path ||
    path.length === 0 ||
    path.length > 1024 ||
    path.startsWith('/') ||
    path.includes('\\') ||
    !/^[A-Za-z0-9._/-]+$/.test(path) ||
    path.split('/').some(segment => segment.length === 0 || segment === '.' || segment === '..')
  ) {
    throw new Error(`Request path is not canonical: ${JSON.stringify(path)}.`);
  }
  return path;
}

export function requireCanonicalProxyUrl(requestUrl, expectedHost) {
  if (
    typeof requestUrl !== 'string' ||
    requestUrl.length === 0 ||
    requestUrl.length > 4096 ||
    !requestUrl.startsWith('/') ||
    requestUrl.startsWith('//') ||
    requestUrl.includes('\\') ||
    /[\u0000-\u0020\u007f]/u.test(requestUrl)
  ) {
    throw new Error(`Benchmark proxy request target is not canonical origin-form: ${JSON.stringify(requestUrl)}.`);
  }
  const expectedOrigin = `http://${expectedHost}`;
  let url;
  try {
    url = new URL(requestUrl, expectedOrigin);
  } catch (error) {
    throw new Error(`Benchmark proxy request target is invalid: ${JSON.stringify(requestUrl)}.`, {cause: error});
  }
  if (
    url.origin !== expectedOrigin ||
    url.hash !== '' ||
    requestUrl !== `${url.pathname}${url.search}`
  ) {
    throw new Error(`Benchmark proxy request target changed during URL parsing: ${JSON.stringify(requestUrl)}.`);
  }
  let decodedPathname;
  try {
    decodedPathname = decodeURIComponent(url.pathname);
  } catch (error) {
    throw new Error(`Benchmark proxy request path has malformed percent encoding: ${JSON.stringify(url.pathname)}.`, {
      cause: error,
    });
  }
  if (
    decodedPathname !== url.pathname ||
    !/^\/[A-Za-z0-9._/-]*$/.test(url.pathname) ||
    (url.pathname !== '/' &&
      url.pathname.slice(1).split('/').some(segment => segment.length === 0 || segment === '.' || segment === '..'))
  ) {
    throw new Error(`Benchmark proxy request path is not canonical: ${JSON.stringify(url.pathname)}.`);
  }
  return url;
}

function coordinateKey(offset, length) {
  return `${offset}:${length}`;
}

function buildCoreAuthorization(core) {
  const packs = core.manifest.packs.map((pack, packNumber) => {
    const record = core.records.find(candidate => candidate.path === pack.path);
    const indexBytes = core.indexPayloads.get(pack.index.path);
    if (!record || !indexBytes) throw new Error(`Validated core pack ${packNumber} is incomplete.`);
    const parsed = parsePackedImageAuthorizationIndex(indexBytes, {
      expectedPackNumber: packNumber,
      expectedPackBytes: pack.bytes,
    });
    return {
      path: record.localPath,
      bytes: pack.bytes,
      coordinates: new Set(parsed.entries.map(([offset, length]) => coordinateKey(offset, length))),
    };
  });
  return packs;
}

function buildPreviewAuthorization(preview) {
  return preview.manifest.packs.map((pack, packNumber) => ({
    path: resolve(preview.root, ...pack.path.split('/')),
    bytes: pack.bytes,
    coordinates: new Set(
      preview.coordinatesByPack[packNumber].map(({offset, length}) => coordinateKey(offset, length)),
    ),
  }));
}

async function readExactSlice(path, offset, length, expectedPackBytes) {
  const handle = await open(path, 'r');
  try {
    const info = await handle.stat();
    if (!info.isFile() || info.size !== expectedPackBytes) {
      throw new Error(`Authorized asset pack changed before range delivery: ${path}.`);
    }
    const body = Buffer.allocUnsafe(length);
    const {bytesRead} = await handle.read(body, 0, length, offset);
    if (bytesRead !== length) {
      throw new Error(`Authorized asset range read ${bytesRead}/${length} bytes from ${path}.`);
    }
    return body;
  } finally {
    await handle.close();
  }
}

function writeNodeResponse(response, status, headers, body, method) {
  response.writeHead(status, headers);
  response.end(method === 'HEAD' ? undefined : body);
}

function exactSearch(url, expected) {
  return url.search === expected;
}

function makeRunTraffic() {
  return {
    requests: 0,
    datasetRequests: 0,
    catalogRequests: 0,
    documentRequests: 0,
    imageRequests: 0,
    documentBodyBytes: 0,
    imageBodyBytes: 0,
    served: new Map(),
    failures: [],
  };
}

function recordServed(stats, key, kind, bytes, method) {
  const existing = stats.served.get(key);
  if (!existing && stats.served.size >= MAX_TRAFFIC_OBJECTS) {
    throw new Error(`Benchmark traffic exceeded the ${MAX_TRAFFIC_OBJECTS}-object evidence bound.`);
  }
  if (existing && existing.kind !== kind) {
    throw new Error(`Benchmark traffic classified ${key} as both ${existing.kind} and ${kind}.`);
  }
  const responseBodyBytes = method === 'HEAD' ? 0 : bytes;
  stats.datasetRequests += 1;
  stats.served.set(key, {
    kind,
    requests: (existing?.requests ?? 0) + 1,
    responseBodyBytes: (existing?.responseBodyBytes ?? 0) + responseBodyBytes,
  });
  if (kind === 'document') {
    stats.documentRequests += 1;
    stats.documentBodyBytes += responseBodyBytes;
  } else {
    stats.imageRequests += 1;
    stats.imageBodyBytes += responseBodyBytes;
  }
}

function publicTraffic(stats) {
  const servedObjects = [...stats.served.entries()]
    .sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0))
    .map(([key, evidence]) => ({key, ...evidence}));
  return {
    requests: stats.requests,
    datasetRequests: stats.datasetRequests,
    catalogRequests: stats.catalogRequests,
    documentRequests: stats.documentRequests,
    imageRequests: stats.imageRequests,
    documentBodyBytes: stats.documentBodyBytes,
    imageBodyBytes: stats.imageBodyBytes,
    totalDatasetBodyBytes: stats.documentBodyBytes + stats.imageBodyBytes,
    bootstrapDocumentCount: servedObjects.filter(object => object.kind === 'document').length,
    servedObjects,
  };
}

export function validateRunTraffic(stats, bootstrapMetrics) {
  if (stats.failures.length > 0) {
    throw new Error(`Local publication proxy reported failures: ${stats.failures.join(' | ')}`);
  }
  const required = [
    'catalog:/api/datasets',
    ...bootstrapMetrics.coreBootstrapPaths.map(path => `core:${path}`),
    ...bootstrapMetrics.previewBootstrapPaths.map(path => `preview:${path}`),
  ];
  const requiredSet = new Set(required);
  const missing = required.filter(key => {
    const evidence = stats.served.get(key);
    return !evidence || evidence.requests <= 0 || evidence.responseBodyBytes <= 0;
  });
  if (missing.length > 0) {
    throw new Error(`Cold browser omitted required bootstrap request(s): ${missing.join(', ')}.`);
  }
  const unexpectedDocuments = [...stats.served.entries()]
    .filter(([key, evidence]) => evidence.kind === 'document' && !requiredSet.has(key))
    .map(([key]) => key)
    .sort();
  if (unexpectedDocuments.length > 0) {
    throw new Error(
      `Cold browser eagerly loaded unexpected bootstrap dataset document(s): ${unexpectedDocuments.join(', ')}.`,
    );
  }
  if ((stats.served.get('core:manifest.json')?.requests ?? 0) < 2) {
    throw new Error('Cold browser did not perform the required post-bootstrap manifest confirmation.');
  }
  if (stats.datasetRequests <= 0 || stats.documentBodyBytes <= 0) {
    throw new Error('Cold browser produced no measurable dataset traffic.');
  }
  return publicTraffic(stats);
}

function headerValue(headers, name) {
  const value = headers?.[name];
  if (Array.isArray(value)) return value.join(',');
  return typeof value === 'string' ? value : '';
}

function responseMediaTypes(headers) {
  return headerValue(headers, 'content-type')
    .split(',')
    .map(value => value.split(';', 1)[0].trim().toLowerCase())
    .filter(Boolean);
}

function allowedApplicationUpstreamMediaTypes(pathname) {
  if (pathname === '/') return ['text/html'];
  // Vinext can recover initial hydration from the root RSC document when the HTML stream did not
  // embed a complete payload. This exact route is application framing, not a dataset document.
  if (pathname === '/.rsc') return ['text/x-component'];
  if (pathname === '/favicon.ico') return APPLICATION_UPSTREAM_MEDIA_TYPES['.ico'];
  if (pathname === '/favicon.svg') return APPLICATION_UPSTREAM_MEDIA_TYPES['.svg'];
  if (pathname === '/apple-touch-icon.png') return APPLICATION_UPSTREAM_MEDIA_TYPES['.png'];
  if (typeof pathname !== 'string' || !pathname.startsWith('/assets/')) {
    throw new Error(
      `Benchmark proxy refuses non-allowlisted application resource ${JSON.stringify(pathname)}.`,
    );
  }
  requireCanonicalRequestPath(pathname.slice(1));
  const name = basename(pathname);
  const dot = name.lastIndexOf('.');
  const extension = dot <= 0 ? '' : name.slice(dot).toLowerCase();
  const allowed = APPLICATION_UPSTREAM_MEDIA_TYPES[extension];
  if (!allowed) {
    throw new Error(
      `Benchmark proxy refuses non-allowlisted application asset type ${JSON.stringify(pathname)}.`,
    );
  }
  return allowed;
}

export function assertAllowedApplicationUpstreamResponse(pathname, headers = {}) {
  const allowed = allowedApplicationUpstreamMediaTypes(pathname);
  const mediaTypes = responseMediaTypes(headers);
  if (mediaTypes.length !== 1 || !allowed.includes(mediaTypes[0])) {
    throw new Error(
      `Benchmark proxy refuses application resource ${JSON.stringify(pathname)} with ` +
        `Content-Type ${JSON.stringify(headerValue(headers, 'content-type'))}; expected exactly one of ` +
        `${allowed.join(', ')}.`,
    );
  }
}

export async function proxyUpstream(request, response, upstreamPort, pathname) {
  // Only the HTML shell and Vite's typed immutable assets may pass through. Dataset routes are
  // handled above, while APIs, RSC documents, JSON, and untyped extensionless responses fail
  // before they can escape bootstrap accounting.
  allowedApplicationUpstreamMediaTypes(pathname);
  await new Promise((resolveRequest, rejectRequest) => {
    const upstream = httpRequest(
      {
        host: '127.0.0.1',
        port: upstreamPort,
        method: request.method,
        path: request.url,
        headers: {...request.headers, host: `127.0.0.1:${upstreamPort}`},
      },
      upstreamResponse => {
        try {
          assertAllowedApplicationUpstreamResponse(pathname, upstreamResponse.headers);
        } catch (error) {
          upstreamResponse.resume();
          rejectRequest(error);
          return;
        }
        response.writeHead(upstreamResponse.statusCode ?? 502, upstreamResponse.headers);
        upstreamResponse.pipe(response);
        upstreamResponse.once('end', resolveRequest);
        upstreamResponse.once('error', rejectRequest);
      },
    );
    upstream.once('error', rejectRequest);
    request.pipe(upstream);
  });
}

function createPublicationProxy({core, preview, descriptor, upstreamPort}) {
  const coreRecords = new Map(
    core.records.filter(record => record.path.endsWith('.json')).map(record => [record.path, record]),
  );
  const previewRecords = new Map(
    preview.manifest.categoryDocuments.map(record => [
      record.path,
      {...record, localPath: resolve(preview.root, ...record.path.split('/'))},
    ]),
  );
  previewRecords.set('manifest.json', {
    path: 'manifest.json',
    bytes: preview.manifestBytes.length,
    localPath: resolve(preview.root, 'manifest.json'),
  });
  const corePacks = buildCoreAuthorization(core);
  const previewPacks = buildPreviewAuthorization(preview);
  let expectedHost = null;
  let stats = makeRunTraffic();

  async function serveDocument(response, request, record, key) {
    const body = await readFile(record.localPath);
    if (body.length !== record.bytes) throw new Error(`Validated document changed before delivery: ${key}.`);
    recordServed(stats, key, 'document', body.length, request.method);
    writeNodeResponse(
      response,
      200,
      {'content-type': 'application/json; charset=utf-8', 'content-length': body.length, 'cache-control': 'no-store'},
      body,
      request.method,
    );
  }

  async function serveCoordinate(response, request, packs, path, keyPrefix) {
    const match = PACKED_IMAGE_PATH.exec(path);
    if (!match) throw new Error(`Unknown immutable dataset object ${path}.`);
    const packNumber = Number(match[1]);
    const offset = Number(match[2]);
    const length = Number(match[3]);
    const pack = packs[packNumber];
    if (
      !pack ||
      String(packNumber).padStart(3, '0') !== match[1] ||
      !Number.isSafeInteger(offset) ||
      String(offset) !== match[2] ||
      !Number.isSafeInteger(length) ||
      length <= 0 ||
      String(length) !== match[3] ||
      !pack.coordinates.has(coordinateKey(offset, length))
    ) {
      throw new Error(`Dataset request is not an exact MRPI-authorized coordinate: ${path}.`);
    }
    const body = await readExactSlice(pack.path, offset, length, pack.bytes);
    recordServed(stats, `${keyPrefix}:${path}`, 'image', body.length, request.method);
    writeNodeResponse(
      response,
      200,
      {
        'content-type': 'image/webp',
        'content-length': body.length,
        'cache-control': 'public, max-age=31536000, immutable, no-transform',
      },
      body,
      request.method,
    );
  }

  const server = createHttpServer((request, response) => {
    (async () => {
      stats.requests += 1;
      if (expectedHost === null || request.headers.host !== expectedHost) {
        throw new Error(`Benchmark proxy rejected Host ${JSON.stringify(request.headers.host)}.`);
      }
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        throw new Error(`Benchmark proxy rejects method ${request.method}.`);
      }
      const url = requireCanonicalProxyUrl(request.url, expectedHost);
      if (url.pathname === '/api/datasets') {
        if (!exactSearch(url, '')) throw new Error('Dataset catalog request has an unexpected query string.');
        const body = Buffer.from(`${JSON.stringify({datasets: [descriptor]})}\n`, 'utf8');
        stats.catalogRequests += 1;
        recordServed(stats, 'catalog:/api/datasets', 'document', body.length, request.method);
        writeNodeResponse(
          response,
          200,
          {'content-type': 'application/json; charset=utf-8', 'content-length': body.length, 'cache-control': 'no-store'},
          body,
          request.method,
        );
        return;
      }
      const corePrefix = `/dataset/publications/${descriptor.publicationId}/exports/`;
      if (url.pathname.startsWith(corePrefix)) {
        if (!exactSearch(url, `?dataset=${descriptor.publicationId}`)) {
          throw new Error('Core dataset request is missing its exact paired publication query.');
        }
        const path = requireCanonicalRequestPath(url.pathname.slice(corePrefix.length));
        const record = coreRecords.get(path);
        if (record) return await serveDocument(response, request, record, `core:${path}`);
        return await serveCoordinate(response, request, corePacks, path, 'core');
      }
      const previewPrefix = `/dataset/preview-sets/${descriptor.previewAssetSetId}/`;
      if (url.pathname.startsWith(previewPrefix)) {
        if (
          !exactSearch(
            url,
            `?dataset=${descriptor.publicationId}&preview=${descriptor.previewAssetSetId}`,
          )
        ) {
          throw new Error('Preview request is missing its exact paired publication/asset-set query.');
        }
        const path = requireCanonicalRequestPath(url.pathname.slice(previewPrefix.length));
        const record = previewRecords.get(path);
        if (record) return await serveDocument(response, request, record, `preview:${path}`);
        return await serveCoordinate(response, request, previewPacks, path, 'preview');
      }
      await proxyUpstream(request, response, upstreamPort, url.pathname);
    })().catch(error => {
      const message = errorMessage(error);
      stats.failures.push(message);
      console.error('Cold-browser publication proxy failed closed.', {url: request.url, error});
      if (!response.headersSent) {
        writeNodeResponse(
          response,
          500,
          {'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store'},
          Buffer.from('Benchmark proxy rejected request\n', 'utf8'),
          request.method,
        );
      } else {
        response.destroy(error instanceof Error ? error : new Error(message));
      }
    });
  });

  return {
    server,
    beginRun() {
      stats = makeRunTraffic();
      return stats;
    },
    setExpectedHost(host) {
      expectedHost = host;
    },
  };
}

async function listenLoopback(server) {
  await new Promise((resolveListen, rejectListen) => {
    server.once('error', rejectListen);
    server.listen(0, '127.0.0.1', () => {
      server.off('error', rejectListen);
      resolveListen();
    });
  });
  const address = server.address();
  if (!address || typeof address === 'string' || address.address !== '127.0.0.1') {
    throw new Error('Local benchmark server did not bind an ephemeral IPv4 loopback port.');
  }
  return address.port;
}

export async function closeHttpServer(server, timeoutMs = CLEANUP_TIMEOUT_MS) {
  assertPositiveInteger(timeoutMs, 'HTTP server cleanup timeout', 60_000);
  let timer;
  let closeStarted = false;
  const closed = new Promise((resolveClose, rejectClose) => {
    try {
      // server.close() synchronously stops new accepts. Force-closing before this call leaves a
      // race in which one more connection can be accepted and keep cleanup alive indefinitely.
      server.close(error => (error ? rejectClose(error) : resolveClose()));
      closeStarted = true;
    } catch (error) {
      rejectClose(error);
    }
  });
  const forceErrors = [];
  if (closeStarted) {
    try {
      server.closeIdleConnections?.();
    } catch (error) {
      forceErrors.push(error);
    }
    try {
      server.closeAllConnections?.();
    } catch (error) {
      forceErrors.push(error);
    }
  }
  let closeError = null;
  try {
    await Promise.race([
      closed,
      new Promise((_, rejectTimeout) => {
        timer = setTimeout(
          () => rejectTimeout(new Error(`HTTP server cleanup exceeded ${timeoutMs} ms.`)),
          timeoutMs,
        );
      }),
    ]);
  } catch (error) {
    closeError = error;
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
  if (closeError || forceErrors.length > 0) {
    throw new AggregateError(
      [...(closeError ? [closeError] : []), ...forceErrors],
      'HTTP server cleanup failed after stopping new accepts.',
    );
  }
}

async function reserveLoopbackPort(timeoutMs = CLEANUP_TIMEOUT_MS) {
  assertPositiveInteger(timeoutMs, 'Loopback port reservation cleanup timeout', 60_000);
  const sockets = new Set();
  const server = createNetServer(socket => {
    sockets.add(socket);
    socket.once('close', () => sockets.delete(socket));
    // A reservation never speaks a protocol. Destroying an accepted socket prevents a local
    // peer from pinning server.close() and turns port selection into a bounded operation.
    socket.destroy();
  });
  let port;
  let primaryError = null;
  try {
    port = await listenLoopback(server);
  } catch (error) {
    primaryError = error;
  }
  if (server.listening) {
    let timer;
    try {
      const closed = new Promise((resolveClose, rejectClose) => {
        server.close(error => (error ? rejectClose(error) : resolveClose()));
      });
      for (const socket of sockets) socket.destroy();
      await Promise.race([
        closed,
        new Promise((_, rejectTimeout) => {
          timer = setTimeout(
            () => rejectTimeout(new Error(`Loopback port reservation cleanup exceeded ${timeoutMs} ms.`)),
            timeoutMs,
          );
        }),
      ]);
    } catch (error) {
      primaryError = primaryError
        ? new AggregateError([primaryError, error], 'Loopback port reservation and cleanup failed.')
        : error;
    } finally {
      if (timer !== undefined) clearTimeout(timer);
    }
  }
  if (primaryError) throw primaryError;
  return port;
}

function boundedProcessLog(stream, label) {
  let text = '';
  let bytes = 0;
  let truncated = false;
  stream.setEncoding('utf8');
  stream.on('data', chunk => {
    bytes += Buffer.byteLength(chunk);
    if (bytes <= PROCESS_LOG_LIMIT_BYTES) text += chunk;
    else truncated = true;
  });
  return {
    assertComplete() {
      if (truncated) throw new Error(`${label} exceeded the ${PROCESS_LOG_LIMIT_BYTES}-byte capture bound.`);
    },
    text() {
      return text.trim();
    },
  };
}

export function isolatedRuntimePaths(runtimeRoot) {
  const root = resolve(runtimeRoot);
  const home = resolve(root, 'home');
  return Object.freeze({
    root,
    home,
    config: resolve(root, 'xdg-config'),
    cache: resolve(root, 'xdg-cache'),
    data: resolve(root, 'xdg-data'),
    state: resolve(root, 'xdg-state'),
    runtime: resolve(root, 'xdg-runtime'),
    temporary: resolve(root, 'tmp'),
    appData: resolve(home, 'AppData', 'Roaming'),
    localAppData: resolve(home, 'AppData', 'Local'),
  });
}

async function prepareIsolatedRuntimeDirectories(runtimeRoot) {
  const paths = isolatedRuntimePaths(runtimeRoot);
  await Promise.all(
    [...new Set(Object.values(paths).filter(path => path !== paths.root))].map(path =>
      mkdir(path, {recursive: true, mode: 0o700}),
    ),
  );
  return paths;
}

function isolatedBaseEnvironment(runtimeRoot, sourceEnvironment = process.env) {
  const paths = isolatedRuntimePaths(runtimeRoot);
  const environment = {
    HOME: paths.home,
    USERPROFILE: paths.home,
    APPDATA: paths.appData,
    LOCALAPPDATA: paths.localAppData,
    XDG_CONFIG_HOME: paths.config,
    XDG_CACHE_HOME: paths.cache,
    XDG_DATA_HOME: paths.data,
    XDG_STATE_HOME: paths.state,
    XDG_RUNTIME_DIR: paths.runtime,
    TMPDIR: paths.temporary,
    TMP: paths.temporary,
    TEMP: paths.temporary,
  };
  for (const name of ['PATH', 'LANG', 'LC_ALL']) {
    if (sourceEnvironment[name] !== undefined) environment[name] = sourceEnvironment[name];
  }
  return environment;
}

export function isolatedProcessEnvironment(runtimeRoot, sourceEnvironment = process.env) {
  return {
    ...isolatedBaseEnvironment(runtimeRoot, sourceEnvironment),
    CI: '1',
    NO_COLOR: '1',
    WRANGLER_WRITE_LOGS: 'false',
    WRANGLER_LOG_PATH: resolve(runtimeRoot, 'wrangler.log'),
    MINIFLARE_REGISTRY_PATH: resolve(runtimeRoot, 'miniflare-registry.json'),
  };
}

async function waitForWrangler(url, child, timeoutMs, logs) {
  const expiresAt = nodePerformance.now() + timeoutMs;
  let lastError = null;
  while (nodePerformance.now() < expiresAt) {
    if (child.exitCode !== null || child.signalCode !== null) {
      logs.stdout.assertComplete();
      logs.stderr.assertComplete();
      throw new Error(
        `Local Wrangler exited before readiness (code=${child.exitCode}, signal=${child.signalCode}). ` +
          `stdout=${JSON.stringify(logs.stdout.text())} stderr=${JSON.stringify(logs.stderr.text())}`,
      );
    }
    try {
      const remaining = Math.max(1, Math.ceil(expiresAt - nodePerformance.now()));
      const response = await fetch(url, {
        redirect: 'manual',
        signal: AbortSignal.timeout(Math.min(2_000, remaining)),
      });
      if (response.status >= 200 && response.status < 400) {
        await response.arrayBuffer();
        return;
      }
      lastError = new Error(`HTTP ${response.status}`);
    } catch (error) {
      lastError = error;
    }
    const retryBudget = expiresAt - nodePerformance.now();
    if (retryBudget > 0) await delay(Math.min(100, retryBudget));
  }
  throw new Error(`Local Wrangler did not become ready within ${timeoutMs} ms: ${errorMessage(lastError)}.`);
}

async function startWrangler({dist, runtimeRoot, timeoutMs}) {
  const modulePath = resolve(VIEWER_ROOT, 'node_modules/wrangler/bin/wrangler.js');
  const config = resolve(dist, 'server/wrangler.json');
  await assertPlainFile(modulePath, 'Pinned Wrangler module');
  await assertPlainFile(config, 'Production Wrangler configuration');
  await assertPlainFile(resolve(dist, 'server/index.js'), 'Production Worker entry point');
  await prepareIsolatedRuntimeDirectories(runtimeRoot);
  const emptyEnvironment = resolve(runtimeRoot, 'empty.env');
  await writeFile(emptyEnvironment, '', {flag: 'wx', mode: 0o600});
  const port = await reserveLoopbackPort();
  let child = null;
  let logs = null;
  try {
    child = spawn(
      process.execPath,
      [
        modulePath,
        'dev',
        '--config',
        config,
        '--local',
        '--ip',
        '127.0.0.1',
        '--port',
        String(port),
        '--persist-to',
        resolve(runtimeRoot, 'wrangler-state'),
        '--env-file',
        emptyEnvironment,
        '--log-level',
        'error',
      ],
      {
        cwd: VIEWER_ROOT,
        env: isolatedProcessEnvironment(runtimeRoot),
        stdio: ['ignore', 'pipe', 'pipe'],
      },
    );
    logs = {
      stdout: boundedProcessLog(child.stdout, 'Wrangler stdout'),
      stderr: boundedProcessLog(child.stderr, 'Wrangler stderr'),
    };
    child.once('error', error => console.error('Local Wrangler process failed.', error));
    await waitForWrangler(`http://127.0.0.1:${port}/`, child, timeoutMs, logs);
    return {child, logs, port};
  } catch (primaryError) {
    const cleanupErrors = [];
    if (child) {
      try {
        await stopChildProcess(child, 'Failed local Wrangler startup');
      } catch (error) {
        console.error('Failed local Wrangler startup cleanup failed.', {
          error,
          stdout: logs?.stdout.text() ?? '',
          stderr: logs?.stderr.text() ?? '',
        });
        cleanupErrors.push(error);
      }
    }
    if (cleanupErrors.length > 0) {
      throw new AggregateError([primaryError, ...cleanupErrors], 'Local Wrangler startup and cleanup failed.');
    }
    throw primaryError;
  }
}

async function stopChildProcess(child, label) {
  if (child.exitCode !== null || child.signalCode !== null) return;
  const closed = new Promise(resolveClose => child.once('close', resolveClose));
  if (!child.kill('SIGTERM')) throw new Error(`${label} refused SIGTERM.`);
  if (await Promise.race([closed.then(() => true), delay(5_000).then(() => false)])) return;
  console.error(`${label} did not stop after SIGTERM; issuing SIGKILL.`);
  child.kill('SIGKILL');
  await Promise.race([closed, delay(5_000)]);
  if (child.exitCode === null && child.signalCode === null) {
    throw new Error(`${label} remained alive after SIGKILL.`);
  }
  throw new Error(`${label} required SIGKILL during cleanup.`);
}

class CdpClient {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    socket.addEventListener('message', event => this.receive(event.data));
    socket.addEventListener('error', event => this.failAll(new Error(`CDP WebSocket error: ${event.message ?? 'unknown error'}.`)));
    socket.addEventListener('close', () => this.failAll(new Error('CDP WebSocket closed.')));
  }

  static async connect(url, deadline) {
    const socket = new WebSocket(url);
    try {
      await deadline.wait(new Promise((resolveOpen, rejectOpen) => {
        socket.addEventListener('open', resolveOpen, {once: true});
        socket.addEventListener(
          'error',
          () => rejectOpen(new Error('CDP WebSocket could not open.')),
          {once: true},
        );
      }), 'CDP WebSocket connection');
      return new CdpClient(socket);
    } catch (error) {
      try {
        socket.close();
      } catch (closeError) {
        console.error('Failed CDP WebSocket connection could not be closed.', closeError);
      }
      throw error;
    }
  }

  receive(raw) {
    let message;
    try {
      message = JSON.parse(typeof raw === 'string' ? raw : Buffer.from(raw).toString('utf8'));
    } catch (error) {
      this.failAll(new Error(`CDP returned invalid JSON: ${errorMessage(error)}`));
      return;
    }
    if (message.id !== undefined) {
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      clearTimeout(pending.timer);
      try {
        pending.deadline.remainingMilliseconds(`CDP ${pending.method}`);
      } catch (error) {
        pending.reject(error);
        return;
      }
      if (message.error) pending.reject(new Error(`CDP ${pending.method} failed: ${message.error.message}.`));
      else pending.resolve(message.result ?? {});
      return;
    }
    for (const listener of this.listeners.get(message.method) ?? []) listener(message.params ?? {});
  }

  failAll(error) {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(error);
    }
    this.pending.clear();
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? [];
    listeners.push(listener);
    this.listeners.set(method, listeners);
  }

  call(method, params = {}, deadline) {
    if (this.socket.readyState !== WebSocket.OPEN) return Promise.reject(new Error('CDP WebSocket is not open.'));
    if (!(deadline instanceof MonotonicDeadline)) {
      return Promise.reject(new Error(`CDP ${method} requires the monotonic run deadline.`));
    }
    const id = this.nextId++;
    const remaining = deadline.remainingMilliseconds(`CDP ${method}`);
    return new Promise((resolveCall, rejectCall) => {
      const pending = {
        method,
        deadline,
        resolve: resolveCall,
        reject: rejectCall,
        timer: setTimeout(() => {
          if (this.pending.get(id) !== pending) return;
          this.pending.delete(id);
          rejectCall(new Error(`Cold-browser run deadline expired during CDP ${method}.`));
        }, remaining),
      };
      this.pending.set(id, pending);
      try {
        this.socket.send(JSON.stringify({id, method, params}));
      } catch (error) {
        clearTimeout(pending.timer);
        this.pending.delete(id);
        rejectCall(error);
      }
    });
  }

  async close(deadline) {
    if (this.socket.readyState === WebSocket.CLOSED) return;
    const closed = new Promise(resolveClose => this.socket.addEventListener('close', resolveClose, {once: true}));
    this.socket.close();
    await deadline.wait(closed, 'CDP WebSocket cleanup');
  }
}

async function pageWebSocketUrl(port, deadline) {
  let lastError = null;
  const discoveryDeadlineError = error =>
    new Error(
      `Chrome debugging target discovery exhausted the cold-browser run deadline; ` +
        `last target error: ${errorMessage(lastError ?? error)}.`,
      {cause: error},
    );
  for (;;) {
    let remaining;
    try {
      remaining = deadline.remainingMilliseconds('Chrome debugging target discovery');
    } catch (error) {
      throw discoveryDeadlineError(error);
    }
    try {
      const response = await deadline.wait(
        fetch(`http://127.0.0.1:${port}/json/list`, {
          signal: AbortSignal.timeout(Math.min(2_000, remaining)),
        }),
        'Chrome debugging target request',
      );
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const targets = await deadline.wait(response.json(), 'Chrome debugging target response');
      const page = targets.find(target => target.type === 'page' && typeof target.webSocketDebuggerUrl === 'string');
      if (page) return requireOwnedPageWebSocketUrl(page.webSocketDebuggerUrl, port);
      lastError = new Error('Chrome debugging target response contained no page WebSocket target.');
    } catch (error) {
      // Chrome's debugging HTTP endpoint can lag behind chrome-launcher's socket probe.
      lastError = error;
      try {
        deadline.remainingMilliseconds('Chrome debugging target discovery');
      } catch (deadlineError) {
        throw discoveryDeadlineError(deadlineError);
      }
    }
    try {
      await deadline.pause(50, 'Chrome debugging target retry');
    } catch (error) {
      throw discoveryDeadlineError(error);
    }
  }
}

export function requireOwnedPageWebSocketUrl(candidate, expectedPort) {
  assertPositiveInteger(expectedPort, 'Chrome debugging port', 65_535);
  if (typeof candidate !== 'string' || candidate.length === 0 || candidate.length > 2_048) {
    throw new Error(`Chrome returned an invalid page WebSocket URL: ${JSON.stringify(candidate)}.`);
  }
  let url;
  try {
    url = new URL(candidate);
  } catch (error) {
    throw new Error(`Chrome returned an invalid page WebSocket URL: ${JSON.stringify(candidate)}.`, {cause: error});
  }
  let decodedPath;
  try {
    decodedPath = decodeURIComponent(url.pathname);
  } catch (error) {
    throw new Error(`Chrome page WebSocket URL has malformed path encoding: ${JSON.stringify(candidate)}.`, {
      cause: error,
    });
  }
  if (
    candidate !== url.href ||
    url.protocol !== 'ws:' ||
    url.hostname !== '127.0.0.1' ||
    url.port !== String(expectedPort) ||
    url.username !== '' ||
    url.password !== '' ||
    url.search !== '' ||
    url.hash !== '' ||
    decodedPath !== url.pathname ||
    !/^\/devtools\/page\/[A-Za-z0-9_-]+$/.test(url.pathname)
  ) {
    throw new Error(
      `Chrome page WebSocket URL is not the owned loopback endpoint on port ${expectedPort}: ${JSON.stringify(candidate)}.`,
    );
  }
  return url.href;
}

export function assertOwnedChromeCommandLine(result, {userDataDir, port}) {
  assertPositiveInteger(port, 'Chrome debugging port', 65_535);
  if (typeof userDataDir !== 'string' || !userDataDir.startsWith('/')) {
    throw new Error(`Owned Chrome user-data directory is invalid: ${JSON.stringify(userDataDir)}.`);
  }
  const args = result?.arguments;
  if (!Array.isArray(args) || !args.every(argument => typeof argument === 'string')) {
    throw new Error('CDP Browser.getBrowserCommandLine returned malformed arguments.');
  }
  for (const expected of [
    `--user-data-dir=${userDataDir}`,
    `--remote-debugging-port=${port}`,
    '--remote-debugging-address=127.0.0.1',
    '--enable-automation',
  ]) {
    const matches = args.filter(argument => argument === expected).length;
    if (matches !== 1) {
      throw new Error(
        `CDP endpoint is not bound to the owned Chrome command line: expected exactly one ${JSON.stringify(expected)}, received ${matches}.`,
      );
    }
  }
}

export function chromeEnvironment(runtimeRoot, sourceEnvironment = process.env) {
  return {
    ...isolatedBaseEnvironment(runtimeRoot, sourceEnvironment),
    CHROME_LOG_FILE: resolve(runtimeRoot, 'chrome.log'),
  };
}

export function chromeUserDataDirectory(runtimeRoot) {
  const root = resolve(runtimeRoot);
  const userDataDir = resolve(root, 'chrome-user-data');
  if (userDataDir === root || !isPathInside(root, userDataDir)) {
    throw new Error(`Chrome user-data directory escaped its isolated runtime root: ${userDataDir}.`);
  }
  return userDataDir;
}

export async function prepareChromeUserDataDirectory(runtimeRoot) {
  const root = await canonicalPlainDirectory(runtimeRoot, 'Chrome isolated runtime root');
  const userDataDir = chromeUserDataDirectory(root);
  await mkdir(userDataDir, {recursive: false, mode: 0o700});
  const canonicalUserDataDir = await canonicalPlainDirectory(userDataDir, 'Chrome isolated user-data directory');
  if (!isPathInside(root, canonicalUserDataDir) || canonicalUserDataDir === root) {
    throw new Error(`Canonical Chrome user-data directory escaped its runtime root: ${canonicalUserDataDir}.`);
  }
  return {runtimeRoot: root, userDataDir: canonicalUserDataDir};
}

async function removeOwnedChromeProfile(ownership) {
  const {runtimeRoot, userDataDir} = ownership;
  if (userDataDir === runtimeRoot || !isPathInside(runtimeRoot, userDataDir)) {
    throw new Error(`Refusing to remove Chrome profile outside its runtime root: ${userDataDir}.`);
  }
  const info = await lstat(userDataDir);
  if (info.isSymbolicLink() || !info.isDirectory()) {
    throw new Error(`Owned Chrome profile is no longer a plain directory: ${userDataDir}.`);
  }
  await rm(userDataDir, {recursive: true, force: false, maxRetries: 3});
}

export function createChromeSpawnGate(spawnImplementation = spawn) {
  if (typeof spawnImplementation !== 'function') {
    throw new Error('Chrome spawn gate requires a process-spawn function.');
  }
  let aborted = false;
  let spawnAttempted = false;
  let process = null;
  return {
    get aborted() {
      return aborted;
    },
    get process() {
      return process;
    },
    abort() {
      aborted = true;
    },
    spawn(...arguments_) {
      if (aborted) {
        throw new Error('Chrome launch was cancelled before process ownership could be established.');
      }
      if (spawnAttempted) throw new Error('Chrome spawn gate permits exactly one owned process.');
      spawnAttempted = true;
      process = spawnImplementation(...arguments_);
      return process;
    },
  };
}

function ownedChromeLauncher(instance, ownership, spawnGate, activeOwners) {
  let launchPromise = null;
  let launchSettled = false;
  let ownershipReleased = false;
  const chrome = {
    get port() {
      return instance.port;
    },
    get process() {
      return instance.chromeProcess ?? spawnGate.process;
    },
    get launchPromise() {
      return launchPromise;
    },
    get launchSettled() {
      return launchSettled;
    },
    ownership,
    trackLaunch(promise) {
      if (launchPromise) throw new Error('Chrome launch promise may be registered only once.');
      launchPromise = Promise.resolve(promise).then(
        value => {
          launchSettled = true;
          return value;
        },
        error => {
          launchSettled = true;
          throw error;
        },
      );
      // A bounded launch race can reject before chrome-launcher's internal polling settles.
      // Register a rejection observer immediately so that cancellation never creates an
      // unhandled late rejection while stopChrome waits on the same promise.
      launchPromise.catch(() => {});
      return launchPromise;
    },
    abortLaunch() {
      spawnGate.abort();
    },
    kill() {
      // chrome-launcher 0.15.2 deliberately never removes an explicitly supplied
      // userDataDir. Process termination stays with the launcher; profile deletion stays
      // with this harness so neither ownership boundary can fall back to the host profile.
      instance.kill();
    },
    releaseOwnership() {
      if (ownershipReleased) throw new Error('Chrome runtime ownership was already released.');
      ownershipReleased = true;
      activeOwners?.delete(chrome);
    },
  };
  activeOwners?.add(chrome);
  return chrome;
}

async function launchColdChrome(chromePath, runtimeRoot, deadline, activeOwners) {
  await assertPlainFile(chromePath, 'Explicit Chrome executable', {executable: true});
  await prepareIsolatedRuntimeDirectories(runtimeRoot);
  let ownership = null;
  let chrome = null;
  let cdp = null;
  try {
    ownership = await prepareChromeUserDataDirectory(runtimeRoot);
    const launcher = await deadline.wait(import('chrome-launcher'), 'Chrome launcher module load');
    if (typeof launcher.Launcher !== 'function') {
      throw new Error('Pinned chrome-launcher module does not export its Launcher ownership boundary.');
    }
    const port = await deadline.wait(
      reserveLoopbackPort(Math.min(CLEANUP_TIMEOUT_MS, deadline.remainingMilliseconds('Chrome port reservation'))),
      'Chrome port reservation',
    );
    const launchBudget = deadline.remainingMilliseconds('Chrome launch');
    const spawnGate = createChromeSpawnGate();
    const instance = new launcher.Launcher({
      chromePath,
      startingUrl: 'about:blank',
      port,
      handleSIGINT: false,
      logLevel: 'silent',
      connectionPollInterval: 100,
      maxConnectionRetries: Math.max(1, Math.ceil(launchBudget / 100)),
      envVars: chromeEnvironment(runtimeRoot),
      userDataDir: ownership.userDataDir,
      chromeFlags: [
        '--headless=new',
        '--remote-debugging-address=127.0.0.1',
        '--enable-automation',
        '--enable-precise-memory-info',
        '--disable-gpu',
        '--window-size=1440,1000',
      ],
    }, {spawn: spawnGate.spawn});
    chrome = ownedChromeLauncher(instance, ownership, spawnGate, activeOwners);
    const launchPromise = chrome.trackLaunch(instance.launch());
    await deadline.wait(launchPromise, 'Chrome launch');
    deadline.remainingMilliseconds('Chrome launch');
    if (!Number.isSafeInteger(chrome.port) || chrome.port <= 0 || !chrome.process) {
      throw new Error('Chrome launcher completed without an owned process and debugging port.');
    }
    const pageUrl = await pageWebSocketUrl(chrome.port, deadline);
    cdp = await CdpClient.connect(pageUrl, deadline);
    const browserCommandLine = await cdp.call('Browser.getBrowserCommandLine', {}, deadline);
    assertOwnedChromeCommandLine(browserCommandLine, {
      userDataDir: ownership.userDataDir,
      port: chrome.port,
    });
    return {chrome, cdp};
  } catch (primaryError) {
    const cleanupErrors = [];
    if (cdp) {
      try {
        await cdp.close(new MonotonicDeadline(CLEANUP_TIMEOUT_MS));
      } catch (error) {
        console.error('Failed Chrome CDP constructor cleanup failed.', error);
        cleanupErrors.push(error);
      }
    }
    if (chrome) {
      try {
        await stopChrome(chrome);
      } catch (error) {
        console.error('Failed Chrome constructor cleanup failed.', error);
        cleanupErrors.push(error);
      }
    } else if (ownership) {
      try {
        await new MonotonicDeadline(CLEANUP_TIMEOUT_MS).wait(
          removeOwnedChromeProfile(ownership),
          'failed Chrome profile cleanup',
        );
      } catch (error) {
        console.error('Failed unlaunched Chrome profile cleanup failed.', error);
        cleanupErrors.push(error);
      }
    }
    if (cleanupErrors.length > 0) {
      throw new AggregateError([primaryError, ...cleanupErrors], 'Chrome startup and cleanup failed.');
    }
    throw primaryError;
  }
}

export async function stopChrome(chrome, timeoutMs = CLEANUP_TIMEOUT_MS) {
  if (!chrome) return;
  assertPositiveInteger(timeoutMs, 'Chrome cleanup timeout', 60_000);
  const deadline = new MonotonicDeadline(timeoutMs);
  const errors = [];
  let confirmedLaunchSettlement = !chrome.launchPromise;
  const waitForProcess = async (process, label) => {
    if (!process || process.exitCode !== null || process.signalCode !== null) return;
    await deadline.wait(
      new Promise(resolveClose => process.once('close', resolveClose)),
      label,
    );
  };
  try {
    chrome.abortLaunch?.();
  } catch (error) {
    errors.push(error);
  }
  const initialProcess = chrome.process;
  try {
    chrome.kill();
  } catch (error) {
    errors.push(error);
  }
  try {
    await waitForProcess(initialProcess, 'Chrome process cleanup');
  } catch (error) {
    errors.push(error);
  }
  if (chrome.launchPromise) {
    try {
      await deadline.wait(
        chrome.launchPromise.then(
          () => undefined,
          () => undefined,
        ),
        'cancelled Chrome launcher settlement',
      );
      confirmedLaunchSettlement = true;
    } catch (error) {
      errors.push(error);
      confirmedLaunchSettlement = chrome.launchSettled === true;
    }
  }
  // If cancellation won at the spawn boundary, the process could have appeared after the first
  // kill request. Re-read the owned child and terminate it once more before profile deletion.
  const lateProcess = chrome.process;
  if (lateProcess && lateProcess.exitCode === null && lateProcess.signalCode === null) {
    try {
      chrome.kill();
    } catch (error) {
      errors.push(error);
    }
    try {
      await waitForProcess(lateProcess, 'late Chrome process cleanup');
    } catch (error) {
      errors.push(error);
    }
  }
  const remainingProcess = chrome.process;
  const processRemainsLive =
    remainingProcess && remainingProcess.exitCode === null && remainingProcess.signalCode === null;
  if (processRemainsLive) {
    errors.push(new Error('Chrome process remained live; refusing to delete its owned profile.'));
  }
  if (!confirmedLaunchSettlement) {
    errors.push(new Error('Chrome launch remained unsettled; refusing to delete its owned profile.'));
  }
  if (!processRemainsLive && confirmedLaunchSettlement) {
    try {
      await deadline.wait(removeOwnedChromeProfile(chrome.ownership), 'Chrome profile cleanup');
    } catch (error) {
      errors.push(error);
    }
  }
  if (errors.length > 0) {
    throw new AggregateError(errors, 'Chrome process/profile cleanup failed within its independent bound.');
  }
  chrome.releaseOwnership?.();
}

export function assertRuntimeRootRemovable(runtimeRoot, activeChromeOwners, wranglerChild = null) {
  if (!(activeChromeOwners instanceof Set)) {
    throw new Error('Runtime-root cleanup requires the active Chrome ownership set.');
  }
  const liveWrangler =
    wranglerChild && wranglerChild.exitCode === null && wranglerChild.signalCode === null;
  if (activeChromeOwners.size > 0 || liveWrangler) {
    throw new Error(
      `Refusing to remove benchmark runtime root ${runtimeRoot}: ` +
        `unreleasedChromeOwners=${activeChromeOwners.size}, liveWrangler=${Boolean(liveWrangler)}.`,
    );
  }
}

function cdpArgumentText(argument) {
  if (Object.prototype.hasOwnProperty.call(argument, 'value')) {
    try {
      return JSON.stringify(argument.value);
    } catch {
      return String(argument.value);
    }
  }
  return argument.description ?? argument.type ?? '<unserializable>';
}

function installFailureAndNetworkObservers(cdp) {
  const failures = [];
  const requests = new Map();
  const pendingHttp = new Set();
  const traffic = {
    datasetRequests: 0,
    datasetCompletedRequests: 0,
    datasetDecodedBytes: 0,
    datasetEncodedBytes: 0,
  };
  const isDatasetUrl = value => {
    try {
      const path = new URL(value).pathname;
      return path === '/api/datasets' || path.startsWith('/dataset/');
    } catch {
      return false;
    }
  };
  cdp.on('Runtime.exceptionThrown', event => {
    failures.push(`uncaught exception: ${event.exceptionDetails?.text ?? 'unknown exception'}`);
  });
  cdp.on('Runtime.consoleAPICalled', event => {
    if (event.type === 'error' || event.type === 'assert') {
      failures.push(`console.${event.type}: ${(event.args ?? []).map(cdpArgumentText).join(' ')}`);
    }
  });
  cdp.on('Log.entryAdded', event => {
    if (event.entry?.level === 'error') failures.push(`browser log error: ${event.entry.text}`);
  });
  cdp.on('Network.requestWillBeSent', event => {
    const url = event.request?.url;
    if (typeof url !== 'string') return;
    const isHttp = url.startsWith('http://') || url.startsWith('https://');
    const dataset = isDatasetUrl(url);
    requests.set(event.requestId, {url, dataset, decodedBytes: 0});
    if (isHttp) pendingHttp.add(event.requestId);
    if (dataset) traffic.datasetRequests += 1;
  });
  cdp.on('Network.dataReceived', event => {
    const request = requests.get(event.requestId);
    if (!request) return;
    request.decodedBytes += event.dataLength ?? 0;
  });
  cdp.on('Network.responseReceived', event => {
    const status = event.response?.status;
    if (Number.isFinite(status) && status >= 400) {
      failures.push(`HTTP ${status} for ${event.response.url}`);
    }
  });
  cdp.on('Network.loadingFinished', event => {
    const request = requests.get(event.requestId);
    pendingHttp.delete(event.requestId);
    if (request?.dataset) {
      traffic.datasetCompletedRequests += 1;
      traffic.datasetDecodedBytes += request.decodedBytes;
      traffic.datasetEncodedBytes += event.encodedDataLength ?? 0;
    }
  });
  cdp.on('Network.loadingFailed', event => {
    const request = requests.get(event.requestId);
    pendingHttp.delete(event.requestId);
    failures.push(
      `request failed for ${request?.url ?? event.requestId}: ${event.errorText ?? 'unknown error'}`,
    );
  });
  return {failures, pendingHttp, traffic};
}

async function readHeapUsage(cdp, deadline) {
  const usage = await cdp.call('Runtime.getHeapUsage', {}, deadline);
  if (!Number.isFinite(usage.usedSize) || usage.usedSize <= 0) {
    throw new Error(`Chrome returned an invalid JavaScript heap signal: ${usage.usedSize}.`);
  }
  return Math.ceil(usage.usedSize);
}

async function waitForDatasetMarker(cdp, publicationId, deadline, failures) {
  let last = null;
  for (;;) {
    deadline.remainingMilliseconds(`dataset readiness for ${publicationId}`);
    if (failures.length > 0) throw new Error(`Browser failed before readiness: ${failures.join(' | ')}`);
    const result = await cdp.call('Runtime.evaluate', {
      expression: `(() => {
        const root = document.documentElement;
        const marks = performance.getEntriesByName(${JSON.stringify(DATASET_READY_MARK)}, 'mark');
        return {
          state: root.getAttribute(${JSON.stringify(DATASET_STATE_ATTRIBUTE)}),
          publicationId: root.getAttribute(${JSON.stringify(DATASET_PUBLICATION_ATTRIBUTE)}),
          marks: marks.map(mark => ({name: mark.name, startTime: mark.startTime}))
        };
      })()`,
      returnByValue: true,
    }, deadline);
    last = result.result?.value;
    if (last?.state === 'error' || last?.state === 'identity-error') {
      throw new Error(`Browser dataset readiness entered terminal state ${last.state}.`);
    }
    if (last?.state === 'ready') {
      if (last.publicationId !== publicationId) {
        throw new Error(
          `Readiness marker selected ${JSON.stringify(last.publicationId)}; expected ${publicationId}.`,
        );
      }
      if (!Array.isArray(last.marks) || last.marks.length !== 1) {
        throw new Error(`Readiness contract emitted ${last?.marks?.length ?? 0} performance marks; expected one.`);
      }
      const readyMs = last.marks[0].startTime;
      if (!Number.isFinite(readyMs) || readyMs <= 0) {
        throw new Error(`Readiness performance mark has invalid startTime ${readyMs}.`);
      }
      return readyMs;
    }
    await deadline.pause(50, 'dataset readiness polling');
  }
}

async function waitForNetworkIdle(pendingHttp, failures, deadline) {
  let idleSince = null;
  for (;;) {
    deadline.remainingMilliseconds('network idle');
    if (failures.length > 0) throw new Error(`Browser failed before network idle: ${failures.join(' | ')}`);
    if (pendingHttp.size === 0) {
      idleSince ??= nodePerformance.now();
      if (nodePerformance.now() - idleSince >= NETWORK_IDLE_MS) return;
    } else {
      idleSince = null;
    }
    await deadline.pause(25, 'network-idle polling');
  }
}

async function waitForPaint(cdp, deadline) {
  const result = await cdp.call('Runtime.evaluate', {
    expression:
      'new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve(true))))',
    awaitPromise: true,
    returnByValue: true,
  }, deadline);
  if (result.result?.value !== true) throw new Error('Chrome did not confirm two post-readiness animation frames.');
}

function assertCdpTraffic(traffic) {
  if (
    traffic.datasetRequests <= 0 ||
    traffic.datasetCompletedRequests !== traffic.datasetRequests ||
    !Number.isSafeInteger(traffic.datasetDecodedBytes) ||
    traffic.datasetDecodedBytes <= 0 ||
    !Number.isFinite(traffic.datasetEncodedBytes) ||
    traffic.datasetEncodedBytes <= 0
  ) {
    throw new Error(`Chrome returned incomplete dataset traffic signals: ${JSON.stringify(traffic)}.`);
  }
  return {
    datasetRequests: traffic.datasetRequests,
    datasetCompletedRequests: traffic.datasetCompletedRequests,
    datasetDecodedBytes: traffic.datasetDecodedBytes,
    datasetEncodedBytes: Math.ceil(traffic.datasetEncodedBytes),
  };
}

async function measureColdBrowserRun({
  chromePath,
  origin,
  publicationId,
  timeoutMs,
  runtimeRoot,
  activeChromeOwners,
}) {
  const deadline = new MonotonicDeadline(timeoutMs);
  let launched = null;
  let cdp = null;
  let primaryError = null;
  try {
    launched = await launchColdChrome(chromePath, runtimeRoot, deadline, activeChromeOwners);
    cdp = launched.cdp;
    await Promise.all([
      cdp.call('Page.enable', {}, deadline),
      cdp.call('Runtime.enable', {}, deadline),
      cdp.call('Network.enable', {}, deadline),
      cdp.call('Log.enable', {}, deadline),
      cdp.call('HeapProfiler.enable', {}, deadline),
    ]);
    await cdp.call('Network.setCacheDisabled', {cacheDisabled: true}, deadline);
    await cdp.call('Network.clearBrowserCache', {}, deadline);
    const browser = await cdp.call('Browser.getVersion', {}, deadline);
    const observed = installFailureAndNetworkObservers(cdp);

    const heapSamples = [];
    let sample = true;
    let samplingError = null;
    const sampler = (async () => {
      while (sample) {
        try {
          heapSamples.push(await readHeapUsage(cdp, deadline));
        } catch (error) {
          samplingError = error;
          return;
        }
        if (sample) await deadline.pause(HEAP_SAMPLE_INTERVAL_MS, 'JavaScript heap sampling interval');
      }
    })();

    let readyMs;
    try {
      await cdp.call('Page.navigate', {url: `${origin}/`}, deadline);
      readyMs = await waitForDatasetMarker(cdp, publicationId, deadline, observed.failures);
      await waitForNetworkIdle(observed.pendingHttp, observed.failures, deadline);
      await waitForPaint(cdp, deadline);
    } finally {
      sample = false;
      await sampler;
    }
    if (samplingError) throw new Error(`Heap sampling failed: ${errorMessage(samplingError)}`, {cause: samplingError});
    if (heapSamples.length === 0) throw new Error('Chrome returned no JavaScript heap samples.');
    const peakHeapBytes = Math.max(...heapSamples);

    const settledSamples = [];
    for (let round = 0; round < 3; round += 1) {
      await cdp.call('HeapProfiler.collectGarbage', {}, deadline);
      await deadline.pause(100, 'post-GC heap settling');
      settledSamples.push(await readHeapUsage(cdp, deadline));
    }
    const settledHeapBytes = Math.max(...settledSamples);
    const settledSpread = settledHeapBytes - Math.min(...settledSamples);
    const stabilityLimit = Math.max(8 * MIB, Math.ceil(settledHeapBytes * 0.05));
    if (settledSpread > stabilityLimit) {
      throw new Error(
        `Post-GC heap did not settle: samples=${settledSamples.join(',')}, allowed spread=${stabilityLimit}.`,
      );
    }
    if (observed.failures.length > 0) {
      throw new Error(`Browser emitted fatal diagnostics: ${observed.failures.join(' | ')}`);
    }
    return {
      browser,
      readyMs: Math.round(readyMs * 1_000) / 1_000,
      peakHeapBytes,
      settledHeapBytes,
      heapSampleCount: heapSamples.length,
      settledHeapSamples: settledSamples,
      cdpTraffic: assertCdpTraffic(observed.traffic),
    };
  } catch (error) {
    primaryError = error;
    throw error;
  } finally {
    const cleanupErrors = [];
    if (cdp) {
      try {
        await cdp.close(new MonotonicDeadline(CLEANUP_TIMEOUT_MS));
      } catch (error) {
        console.error('CDP cleanup failed.', error);
        cleanupErrors.push(error);
      }
    }
    if (launched?.chrome) {
      try {
        await stopChrome(launched.chrome);
      } catch (error) {
        console.error('Chrome cleanup failed.', error);
        cleanupErrors.push(error);
      }
    }
    if (cleanupErrors.length > 0) {
      throw new AggregateError(
        primaryError ? [primaryError, ...cleanupErrors] : cleanupErrors,
        'Cold-browser run cleanup failed.',
      );
    }
  }
}

export function assertProfileSlugBinding(profile, slug) {
  if (profile === GTNH_PROFILE && slug !== GTNH_SLUG) {
    throw new Error(`Manifest profile ${GTNH_PROFILE} requires benchmark slug ${GTNH_SLUG}; received ${slug}.`);
  }
  if (slug === GTNH_SLUG && profile !== GTNH_PROFILE) {
    throw new Error(
      `Benchmark slug ${GTNH_SLUG} requires manifest profile ${GTNH_PROFILE}; received ${String(profile)}.`,
    );
  }
}

async function validatePublicationPair(options, logger = console) {
  const core = await validateLocalCoreDatasetPublication({
    exportRoot: options.exportRoot,
    publication: options.publication,
    concurrency: options.concurrency,
    logger,
  });
  const preview = await validateLocalRecipePreviewSidecar(
    options.previewSidecar,
    options.concurrency,
  );
  if (preview.manifest.datasetPublicationId !== core.publicationId) {
    throw new Error(
      `Preview sidecar targets ${preview.manifest.datasetPublicationId}; core publication is ${core.publicationId}.`,
    );
  }
  if (!SHA256_PATTERN.test(preview.manifest.assetSetId)) {
    throw new Error('Validated preview sidecar returned a non-canonical asset-set identity.');
  }
  const applicationManifestRecord = core.records.find(record => record.path === 'manifest.json');
  if (!applicationManifestRecord) throw new Error('Validated core publication has no application manifest.json.');
  const applicationManifestBytes = await readFile(applicationManifestRecord.localPath);
  if (applicationManifestBytes.length !== applicationManifestRecord.bytes) {
    throw new Error('Application manifest changed after core publication validation.');
  }
  const applicationManifest = parseJson(applicationManifestBytes, 'Application manifest');
  if (
    applicationManifest.publicationId !== core.publicationId ||
    typeof applicationManifest.minecraft !== 'string' ||
    !applicationManifest.pack ||
    typeof applicationManifest.pack !== 'object' ||
    Array.isArray(applicationManifest.pack)
  ) {
    throw new Error('Application manifest lacks the exact content identity, Minecraft version, or pack identity.');
  }
  assertProfileSlugBinding(applicationManifest.profile, options.slug);
  const descriptor = {
    slug: options.slug,
    displayName: assertBoundedText(applicationManifest.pack.name, 'manifest.pack.name', 120),
    minecraftVersion: assertBoundedText(applicationManifest.minecraft, 'manifest.minecraft', 40),
    packVersion: assertBoundedText(applicationManifest.pack.version, 'manifest.pack.version', 80),
    publicationId: core.publicationId,
    previewAssetSetId: preview.manifest.assetSetId,
    isDefault: true,
  };
  return {core, preview, descriptor};
}

function sameBrowserVersion(left, right) {
  return ['protocolVersion', 'product', 'revision', 'userAgent', 'jsVersion'].every(
    key => left?.[key] === right?.[key],
  );
}

async function runBenchmark(options) {
  const output = await resolveIsolatedOutputTarget(options);
  await assertPlainFile(options.chrome, 'Explicit Chrome executable', {executable: true});
  await verifyReadinessContractInBuild(options.dist);
  const [build, benchmarkSha256] = await Promise.all([
    digestBuildTree(options.dist),
    digestColdDatasetBenchmarkSource(),
  ]);
  console.info('Exhaustively validating the local core publication and preview sidecar.');
  const validated = await validatePublicationPair(options);
  const staticMetrics = await computeBootstrapMetrics(validated.core, validated.preview);

  const runtimeRoot = await mkdtemp(resolve(tmpdir(), 'mrt-cold-browser-'));
  const activeChromeOwners = new Set();
  let wrangler = null;
  let proxy = null;
  let primaryError = null;
  let report = null;
  try {
    wrangler = await startWrangler({
      dist: options.dist,
      runtimeRoot,
      timeoutMs: options.timeoutMs,
    });
    proxy = createPublicationProxy({
      ...validated,
      upstreamPort: wrangler.port,
    });
    const proxyPort = await listenLoopback(proxy.server);
    proxy.setExpectedHost(`127.0.0.1:${proxyPort}`);
    const origin = `http://127.0.0.1:${proxyPort}`;
    const runs = [];
    let browser = null;
    for (let index = 0; index < options.runs; index += 1) {
      console.info(`Running cold Chrome measurement ${index + 1}/${options.runs}.`);
      const proxyStats = proxy.beginRun();
      const runRuntime = await mkdtemp(resolve(runtimeRoot, `chrome-${index + 1}-`));
      const measured = await measureColdBrowserRun({
        chromePath: options.chrome,
        origin,
        publicationId: validated.core.publicationId,
        timeoutMs: options.timeoutMs,
        runtimeRoot: runRuntime,
        activeChromeOwners,
      });
      if (browser === null) browser = measured.browser;
      else if (!sameBrowserVersion(browser, measured.browser)) {
        throw new Error('Chrome version identity changed between cold-browser runs.');
      }
      const {browser: ignoredBrowser, ...metrics} = measured;
      void ignoredBrowser;
      runs.push({
        run: index + 1,
        ...metrics,
        proxyTraffic: validateRunTraffic(proxyStats, staticMetrics),
      });
    }

    console.info('Revalidating the production build and both local publications after measurement.');
    const post = await validatePublicationPair(options, {
      info(message) {
        console.info(message);
      },
      warn(message) {
        console.warn(message);
      },
      error(message) {
        console.error(message);
      },
    });
    if (
      post.core.publicationId !== validated.core.publicationId ||
      post.preview.manifest.assetSetId !== validated.preview.manifest.assetSetId
    ) {
      throw new Error('Publication identity changed between preflight and postflight validation.');
    }
    const postBuild = await digestBuildTree(options.dist);
    assertSameBuildTree(build, postBuild);
    const gate = classifyActivationGate(staticMetrics, runs);
    report = {
      schemaVersion: COLD_DATASET_REPORT_SCHEMA_VERSION,
      generatedAt: new Date().toISOString(),
      benchmark: {
        sourceSha256: benchmarkSha256,
        runs: options.runs,
        coldDefinition: COLD_BROWSER_DEFINITION,
      },
      platform: {
        node: process.version,
        platform: platform(),
        arch: arch(),
        release: release(),
        logicalCpuCount: cpus().length,
      },
      chrome: browser,
      build,
      dataset: validated.descriptor,
      staticMetrics,
      thresholds: ACTIVATION_THRESHOLDS,
      runs,
      ...gate,
    };
  } catch (error) {
    primaryError = error;
  } finally {
    const cleanupErrors = [];
    if (proxy) {
      try {
        await closeHttpServer(proxy.server);
      } catch (error) {
        console.error('Benchmark proxy cleanup failed.', error);
        cleanupErrors.push(error);
      }
    }
    if (wrangler) {
      try {
        wrangler.logs.stdout.assertComplete();
        wrangler.logs.stderr.assertComplete();
        const exitedUnexpectedly =
          report !== null &&
          (wrangler.child.exitCode !== null || wrangler.child.signalCode !== null);
        if (exitedUnexpectedly) {
          throw new Error(
            `Local Wrangler exited unexpectedly (code=${wrangler.child.exitCode}, signal=${wrangler.child.signalCode}).`,
          );
        }
        await stopChildProcess(wrangler.child, 'Local Wrangler');
      } catch (error) {
        console.error('Local Wrangler cleanup failed.', {
          error,
          stdout: wrangler.logs.stdout.text(),
          stderr: wrangler.logs.stderr.text(),
        });
        cleanupErrors.push(error);
      }
    }
    let runtimeRemovalBlocked = false;
    try {
      assertRuntimeRootRemovable(runtimeRoot, activeChromeOwners, wrangler?.child ?? null);
    } catch (error) {
      runtimeRemovalBlocked = true;
      console.error('Benchmark runtime root retained because process ownership is unresolved.', {
        runtimeRoot,
        error,
      });
      cleanupErrors.push(error);
    }
    if (!runtimeRemovalBlocked) {
      try {
        await rm(runtimeRoot, {recursive: true, force: false, maxRetries: 3});
      } catch (error) {
        console.error('Benchmark temporary-directory cleanup failed.', {runtimeRoot, error});
        cleanupErrors.push(error);
      }
    }
    if (cleanupErrors.length > 0) {
      primaryError = new AggregateError(
        primaryError ? [primaryError, ...cleanupErrors] : cleanupErrors,
        'Cold-browser benchmark cleanup failed.',
      );
    }
  }
  if (primaryError) throw primaryError;
  if (!report) throw new Error('Cold-browser benchmark produced no report.');
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, {
    flag: 'wx',
    mode: 0o600,
  });
  console.info(
    `Cold-browser activation decision: ${report.decision}; report written exclusively to ${output}.`,
  );
  return report;
}

async function main() {
  const options = parseBenchmarkArguments(process.argv.slice(2));
  const report = await runBenchmark(options);
  if (report.decision !== 'current-storage-eligible') {
    console.error(
      `Activation gate rejected automatic activation with decision ${report.decision}: ` +
        JSON.stringify(report.violations),
    );
    process.exitCode = 2;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === SCRIPT_PATH) {
  main().catch(error => {
    console.error('Cold-browser activation benchmark failed closed.', error);
    process.exitCode = 1;
  });
}
