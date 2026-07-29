import {lstat, readdir, readFile, stat} from 'node:fs/promises';
import {join} from 'node:path';

export function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

export async function readJsonDocument(path, label = path) {
  let source;
  try {
    source = await readFile(path, 'utf8');
  } catch (error) {
    console.error(`Required export document could not be read: ${label}`, error);
    throw error;
  }
  try {
    return JSON.parse(source);
  } catch (error) {
    console.error(`Required export document contains invalid JSON: ${label}`, error);
    throw error;
  }
}

export async function pathKind(path) {
  try {
    const info = await stat(path);
    if (info.isDirectory()) return 'directory';
    if (info.isFile()) return 'file';
    return 'other';
  } catch (error) {
    if (error?.code === 'ENOENT') return 'missing';
    throw error;
  }
}

export async function requireDirectory(path, label) {
  const kind = await pathKind(path);
  if (kind !== 'directory') {
    throw new Error(`Required ${label} directory is ${kind}: ${path}`);
  }
}

export async function optionalDirectory(path, label) {
  const kind = await pathKind(path);
  if (kind === 'missing') {
    console.info(`Optional ${label} directory is absent; skipping it: ${path}`);
    return false;
  }
  if (kind !== 'directory') {
    throw new Error(`Optional ${label} path exists but is not a directory: ${path}`);
  }
  return true;
}

async function walkPlainDirectoryTree(directory, visitFile) {
  const pending = [directory];
  let fileCount = 0;
  while (pending.length > 0) {
    const current = pending.pop();
    const currentInfo = await lstat(current);
    if (currentInfo.isSymbolicLink() || !currentInfo.isDirectory()) {
      throw new Error(
        `Export tree contains an unsupported directory entry (symlinks and special files are refused): ${current}`,
      );
    }
    const entries = await readdir(current, {withFileTypes: true});
    for (const entry of entries) {
      const path = join(current, entry.name);
      if (entry.isDirectory()) pending.push(path);
      else if (entry.isFile()) {
        fileCount += 1;
        visitFile?.(path);
      }
      else {
        throw new Error(
          `Export tree contains an unsupported filesystem entry (symlinks and special files are refused): ${path}`,
        );
      }
    }
  }
  return fileCount;
}

/**
 * Iterative traversal avoids opening every category directory concurrently on
 * exports with hundreds of thousands of assets. Each visited directory is
 * checked with lstat so a top-level image-root symlink cannot be followed.
 */
export async function collectFiles(directory) {
  const files = [];
  await walkPlainDirectoryTree(directory, path => files.push(path));
  return files;
}

/**
 * Exhaustively reject symlinks, sockets, FIFOs, devices, and other special
 * entries without retaining every file path in memory.
 */
export async function assertPlainDirectoryTree(directory) {
  return walkPlainDirectoryTree(directory);
}

export async function assertRequiredExportDocuments(exportRoot) {
  const required = [
    [
      'manifest.json',
      value =>
        isRecord(value) &&
        Number.isSafeInteger(value.format) &&
        typeof value.generatedAt === 'string' &&
        value.generatedAt.length > 0 &&
        Number.isFinite(Date.parse(value.generatedAt)) &&
        typeof value.durationMs === 'number' &&
        Number.isFinite(value.durationMs) &&
        value.durationMs >= 0 &&
        typeof value.minecraft === 'string' &&
        value.minecraft.length > 0 &&
        typeof value.aborted === 'boolean' &&
        isRecord(value.counts) &&
        ['items', 'recipes', 'categories', 'mobs'].every(
          name => Number.isSafeInteger(value.counts[name]) && value.counts[name] >= 0,
        ) &&
        isRecord(value.settings) &&
        ['iconScale', 'recipeScale', 'mobCanvas'].every(
          name => Number.isSafeInteger(value.settings[name]) && value.settings[name] > 0,
        ) &&
        isRecord(value.mods) &&
        Object.values(value.mods).every(name => typeof name === 'string'),
      'a manifest object with generated metadata, positive integer render settings, non-negative core counts, and string-valued mods',
    ],
    [
      'items.json',
      value => isRecord(value) && Array.isArray(value.items),
      'an object with an items array',
    ],
    [
      'categories.json',
      value => isRecord(value) && Array.isArray(value.categories),
      'an object with a categories array',
    ],
    ['index.json', value => isRecord(value), 'an object keyed by exported ingredient key'],
  ];

  for (const [name, validate, expected] of required) {
    const value = await readJsonDocument(join(exportRoot, name), name);
    if (!validate(value)) {
      throw new Error(`Invalid required export document ${name}: expected ${expected}.`);
    }
    if (name === 'manifest.json' && value.aborted) {
      throw new Error('Export manifest is marked aborted; refusing to process partial data.');
    }
  }
}
