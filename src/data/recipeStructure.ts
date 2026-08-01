import type {RecipeStructure} from '../types';

const MAX_STRUCTURE_CELLS = 100_000;
const MAX_STRUCTURE_BLOCK_TYPES = 10_000;

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, expected: readonly string[]): boolean {
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  return actual.length === required.length && actual.every((key, index) => key === required[index]);
}

export function requireRecipeStructure(value: unknown, label: string): RecipeStructure {
  if (
    !isRecord(value) ||
    !hasExactKeys(value, ['size', 'total', 'controller', 'blocks', 'cells']) ||
    !Array.isArray(value.size) ||
    value.size.length !== 3 ||
    !value.size.every(size => Number.isSafeInteger(size) && (size as number) > 0) ||
    !Number.isSafeInteger(value.total) ||
    (value.total as number) <= 0 ||
    typeof value.controller !== 'string' ||
    value.controller.length === 0 ||
    !Array.isArray(value.blocks) ||
    value.blocks.length === 0 ||
    value.blocks.length > MAX_STRUCTURE_BLOCK_TYPES ||
    !Array.isArray(value.cells) ||
    value.cells.length === 0 ||
    value.cells.length > MAX_STRUCTURE_CELLS ||
    value.total !== value.cells.length
  ) {
    throw new Error(`${label} is not a valid bounded multiblock structure.`);
  }

  const declaredCounts = new Map<string, number>();
  let declaredTotal = 0;
  for (const block of value.blocks) {
    if (
      !Array.isArray(block) ||
      block.length !== 2 ||
      typeof block[0] !== 'string' ||
      block[0].length === 0 ||
      !Number.isSafeInteger(block[1]) ||
      block[1] <= 0 ||
      declaredCounts.has(block[0])
    ) {
      throw new Error(`${label}.blocks contains an invalid or repeated counted block.`);
    }
    declaredCounts.set(block[0], block[1]);
    declaredTotal += block[1];
  }
  if (declaredTotal !== value.total || !declaredCounts.has(value.controller)) {
    throw new Error(`${label}.blocks does not account for every position and its controller.`);
  }

  const actualCounts = new Map<string, number>();
  const positions = new Set<string>();
  let minX = Infinity;
  let minY = Infinity;
  let minZ = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  let maxZ = -Infinity;
  for (const cell of value.cells) {
    if (
      !Array.isArray(cell) ||
      cell.length !== 4 ||
      !Number.isSafeInteger(cell[0]) ||
      !Number.isSafeInteger(cell[1]) ||
      !Number.isSafeInteger(cell[2]) ||
      typeof cell[3] !== 'string' ||
      cell[3].length === 0
    ) {
      throw new Error(`${label}.cells contains an invalid position.`);
    }
    const [x, y, z, key] = cell;
    const position = `${x}:${y}:${z}`;
    if (positions.has(position)) {
      throw new Error(`${label}.cells repeats position ${position}.`);
    }
    positions.add(position);
    actualCounts.set(key, (actualCounts.get(key) ?? 0) + 1);
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    minZ = Math.min(minZ, z);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
    maxZ = Math.max(maxZ, z);
  }
  const [sizeX, sizeY, sizeZ] = value.size;
  if (maxX - minX + 1 !== sizeX || maxY - minY + 1 !== sizeY || maxZ - minZ + 1 !== sizeZ) {
    throw new Error(`${label}.size does not match the exported positions.`);
  }
  if (
    actualCounts.size !== declaredCounts.size ||
    [...actualCounts].some(([key, count]) => declaredCounts.get(key) !== count)
  ) {
    throw new Error(`${label}.blocks does not match its structure positions.`);
  }
  if (!value.cells.some(cell => cell[3] === value.controller)) {
    throw new Error(`${label}.controller does not occur in the structure positions.`);
  }
  return value as unknown as RecipeStructure;
}
