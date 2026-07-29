import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PREVIEW_CATEGORY_FORMAT,
  PREVIEW_SIDECAR_FORMAT,
  isCanonicalRecipePreviewImagePath,
  previewAssetUrl,
  recipePreviewImagePath,
  requireRecipePreviewCategory,
  requireRecipePreviewManifest,
  selectRecipePreviewEntries,
} from './previewAssets.ts';

const DATASET = 'a'.repeat(64);
const ASSET_SET = 'b'.repeat(64);
const PACKS = [{
  path: 'assets/pack-000.bin',
  bytes: 50,
  sha256: 'c'.repeat(64),
  index: {
    path: 'indexes/pack-000.bin',
    bytes: 28,
    sha256: 'd'.repeat(64),
    entries: 1,
  },
}];

test('validates and versions canonical external preview coordinates', () => {
  const path = recipePreviewImagePath([12, 34, 56, 124, 62]);
  assert.equal(path, 'recipe-assets/s/012-34-56.webp');
  assert.equal(isCanonicalRecipePreviewImagePath(path), true);
  assert.equal(isCanonicalRecipePreviewImagePath('recipe-assets/s/12-34-56.webp'), false);
  assert.equal(
    previewAssetUrl(path, DATASET, ASSET_SET),
    `/dataset/previews/assets/s/012-34-56.webp?dataset=${DATASET}&preview=${ASSET_SET}`,
  );
  assert.equal(
    previewAssetUrl(path, DATASET, ASSET_SET, 'https://native.example/previews/'),
    `https://native.example/previews/assets/s/012-34-56.webp?dataset=${DATASET}&preview=${ASSET_SET}`,
  );
});

test('rejects inconsistent preview manifests', () => {
  const manifest = {
    format: PREVIEW_SIDECAR_FORMAT,
    assetSetId: ASSET_SET,
    datasetPublicationId: DATASET,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-recipe-preview-pack-index-v1',
    maxPackIndexBytes: 512 * 1024,
    imageFormat: 'lossless-webp',
    settings: {
      itemIconPixels: 48,
      recipeScale: 2,
      webpEffort: 4,
      maxCategoryBytes: 256 * 1024,
    },
    counts: {
      categories: 1,
      recipes: 2,
      previews: 1,
      missing: 1,
      uniqueImages: 1,
      duplicates: 0,
      packs: 1,
      inputBytes: 100,
      hostedOmittedPngBytes: 75,
      encodedBytes: 50,
      storedBytes: 50,
      packIndexBytes: 28,
    },
    packs: PACKS,
  };
  assert.equal(requireRecipePreviewManifest(manifest, DATASET).assetSetId, ASSET_SET);
  const legacyCounts = {...manifest.counts, hostedOmittedWebpBytes: 70};
  delete legacyCounts.hostedOmittedPngBytes;
  assert.equal(
    requireRecipePreviewManifest({...manifest, counts: legacyCounts}, DATASET).assetSetId,
    ASSET_SET,
  );
  assert.throws(
    () =>
      requireRecipePreviewManifest(
        {
          ...manifest,
          counts: {...manifest.counts, hostedOmittedWebpBytes: 70},
        },
        DATASET,
      ),
    /exactly one current PNG or legacy WebP omission byte count/,
  );
  assert.throws(
    () => requireRecipePreviewManifest({...manifest, datasetPublicationId: 'd'.repeat(64)}, DATASET),
    /sidecar contract/,
  );
  assert.throws(
    () => requireRecipePreviewManifest({...manifest, counts: {...manifest.counts, missing: 0}}, DATASET),
    /inconsistent/,
  );
  assert.throws(
    () =>
      requireRecipePreviewManifest(
        {...manifest, settings: {...manifest.settings, itemIconPixels: 47}},
        DATASET,
      ),
    /sidecar contract/,
  );
  assert.throws(
    () =>
      requireRecipePreviewManifest(
        {...manifest, settings: {...manifest.settings, recipeScale: 0}},
        DATASET,
      ),
    /sidecar contract/,
  );
});

test('validates inline and sharded category mappings', () => {
  const inline = requireRecipePreviewCategory(
    {
      format: PREVIEW_CATEGORY_FORMAT,
      categoryIndex: 0,
      categoryId: 'minecraft.crafting',
      count: 2,
      previews: [[0, 0, 50, 124, 62], null],
    },
    0,
    'minecraft.crafting',
    2,
    'category 0',
    PACKS,
  );
  assert.equal(inline.previews?.length, 2);

  const sharded = requireRecipePreviewCategory(
    {
      format: PREVIEW_CATEGORY_FORMAT,
      categoryIndex: 4,
      categoryId: 'minecraft.anvil',
      count: 2,
      parts: [{path: 'categories/004/part-000.json', start: 0, count: 2, bytes: 10}],
    },
    4,
    'minecraft.anvil',
    2,
    'category 4',
    PACKS,
  );
  assert.equal(sharded.parts?.[0].start, 0);

  const fourDigitSharded = requireRecipePreviewCategory(
    {
      format: PREVIEW_CATEGORY_FORMAT,
      categoryIndex: 1000,
      categoryId: 'example.large',
      count: 1,
      parts: [{path: 'categories/1000/part-000.json', start: 0, count: 1, bytes: 10}],
    },
    1000,
    'example.large',
    1,
    'category 1000',
    PACKS,
  );
  assert.equal(fourDigitSharded.parts?.[0].path, 'categories/1000/part-000.json');

  const manyParts = Array.from({length: 1001}, (_, partIndex) => ({
    path: `categories/1000/part-${String(partIndex).padStart(3, '0')}.json`,
    start: partIndex,
    count: 1,
    bytes: 10,
  }));
  const fourDigitPart = requireRecipePreviewCategory(
    {
      format: PREVIEW_CATEGORY_FORMAT,
      categoryIndex: 1000,
      categoryId: 'example.many_parts',
      count: manyParts.length,
      parts: manyParts,
    },
    1000,
    'example.many_parts',
    manyParts.length,
    'category 1000 with many parts',
    PACKS,
  );
  assert.equal(fourDigitPart.parts?.[1000].path, 'categories/1000/part-1000.json');

  for (const path of [
    'categories/0000/part-000.json',
    'categories/0123/part-000.json',
    'categories/1000/part-0000.json',
  ]) {
    assert.throws(
      () =>
        requireRecipePreviewCategory(
          {
            format: PREVIEW_CATEGORY_FORMAT,
            categoryIndex: 1000,
            categoryId: 'example.large',
            count: 1,
            parts: [{path, start: 0, count: 1, bytes: 10}],
          },
          1000,
          'example.large',
          1,
          'category 1000',
          PACKS,
        ),
      /part 0 is invalid/,
      path,
    );
  }
  assert.throws(
    () =>
      requireRecipePreviewCategory(
        {
          format: PREVIEW_CATEGORY_FORMAT,
          categoryIndex: 4,
          categoryId: 'minecraft.anvil',
          count: 2,
          parts: [{path: 'categories/005/part-000.json', start: 0, count: 2, bytes: 10}],
        },
        4,
        'minecraft.anvil',
        2,
        'category 4',
        PACKS,
      ),
    /part 0 is invalid/,
  );
  assert.throws(
    () =>
      requireRecipePreviewCategory(
        {
          format: PREVIEW_CATEGORY_FORMAT,
          categoryIndex: 0,
          categoryId: 'minecraft.crafting',
          count: 1,
          previews: [[0, 40, 20, 124, 62]],
        },
        0,
        'minecraft.crafting',
        1,
        'category 0',
        PACKS,
      ),
    /outside the preview pack/,
  );
});

test('selects inline, sharded, duplicate, and missing preview entries lazily', async () => {
  const coordinate = [0, 0, 50, 124, 62];
  const inline = requireRecipePreviewCategory(
    {
      format: PREVIEW_CATEGORY_FORMAT,
      categoryIndex: 0,
      categoryId: 'minecraft.crafting',
      count: 3,
      previews: [coordinate, coordinate, null],
    },
    0,
    'minecraft.crafting',
    3,
    'category 0',
    PACKS,
  );
  const inlineSelection = await selectRecipePreviewEntries(
    inline,
    new Set([1, 2]),
    async () => {
      throw new Error('inline mappings must not request a part');
    },
  );
  assert.deepEqual(inlineSelection.get(1), coordinate);
  assert.equal(inlineSelection.get(2), null);

  const sharded = requireRecipePreviewCategory(
    {
      format: PREVIEW_CATEGORY_FORMAT,
      categoryIndex: 4,
      categoryId: 'minecraft.anvil',
      count: 2,
      parts: [{path: 'categories/004/part-000.json', start: 0, count: 2, bytes: 10}],
    },
    4,
    'minecraft.anvil',
    2,
    'category 4',
    PACKS,
  );
  const loaded = [];
  const shardedSelection = await selectRecipePreviewEntries(
    sharded,
    new Set([1]),
    async part => {
      loaded.push(part.path);
      return [null, coordinate];
    },
  );
  assert.deepEqual(loaded, ['categories/004/part-000.json']);
  assert.deepEqual(shardedSelection.get(1), coordinate);
});
