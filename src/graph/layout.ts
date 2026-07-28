import type {Recipe} from '../types.ts';
import {pixelArtDisplaySize} from '../data/pixelArtSizing.ts';
import type {ItemTreeNode, SourceTreeNode} from './model.ts';

export const ITEM_W = 172;
export const ITEM_H = 58;
export const COMPACT_ITEM_SIZE = 52;
export const COMPACT_ROOT_SIZE = 72;
/** Header strip on source nodes: icon + "Name ×N · Category". */
export const SOURCE_HEADER = 22;
/** Vertical gap between tree levels (rows). */
const LEVEL_GAP = 48;
/** Horizontal gap between siblings. */
const SIBLING_GAP = 18;
const EDGE_T = 2;

export interface LaidNode {
  id: string;
  kind: 'item' | 'source';
  x: number;
  y: number;
  w: number;
  h: number;
  /** The tree item (owner for both kinds). */
  item: ItemTreeNode;
  source?: SourceTreeNode;
  /** Collapsed radial-layout ingredient rendered as an upright icon. */
  radial?: boolean;
  /** Tree depth used to keep expanded compact-mode branch context legible. */
  depth?: number;
  /** Expanded source node that receives a persistent compact branch label. */
  compactBranch?: boolean;
}

export interface EdgeRect {
  x: number;
  y: number;
  w: number;
  h: number;
  /** Optional clockwise rotation in radians around the rectangle center. */
  angle?: number;
}

export interface GraphLayout {
  nodes: LaidNode[];
  edges: EdgeRect[];
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

export function recipeImageDisplay(recipe: Recipe): {w: number; h: number} {
  const w = recipe.w ?? 160;
  const h = recipe.h ?? 60;
  return pixelArtDisplaySize(w, h, 280, 220);
}

export function sourceNodeSize(source: SourceTreeNode): {w: number; h: number} {
  if (source.kind === 'recipe' && source.recipe) {
    const img = recipeImageDisplay(source.recipe);
    return {w: Math.max(180, img.w + 12), h: img.h + SOURCE_HEADER + 12};
  }
  if (source.kind === 'mob') {
    return {w: 210, h: SOURCE_HEADER + 66};
  }
  return {w: 210, h: SOURCE_HEADER + 44};
}

function treeNodeSize(
  node: ItemTreeNode,
  compact: boolean,
  isRoot = false,
): {w: number; h: number} {
  if (compact) {
    if (isRoot) {
      return {w: COMPACT_ROOT_SIZE, h: COMPACT_ROOT_SIZE};
    }
    return {w: COMPACT_ITEM_SIZE, h: COMPACT_ITEM_SIZE};
  }
  return node.source ? sourceNodeSize(node.source) : {w: ITEM_W, h: ITEM_H};
}

/**
 * Tidy top-down tree layout. Expanded items are drawn as a single source node in
 * their item's place; collapsed items are small item nodes. Descendant bands
 * remain disjoint, while immediate inputs anchor into a compact parent-centered
 * row whenever those bands allow it. Edges run child-top -> parent-bottom.
 */
export function layoutTree(root: ItemTreeNode, compact = false): GraphLayout {
  const nodes: LaidNode[] = [];
  const edges: EdgeRect[] = [];

  // Pass 1: row heights per depth.
  const rowH: number[] = [];
  const seeH = (d: number, h: number) => {
    rowH[d] = Math.max(rowH[d] ?? 0, h);
  };
  const rowStack: Array<{node: ItemTreeNode; depth: number}> = [{node: root, depth: 0}];
  while (rowStack.length > 0) {
    const {node, depth} = rowStack.pop()!;
    if (node.source) {
      seeH(depth, treeNodeSize(node, compact, depth === 0).h);
      for (let index = node.source.inputs.length - 1; index >= 0; index -= 1) {
        rowStack.push({node: node.source.inputs[index], depth: depth + 1});
      }
    } else {
      seeH(depth, treeNodeSize(node, compact, depth === 0).h);
    }
  }

  const rowTop: number[] = [0];
  for (let d = 1; d < rowH.length; d++) {
    rowTop[d] = rowTop[d - 1] + rowH[d - 1] + LEVEL_GAP;
  }

  // Pass 2: Buchheim's linear-time tidy-tree algorithm. Contour apportionment
  // separates only rows that actually overlap, so a wide descendant fan does
  // not create unnecessary space between its parent and leaf siblings.
  interface LayoutRecord {
    node: ItemTreeNode;
    size: {w: number; h: number};
    parent?: LayoutRecord;
    children: LayoutRecord[];
    index: number;
    depth: number;
    prelim: number;
    mod: number;
    shift: number;
    change: number;
    thread?: LayoutRecord;
    ancestor: LayoutRecord;
    defaultAncestor?: LayoutRecord;
    center: number;
  }

  const createRecord = (
    node: ItemTreeNode,
    parent: LayoutRecord | undefined,
    index: number,
  ): LayoutRecord => {
    const record = {
      node,
      size: treeNodeSize(node, compact, parent === undefined),
      parent,
      children: [],
      index,
      depth: parent ? parent.depth + 1 : 0,
      prelim: 0,
      mod: 0,
      shift: 0,
      change: 0,
      ancestor: undefined as unknown as LayoutRecord,
      center: 0,
    };
    record.ancestor = record;
    return record;
  };

  const rootRecord = createRecord(root, undefined, 0);
  const traversal: LayoutRecord[] = [];
  const flattenStack = [rootRecord];
  while (flattenStack.length > 0) {
    const record = flattenStack.pop()!;
    traversal.push(record);
    const inputs = record.node.source?.inputs ?? [];
    record.children = inputs.map((input, index) => createRecord(input, record, index));
    for (let index = record.children.length - 1; index >= 0; index -= 1) {
      flattenStack.push(record.children[index]);
    }
  }
  const postorder: LayoutRecord[] = [];
  const postorderStack: Array<{record: LayoutRecord; visited: boolean}> = [
    {record: rootRecord, visited: false},
  ];
  while (postorderStack.length > 0) {
    const frame = postorderStack.pop()!;
    if (frame.visited) {
      postorder.push(frame.record);
      continue;
    }
    postorderStack.push({record: frame.record, visited: true});
    for (let index = frame.record.children.length - 1; index >= 0; index -= 1) {
      postorderStack.push({record: frame.record.children[index], visited: false});
    }
  }

  const leftSibling = (record: LayoutRecord): LayoutRecord | undefined =>
    record.index > 0 ? record.parent?.children[record.index - 1] : undefined;
  const leftmostSibling = (record: LayoutRecord): LayoutRecord | undefined =>
    record.index > 0 ? record.parent?.children[0] : undefined;
  const nextLeft = (record: LayoutRecord): LayoutRecord | undefined =>
    record.children[0] ?? record.thread;
  const nextRight = (record: LayoutRecord): LayoutRecord | undefined =>
    record.children[record.children.length - 1] ?? record.thread;
  const separation = (left: LayoutRecord, right: LayoutRecord): number =>
    left.size.w / 2 + SIBLING_GAP + right.size.w / 2;

  const moveSubtree = (left: LayoutRecord, right: LayoutRecord, shift: number) => {
    const subtreeCount = right.index - left.index;
    if (subtreeCount <= 0) {
      throw new Error('Graph contour apportionment received an invalid sibling order.');
    }
    right.change -= shift / subtreeCount;
    right.shift += shift;
    left.change += shift / subtreeCount;
    right.prelim += shift;
    right.mod += shift;
  };

  const executeShifts = (record: LayoutRecord) => {
    let shift = 0;
    let change = 0;
    for (let index = record.children.length - 1; index >= 0; index -= 1) {
      const child = record.children[index];
      child.prelim += shift;
      child.mod += shift;
      change += child.change;
      shift += child.shift + change;
    }
  };

  const apportion = (
    record: LayoutRecord,
    defaultAncestor: LayoutRecord,
  ): LayoutRecord => {
    const left = leftSibling(record);
    if (!left) return defaultAncestor;

    let innerRight = record;
    let outerRight = record;
    let innerLeft = left;
    let outerLeft = leftmostSibling(record);
    if (!outerLeft) {
      throw new Error('Graph contour apportionment could not find the leftmost sibling.');
    }
    let innerRightMod = innerRight.mod;
    let outerRightMod = outerRight.mod;
    let innerLeftMod = innerLeft.mod;
    let outerLeftMod = outerLeft.mod;

    while (nextRight(innerLeft) && nextLeft(innerRight)) {
      innerLeft = nextRight(innerLeft)!;
      innerRight = nextLeft(innerRight)!;
      const followingOuterLeft = nextLeft(outerLeft);
      const followingOuterRight = nextRight(outerRight);
      if (!followingOuterLeft || !followingOuterRight) {
        throw new Error('Graph contour traversal ended before its inner contour.');
      }
      outerLeft = followingOuterLeft;
      outerRight = followingOuterRight;
      outerRight.ancestor = record;

      const contourShift =
        innerLeft.prelim +
        innerLeftMod -
        (innerRight.prelim + innerRightMod) +
        separation(innerLeft, innerRight);
      if (contourShift > 0) {
        const ancestor =
          innerLeft.ancestor.parent === record.parent
            ? innerLeft.ancestor
            : defaultAncestor;
        moveSubtree(ancestor, record, contourShift);
        innerRightMod += contourShift;
        outerRightMod += contourShift;
      }

      innerLeftMod += innerLeft.mod;
      innerRightMod += innerRight.mod;
      outerLeftMod += outerLeft.mod;
      outerRightMod += outerRight.mod;
    }

    if (nextRight(innerLeft) && !nextRight(outerRight)) {
      outerRight.thread = nextRight(innerLeft);
      outerRight.mod += innerLeftMod - outerRightMod;
    } else if (nextLeft(innerRight) && !nextLeft(outerLeft)) {
      outerLeft.thread = nextLeft(innerRight);
      outerLeft.mod += innerRightMod - outerLeftMod;
      return record;
    }
    return defaultAncestor;
  };

  for (const record of postorder) {
    if (record.children.length === 0) {
      const left = leftSibling(record);
      if (left) record.prelim = left.prelim + separation(left, record);
    } else {
      executeShifts(record);
      const first = record.children[0];
      const last = record.children[record.children.length - 1];
      const midpoint = (first.prelim + last.prelim) / 2;
      const left = leftSibling(record);
      if (left) {
        record.prelim = left.prelim + separation(left, record);
        record.mod = record.prelim - midpoint;
      } else {
        record.prelim = midpoint;
      }
    }

    if (record.parent) {
      record.parent.defaultAncestor = apportion(
        record,
        record.parent.defaultAncestor ?? record.parent.children[0],
      );
    }
  }

  const modifierByRecord = new Map<LayoutRecord, number>([
    [rootRecord, -rootRecord.prelim],
  ]);
  for (const record of traversal) {
    const modifier = modifierByRecord.get(record);
    if (modifier === undefined) {
      throw new Error(`Graph layout modifier is missing for node ${record.node.id}.`);
    }
    record.center = record.prelim + modifier;
    for (const child of record.children) {
      modifierByRecord.set(child, modifier + record.mod);
    }
  }

  // Pass 3: emit nodes and edges (child top-center up to parent bottom-center).
  const elbow = (x1: number, y1: number, x2: number, y2: number) => {
    const midY = Math.round((y1 + y2) / 2);
    edges.push({x: x1 - EDGE_T / 2, y: Math.min(y1, midY), w: EDGE_T, h: Math.abs(midY - y1)});
    edges.push({
      x: Math.min(x1, x2) - EDGE_T / 2,
      y: midY - EDGE_T / 2,
      w: Math.abs(x2 - x1) + EDGE_T,
      h: EDGE_T,
    });
    edges.push({x: x2 - EDGE_T / 2, y: Math.min(midY, y2), w: EDGE_T, h: Math.abs(y2 - midY)});
  };

  for (const record of traversal) {
    const {node, depth, center, size} = record;
    const x = center - size.w / 2;
    const y = rowTop[depth];
    if (!node.source) {
      nodes.push({
        id: node.id,
        kind: 'item',
        x,
        y,
        w: size.w,
        h: size.h,
        item: node,
      });
      continue;
    }
    nodes.push({
      id: node.source.id,
      kind: 'source',
      x,
      y,
      w: size.w,
      h: size.h,
      item: node,
      source: node.source,
    });

    for (const child of record.children) {
      elbow(
        child.center,
        rowTop[depth + 1],
        center,
        y + size.h,
      );
    }
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    minX = Math.min(minX, n.x);
    minY = Math.min(minY, n.y);
    maxX = Math.max(maxX, n.x + n.w);
    maxY = Math.max(maxY, n.y + n.h);
  }
  return {nodes, edges, minX, minY, maxX, maxY};
}
