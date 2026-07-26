import type {Category, RecipeRef} from '../types';

export interface RecipeChoiceLike {
  ref: RecipeRef;
}

export interface PlannedRecipeGroup<T extends RecipeChoiceLike> {
  categoryIndex: number;
  groupKey: string;
  choices: T[];
}

export interface RecipePickerPlan<T extends RecipeChoiceLike> {
  initialChoices: T[];
  groups: PlannedRecipeGroup<T>[];
}

/** Packaging and recycling are valid sources, but usually poor graph-expansion defaults. */
export function isDeferredGraphRecipeCategory(category: Category | undefined): boolean {
  return /\b(?:packag(?:e|er|ing)|recycl(?:e|er|ing))\b/i.test(category?.title ?? '');
}

/**
 * Plans a bounded, category-complete initial source load.
 *
 * One representative from every recipe type is always selected first. Remaining
 * capacity is assigned to smaller, graph-relevant groups before high-volume or
 * packaging/recycling groups.
 */
export function planRecipePickerChoices<T extends RecipeChoiceLike>(
  choices: readonly T[],
  categories: readonly Category[],
  minimumInitialChoices: number,
): RecipePickerPlan<T> {
  const byCategory = new Map<number, T[]>();
  for (const choice of choices) {
    const categoryIndex = choice.ref[0];
    const group = byCategory.get(categoryIndex);
    if (group) group.push(choice);
    else byCategory.set(categoryIndex, [choice]);
  }

  const groups = [...byCategory.entries()]
    .map(([categoryIndex, categoryChoices]) => ({
      categoryIndex,
      groupKey: categories[categoryIndex]?.id ?? `recipe-category:${categoryIndex}`,
      choices: categoryChoices,
    }))
    .sort((a, b) => {
      const aCategory = categories[a.categoryIndex];
      const bCategory = categories[b.categoryIndex];
      const deferredDifference =
        Number(isDeferredGraphRecipeCategory(aCategory)) -
        Number(isDeferredGraphRecipeCategory(bCategory));
      if (deferredDifference !== 0) return deferredDifference;
      return (
        a.choices.length - b.choices.length ||
        (aCategory?.title ?? '').localeCompare(bCategory?.title ?? '') ||
        a.categoryIndex - b.categoryIndex
      );
    });

  const targetCount = Math.min(
    choices.length,
    Math.max(minimumInitialChoices, groups.length),
  );
  const initialChoices = groups.map(group => group.choices[0]);
  let remainingCapacity = targetCount - initialChoices.length;

  for (const group of groups) {
    if (remainingCapacity <= 0) break;
    const extraChoices = group.choices.slice(
      1,
      Math.min(group.choices.length, remainingCapacity + 1),
    );
    initialChoices.push(...extraChoices);
    remainingCapacity -= extraChoices.length;
  }

  return {initialChoices, groups};
}
