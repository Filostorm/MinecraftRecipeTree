import type {RecipeIndex, RecipeRef} from '../types.ts';

/** Union recipe references for every concrete member of one logical ingredient. */
export function indexedRecipeRefs(
  index: RecipeIndex,
  key: string,
  equivalentKeys: readonly string[] | undefined,
  field: 'p' | 'u',
): RecipeRef[] {
  const refs: RecipeRef[] = [];
  const seen = new Set<string>();
  for (const lookupKey of new Set([key, ...(equivalentKeys ?? [])])) {
    for (const ref of index[lookupKey]?.[field] ?? []) {
      const identity = `${ref[0]}:${ref[1]}`;
      if (seen.has(identity)) continue;
      seen.add(identity);
      refs.push(ref);
    }
  }
  return refs;
}
