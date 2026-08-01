#!/usr/bin/env node
import {execFile} from 'node:child_process';
import {readFile, readdir, writeFile} from 'node:fs/promises';
import {basename, join, resolve} from 'node:path';
import {promisify} from 'node:util';

const SOURCES = {
  mm2: {
    commit: 'b2394d07b8bafff7ba491e30bded6adfe8358cdd',
    label: 'Filostorm/Multiblock-Madness-2 1.0.0',
  },
  meatballcraft: {
    commit: '3533ad27ec4a550778c6107707dc5f35b611d9c0',
    label: 'sainagh/meatballcraft 0.18.6',
  },
};

function argument(name) {
  const index = process.argv.indexOf(name);
  if (index < 0 || index + 1 >= process.argv.length) {
    throw new Error(`Missing required ${name} argument.`);
  }
  return process.argv[index + 1];
}

const pack = argument('--pack');
if (!Object.hasOwn(SOURCES, pack)) throw new Error(`Unsupported --pack ${JSON.stringify(pack)}.`);
const packRoot = resolve(argument('--pack-root'));
const itemsDirectory = resolve(argument('--items-dir'));
const recipesPath = resolve(argument('--recipes'));
const output = resolve(argument('--output'));
const source = SOURCES[pack];

// MM2's Galactic Trade Center still names this removed KubeJS block in its checked-in
// pattern. Multiblocked omits the unresolved predicate from the published REI preview,
// so the compatibility snapshot must do the same to match what players actually see.
const MM2_UNREGISTERED_PREVIEW_BLOCKS = new Set(['kubejs:uhv_casing']);
const MM2_PUBLISHED_BLOCK_ALIASES = new Map([
  // The immutable 1.0.0 REI export contains the corrected tier-three receiver parts,
  // while the public pack repository still has the older tier-one/tier-two IDs.
  ['mbm2_data_receiver.json|kubejs:tier_1_mechanical_alloy_cog_block',
    'kubejs:tier_3_mechanical_alloy_cog_block'],
  ['mbm2_data_receiver.json|kubejs:tier_2_structural_alloy_scaffolding',
    'kubejs:tier_3_structural_alloy_scaffolding'],
  // Multiblocked exposes a placed source-water block as its obtainable bucket stack.
  ['*|minecraft:water', 'minecraft:water_bucket'],
]);
const MEATBALL_MMCE_BLOCK_ALIASES = new Map([
  // MMCE absorbed the old Modular Machinery Addons singularity buses. MeatballCraft's
  // immutable 0.18.6 preview exposes their compatible MMCE bus stacks instead.
  ['modularmachineryaddons:blocksingularityiteminputbus@0',
    'modularmachinery:blockinputbus@0'],
  ['modularmachineryaddons:blocksingularityitemoutputbus@0',
    'modularmachinery:blockoutputbus@2'],
  // Log axis bits are stripped by ItemStack conversion; all three placed orientations
  // represent the same BOP log item used by the published preview.
  ['biomesoplenty:log_4@1', 'biomesoplenty:log_4@5'],
  ['biomesoplenty:log_4@9', 'biomesoplenty:log_4@5'],
]);
const MEATBALL_DIRECT_CATALOG_KEYS = new Map([
  // Forge 1.12 fluid blocks expose their filled universal-bucket item in JEI.
  ['twilightforest:molten_fierymetal', 'item|forge:bucketfilled:fierymetal;'],
  ['contenttweaker:ichorium_molten', 'item|forge:bucketfilled:ichorium;'],
  ['mysticalagradditions:molten_inferium', 'item|forge:bucketfilled:inferium;'],
  ['mysticalagradditions:molten_prudentium', 'item|forge:bucketfilled:prudentium;'],
  ['mysticalagradditions:molten_intermedium', 'item|forge:bucketfilled:intermedium;'],
  ['mysticalagradditions:molten_superium', 'item|forge:bucketfilled:superium;'],
  ['mysticalagradditions:molten_supremium', 'item|forge:bucketfilled:supremium;'],
  ['contenttweaker:molten_insanium', 'item|forge:bucketfilled:molten_insanium;'],
  ['contenttweaker:molten_defined', 'item|forge:bucketfilled:molten_defined;'],
  ['contenttweaker:rhenium_molten', 'item|forge:bucketfilled:rhenium;'],
  ['contenttweaker:actualizing_fluid', 'item|forge:bucketfilled:actualizing_fluid;'],
  ['contenttweaker:molten_vibranium', 'item|forge:bucketfilled:vibranium;'],
  ['contenttweaker:vengeful_steel_molten', 'item|forge:bucketfilled:vengeful_steel;'],
  ['enderio:block_fluid_stellar_alloy', 'item|forge:bucketfilled:stellar_alloy;'],
  ['nuclearcraft:fluid_plasma', 'item|forge:bucketfilled:plasma;'],
  ['bloodmagic:life_essence', 'item|forge:bucketfilled:lifeessence;'],
  ['twilightforest:molten_knightmetal', 'item|forge:bucketfilled:knightmetal;'],
  ['tconstruct:molten_manyullyn', 'item|forge:bucketfilled:manyullyn;'],
  ['tconstruct:molten_cobalt', 'item|forge:bucketfilled:cobalt;'],
  ['tconstruct:molten_ardite', 'item|forge:bucketfilled:ardite;'],
]);

const {stdout: actualCommitOutput} = await promisify(execFile)(
  'git',
  ['-C', packRoot, 'rev-parse', 'HEAD'],
  {encoding: 'utf8'},
);
const actualCommit = actualCommitOutput.trim();
if (actualCommit !== source.commit) {
  throw new Error(`Expected ${source.label} commit ${source.commit}, received ${actualCommit}.`);
}

const itemFiles = (await readdir(itemsDirectory))
  .filter(file => /^part-\d+\.json$/u.test(file))
  .sort();
if (itemFiles.length === 0) throw new Error('No item catalog shards were provided.');
const itemParts = await Promise.all(
  itemFiles.map(file => readFile(join(itemsDirectory, file), 'utf8').then(JSON.parse)),
);
const items = itemParts.flat();
const itemsById = new Map();
const itemsByKey = new Map();
for (const item of items) {
  const candidates = itemsById.get(item.id) ?? [];
  candidates.push(item);
  itemsById.set(item.id, candidates);
  itemsByKey.set(item.k, item);
}
const recipes = JSON.parse(await readFile(recipesPath, 'utf8'));
if (!Array.isArray(recipes)) throw new Error('Structure recipes must be an array.');
const recipeRelationKeys = new Set(
  recipes.flatMap(recipe =>
    [...(recipe.in ?? []), ...(recipe.out ?? [])]
      .flatMap(slot => slot ?? [])
      .map(alternative => alternative?.[0])
      .filter(key => typeof key === 'string'),
  ),
);

function onlyCatalogKey(id, context) {
  const candidates = itemsById.get(id) ?? [];
  if (candidates.length === 1) return candidates[0].k;
  const plain = candidates.find(item => item.k === `item|${id}`);
  if (plain) return plain.k;
  const usedByStructureRecipe = candidates.filter(item => recipeRelationKeys.has(item.k));
  if (usedByStructureRecipe.length === 1) return usedByStructureRecipe[0].k;
  throw new Error(
    `${context} cannot resolve ${id} to one catalog item; candidates=` +
      candidates.map(item => item.k).join(', '),
  );
}

function finishStructure(requestedController, rawCells) {
  const positions = new Map();
  for (const cell of rawCells) positions.set(`${cell[0]}:${cell[1]}:${cell[2]}`, cell);
  const cells = [...positions.values()].sort(
    (a, b) => a[1] - b[1] || a[2] - b[2] || a[0] - b[0] || a[3].localeCompare(b[3]),
  );
  if (cells.length === 0) throw new Error('A generated structure contains no occupied cells.');
  const counts = new Map();
  for (const cell of cells) counts.set(cell[3], (counts.get(cell[3]) ?? 0) + 1);
  let controller = requestedController;
  if (!counts.has(controller)) {
    const originKey = positions.get('0:0:0')?.[3];
    if (
      typeof originKey !== 'string' ||
      !originKey.startsWith('item|modularmachinery:blockcontroller')
    ) {
      throw new Error('A generated structure does not contain its requested controller.');
    }
    // A few MMCE definitions (notably the TARDIS) explicitly place the legacy controller
    // variant at the origin. That placed block is what JEI renders and counts for this layout.
    controller = originKey;
  }
  const xs = cells.map(cell => cell[0]);
  const ys = cells.map(cell => cell[1]);
  const zs = cells.map(cell => cell[2]);
  return {
    size: [
      Math.max(...xs) - Math.min(...xs) + 1,
      Math.max(...ys) - Math.min(...ys) + 1,
      Math.max(...zs) - Math.min(...zs) + 1,
    ],
    total: cells.length,
    controller,
    blocks: [...counts],
    cells,
  };
}

function recipeCounts(recipe) {
  const counts = new Map();
  for (const slot of recipe.in ?? []) {
    const first = slot?.[0];
    if (!Array.isArray(first) || typeof first[0] !== 'string' || !Number.isSafeInteger(first[1])) {
      continue;
    }
    counts.set(first[0], (counts.get(first[0]) ?? 0) + first[1]);
  }
  return counts;
}

function countSignature(counts) {
  return [...counts]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, count]) => `${key}=${count}`)
    .join('\n');
}

function actualDirection(relative, facing = 'NORTH') {
  if (relative === 'UP' || relative === 'DOWN') return relative;
  const horizontal = ['NORTH', 'EAST', 'SOUTH', 'WEST'];
  const index = horizontal.indexOf(facing);
  if (index < 0) throw new Error(`Unsupported horizontal facing ${facing}.`);
  if (relative === 'FRONT') return facing;
  if (relative === 'BACK') return horizontal[(index + 2) % 4];
  if (relative === 'LEFT') return horizontal[(index + 3) % 4];
  if (relative === 'RIGHT') return horizontal[(index + 1) % 4];
  throw new Error(`Unsupported Multiblocked relative direction ${relative}.`);
}

function transformMultiblockedPosition(values, directions) {
  const result = [0, 0, 0];
  for (let axis = 0; axis < 3; axis += 1) {
    const value = values[axis];
    switch (actualDirection(directions[axis])) {
      case 'UP': result[1] = value; break;
      case 'DOWN': result[1] = -value; break;
      case 'WEST': result[0] = -value; break;
      case 'EAST': result[0] = value; break;
      case 'NORTH': result[2] = -value; break;
      case 'SOUTH': result[2] = value; break;
      default: throw new Error('Unreachable Multiblocked direction.');
    }
  }
  return result;
}

function predicateCandidate(predicate, context) {
  if (predicate.type === 'blocks') {
    const first = predicate.blocks?.[0]?.id;
    if (MM2_UNREGISTERED_PREVIEW_BLOCKS.has(first) && !itemsById.has(first)) return null;
    if (typeof first !== 'string') return null;
    const file = context.slice('MM2 '.length);
    const id = MM2_PUBLISHED_BLOCK_ALIASES.get(`${file}|${first}`) ??
      MM2_PUBLISHED_BLOCK_ALIASES.get(`*|${first}`) ?? first;
    return onlyCatalogKey(id, context);
  }
  if (predicate.type === 'component') {
    return onlyCatalogKey(predicate.location, context);
  }
  if (predicate.type === 'any' || predicate.type === 'air' || predicate.type === 'capability') {
    return null;
  }
  throw new Error(`${context} contains unsupported predicate type ${predicate.type}.`);
}

function selectMultiblockedPredicate(names, predicates, counts, context) {
  const resolved = names.map(name => {
    const predicate = predicates[name];
    if (!predicate) throw new Error(`${context} references missing predicate ${name}.`);
    return {name, predicate};
  });
  const limited = resolved.filter(
    ({predicate}) => predicate.minCount !== undefined || predicate.maxCount !== undefined,
  );
  const common = resolved.filter(
    ({predicate}) => predicate.minCount === undefined && predicate.maxCount === undefined,
  );
  for (const entry of limited) {
    const {predicate} = entry;
    const current = counts.get(entry.name) ?? 0;
    if (predicate.minCount === undefined && predicate.previewCount === undefined) continue;
    if (predicate.previewCount !== undefined && current < predicate.previewCount) {
      counts.set(entry.name, current + 1);
      return entry;
    }
    if ((predicate.minCount ?? -1) > 0 && current < predicate.minCount) {
      counts.set(entry.name, current + 1);
      return entry;
    }
  }
  for (const entry of common) {
    const previewCount = entry.predicate.previewCount;
    if ((previewCount ?? -1) <= 0) continue;
    const current = counts.get(entry.name) ?? 0;
    if (current < previewCount) {
      counts.set(entry.name, current + 1);
      return entry;
    }
  }
  const unrestricted = common.find(({predicate}) => predicate.previewCount === undefined);
  if (unrestricted) return unrestricted;
  for (const entry of limited) {
    const {predicate} = entry;
    if (predicate.previewCount !== undefined) continue;
    const current = counts.get(entry.name) ?? 0;
    if (predicate.maxCount !== undefined && current >= predicate.maxCount) continue;
    if (predicate.maxCount !== undefined) counts.set(entry.name, current + 1);
    return entry;
  }
  return null;
}

async function generateMm2() {
  const definitionsRoot = join(packRoot, 'multiblocked', 'definition', 'controller');
  const definitionFiles = (await readdir(definitionsRoot))
    .filter(file => file.endsWith('.json'))
    .sort();
  const generated = [];
  for (const file of definitionFiles) {
    const context = `MM2 ${basename(file)}`;
    const definition = JSON.parse(await readFile(join(definitionsRoot, file), 'utf8'));
    const pattern = definition.basePattern;
    if (!pattern || typeof definition.location !== 'string') {
      throw new Error(`${context} has no controller location or base pattern.`);
    }
    const repetitions = pattern.aisleRepetitions.map(range => range[0]);
    const globalCounts = new Map();
    const cells = [];
    let expandedAisle = 0;
    for (let aisle = 0; aisle < pattern.pattern.length; aisle += 1) {
      for (let repeat = 0; repeat < repetitions[aisle]; repeat += 1) {
        for (let y = 0; y < pattern.pattern[aisle].length; y += 1) {
          const row = pattern.pattern[aisle][y];
          for (let character = 0; character < row.length; character += 1) {
            const symbol = row[character];
            const names = pattern.symbolMap[symbol] ?? ['any'];
            const selected = selectMultiblockedPredicate(
              names,
              pattern.predicates,
              globalCounts,
              context,
            );
            if (!selected) continue;
            const key = predicateCandidate(selected.predicate, context);
            if (!key) continue;
            const [x, cellY, z] = transformMultiblockedPosition(
              [character, y, expandedAisle],
              pattern.structureDir,
            );
            cells.push([x, cellY, z, key]);
          }
        }
        expandedAisle += 1;
      }
    }
    const controller = onlyCatalogKey(definition.location, context);
    generated.push({name: definition.location, structure: finishStructure(controller, cells)});
  }

  const recipeIndexesBySignature = new Map();
  recipes.forEach((recipe, index) => {
    const signature = countSignature(recipeCounts(recipe));
    const indexes = recipeIndexesBySignature.get(signature) ?? [];
    indexes.push(index);
    recipeIndexesBySignature.set(signature, indexes);
  });
  const lookup = {};
  const unmatched = [];
  const usedRecipeIndexes = new Set();
  generated.forEach((entry, structureIndex) => {
    const signature = countSignature(new Map(entry.structure.blocks));
    const candidates = (recipeIndexesBySignature.get(signature) ?? []).filter(
      index => !usedRecipeIndexes.has(index),
    );
    if (candidates.length !== 1) {
      const controllerRecipe = recipes.findIndex(recipe => recipeCounts(recipe).has(entry.structure.controller));
      const expected = controllerRecipe >= 0 ? countSignature(recipeCounts(recipes[controllerRecipe])) : 'none';
      unmatched.push(
        `${entry.name} candidates=[${candidates.join(',')}] recipe=${controllerRecipe}` +
          `\ngenerated:\n${signature}\npublished:\n${expected}`,
      );
      return;
    }
    lookup[String(candidates[0])] = structureIndex;
    usedRecipeIndexes.add(candidates[0]);
  });
  if (unmatched.length > 0) {
    throw new Error(`MM2 structures did not match immutable preview recipes:\n${unmatched.join('\n')}`);
  }
  return {generated, lookup};
}

function firstElement(value, variables) {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (typeof candidate !== 'string') throw new Error(`Invalid MMCE machine element ${candidate}.`);
  if (Object.hasOwn(variables, candidate)) return firstElement(variables[candidate], variables);
  return candidate;
}

function meatballCatalogKey(element, variables, context) {
  const original = firstElement(element, variables);
  const resolved = MEATBALL_MMCE_BLOCK_ALIASES.get(original) ?? original;
  const separator = resolved.lastIndexOf('@');
  const id = separator >= 0 ? resolved.slice(0, separator) : resolved;
  const meta = separator >= 0 ? Number(resolved.slice(separator + 1)) : 0;
  if (!Number.isSafeInteger(meta) || meta < 0) {
    throw new Error(`${context} contains invalid block metadata in ${resolved}.`);
  }
  if (id === 'minecraft:air') return null;
  const candidates = itemsById.get(id) ?? [];
  const directKey = MEATBALL_DIRECT_CATALOG_KEYS.get(id);
  if (directKey) {
    if (!itemsByKey.has(directKey)) throw new Error(`${context} is missing ${directKey}.`);
    return directKey;
  }
  if (id === 'enderio:block_fluid_ender_distillation') {
    const bucket = itemsById
      .get('enderio:bucketfilled')
      ?.find(item => item.k === 'item|forge:bucketfilled:ender_distillation;');
    if (!bucket) throw new Error(`${context} cannot resolve the Dew of the Void bucket.`);
    return bucket.k;
  }
  const exactPrefix = `item|${id}:${meta}`;
  const exact =
    candidates.find(item => item.k === exactPrefix) ??
    candidates.find(item => item.k === `${exactPrefix}:basic`) ??
    (candidates.filter(item => item.k.startsWith(`${exactPrefix}:`)).length === 1
      ? candidates.find(item => item.k.startsWith(`${exactPrefix}:`))
      : undefined);
  const normalized = [meta & 7, meta & 3]
    .filter((value, index, values) => value !== meta && values.indexOf(value) === index)
    .map(value => candidates.find(item => item.k === `item|${id}:${value}`))
    .find(Boolean);
  const metadataFree = meta === 0 ? candidates.find(item => item.k === `item|${id}`) : undefined;
  const selected =
    exact ?? normalized ?? metadataFree ?? (candidates.length === 1 ? candidates[0] : undefined);
  if (!selected) {
    throw new Error(
      `${context} element ${resolved} cannot be matched to one catalog key; candidates=` +
        candidates.map(item => item.k).join(', '),
    );
  }
  return selected.k;
}

function coordinates(value, context, axis) {
  const values = Array.isArray(value) ? value : [value];
  if (values.length === 0 || !values.every(Number.isSafeInteger)) {
    throw new Error(`${context} has invalid ${axis} coordinates.`);
  }
  return values;
}

async function generateMeatballcraft() {
  const machineryRoot = join(packRoot, 'config', 'modularmachinery', 'machinery');
  const variables = JSON.parse(
    await readFile(join(machineryRoot, 'variables', 'casings.var.json'), 'utf8'),
  );
  const recipeByMachine = new Map();
  recipes.forEach((recipe, index) => {
    for (const slot of recipe.out ?? []) {
      for (const alternative of slot ?? []) {
        const key = alternative?.[0];
        const prefix = 'item|modularmachinery:itemblueprint:modularmachinery:';
        if (typeof key === 'string' && key.startsWith(prefix)) {
          recipeByMachine.set(key.slice(prefix.length), {recipe, index});
        }
      }
    }
  });
  const machineFiles = (await readdir(machineryRoot))
    .filter(file => file.endsWith('.json') && file !== 'induction_electrolyzer.json')
    .sort();
  const generated = [];
  const lookup = {};
  for (const file of machineFiles) {
    const context = `MeatballCraft ${basename(file)}`;
    const machine = JSON.parse(await readFile(join(machineryRoot, file), 'utf8'));
    if (typeof machine.registryname !== 'string' || !Array.isArray(machine.parts)) {
      throw new Error(`${context} is malformed.`);
    }
    const relation = recipeByMachine.get(machine.registryname);
    if (!relation) throw new Error(`${context} has no immutable structure-preview recipe.`);
    const outputKeys = (relation.recipe.out ?? [])
      .flatMap(slot => slot ?? [])
      .map(alternative => alternative?.[0])
      .filter(key => typeof key === 'string');
    const controller = outputKeys.find(key => {
      const item = itemsByKey.get(key);
      return item?.id?.startsWith('modularmachinery:') && item.id.endsWith('_controller');
    });
    if (!controller) throw new Error(`${context} has no generated MMCE controller output.`);
    const cells = [[0, 0, 0, controller]];
    for (const part of machine.parts) {
      const key = meatballCatalogKey(part.elements, variables, context);
      if (!key) continue;
      for (const x of coordinates(part.x, context, 'x')) {
        for (const y of coordinates(part.y, context, 'y')) {
          for (const z of coordinates(part.z, context, 'z')) cells.push([x, y, z, key]);
        }
      }
    }
    const structureIndex = generated.length;
    generated.push({name: machine.registryname, structure: finishStructure(controller, cells)});
    lookup[String(relation.index)] = structureIndex;
    for (const key of outputKeys) lookup[key] = structureIndex;
  }
  if (generated.length !== recipes.length) {
    throw new Error(
      `Generated ${generated.length} MeatballCraft structures for ${recipes.length} preview recipes.`,
    );
  }
  return {generated, lookup};
}

function packGenerated(generated, lookup) {
  const keys = [];
  const keyIndexes = new Map();
  const keyIndex = key => {
    let index = keyIndexes.get(key);
    if (index !== undefined) return index;
    index = keys.length;
    keys.push(key);
    keyIndexes.set(key, index);
    return index;
  };
  const structures = generated.map(({structure}) => [
    structure.size,
    keyIndex(structure.controller),
    structure.blocks.flatMap(([key, count]) => [keyIndex(key), count]),
    structure.cells.flatMap(([x, y, z, key]) => [x, y, z, keyIndex(key)]),
  ]);
  return {keys, structures, lookup};
}

const result = pack === 'mm2' ? await generateMm2() : await generateMeatballcraft();
const packed = packGenerated(result.generated, result.lookup);
const generatedSource = `/* Generated by scripts/generate-current-multiblock-structures.mjs.
 * Source: ${source.label}, commit ${source.commit}.
 * Loaded only for the matching immutable current dataset publication.
 */
import type {RecipeStructure} from '../types';

type PackedStructure = [
  size: [number, number, number],
  controller: number,
  blocks: number[],
  cells: number[],
];

const keys = ${JSON.stringify(packed.keys)};
const packedStructures: PackedStructure[] = ${JSON.stringify(packed.structures)};
const lookup: Record<string, number> = ${JSON.stringify(packed.lookup)};

const decoded = packedStructures.map(([size, controllerIndex, packedBlocks, packedCells]) => {
  const blocks: RecipeStructure['blocks'] = [];
  for (let index = 0; index < packedBlocks.length; index += 2) {
    blocks.push([keys[packedBlocks[index]], packedBlocks[index + 1]]);
  }
  const cells: RecipeStructure['cells'] = [];
  for (let index = 0; index < packedCells.length; index += 4) {
    cells.push([
      packedCells[index],
      packedCells[index + 1],
      packedCells[index + 2],
      keys[packedCells[index + 3]],
    ]);
  }
  return {size, total: cells.length, controller: keys[controllerIndex], blocks, cells};
});

const structures: Record<string, RecipeStructure> = Object.fromEntries(
  Object.entries(lookup).map(([key, structureIndex]) => [key, decoded[structureIndex]]),
);

export default structures;
`;
await writeFile(output, generatedSource);
const cellCount = result.generated.reduce((total, entry) => total + entry.structure.cells.length, 0);
console.log(
  `Generated ${result.generated.length} ${pack} structures (${cellCount} cells) at ${output}.`,
);
