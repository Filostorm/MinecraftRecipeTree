import type {EdgeRect, GraphLayout, LaidNode} from './layout.ts';
import {
  COMPACT_LABEL_HEIGHT,
  COMPACT_LABEL_WIDTH,
  COMPACT_ITEM_SIZE,
  COMPACT_ROOT_LABEL_HEIGHT,
  sourceNodeSize,
} from './layout.ts';
import type {ItemTreeNode} from './model.ts';

const FULL_TURN = Math.PI * 2;
const EDGE_THICKNESS = 2;
const RADIAL_DEPTH_GAP = 64;
const RADIAL_NODE_GAP = 20;
const RADIAL_TERMINAL_GAP = RADIAL_NODE_GAP;
const RADIAL_COLLISION_PLACEMENT_STEP = 12;
const RADIAL_COLLISION_ROTATION_STEP = Math.PI / 36;
const RADIAL_COLLISION_ROTATION_STEPS = 8;
const MAX_COLLISION_PLACEMENT_ATTEMPTS = 100_000;
const MAX_STAGGERED_ROWS = 8;
const MIN_LOCAL_FAN_SPAN = Math.PI / 7.5;
const MAX_LOCAL_FAN_SPAN = Math.PI * 5 / 6;

export const RADIAL_ITEM_SIZE = 52;
export const RADIAL_ROOT_DIAMOND_SIZE = 72;
export const RADIAL_ROOT_SIZE = Math.ceil(RADIAL_ROOT_DIAMOND_SIZE * Math.SQRT2);
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
    if (!Number.isFinite(angle)) {
      throw new Error(`Radial row angle ${index} must be finite.`);
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

function rotateSubtreeAngles(
  units: RadialUnit[],
  rootIndex: number,
  angularDelta: number,
): void {
  if (Math.abs(angularDelta) <= 1e-9) return;
  const stack = [rootIndex];
  while (stack.length > 0) {
    const index = stack.pop()!;
    const unit = units[index];
    unit.angle += angularDelta;
    unit.leafStart += angularDelta;
    unit.leafEnd += angularDelta;
    for (const childIndex of unit.children) stack.push(childIndex);
  }
}

/**
 * Compress a non-root recipe's children into an outward-facing local fan.
 *
 * Global leaf sectors are useful for separating the root branches, but reusing
 * their full width at every descendant creates large hollow wedges. Local fans
 * retain the branch direction while using only the angle needed by this
 * recipe's immediate inputs; the stagger planner handles denser groups.
 */
function packLocalChildAngles(
  units: RadialUnit[],
  parentIndex: number,
  minimumParentDistance: number,
): void {
  const parent = units[parentIndex];
  const childIndices = parent.children;
  if (parent.depth === 0 || childIndices.length === 0) return;
  if (childIndices.length === 1) {
    rotateSubtreeAngles(
      units,
      childIndices[0],
      parent.angle - units[childIndices[0]].angle,
    );
    return;
  }

  let maximumDiameter = 0;
  for (const childIndex of childIndices) {
    maximumDiameter = Math.max(
      maximumDiameter,
      units[childIndex].collisionDiameter,
    );
  }
  const separationRatio = Math.min(
    0.95,
    (maximumDiameter + RADIAL_NODE_GAP) / (2 * minimumParentDistance),
  );
  const minimumAngularSeparation = 2 * Math.asin(separationRatio);
  const requiredSpan = Math.min(
    MAX_LOCAL_FAN_SPAN,
    minimumAngularSeparation * (childIndices.length - 1),
  );
  const originalSpan =
    units[childIndices[childIndices.length - 1]].angle -
    units[childIndices[0]].angle;
  if (!(originalSpan >= 0) || !Number.isFinite(originalSpan)) {
    throw new Error(`Radial graph branch ${parentIndex} has invalid child angles.`);
  }
  const preferredMaximumSpan = Math.max(
    MIN_LOCAL_FAN_SPAN,
    requiredSpan * 1.15,
  );
  const targetSpan = Math.max(
    requiredSpan,
    Math.min(originalSpan, preferredMaximumSpan, MAX_LOCAL_FAN_SPAN),
  );
  const firstAngle = parent.angle - targetSpan / 2;
  const angularStep = targetSpan / (childIndices.length - 1);
  childIndices.forEach((childIndex, offset) => {
    const targetAngle = firstAngle + angularStep * offset;
    rotateSubtreeAngles(
      units,
      childIndex,
      targetAngle - units[childIndex].angle,
    );
  });
}

interface RadialUnit {
  item: ItemTreeNode;
  children: number[];
  parentIndex: number | null;
  depth: number;
  visualW: number;
  visualH: number;
  collisionW: number;
  collisionH: number;
  collisionDiameter: number;
  terminal: boolean;
  leafCount: number;
  leafStart: number;
  leafEnd: number;
  angle: number;
  centerX: number;
  centerY: number;
}

function makeRadialUnit(
  item: ItemTreeNode,
  depth: number,
  parentIndex: number | null,
  terminal: boolean,
): RadialUnit {
  const visualSize = item.source
    ? sourceNodeSize(item.source)
    : {w: RADIAL_ITEM_SIZE, h: RADIAL_ITEM_SIZE};
  const collisionSize = item.source
    ? visualSize
    : {w: RADIAL_ITEM_SIZE, h: RADIAL_ITEM_SIZE};
  return {
    item,
    children: [],
    parentIndex,
    depth,
    visualW: visualSize.w,
    visualH: visualSize.h,
    collisionW: collisionSize.w,
    collisionH: collisionSize.h,
    collisionDiameter: Math.max(collisionSize.w, collisionSize.h),
    terminal,
    leafCount: 0,
    leafStart: 0,
    leafEnd: 0,
    angle: 0,
    centerX: 0,
    centerY: 0,
  };
}

function flattenRadialTree(
  root: ItemTreeNode,
  compact: boolean,
  isTerminal: (item: ItemTreeNode) => boolean,
  showLabels: boolean,
): RadialUnit[] {
  const rootUnit = makeRadialUnit(root, 0, null, false);
  if (compact) {
    rootUnit.visualW = RADIAL_ROOT_SIZE;
    rootUnit.visualH = RADIAL_ROOT_SIZE;
    rootUnit.collisionW = showLabels
      ? Math.max(COMPACT_LABEL_WIDTH, RADIAL_ROOT_SIZE)
      : RADIAL_ROOT_SIZE;
    rootUnit.collisionH = showLabels
      ? RADIAL_ROOT_SIZE + COMPACT_ROOT_LABEL_HEIGHT
      : RADIAL_ROOT_SIZE;
    rootUnit.collisionDiameter = Math.max(rootUnit.collisionW, rootUnit.collisionH);
  } else if (!root.source) {
    rootUnit.visualW = RADIAL_ROOT_SIZE;
    rootUnit.visualH = RADIAL_ROOT_SIZE;
    rootUnit.collisionW = showLabels
      ? Math.max(COMPACT_LABEL_WIDTH, RADIAL_ROOT_SIZE)
      : RADIAL_ROOT_SIZE;
    rootUnit.collisionH = showLabels
      ? RADIAL_ROOT_SIZE + COMPACT_ROOT_LABEL_HEIGHT
      : RADIAL_ROOT_SIZE;
    rootUnit.collisionDiameter = Math.max(rootUnit.collisionW, rootUnit.collisionH);
  } else {
    rootUnit.visualW += RADIAL_EXPANDED_ROOT_HORIZONTAL_GROWTH;
    rootUnit.visualH += RADIAL_EXPANDED_ROOT_VERTICAL_GROWTH;
    rootUnit.collisionW = rootUnit.visualW;
    rootUnit.collisionH = rootUnit.visualH;
    rootUnit.collisionDiameter = Math.max(rootUnit.collisionW, rootUnit.collisionH);
  }
  const units = [rootUnit];
  const expansionStack = [0];

  while (expansionStack.length > 0) {
    const parentIndex = expansionStack.pop()!;
    const parent = units[parentIndex];
    const inputs = parent.item.source?.inputs ?? [];
    for (const input of inputs) {
      const child = makeRadialUnit(
        input,
        parent.depth + 1,
        parentIndex,
        input.source === undefined && isTerminal(input),
      );
      if (compact) {
        child.visualW = COMPACT_ITEM_SIZE;
        child.visualH = COMPACT_ITEM_SIZE;
        child.collisionW = showLabels ? COMPACT_LABEL_WIDTH : COMPACT_ITEM_SIZE;
        child.collisionH = showLabels
          ? COMPACT_ITEM_SIZE + COMPACT_LABEL_HEIGHT
          : COMPACT_ITEM_SIZE;
        child.collisionDiameter = Math.max(child.collisionW, child.collisionH);
      } else if (!input.source && showLabels) {
        child.collisionW = COMPACT_LABEL_WIDTH;
        child.collisionH = RADIAL_ITEM_SIZE + COMPACT_LABEL_HEIGHT;
        child.collisionDiameter = Math.max(child.collisionW, child.collisionH);
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

function calculateAngularSectors(units: RadialUnit[]): void {
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
  units[0].leafEnd = FULL_TURN;
  const spanStack = [0];
  while (spanStack.length > 0) {
    const index = spanStack.pop()!;
    const unit = units[index];
    const angularSpan = unit.leafEnd - unit.leafStart;
    let totalAngularWeight = 0;
    for (const childIndex of unit.children) {
      totalAngularWeight += Math.sqrt(units[childIndex].leafCount);
    }
    if (unit.children.length > 0 && !(totalAngularWeight > 0)) {
      throw new Error(`Radial graph branch ${index} has invalid angular weight.`);
    }

    let cursor = unit.leafStart;
    for (const childIndex of unit.children) {
      const child = units[childIndex];
      const childAngularSpan =
        angularSpan * (Math.sqrt(child.leafCount) / totalAngularWeight);
      child.leafStart = cursor;
      child.leafEnd = cursor + childAngularSpan;
      child.angle = (child.leafStart + child.leafEnd) / 2;
      cursor = child.leafEnd;
    }
    for (let offset = unit.children.length - 1; offset >= 0; offset -= 1) {
      spanStack.push(unit.children[offset]);
    }
  }
}

interface SpatialEntry {
  x: number;
  y: number;
  halfW: number;
  halfH: number;
  cells: string[];
}

class RadialSpatialIndex {
  private readonly units: RadialUnit[];
  private readonly cellSize: number;
  private readonly cells = new Map<string, Set<number>>();
  private readonly entries = new Map<number, SpatialEntry>();

  constructor(units: RadialUnit[]) {
    this.units = units;
    let maximumCollisionDiameter = 0;
    for (const unit of units) {
      maximumCollisionDiameter = Math.max(maximumCollisionDiameter, unit.collisionDiameter);
    }
    this.cellSize = Math.max(96, maximumCollisionDiameter + RADIAL_NODE_GAP);
  }

  private cellKeysFor(x: number, y: number, radius: number): string[] {
    const keys: string[] = [];
    const minColumn = Math.floor((x - radius) / this.cellSize);
    const maxColumn = Math.floor((x + radius) / this.cellSize);
    const minRow = Math.floor((y - radius) / this.cellSize);
    const maxRow = Math.floor((y + radius) / this.cellSize);
    for (let column = minColumn; column <= maxColumn; column += 1) {
      for (let row = minRow; row <= maxRow; row += 1) {
        keys.push(`${column}:${row}`);
      }
    }
    return keys;
  }

  insert(index: number, x: number, y: number): void {
    if (this.entries.has(index)) {
      throw new Error(`Radial spatial index cannot insert duplicate node ${index}.`);
    }
    const halfW = this.units[index].collisionW / 2;
    const halfH = this.units[index].collisionH / 2;
    const entry = {
      x,
      y,
      halfW,
      halfH,
      cells: this.cellKeysFor(x, y, Math.max(halfW, halfH) + RADIAL_NODE_GAP),
    };
    this.entries.set(index, entry);
    for (const key of entry.cells) {
      const bucket = this.cells.get(key) ?? new Set<number>();
      bucket.add(index);
      this.cells.set(key, bucket);
    }
  }

  remove(index: number): void {
    const entry = this.entries.get(index);
    if (!entry) {
      throw new Error(`Radial spatial index cannot remove missing node ${index}.`);
    }
    for (const key of entry.cells) {
      const bucket = this.cells.get(key);
      bucket?.delete(index);
      if (bucket?.size === 0) this.cells.delete(key);
    }
    this.entries.delete(index);
  }

  collides(index: number, x: number, y: number): boolean {
    const halfW = this.units[index].collisionW / 2;
    const halfH = this.units[index].collisionH / 2;
    const candidates = new Set<number>();
    for (const key of this.cellKeysFor(
      x,
      y,
      Math.max(halfW, halfH) + RADIAL_NODE_GAP,
    )) {
      for (const candidate of this.cells.get(key) ?? []) candidates.add(candidate);
    }
    for (const candidate of candidates) {
      if (candidate === index) continue;
      const entry = this.entries.get(candidate);
      if (!entry) {
        throw new Error(`Radial spatial index lost node ${candidate}.`);
      }
      const horizontalClearance = halfW + entry.halfW + RADIAL_NODE_GAP;
      const verticalClearance = halfH + entry.halfH + RADIAL_NODE_GAP;
      if (
        Math.abs(x - entry.x) < horizontalClearance - 0.001 &&
        Math.abs(y - entry.y) < verticalClearance - 0.001
      ) {
        return true;
      }
    }
    return false;
  }
}

function collisionRotationOffsets(parent: RadialUnit): number[] {
  if (parent.depth === 0) return [0];
  const offsets = [0];
  for (let step = 1; step <= RADIAL_COLLISION_ROTATION_STEPS; step += 1) {
    const offset = step * RADIAL_COLLISION_ROTATION_STEP;
    offsets.push(offset, -offset);
  }
  return offsets;
}

/**
 * Place every sibling group on parent-centered annuli. A shared collision
 * offset moves the complete group together, preserving approximately equal
 * parent-to-child edge lengths instead of aligning descendants to root-centered
 * depth rings. Before extending those edges, the solver rotates a descendant
 * fan through nearby angular lanes so occupied neighboring branches do not
 * force an otherwise compact recipe ring far away from its parent.
 */
function placeBranchLocalRings(units: RadialUnit[], levels: number[][]): void {
  const spatialIndex = new RadialSpatialIndex(units);
  spatialIndex.insert(0, units[0].centerX, units[0].centerY);

  for (let depth = 1; depth < levels.length; depth += 1) {
    const indices = levels[depth];
    if (!indices || indices.length === 0) {
      throw new Error(`Radial graph layout is missing tree depth ${depth}.`);
    }

    const parentIndices = [...new Set(indices.map(index => units[index].parentIndex))]
      .sort((left, right) => {
        if (left === null || right === null) return Number(left === null) - Number(right === null);
        return units[left].angle - units[right].angle || left - right;
      });
    for (const parentIndex of parentIndices) {
      if (parentIndex === null) {
        throw new Error(`Radial graph depth ${depth} contains a parentless node.`);
      }
      const parent = units[parentIndex];
      const childIndices = parent.children;
      const diameters = childIndices.map(index => units[index].collisionDiameter);
      let maximumDiameter = 0;
      for (const diameter of diameters) maximumDiameter = Math.max(maximumDiameter, diameter);
      const allTerminal = childIndices.every(index => units[index].terminal);
      const minimumParentDistance =
        parent.collisionDiameter / 2 +
        maximumDiameter / 2 +
        (allTerminal ? RADIAL_TERMINAL_GAP : RADIAL_DEPTH_GAP);
      packLocalChildAngles(units, parentIndex, minimumParentDistance);
      const packedAngles = childIndices.map(index => units[index].angle);
      const rowPlan = planStaggeredRadialRows(
        packedAngles,
        diameters,
        minimumParentDistance,
      );

      let collisionOffset = 0;
      const rotationOffsets = collisionRotationOffsets(parent);
      for (let attempts = 0; ; attempts += 1) {
        let placement:
          | {
              rotationOffset: number;
              candidates: Array<{index: number; centerX: number; centerY: number}>;
            }
          | undefined;
        for (const rotationOffset of rotationOffsets) {
          const candidates = childIndices.map((index, offset) => {
            const unit = units[index];
            const displayAngle = unit.angle + rotationOffset - Math.PI / 2;
            const parentDistance = rowPlan.radiusByIndex[offset] + collisionOffset;
            return {
              index,
              centerX: parent.centerX + Math.cos(displayAngle) * parentDistance,
              centerY: parent.centerY + Math.sin(displayAngle) * parentDistance,
            };
          });
          if (
            candidates.every(candidate =>
              !spatialIndex.collides(candidate.index, candidate.centerX, candidate.centerY),
            )
          ) {
            placement = {rotationOffset, candidates};
            break;
          }
        }
        if (placement) {
          for (const childIndex of childIndices) {
            rotateSubtreeAngles(units, childIndex, placement.rotationOffset);
          }
          for (const candidate of placement.candidates) {
            const unit = units[candidate.index];
            unit.centerX = candidate.centerX;
            unit.centerY = candidate.centerY;
            spatialIndex.insert(candidate.index, candidate.centerX, candidate.centerY);
          }
          break;
        }
        collisionOffset += RADIAL_COLLISION_PLACEMENT_STEP;
        if (attempts > MAX_COLLISION_PLACEMENT_ATTEMPTS) {
          throw new Error(
            `Radial graph branch ${parentIndex} could not find collision-free placement.`,
          );
        }
      }
    }
  }
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
 * angular sectors with sublinear leaf weighting so sparse deep branches retain
 * usable interior clearance. Sibling groups expand through parent-centered,
 * staggered annuli, and a global spatial index moves complete sibling groups
 * together when cross-branch collisions require more clearance. Node views
 * themselves are never rotated, so item icons and recipe previews remain upright.
 */
export function layoutRadialTree(
  root: ItemTreeNode,
  compact = false,
  isTerminal: (item: ItemTreeNode) => boolean = () => false,
  showLabels = false,
): GraphLayout {
  const units = flattenRadialTree(root, compact, isTerminal, showLabels);
  calculateAngularSectors(units);

  const levels: number[][] = [];
  units.forEach((unit, index) => {
    (levels[unit.depth] ??= []).push(index);
  });

  placeBranchLocalRings(units, levels);

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
    if (showLabels && (node.radial || node.compactBranch)) {
      const labelOverflow = Math.max(0, (COMPACT_LABEL_WIDTH - node.w) / 2);
      minX = Math.min(minX, node.x - labelOverflow);
      maxX = Math.max(maxX, node.x + node.w + labelOverflow);
      maxY = Math.max(
        maxY,
        node.y +
          node.h +
          (node.depth === 0 ? COMPACT_ROOT_LABEL_HEIGHT : COMPACT_LABEL_HEIGHT),
      );
    }
  });
  if (![minX, minY, maxX, maxY].every(Number.isFinite)) {
    throw new Error('Radial graph layout produced non-finite bounds.');
  }

  return {nodes, edges, minX, minY, maxX, maxY};
}
