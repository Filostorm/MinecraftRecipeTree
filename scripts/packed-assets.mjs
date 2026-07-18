export const PACKED_IMAGE_FORMAT = 'coordinate-v1';
export const MAX_PACK_BYTES = 1024 * 1024;
export const PACKED_IMAGE_ROUTE_PATTERN = /^assets\/s\/(\d+)-(\d+)-(\d+)\.webp$/;

export function packedImagePath(packNumber, offset, length) {
  return (
    `assets/s/${String(packNumber).padStart(3, '0')}-` +
    `${String(offset)}-${String(length)}.webp`
  );
}

export function parsePackedImagePath(value) {
  if (typeof value !== 'string') return null;
  const match = PACKED_IMAGE_ROUTE_PATTERN.exec(value);
  if (!match) return null;
  const numbers = match.slice(1).map(Number);
  if (!numbers.every(Number.isSafeInteger)) return null;
  const [packNumber, offset, length] = numbers;
  if (packNumber < 0 || offset < 0 || length <= 0) return null;
  return {packNumber, offset, length};
}

export function packFileKey(packNumber) {
  return `assets/pack-${String(packNumber).padStart(3, '0')}.bin`;
}
