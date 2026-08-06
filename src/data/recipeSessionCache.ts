import type {Recipe, RecipeRef} from '../types';

export const MAX_SESSION_RECIPES = 4096;

export function recipeSessionCacheKey([categoryIndex, recipeIndex]: RecipeRef): string {
  return `${categoryIndex}:${recipeIndex}`;
}

/**
 * Keeps recently resolved recipe cards available while the active dataset remains mounted.
 * Raw recipe shards have a separate byte-bounded cache; this smaller object cache prevents
 * closing and reopening an item from repeating recipe normalization and classification.
 */
export class RecipeSessionCache {
  private readonly entries = new Map<string, Recipe>();
  private readonly maximumEntries: number;

  constructor(maximumEntries = MAX_SESSION_RECIPES) {
    if (!Number.isSafeInteger(maximumEntries) || maximumEntries <= 0) {
      throw new Error('Recipe session cache requires a positive safe entry limit.');
    }
    this.maximumEntries = maximumEntries;
  }

  clear(): void {
    this.entries.clear();
  }

  /** Read without changing recency, so React renders remain side-effect free. */
  peek(ref: RecipeRef): Recipe | undefined {
    return this.entries.get(recipeSessionCacheKey(ref));
  }

  get(ref: RecipeRef): Recipe | undefined {
    const key = recipeSessionCacheKey(ref);
    const recipe = this.entries.get(key);
    if (!recipe) return undefined;
    this.entries.delete(key);
    this.entries.set(key, recipe);
    return recipe;
  }

  set(ref: RecipeRef, recipe: Recipe): void {
    const key = recipeSessionCacheKey(ref);
    this.entries.delete(key);
    this.entries.set(key, recipe);
    while (this.entries.size > this.maximumEntries) {
      const oldest = this.entries.keys().next().value as string | undefined;
      if (oldest === undefined) break;
      this.entries.delete(oldest);
    }
  }
}
