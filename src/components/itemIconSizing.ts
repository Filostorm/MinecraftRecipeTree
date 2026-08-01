/** One Minecraft/JEI item occupies a 16×16 logical texel grid, independent of export canvas size. */
export const LOGICAL_ITEM_ICON_GRID_SIZE = 16;
export const RECIPE_HISTORY_ITEM_ICON_SIZE = 32;
export const ROOT_QUICK_ACTION_ITEM_ICON_SIZE = 32;
export const RADIAL_ROOT_ITEM_ICON_SIZE = 48;

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
