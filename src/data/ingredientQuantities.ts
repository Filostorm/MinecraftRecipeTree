/** Quantity semantics shared by recipe cards and graph calculations. */

const BULK_INGREDIENT_TYPES = new Set([
  'fluid',
  'gasstack',
  'infusionstack',
  'pigmentstack',
  'slurrystack',
]);

const warnedUnknownTypes = new Set<string>();

export function ingredientType(key: string): string {
  const separator = key.indexOf('|');
  return separator >= 0 ? key.slice(0, separator) : 'unknown';
}

/** Fluids and Mekanism chemical stacks use millibuckets rather than item counts. */
export function isBulkIngredient(key: string): boolean {
  return BULK_INGREDIENT_TYPES.has(ingredientType(key));
}

/** Convert the export's <= 0 sentinel into an explicit unknown quantity. */
export function normalizeIngredientAmount(key: string, raw: number): number | null {
  if (Number.isFinite(raw) && raw > 0) return raw;

  const type = ingredientType(key);
  if (!warnedUnknownTypes.has(type)) {
    warnedUnknownTypes.add(type);
    console.warn('Ingredient amount is unspecified in the recipe export.', {
      ingredientType: type,
      exampleKey: key,
      exportedAmount: raw,
    });
  }
  return null;
}

/**
 * Minecraft item stacks are discrete. Recipe exporters can expose fractional
 * item counts, so consumed inputs are rounded upward while bulk resources keep
 * their continuous quantities.
 */
export function normalizeRecipeInputAmount(key: string, raw: number): number | null {
  const amount = normalizeIngredientAmount(key, raw);
  if (amount == null || isBulkIngredient(key)) return amount;
  return Math.max(1, Math.ceil(amount));
}

export function formatAmount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 10_000) return `${Math.round(n / 1000)}k`;
  if (Number.isInteger(n)) return String(n);

  const roundedToHundredth = Math.round(n * 100) / 100;
  if (roundedToHundredth !== 0) return String(roundedToHundredth);

  if (n > 0) {
    const decimalPlaces = Math.min(6, Math.ceil(-Math.log10(n)) + 2);
    const precise = Number(n.toFixed(decimalPlaces));
    return precise > 0 ? String(precise) : '<0.000001';
  }

  return String(roundedToHundredth);
}

/** Graph/totals notation: item counts are ×N; bulk quantities are N mB. */
export function formatIngredientQuantity(key: string, amount: number | null): string {
  if (isBulkIngredient(key)) return `${amount == null ? '?' : formatAmount(amount)} mB`;
  return amount == null ? '×?' : `×${formatAmount(amount)}`;
}

/** Recipe-chip notation places the item multiplier before the ingredient name. */
export function formatIngredientQuantityPrefix(key: string, amount: number | null): string {
  if (isBulkIngredient(key)) return `${amount == null ? '?' : formatAmount(amount)} mB`;
  return amount == null ? '?×' : `${formatAmount(amount)}×`;
}

export function shouldShowIngredientQuantity(key: string, amount: number | null): boolean {
  return isBulkIngredient(key) || amount == null || amount !== 1;
}
