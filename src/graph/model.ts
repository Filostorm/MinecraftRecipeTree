import {DropStat, Mob, Recipe, RecipeRef} from '../types';

/**
 * The flowchart is a tree rooted at the item being crafted, growing downward.
 * An expanded item is drawn as a single "source" node (recipe image / mob / block
 * with the item name + required amount in its header) — no separate item node —
 * which keeps the chart compact. Collapsed items stay small item nodes.
 */
export type SourceKind = 'recipe' | 'mob' | 'block';

export interface SourceTreeNode {
  id: string;
  kind: SourceKind;
  /** recipe source */
  ref?: RecipeRef;
  recipe?: Recipe;
  dir?: string;
  catTitle?: string;
  /** mob-drop source */
  mob?: Mob;
  /** block-mining source */
  blockKey?: string;
  /** drop odds for mob/block sources */
  stat?: DropStat;
  /** ingredient children (recipe sources only) */
  inputs: ItemTreeNode[];
}

export interface ItemTreeNode {
  id: string;
  /** Catalog key */
  key: string;
  /** Total amount required by the parent recipe (summed over merged slots) */
  /** null means the selected recipe did not export a usable quantity. */
  amount?: number | null;
  /** Number of interchangeable variants in the parent slot (tags) */
  variantCount?: number;
  /** Resolved members of a logical ingredient tag. */
  alternatives?: string[];
  /** Canonical tag id reconstructed from an unambiguous variant family. */
  tag?: string;
  /** Required by the parent source but retained after the recipe runs. */
  nonConsumed?: boolean;
  /** Keys of item ancestors, for cycle detection */
  ancestors: string[];
  /** This item already appears up the chain */
  cyclic?: boolean;
  loading?: boolean;
  /** The chosen way to obtain this item; set = expanded */
  source?: SourceTreeNode;
}

export function makeRoot(key: string): ItemTreeNode {
  return {id: 'root', key, ancestors: []};
}
