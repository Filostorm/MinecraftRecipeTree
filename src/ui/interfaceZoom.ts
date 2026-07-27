export const INTERFACE_ZOOM_LEVELS = [0.75, 0.85, 1, 1.15, 1.3, 1.5] as const;
export const DEFAULT_INTERFACE_ZOOM = 1;

const INTERFACE_ZOOM_STORAGE_KEY = 'interfaceZoom';

export interface WebModalZoomStyle {
  zoom: number;
  width: `${number}%`;
  maxWidth: number;
  maxHeight: `${number}%`;
}

/**
 * Scale portal-rendered modal content while keeping its visual bounds inside
 * the viewport. CSS zoom enlarges the descendants too, including raster recipe
 * previews; the inverse layout dimensions prevent the scaled card overflowing.
 */
export function webModalZoomStyle(
  interfaceZoom: number,
  visualMaxWidth: number,
  visualMaxHeightPercent: number,
): WebModalZoomStyle {
  if (!INTERFACE_ZOOM_LEVELS.some(level => level === interfaceZoom)) {
    throw new Error(`Interface zoom ${interfaceZoom} is not a supported zoom level.`);
  }
  if (!Number.isFinite(visualMaxWidth) || visualMaxWidth <= 0) {
    throw new Error('Modal visual maximum width must be a positive finite number.');
  }
  if (
    !Number.isFinite(visualMaxHeightPercent) ||
    visualMaxHeightPercent <= 0 ||
    visualMaxHeightPercent > 100
  ) {
    throw new Error('Modal visual maximum height must be within 0–100 percent.');
  }
  return {
    zoom: interfaceZoom,
    width: `${100 / interfaceZoom}%`,
    maxWidth: visualMaxWidth / interfaceZoom,
    maxHeight: `${visualMaxHeightPercent / interfaceZoom}%`,
  };
}

export function adjacentInterfaceZoom(
  current: number,
  direction: -1 | 1,
): number {
  if (!Number.isFinite(current)) {
    throw new Error('Interface zoom must be a finite number.');
  }
  const currentIndex = INTERFACE_ZOOM_LEVELS.findIndex(level => level === current);
  if (currentIndex === -1) {
    throw new Error(`Interface zoom ${current} is not a supported zoom level.`);
  }
  const nextIndex = Math.min(
    INTERFACE_ZOOM_LEVELS.length - 1,
    Math.max(0, currentIndex + direction),
  );
  return INTERFACE_ZOOM_LEVELS[nextIndex];
}

export function loadInterfaceZoom(): number {
  try {
    const stored = globalThis.localStorage?.getItem(INTERFACE_ZOOM_STORAGE_KEY);
    if (stored == null) return DEFAULT_INTERFACE_ZOOM;
    const parsed = Number(stored);
    if (INTERFACE_ZOOM_LEVELS.some(level => level === parsed)) return parsed;
    console.warn('Stored interface zoom was invalid and has been reset.', {stored});
  } catch (error) {
    console.error('Interface zoom could not be loaded from localStorage.', error);
  }
  return DEFAULT_INTERFACE_ZOOM;
}

export function persistInterfaceZoom(value: number): void {
  if (!INTERFACE_ZOOM_LEVELS.some(level => level === value)) {
    throw new Error(`Interface zoom ${value} is not a supported zoom level.`);
  }
  const storage = globalThis.localStorage;
  if (!storage) {
    console.warn('Interface zoom is using memory only because localStorage is unavailable.');
    return;
  }
  storage.setItem(INTERFACE_ZOOM_STORAGE_KEY, String(value));
}
