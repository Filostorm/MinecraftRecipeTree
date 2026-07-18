import {Recipe} from '../types';
import {pixelArtDisplaySize} from '../data/pixelArtSizing';
import {ItemTreeNode, SourceTreeNode} from './model';

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
  const rowWalk = (n: ItemTreeNode, d: number) => {
    if (n.source) {
      seeH(d, compact ? COMPACT_ITEM_SIZE : sourceNodeSize(n.source).h);
      for (const input of n.source.inputs) rowWalk(input, d + 1);
    } else {
      seeH(d, compact ? COMPACT_ITEM_SIZE : ITEM_H);
    }
  };
  rowWalk(root, 0);

  const rowTop: number[] = [0];
  for (let d = 1; d < rowH.length; d++) {
    rowTop[d] = rowTop[d - 1] + rowH[d - 1] + LEVEL_GAP;
  }

  // Pass 2: subtree widths.
  const wMemo = new Map<string, number>();
  const widthItem = (n: ItemTreeNode): number => {
    const cached = wMemo.get(n.id);
    if (cached != null) return cached;
    let w = compact ? COMPACT_ITEM_SIZE : ITEM_W;
    if (n.source) {
      const own = compact ? COMPACT_ITEM_SIZE : sourceNodeSize(n.source).w;
      const kids = n.source.inputs;
      const kidsW = kids.length
        ? kids.reduce((s, k) => s + widthItem(k), 0) + SIBLING_GAP * (kids.length - 1)
        : 0;
      w = Math.max(own, kidsW);
    }
    wMemo.set(n.id, w);
    return w;
  };

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

  const placeItem = (n: ItemTreeNode, d: number, left: number, band: number) => {
    if (!n.source) {
      const size = compact ? COMPACT_ITEM_SIZE : ITEM_W;
      const height = compact ? COMPACT_ITEM_SIZE : ITEM_H;
      const x = left + (band - size) / 2;
      nodes.push({id: n.id, kind: 'item', x, y: rowTop[d], w: size, h: height, item: n});
      return;
    }
    const size = compact
      ? {w: COMPACT_ITEM_SIZE, h: COMPACT_ITEM_SIZE}
      : sourceNodeSize(n.source);
    const x = left + (band - size.w) / 2;
    const y = rowTop[d];
    nodes.push({id: n.source.id, kind: 'source', x, y, w: size.w, h: size.h, item: n, source: n.source});

    const kids = n.source.inputs;
    if (kids.length) {
      const kidsW = kids.reduce((s, k) => s + widthItem(k), 0) + SIBLING_GAP * (kids.length - 1);
      let childLeft = left + (band - kidsW) / 2;
      for (const kid of kids) {
        const kw = widthItem(kid);
        placeItem(kid, d + 1, childLeft, kw);
        elbow(childLeft + kw / 2, rowTop[d + 1], x + size.w / 2, y + size.h);
        childLeft += kw + SIBLING_GAP;
      }
    }
  };

  placeItem(root, 0, 0, widthItem(root));

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
