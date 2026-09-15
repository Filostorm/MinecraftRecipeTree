import type {ItemTreeNode} from './model';
import type {RecipeRef} from '../types';

/** Include every producing recipe and selected ingredient in the ancestry, not just slot numbers. */
export function childRequirementId(parent: ItemTreeNode, ref: RecipeRef, index: number, direction = 'inputs'): string {
  return `${parent.requirementId ?? 'root'}/${JSON.stringify([parent.key, ref, index, direction])}`;
}

export function resourceIdentity(node: Pick<ItemTreeNode, 'id' | 'key' | 'requirementId'>): string {
  return JSON.stringify([node.requirementId ?? node.id, node.key]);
}

export function resourceCompletionIdentity(node: ItemTreeNode, amount: number | null): string {
  return JSON.stringify([resourceIdentity(node), amount]);
}

/** Negative decisions must survive rebuilds just as positive ones do. */
export function catalystOverride(
  marks: ReadonlySet<string>,
  node: Pick<ItemTreeNode, 'id' | 'key' | 'requirementId'>,
): boolean | undefined {
  const identity = resourceIdentity(node);
  if (marks.has(identity)) return true;
  if (marks.has(`!${identity}`)) return false;
  return undefined;
}

export function resourceNodesById(root: ItemTreeNode | null): Map<string, ItemTreeNode> {
  const nodes = new Map<string, ItemTreeNode>();
  const stack = root ? [root] : [];
  while (stack.length) {
    const node = stack.pop()!;
    nodes.set(node.id, node);
    stack.push(...(node.source ?? node.collapsedSource)?.inputs ?? []);
  }
  return nodes;
}
