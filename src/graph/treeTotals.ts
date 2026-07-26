import {slotSummary} from '../data/slotSummary.ts';
import type {ByproductAllocation, ItemTreeNode} from './model.ts';

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

export interface NodeByproductCoverage {
  nodeId: string;
  key: string;
  requiredAmount: number;
  creditedAmount: number;
  remainingAmount: number;
  allocations: ByproductAllocation[];
}

export interface TreeCalculation extends TreeTotals {
  requiredByNode: Map<string, number | null>;
  byproductCoverageByNode: Map<string, NodeByproductCoverage>;
}

const warnedMissingTreeYields = new Set<string>();
const warnedStochasticTreeYields = new Set<string>();
const warnedUnknownByproductBalances = new Set<string>();
const warnedStochasticByproducts = new Set<string>();
const warnedStochasticInputConsumption = new Set<string>();

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

interface InputBalance {
  nodeId: string;
  key: string;
  tag?: string;
  variants: number;
  requiredAmount: number;
  remainingAmount: number;
}

interface ProducerBalance {
  sourceId: string;
  logicalKey: string;
  remainingAmount: number | null;
}

interface CommittedCredit {
  node: ItemTreeNode;
  requiredAmount: number;
  intendedAmount: number;
  allocations: ByproductAllocation[];
}

function subtractTotal(
  target: Map<string, TreeTotal>,
  logicalKey: string,
  amount: number,
): void {
  const total = target.get(logicalKey);
  if (!total || total.amount == null || total.amount < amount) {
    throw new Error(
      `Material balance for ${logicalKey} cannot subtract ${amount} from ${String(total?.amount)}.`,
    );
  }
  total.amount -= amount;
}

function consumeProducerBalance(
  producers: ProducerBalance[],
  logicalKey: string,
  requestedAmount: number,
  preferredSourceId?: string,
): {consumed: number; allocations: ByproductAllocation[]} {
  let remaining = requestedAmount;
  const allocations: ByproductAllocation[] = [];
  const ordered = preferredSourceId
    ? [
        ...producers.filter(
          producer =>
            producer.logicalKey === logicalKey && producer.sourceId === preferredSourceId,
        ),
        ...producers.filter(
          producer =>
            producer.logicalKey === logicalKey && producer.sourceId !== preferredSourceId,
        ),
      ]
    : producers.filter(producer => producer.logicalKey === logicalKey);

  for (const producer of ordered) {
    if (remaining <= 0 || producer.remainingAmount == null || producer.remainingAmount <= 0) {
      continue;
    }
    const consumed = Math.min(remaining, producer.remainingAmount);
    producer.remainingAmount -= consumed;
    remaining -= consumed;
    allocations.push({producerSourceId: producer.sourceId, amount: consumed});
  }
  return {consumed: requestedAmount - remaining, allocations};
}

function mergeCoverage(
  target: Map<string, NodeByproductCoverage>,
  balance: {
    nodeId: string;
    key: string;
    requiredAmount: number;
    creditedAmount: number;
    allocations: ByproductAllocation[];
  },
): void {
  const current = target.get(balance.nodeId);
  const creditedAmount = (current?.creditedAmount ?? 0) + balance.creditedAmount;
  target.set(balance.nodeId, {
    nodeId: balance.nodeId,
    key: balance.key,
    requiredAmount: current?.requiredAmount ?? balance.requiredAmount,
    creditedAmount,
    remainingAmount: Math.max(
      0,
      (current?.requiredAmount ?? balance.requiredAmount) - creditedAmount,
    ),
    allocations: [...(current?.allocations ?? []), ...balance.allocations],
  });
}

function applyByproductCredits(
  inputs: Map<string, TreeTotal>,
  byproducts: Map<string, TreeTotal>,
  inputBalances: InputBalance[],
  producerBalances: ProducerBalance[],
  committedCredits: CommittedCredit[],
): {
  credits: Map<string, TreeTotal>;
  coverageByNode: Map<string, NodeByproductCoverage>;
} {
  const credits = new Map<string, TreeTotal>();
  const coverageByNode = new Map<string, NodeByproductCoverage>();

  for (const commitment of committedCredits) {
    const logicalKey = treeTotalIdentity(commitment.node);
    const byproduct = byproducts.get(logicalKey);
    let consumed = 0;
    const allocations: ByproductAllocation[] = [];
    if (byproduct?.amount != null) {
      for (const intendedAllocation of commitment.allocations) {
        const result = consumeProducerBalance(
          producerBalances,
          logicalKey,
          intendedAllocation.amount,
          intendedAllocation.producerSourceId,
        );
        consumed += result.consumed;
        allocations.push(...result.allocations);
      }
      const unallocated = commitment.intendedAmount - consumed;
      if (unallocated > 0) {
        const result = consumeProducerBalance(producerBalances, logicalKey, unallocated);
        consumed += result.consumed;
        allocations.push(...result.allocations);
      }
    }

    if (consumed > 0) {
      subtractTotal(byproducts, logicalKey, consumed);
      addTotal(
        credits,
        commitment.node.key,
        consumed,
        commitment.node.variantCount ?? 1,
        commitment.node.tag,
      );
      mergeCoverage(coverageByNode, {
        nodeId: commitment.node.id,
        key: commitment.node.key,
        requiredAmount: commitment.requiredAmount,
        creditedAmount: consumed,
        allocations,
      });
    }

    const missing = commitment.intendedAmount - consumed;
    if (missing > 0) {
      console.error(
        'A committed byproduct fulfillment lost some of its producing output; the missing amount was restored as an external input.',
        {
          nodeId: commitment.node.id,
          logicalIngredient: logicalKey,
          committedAmount: commitment.intendedAmount,
          availableAmount: consumed,
        },
      );
      addTotal(
        inputs,
        commitment.node.key,
        missing,
        commitment.node.variantCount ?? 1,
        commitment.node.tag,
      );
      inputBalances.push({
        nodeId: commitment.node.id,
        key: commitment.node.key,
        tag: commitment.node.tag,
        variants: commitment.node.variantCount ?? 1,
        requiredAmount: missing,
        remainingAmount: missing,
      });
    }
  }

  for (const inputBalance of inputBalances) {
    const logicalKey = treeTotalIdentity(inputBalance);
    const input = inputs.get(logicalKey);
    const byproduct = byproducts.get(logicalKey);
    if (!byproduct) continue;
    if (input?.amount == null || byproduct.amount == null) {
      if (!warnedUnknownByproductBalances.has(logicalKey)) {
        warnedUnknownByproductBalances.add(logicalKey);
        console.warn('Byproduct credit was not applied because its material balance is unknown.', {
          logicalIngredient: logicalKey,
          inputAmount: input?.amount,
          byproductAmount: byproduct.amount,
        });
      }
      continue;
    }
    const creditedAmount = Math.min(inputBalance.remainingAmount, byproduct.amount);
    if (creditedAmount <= 0) continue;
    const result = consumeProducerBalance(producerBalances, logicalKey, creditedAmount);
    if (result.consumed !== creditedAmount) {
      throw new Error(
        `Byproduct producers for ${logicalKey} exposed ${result.consumed}; expected ${creditedAmount}.`,
      );
    }
    subtractTotal(inputs, logicalKey, creditedAmount);
    subtractTotal(byproducts, logicalKey, creditedAmount);
    inputBalance.remainingAmount -= creditedAmount;
    addTotal(
      credits,
      inputBalance.key,
      creditedAmount,
      Math.max(inputBalance.variants, byproduct.variants),
      inputBalance.tag,
    );
    mergeCoverage(coverageByNode, {
      nodeId: inputBalance.nodeId,
      key: inputBalance.key,
      requiredAmount: inputBalance.requiredAmount,
      creditedAmount,
      allocations: result.allocations,
    });
  }
  return {credits, coverageByNode};
}

function effectiveNodeRequirement(
  node: ItemTreeNode,
  required: number | null,
): number | null {
  if (!node.nonConsumed || !node.key.startsWith('item|')) return required;
  return required === 0 ? 0 : 1;
}

/**
 * Calculate the graph's material balance.
 *
 * Consumed leaf inputs are summed. Retained item prerequisites are normalized
 * to one reusable item per logical ingredient, while other retained resources
 * use their maximum simultaneous requirement. Optional byproduct credits reduce
 * only consumed inputs with the same exact logical ingredient identity.
 */
export function calculateTreeTotals(
  root: ItemTreeNode,
  useByproducts = false,
): TreeCalculation {
  const inputs = new Map<string, TreeTotal>();
  const prerequisites = new Map<string, TreeTotal>();
  const byproducts = new Map<string, TreeTotal>();
  const requiredByNode = new Map<string, number | null>();
  const inputBalances: InputBalance[] = [];
  const producerBalances: ProducerBalance[] = [];
  const committedCredits: CommittedCredit[] = [];

  type VisitFrame =
    | {
        phase: 'enter';
        node: ItemTreeNode;
        required: number | null;
        grossRequired: number | null;
      }
    | {
        phase: 'exit';
        source: NonNullable<ItemTreeNode['source']>;
        outputs: ReturnType<typeof slotSummary>;
        selectedOutput: ReturnType<typeof slotSummary>[number] | undefined;
        runs: number | null;
      };
  const stack: VisitFrame[] = [
    {
      phase: 'enter',
      node: root,
      required: root.amount === undefined ? 1 : root.amount,
      grossRequired: root.amount === undefined ? 1 : root.amount,
    },
  ];

  while (stack.length > 0) {
    const frame = stack.pop()!;
    if (frame.phase === 'exit') {
      for (const output of frame.outputs) {
        if (output === frame.selectedOutput) continue;
        const stochastic = output.probability !== undefined;
        if (stochastic) {
          const warningKey =
            `${frame.source.ref?.[0] ?? 'unknown'}:` +
            `${frame.source.ref?.[1] ?? 'unknown'}:${output.key}`;
          if (!warnedStochasticByproducts.has(warningKey)) {
            warnedStochasticByproducts.add(warningKey);
            console.warn(
              'Stochastic byproduct credits are disabled because a guaranteed material balance cannot be derived.',
              {
                recipe: frame.source.ref,
                itemKey: output.key,
                probability: output.probability,
              },
            );
          }
        }
        const amount =
          stochastic || output.amount == null || frame.runs == null
            ? null
            : output.amount * frame.runs;
        addTotal(
          byproducts,
          output.key,
          amount,
          output.variants,
          output.tag,
        );
        producerBalances.push({
          sourceId: frame.source.id,
          logicalKey: treeTotalIdentity(output),
          remainingAmount: amount,
        });
      }
      continue;
    }

    const {node} = frame;
    const required = effectiveNodeRequirement(node, frame.required);
    const grossRequired = effectiveNodeRequirement(node, frame.grossRequired);
    requiredByNode.set(node.id, required);
    if (
      useByproducts &&
      node.byproductFulfillment &&
      grossRequired != null &&
      node.byproductFulfillment.creditedAmount > 0
    ) {
      committedCredits.push({
        node,
        requiredAmount: grossRequired,
        intendedAmount: Math.min(
          grossRequired,
          node.byproductFulfillment.creditedAmount,
        ),
        allocations: node.byproductFulfillment.allocations,
      });
    }
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
        if (required != null && required > 0) {
          inputBalances.push({
            nodeId: node.id,
            key: node.key,
            tag: node.tag,
            variants: node.variantCount ?? 1,
            requiredAmount: required,
            remainingAmount: required,
          });
        }
      }
      continue;
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
    } else if (selectedOutput.probability !== undefined) {
      const warningKey = `${source.ref?.[0] ?? 'unknown'}:${source.ref?.[1] ?? 'unknown'}:${node.key}`;
      if (!warnedStochasticTreeYields.has(warningKey)) {
        warnedStochasticTreeYields.add(warningKey);
        console.warn(
          'Tree totals cannot derive a guaranteed recipe count from a stochastic selected output; quantitative totals are intentionally unknown.',
          {
            recipe: source.ref,
            itemKey: node.key,
            probability: selectedOutput.probability,
          },
        );
      }
      outputYield = null;
    } else {
      outputYield = selectedOutput.amount;
    }

    const runs =
      required == null || outputYield == null
        ? null
        : node.key.startsWith('item|')
          ? Math.ceil(required / outputYield)
          : required / outputYield;

    stack.push({phase: 'exit', source, outputs, selectedOutput, runs});
    for (let index = source.inputs.length - 1; index >= 0; index -= 1) {
      const child = source.inputs[index];
      const stochasticConsumption =
        !child.nonConsumed && child.consumptionProbability !== undefined;
      if (stochasticConsumption) {
        const warningKey =
          `${source.ref?.[0] ?? 'unknown'}:${source.ref?.[1] ?? 'unknown'}:${child.key}`;
        if (!warnedStochasticInputConsumption.has(warningKey)) {
          warnedStochasticInputConsumption.add(warningKey);
          console.warn(
            'Tree totals cannot derive guaranteed material consumption from a stochastic input; quantitative consumption is intentionally unknown.',
            {
              recipe: source.ref,
              itemKey: child.key,
              probability: child.consumptionProbability,
            },
          );
        }
      }
      const grossChildRequired =
        stochasticConsumption || child.amount == null || runs == null
          ? null
          : child.nonConsumed
            ? child.amount
            : child.amount * runs;
      const committedAmount =
        useByproducts &&
        !child.nonConsumed &&
        grossChildRequired != null &&
        child.byproductFulfillment
          ? Math.min(grossChildRequired, child.byproductFulfillment.creditedAmount)
          : 0;
      stack.push({
        phase: 'enter',
        node: child,
        grossRequired: grossChildRequired,
        required:
          grossChildRequired == null ? null : grossChildRequired - committedAmount,
      });
    }
  }

  const creditResult = useByproducts
    ? applyByproductCredits(
        inputs,
        byproducts,
        inputBalances,
        producerBalances,
        committedCredits,
      )
    : {
        credits: new Map<string, TreeTotal>(),
        coverageByNode: new Map<string, NodeByproductCoverage>(),
      };
  const nonZero = (total: TreeTotal) => total.amount == null || total.amount > 0;

  return {
    inputs: [...inputs.values()].filter(nonZero),
    prerequisites: [...prerequisites.values()].filter(nonZero),
    byproductCredits: [...creditResult.credits.values()].filter(nonZero),
    byproducts: [...byproducts.values()].filter(nonZero),
    requiredByNode,
    byproductCoverageByNode: creditResult.coverageByNode,
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
