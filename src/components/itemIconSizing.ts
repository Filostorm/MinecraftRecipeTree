/** One Minecraft/JEI item occupies a 16×16 logical texel grid, independent of export canvas size. */
export const LOGICAL_ITEM_ICON_GRID_SIZE = 16;

/**
 * Pixel-art icons must occupy an integer multiple of their logical grid on the web.
 * Fractional ratios such as 44 / 16 distribute logical texels unevenly and visibly warp renders.
 */
export function isPixelGridAlignedItemIconSize(size: number): boolean {
  return (
    Number.isSafeInteger(size) &&
    size >= LOGICAL_ITEM_ICON_GRID_SIZE &&
    size % LOGICAL_ITEM_ICON_GRID_SIZE === 0
  );
}
