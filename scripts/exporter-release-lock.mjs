import {constants} from 'node:fs';
import {lstat, mkdir, open, unlink} from 'node:fs/promises';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const SCRIPT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const EXPORTER_RELEASE_LOCK_FILENAME = '.exporter-release-manifest.lock';

export const DEFAULT_EXPORTER_PUBLIC_ROOT = resolve(
  SCRIPT_DIRECTORY,
  '..',
  'public',
  'exporters',
);

export async function requireRealExporterPublicRoot(publicRoot, {create = false} = {}) {
  if (create) await mkdir(publicRoot, {recursive: true});
  const entry = await lstat(publicRoot);
  if (entry.isSymbolicLink() || !entry.isDirectory()) {
    throw new Error(`Exporter release publicRoot must be a real directory: ${publicRoot}.`);
  }
  return entry;
}

async function requireOwnedLock(lockPath, lockIdentity) {
  const current = await lstat(lockPath);
  if (
    current.isSymbolicLink() ||
    !current.isFile() ||
    current.nlink !== 1 ||
    current.dev !== lockIdentity.dev ||
    current.ino !== lockIdentity.ino
  ) {
    throw new Error(
      `Exporter release transaction lock changed while packaging was active: ${lockPath}.`,
    );
  }
}

/**
 * Serialize release-manifest mutations and acceptance-receipt replacement. Existing locks are
 * never guessed to be stale or removed automatically: operators must first prove no transaction
 * is active, then inspect and remove a leftover lock manually.
 */
export async function withExporterReleaseManifestLock({
  publicRoot,
  operation,
  logger = console,
  action,
}) {
  if (typeof action !== 'function') throw new Error('Exporter release lock requires an action.');
  if (typeof operation !== 'string' || operation.length === 0 || operation.length > 160) {
    throw new Error('Exporter release lock operation must be a non-empty bounded string.');
  }
  await requireRealExporterPublicRoot(publicRoot);
  if (!Number.isSafeInteger(constants.O_NOFOLLOW)) {
    throw new Error('Exporter release locking requires O_NOFOLLOW support.');
  }
  const lockPath = join(publicRoot, EXPORTER_RELEASE_LOCK_FILENAME);
  let handle;
  try {
    handle = await open(
      lockPath,
      constants.O_WRONLY |
        constants.O_CREAT |
        constants.O_EXCL |
        constants.O_NOFOLLOW,
      0o600,
    );
  } catch (error) {
    if (error?.code === 'EEXIST') {
      throw new Error(
        `Exporter release transaction lock already exists: ${lockPath}. Transaction stopped. ` +
          'The lock is never auto-removed, even when it appears stale; verify that no release or acceptance process is active, then inspect and remove it manually.',
        {cause: error},
      );
    }
    throw error;
  }

  const lockIdentity = await handle.stat();
  let result;
  let operationError = null;
  try {
    await handle.writeFile(
      `${JSON.stringify({
        format: 'mrt-exporter-release-lock-v1',
        createdAt: new Date().toISOString(),
        pid: process.pid,
        operation,
      })}\n`,
      'utf8',
    );
    await handle.sync();
    logger.info(`[exporter-release] Acquired exclusive manifest lock for ${operation}.`);
    const assertOwned = () => requireOwnedLock(lockPath, lockIdentity);
    result = await action(assertOwned);
  } catch (error) {
    operationError = error;
  }

  const cleanupErrors = [];
  try {
    await handle.close();
  } catch (error) {
    cleanupErrors.push(error);
  }
  try {
    await requireOwnedLock(lockPath, lockIdentity);
    await unlink(lockPath);
    logger.info(`[exporter-release] Released exclusive manifest lock for ${operation}.`);
  } catch (error) {
    cleanupErrors.push(error);
  }

  if (operationError && cleanupErrors.length > 0) {
    throw new AggregateError(
      [operationError, ...cleanupErrors],
      `Exporter release operation ${operation} failed and its transaction lock could not be cleanly released.`,
    );
  }
  if (operationError) throw operationError;
  if (cleanupErrors.length > 0) {
    throw new AggregateError(
      cleanupErrors,
      `Exporter release operation ${operation} completed but its transaction lock could not be cleanly released. Inspect the public release directory before continuing.`,
    );
  }
  return result;
}
