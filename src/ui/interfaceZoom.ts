export const MINIMUM_INTERFACE_ZOOM = 0.75;
export const MAXIMUM_INTERFACE_ZOOM = 1.5;
export const INTERFACE_ZOOM_STEP = 0.05;
export const DEFAULT_INTERFACE_ZOOM = 1;

const INTERFACE_ZOOM_STORAGE_KEY = 'interfaceZoom';
const ZOOM_PRECISION = 2;

export function normalizeInterfaceZoom(value: number): number {
  if (!Number.isFinite(value)) {
    throw new Error('Interface zoom must be a finite number.');
  }
  const stepIndex = Math.round(
    (value - MINIMUM_INTERFACE_ZOOM) / INTERFACE_ZOOM_STEP,
  );
  const normalized = Number(
    (MINIMUM_INTERFACE_ZOOM + stepIndex * INTERFACE_ZOOM_STEP).toFixed(
      ZOOM_PRECISION,
    ),
  );
  if (
    normalized < MINIMUM_INTERFACE_ZOOM ||
    normalized > MAXIMUM_INTERFACE_ZOOM ||
    Math.abs(normalized - value) > 1e-6
  ) {
    throw new Error(
      `Interface zoom ${String(value)} is outside the supported slider range or step interval.`,
    );
  }
  return normalized;
}

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
  const normalizedZoom = normalizeInterfaceZoom(interfaceZoom);
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
    normalizedZoom,
    maxWidth / logicalWidth,
    maxHeight / logicalHeight,
  );
  return {
    width: Math.max(1, Math.round(logicalWidth * scale)),
    height: Math.max(1, Math.round(logicalHeight * scale)),
  };
}

export function loadInterfaceZoom(): number {
  try {
    const stored = globalThis.localStorage?.getItem(INTERFACE_ZOOM_STORAGE_KEY);
    if (stored == null) return DEFAULT_INTERFACE_ZOOM;
    const parsed = Number(stored);
    try {
      return normalizeInterfaceZoom(parsed);
    } catch (error) {
      console.warn('Stored interface zoom was invalid and has been reset.', {
        stored,
        error,
      });
    }
  } catch (error) {
    console.error('Interface zoom could not be loaded from localStorage.', error);
  }
  return DEFAULT_INTERFACE_ZOOM;
}

export function persistInterfaceZoom(value: number): void {
  const normalized = normalizeInterfaceZoom(value);
  const storage = globalThis.localStorage;
  if (!storage) {
    console.warn('Interface zoom is using memory only because localStorage is unavailable.');
    return;
  }
  storage.setItem(INTERFACE_ZOOM_STORAGE_KEY, String(normalized));
}
