// Generates a small fake jei-exports dataset into public/exports so the viewer
// can be tried without Minecraft. Pure node (zero deps): hand-rolled PNG encoder.
import {deflateSync} from 'node:zlib';
import {mkdirSync, writeFileSync, rmSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'exports');

// ---------------------------------------------------------------- png encoder
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function encodePng(c) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(c.w, 0);
  ihdr.writeUInt32BE(c.h, 4);
  ihdr.set([8, 6, 0, 0, 0], 8); // 8-bit RGBA
  const raw = Buffer.alloc(c.h * (1 + c.w * 4));
  for (let y = 0; y < c.h; y++) {
    raw[y * (1 + c.w * 4)] = 0; // filter: none
    c.data.copy(raw, y * (1 + c.w * 4) + 1, y * c.w * 4, (y + 1) * c.w * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, {level: 9})),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------------------------------------------------------------- tiny canvas
function canvas(w, h) {
  return {w, h, data: Buffer.alloc(w * h * 4)};
}
function hex(c) {
  return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16), 255];
}
function px(c, x, y, [r, g, b, a]) {
  if (x < 0 || y < 0 || x >= c.w || y >= c.h) return;
  const i = (y * c.w + x) * 4;
  c.data[i] = r;
  c.data[i + 1] = g;
  c.data[i + 2] = b;
  c.data[i + 3] = a;
}
function rect(c, x, y, w, h, col) {
  for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) px(c, i, j, col);
}
function frame(c, x, y, w, h, col, t = 1) {
  rect(c, x, y, w, t, col);
  rect(c, x, y + h - t, w, t, col);
  rect(c, x, y, t, h, col);
  rect(c, x + w - t, y, t, h, col);
}
function shade(col, f) {
  return [Math.min(255, col[0] * f) | 0, Math.min(255, col[1] * f) | 0, Math.min(255, col[2] * f) | 0, col[3]];
}

// minecraft-ish 32px item icon: fill + bevel + diagonal highlight
function itemIcon(colorHex) {
  const col = hex(colorHex);
  const c = canvas(32, 32);
  rect(c, 2, 2, 28, 28, col);
  rect(c, 2, 2, 28, 3, shade(col, 1.35));
  rect(c, 2, 27, 28, 3, shade(col, 0.6));
  for (let i = 0; i < 10; i++) px(c, 8 + i, 20 - i, shade(col, 1.5));
  frame(c, 2, 2, 28, 28, shade(col, 0.45));
  return c;
}

const SLOT_BG = hex('#8b8b8b');
const SLOT_DARK = hex('#373737');
const SLOT_LIGHT = hex('#ffffff');
const PANEL = hex('#c6c6c6');

function slot(c, x, y, s, fillHex) {
  rect(c, x, y, s, s, SLOT_BG);
  rect(c, x, y, s, 1, SLOT_DARK);
  rect(c, x, y, 1, s, SLOT_DARK);
  rect(c, x, y + s - 1, s, 1, SLOT_LIGHT);
  rect(c, x + s - 1, y, 1, s, SLOT_LIGHT);
  if (fillHex) {
    const col = hex(fillHex);
    rect(c, x + 3, y + 3, s - 6, s - 6, col);
    rect(c, x + 3, y + 3, s - 6, 2, shade(col, 1.3));
  }
}
function arrow(c, x, y) {
  rect(c, x, y + 5, 14, 4, SLOT_DARK);
  for (let i = 0; i < 7; i++) rect(c, x + 14 + i, y + i, 1, 14 - i * 2, SLOT_DARK);
}
function flame(c, x, y) {
  const f = hex('#d87f33');
  rect(c, x + 4, y + 6, 6, 8, f);
  rect(c, x + 5, y + 2, 4, 4, hex('#ffcc33'));
}

// scale-2 recipe images, logical sizes recorded in JSON
function craftingImage(grid, outHex) {
  const lw = 150;
  const lh = 66;
  const s = 2;
  const c = canvas(lw * s, lh * s);
  rect(c, 0, 0, lw * s, lh * s, PANEL);
  for (let r = 0; r < 3; r++)
    for (let col = 0; col < 3; col++) slot(c, (6 + col * 18) * s, (6 + r * 18) * s, 18 * s, grid[r * 3 + col]);
  arrow(c, 72 * s, 26 * s);
  slot(c, 108 * s, 18 * s, 26 * s, outHex);
  return {img: c, w: lw, h: lh};
}
function machineImage(ins, outHex, flameOrGear) {
  const lw = 130;
  const lh = 40;
  const s = 2;
  const c = canvas(lw * s, lh * s);
  rect(c, 0, 0, lw * s, lh * s, PANEL);
  ins.forEach((f, i) => slot(c, (8 + i * 20) * s, 11 * s, 18 * s, f));
  if (flameOrGear === 'flame') flame(c, (12 + ins.length * 20) * s, 12 * s);
  arrow(c, (30 + ins.length * 20) * s, 13 * s);
  slot(c, (102) * s, 11 * s, 18 * s, outHex);
  return {img: c, w: lw, h: lh};
}

// blocky mob render 128px
function mobImage(colorHex, kind) {
  const col = hex(colorHex);
  const c = canvas(128, 128);
  const dark = shade(col, 0.65);
  const light = shade(col, 1.25);
  if (kind === 'tall') {
    rect(c, 48, 14, 32, 32, light); // head
    rect(c, 50, 46, 28, 44, col); // body
    rect(c, 50, 90, 12, 24, dark); // legs
    rect(c, 66, 90, 12, 24, dark);
    rect(c, 38, 48, 12, 34, dark); // arms
    rect(c, 78, 48, 12, 34, dark);
    rect(c, 54, 24, 6, 6, [20, 20, 20, 255]); // eyes
    rect(c, 68, 24, 6, 6, [20, 20, 20, 255]);
  } else if (kind === 'quad') {
    rect(c, 28, 44, 72, 40, col); // body
    rect(c, 88, 26, 28, 28, light); // head
    rect(c, 32, 84, 12, 26, dark);
    rect(c, 52, 84, 12, 26, dark);
    rect(c, 72, 84, 12, 26, dark);
    rect(c, 88, 84, 12, 26, dark);
    rect(c, 94, 34, 5, 5, [20, 20, 20, 255]);
    rect(c, 106, 34, 5, 5, [20, 20, 20, 255]);
  } else {
    // creeper-ish pillar
    rect(c, 48, 18, 32, 32, light);
    rect(c, 50, 50, 28, 50, col);
    rect(c, 50, 100, 12, 16, dark);
    rect(c, 66, 100, 12, 16, dark);
    rect(c, 54, 28, 7, 7, [20, 20, 20, 255]);
    rect(c, 67, 28, 7, 7, [20, 20, 20, 255]);
    rect(c, 60, 36, 8, 10, [20, 20, 20, 255]);
  }
  return c;
}

// ---------------------------------------------------------------- the dataset
const ITEMS = [
  ['minecraft:oak_log', 'Oak Log', '#6b502d'],
  ['minecraft:oak_planks', 'Oak Planks', '#b8945f'],
  ['minecraft:stick', 'Stick', '#8a6d3b'],
  ['minecraft:crafting_table', 'Crafting Table', '#7d5f35'],
  ['minecraft:cobblestone', 'Cobblestone', '#7a7a7a'],
  ['minecraft:coal', 'Coal', '#2e2e2e'],
  ['minecraft:charcoal', 'Charcoal', '#463931'],
  ['minecraft:raw_iron', 'Raw Iron', '#d8a878'],
  ['minecraft:iron_ingot', 'Iron Ingot', '#d8d8d8'],
  ['minecraft:furnace', 'Furnace', '#6e6e6e'],
  ['minecraft:torch', 'Torch', '#ffd966'],
  ['minecraft:diamond', 'Diamond', '#6ee7e0'],
  ['minecraft:iron_pickaxe', 'Iron Pickaxe', '#cfd6dc'],
  ['examplemod:gear', 'Iron Gear', '#d97f4a'],
  ['examplemod:machine_frame', 'Machine Frame', '#5a8aa6'],
  ['examplemod:energized_core', 'Energized Core', '#9a5ac8'],
];
const FLUIDS = [['minecraft:water', 'Water', '#3b6dd8']];

const K = id => `item|${id}`;
const KF = id => `fluid|${id}`;
const colorOf = Object.fromEntries([...ITEMS.map(([id, , c]) => [K(id), c]), ...FLUIDS.map(([id, , c]) => [KF(id), c])]);

const slotIn = (...entries) => entries; // a slot = list of [key, amount] variants

const CATEGORIES = [
  {
    id: 'minecraft:crafting',
    title: 'Crafting',
    dir: 'recipes/minecraft_crafting',
    catalysts: [K('minecraft:crafting_table')],
    recipes: [
      {id: 'minecraft:oak_planks', in: [[ [K('minecraft:oak_log'), 1] ]], out: [[[K('minecraft:oak_planks'), 4]]], grid: [null, null, null, null, 0, null, null, null, null]},
      {id: 'minecraft:stick', in: [[[K('minecraft:oak_planks'), 1]], [[K('minecraft:oak_planks'), 1]]], out: [[[K('minecraft:stick'), 4]]], grid: [null, 0, null, null, 1, null, null, null, null]},
      {id: 'minecraft:crafting_table', in: [[[K('minecraft:oak_planks'), 1]], [[K('minecraft:oak_planks'), 1]], [[K('minecraft:oak_planks'), 1]], [[K('minecraft:oak_planks'), 1]]], out: [[[K('minecraft:crafting_table'), 1]]], grid: [0, 1, null, 2, 3, null, null, null, null]},
      {id: 'minecraft:torch', in: [[[K('minecraft:coal'), 1], [K('minecraft:charcoal'), 1]], [[K('minecraft:stick'), 1]]], out: [[[K('minecraft:torch'), 4]]], grid: [null, 0, null, null, 1, null, null, null, null]},
      {id: 'minecraft:furnace', in: Array.from({length: 8}, () => [[K('minecraft:cobblestone'), 1]]), out: [[[K('minecraft:furnace'), 1]]], grid: [0, 1, 2, 3, null, 4, 5, 6, 7]},
      {id: 'minecraft:iron_pickaxe', in: [[[K('minecraft:iron_ingot'), 1]], [[K('minecraft:iron_ingot'), 1]], [[K('minecraft:iron_ingot'), 1]], [[K('minecraft:stick'), 1]], [[K('minecraft:stick'), 1]]], out: [[[K('minecraft:iron_pickaxe'), 1]]], grid: [0, 1, 2, null, 3, null, null, 4, null]},
    ],
  },
  {
    id: 'minecraft:smelting',
    title: 'Smelting',
    dir: 'recipes/minecraft_smelting',
    catalysts: [K('minecraft:furnace')],
    machine: 'flame',
    recipes: [
      {id: 'minecraft:iron_ingot_from_smelting', in: [[[K('minecraft:raw_iron'), 1]]], out: [[[K('minecraft:iron_ingot'), 1]]]},
      {id: 'minecraft:charcoal', in: [[[K('minecraft:oak_log'), 1]]], out: [[[K('minecraft:charcoal'), 1]]]},
    ],
  },
  {
    id: 'examplemod:assembling',
    title: 'Assembling',
    dir: 'recipes/examplemod_assembling',
    catalysts: [K('examplemod:machine_frame')],
    machine: 'gear',
    recipes: [
      {id: 'examplemod:gear', in: [[[K('minecraft:iron_ingot'), 4]]], out: [[[K('examplemod:gear'), 1]]]},
      {id: 'examplemod:machine_frame', in: [[[K('minecraft:iron_ingot'), 4]], [[K('examplemod:gear'), 2]], [[KF('minecraft:water'), 1000]]], out: [[[K('examplemod:machine_frame'), 1]]]},
      {id: 'examplemod:energized_core', in: [[[K('minecraft:diamond'), 1]], [[K('examplemod:gear'), 2]]], out: [[[K('examplemod:energized_core'), 1]]]},
      {id: 'examplemod:gear_alt', in: [[[K('minecraft:cobblestone'), 8]], [[K('minecraft:coal'), 2]]], out: [[[K('examplemod:gear'), 1]]]},
    ],
  },
];

const MOBS = [
  ['minecraft:zombie', 'Zombie', '#4a7a3d', 'tall', 0.6, 1.95, 20],
  ['minecraft:skeleton', 'Skeleton', '#bdbdbd', 'tall', 0.6, 1.99, 20],
  ['minecraft:creeper', 'Creeper', '#4fa64f', 'creeper', 0.6, 1.7, 20],
  ['minecraft:cow', 'Cow', '#6e4a2f', 'quad', 0.9, 1.4, 10],
  ['minecraft:spider', 'Spider', '#3a3a3a', 'quad', 1.4, 0.9, 16],
  ['examplemod:clockwork_golem', 'Clockwork Golem', '#b08d57', 'tall', 1.4, 2.7, 100],
  ['examplemod:void_wisp', 'Void Wisp', '#7a5ac8', 'creeper', 0.5, 0.5, 8],
];

// ---------------------------------------------------------------- write it all
rmSync(OUT, {recursive: true, force: true});
mkdirSync(OUT, {recursive: true});
const write = (rel, buf) => {
  const p = join(OUT, rel);
  mkdirSync(dirname(p), {recursive: true});
  writeFileSync(p, buf);
};

const itemsJson = [];
for (const [id, name, color] of ITEMS) {
  const [ns, path] = id.split(':');
  const icon = `icons/item/${ns}/${path}.png`;
  write(icon, encodePng(itemIcon(color)));
  itemsJson.push({k: K(id), id, n: name, m: ns, icon});
}
for (const [id, name, color] of FLUIDS) {
  const [ns, path] = id.split(':');
  const icon = `icons/fluid/${ns}/${path}.png`;
  write(icon, encodePng(itemIcon(color)));
  itemsJson.push({k: KF(id), id, n: name, m: ns, t: 'fluid', icon});
}
write('items.json', Buffer.from(JSON.stringify({items: itemsJson})));

const index = {};
const addRef = (key, side, ref) => {
  index[key] ??= {p: [], u: []};
  index[key][side].push(ref);
};

const categoriesJson = [];
let totalRecipes = 0;
CATEGORIES.forEach((cat, catIdx) => {
  const recipesJson = [];
  cat.recipes.forEach((r, i) => {
    let rendered;
    if (cat.machine) {
      rendered = machineImage(r.in.map(s => colorOf[s[0][0]] ?? '#999999'), colorOf[r.out[0][0][0]], cat.machine);
    } else {
      const grid = (r.grid ?? []).map(g => (g == null ? null : colorOf[r.in[g][0][0]] ?? '#999999'));
      rendered = craftingImage(grid, colorOf[r.out[0][0][0]]);
    }
    const img = `r${i}.png`;
    write(`${cat.dir}/${img}`, encodePng(rendered.img));
    recipesJson.push({id: r.id, img, w: rendered.w, h: rendered.h, in: r.in, out: r.out});
    const inKeys = new Set(r.in.flatMap(s => s.map(v => v[0])));
    const outKeys = new Set(r.out.flatMap(s => s.map(v => v[0])));
    for (const k of inKeys) addRef(k, 'u', [catIdx, i]);
    for (const k of outKeys) addRef(k, 'p', [catIdx, i]);
    totalRecipes++;
  });
  write(`${cat.dir}/recipes.json`, Buffer.from(JSON.stringify(recipesJson)));
  categoriesJson.push({id: cat.id, title: cat.title, dir: cat.dir, count: cat.recipes.length, catalysts: cat.catalysts});
});
write('categories.json', Buffer.from(JSON.stringify({categories: categoriesJson})));
write('index.json', Buffer.from(JSON.stringify(index)));

const mobsJson = MOBS.map(([id, n, color, kind, w, h, hp]) => {
  const [ns, path] = id.split(':');
  const icon = `mobs/${ns}/${path}.png`;
  write(icon, encodePng(mobImage(color, kind)));
  return {id, n, m: ns, icon, w, h, hp, cat: 'creature'};
});
write('mobs.json', Buffer.from(JSON.stringify({mobs: mobsJson})));

write(
  'manifest.json',
  Buffer.from(
    JSON.stringify(
      {
        format: 1,
        generatedAt: new Date().toISOString(),
        durationMs: 0,
        aborted: false,
        minecraft: '1.20.1 (sample data)',
        settings: {iconScale: 2, recipeScale: 2, mobCanvas: 128},
        counts: {items: itemsJson.length, recipes: totalRecipes, categories: categoriesJson.length, mobs: mobsJson.length, failures: 0},
        mods: {minecraft: 'Minecraft', examplemod: 'Example Mod'},
      },
      null,
      2,
    ),
  ),
);
write('failures.json', Buffer.from('[]'));

console.log(`sample data written to ${OUT}`);
console.log(`  ${itemsJson.length} items, ${totalRecipes} recipes, ${mobsJson.length} mobs`);
