export const PROJECTE_EMC_CATEGORY_ID = 'projecte:emc_transmutation';
export const MEATBALLCRAFT_01864_PACK_VERSION = 'prerelease-0.18.6.4';
export const MEATBALLCRAFT_01864_RECIPE_COUNT = 376179;
export const MEATBALLCRAFT_01864_EMC_RECIPE_COUNT = 11106;

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isSingleIngredientGroup(value, expectedKeyPrefix, expectedQuantity = null) {
  if (!Array.isArray(value) || value.length !== 1) return false;
  const group = value[0];
  if (!Array.isArray(group) || group.length !== 1) return false;
  const ingredient = group[0];
  if (!Array.isArray(ingredient) || ingredient.length < 2) return false;
  if (typeof ingredient[0] !== 'string' || !ingredient[0].startsWith(expectedKeyPrefix)) {
    return false;
  }
  if (!Number.isFinite(ingredient[1]) || ingredient[1] <= 0) return false;
  return expectedQuantity === null || ingredient[1] === expectedQuantity;
}

export function isIntentionalProjecteEmcRecipe(recipe, categoryId) {
  return (
    categoryId === PROJECTE_EMC_CATEGORY_ID &&
    isRecord(recipe) &&
    typeof recipe.id === 'string' &&
    recipe.id.startsWith('projecte:emc/') &&
    recipe.img === undefined &&
    recipe.w === undefined &&
    recipe.h === undefined &&
    isSingleIngredientGroup(recipe.in, 'emc|projecte:emc') &&
    isSingleIngredientGroup(recipe.out, 'item|', 1)
  );
}

export function expectedIntentionalPreviewlessRecipes(profile, manifest) {
  return profile === 'meatballcraft-1.12.2' &&
    manifest?.pack?.name === 'MeatballCraft' &&
    manifest?.pack?.version === MEATBALLCRAFT_01864_PACK_VERSION &&
    manifest?.counts?.recipes === MEATBALLCRAFT_01864_RECIPE_COUNT
    ? MEATBALLCRAFT_01864_EMC_RECIPE_COUNT
    : 0;
}
