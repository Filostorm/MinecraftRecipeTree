import type {ItemTreeNode} from './model';

/**
 * Expands preferred sources depth-first and waits for every newly revealed
 * ingredient before returning. Existing branches are traversed but never
 * reported as work performed by this run.
 */
export async function autoExpandPreferredNodes<TChoice>(
  root: ItemTreeNode,
  preferredSourceFor: (node: ItemTreeNode) => TChoice | null,
  applyPreferredSource: (node: ItemTreeNode, choice: TChoice) => Promise<void>,
): Promise<ItemTreeNode[]> {
  const expandedRecipes: ItemTreeNode[] = [];
  const stack = [root];

  while (stack.length > 0) {
    const node = stack.pop()!;
    if (node.source) {
      for (let index = node.source.inputs.length - 1; index >= 0; index -= 1) {
        stack.push(node.source.inputs[index]);
      }
      continue;
    }
    if (node.loading || node.cyclic || node.deferredRecipeExpansion) continue;

    const preferred = preferredSourceFor(node);
    if (!preferred) continue;
    await applyPreferredSource(node, preferred);

    // The callback attaches the source by mutation, which TypeScript cannot
    // infer after the source-less guard above.
    const attachedSource = node.source as ItemTreeNode['source'];
    if (!attachedSource) continue;
    if (attachedSource.kind === 'recipe') expandedRecipes.push(node);
    for (let index = attachedSource.inputs.length - 1; index >= 0; index -= 1) {
      stack.push(attachedSource.inputs[index]);
    }
  }

  return expandedRecipes;
}
