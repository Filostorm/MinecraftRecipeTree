export const INTERFACE_ZOOM_LEVELS = [0.75, 0.85, 1, 1.15, 1.3, 1.5] as const;
export const DEFAULT_INTERFACE_ZOOM = 1;

const INTERFACE_ZOOM_STORAGE_KEY = 'interfaceZoom';

/**
 * Scale only a picker recipe preview. Scaling the entire portal modal reduces
 * its logical viewport and makes controls wrap aggressively at larger UI zooms.
 */
export function uniformPickerRecipePreviewSize(
  logicalWidth: number,
  logicalHeight: number,
  interfaceZoom: number,
  maxWidth = 375,
  maxHeight = 192,
): {width: number; height: number} {
  if (!INTERFACE_ZOOM_LEVELS.some(level => level === interfaceZoom)) {
    throw new Error(`Interface zoom ${interfaceZoom} is not a supported zoom level.`);
  }
  if (
    ![logicalWidth, logicalHeight, maxWidth, maxHeight].every(Number.isFinite) ||
    logicalWidth <= 0 ||
    logicalHeight <= 0 ||
    maxWidth <= 0 ||
    maxHeight <= 0
  ) {
    throw new Error('Picker recipe preview dimensions and bounds must be positive finite numbers.');
  }
  const scale = Math.min(
    interfaceZoom,
    maxWidth / logicalWidth,
    maxHeight / logicalHeight,
  );
  return {
    width: Math.max(1, Math.round(logicalWidth * scale)),
    height: Math.max(1, Math.round(logicalHeight * scale)),
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
