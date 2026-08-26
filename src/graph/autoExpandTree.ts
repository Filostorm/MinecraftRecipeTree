import type {ItemTreeNode} from './model';

export interface AutoExpandProgress {
  appliedSourceCount: number;
  expandedRecipeCount: number;
}

export interface AutoExpandOptions {
  /** Stop cleanly between sources when the active Auto expand run is cancelled. */
  shouldContinue?: () => boolean;
  /** Number of attached sources between graph renders/browser yields. */
  batchSize?: number;
  onBatch?: (progress: AutoExpandProgress) => void | Promise<void>;
}

/**
 * Expands preferred sources depth-first and waits for every newly revealed
 * ingredient before returning. Existing branches are traversed but never
 * reported as work performed by this run.
 */
export async function autoExpandPreferredNodes<TChoice>(
  root: ItemTreeNode,
  preferredSourceFor: (node: ItemTreeNode) => TChoice | null,
  applyPreferredSource: (node: ItemTreeNode, choice: TChoice) => Promise<void>,
  options: AutoExpandOptions = {},
): Promise<ItemTreeNode[]> {
  const batchSize = options.batchSize ?? 1;
  if (!Number.isSafeInteger(batchSize) || batchSize < 1) {
    throw new Error('Auto expand batch size must be a positive integer.');
  }
  const expandedRecipes: ItemTreeNode[] = [];
  const stack = [root];
  let appliedSourceCount = 0;
  let pendingBatchSize = 0;

  const flushBatch = async () => {
    if (pendingBatchSize === 0 || !options.onBatch) return;
    pendingBatchSize = 0;
    await options.onBatch({
      appliedSourceCount,
      expandedRecipeCount: expandedRecipes.length,
    });
  };

  while (stack.length > 0) {
    if (options.shouldContinue && !options.shouldContinue()) break;
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
    appliedSourceCount += 1;
    pendingBatchSize += 1;

    // The callback attaches the source by mutation, which TypeScript cannot
    // infer after the source-less guard above.
    const attachedSource = node.source as ItemTreeNode['source'];
    if (!attachedSource) {
      if (pendingBatchSize >= batchSize) await flushBatch();
      continue;
    }
    if (attachedSource.kind === 'recipe') expandedRecipes.push(node);
    for (let index = attachedSource.inputs.length - 1; index >= 0; index -= 1) {
      stack.push(attachedSource.inputs[index]);
    }
    if (pendingBatchSize >= batchSize) await flushBatch();
  }

  await flushBatch();

  return expandedRecipes;
}
