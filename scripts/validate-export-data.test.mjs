import assert from 'node:assert/strict';
import {mkdir, mkdtemp, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import sharp from 'sharp';
import {
  configureMultiblockExportFixture,
  createRawExportFixture,
  multiblockMadness2Warnings,
  readJson,
  writeJson,
  writeTransparentImage,
  writeUniformVisibleImage,
} from './test-export-fixture.mjs';
import {validateExportData} from './validate-export-data.mjs';
import {
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS,
  MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
} from './export-quality-policy.mjs';
import {createRecipeImageInventory} from './recipe-image-inventory.mjs';
import {
  GTNH_RECIPE_IMAGE_OMISSION_REASON,
  GTNH_STRUCTURED_DATA_ONLY_POLICY_ID,
  GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS,
} from './visual-assets-rights-policy.mjs';

async function withFixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-validator-test-'));
  try {
    await createRawExportFixture(root);
    await run(root);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

async function configureStructuredDataOnlyFixture(root) {
  const itemsPath = join(root, 'items.json');
  const items = await readJson(itemsPath);
  delete items.items[0].icon;
  await writeJson(itemsPath, items);

  const categoriesPath = join(root, 'categories.json');
  const categories = await readJson(categoriesPath);
  delete categories.categories[0].icon;
  await writeJson(categoriesPath, categories);

  await rm(join(root, 'icons'), {recursive: true, force: false});
  await rm(join(root, 'recipes', 'minecraft_crafting', 'icon.png'));

  const manifestPath = join(root, 'manifest.json');
  const manifest = await readJson(manifestPath);
  manifest.publicationPolicy = GTNH_STRUCTURED_DATA_ONLY_POLICY_ID;
  manifest.web = {
    format: 2,
    shardedJson: 'mrt-sharded-json-v1',
    maxShardBytes: 8 * 1024 * 1024,
    visualAssets: {...GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS},
    recipeImages: {
      mode: 'omitted',
      reason: GTNH_RECIPE_IMAGE_OMISSION_REASON,
      policy: GTNH_STRUCTURED_DATA_ONLY_POLICY_ID,
      references: 0,
      files: 0,
      encoding: 'png',
      bytes: 0,
      inventory: createRecipeImageInventory().finish(),
    },
  };
  await writeJson(manifestPath, manifest);
}

async function configureSingleStoneRecipe(root, recipe, {format} = {}) {
  const manifestPath = join(root, 'manifest.json');
  const manifest = await readJson(manifestPath);
  if (format !== undefined) manifest.format = format;
  manifest.counts.recipes = 1;
  await writeJson(manifestPath, manifest);

  const categoriesPath = join(root, 'categories.json');
  const categories = await readJson(categoriesPath);
  categories.categories[0].count = 1;
  await writeJson(categoriesPath, categories);

  await writeJson(join(root, 'recipes', 'minecraft_crafting', 'recipes.json'), [recipe]);
  await writeJson(join(root, 'index.json'), {
    'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
  });
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

test('structured-data-only validation accepts the exact zero-visual publication contract', async () => {
  await withFixture(async root => {
    await configureStructuredDataOnlyFixture(root);
    const summary = await validateExportData(root);
    assert.equal(summary.imageReferences, 0);
    assert.equal(summary.packedAssets, 0);
  });
});

test('structured-data-only validation fails closed on visual fields, coordinates, rasters, and packs', async () => {
  await withFixture(async root => {
    await configureStructuredDataOnlyFixture(root);

    const itemsPath = join(root, 'items.json');
    const items = await readJson(itemsPath);
    items.items[0].icon = 'assets/s/000-0-10.webp';
    await writeJson(itemsPath, items);
    await assert.rejects(
      validateExportData(root),
      error => {
        assert.match(error.message, /items\[0\]\.icon is forbidden/);
        assert.match(error.message, /forbids packed coordinates/);
        return true;
      },
    );
    delete items.items[0].icon;
    await writeJson(itemsPath, items);

    await writeFile(join(root, 'recipes', 'surviving.webp'), Buffer.from([1]));
    await assert.rejects(validateExportData(root), /forbids every raster file/);
    await rm(join(root, 'recipes', 'surviving.webp'));

    await mkdir(join(root, 'assets'));
    await writeFile(join(root, 'assets', 'pack-000.bin'), Buffer.from([1]));
    await assert.rejects(validateExportData(root), /forbids packed-image files/);
  });
});

test('structured-data-only validation rejects every catalog, category, recipe, and mob visual field', async () => {
  await withFixture(async root => {
    await configureStructuredDataOnlyFixture(root);

    const itemsPath = join(root, 'items.json');
    const items = await readJson(itemsPath);
    items.items[0].icon = 'icons/stone.png';
    await writeJson(itemsPath, items);

    const categoriesPath = join(root, 'categories.json');
    const categories = await readJson(categoriesPath);
    categories.categories[0].icon = 'recipes/minecraft_crafting/icon.png';
    categories.categories[0].count = 1;
    await writeJson(categoriesPath, categories);

    await writeJson(join(root, 'recipes', 'minecraft_crafting', 'recipes.json'), [
      {
        img: 'assets/s/000-0-10.webp',
        w: 16,
        h: 16,
        in: [[['minecraft:stone', 1]]],
        out: [[['minecraft:stone', 1]]],
      },
    ]);
    await writeJson(join(root, 'index.json'), {
      'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
    });
    await writeJson(join(root, 'mobs.json'), {
      mobs: [{id: 'minecraft:zombie', icon: 'mobs/zombie.png', frames: 4, fps: 8}],
    });
    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    manifest.counts.recipes = 1;
    manifest.counts.mobs = 1;
    manifest.web.recipeImages.references = 1;
    manifest.web.recipeImages.files = 1;
    // The malformed inventory is intentionally left in place: the validator must
    // report every surviving visual field even when exclusion accounting also fails.
    await writeJson(manifestPath, manifest);

    await assert.rejects(
      validateExportData(root),
      error => {
        for (const pattern of [
          /items\[0\]\.icon is forbidden/,
          /categories\[0\]\.icon is forbidden/,
          /recipe 0\.img is forbidden/,
          /recipe 0\.w is forbidden/,
          /recipe 0\.h is forbidden/,
          /mobs\[0\]\.icon is forbidden/,
          /mobs\[0\]\.frames is forbidden/,
          /mobs\[0\]\.fps is forbidden/,
        ]) {
          assert.match(error.message, pattern);
        }
        return true;
      },
    );
  });
});

test('structured-data-only validation requires the exact manifest policy object', async () => {
  await withFixture(async root => {
    await configureStructuredDataOnlyFixture(root);
    const manifestPath = join(root, 'manifest.json');
    const manifest = await readJson(manifestPath);
    manifest.web.visualAssets.itemIcons = 1;
    await writeJson(manifestPath, manifest);
    await assert.rejects(
      validateExportData(root),
      /must form the exact supported structured-data-only contract/,
    );
  });
});

test('MM1 validation requires and reconciles the complete warnings.json audit', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-mm1-warnings-test-'));
  try {
    await createRawExportFixture(root, {iconScale: 3, recipeScale: 2});
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

test('MM2 validation permits isolated magenta but rejects both canonical missingno phases', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-mm2-icon-quality-test-'));
  try {
    await createRawExportFixture(root, {iconScale: 3, recipeScale: 2});
    const manifest = await configureMultiblockExportFixture(
      root,
      MULTIBLOCK_MADNESS_2_118_PROFILE,
    );

    const summary = await validateExportData(root, {
      assetMode: 'raw',
      profile: MULTIBLOCK_MADNESS_2_118_PROFILE,
    });
    assert.equal(summary.items, 1 + MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS.length);
    assert.equal(
      summary.warnings,
      MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS.length +
        MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS.length,
    );

    manifest.counts.nativeIconCorrections = 2;
    manifest.diagnostics.nativeIconCorrections = 2;
    await writeJson(join(root, 'manifest.json'), manifest);
    const mixedCorrectionWarnings = multiblockMadness2Warnings(2);
    await writeJson(join(root, 'warnings.json'), mixedCorrectionWarnings);
    const mixedCorrectionSummary = await validateExportData(root, {
      assetMode: 'raw',
      profile: MULTIBLOCK_MADNESS_2_118_PROFILE,
    });
    assert.equal(
      mixedCorrectionSummary.warnings,
      MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS.length +
        MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS.length +
        2,
    );

    await writeJson(join(root, 'warnings.json'), [
      ...mixedCorrectionWarnings.slice(0, -1),
      'Canonicalized animated native REI catalog icon to its first declared frame: ' +
        'entry=fixture:near_miss',
    ]);
    await assert.rejects(
      validateExportData(root, {
        assetMode: 'raw',
        profile: MULTIBLOCK_MADNESS_2_118_PROFILE,
      }),
      /unrecognized warning class/,
    );
    await writeJson(join(root, 'warnings.json'), mixedCorrectionWarnings);

    const iconSize = 48;
    const isolatedMagenta = Buffer.alloc(iconSize * iconSize * 4);
    for (let offset = 0; offset < isolatedMagenta.length; offset += 4) {
      isolatedMagenta[offset] = 24;
      isolatedMagenta[offset + 1] = 48;
      isolatedMagenta[offset + 2] = 72;
      isolatedMagenta[offset + 3] = 255;
    }
    for (const pixelIndex of [0, (iconSize * iconSize) / 2, iconSize * iconSize - 1]) {
      const offset = pixelIndex * 4;
      isolatedMagenta[offset] = 248;
      isolatedMagenta[offset + 1] = 0;
      isolatedMagenta[offset + 2] = 248;
    }
    await sharp(isolatedMagenta, {
      raw: {width: iconSize, height: iconSize, channels: 4},
    })
      .png()
      .toFile(join(root, 'icons', 'stone.png'));
    const isolatedSummary = await validateExportData(root, {
      assetMode: 'raw',
      profile: MULTIBLOCK_MADNESS_2_118_PROFILE,
    });
    assert.equal(
      isolatedSummary.items,
      1 + MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS.length,
    );

    for (const [inverted, expectedRgbaSha256] of [
      [false, '386c06ecfb9a401bf0abe1d92a04a23a06a424dd39e33df2c7dcae19bc6ec31a'],
      [true, '1e76e32423f2e15298af791e370c640b8e62dd7e36efc1cee260ba1430432b44'],
    ]) {
      const missingno = Buffer.alloc(iconSize * iconSize * 4);
      for (let y = 0; y < iconSize; y += 1) {
        for (let x = 0; x < iconSize; x += 1) {
          const offset = (y * iconSize + x) * 4;
          const magenta =
            ((x < iconSize / 2) !== (y < iconSize / 2)) !== inverted;
          missingno[offset] = magenta ? 248 : 0;
          missingno[offset + 1] = 0;
          missingno[offset + 2] = magenta ? 248 : 0;
          missingno[offset + 3] = 255;
        }
      }
      await sharp(missingno, {
        raw: {width: iconSize, height: iconSize, channels: 4},
      })
        .png()
        .toFile(join(root, 'icons', 'stone.png'));
      await assert.rejects(
        validateExportData(root, {
          assetMode: 'raw',
          profile: MULTIBLOCK_MADNESS_2_118_PROFILE,
        }),
        error => {
          assert.match(error.message, /canonical Minecraft missing-texture signature/);
          assert.match(error.message, new RegExp(`decoded RGBA SHA-256 ${expectedRgbaSha256}`));
          assert.match(
            error.message,
            /exact black\/magenta checker scaled from 8×8 source quadrants/,
          );
          return true;
        },
      );
    }
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

test('recipe validation accepts exact bounded multiblock geometry and counts', async () => {
  await withFixture(async root => {
    await configureSingleStoneRecipe(root, {
      in: [[['minecraft:stone', 1]]],
      out: [[['minecraft:stone', 1]]],
      structure: {
        size: [2, 1, 1],
        total: 2,
        controller: 'minecraft:stone',
        blocks: [['minecraft:stone', 2]],
        cells: [
          [0, 0, 0, 'minecraft:stone'],
          [1, 0, 0, 'minecraft:stone'],
        ],
      },
    });
    const summary = await validateExportData(root, {assetMode: 'raw'});
    assert.equal(summary.recipes, 1);
  });
});

test('recipe validation rejects multiblock count and coordinate drift', async () => {
  await withFixture(async root => {
    await configureSingleStoneRecipe(root, {
      in: [[['minecraft:stone', 1]]],
      out: [[['minecraft:stone', 1]]],
      structure: {
        size: [3, 1, 1],
        total: 2,
        controller: 'minecraft:stone',
        blocks: [['minecraft:stone', 1]],
        cells: [
          [0, 0, 0, 'minecraft:stone'],
          [0, 0, 0, 'minecraft:stone'],
        ],
      },
    });
    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      error => {
        assert.match(error.message, /must account for every position/);
        assert.match(error.message, /repeats position/);
        assert.match(error.message, /size must match/);
        return true;
      },
    );
  });
});

test('format-v2 recipe inputs and outputs accept one strict probability shared by every alternative', async () => {
  await withFixture(async root => {
    await configureSingleStoneRecipe(
      root,
      {
        in: [[['minecraft:stone', 1, null, 0.2]]],
        out: [
          [
            ['minecraft:stone', 1, null, 0.25],
            ['minecraft:stone', 1, null, 0.25],
          ],
          [['minecraft:stone', 2, 'ore:stone', 0.5]],
        ],
      },
      {format: 2},
    );

    const summary = await validateExportData(root, {assetMode: 'raw'});
    assert.equal(summary.recipes, 1);
  });
});

test('stochastic tuples reject catalysts, format-v1 manifests, invalid ranges, and partial probabilities', async () => {
  await withFixture(async root => {
    await configureSingleStoneRecipe(root, {
      in: [[['minecraft:stone', 1]]],
      cat: [[['minecraft:stone', 1, null, 0.5]]],
      out: [
        [['minecraft:stone', 1, null, 0]],
        [
          ['minecraft:stone', 1, null, 0.5],
          ['minecraft:stone', 1],
        ],
      ],
    });

    await assert.rejects(
      validateExportData(root, {assetMode: 'raw'}),
      error => {
        assert.match(error.message, /may declare an occurrence probability only in recipe\.in or recipe\.out/);
        assert.match(error.message, /stochastic-occurrence tuple introduced by manifest format 2/);
        assert.match(error.message, /optional occurrence probability/);
        assert.match(error.message, /must use one occurrence probability for every variant/);
        return true;
      },
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
