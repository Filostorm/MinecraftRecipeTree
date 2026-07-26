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
