import type {Recipe} from '../types.ts';
import {pixelArtDisplaySize} from '../data/pixelArtSizing.ts';
import type {ItemTreeNode, SourceTreeNode} from './model.ts';

export const ITEM_W = 172;
export const ITEM_H = 58;
export const COMPACT_ITEM_SIZE = 52;
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
}

export interface EdgeRect {
  x: number;
  y: number;
  w: number;
  h: number;
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

/**
 * Tidy top-down tree layout. Expanded items are drawn as a single source node in
 * their item's place; collapsed items are small item nodes. Siblings stack
 * horizontally, parents center over their subtree, edges run child-top -> parent-bottom.
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
      seeH(depth, compact ? COMPACT_ITEM_SIZE : sourceNodeSize(node.source).h);
      for (let index = node.source.inputs.length - 1; index >= 0; index -= 1) {
        rowStack.push({node: node.source.inputs[index], depth: depth + 1});
      }
    } else {
      seeH(depth, compact ? COMPACT_ITEM_SIZE : ITEM_H);
    }
  }

  const rowTop: number[] = [0];
  for (let d = 1; d < rowH.length; d++) {
    rowTop[d] = rowTop[d - 1] + rowH[d - 1] + LEVEL_GAP;
  }

  // Pass 2: subtree widths.
  const wMemo = new Map<string, number>();
  const widthStack: Array<{node: ItemTreeNode; visited: boolean}> = [
    {node: root, visited: false},
  ];
  while (widthStack.length > 0) {
    const frame = widthStack.pop()!;
    if (!frame.visited && frame.node.source?.inputs.length) {
      widthStack.push({node: frame.node, visited: true});
      for (let index = frame.node.source.inputs.length - 1; index >= 0; index -= 1) {
        widthStack.push({node: frame.node.source.inputs[index], visited: false});
      }
      continue;
    }

    let width = compact ? COMPACT_ITEM_SIZE : ITEM_W;
    if (frame.node.source) {
      const own = compact ? COMPACT_ITEM_SIZE : sourceNodeSize(frame.node.source).w;
      const kids = frame.node.source.inputs;
      const kidsWidth =
        kids.length === 0
          ? 0
          : kids.reduce((sum, child) => {
              const childWidth = wMemo.get(child.id);
              if (childWidth === undefined) {
                throw new Error(`Graph layout width is missing for child node ${child.id}.`);
              }
              return sum + childWidth;
            }, 0) +
            SIBLING_GAP * (kids.length - 1);
      width = Math.max(own, kidsWidth);
    }
    wMemo.set(frame.node.id, width);
  }

  // Pass 3: placement + edges (child top-center up to parent bottom-center).
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

  type PlacementAction =
    | {kind: 'node'; node: ItemTreeNode; depth: number; left: number; band: number}
    | {kind: 'edge'; x1: number; y1: number; x2: number; y2: number};
  const rootWidth = wMemo.get(root.id);
  if (rootWidth === undefined) {
    throw new Error('Graph layout did not calculate a width for the root node.');
  }
  const placementStack: PlacementAction[] = [
    {kind: 'node', node: root, depth: 0, left: 0, band: rootWidth},
  ];
  while (placementStack.length > 0) {
    const action = placementStack.pop()!;
    if (action.kind === 'edge') {
      elbow(action.x1, action.y1, action.x2, action.y2);
      continue;
    }

    const {node, depth, left, band} = action;
    if (!node.source) {
      const size = compact ? COMPACT_ITEM_SIZE : ITEM_W;
      const height = compact ? COMPACT_ITEM_SIZE : ITEM_H;
      const x = left + (band - size) / 2;
      nodes.push({
        id: node.id,
        kind: 'item',
        x,
        y: rowTop[depth],
        w: size,
        h: height,
        item: node,
      });
      continue;
    }
    const size = compact
      ? {w: COMPACT_ITEM_SIZE, h: COMPACT_ITEM_SIZE}
      : sourceNodeSize(node.source);
    const x = left + (band - size.w) / 2;
    const y = rowTop[depth];
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

    const kids = node.source.inputs;
    if (kids.length) {
      let kidsWidth = SIBLING_GAP * (kids.length - 1);
      for (const child of kids) {
        const childWidth = wMemo.get(child.id);
        if (childWidth === undefined) {
          throw new Error(`Graph placement width is missing for child node ${child.id}.`);
        }
        kidsWidth += childWidth;
      }
      let childLeft = left + (band - kidsWidth) / 2;
      const childPlacements: Array<{node: ItemTreeNode; left: number; width: number}> = [];
      for (const kid of kids) {
        const childWidth = wMemo.get(kid.id);
        if (childWidth === undefined) {
          throw new Error(`Graph placement width is missing for child node ${kid.id}.`);
        }
        childPlacements.push({node: kid, left: childLeft, width: childWidth});
        childLeft += childWidth + SIBLING_GAP;
      }
      for (let index = childPlacements.length - 1; index >= 0; index -= 1) {
        const child = childPlacements[index];
        placementStack.push({
          kind: 'edge',
          x1: child.left + child.width / 2,
          y1: rowTop[depth + 1],
          x2: x + size.w / 2,
          y2: y + size.h,
        });
        placementStack.push({
          kind: 'node',
          node: child.node,
          depth: depth + 1,
          left: child.left,
          band: child.width,
        });
      }
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
