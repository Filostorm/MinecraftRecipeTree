import type {ItemTreeNode} from './model';

/**
 * Collect every eligible occurrence of an item before any source subtree is
 * replaced. Keeping collection separate from mutation prevents a parent
 * replacement from hiding matching nodes in sibling branches.
 */
export function preferredSourceTargets(
  root: ItemTreeNode | null,
  selected: ItemTreeNode,
): ItemTreeNode[] {
  const matches = new Set<ItemTreeNode>();
  if (!selected.cyclic && !selected.loading) matches.add(selected);

  const traversal = root ? [root] : [];
  while (traversal.length > 0) {
    const node = traversal.pop()!;
    if (node.key === selected.key && !node.loading && !node.cyclic) {
      matches.add(node);
    }
    const children = node.source?.inputs ?? [];
    for (let index = children.length - 1; index >= 0; index -= 1) {
      traversal.push(children[index]);
    }
  }
  return [...matches];
}
