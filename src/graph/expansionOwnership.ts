import type {GraphDirection} from './direction';
import type {
  DeferredRecipeExpansion,
  ItemTreeNode,
  SourceTreeNode,
} from './model';

export function recipeExpansionIdentity(
  itemKey: string,
  direction: GraphDirection,
  expansion: Pick<DeferredRecipeExpansion, 'ref'>,
): string {
  return `${direction}:${itemKey}:${expansion.ref[0]}:${expansion.ref[1]}`;
}

export function recipeExpansionFromSource(
  source: SourceTreeNode | undefined,
): DeferredRecipeExpansion | null {
  if (source?.kind !== 'recipe' || !source.ref) return null;
  return {
    ref: [...source.ref],
    ...(source.allowFluidTransfer ? {allowFluidTransfer: true as const} : {}),
    ...(source.ingredientSelections
      ? {ingredientSelections: {...source.ingredientSelections}}
      : {}),
  };
}

export function findRecipeExpansionOwner(
  root: ItemTreeNode | null,
  itemKey: string,
  direction: GraphDirection,
  expansion: Pick<DeferredRecipeExpansion, 'ref'>,
  excluded?: ItemTreeNode,
): ItemTreeNode | null {
  const targetIdentity = recipeExpansionIdentity(itemKey, direction, expansion);
  const stack = root ? [root] : [];
  while (stack.length > 0) {
    const node = stack.pop()!;
    if (node !== excluded) {
      const currentExpansion = recipeExpansionFromSource(node.source);
      if (
        currentExpansion &&
        recipeExpansionIdentity(node.key, direction, currentExpansion) === targetIdentity
      ) {
        return node;
      }
    }
    const children = node.source?.inputs ?? [];
    for (let index = children.length - 1; index >= 0; index -= 1) {
      stack.push(children[index]);
    }
  }
  return null;
}

export interface DuplicateRecipeExpansion {
  node: ItemTreeNode;
  expansion: DeferredRecipeExpansion;
}

/**
 * Finds later pre-order occurrences of an expanded recipe. Descendants of a
 * duplicate are skipped because collapsing that duplicate removes the entire
 * descendant subtree from the visible graph.
 */
export function duplicateRecipeExpansions(
  root: ItemTreeNode | null,
  direction: GraphDirection,
): DuplicateRecipeExpansion[] {
  const duplicates: DuplicateRecipeExpansion[] = [];
  const seen = new Set<string>();
  const stack = root ? [root] : [];
  while (stack.length > 0) {
    const node = stack.pop()!;
    const expansion = recipeExpansionFromSource(node.source);
    if (expansion) {
      const identity = recipeExpansionIdentity(node.key, direction, expansion);
      if (seen.has(identity)) {
        duplicates.push({node, expansion});
        continue;
      }
      seen.add(identity);
    }
    const children = node.source?.inputs ?? [];
    for (let index = children.length - 1; index >= 0; index -= 1) {
      stack.push(children[index]);
    }
  }
  return duplicates;
}

export function deferredRecipeExpansionNodes(root: ItemTreeNode | null): ItemTreeNode[] {
  const deferred: ItemTreeNode[] = [];
  const stack = root ? [root] : [];
  while (stack.length > 0) {
    const node = stack.pop()!;
    if (node.deferredRecipeExpansion) deferred.push(node);
    const children = node.source?.inputs ?? [];
    for (let index = children.length - 1; index >= 0; index -= 1) {
      stack.push(children[index]);
    }
  }
  return deferred;
}
