import {slotSummary} from '../data/slotSummary.ts';
import type {ItemTreeNode} from './model';

export interface TreeTotal {
  key: string;
  amount: number | null;
  variants: number;
  tag?: string;
}

export interface TreeTotals {
  inputs: TreeTotal[];
  prerequisites: TreeTotal[];
  byproductCredits: TreeTotal[];
  byproducts: TreeTotal[];
}

export interface TreeCalculation extends TreeTotals {
  requiredByNode: Map<string, number | null>;
}

const warnedMissingTreeYields = new Set<string>();
const warnedUnknownByproductBalances = new Set<string>();

export function treeTotalIdentity(total: Pick<TreeTotal, 'key' | 'tag'>): string {
  return total.tag ? `#${total.tag}` : total.key;
}

function addTotal(
  target: Map<string, TreeTotal>,
  key: string,
  amount: number | null,
  variants = 1,
  tag?: string,
  aggregate: 'sum' | 'max' = 'sum',
) {
  const logicalKey = treeTotalIdentity({key, tag});
  const current = target.get(logicalKey) ?? {
    key,
    amount: amount == null ? null : 0,
    variants: 1,
    tag,
  };
  if (current.amount != null) {
    current.amount =
      amount == null
        ? null
        : aggregate === 'max'
          ? Math.max(current.amount, amount)
          : current.amount + amount;
  }
  current.variants = Math.max(current.variants, variants);
  target.set(logicalKey, current);
}

function applyByproductCredits(
  inputs: Map<string, TreeTotal>,
  byproducts: Map<string, TreeTotal>,
): Map<string, TreeTotal> {
  const credits = new Map<string, TreeTotal>();
  for (const [logicalKey, input] of inputs) {
    const byproduct = byproducts.get(logicalKey);
    if (!byproduct) continue;
    if (input.amount == null || byproduct.amount == null) {
      if (!warnedUnknownByproductBalances.has(logicalKey)) {
        warnedUnknownByproductBalances.add(logicalKey);
        console.warn('Byproduct credit was not applied because its material balance is unknown.', {
          logicalIngredient: logicalKey,
          inputAmount: input.amount,
          byproductAmount: byproduct.amount,
        });
      }
      continue;
    }
    const creditedAmount = Math.min(input.amount, byproduct.amount);
    if (creditedAmount <= 0) continue;
    input.amount -= creditedAmount;
    byproduct.amount -= creditedAmount;
    credits.set(logicalKey, {
      key: input.key,
      amount: creditedAmount,
      variants: Math.max(input.variants, byproduct.variants),
      tag: input.tag,
    });
  }
  return credits;
}

/**
 * Calculate the graph's material balance.
 *
 * Consumed leaf inputs are summed, retained prerequisites use their maximum
 * simultaneous requirement, and optional byproduct credits reduce only
 * consumed inputs with the same exact logical ingredient identity.
 */
export function calculateTreeTotals(
  root: ItemTreeNode,
  useByproducts = false,
): TreeCalculation {
  const inputs = new Map<string, TreeTotal>();
  const prerequisites = new Map<string, TreeTotal>();
  const byproducts = new Map<string, TreeTotal>();
  const requiredByNode = new Map<string, number | null>();

  const visit = (node: ItemTreeNode, required: number | null) => {
    requiredByNode.set(node.id, required);
    if (node.nonConsumed) {
      addTotal(
        prerequisites,
        node.key,
        required,
        node.variantCount ?? 1,
        node.tag,
        'max',
      );
    }

    const source = node.source;
    if (!source || source.kind !== 'recipe' || !source.recipe || node.cyclic) {
      if (!node.nonConsumed) {
        addTotal(inputs, node.key, required, node.variantCount ?? 1, node.tag);
      }
      return;
    }

    const outputs = slotSummary(source.recipe.out);
    const selectedOutput = outputs.find(
      output => output.key === node.key || output.alternatives.includes(node.key),
    );
    let outputYield: number | null;
    if (!selectedOutput) {
      const warningKey = `${source.ref?.[0] ?? 'unknown'}:${source.ref?.[1] ?? 'unknown'}:${node.key}`;
      if (!warnedMissingTreeYields.has(warningKey)) {
        warnedMissingTreeYields.add(warningKey);
        console.warn('Tree totals could not identify the selected item output; assuming a yield of one.', {
          recipe: source.ref,
          itemKey: node.key,
        });
      }
      outputYield = 1;
    } else {
      outputYield = selectedOutput.amount;
    }

    const runs =
      required == null || outputYield == null
        ? null
        : node.key.startsWith('item|')
          ? Math.ceil(required / outputYield)
          : required / outputYield;

    for (const child of source.inputs) {
      visit(
        child,
        child.amount == null || runs == null
          ? null
          : child.nonConsumed
            ? child.amount
            : child.amount * runs,
      );
    }

    for (const output of outputs) {
      if (output === selectedOutput) continue;
      addTotal(
        byproducts,
        output.key,
        output.amount == null || runs == null ? null : output.amount * runs,
        output.variants,
        output.tag,
      );
    }
  };

  visit(root, root.amount === undefined ? 1 : root.amount);
  const credits = useByproducts ? applyByproductCredits(inputs, byproducts) : new Map();
  const nonZero = (total: TreeTotal) => total.amount == null || total.amount > 0;

  return {
    inputs: [...inputs.values()].filter(nonZero),
    prerequisites: [...prerequisites.values()].filter(nonZero),
    byproductCredits: [...credits.values()].filter(nonZero),
    byproducts: [...byproducts.values()].filter(nonZero),
    requiredByNode,
  };
}

export function requiredAmountFor(
  node: ItemTreeNode,
  calculation: TreeCalculation,
): number | null {
  if (calculation.requiredByNode.has(node.id)) {
    return calculation.requiredByNode.get(node.id) ?? null;
  }
  return node.amount === undefined ? 1 : node.amount;
}
