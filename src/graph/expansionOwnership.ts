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

export type DeferredRecipeSourceResolver = (
  node: ItemTreeNode,
) => SourceTreeNode | undefined;

/**
 * Resolve a deferred expand-once occurrence to the visible recipe subtree that
 * owns the same expansion. Consumers can traverse that subtree virtually
 * without adding duplicate nodes to the rendered graph.
 */
export function createDeferredRecipeSourceResolver(
  root: ItemTreeNode | null,
  direction: GraphDirection,
): DeferredRecipeSourceResolver {
  const sourcesByIdentity = new Map<string, SourceTreeNode>();
  const stack = root ? [root] : [];
  while (stack.length > 0) {
    const node = stack.pop()!;
    const expansion = recipeExpansionFromSource(node.source);
    if (expansion && node.source) {
      const identity = recipeExpansionIdentity(node.key, direction, expansion);
      if (!sourcesByIdentity.has(identity)) {
        sourcesByIdentity.set(identity, node.source);
      }
    }
    const children = node.source?.inputs ?? [];
    for (let index = children.length - 1; index >= 0; index -= 1) {
      stack.push(children[index]);
    }
  }

  const reportedMissing = new Set<string>();
  return node => {
    const expansion = node.deferredRecipeExpansion;
    if (!expansion) return undefined;
    const identity = recipeExpansionIdentity(node.key, direction, expansion);
    const source = sourcesByIdentity.get(identity);
    if (!source && !reportedMissing.has(identity)) {
      reportedMissing.add(identity);
      console.error(
        'Tree totals could not resolve a deferred recipe to its expanded owner.',
        {
          nodeId: node.id,
          itemKey: node.key,
          recipeRef: expansion.ref,
          direction,
        },
      );
    }
    return source;
  };
}
