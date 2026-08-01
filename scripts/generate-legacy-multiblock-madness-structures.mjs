#!/usr/bin/env node
import {readFile, readdir, writeFile} from 'node:fs/promises';
import {basename, join, resolve} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';

function argument(name) {
  const index = process.argv.indexOf(name);
  if (index < 0 || index + 1 >= process.argv.length) {
    throw new Error(`Missing required ${name} argument.`);
  }
  return resolve(process.argv[index + 1]);
}

const packRoot = argument('--pack-root');
const firstItems = argument('--items-0');
const secondItems = argument('--items-1');
const output = argument('--output');
const expectedCommit = '5ec9f34c766866adaff6adbfc69b8a0f7b39ca72';
const machineryRoot = join(packRoot, 'config', 'modularmachinery', 'machinery');
const {stdout: actualCommitOutput} = await promisify(execFile)(
  'git',
  ['-C', packRoot, 'rev-parse', 'HEAD'],
  {encoding: 'utf8'},
);
const actualCommit = actualCommitOutput.trim();
if (actualCommit !== expectedCommit) {
  throw new Error(`Expected Multiblock Madness ${expectedCommit}, received ${actualCommit}.`);
}

const [items0, items1, variables] = await Promise.all([
  readFile(firstItems, 'utf8').then(JSON.parse),
  readFile(secondItems, 'utf8').then(JSON.parse),
  readFile(join(machineryRoot, 'variables', 'casings.var.json'), 'utf8').then(JSON.parse),
]);
const itemsById = new Map();
for (const item of [...items0, ...items1]) {
  const list = itemsById.get(item.id) ?? [];
  list.push(item);
  itemsById.set(item.id, list);
}

function firstElement(value) {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (typeof candidate !== 'string') throw new Error(`Invalid machine element ${candidate}.`);
  if (Object.hasOwn(variables, candidate)) return firstElement(variables[candidate]);
  return candidate;
}

function catalogKey(element, source) {
  const resolved = firstElement(element);
  const separator = resolved.lastIndexOf('@');
  const id = separator >= 0 ? resolved.slice(0, separator) : resolved;
  const meta = separator >= 0 ? Number(resolved.slice(separator + 1)) : 0;
  if (!Number.isSafeInteger(meta) || meta < 0) {
    throw new Error(`${source} contains invalid block metadata in ${resolved}.`);
  }
  const candidates = itemsById.get(id) ?? [];
  if (id === 'enderio:block_fluid_ender_distillation') {
    const bucket = itemsById
      .get('enderio:bucketfilled')
      ?.find(item => item.k === 'item|forge:bucketfilled:ender_distillation;');
    if (!bucket) throw new Error(`${source} cannot resolve the Dew of the Void bucket.`);
    return bucket.k;
  }
  const exactPrefix = `item|${id}:${meta}`;
  const exact =
    candidates.find(item => item.k === exactPrefix) ??
    candidates.find(item => item.k === `${exactPrefix}:basic`) ??
    (candidates.filter(item => item.k.startsWith(`${exactPrefix}:`)).length === 1
      ? candidates.find(item => item.k.startsWith(`${exactPrefix}:`))
      : undefined);
  // Several vanilla 1.12 blocks encode placement/orientation in upper metadata bits while their
  // picked ItemStack keeps only the variant bits (stone slabs are the relevant pack case).
  const normalized = [meta & 7, meta & 3]
    .filter((value, index, values) => value !== meta && values.indexOf(value) === index)
    .map(value => candidates.find(item => item.k === `item|${id}:${value}`))
    .find(Boolean);
  const metadataFree = meta === 0 ? candidates.find(item => item.k === `item|${id}`) : undefined;
  const selected = exact ?? normalized ?? metadataFree ?? (candidates.length === 1 ? candidates[0] : undefined);
  if (!selected) {
    throw new Error(
      `${source} element ${resolved} cannot be matched to one catalog key; candidates=` +
        candidates.map(item => item.k).join(', '),
    );
  }
  return selected.k;
}

function coordinates(value, source, axis) {
  const values = Array.isArray(value) ? value : [value];
  if (values.length === 0 || !values.every(Number.isSafeInteger)) {
    throw new Error(`${source} has invalid ${axis} coordinates.`);
  }
  return values;
}

const structures = {};
const machineFiles = (await readdir(machineryRoot))
  .filter(file => file.endsWith('.json'))
  .sort();
for (const file of machineFiles) {
  const source = `Multiblock Madness ${basename(file)}`;
  const machine = JSON.parse(await readFile(join(machineryRoot, file), 'utf8'));
  if (typeof machine.registryname !== 'string' || !Array.isArray(machine.parts)) {
    throw new Error(`${source} is malformed.`);
  }
  const positions = new Map();
  for (const part of machine.parts) {
    const key = catalogKey(part.elements, source);
    for (const x of coordinates(part.x, source, 'x')) {
      for (const y of coordinates(part.y, source, 'y')) {
        for (const z of coordinates(part.z, source, 'z')) {
          positions.set(`${x}:${y}:${z}`, [x, y, z, key]);
        }
      }
    }
  }
  const controller = catalogKey('modularmachinery:blockcontroller', source);
  positions.set('0:0:0', [0, 0, 0, controller]);
  const cells = [...positions.values()].sort(
    (a, b) => a[1] - b[1] || a[2] - b[2] || a[0] - b[0] || a[3].localeCompare(b[3]),
  );
  const counts = new Map([[controller, 0]]);
  for (const cell of cells) counts.set(cell[3], (counts.get(cell[3]) ?? 0) + 1);
  const xs = cells.map(cell => cell[0]);
  const ys = cells.map(cell => cell[1]);
  const zs = cells.map(cell => cell[2]);
  const structure = {
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
  structures[
    `item|modularmachinery:itemblueprint:modularmachinery:${machine.registryname}`
  ] = structure;
}

const source = `/* This file is generated by scripts/generate-legacy-multiblock-madness-structures.mjs.
 * Source: Filostorm/Multiblock-Madness commit ${expectedCommit} (MIT), pack version 3.2.3.
 * It is lazy-loaded only for the matching immutable legacy dataset publication.
 */
import type {RecipeStructure} from '../types';

const structures: Record<string, RecipeStructure> = ${JSON.stringify(structures)};

export default structures;
`;
await writeFile(output, source);
console.log(`Generated ${Object.keys(structures).length} legacy multiblock structures at ${output}.`);
