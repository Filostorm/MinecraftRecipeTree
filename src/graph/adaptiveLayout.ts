import type {
  EdgeRect,
  GraphLayout,
  LaidInputCluster,
  LaidNode,
} from './layout.ts';
import {
  COMPACT_ITEM_SIZE,
  ITEM_H,
  ITEM_W,
  sourceNodeSize,
} from './layout.ts';
import type {ItemTreeNode} from './model.ts';

const LEVEL_GAP = 48;
const SIBLING_GAP = 18;
const EDGE_T = 2;

export const PACKED_INPUT_THRESHOLD = 9;
export const PACKED_ITEM_SIZE = 44;
export const COMPACT_BRANCH_LABEL_WIDTH = 96;
const PACKED_ITEM_GAP = 8;
const PACKED_CLUSTER_PADDING = 12;
const ROOT_FIRST_DROP = 54;
const ROOT_ROW_GAP = 72;
const ROOT_ROW_SPLAY = 10;
const ROOT_GROUP_SIZE = 4;

export interface PackedFanMemberPosition {
  x: number;
  y: number;
  row: number;
}

export interface PackedFanPlan {
  w: number;
  h: number;
  hubX: number;
  hubY: number;
  rowYs: number[];
  members: PackedFanMemberPosition[];
}

/**
 * Plan a collision-safe, widening root bed for a compound ingredient node.
 *
 * Each successive row grows by two slots, producing a tapered root silhouette.
 * Rows are staggered vertically at their outer edges so the result reads as
 * spreading roots instead of a rectangular grid.
 */
export function planPackedInputFan(itemCount: number): PackedFanPlan {
  if (!Number.isSafeInteger(itemCount) || itemCount <= 0) {
    throw new Error('Packed input fans require a positive safe-integer item count.');
  }

  const axisClearance = PACKED_ITEM_SIZE + PACKED_ITEM_GAP;
  const rowCounts: number[] = [];
  let remaining = itemCount;
  let row = 0;
  while (remaining > 0) {
    const capacity = 3 + row * 2;
    const rowCount = Math.min(capacity, remaining);
    rowCounts.push(rowCount);
    remaining -= rowCount;
    row += 1;
  }

  const widestRow = Math.max(...rowCounts);
  const widestSpan = (widestRow - 1) * axisClearance;
  const w = widestSpan + PACKED_ITEM_SIZE + PACKED_CLUSTER_PADDING * 2;
  const hubX = w / 2;
  const hubY = 0;
  const rowYs: number[] = [];
  const members: PackedFanMemberPosition[] = [];
  let maxBottom = hubY;

  rowCounts.forEach((rowCount, rowIndex) => {
    const span = (rowCount - 1) * axisClearance;
    const rowBaseY = ROOT_FIRST_DROP + rowIndex * ROOT_ROW_GAP;
    rowYs.push(rowBaseY);
    for (let index = 0; index < rowCount; index += 1) {
      const centerOffset = -span / 2 + index * axisClearance;
      const normalizedDistance = span === 0 ? 0 : Math.abs(centerOffset) / (span / 2);
      const y = rowBaseY + Math.round(normalizedDistance * ROOT_ROW_SPLAY);
      const member = {
        x: hubX + centerOffset - PACKED_ITEM_SIZE / 2,
        y,
        row: rowIndex,
      };
      members.push(member);
      maxBottom = Math.max(maxBottom, member.y + PACKED_ITEM_SIZE);
    }
  });

  return {
    w,
    h: maxBottom + PACKED_CLUSTER_PADDING,
    hubX,
    hubY,
    rowYs,
    members,
  };
}

interface FanUnit {
  id: string;
  items: ItemTreeNode[];
  plan: PackedFanPlan;
}

interface LayoutUnit {
  id: string;
  kind: 'tree' | 'fan';
  item?: ItemTreeNode;
  fan?: FanUnit;
  parent: number;
  children: number[];
  siblingIndex: number;
  depth: number;
  w: number;
  h: number;
  prelim: number;
  modifier: number;
  shift: number;
  change: number;
  thread: number;
  ancestor: number;
  centerX: number;
  y: number;
}

function itemNodeSize(item: ItemTreeNode, compact: boolean): {w: number; h: number} {
  if (compact) {
    return {
      w: item.source ? COMPACT_BRANCH_LABEL_WIDTH : COMPACT_ITEM_SIZE,
      h: COMPACT_ITEM_SIZE,
    };
  }
  if (item.source) return sourceNodeSize(item.source);
  return {w: ITEM_W, h: ITEM_H};
}

function makeTreeUnit(
  item: ItemTreeNode,
  compact: boolean,
  parent: number,
  siblingIndex: number,
  depth: number,
): LayoutUnit {
  const size = itemNodeSize(item, compact);
  return {
    id: item.id,
    kind: 'tree',
    item,
    parent,
    children: [],
    siblingIndex,
    depth,
    w: size.w,
    h: size.h,
    prelim: 0,
    modifier: 0,
    shift: 0,
    change: 0,
    thread: -1,
    ancestor: -1,
    centerX: 0,
    y: 0,
  };
}

function makeFanUnit(
  parentSourceId: string,
  items: ItemTreeNode[],
  parent: number,
  siblingIndex: number,
  depth: number,
): LayoutUnit {
  const plan = planPackedInputFan(items.length);
  return {
    id: `${parentSourceId}.packed-inputs`,
    kind: 'fan',
    fan: {id: `${parentSourceId}.packed-inputs`, items, plan},
    parent,
    children: [],
    siblingIndex,
    depth,
    w: plan.w,
    h: plan.h,
    prelim: 0,
    modifier: 0,
    shift: 0,
    change: 0,
    thread: -1,
    ancestor: -1,
    centerX: 0,
    y: 0,
  };
}

function flattenAdaptiveTree(
  root: ItemTreeNode,
  compact: boolean,
): {units: LayoutUnit[]; rootIndex: number} {
  const units: LayoutUnit[] = [makeTreeUnit(root, compact, -1, 0, 0)];
  units[0].ancestor = 0;
  const expansionStack = [0];

  while (expansionStack.length > 0) {
    const unitIndex = expansionStack.pop()!;
    const unit = units[unitIndex];
    const source = unit.item?.source;
    if (!source || source.inputs.length === 0) continue;

    const collapsed: Array<{item: ItemTreeNode; originalIndex: number}> = [];
    const expanded: Array<{item: ItemTreeNode; originalIndex: number}> = [];
    source.inputs.forEach((item, originalIndex) => {
      (item.source ? expanded : collapsed).push({item, originalIndex});
    });

    const descriptors: Array<
      | {kind: 'tree'; item: ItemTreeNode; order: number}
      | {kind: 'fan'; items: ItemTreeNode[]; order: number}
    > = [];
    if (collapsed.length >= PACKED_INPUT_THRESHOLD) {
      for (const entry of expanded) {
        descriptors.push({kind: 'tree', item: entry.item, order: entry.originalIndex});
      }
      descriptors.push({
        kind: 'fan',
        items: collapsed.map(entry => entry.item),
        order: collapsed[Math.floor(collapsed.length / 2)].originalIndex,
      });
      descriptors.sort((left, right) => left.order - right.order);
    } else {
      for (const [originalIndex, item] of source.inputs.entries()) {
        descriptors.push({kind: 'tree', item, order: originalIndex});
      }
    }

    for (const [siblingIndex, descriptor] of descriptors.entries()) {
      const child =
        descriptor.kind === 'fan'
          ? makeFanUnit(
              source.id,
              descriptor.items,
              unitIndex,
              siblingIndex,
              unit.depth + 1,
            )
          : makeTreeUnit(
              descriptor.item,
              compact,
              unitIndex,
              siblingIndex,
              unit.depth + 1,
            );
      const childIndex = units.length;
      child.ancestor = childIndex;
      units.push(child);
      unit.children.push(childIndex);
    }

    for (let index = unit.children.length - 1; index >= 0; index -= 1) {
      const childIndex = unit.children[index];
      if (units[childIndex].kind === 'tree') expansionStack.push(childIndex);
    }
  }

  return {units, rootIndex: 0};
}

function leftSibling(units: LayoutUnit[], index: number): number {
  const unit = units[index];
  if (unit.parent < 0 || unit.siblingIndex === 0) return -1;
  return units[unit.parent].children[unit.siblingIndex - 1] ?? -1;
}

function leftmostSibling(units: LayoutUnit[], index: number): number {
  const parent = units[index].parent;
  if (parent < 0) return -1;
  return units[parent].children[0] ?? -1;
}

function nextLeft(units: LayoutUnit[], index: number): number {
  const unit = units[index];
  return unit.children[0] ?? unit.thread;
}

function nextRight(units: LayoutUnit[], index: number): number {
  const unit = units[index];
  return unit.children.at(-1) ?? unit.thread;
}

function centerSeparation(left: LayoutUnit, right: LayoutUnit): number {
  return left.w / 2 + SIBLING_GAP + right.w / 2;
}

function moveSubtree(
  units: LayoutUnit[],
  leftIndex: number,
  rightIndex: number,
  shift: number,
): void {
  const left = units[leftIndex];
  const right = units[rightIndex];
  const subtreeCount = right.siblingIndex - left.siblingIndex;
  if (subtreeCount <= 0) {
    throw new Error('Adaptive tree compaction encountered invalid sibling ordering.');
  }
  right.change -= shift / subtreeCount;
  right.shift += shift;
  left.change += shift / subtreeCount;
  right.prelim += shift;
  right.modifier += shift;
}

function executeShifts(units: LayoutUnit[], index: number): void {
  let shift = 0;
  let change = 0;
  const children = units[index].children;
  for (let childOffset = children.length - 1; childOffset >= 0; childOffset -= 1) {
    const child = units[children[childOffset]];
    child.prelim += shift;
    child.modifier += shift;
    change += child.change;
    shift += child.shift + change;
  }
}

function ancestor(
  units: LayoutUnit[],
  innerLeftIndex: number,
  currentIndex: number,
  defaultAncestor: number,
): number {
  const candidate = units[innerLeftIndex].ancestor;
  return candidate >= 0 && units[candidate].parent === units[currentIndex].parent
    ? candidate
    : defaultAncestor;
}

function apportion(
  units: LayoutUnit[],
  index: number,
  defaultAncestor: number,
): number {
  const sibling = leftSibling(units, index);
  if (sibling < 0) return defaultAncestor;

  let innerRight = index;
  let outerRight = index;
  let innerLeft = sibling;
  let outerLeft = leftmostSibling(units, index);
  let innerRightModifier = units[innerRight].modifier;
  let outerRightModifier = units[outerRight].modifier;
  let innerLeftModifier = units[innerLeft].modifier;
  let outerLeftModifier = units[outerLeft].modifier;

  while (nextRight(units, innerLeft) >= 0 && nextLeft(units, innerRight) >= 0) {
    innerLeft = nextRight(units, innerLeft);
    innerRight = nextLeft(units, innerRight);
    outerLeft = nextLeft(units, outerLeft);
    outerRight = nextRight(units, outerRight);
    if (outerLeft < 0 || outerRight < 0) {
      throw new Error('Adaptive tree compaction lost an outer contour thread.');
    }
    units[outerRight].ancestor = index;

    const shift =
      units[innerLeft].prelim +
      innerLeftModifier +
      units[innerLeft].w / 2 +
      SIBLING_GAP -
      (units[innerRight].prelim + innerRightModifier - units[innerRight].w / 2);
    if (shift > 0) {
      const shiftAncestor = ancestor(units, innerLeft, index, defaultAncestor);
      moveSubtree(units, shiftAncestor, index, shift);
      innerRightModifier += shift;
      outerRightModifier += shift;
    }
    innerLeftModifier += units[innerLeft].modifier;
    innerRightModifier += units[innerRight].modifier;
    outerLeftModifier += units[outerLeft].modifier;
    outerRightModifier += units[outerRight].modifier;
  }

  const innerLeftRight = nextRight(units, innerLeft);
  const outerRightRight = nextRight(units, outerRight);
  if (innerLeftRight >= 0 && outerRightRight < 0) {
    units[outerRight].thread = innerLeftRight;
    units[outerRight].modifier += innerLeftModifier - outerRightModifier;
  }

  const innerRightLeft = nextLeft(units, innerRight);
  const outerLeftLeft = nextLeft(units, outerLeft);
  if (innerRightLeft >= 0 && outerLeftLeft < 0) {
    units[outerLeft].thread = innerRightLeft;
    units[outerLeft].modifier += innerRightModifier - outerLeftModifier;
    defaultAncestor = index;
  }
  return defaultAncestor;
}

function calculateHorizontalPositions(units: LayoutUnit[], rootIndex: number): void {
  const postorder: number[] = [];
  const stack: Array<{index: number; visited: boolean}> = [
    {index: rootIndex, visited: false},
  ];
  while (stack.length > 0) {
    const frame = stack.pop()!;
    if (frame.visited) {
      postorder.push(frame.index);
      continue;
    }
    stack.push({index: frame.index, visited: true});
    const children = units[frame.index].children;
    for (let offset = children.length - 1; offset >= 0; offset -= 1) {
      stack.push({index: children[offset], visited: false});
    }
  }

  for (const index of postorder) {
    const unit = units[index];
    const sibling = leftSibling(units, index);
    if (unit.children.length === 0) {
      if (sibling >= 0) {
        unit.prelim =
          units[sibling].prelim + centerSeparation(units[sibling], unit);
      }
      continue;
    }

    let defaultAncestor = unit.children[0];
    for (const childIndex of unit.children) {
      defaultAncestor = apportion(units, childIndex, defaultAncestor);
    }
    executeShifts(units, index);
    const midpoint =
      (units[unit.children[0]].prelim + units[unit.children.at(-1)!].prelim) / 2;
    if (sibling >= 0) {
      unit.prelim =
        units[sibling].prelim + centerSeparation(units[sibling], unit);
      unit.modifier = unit.prelim - midpoint;
    } else {
      unit.prelim = midpoint;
    }
  }

  const positionStack: Array<{index: number; modifier: number}> = [
    {index: rootIndex, modifier: 0},
  ];
  while (positionStack.length > 0) {
    const {index, modifier} = positionStack.pop()!;
    const unit = units[index];
    unit.centerX = unit.prelim + modifier;
    for (let offset = unit.children.length - 1; offset >= 0; offset -= 1) {
      positionStack.push({
        index: unit.children[offset],
        modifier: modifier + unit.modifier,
      });
    }
  }
}

function calculateVerticalPositions(units: LayoutUnit[]): void {
  const rowHeights: number[] = [];
  for (const unit of units) {
    rowHeights[unit.depth] = Math.max(rowHeights[unit.depth] ?? 0, unit.h);
  }
  const rowTops = [0];
  for (let depth = 1; depth < rowHeights.length; depth += 1) {
    rowTops[depth] = rowTops[depth - 1] + rowHeights[depth - 1] + LEVEL_GAP;
  }
  for (const unit of units) unit.y = rowTops[unit.depth];
}

function addElbow(
  edges: EdgeRect[],
  childX: number,
  childY: number,
  parentX: number,
  parentY: number,
): void {
  const midY = Math.round((childY + parentY) / 2);
  edges.push({
    x: childX - EDGE_T / 2,
    y: Math.min(childY, midY),
    w: EDGE_T,
    h: Math.abs(midY - childY),
  });
  edges.push({
    x: Math.min(childX, parentX) - EDGE_T / 2,
    y: midY - EDGE_T / 2,
    w: Math.abs(parentX - childX) + EDGE_T,
    h: EDGE_T,
  });
  edges.push({
    x: parentX - EDGE_T / 2,
    y: Math.min(midY, parentY),
    w: EDGE_T,
    h: Math.abs(parentY - midY),
  });
}

function addBundledEdges(
  edges: EdgeRect[],
  parent: LayoutUnit,
  children: LayoutUnit[],
): void {
  if (children.length === 0) return;
  const parentX = parent.centerX;
  const parentY = parent.y + parent.h;
  const childY = children[0].y;
  if (children.some(child => child.y !== childY)) {
    throw new Error('Adaptive edge bundling requires child roots on one layout rank.');
  }
  if (children.length === 1) {
    addElbow(edges, children[0].centerX, childY, parentX, parentY);
    return;
  }

  const midY = Math.round((childY + parentY) / 2);
  let minimumChildX = Infinity;
  let maximumChildX = -Infinity;
  for (const child of children) {
    minimumChildX = Math.min(minimumChildX, child.centerX);
    maximumChildX = Math.max(maximumChildX, child.centerX);
  }
  edges.push({
    x: parentX - EDGE_T / 2,
    y: Math.min(parentY, midY),
    w: EDGE_T,
    h: Math.abs(midY - parentY),
  });
  edges.push({
    x: minimumChildX - EDGE_T / 2,
    y: midY - EDGE_T / 2,
    w: maximumChildX - minimumChildX + EDGE_T,
    h: EDGE_T,
  });
  for (const child of children) {
    edges.push({
      x: child.centerX - EDGE_T / 2,
      y: Math.min(childY, midY),
      w: EDGE_T,
      h: Math.abs(childY - midY),
    });
  }
}

function addRootSegment(
  edges: EdgeRect[],
  x: number,
  y: number,
  w: number,
  h: number,
  rootDepth: number,
): void {
  if (w <= 0 || h <= 0) return;
  edges.push({x, y, w, h, root: true, rootDepth});
}

/**
 * Route terminal inputs through shared shoulders and forks. The result is a
 * sparse root network: a central trunk, lateral arms, then short terminal
 * rootlets. It deliberately uses export-safe rectangles instead of a second
 * SVG/canvas renderer.
 */
function addFanRootEdges(edges: EdgeRect[], unit: LayoutUnit): void {
  const fan = unit.fan;
  if (!fan) throw new Error(`Adaptive fan unit ${unit.id} is missing its root plan.`);
  const left = unit.centerX - unit.w / 2;
  const hubX = left + fan.plan.hubX;
  const hubY = unit.y + fan.plan.hubY;
  const membersByRow: PackedFanMemberPosition[][] = Array.from(
    {length: fan.plan.rowYs.length},
    () => [],
  );
  fan.plan.members.forEach(member => {
    const members = membersByRow[member.row];
    if (!members) {
      throw new Error(`Adaptive fan ${fan.id} references missing root row ${member.row}.`);
    }
    members.push(member);
  });

  for (let row = 0; row < fan.plan.rowYs.length; row += 1) {
    const rowMembers = membersByRow[row];
    if (rowMembers.length === 0) {
      throw new Error(`Adaptive fan ${fan.id} has an empty planned root row ${row}.`);
    }
    const shoulderY = hubY + 14 + row * 7;
    const branchY =
      unit.y + Math.min(...rowMembers.map(member => member.y)) - 14;
    addRootSegment(
      edges,
      hubX - EDGE_T / 2,
      Math.min(hubY, shoulderY),
      EDGE_T,
      Math.abs(shoulderY - hubY),
      row,
    );

    for (let offset = 0; offset < rowMembers.length; offset += ROOT_GROUP_SIZE) {
      const group = rowMembers.slice(offset, offset + ROOT_GROUP_SIZE);
      const centers = group.map(
        member => left + member.x + PACKED_ITEM_SIZE / 2,
      );
      const groupX = centers.reduce((sum, value) => sum + value, 0) / centers.length;
      const minimumX = Math.min(...centers);
      const maximumX = Math.max(...centers);
      addRootSegment(
        edges,
        Math.min(hubX, groupX),
        shoulderY - EDGE_T / 2,
        Math.abs(groupX - hubX) + EDGE_T,
        EDGE_T,
        row,
      );
      addRootSegment(
        edges,
        groupX - EDGE_T / 2,
        Math.min(shoulderY, branchY),
        EDGE_T,
        Math.abs(branchY - shoulderY),
        row,
      );
      addRootSegment(
        edges,
        minimumX - EDGE_T / 2,
        branchY - EDGE_T / 2,
        maximumX - minimumX + EDGE_T,
        EDGE_T,
        row + 1,
      );
      group.forEach((member, memberIndex) => {
        const memberX = centers[memberIndex];
        const memberY = unit.y + member.y;
        addRootSegment(
          edges,
          memberX - EDGE_T / 2,
          Math.min(branchY, memberY),
          EDGE_T,
          Math.abs(memberY - branchY),
          row + 1,
        );
      });
    }
  }
}

/**
 * Experimental graph layout: linear-time Buchheim contour compaction plus
 * unboxed root networks for high-arity collapsed inputs.
 */
export function layoutAdaptiveTree(
  root: ItemTreeNode,
  compact = false,
): GraphLayout {
  const {units, rootIndex} = flattenAdaptiveTree(root, compact);
  calculateHorizontalPositions(units, rootIndex);
  calculateVerticalPositions(units);

  const nodes: LaidNode[] = [];
  const clusters: LaidInputCluster[] = [];
  const edges: EdgeRect[] = [];
  const traversal = [rootIndex];
  while (traversal.length > 0) {
    const index = traversal.pop()!;
    const unit = units[index];
    if (unit.kind === 'tree') {
      const item = unit.item;
      if (!item) throw new Error(`Adaptive tree unit ${unit.id} is missing its item.`);
      const visualSize =
        compact
          ? {w: COMPACT_ITEM_SIZE, h: COMPACT_ITEM_SIZE}
          : item.source
            ? sourceNodeSize(item.source)
            : {w: ITEM_W, h: ITEM_H};
      nodes.push({
        id: item.source?.id ?? item.id,
        kind: item.source ? 'source' : 'item',
        x: unit.centerX - visualSize.w / 2,
        y: unit.y,
        w: visualSize.w,
        h: visualSize.h,
        item,
        source: item.source,
        depth: unit.depth,
        compactBranch: compact && item.source !== undefined,
      });
      addBundledEdges(
        edges,
        unit,
        unit.children.map(childIndex => units[childIndex]),
      );
    } else {
      const fan = unit.fan;
      if (!fan) throw new Error(`Adaptive fan unit ${unit.id} is missing its plan.`);
      const x = unit.centerX - unit.w / 2;
      clusters.push({
        id: fan.id,
        x,
        y: unit.y,
        w: fan.plan.w,
        h: fan.plan.h,
        itemCount: fan.items.length,
        hubX: fan.plan.hubX,
        hubY: fan.plan.hubY,
      });
      addFanRootEdges(edges, unit);
      fan.items.forEach((item, itemIndex) => {
        const position = fan.plan.members[itemIndex];
        if (!position) {
          throw new Error(`Adaptive fan ${fan.id} is missing member position ${itemIndex}.`);
        }
        nodes.push({
          id: item.id,
          kind: 'item',
          x: x + position.x,
          y: unit.y + position.y,
          w: PACKED_ITEM_SIZE,
          h: PACKED_ITEM_SIZE,
          item,
          packed: true,
          depth: unit.depth,
        });
      });
    }
    for (let offset = unit.children.length - 1; offset >= 0; offset -= 1) {
      traversal.push(unit.children[offset]);
    }
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  const seeRect = ({x, y, w, h}: {x: number; y: number; w: number; h: number}) => {
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x + w);
    maxY = Math.max(maxY, y + h);
  };
  nodes.forEach(node => {
    seeRect(node);
    if (node.compactBranch) {
      seeRect({
        x: node.x - (COMPACT_BRANCH_LABEL_WIDTH - node.w) / 2,
        y: node.y,
        w: COMPACT_BRANCH_LABEL_WIDTH,
        h: node.h + 20,
      });
    }
  });
  clusters.forEach(seeRect);
  edges.forEach(seeRect);
  if (![minX, minY, maxX, maxY].every(Number.isFinite)) {
    throw new Error('Adaptive graph layout produced non-finite bounds.');
  }

  return {nodes, edges, clusters, minX, minY, maxX, maxY};
}
