export interface GraphTransform {
  x: number;
  y: number;
  scale: number;
}

export interface PanGestureOrigin {
  transformX: number;
  transformY: number;
  gestureDx: number;
  gestureDy: number;
  scale: number;
}

export interface GraphViewportPoint {
  x: number;
  y: number;
}

export function graphViewportPointFromClient(
  clientX: number,
  clientY: number,
  rect: {left: number; top: number; width: number; height: number},
  viewport: {width: number; height: number},
): GraphViewportPoint {
  if (
    ![clientX, clientY, rect.left, rect.top, rect.width, rect.height, viewport.width, viewport.height]
      .every(Number.isFinite)
  ) {
    throw new Error('Graph pointer coordinates and dimensions must be finite numbers.');
  }
  if (rect.width <= 0 || rect.height <= 0 || viewport.width <= 0 || viewport.height <= 0) {
    throw new Error('Graph pointer dimensions must be positive.');
  }
  return {
    x: (clientX - rect.left) * (viewport.width / rect.width),
    y: (clientY - rect.top) * (viewport.height / rect.height),
  };
}

/**
 * Convert a browser wheel delta into a continuous multiplicative zoom factor.
 * Delta normalization keeps trackpads and line-based mouse wheels comparable.
 */
export function graphWheelZoomFactor(deltaY: number, deltaMode: number): number {
  if (!Number.isFinite(deltaY) || !Number.isInteger(deltaMode) || deltaMode < 0 || deltaMode > 2) {
    throw new Error('Graph wheel delta and mode are invalid.');
  }
  const pixelDelta = deltaY * (deltaMode === 1 ? 16 : deltaMode === 2 ? 800 : 1);
  const boundedDelta = Math.max(-160, Math.min(160, pixelDelta));
  return Math.exp(-boundedDelta * 0.0015);
}

const GRAPH_PINCH_ZOOM_SENSITIVITY = 2.25;

/**
 * Amplify pinch distance logarithmically. Raising each incremental ratio to a
 * constant power remains gesture-frame independent while requiring less travel.
 */
export function graphPinchZoomFactor(
  currentDistance: number,
  previousDistance: number,
): number {
  if (
    ![currentDistance, previousDistance].every(Number.isFinite) ||
    currentDistance <= 0 ||
    previousDistance <= 0
  ) {
    throw new Error('Graph pinch distances must be positive finite numbers.');
  }
  return Math.pow(currentDistance / previousDistance, GRAPH_PINCH_ZOOM_SENSITIVITY);
}

/**
 * Captures both coordinate systems at the same instant. PanResponder may grant
 * control after a movement threshold, so its dx/dy values are not necessarily zero.
 */
export function capturePanGestureOrigin(
  transform: GraphTransform,
  gestureDx: number,
  gestureDy: number,
): PanGestureOrigin {
  return {
    transformX: transform.x,
    transformY: transform.y,
    gestureDx,
    gestureDy,
    scale: transform.scale,
  };
}

/** Applies only movement performed after the origin was captured. */
export function transformForPanGesture(
  origin: PanGestureOrigin,
  gestureDx: number,
  gestureDy: number,
): GraphTransform {
  return {
    x: origin.transformX + gestureDx - origin.gestureDx,
    y: origin.transformY + gestureDy - origin.gestureDy,
    scale: origin.scale,
  };
}
