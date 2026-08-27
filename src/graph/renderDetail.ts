export const DENSE_GRAPH_NODE_THRESHOLD = 160;
export const DENSE_GRAPH_LOW_DETAIL_SCALE = 0.42;
export const NODE_AMOUNT_LABEL_MIN_SCALE = 0.6;
export const LOW_DETAIL_RECIPE_HOVER_TARGET_SCALE = 0.72;
export const LOW_DETAIL_RECIPE_HOVER_MAX_MAGNIFICATION = 10;
export const LOW_DETAIL_RECIPE_HOVER_ENTER_PADDING = 10;
export const LOW_DETAIL_RECIPE_HOVER_RETAIN_PADDING = 18;

export interface LowDetailHoverNode {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
  source?: {kind: string};
}

export interface LowDetailHoverTransform {
  x: number;
  y: number;
  scale: number;
}

export interface LowDetailHoverPoint {
  x: number;
  y: number;
}

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

/** Duplicate recipe branches become prohibitively expensive once a graph is dense. */
export function shouldRequireUniqueRecipes(nodeCount: number): boolean {
  return Number.isFinite(nodeCount) && nodeCount >= DENSE_GRAPH_NODE_THRESHOLD;
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

function screenRectDistanceSquared(
  node: LowDetailHoverNode,
  transform: LowDetailHoverTransform,
  point: LowDetailHoverPoint,
): number {
  const left = node.x * transform.scale + transform.x;
  const top = node.y * transform.scale + transform.y;
  const right = (node.x + node.w) * transform.scale + transform.x;
  const bottom = (node.y + node.h) * transform.scale + transform.y;
  const dx = point.x < left ? left - point.x : point.x > right ? point.x - right : 0;
  const dy = point.y < top ? top - point.y : point.y > bottom ? point.y - bottom : 0;
  return dx * dx + dy * dy;
}

/**
 * Resolve low-detail hover in viewport space instead of relying on sub-pixel DOM
 * hit targets. The larger retain radius prevents the enlarged preview or a tiny
 * pointer movement from repeatedly dismissing and recreating itself.
 */
export function lowDetailRecipeHoverNodeId(
  nodes: readonly LowDetailHoverNode[],
  transform: LowDetailHoverTransform,
  point: LowDetailHoverPoint,
  currentNodeId: string | null,
  enterPadding = LOW_DETAIL_RECIPE_HOVER_ENTER_PADDING,
  retainPadding = LOW_DETAIL_RECIPE_HOVER_RETAIN_PADDING,
): string | null {
  if (
    ![transform.x, transform.y, transform.scale, point.x, point.y, enterPadding, retainPadding].every(
      Number.isFinite,
    ) ||
    transform.scale <= 0 ||
    enterPadding < 0 ||
    retainPadding < enterPadding
  ) {
    throw new Error('Low-detail recipe hover geometry is invalid.');
  }

  const currentNode = currentNodeId
    ? nodes.find(node => node.id === currentNodeId && node.source?.kind === 'recipe')
    : undefined;
  if (
    currentNode &&
    screenRectDistanceSquared(currentNode, transform, point) <= retainPadding * retainPadding
  ) {
    return currentNode.id;
  }

  let nearestNodeId: string | null = null;
  let nearestDistanceSquared = enterPadding * enterPadding;
  for (const node of nodes) {
    if (node.source?.kind !== 'recipe') continue;
    const distanceSquared = screenRectDistanceSquared(node, transform, point);
    if (distanceSquared <= nearestDistanceSquared) {
      nearestNodeId = node.id;
      nearestDistanceSquared = distanceSquared;
    }
  }
  return nearestNodeId;
}
