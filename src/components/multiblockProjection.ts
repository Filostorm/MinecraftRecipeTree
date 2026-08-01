import type {RecipeStructure} from '../types';

export const MAX_MULTIBLOCK_PREVIEW_CELLS = 240;

export interface ProjectedStructureCell {
  source: RecipeStructure['cells'][number];
  left: number;
  top: number;
  size: number;
  layer: number;
}

const LARGE_BLOCK_SPRITE = 32;
const COMPACT_BLOCK_SPRITE = 16;
const PREVIEW_PADDING = 10;

interface RawProjection {
  source: RecipeStructure['cells'][number];
  x: number;
  y: number;
  depth: number;
}

function rotatedXZ(x: number, z: number, rotation: number): [number, number] {
  switch (((rotation % 4) + 4) % 4) {
    case 1:
      return [-z, x];
    case 2:
      return [-x, -z];
    case 3:
      return [z, -x];
    default:
      return [x, z];
  }
}

export function previewStructureCells(
  cells: RecipeStructure['cells'],
  limit = MAX_MULTIBLOCK_PREVIEW_CELLS,
): RecipeStructure['cells'] {
  if (cells.length <= limit) return cells;
  let minX = Infinity;
  let minY = Infinity;
  let minZ = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  let maxZ = -Infinity;
  for (const [x, y, z] of cells) {
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    minZ = Math.min(minZ, z);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
    maxZ = Math.max(maxZ, z);
  }
  const exterior = cells.filter(
    ([x, y, z]) => x === minX || x === maxX || y === minY || y === maxY || z === minZ || z === maxZ,
  );
  const candidates = exterior.length >= limit ? exterior : cells;
  const selected: RecipeStructure['cells'] = [];
  for (let index = 0; index < limit; index += 1) {
    selected.push(candidates[Math.floor((index * candidates.length) / limit)]);
  }
  return selected;
}

export function projectStructureCells(
  cells: RecipeStructure['cells'],
  width: number,
  height: number,
  rotation: number,
): ProjectedStructureCell[] {
  if (cells.length === 0 || width <= 0 || height <= 0) return [];
  const raw: RawProjection[] = cells.map(source => {
    const [x, y, z] = source;
    const [rotatedX, rotatedZ] = rotatedXZ(x, z, rotation);
    return {
      source,
      // Minecraft's inventory block renders are already isometric. Moving adjacent sprites by
      // half their width and a quarter of their height makes those block faces meet like a placed
      // world volume instead of scattering icons across a diagram.
      x: (rotatedX - rotatedZ) * 0.5,
      y: (rotatedX + rotatedZ) * 0.25 - y * 0.5,
      depth: rotatedX + rotatedZ + y * 0.01,
    };
  });
  const minX = Math.min(...raw.map(cell => cell.x));
  const maxX = Math.max(...raw.map(cell => cell.x));
  const minY = Math.min(...raw.map(cell => cell.y));
  const maxY = Math.max(...raw.map(cell => cell.y));
  const spanX = Math.max(0, maxX - minX);
  const spanY = Math.max(0, maxY - minY);
  const availableWidth = Math.max(1, width - PREVIEW_PADDING * 2);
  const availableHeight = Math.max(1, height - PREVIEW_PADDING * 2);
  const largeWidth = spanX * LARGE_BLOCK_SPRITE + LARGE_BLOCK_SPRITE;
  const largeHeight = spanY * LARGE_BLOCK_SPRITE + LARGE_BLOCK_SPRITE;
  const size =
    largeWidth <= availableWidth && largeHeight <= availableHeight
      ? LARGE_BLOCK_SPRITE
      : COMPACT_BLOCK_SPRITE;
  // Very large structures retain a 16px pixel-aligned block sprite while their centers compress
  // uniformly. The overlap remains volumetric and avoids non-integral ItemIcon scaling.
  const spacing = Math.min(
    size,
    spanX > 0 ? (availableWidth - size) / spanX : size,
    spanY > 0 ? (availableHeight - size) / spanY : size,
  );
  const safeSpacing = Math.max(1, spacing);
  const usedWidth = spanX * safeSpacing + size;
  const usedHeight = spanY * safeSpacing + size;
  const offsetX = (width - usedWidth) / 2;
  const offsetY = (height - usedHeight) / 2;
  return raw
    .sort((a, b) => a.depth - b.depth || a.y - b.y || a.x - b.x)
    .map((cell, layer) => ({
      source: cell.source,
      left: offsetX + (cell.x - minX) * safeSpacing,
      top: offsetY + (cell.y - minY) * safeSpacing,
      size,
      layer,
    }));
}
