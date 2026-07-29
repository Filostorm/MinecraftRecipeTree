import assert from 'node:assert/strict';
import test from 'node:test';
import {
  datasetMountKey,
  datasetSource,
  readRequestedDatasetSlug,
  requireDatasetCatalog,
  searchWithDatasetSlug,
  selectDataset,
} from './datasetCatalog.ts';
import {datasetPackIconPath} from './datasetPresentation.ts';

const PUBLICATION_A = 'a'.repeat(64);
const PUBLICATION_B = 'b'.repeat(64);
const PREVIEW_A = 'c'.repeat(64);
const PREVIEW_B = 'd'.repeat(64);

const meatball = {
  slug: 'meatballcraft',
  displayName: 'MeatballCraft',
  minecraftVersion: '1.12.2',
  packVersion: '0.16.7-hotfix',
  publicationId: PUBLICATION_A,
  previewAssetSetId: PREVIEW_A,
  isDefault: true,
};
const madness = {
  slug: 'multiblock-madness',
  displayName: 'Multiblock Madness',
  minecraftVersion: '1.12.2',
  packVersion: '3.2.2',
  publicationId: PUBLICATION_B,
  previewAssetSetId: PREVIEW_B,
  isDefault: false,
};

test('validates an exact catalog and selects only the requested or declared default pack', () => {
  const datasets = requireDatasetCatalog({datasets: [meatball, madness]});
  assert.equal(selectDataset(datasets, null).slug, 'meatballcraft');
  assert.equal(selectDataset(datasets, 'multiblock-madness').packVersion, '3.2.2');
  assert.throws(
    () => selectDataset(datasets, 'missing-pack'),
    /Unknown dataset slug "missing-pack".*meatballcraft, multiblock-madness/,
  );
});

test('rejects catalog contract drift, ambiguous defaults, and repeated identities', () => {
  assert.throws(
    () => requireDatasetCatalog({datasets: [{...meatball, unexpected: true}, madness]}),
    /exact immutable dataset contract/,
  );
  assert.throws(
    () => requireDatasetCatalog({datasets: [meatball, {...madness, isDefault: true}]}),
    /exactly one default/,
  );
  assert.throws(
    () =>
      requireDatasetCatalog({
        datasets: [meatball, {...madness, publicationId: meatball.publicationId}],
      }),
    /repeats publicationId/,
  );
  assert.throws(
    () => requireDatasetCatalog({datasets: [meatball], extra: []}),
    /exact object/,
  );
});

test('identity bounds count Unicode code points and reject invisible formatting controls', () => {
  assert.equal(
    requireDatasetCatalog({
      datasets: [{...meatball, displayName: '🧱'.repeat(120)}],
    })[0].displayName,
    '🧱'.repeat(120),
  );
  assert.throws(
    () => requireDatasetCatalog({
      datasets: [{...meatball, displayName: '🧱'.repeat(121)}],
    }),
    /exact immutable dataset contract/,
  );
  assert.throws(
    () => requireDatasetCatalog({
      datasets: [{...meatball, displayName: 'Invisible\u200bName'}],
    }),
    /exact immutable dataset contract/,
  );
});

test('builds immutable core and preview routes only from validated content identities', () => {
  assert.deepEqual(datasetSource(madness), {
    descriptor: madness,
    base: `/dataset/publications/${PUBLICATION_B}/exports`,
    previewBase: `/dataset/preview-sets/${PREVIEW_B}`,
  });
  assert.deepEqual(datasetSource(madness, 'https://recipes.example.test'), {
    descriptor: madness,
    base: `https://recipes.example.test/dataset/publications/${PUBLICATION_B}/exports`,
    previewBase: `https://recipes.example.test/dataset/preview-sets/${PREVIEW_B}`,
  });
  assert.throws(
    () => datasetSource({...madness, publicationId: '../mutable'}),
    /valid publicationId/,
  );
  assert.throws(
    () => datasetSource(madness, 'https://recipes.example.test/subpath'),
    /absolute HTTP\(S\) origin/,
  );
});

test('pack query parsing is canonical and never silently maps an invalid slug to default', () => {
  assert.equal(readRequestedDatasetSlug('?tab=items'), null);
  assert.equal(
    readRequestedDatasetSlug('?tab=items&pack=multiblock-madness'),
    'multiblock-madness',
  );
  assert.throws(
    () => readRequestedDatasetSlug('?pack=meatballcraft&pack=multiblock-madness'),
    /exactly one canonical dataset slug/,
  );
  assert.throws(
    () => readRequestedDatasetSlug('?pack=Multiblock%20Madness'),
    /canonical dataset slug/,
  );
});

test('pack query writes preserve unrelated state and mount keys change with either identity', () => {
  assert.equal(
    searchWithDatasetSlug('?tab=graph&pack=meatballcraft', 'multiblock-madness'),
    '?tab=graph&pack=multiblock-madness',
  );
  assert.notEqual(datasetMountKey(meatball), datasetMountKey(madness));
  assert.notEqual(
    datasetMountKey(madness),
    datasetMountKey({...madness, previewAssetSetId: 'e'.repeat(64)}),
  );
});

test('maps every published modpack to a first-party picker icon', () => {
  assert.equal(datasetPackIconPath('meatballcraft'), '/pack-icons/meatballcraft.webp?v=1');
  assert.equal(datasetPackIconPath('gt-new-horizons'), '/pack-icons/gt-new-horizons.webp?v=1');
  assert.equal(
    datasetPackIconPath('multiblock-madness'),
    '/pack-icons/multiblock-madness.webp?v=1',
  );
  assert.equal(
    datasetPackIconPath('multiblock-madness-2'),
    '/pack-icons/multiblock-madness-2.webp?v=1',
  );
  assert.equal(datasetPackIconPath('unconfigured-pack'), null);
});
