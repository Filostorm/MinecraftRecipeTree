/**
 * Prefer an integer nearest-neighbor scale. Only oversized source art is reduced
 * fractionally because it cannot fit the available viewport at its native grid.
 */
export function pixelArtDisplaySize(
  width: number,
  height: number,
  maxWidth: number,
  maxHeight: number,
  maxScale = 2,
): {w: number; h: number} {
  if (
    ![width, height, maxWidth, maxHeight, maxScale].every(Number.isFinite) ||
    width <= 0 ||
    height <= 0 ||
    maxWidth <= 0 ||
    maxHeight <= 0 ||
    maxScale < 1
  ) {
    throw new Error('Pixel-art display dimensions and scale bounds must be positive finite values.');
  }
  if (width <= maxWidth && height <= maxHeight) {
    const scale = Math.max(
      1,
      Math.min(Math.floor(maxScale), Math.floor(maxWidth / width), Math.floor(maxHeight / height)),
    );
    return {w: Math.round(width * scale), h: Math.round(height * scale)};
  }
  const constrainedScale = Math.min(maxWidth / width, maxHeight / height);
  return {
    w: Math.max(1, Math.round(width * constrainedScale)),
    h: Math.max(1, Math.round(height * constrainedScale)),
  };
}

/** Convert logical pixel-art dimensions directly into React Native image-style keys. */
export function pixelArtImageStyle(
  width: number,
  height: number,
  maxWidth: number,
  maxHeight: number,
  maxScale = 2,
): {width: number; height: number} {
  const size = pixelArtDisplaySize(width, height, maxWidth, maxHeight, maxScale);
  return {width: size.w, height: size.h};
}
