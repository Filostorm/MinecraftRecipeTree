import type {EdgeRect, GraphLayout, LaidNode} from './layout.ts';
import {
  COMPACT_ITEM_SIZE,
  sourceNodeSize,
} from './layout.ts';
import type {ItemTreeNode} from './model.ts';

const FULL_TURN = Math.PI * 2;
const EDGE_THICKNESS = 2;
const RADIAL_DEPTH_GAP = 64;
const RADIAL_NODE_GAP = 20;
const MAX_STAGGERED_ROWS = 8;

export const RADIAL_ITEM_SIZE = 52;
export const RADIAL_ROOT_SIZE = 76;
export const RADIAL_BRANCH_LABEL_WIDTH = 96;
const RADIAL_BRANCH_LABEL_HEIGHT = 72;
const RADIAL_EXPANDED_ROOT_HORIZONTAL_GROWTH = 24;
const RADIAL_EXPANDED_ROOT_VERTICAL_GROWTH = 18;

export interface StaggeredRadialRows {
  rowCount: number;
  rowGap: number;
  baseRadius: number;
  outerRadius: number;
  rowByIndex: number[];
  radiusByIndex: number[];
}

function positiveAngularDelta(from: number, to: number): number {
  const delta = to - from;
  return delta > 0 ? delta : delta + FULL_TURN;
}

/**
 * Assign one ordered polar level to collision-safe concentric rows.
 *
 * Adjacent angular nodes are interleaved between rows. The planner evaluates
 * up to eight row counts and chooses the smallest outer radius, so a
 * high-cardinality ingredient level becomes a compact staggered annulus rather
 * than one enormous circle.
 */
export function planStaggeredRadialRows(
  angles: number[],
  diameters: number[],
  minimumRadius: number,
): StaggeredRadialRows {
  if (angles.length === 0 || angles.length !== diameters.length) {
    throw new Error('Radial row planning requires matching non-empty angle and diameter arrays.');
  }
  if (!Number.isFinite(minimumRadius) || minimumRadius <= 0) {
    throw new Error('Radial row planning requires a positive finite minimum radius.');
  }
  angles.forEach((angle, index) => {
    if (!Number.isFinite(angle) || angle < 0 || angle >= FULL_TURN) {
      throw new Error(`Radial row angle ${index} must be finite and within one turn.`);
    }
    if (index > 0 && angle <= angles[index - 1]) {
      throw new Error('Radial row angles must be strictly increasing.');
    }
  });
  diameters.forEach((diameter, index) => {
    if (!Number.isFinite(diameter) || diameter <= 0) {
      throw new Error(`Radial node diameter ${index} must be positive and finite.`);
    }
  });

  let maximumDiameter = 0;
  for (const diameter of diameters) maximumDiameter = Math.max(maximumDiameter, diameter);
  const rowGap = maximumDiameter + RADIAL_NODE_GAP;
  let best: StaggeredRadialRows | null = null;
  const maximumRows = Math.min(MAX_STAGGERED_ROWS, angles.length);

  for (let rowCount = 1; rowCount <= maximumRows; rowCount += 1) {
    const rowByIndex = angles.map((_, index) => index % rowCount);
    let baseRadius = minimumRadius;

    for (let row = 0; row < rowCount; row += 1) {
      const indices: number[] = [];
      rowByIndex.forEach((assignedRow, index) => {
        if (assignedRow === row) indices.push(index);
      });
      if (indices.length <= 1) continue;

      indices.forEach((index, offset) => {
        const nextIndex = indices[(offset + 1) % indices.length];
        const angularDelta = positiveAngularDelta(angles[index], angles[nextIndex]);
        const sine = Math.sin(angularDelta / 2);
        if (!(sine > 0)) {
          throw new Error('Radial row planning encountered coincident angular nodes.');
        }
        const requiredSeparation =
          diameters[index] / 2 + diameters[nextIndex] / 2 + RADIAL_NODE_GAP;
        const requiredRowRadius = requiredSeparation / (2 * sine);
        baseRadius = Math.max(baseRadius, requiredRowRadius - row * rowGap);
      });
    }

    const radiusByIndex = rowByIndex.map(row => baseRadius + row * rowGap);
    let outerRadius = 0;
    radiusByIndex.forEach((radius, index) => {
      outerRadius = Math.max(outerRadius, radius + diameters[index] / 2);
    });
    const candidate = {
      rowCount,
      rowGap,
      baseRadius,
      outerRadius,
      rowByIndex,
      radiusByIndex,
    };
    if (
      !best ||
      candidate.outerRadius < best.outerRadius - 0.001 ||
      (Math.abs(candidate.outerRadius - best.outerRadius) <= 0.001 &&
        candidate.rowCount < best.rowCount)
    ) {
      best = candidate;
    }
  }

  if (!best) {
    throw new Error('Radial row planning did not produce a finite layout.');
  }
  return best;
}

interface RadialUnit {
  item: ItemTreeNode;
  children: number[];
  depth: number;
  visualW: number;
  visualH: number;
  collisionDiameter: number;
  leafCount: number;
  leafStart: number;
  leafEnd: number;
  angle: number;
  centerX: number;
  centerY: number;
}

function makeRadialUnit(item: ItemTreeNode, depth: number): RadialUnit {
  const visualSize = item.source
    ? sourceNodeSize(item.source)
    : {w: RADIAL_ITEM_SIZE, h: RADIAL_ITEM_SIZE};
  const collisionSize = item.source
    ? visualSize
    : {w: RADIAL_ITEM_SIZE, h: RADIAL_ITEM_SIZE};
  return {
    item,
    children: [],
    depth,
    visualW: visualSize.w,
    visualH: visualSize.h,
    collisionDiameter: Math.hypot(collisionSize.w, collisionSize.h),
    leafCount: 0,
    leafStart: 0,
    leafEnd: 0,
    angle: 0,
    centerX: 0,
    centerY: 0,
  };
}

function flattenRadialTree(root: ItemTreeNode, compact: boolean): RadialUnit[] {
  const rootUnit = makeRadialUnit(root, 0);
  if (compact) {
    rootUnit.visualW = RADIAL_ROOT_SIZE;
    rootUnit.visualH = RADIAL_ROOT_SIZE;
    rootUnit.collisionDiameter = Math.hypot(
      root.source ? Math.max(RADIAL_BRANCH_LABEL_WIDTH, RADIAL_ROOT_SIZE) : RADIAL_ROOT_SIZE,
      root.source ? Math.max(RADIAL_BRANCH_LABEL_HEIGHT, RADIAL_ROOT_SIZE) : RADIAL_ROOT_SIZE,
    );
  } else {
    rootUnit.visualW += RADIAL_EXPANDED_ROOT_HORIZONTAL_GROWTH;
    rootUnit.visualH += RADIAL_EXPANDED_ROOT_VERTICAL_GROWTH;
    rootUnit.collisionDiameter = Math.hypot(rootUnit.visualW, rootUnit.visualH);
  }
  const units = [rootUnit];
  const expansionStack = [0];

  while (expansionStack.length > 0) {
    const parentIndex = expansionStack.pop()!;
    const parent = units[parentIndex];
    const inputs = parent.item.source?.inputs ?? [];
    for (const input of inputs) {
      const child = makeRadialUnit(input, parent.depth + 1);
      if (compact) {
        child.visualW = COMPACT_ITEM_SIZE;
        child.visualH = COMPACT_ITEM_SIZE;
        child.collisionDiameter = Math.hypot(
          input.source ? RADIAL_BRANCH_LABEL_WIDTH : COMPACT_ITEM_SIZE,
          input.source ? RADIAL_BRANCH_LABEL_HEIGHT : COMPACT_ITEM_SIZE,
        );
      }
      const childIndex = units.length;
      units.push(child);
      parent.children.push(childIndex);
    }
    for (let offset = parent.children.length - 1; offset >= 0; offset -= 1) {
      expansionStack.push(parent.children[offset]);
    }
  }
  return units;
}

function calculateLeafSpans(units: RadialUnit[]): number {
  for (let index = units.length - 1; index >= 0; index -= 1) {
    const unit = units[index];
    unit.leafCount =
      unit.children.length === 0
        ? 1
        : unit.children.reduce((sum, childIndex) => sum + units[childIndex].leafCount, 0);
  }
  const totalLeaves = units[0]?.leafCount;
  if (!Number.isSafeInteger(totalLeaves) || totalLeaves <= 0) {
    throw new Error('Radial graph layout could not derive a positive leaf count.');
  }

  units[0].leafStart = 0;
  units[0].leafEnd = totalLeaves;
  const spanStack = [0];
  while (spanStack.length > 0) {
    const index = spanStack.pop()!;
    const unit = units[index];
    let cursor = unit.leafStart;
    for (const childIndex of unit.children) {
      const child = units[childIndex];
      child.leafStart = cursor;
      child.leafEnd = cursor + child.leafCount;
      child.angle = FULL_TURN * ((child.leafStart + child.leafEnd) / 2 / totalLeaves);
      cursor = child.leafEnd;
    }
    for (let offset = unit.children.length - 1; offset >= 0; offset -= 1) {
      spanStack.push(unit.children[offset]);
    }
  }
  return totalLeaves;
}

function clipDistanceToRect(width: number, height: number, ux: number, uy: number): number {
  const horizontal = Math.abs(ux) > 1e-9 ? width / 2 / Math.abs(ux) : Infinity;
  const vertical = Math.abs(uy) > 1e-9 ? height / 2 / Math.abs(uy) : Infinity;
  return Math.min(horizontal, vertical);
}

function addRadialEdge(edges: EdgeRect[], parent: RadialUnit, child: RadialUnit): void {
  const dx = child.centerX - parent.centerX;
  const dy = child.centerY - parent.centerY;
  const centerDistance = Math.hypot(dx, dy);
  if (!(centerDistance > 0) || !Number.isFinite(centerDistance)) {
    throw new Error('Radial graph edge endpoints must have distinct finite centers.');
  }
  const ux = dx / centerDistance;
  const uy = dy / centerDistance;
  const parentClip = clipDistanceToRect(parent.visualW, parent.visualH, ux, uy);
  const childClip = clipDistanceToRect(child.visualW, child.visualH, ux, uy);
  const startX = parent.centerX + ux * parentClip;
  const startY = parent.centerY + uy * parentClip;
  const endX = child.centerX - ux * childClip;
  const endY = child.centerY - uy * childClip;
  const length = Math.hypot(endX - startX, endY - startY);
  if (!(length > 0) || !Number.isFinite(length)) {
    throw new Error('Radial graph edge does not have positive finite clearance.');
  }
  edges.push({
    x: (startX + endX) / 2 - length / 2,
    y: (startY + endY) / 2 - EDGE_THICKNESS / 2,
    w: length,
    h: EDGE_THICKNESS,
    angle: Math.atan2(endY - startY, endX - startX),
  });
}

/**
 * Deterministic radial tree layout.
 *
 * The selected output is centered, dependency subtrees receive contiguous
 * angular sectors, and every depth expands outward through one or more
 * automatically staggered concentric rows. Node views themselves are never
 * rotated, so item icons and recipe previews remain upright.
 */
export function layoutRadialTree(root: ItemTreeNode, compact = false): GraphLayout {
  const units = flattenRadialTree(root, compact);
  calculateLeafSpans(units);

  const levels: number[][] = [];
  units.forEach((unit, index) => {
    (levels[unit.depth] ??= []).push(index);
  });

  let previousOuterRadius = units[0].collisionDiameter / 2;
  for (let depth = 1; depth < levels.length; depth += 1) {
    const indices = levels[depth];
    if (!indices || indices.length === 0) {
      throw new Error(`Radial graph layout is missing tree depth ${depth}.`);
    }
    const angles = indices.map(index => units[index].angle);
    const diameters = indices.map(index => units[index].collisionDiameter);
    let maximumDiameter = 0;
    for (const diameter of diameters) maximumDiameter = Math.max(maximumDiameter, diameter);
    const minimumRadius =
      previousOuterRadius + maximumDiameter / 2 + RADIAL_DEPTH_GAP;
    const rowPlan = planStaggeredRadialRows(angles, diameters, minimumRadius);

    indices.forEach((index, offset) => {
      const unit = units[index];
      const displayAngle = unit.angle - Math.PI / 2;
      const radius = rowPlan.radiusByIndex[offset];
      unit.centerX = Math.cos(displayAngle) * radius;
      unit.centerY = Math.sin(displayAngle) * radius;
    });
    previousOuterRadius = rowPlan.outerRadius;
  }

  const nodes: LaidNode[] = units.map(unit => ({
    id: unit.item.source?.id ?? unit.item.id,
    kind: unit.item.source ? 'source' : 'item',
    x: unit.centerX - unit.visualW / 2,
    y: unit.centerY - unit.visualH / 2,
    w: unit.visualW,
    h: unit.visualH,
    item: unit.item,
    source: unit.item.source,
    radial: unit.item.source === undefined,
    depth: unit.depth,
    compactBranch: compact && unit.item.source !== undefined,
  }));

  const edges: EdgeRect[] = [];
  units.forEach(unit => {
    unit.children.forEach(childIndex => addRadialEdge(edges, unit, units[childIndex]));
  });

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  nodes.forEach(node => {
    minX = Math.min(minX, node.x);
    minY = Math.min(minY, node.y);
    maxX = Math.max(maxX, node.x + node.w);
    maxY = Math.max(maxY, node.y + node.h);
    if (node.compactBranch) {
      minX = Math.min(minX, node.x - (RADIAL_BRANCH_LABEL_WIDTH - node.w) / 2);
      maxX = Math.max(maxX, node.x + node.w + (RADIAL_BRANCH_LABEL_WIDTH - node.w) / 2);
      maxY = Math.max(maxY, node.y + RADIAL_BRANCH_LABEL_HEIGHT);
    }
  });
  if (![minX, minY, maxX, maxY].every(Number.isFinite)) {
    throw new Error('Radial graph layout produced non-finite bounds.');
  }

  return {nodes, edges, minX, minY, maxX, maxY};
}
