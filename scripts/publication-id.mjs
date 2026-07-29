import {createHash, randomUUID} from 'node:crypto';
import {readFile, rename, rm, writeFile} from 'node:fs/promises';
import {dirname, join, relative, resolve, sep} from 'node:path';
import {collectFiles, isRecord, pathKind, readJsonDocument} from './export-data-utils.mjs';

export const PUBLICATION_ID_PATTERN = /^[a-f0-9]{64}$/;

function relativeKey(root, path) {
  return relative(root, path).split(sep).join('/');
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function updateFramed(hash, value) {
  const bytes = Buffer.isBuffer(value) ? value : Buffer.from(value);
  hash.update(String(bytes.length));
  hash.update(':');
  hash.update(bytes);
}

async function publicationBytes(path, key) {
  if (key !== 'manifest.json') return readFile(path);
  const manifest = await readJsonDocument(path, 'manifest.json');
  if (!isRecord(manifest)) {
    throw new Error('Cannot compute a publication ID from a non-object manifest.json.');
  }
  const normalized = {...manifest};
  delete normalized.publicationId;
  return Buffer.from(canonicalJson(normalized));
}

/**
 * Hash every published file in canonical path order. Length-prefixed framing
 * makes the byte stream unambiguous, while normalizing manifest.publicationId
 * removes the otherwise circular dependency.
 */
export async function computePublicationId(exportRoot) {
  const root = resolve(exportRoot);
  if ((await pathKind(root)) !== 'directory') {
    throw new Error(`Cannot compute a publication ID for a non-directory export root: ${root}`);
  }

  const files = (await collectFiles(root))
    .map(path => ({path, key: relativeKey(root, path)}))
    .sort((left, right) => (left.key < right.key ? -1 : left.key > right.key ? 1 : 0));
  if (!files.some(file => file.key === 'manifest.json')) {
    throw new Error('Cannot compute a publication ID without manifest.json.');
  }

  const hash = createHash('sha256');
  hash.update('minecraft-recipe-tree-publication-v1\0');
  for (const file of files) {
    updateFramed(hash, file.key);
    updateFramed(hash, await publicationBytes(file.path, file.key));
  }
  return hash.digest('hex');
}

export async function writePublicationId(exportRoot) {
  const root = resolve(exportRoot);
  const manifestPath = join(root, 'manifest.json');
  const manifest = await readJsonDocument(manifestPath, 'manifest.json');
  if (!isRecord(manifest)) {
    throw new Error('Cannot publish a dataset with a non-object manifest.json.');
  }

  const publicationId = await computePublicationId(root);
  const temporaryPath = join(
    dirname(root),
    `.manifest-publication-${process.pid}-${randomUUID()}.json`,
  );
  if ((await pathKind(temporaryPath)) !== 'missing') {
    throw new Error(`Generated publication-manifest path already exists: ${temporaryPath}`);
  }

  try {
    await writeFile(
      temporaryPath,
      `${JSON.stringify({...manifest, publicationId}, null, 2)}\n`,
      {flag: 'wx'},
    );
    await rename(temporaryPath, manifestPath);
  } catch (error) {
    console.error('Writing the content-addressed publication ID failed.', error);
    try {
      await rm(temporaryPath, {force: true});
    } catch (cleanupError) {
      console.error(`Temporary publication manifest cleanup failed: ${temporaryPath}`, cleanupError);
      throw new AggregateError(
        [error, cleanupError],
        'Writing the publication ID and cleaning its temporary manifest both failed.',
      );
    }
    throw error;
  }
  console.log(`[publication] Dataset publication ID: ${publicationId}`);
  return publicationId;
}
