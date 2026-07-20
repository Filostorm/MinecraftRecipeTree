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
const HASH_CONCURRENCY = Math.max(1, Math.min(8, availableParallelism()));
const READ_CHUNK_BYTES = 256 * 1024;

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

async function collectPlainFiles(root) {
  const rootBefore = await lstat(root, {bigint: true});
  if (rootBefore.isSymbolicLink() || !rootBefore.isDirectory()) {
    throw new Error(`Acceptance export root must be a real directory: ${root}.`);
  }
  const files = [];
  const pending = [root];
  while (pending.length > 0) {
    const directory = pending.pop();
    const before = await lstat(directory, {bigint: true});
    if (before.isSymbolicLink() || !before.isDirectory()) {
      throw new Error(`Export tree directory changed or is unsupported: ${directory}.`);
    }
    const entries = await readdir(directory, {withFileTypes: true});
    entries.sort((left, right) => left.name.localeCompare(right.name));
    for (const entry of entries) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        pending.push(path);
      } else if (entry.isFile()) {
        files.push(Object.freeze({path, relativePath: canonicalRelativePath(root, path)}));
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
  return files;
}

async function hashPlainFile(file) {
  const before = await lstat(file.path, {bigint: true});
  if (before.isSymbolicLink() || !before.isFile() || before.nlink !== 1n) {
    throw new Error(
      `Export tree file must be a plain, non-hard-linked regular file: ${file.relativePath}.`,
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
    const buffer = Buffer.allocUnsafe(READ_CHUNK_BYTES);
    let offset = 0;
    while (offset < Number(opened.size)) {
      const length = Math.min(buffer.length, Number(opened.size) - offset);
      const {bytesRead} = await handle.read(buffer, 0, length, offset);
      if (bytesRead === 0) {
        throw new Error(`Export tree file ended early while hashing: ${file.relativePath}.`);
      }
      hash.update(buffer.subarray(0, bytesRead));
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
    });
  } finally {
    await handle.close();
  }
}

function uint64be(value) {
  const result = Buffer.allocUnsafe(8);
  result.writeBigUInt64BE(BigInt(value));
  return result;
}

export async function digestExportTree(exportRoot, {logger = console} = {}) {
  const root = resolve(exportRoot);
  logger.info(`[export-tree] Inventorying plain files under ${root}.`);
  const files = await collectPlainFiles(root);
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
        hashed[index] = await hashPlainFile(files[index]);
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
  return result;
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
