import type {LaidNode} from './layout.ts';
import type {GraphTransform} from './panGesture.ts';

export const LOW_DETAIL_RASTER_ICON_SIZE = 32;

export interface LowDetailRasterGeometry {
  left: number;
  top: number;
  width: number;
  height: number;
  iconLeft: number;
  iconTop: number;
  iconSize: number;
}

/**
 * Project graph geometry directly into a fixed viewport canvas. Keeping this
 * projection out of the DOM lets far-zoom trees paint as one inert bitmap.
 */
export function lowDetailRasterGeometry(
  node: Pick<LaidNode, 'x' | 'y' | 'w' | 'h'>,
  transform: GraphTransform,
): LowDetailRasterGeometry {
  if (
    ![
      node.x,
      node.y,
      node.w,
      node.h,
      transform.x,
      transform.y,
      transform.scale,
    ].every(Number.isFinite) ||
    node.w <= 0 ||
    node.h <= 0 ||
    transform.scale <= 0
  ) {
    throw new Error('Low-detail raster geometry requires positive finite dimensions and scale.');
  }

  const centerX = (node.x + node.w / 2) * transform.scale + transform.x;
  const centerY = (node.y + node.h / 2) * transform.scale + transform.y;
  const width = Math.max(1, node.w * transform.scale);
  const height = Math.max(1, node.h * transform.scale);
  const iconSize = Math.max(1, LOW_DETAIL_RASTER_ICON_SIZE * transform.scale);
  return {
    left: centerX - width / 2,
    top: centerY - height / 2,
    width,
    height,
    iconLeft: centerX - iconSize / 2,
    iconTop: centerY - iconSize / 2,
    iconSize,
  };
}
