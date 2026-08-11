export const DEFAULT_USE_BYPRODUCTS = true;

/** Preserve an explicit opt-out while enabling byproduct credits for new users. */
export function useByproductsFromStoredValue(
  stored: string | null | undefined,
): boolean {
  return stored !== '0';
}
