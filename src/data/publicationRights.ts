import type {CatalogItem, Category, Manifest, Mob, Recipe} from '../types';
import {GTNH_STRUCTURED_DATA_ONLY_POLICY} from './datasetAttribution.ts';

function hasOwnVisualReference(value: unknown, key: 'icon' | 'img'): boolean {
  return !!value &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    Object.prototype.hasOwnProperty.call(value, key);
}

export function shouldFetchRecipePreviewSidecar(manifest: Manifest): boolean {
  return (
    manifest.publicationPolicy !== GTNH_STRUCTURED_DATA_ONLY_POLICY &&
    manifest.web?.recipeImages?.mode === 'omitted'
  );
}

export function catalogVisualReferenceCounts(
  items: readonly CatalogItem[],
  categories: readonly Category[],
  mobs: readonly Mob[],
): {itemIcons: number; categoryIcons: number; mobSprites: number} {
  return {
    itemIcons: items.filter(item => hasOwnVisualReference(item, 'icon')).length,
    categoryIcons: categories.filter(category => hasOwnVisualReference(category, 'icon')).length,
    mobSprites: mobs.filter(mob => hasOwnVisualReference(mob, 'icon')).length,
  };
}

export function recipeVisualReferenceIndices(recipes: readonly Recipe[]): number[] {
  return recipes.flatMap((recipe, index) =>
    hasOwnVisualReference(recipe, 'img') ? [index] : [],
  );
}
