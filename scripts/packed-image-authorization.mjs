export const PACKED_IMAGE_AUTHORIZATION_FORMAT =
  'mrt-packed-image-authorization-index-v1';
export const PACKED_IMAGE_AUTHORIZATION_MAGIC = 'MRPI';
export const PACKED_IMAGE_AUTHORIZATION_VERSION = 1;
export const PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES = 20;
export const PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES = 8;
export const MAX_PACKED_IMAGE_AUTHORIZATION_BYTES = 512 * 1024;

const MAGIC_BYTES = Buffer.from(PACKED_IMAGE_AUTHORIZATION_MAGIC, 'ascii');

function requireUint32(value, label, {positive = false} = {}) {
  if (
    !Number.isSafeInteger(value) ||
    value < (positive ? 1 : 0) ||
    value > 0xffff_ffff
  ) {
    throw new Error(`${label} must be ${positive ? 'a positive' : 'a non-negative'} uint32.`);
  }
  return value;
}

/**
 * Encode the exact WebP byte ranges authorized inside one immutable pack.
 *
 * MRPI v1 is deliberately small and language-neutral:
 *   0..3   ASCII "MRPI"
 *   4..5   uint16 BE version (1)
 *   6..7   uint16 BE header length (20)
 *   8..11  uint32 BE pack number
 *   12..15 uint32 BE complete pack byte length
 *   16..19 uint32 BE entry count
 *   20..   repeated uint32 BE offset, uint32 BE length pairs
 *
 * Entries must be canonical, strictly contiguous, and cover the complete pack.
 * That makes every accepted range an image boundary and leaves no unindexed bytes.
 */
export function encodePackedImageAuthorizationIndex({packNumber, packBytes, entries}) {
  requireUint32(packNumber, 'MRPI pack number');
  requireUint32(packBytes, 'MRPI pack byte length', {positive: true});
  if (!Array.isArray(entries) || entries.length === 0) {
    throw new Error('MRPI entries must be a non-empty array.');
  }
  requireUint32(entries.length, 'MRPI entry count', {positive: true});
  const byteLength =
    PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES +
    entries.length * PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES;
  if (byteLength > MAX_PACKED_IMAGE_AUTHORIZATION_BYTES) {
    throw new Error(
      `MRPI authorization index is ${byteLength} bytes, above the ` +
        `${MAX_PACKED_IMAGE_AUTHORIZATION_BYTES}-byte security bound.`,
    );
  }

  const output = Buffer.allocUnsafe(byteLength);
  MAGIC_BYTES.copy(output, 0);
  output.writeUInt16BE(PACKED_IMAGE_AUTHORIZATION_VERSION, 4);
  output.writeUInt16BE(PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES, 6);
  output.writeUInt32BE(packNumber, 8);
  output.writeUInt32BE(packBytes, 12);
  output.writeUInt32BE(entries.length, 16);

  let cursor = 0;
  for (const [entryIndex, entry] of entries.entries()) {
    if (!Array.isArray(entry) || entry.length !== 2) {
      throw new Error(`MRPI entry ${entryIndex} must be [offset, length].`);
    }
    const [offset, length] = entry;
    requireUint32(offset, `MRPI entry ${entryIndex} offset`);
    requireUint32(length, `MRPI entry ${entryIndex} length`, {positive: true});
    if (offset !== cursor || offset + length > packBytes) {
      throw new Error(
        `MRPI entry ${entryIndex} is not the canonical contiguous range beginning at ${cursor}.`,
      );
    }
    const position =
      PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES +
      entryIndex * PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES;
    output.writeUInt32BE(offset, position);
    output.writeUInt32BE(length, position + 4);
    cursor += length;
  }
  if (cursor !== packBytes) {
    throw new Error(`MRPI entries cover ${cursor}/${packBytes} pack bytes.`);
  }
  return output;
}

/** Parse and fully validate an MRPI v1 authorization index. */
export function parsePackedImageAuthorizationIndex(
  value,
  {expectedPackNumber, expectedPackBytes} = {},
) {
  const bytes = Buffer.isBuffer(value)
    ? value
    : value instanceof Uint8Array
      ? Buffer.from(value.buffer, value.byteOffset, value.byteLength)
      : null;
  if (!bytes) throw new Error('MRPI authorization index must be bytes.');
  if (
    bytes.length <
      PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES + PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES ||
    bytes.length > MAX_PACKED_IMAGE_AUTHORIZATION_BYTES
  ) {
    throw new Error(`MRPI authorization index has invalid byte length ${bytes.length}.`);
  }
  if (!bytes.subarray(0, 4).equals(MAGIC_BYTES)) {
    throw new Error('MRPI authorization index has invalid magic bytes.');
  }
  if (bytes.readUInt16BE(4) !== PACKED_IMAGE_AUTHORIZATION_VERSION) {
    throw new Error('MRPI authorization index has an unsupported version.');
  }
  if (bytes.readUInt16BE(6) !== PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES) {
    throw new Error('MRPI authorization index has a non-canonical header length.');
  }

  const packNumber = bytes.readUInt32BE(8);
  const packBytes = bytes.readUInt32BE(12);
  const entryCount = bytes.readUInt32BE(16);
  const expectedLength =
    PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES +
    entryCount * PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES;
  if (entryCount === 0 || expectedLength !== bytes.length) {
    throw new Error(
      `MRPI authorization index declares ${entryCount} entries and ${expectedLength} bytes, ` +
        `but contains ${bytes.length}.`,
    );
  }
  if (packBytes === 0) throw new Error('MRPI authorization index declares an empty pack.');
  if (expectedPackNumber !== undefined && packNumber !== expectedPackNumber) {
    throw new Error(
      `MRPI authorization index targets pack ${packNumber}; expected ${expectedPackNumber}.`,
    );
  }
  if (expectedPackBytes !== undefined && packBytes !== expectedPackBytes) {
    throw new Error(
      `MRPI authorization index declares ${packBytes} pack bytes; expected ${expectedPackBytes}.`,
    );
  }

  const entries = new Array(entryCount);
  let cursor = 0;
  for (let entryIndex = 0; entryIndex < entryCount; entryIndex += 1) {
    const position =
      PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES +
      entryIndex * PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES;
    const offset = bytes.readUInt32BE(position);
    const length = bytes.readUInt32BE(position + 4);
    if (offset !== cursor || length === 0 || offset + length > packBytes) {
      throw new Error(
        `MRPI authorization entry ${entryIndex} is not a canonical contiguous in-pack range.`,
      );
    }
    entries[entryIndex] = [offset, length];
    cursor += length;
  }
  if (cursor !== packBytes) {
    throw new Error(`MRPI authorization ranges cover ${cursor}/${packBytes} pack bytes.`);
  }
  return {packNumber, packBytes, entries};
}
