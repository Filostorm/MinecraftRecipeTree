/** JEI item renderers produce one 16×16 logical icon. */
export const NATIVE_ITEM_ICON_SIZE = 16;

/**
 * Pixel-art icons must occupy an integer multiple of their source grid on the web.
 * Fractional ratios such as 44 / 16 distribute texels unevenly and visibly warp renders.
 */
export function isPixelGridAlignedItemIconSize(size: number): boolean {
  return (
    Number.isSafeInteger(size) &&
    size >= NATIVE_ITEM_ICON_SIZE &&
    size % NATIVE_ITEM_ICON_SIZE === 0
  );
}
