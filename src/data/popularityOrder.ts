import type {CatalogItem} from '../types';

/** O(catalog + top ranking), not a sort of every item after each view. */
export function popularityOrder(items: readonly CatalogItem[], keys: readonly string[]): CatalogItem[] {
  if (!keys.length) return [...items];
  const lookup = new Map(items.map(item => [item.k, item]));
  const seen = new Set<string>();
  const result: CatalogItem[] = [];
  for (const key of keys) {
    const item = lookup.get(key);
    if (item && !seen.has(key)) {result.push(item); seen.add(key);}
  }
  for (const item of items) if (!seen.has(item.k)) result.push(item);
  return result;
}
