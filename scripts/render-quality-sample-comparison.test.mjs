import assert from 'node:assert/strict';
import {access, mkdir, mkdtemp, rm, unlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join} from 'node:path';
import test from 'node:test';
import sharp from 'sharp';
import {
  QUALITY_COMPARISON_LAYOUT,
  renderQualitySampleComparison,
} from './render-quality-sample-comparison.mjs';

const paths = Object.freeze({
  item: 'icons/item/thermalexpansion/frame_d5baf740.png',
  baselineCrafting: 'recipes/minecraft.crafting/r31319.png',
  sampleCrafting: 'recipes/minecraft.crafting/r0.png',
  baselineBasic: 'recipes/extendedcrafting_table_crafting_3x3/r131.png',
  sampleBasic: 'recipes/extendedcrafting_table_crafting_3x3/r0.png',
});

function checkerPixels(width, height, first = [240, 35, 65, 255], second = [25, 170, 230, 255]) {
  const pixels = Buffer.alloc(width * height * 4);
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const color = (x + y) % 2 === 0 ? first : second;
      const offset = (y * width + x) * 4;
      pixels.set(color, offset);
    }
  }
  return pixels;
}

async function writePng(path, width, height, colors) {
  await mkdir(dirname(path), {recursive: true});
  await sharp(checkerPixels(width, height, ...colors), {
    raw: {width, height, channels: 4},
  })
    .png({compressionLevel: 1, palette: false})
    .toFile(path);
}

async function createFixture(root) {
  const baseline = join(root, 'baseline');
  const sample = join(root, 'sample');
  await Promise.all([
    writePng(join(baseline, paths.item), 16, 16, []),
    writePng(join(sample, paths.item), 48, 48, [
      [245, 190, 30, 255],
      [105, 65, 220, 255],
    ]),
    writePng(join(baseline, paths.baselineCrafting), 124, 62, []),
    writePng(join(sample, paths.sampleCrafting), 248, 124, [
      [15, 120, 70, 255],
      [220, 180, 45, 255],
    ]),
    writePng(join(baseline, paths.baselineBasic), 124, 62, []),
    writePng(join(sample, paths.sampleBasic), 248, 124, [
      [100, 35, 160, 255],
      [40, 210, 180, 255],
    ]),
  ]);
  return {baseline, sample};
}

async function pathMissing(path) {
  try {
    await access(path);
    return false;
  } catch (error) {
    if (error?.code === 'ENOENT') return true;
    throw error;
  }
}

test('renders a labeled, lossless comparison with nearest-neighbor integer scaling', async () => {
  const root = await mkdtemp(join(tmpdir(), 'mrt-quality-comparison-'));
  try {
    const {baseline, sample} = await createFixture(root);
    const output = join(root, 'comparison.png');
    const result = await renderQualitySampleComparison({baseline, sample, output});
    assert.equal(result.output, output);
    assert.equal(result.sources.length, 6);
    assert.deepEqual(
      result.sources.map(source => source.displayScale),
      [9, 3, 4, 2, 4, 2],
    );

    const metadata = await sharp(output).metadata();
    assert.equal(metadata.format, 'png');
    assert.equal(metadata.width, QUALITY_COMPARISON_LAYOUT.width);
    assert.equal(metadata.height, QUALITY_COMPARISON_LAYOUT.height);

    const baselineSource = checkerPixels(16, 16);
    const expected = Buffer.alloc(144 * 144 * 4);
    for (let y = 0; y < 144; y += 1) {
      for (let x = 0; x < 144; x += 1) {
        const sourceOffset = (Math.floor(y / 9) * 16 + Math.floor(x / 9)) * 4;
        const outputOffset = (y * 144 + x) * 4;
        baselineSource.copy(expected, outputOffset, sourceOffset, sourceOffset + 4);
      }
    }
    const actual = await sharp(output)
      .extract({
        left: QUALITY_COMPARISON_LAYOUT.item.baselineX,
        top: QUALITY_COMPARISON_LAYOUT.item.y,
        width: 144,
        height: 144,
      })
      .ensureAlpha()
      .raw()
      .toBuffer();
    assert.deepEqual(actual, expected, 'baseline icon pixels must be replicated in exact 9×9 blocks');
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('fails loudly before writing when a source has the wrong dimensions or is missing', async () => {
  const root = await mkdtemp(join(tmpdir(), 'mrt-quality-comparison-invalid-'));
  try {
    const {baseline, sample} = await createFixture(root);
    const wrongDimensionOutput = join(root, 'wrong-dimension.png');
    await writePng(join(sample, paths.item), 47, 48, []);
    await assert.rejects(
      renderQualitySampleComparison({baseline, sample, output: wrongDimensionOutput}),
      /sample Machine Frame item render must be exactly 48×48; decoded 47×48/,
    );
    assert.equal(await pathMissing(wrongDimensionOutput), true);

    await writePng(join(sample, paths.item), 48, 48, []);
    await unlink(join(baseline, paths.baselineBasic));
    const missingOutput = join(root, 'missing.png');
    await assert.rejects(
      renderQualitySampleComparison({baseline, sample, output: missingOutput}),
      /baseline Basic Crafting JEI preview source #131 is missing/,
    );
    assert.equal(await pathMissing(missingOutput), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('refuses to contaminate either validated export with the generated comparison', async () => {
  const root = await mkdtemp(join(tmpdir(), 'mrt-quality-comparison-output-'));
  try {
    const {baseline, sample} = await createFixture(root);
    await assert.rejects(
      renderQualitySampleComparison({
        baseline,
        sample,
        output: join(sample, 'comparison.png'),
      }),
      /output must be outside the sample export/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
