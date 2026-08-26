export const DENSE_GRAPH_NODE_THRESHOLD = 160;
export const DENSE_GRAPH_LOW_DETAIL_SCALE = 0.42;
export const NODE_AMOUNT_LABEL_MIN_SCALE = 0.6;
export const LOW_DETAIL_RECIPE_HOVER_TARGET_SCALE = 0.72;
export const LOW_DETAIL_RECIPE_HOVER_MAX_MAGNIFICATION = 10;

/** Low zoom makes labels and recipe previews unreadable; keep only cheap geometry for dense trees. */
export function shouldUseLowDetailGraph(
  scale: number,
  nodeCount: number,
): boolean {
  return (
    Number.isFinite(scale) &&
    scale > 0 &&
    scale <= DENSE_GRAPH_LOW_DETAIL_SCALE &&
    nodeCount >= DENSE_GRAPH_NODE_THRESHOLD
  );
}

/** Amount text turns into visual noise before node contents are otherwise too small to render. */
export function shouldShowNodeAmounts(
  scale: number,
  exportingTree = false,
): boolean {
  if (exportingTree) return true;
  return Number.isFinite(scale) && scale >= NODE_AMOUNT_LABEL_MIN_SCALE;
}

/** Magnify one hovered low-detail recipe without mounting detail for the rest of the tree. */
export function lowDetailRecipeHoverMagnification(scale: number): number {
  if (!Number.isFinite(scale) || scale <= 0) return 1;
  return Math.max(
    1,
    Math.min(
      LOW_DETAIL_RECIPE_HOVER_MAX_MAGNIFICATION,
      LOW_DETAIL_RECIPE_HOVER_TARGET_SCALE / scale,
    ),
  );
}
