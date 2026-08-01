import type {RecipeStructure} from '../types';

export const MAX_MULTIBLOCK_PREVIEW_CELLS = 240;

export interface ProjectedStructureCell {
  source: RecipeStructure['cells'][number];
  left: number;
  top: number;
  size: number;
  layer: number;
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
  const raw = cells.map(source => {
    const [x, y, z] = source;
    const [rotatedX, rotatedZ] = rotatedXZ(x, z, rotation);
    return {
      source,
      x: rotatedX - rotatedZ,
      y: (rotatedX + rotatedZ) * 0.52 - y * 0.92,
      layer: rotatedX + rotatedZ + y * 0.01,
    };
  });
  const minX = Math.min(...raw.map(cell => cell.x));
  const maxX = Math.max(...raw.map(cell => cell.x));
  const minY = Math.min(...raw.map(cell => cell.y));
  const maxY = Math.max(...raw.map(cell => cell.y));
  const spanX = Math.max(1, maxX - minX);
  const spanY = Math.max(1, maxY - minY);
  const padding = 10;
  const scale = Math.min((width - padding * 2) / spanX, (height - padding * 2) / spanY);
  const size = 20;
  const usedWidth = spanX * scale;
  const usedHeight = spanY * scale;
  const offsetX = (width - usedWidth) / 2;
  const offsetY = (height - usedHeight) / 2;
  return raw
    .sort((a, b) => a.layer - b.layer || a.y - b.y || a.x - b.x)
    .map((cell, layer) => ({
      source: cell.source,
      left: offsetX + (cell.x - minX) * scale - size / 2,
      top: offsetY + (cell.y - minY) * scale - size / 2,
      size,
      layer,
    }));
}
