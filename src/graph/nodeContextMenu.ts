export interface NodeContextAnchor {
  x: number;
  y: number;
}

export interface NodeContextViewport {
  width: number;
  height: number;
}

export interface NodeContextMenuPlacement {
  left: number;
  top: number;
  width: number;
  maxHeight: number;
}

const MENU_MARGIN = 8;
const MENU_GAP = 8;
const MENU_WIDTH = 300;
const PREFERRED_MENU_HEIGHT = 420;
const MIN_MENU_HEIGHT = 160;

function finitePositive(value: number, fallback: number): number {
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

/** Places a contextual node menu beside its pointer/node anchor without leaving the graph viewport. */
export function nodeContextMenuPlacement(
  anchor: NodeContextAnchor,
  viewport: NodeContextViewport,
  interfaceZoom = 1,
): NodeContextMenuPlacement {
  const zoom = finitePositive(interfaceZoom, 1);
  const viewportWidth = finitePositive(viewport.width, MENU_WIDTH + MENU_MARGIN * 2);
  const viewportHeight = finitePositive(viewport.height, PREFERRED_MENU_HEIGHT);
  const width = Math.min(
    MENU_WIDTH,
    Math.max(180, (viewportWidth - MENU_MARGIN * 2) / zoom),
  );
  const visualWidth = width * zoom;
  const preferredVisualHeight = Math.min(
    PREFERRED_MENU_HEIGHT * zoom,
    viewportHeight - MENU_MARGIN * 2,
  );
  const x = Number.isFinite(anchor.x) ? anchor.x : viewportWidth / 2;
  const y = Number.isFinite(anchor.y) ? anchor.y : viewportHeight / 2;

  let left = x + MENU_GAP;
  if (left + visualWidth > viewportWidth - MENU_MARGIN) {
    left = x - MENU_GAP - visualWidth;
  }
  left = Math.min(
    Math.max(MENU_MARGIN, left),
    Math.max(MENU_MARGIN, viewportWidth - MENU_MARGIN - visualWidth),
  );

  const roomBelow = viewportHeight - y - MENU_GAP - MENU_MARGIN;
  const roomAbove = y - MENU_GAP - MENU_MARGIN;
  const neitherSideFits =
    roomBelow < preferredVisualHeight && roomAbove < preferredVisualHeight;
  const openAbove = !neitherSideFits && roomBelow < preferredVisualHeight;
  const top = neitherSideFits
    ? MENU_MARGIN
    : openAbove
      ? Math.max(MENU_MARGIN, y - MENU_GAP - preferredVisualHeight)
      : Math.min(
          Math.max(MENU_MARGIN, y + MENU_GAP),
          Math.max(MENU_MARGIN, viewportHeight - MENU_MARGIN - MIN_MENU_HEIGHT * zoom),
        );
  const availableLogicalHeight = Math.max(
    MIN_MENU_HEIGHT,
    (viewportHeight - top - MENU_MARGIN) / zoom,
  );

  return {left, top, width, maxHeight: availableLogicalHeight};
}
