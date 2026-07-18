export const MAX_AUTOMATIC_GRAPH_SCALE = 1;
export const MIN_GRAPH_SCALE = 0.12;
export const GRAPH_FIT_PADDING = 60;

/**
 * Fit the graph without automatically enlarging its pixel art. Fractional scaling is
 * reserved for graphs that are too large for the available viewport.
 */
export function automaticGraphFitScale(
  viewportWidth: number,
  viewportHeight: number,
  boundsWidth: number,
  boundsHeight: number,
): number {
  if (
    ![viewportWidth, viewportHeight, boundsWidth, boundsHeight].every(Number.isFinite) ||
    viewportWidth <= 0 ||
    viewportHeight <= 0 ||
    boundsWidth <= 0 ||
    boundsHeight <= 0
  ) {
    throw new Error('Graph fit dimensions must be positive finite values.');
  }
  const availableWidth = Math.max(1, viewportWidth - GRAPH_FIT_PADDING);
  const availableHeight = Math.max(1, viewportHeight - GRAPH_FIT_PADDING);
  return Math.max(
    MIN_GRAPH_SCALE,
    Math.min(
      MAX_AUTOMATIC_GRAPH_SCALE,
      availableWidth / boundsWidth,
      availableHeight / boundsHeight,
    ),
  );
}
