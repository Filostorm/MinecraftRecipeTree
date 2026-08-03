import {createHash} from 'node:crypto';
import {constants} from 'node:fs';
import {lstat, open, readdir} from 'node:fs/promises';
import {availableParallelism} from 'node:os';
import {join, posix, relative, resolve, sep} from 'node:path';

export const EXPORT_TREE_DIGEST_FORMAT = 'mrt-export-tree-v1';
export const EXPORT_TREE_DIGEST_ALGORITHM = 'sha256';

const DIGEST_DOMAIN = `${EXPORT_TREE_DIGEST_FORMAT}\0`;
const MAX_TREE_FILES = 2_000_000;
const MAX_TREE_BYTES = 256 * 1024 * 1024 * 1024;
const MAX_SINGLE_FILE_BYTES = 8 * 1024 * 1024 * 1024;
const MAX_ROOT_MANIFEST_BYTES = 16 * 1024 * 1024;
const HASH_CONCURRENCY = Math.max(1, Math.min(8, availableParallelism()));
const READ_CHUNK_BYTES = 256 * 1024;
const preparedSnapshotState = new WeakMap();
const finalizedSnapshots = new WeakSet();

function canonicalRelativePath(root, path) {
  const value = relative(root, path).split(sep).join('/');
  if (
    value.length === 0 ||
    value.startsWith('/') ||
    value.includes('\\') ||
    posix.normalize(value) !== value ||
    value === '..' ||
    value.startsWith('../')
  ) {
    throw new Error(`Export tree produced an unsafe relative path: ${JSON.stringify(value)}.`);
  }
  return value;
}

function sameEntry(left, right) {
  return (
    left.dev === right.dev &&
    left.ino === right.ino &&
    left.mode === right.mode &&
    left.size === right.size &&
    left.mtimeNs === right.mtimeNs &&
    left.ctimeNs === right.ctimeNs
  );
}

async function collectPlainTree(root) {
  const rootBefore = await lstat(root, {bigint: true});
  if (rootBefore.isSymbolicLink() || !rootBefore.isDirectory()) {
    throw new Error(`Acceptance export root must be a real directory: ${root}.`);
  }
  const files = [];
  const directories = [];
  const pending = [root];
  while (pending.length > 0) {
    const directory = pending.pop();
    const before = await lstat(directory, {bigint: true});
    if (before.isSymbolicLink() || !before.isDirectory()) {
      throw new Error(`Export tree directory changed or is unsupported: ${directory}.`);
    }
    directories.push(
      Object.freeze({
        path: directory,
        relativePath: directory === root ? '' : canonicalRelativePath(root, directory),
        info: before,
      }),
    );
    const entries = await readdir(directory, {withFileTypes: true});
    entries.sort((left, right) => left.name.localeCompare(right.name));
    for (const entry of entries) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        pending.push(path);
      } else if (entry.isFile()) {
        const info = await lstat(path, {bigint: true});
        if (info.isSymbolicLink() || !info.isFile() || info.nlink !== 1n) {
          throw new Error(
            `Export tree file must be a plain, non-hard-linked regular file: ` +
              `${canonicalRelativePath(root, path)}.`,
          );
        }
        files.push(
          Object.freeze({
            path,
            relativePath: canonicalRelativePath(root, path),
            info,
          }),
        );
        if (files.length > MAX_TREE_FILES) {
          throw new Error(`Export tree exceeds the ${MAX_TREE_FILES}-file acceptance bound.`);
        }
      } else {
        throw new Error(
          `Export tree contains a symlink or special filesystem entry: ${path}.`,
        );
      }
    }
    const after = await lstat(directory, {bigint: true});
    if (!sameEntry(before, after)) {
      throw new Error(`Export tree directory changed during inventory: ${directory}.`);
    }
  }
  const rootAfter = await lstat(root, {bigint: true});
  if (!sameEntry(rootBefore, rootAfter)) {
    throw new Error(`Acceptance export root changed during inventory: ${root}.`);
  }
  files.sort((left, right) =>
    Buffer.compare(
      Buffer.from(left.relativePath, 'utf8'),
      Buffer.from(right.relativePath, 'utf8'),
    ),
  );
  directories.sort((left, right) =>
    Buffer.compare(
      Buffer.from(left.relativePath, 'utf8'),
      Buffer.from(right.relativePath, 'utf8'),
    ),
  );
  return Object.freeze({
    root,
    rootInfo: rootBefore,
    files: Object.freeze(files),
    directories: Object.freeze(directories),
  });
}

async function hashPlainFile(file, {captureRootManifest = false} = {}) {
  const before = await lstat(file.path, {bigint: true});
  if (
    before.isSymbolicLink() ||
    !before.isFile() ||
    before.nlink !== 1n ||
    !sameEntry(file.info, before)
  ) {
    throw new Error(
      `Export tree file changed or is not a plain, non-hard-linked regular file: ` +
        `${file.relativePath}.`,
    );
  }
  if (before.size < 0n || before.size > BigInt(MAX_SINGLE_FILE_BYTES)) {
    throw new Error(`Export tree file exceeds its byte bound: ${file.relativePath}.`);
  }
  if (!Number.isSafeInteger(constants.O_NOFOLLOW)) {
    throw new Error('Export tree hashing requires O_NOFOLLOW support.');
  }
  const handle = await open(file.path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const opened = await handle.stat({bigint: true});
    if (!opened.isFile() || opened.nlink !== 1n || !sameEntry(before, opened)) {
      throw new Error(`Export tree file changed before secure open: ${file.relativePath}.`);
    }
    const hash = createHash('sha256');
    const captureManifest = captureRootManifest && file.relativePath === 'manifest.json';
    if (captureManifest && opened.size > BigInt(MAX_ROOT_MANIFEST_BYTES)) {
      throw new Error(
        `Root manifest exceeds the ${MAX_ROOT_MANIFEST_BYTES}-byte deterministic-comparison bound.`,
      );
    }
    const manifestChunks = captureManifest ? [] : null;
    const buffer = Buffer.allocUnsafe(READ_CHUNK_BYTES);
    let offset = 0;
    while (offset < Number(opened.size)) {
      const length = Math.min(buffer.length, Number(opened.size) - offset);
      const {bytesRead} = await handle.read(buffer, 0, length, offset);
      if (bytesRead === 0) {
        throw new Error(`Export tree file ended early while hashing: ${file.relativePath}.`);
      }
      const chunk = buffer.subarray(0, bytesRead);
      hash.update(chunk);
      if (manifestChunks !== null) manifestChunks.push(Buffer.from(chunk));
      offset += bytesRead;
    }
    const after = await handle.stat({bigint: true});
    if (!sameEntry(opened, after)) {
      throw new Error(`Export tree file changed while hashing: ${file.relativePath}.`);
    }
    return Object.freeze({
      relativePath: file.relativePath,
      bytes: Number(opened.size),
      sha256: hash.digest(),
      ...(manifestChunks === null ? {} : {content: Buffer.concat(manifestChunks)}),
    });
  } finally {
    await handle.close();
  }
}

function assertSamePlainTreeInventory(before, after) {
  for (const [kind, leftEntries, rightEntries] of [
    ['directory', before.directories, after.directories],
    ['file', before.files, after.files],
  ]) {
    if (leftEntries.length !== rightEntries.length) {
      throw new Error(
        `Export tree ${kind} inventory changed during validation or hashing: ` +
          `${leftEntries.length} became ${rightEntries.length}.`,
      );
    }
    for (let index = 0; index < leftEntries.length; index += 1) {
      const left = leftEntries[index];
      const right = rightEntries[index];
      if (left.relativePath !== right.relativePath || !sameEntry(left.info, right.info)) {
        throw new Error(
          `Export tree ${kind} inventory changed during validation or hashing at ` +
            `${JSON.stringify(left.relativePath || right.relativePath || '.')}.`,
        );
      }
    }
  }
}

function uint64be(value) {
  const result = Buffer.allocUnsafe(8);
  result.writeBigUInt64BE(BigInt(value));
  return result;
}

/**
 * Capture the filesystem identity that a subsequent validation must cover.
 * finalizeExportTreeSnapshot verifies this exact inventory before and after its
 * secure reads, binding validation and byte comparison without a redundant
 * second content-hash pass on very large exports.
 */
export async function prepareExportTreeSnapshot(
  exportRoot,
  {logger = console, captureRootManifest = false} = {},
) {
  const root = resolve(exportRoot);
  logger.info(`[export-tree] Inventorying plain files under ${root}.`);
  const inventory = await collectPlainTree(root);
  const prepared = Object.freeze({root});
  preparedSnapshotState.set(prepared, {root, inventory, captureRootManifest});
  return prepared;
}

export async function finalizeExportTreeSnapshot(prepared, {logger = console} = {}) {
  if (finalizedSnapshots.has(prepared)) {
    throw new Error('An export-tree snapshot cannot be finalized more than once.');
  }
  const state = preparedSnapshotState.get(prepared);
  if (state === undefined) {
    throw new Error('A snapshot must be created by prepareExportTreeSnapshot before finalization.');
  }
  finalizedSnapshots.add(prepared);
  preparedSnapshotState.delete(prepared);
  const {root, inventory, captureRootManifest} = state;
  const {files} = inventory;
  logger.info(
    `[export-tree] Securely hashing ${files.length} inventoried files under ${root}.`,
  );
  const hashed = new Array(files.length);
  let nextIndex = 0;
  let completed = 0;
  let firstError = null;
  async function worker() {
    while (firstError === null) {
      const index = nextIndex;
      nextIndex += 1;
      if (index >= files.length) return;
      try {
        hashed[index] = await hashPlainFile(files[index], {captureRootManifest});
        completed += 1;
        if (completed % 50_000 === 0) {
          logger.info(`[export-tree] Hashed ${completed}/${files.length} files.`);
        }
      } catch (error) {
        firstError ??= error;
      }
    }
  }
  await Promise.all(
    Array.from({length: Math.min(HASH_CONCURRENCY, Math.max(1, files.length))}, () => worker()),
  );
  if (firstError !== null) throw firstError;

  const after = await collectPlainTree(root);
  assertSamePlainTreeInventory(inventory, after);

  const hash = createHash('sha256');
  hash.update(DIGEST_DOMAIN, 'utf8');
  let totalBytes = 0;
  for (const file of hashed) {
    totalBytes += file.bytes;
    if (totalBytes > MAX_TREE_BYTES) {
      throw new Error(`Export tree exceeds the ${MAX_TREE_BYTES}-byte acceptance bound.`);
    }
    const pathBytes = Buffer.from(file.relativePath, 'utf8');
    const pathLength = Buffer.allocUnsafe(4);
    pathLength.writeUInt32BE(pathBytes.length);
    hash.update(pathLength);
    hash.update(pathBytes);
    hash.update(uint64be(file.bytes));
    hash.update(file.sha256);
  }
  const result = Object.freeze({
    format: EXPORT_TREE_DIGEST_FORMAT,
    algorithm: EXPORT_TREE_DIGEST_ALGORITHM,
    sha256: hash.digest('hex'),
    files: hashed.length,
    bytes: totalBytes,
  });
  logger.info(
    `[export-tree] Hashed ${result.files} files and ${result.bytes} bytes: sha256=${result.sha256}.`,
  );
  const manifestEntry = hashed.find(file => file.relativePath === 'manifest.json');
  return Object.freeze({
    root,
    treeDigest: result,
    files: Object.freeze(
      hashed.map(file =>
        Object.freeze({
          relativePath: file.relativePath,
          bytes: file.bytes,
          sha256: file.sha256.toString('hex'),
        }),
      ),
    ),
    manifestBytes: manifestEntry?.content,
  });
}

export async function digestExportTree(exportRoot, {logger = console} = {}) {
  const prepared = await prepareExportTreeSnapshot(exportRoot, {logger});
  const snapshot = await finalizeExportTreeSnapshot(prepared, {logger});
  return snapshot.treeDigest;
}

export function sameExportTreeDigest(left, right) {
  return (
    left?.format === right?.format &&
    left?.algorithm === right?.algorithm &&
    left?.sha256 === right?.sha256 &&
    left?.files === right?.files &&
    left?.bytes === right?.bytes
  );
}
