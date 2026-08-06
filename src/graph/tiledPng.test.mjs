import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {unzlibSync} from 'fflate';
import {
  PngRgbaRowEncoder,
  planTiledPng,
  rgbaHasColorVariation,
} from './tiledPng.ts';

const tiledPngSource = await readFile(new URL('./tiledPng.ts', import.meta.url), 'utf8');

test('tree export removes interactive graph zoom from its cloned render surface', () => {
  assert.match(tiledPngSource, /transform:\s*'none',[\s\S]*?zoom:\s*'1'/u);
});

function uint32(data, offset) {
  return (
    data[offset] * 0x1000000 +
    (data[offset + 1] << 16) +
    (data[offset + 2] << 8) +
    data[offset + 3]
  );
}

function parsePng(data) {
  assert.deepEqual([...data.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  const chunks = [];
  let offset = 8;
  while (offset < data.length) {
    const length = uint32(data, offset);
    const type = String.fromCharCode(...data.subarray(offset + 4, offset + 8));
    chunks.push({type, data: data.subarray(offset + 8, offset + 8 + length)});
    offset += length + 12;
  }
  return chunks;
}

test('plans integer-resolution tiles beyond the single-canvas dimension limit', () => {
  const plan = planTiledPng(20_000, 1_000, 3);
  assert.equal(plan.scale, 3);
  assert.equal(plan.outputWidth, 60_000);
  assert.equal(plan.outputHeight, 3_000);
  assert.ok(plan.columns > 1);
  assert.ok(plan.totalTiles > plan.columns);
  assert.ok(plan.logicalTileWidth * plan.scale <= 8_192);
});

test('reduces scale before exceeding the bounded output-pixel budget', () => {
  const plan = planTiledPng(30_000, 1_000, 3);
  assert.equal(plan.scale, 2);
  assert.equal(plan.outputWidth, 60_000);
  assert.equal(plan.outputHeight, 2_000);
});

test('fails explicitly when a graph exceeds tiled safety limits at 1x', () => {
  assert.throws(() => planTiledPng(300_000, 1, 3), /safety limit/);
  assert.throws(() => planTiledPng(0, 1, 3), /positive safe integers/);
});

test('streams stitched RGBA rows into a standards-compliant PNG', async () => {
  const encoder = new PngRgbaRowEncoder(2, 2);
  encoder.pushRow(new Uint8Array([255, 0, 0, 255, 0, 255, 0, 255]));
  encoder.pushRow(new Uint8Array([0, 0, 255, 255, 255, 255, 255, 255]));
  const png = new Uint8Array(await encoder.finish().arrayBuffer());
  const chunks = parsePng(png);
  assert.deepEqual(chunks.map(chunk => chunk.type), ['IHDR', 'IDAT', 'IDAT', 'IEND']);
  assert.equal(uint32(chunks[0].data, 0), 2);
  assert.equal(uint32(chunks[0].data, 4), 2);

  const compressed = Buffer.concat(
    chunks.filter(chunk => chunk.type === 'IDAT').map(chunk => Buffer.from(chunk.data)),
  );
  const scanlines = unzlibSync(compressed);
  assert.equal(scanlines.length, 18);
  assert.equal(scanlines[0], 1);
  assert.equal(scanlines[9], 1);
});

test('rejects incomplete or overfilled PNG row streams', () => {
  const incomplete = new PngRgbaRowEncoder(1, 2);
  incomplete.pushRow(new Uint8Array([0, 0, 0, 255]));
  assert.throws(() => incomplete.finish(), /received 1 rows/);

  const complete = new PngRgbaRowEncoder(1, 1);
  complete.pushRow(new Uint8Array([0, 0, 0, 255]));
  assert.throws(() => complete.pushRow(new Uint8Array([0, 0, 0, 255])), /too many rows|finished/);
});

test('distinguishes a blank export tile from rendered graph content', () => {
  assert.equal(
    rgbaHasColorVariation(new Uint8ClampedArray([14, 17, 22, 255, 14, 17, 22, 255])),
    false,
  );
  assert.equal(
    rgbaHasColorVariation(new Uint8ClampedArray([14, 17, 22, 255, 59, 75, 96, 255])),
    true,
  );
  assert.equal(rgbaHasColorVariation(new Uint8ClampedArray([14, 17, 22, 255])), false);
});
