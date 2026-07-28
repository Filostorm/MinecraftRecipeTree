import type {ItemTreeNode} from './model';
import type {TreeTotal} from './treeTotals';

export type TreeTotalTargetKind = 'input' | 'prerequisite';

function sameLogicalIngredient(node: ItemTreeNode, total: TreeTotal): boolean {
  return node.key === total.key && node.tag === total.tag;
}

function contributesToTotal(
  node: ItemTreeNode,
  kind: TreeTotalTargetKind,
): boolean {
  if (kind === 'prerequisite') return node.nonConsumed === true;
  if (node.nonConsumed) return false;
  return (
    !node.source ||
    node.source.kind !== 'recipe' ||
    !node.source.recipe ||
    node.cyclic === true
  );
}

/** Resolve an aggregate totals row to the first graph node represented by it. */
export function findTreeTotalTarget(
  root: ItemTreeNode | null,
  total: TreeTotal,
  kind: TreeTotalTargetKind,
): ItemTreeNode | null {
  if (!root) return null;
  const traversal = [root];
  while (traversal.length > 0) {
    const node = traversal.pop()!;
    if (sameLogicalIngredient(node, total) && contributesToTotal(node, kind)) {
      return node;
    }
    const children = node.source?.inputs ?? [];
    for (let index = children.length - 1; index >= 0; index -= 1) {
      traversal.push(children[index]);
    }
  }
  return null;
}
