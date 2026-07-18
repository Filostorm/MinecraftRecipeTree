import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {dirname, join, posix} from 'node:path';

export const SHARDED_JSON_FORMAT = 'mrt-sharded-json-v1';
export const MAX_SHARD_BYTES = 8 * 1024 * 1024;

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value, expected) {
  const actual = Object.keys(value).sort();
  const sortedExpected = [...expected].sort();
  return (
    actual.length === sortedExpected.length &&
    actual.every((key, index) => key === sortedExpected[index])
  );
}

function looksLikeShardedDocument(value) {
  return (
    isRecord(value) &&
    (value.format === SHARDED_JSON_FORMAT ||
      ('parts' in value && ('kind' in value || 'count' in value)))
  );
}

function safeRelativePath(value) {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    !value.startsWith('/') &&
    !value.includes('\\') &&
    posix.normalize(value) === value &&
    value !== '..' &&
    !value.startsWith('../')
  );
}

function serializedEntry(value) {
  return JSON.stringify(value);
}

function serializedObjectEntry(key, value) {
  return `${JSON.stringify(key)}:${JSON.stringify(value)}`;
}

function utf8Bytes(value) {
  return Buffer.byteLength(value, 'utf8');
}

function partitionSerializedEntries(entries, wrapperBytes, maxBytes, label) {
  const parts = [];
  let current = [];
  let currentBytes = wrapperBytes;
  for (const entry of entries) {
    const entryBytes = utf8Bytes(entry);
    const addedBytes = entryBytes + (current.length > 0 ? 1 : 0);
    if (wrapperBytes + entryBytes > maxBytes) {
      throw new Error(
        `${label} contains one JSON entry requiring ${wrapperBytes + entryBytes} bytes, ` +
          `above the ${maxBytes}-byte shard limit.`,
      );
    }
    if (current.length > 0 && currentBytes + addedBytes > maxBytes) {
      parts.push(current);
      current = [];
      currentBytes = wrapperBytes;
    }
    current.push(entry);
    currentBytes += entryBytes + (current.length > 1 ? 1 : 0);
  }
  if (current.length > 0) parts.push(current);
  return parts;
}

async function writePart(root, relativePath, source) {
  const bytes = Buffer.from(source, 'utf8');
  if (bytes.length > MAX_SHARD_BYTES) {
    throw new Error(
      `Generated JSON shard ${relativePath} is ${bytes.length} bytes, above the ` +
        `${MAX_SHARD_BYTES}-byte limit.`,
    );
  }
  const path = join(root, ...relativePath.split('/'));
  await mkdir(dirname(path), {recursive: true});
  await writeFile(path, bytes, {flag: 'wx'});
  return bytes.length;
}

function partName(partRoot, index) {
  return posix.join(partRoot, `part-${String(index).padStart(3, '0')}.json`);
}

/**
 * Return the original array when it fits. Oversized arrays are written as
 * contiguous, index-addressable shards and replaced by a small descriptor.
 */
export async function shardArrayDocument(values, root, partRoot, label) {
  if (!Array.isArray(values)) throw new Error(`${label} is not an array.`);
  const entries = values.map(serializedEntry);
  const fullBytes = 3 + entries.reduce((sum, entry, index) => {
    return sum + utf8Bytes(entry) + (index > 0 ? 1 : 0);
  }, 0);
  if (fullBytes <= MAX_SHARD_BYTES) return values;

  const groups = partitionSerializedEntries(entries, 3, MAX_SHARD_BYTES, label);
  const parts = [];
  let start = 0;
  for (const [index, group] of groups.entries()) {
    const path = partName(partRoot, index);
    const bytes = await writePart(root, path, `[${group.join(',')}]\n`);
    parts.push({path, start, count: group.length, bytes});
    start += group.length;
  }
  const descriptor = {
    format: SHARDED_JSON_FORMAT,
    kind: 'array',
    count: values.length,
    parts,
  };
  if (utf8Bytes(`${JSON.stringify(descriptor)}\n`) > MAX_SHARD_BYTES) {
    throw new Error(`${label} generated an oversized shard descriptor.`);
  }
  return descriptor;
}

/** Return the original object when it fits, otherwise write bounded map shards. */
export async function shardObjectDocument(value, root, partRoot, label) {
  if (!isRecord(value)) throw new Error(`${label} is not an object.`);
  const entries = Object.entries(value).map(([key, entry]) =>
    serializedObjectEntry(key, entry),
  );
  const fullBytes = 3 + entries.reduce((sum, entry, index) => {
    return sum + utf8Bytes(entry) + (index > 0 ? 1 : 0);
  }, 0);
  if (fullBytes <= MAX_SHARD_BYTES) return value;

  const groups = partitionSerializedEntries(entries, 3, MAX_SHARD_BYTES, label);
  const parts = [];
  for (const [index, group] of groups.entries()) {
    const path = partName(partRoot, index);
    const bytes = await writePart(root, path, `{${group.join(',')}}\n`);
    parts.push({path, count: group.length, bytes});
  }
  const descriptor = {
    format: SHARDED_JSON_FORMAT,
    kind: 'object',
    count: Object.keys(value).length,
    parts,
  };
  if (utf8Bytes(`${JSON.stringify(descriptor)}\n`) > MAX_SHARD_BYTES) {
    throw new Error(`${label} generated an oversized shard descriptor.`);
  }
  return descriptor;
}

export function isShardedDocument(value, kind) {
  return (
    isRecord(value) &&
    value.format === SHARDED_JSON_FORMAT &&
    value.kind === kind
  );
}

function assertDescriptorHeader(value, kind, label) {
  if (!isShardedDocument(value, kind)) {
    throw new Error(`${label} is not a ${SHARDED_JSON_FORMAT} ${kind} descriptor.`);
  }
  if (!hasExactKeys(value, ['format', 'kind', 'count', 'parts'])) {
    throw new Error(`${label} must contain exactly format, kind, count, and parts.`);
  }
  if (!Number.isSafeInteger(value.count) || value.count < 0) {
    throw new Error(`${label}.count must be a non-negative safe integer.`);
  }
  if (!Array.isArray(value.parts) || (value.count > 0 && value.parts.length === 0)) {
    throw new Error(`${label}.parts must enumerate every shard.`);
  }
}

async function readVerifiedPart(root, part, label, seenPaths, kind) {
  const expectedKeys = kind === 'array'
    ? ['path', 'start', 'count', 'bytes']
    : ['path', 'count', 'bytes'];
  if (
    !isRecord(part) ||
    !hasExactKeys(part, expectedKeys) ||
    !safeRelativePath(part.path)
  ) {
    throw new Error(`${label} contains an invalid shard path.`);
  }
  if (seenPaths.has(part.path)) {
    throw new Error(`${label} repeats shard path ${part.path}.`);
  }
  seenPaths.add(part.path);
  if (!Number.isSafeInteger(part.count) || part.count <= 0) {
    throw new Error(`${label} shard ${part.path} has an invalid count.`);
  }
  if (
    !Number.isSafeInteger(part.bytes) ||
    part.bytes <= 0 ||
    part.bytes > MAX_SHARD_BYTES
  ) {
    throw new Error(`${label} shard ${part.path} has an invalid byte length.`);
  }
  const bytes = await readFile(join(root, ...part.path.split('/')));
  if (bytes.length !== part.bytes) {
    throw new Error(
      `${label} shard ${part.path} declares ${part.bytes} bytes but contains ${bytes.length}.`,
    );
  }
  let parsed;
  try {
    parsed = JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`${label} shard ${part.path} contains invalid JSON: ${error.message}`);
  }
  return parsed;
}

export async function readArrayDocument(root, value, label) {
  if (Array.isArray(value)) return {value, shardPaths: []};
  assertDescriptorHeader(value, 'array', label);
  const result = [];
  const shardPaths = [];
  const seenPaths = new Set();
  let expectedStart = 0;
  for (const [index, part] of value.parts.entries()) {
    if (!Number.isSafeInteger(part?.start) || part.start !== expectedStart) {
      throw new Error(
        `${label}.parts[${index}].start must be the contiguous offset ${expectedStart}.`,
      );
    }
    const parsed = await readVerifiedPart(root, part, label, seenPaths, 'array');
    if (!Array.isArray(parsed) || parsed.length !== part.count) {
      throw new Error(`${label} shard ${part.path} does not match its declared array count.`);
    }
    result.push(...parsed);
    shardPaths.push(part.path);
    expectedStart += part.count;
  }
  if (expectedStart !== value.count) {
    throw new Error(`${label} shards contain ${expectedStart} entries; expected ${value.count}.`);
  }
  return {value: result, shardPaths};
}

export async function readObjectDocument(root, value, label) {
  if (isRecord(value) && !looksLikeShardedDocument(value)) {
    return {value, shardPaths: []};
  }
  assertDescriptorHeader(value, 'object', label);
  const result = Object.create(null);
  const shardPaths = [];
  const seenPaths = new Set();
  let count = 0;
  for (const part of value.parts) {
    const parsed = await readVerifiedPart(root, part, label, seenPaths, 'object');
    if (!isRecord(parsed) || Object.keys(parsed).length !== part.count) {
      throw new Error(`${label} shard ${part.path} does not match its declared object count.`);
    }
    for (const [key, entry] of Object.entries(parsed)) {
      if (Object.prototype.hasOwnProperty.call(result, key)) {
        throw new Error(`${label} repeats object key ${key} across shards.`);
      }
      result[key] = entry;
      count += 1;
    }
    shardPaths.push(part.path);
  }
  if (count !== value.count) {
    throw new Error(`${label} shards contain ${count} entries; expected ${value.count}.`);
  }
  return {value: result, shardPaths};
}
