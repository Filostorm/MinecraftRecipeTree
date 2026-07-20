import assert from 'node:assert/strict';
import {mkdtemp, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import sharp from 'sharp';
import {
  configureMultiblockExportFixture,
  createRawExportFixture,
  readJson,
  writeJson,
  writeTransparentImage,
  writeUniformVisibleImage,
} from './test-export-fixture.mjs';
import {validateExportData} from './validate-export-data.mjs';
import {MULTIBLOCK_MADNESS_112_PROFILE} from './export-quality-policy.mjs';

async function withFixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-validator-test-'));
  try {
    await createRawExportFixture(root);
    await run(root);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

test('strict manifest metadata accepts the complete exporter contract', async () => {
  await withFixture(async root => {
    const summary = await validateExportData(root, {assetMode: 'raw'});
    assert.equal(summary.items, 1);
    assert.equal(summary.categories, 1);
    assert.equal(summary.recipes, 0);
    assert.equal(summary.mobs, 0);
  });
});

test('MM1 validation requires and reconciles the complete warnings.json audit', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-mm1-warnings-test-'));
  try {
    await createRawExportFixture(root, {iconScale: 1, recipeScale: 2});
    const manifest = await configureMultiblockExportFixture(
      root,
      MULTIBLOCK_MADNESS_112_PROFILE,
    );
    const warning =
      'ZERO_ABSENT_ALTERNATIVE recipe output nuclearcraft_centrifuge #0 slot 3';
    manifest.diagnostics.warningEvents = 1;
    await writeJson(join(root, 'manifest.json'), manifest);
    await writeJson(join(root, 'warnings.json'), [warning]);

    const summary = await validateExportData(root, {
      assetMode: 'raw',
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
    });
    assert.equal(summary.warnings, 1);

    await writeJson(join(root, 'warnings.json'), [
      'UNREVIEWED_WARNING future exporter behavior',
    ]);
    await assert.rejects(
      validateExportData(root, {
        assetMode: 'raw',
        profile: MULTIBLOCK_MADNESS_112_PROFILE,
      }),
      /unrecognized warning class/,
    );

    await rm(join(root, 'warnings.json'));
    await assert.rejects(
      validateExportData(root, {
        assetMode: 'raw',
        profile: MULTIBLOCK_MADNESS_112_PROFILE,
      }),
      /requires warnings\.json to contain an array/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('recipe slots accept one shared 1.12 OreDictionary identity on every alternative', async () => {
  await withFixture(async root => {
    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    manifest.counts.recipes = 1;
    await writeJson(manifestPath, manifest);

    const categoriesPath = join(root, 'categories.json');
    const categories = await readJson(categoriesPath);
    categories.categories[0].count = 1;
    await writeJson(categoriesPath, categories);

    await writeJson(join(root, 'recipes', 'minecraft_crafting', 'recipes.json'), [
      {
        in: [
          [
            ['minecraft:stone', 1, 'ore:stone'],
            ['minecraft:stone', 1, 'ore:stone'],
          ],
        ],
        out: [[['minecraft:stone', 1]]],
      },
    ]);
    await writeJson(join(root, 'index.json'), {
      'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
    });

    const summary = await validateExportData(root, {assetMode: 'raw'});
    assert.equal(summary.recipes, 1);
  });
});

test('recipe slots reject partial logical identities instead of silently merging variants', async () => {
  await withFixture(async root => {
    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    manifest.counts.recipes = 1;
    await writeJson(manifestPath, manifest);

    const categoriesPath = join(root, 'categories.json');
    const categories = await readJson(categoriesPath);
    categories.categories[0].count = 1;
    await writeJson(categoriesPath, categories);

    await writeJson(join(root, 'recipes', 'minecraft_crafting', 'recipes.json'), [
      {
        in: [
          [
            ['minecraft:stone', 1, 'ore:stone'],
            ['minecraft:stone', 1],
          ],
        ],
        out: [[['minecraft:stone', 1]]],
      },
    ]);
    await writeJson(join(root, 'index.json'), {
      'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
    });

    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      /must use one logical ingredient id for every variant/,
    );
  });
});

test('raw image validation accepts uniform visible assets and rejects fully transparent assets', async () => {
  await withFixture(async root => {
    const iconPath = join(root, 'icons', 'stone.png');
    await writeUniformVisibleImage(iconPath);
    await validateExportData(root, {assetMode: 'raw'});

    await writeTransparentImage(iconPath);
    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      /Raw image icons\/stone\.png is fully transparent/,
    );
  });
});

test('raw image validation rejects content whose encoding does not match its extension', async () => {
  await withFixture(async root => {
    const iconPath = join(root, 'icons', 'stone.png');
    const disguisedWebp = await sharp({
      create: {width: 16, height: 16, channels: 4, background: '#446688ff'},
    })
      .webp({lossless: true})
      .toBuffer();
    await writeFile(iconPath, disguisedWebp);

    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      /icons\/stone\.png has webp content behind its png filename/,
    );
  });
});

test('strict manifest metadata requires generatedAt and finite non-negative durationMs', async () => {
  await withFixture(async root => {
    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    delete manifest.generatedAt;
    manifest.durationMs = -1;
    await writeJson(manifestPath, manifest);
    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      error => {
        assert.match(error.message, /manifest\.generatedAt/);
        assert.match(error.message, /manifest\.durationMs/);
        return true;
      },
    );
  });
});

test('strict manifest core counts are required and must match validated data', async () => {
  await withFixture(async root => {
    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    delete manifest.counts.categories;
    manifest.counts.items = 2;
    await writeJson(manifestPath, manifest);
    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      error => {
        assert.match(error.message, /manifest\.counts\.categories must be/);
        assert.match(error.message, /manifest\.counts\.items is 2 but validated 1/);
        return true;
      },
    );
  });
});

test('strict manifest render settings and mod names match the runtime contract', async () => {
  await withFixture(async root => {
    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    manifest.settings.recipeScale = 0;
    manifest.settings.mobCanvas = 1.5;
    manifest.mods.minecraft = null;
    await writeJson(manifestPath, manifest);
    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      error => {
        assert.match(error.message, /manifest\.settings\.recipeScale/);
        assert.match(error.message, /manifest\.settings\.mobCanvas/);
        assert.match(error.message, /manifest\.mods values must all be strings/);
        return true;
      },
    );
  });
});

test('final publication validation requires a lowercase SHA-256 publicationId', async () => {
  await withFixture(async root => {
    await assert.rejects(
      validateExportData(root, {assetMode: 'raw', requirePublicationId: true}),
      /manifest\.publicationId is required/,
    );

    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    manifest.publicationId = 'NOT-A-SHA-256-DIGEST';
    await writeJson(manifestPath, manifest);
    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      /manifest\.publicationId must be a lowercase hexadecimal SHA-256 digest/,
    );
  });
});
