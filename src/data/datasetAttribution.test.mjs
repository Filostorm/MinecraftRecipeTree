import assert from 'node:assert/strict';
import test from 'node:test';
import {
  GTNH_1710_DATASET_PROFILE,
  GTNH_DATASET_ATTRIBUTION,
  GTNH_PACK_NAME,
  GTNH_PACK_VERSION,
  GTNH_STRUCTURED_DATA_ONLY_POLICY,
  GTNH_VISUAL_ASSETS_POLICY,
  isExactGtnhDatasetAttribution,
  isExactGtnhVisualAssetsPolicy,
  loadedDatasetAttribution,
} from './datasetAttribution.ts';

const attribution = GTNH_DATASET_ATTRIBUTION;

test('GTNH licensing is resolved from the loaded manifest profile and attribution', () => {
  const resolved = loadedDatasetAttribution({
    profile: GTNH_1710_DATASET_PROFILE,
    pack: {
      name: GTNH_PACK_NAME,
      version: GTNH_PACK_VERSION,
      identitySource: 'explicit-request',
    },
    publicationPolicy: GTNH_STRUCTURED_DATA_ONLY_POLICY,
    web: {visualAssets: GTNH_VISUAL_ASSETS_POLICY},
    attribution,
  });
  assert.deepEqual(resolved, {
    profile: GTNH_1710_DATASET_PROFILE,
    packName: 'GT New Horizons',
    packVersion: '2.8.4',
    publicationPolicy: GTNH_STRUCTURED_DATA_ONLY_POLICY,
    visualMode: 'structured-data-only',
    attribution,
  });
});

test('GTNH licensing identifies runtime-rendered export visuals without a legacy policy', () => {
  const resolved = loadedDatasetAttribution({
    profile: GTNH_1710_DATASET_PROFILE,
    pack: {
      name: GTNH_PACK_NAME,
      version: GTNH_PACK_VERSION,
      identitySource: 'explicit-request',
    },
    attribution,
  });
  assert.deepEqual(resolved, {
    profile: GTNH_1710_DATASET_PROFILE,
    packName: 'GT New Horizons',
    packVersion: '2.8.4',
    publicationPolicy: null,
    visualMode: 'runtime-rendered-export',
    attribution,
  });
});

test('mutable catalog identity cannot independently enable a licensing notice', () => {
  assert.equal(loadedDatasetAttribution(undefined), null);
  assert.equal(loadedDatasetAttribution({profile: 'meatballcraft-1.12.2'}), null);
});

test('a GTNH manifest without its integrity-bound licensing fields fails visibly', () => {
  assert.throws(
    () => loadedDatasetAttribution({profile: GTNH_1710_DATASET_PROFILE}),
    /does not match its exact pack, visual-publication, and attribution contract/,
  );
});

test('GTNH attribution is an exact immutable contract, not merely an HTTPS-shaped object', () => {
  assert.equal(isExactGtnhDatasetAttribution(attribution), true);
  assert.equal(
    isExactGtnhDatasetAttribution({...attribution, sourceUrl: 'https://example.com/lookalike'}),
    false,
  );
  assert.throws(
    () =>
      loadedDatasetAttribution({
        profile: GTNH_1710_DATASET_PROFILE,
        pack: {
          name: GTNH_PACK_NAME,
          version: GTNH_PACK_VERSION,
          identitySource: 'explicit-request',
        },
        publicationPolicy: GTNH_STRUCTURED_DATA_ONLY_POLICY,
        web: {visualAssets: GTNH_VISUAL_ASSETS_POLICY},
        attribution: {...attribution, licenseIdentifier: 'Unreviewed license'},
      }),
    /does not match its exact pack, visual-publication, and attribution contract/,
  );
});

test('GTNH structured-data-only visual policy has an exact immutable shape', () => {
  assert.equal(isExactGtnhVisualAssetsPolicy(GTNH_VISUAL_ASSETS_POLICY), true);
  assert.equal(
    isExactGtnhVisualAssetsPolicy({...GTNH_VISUAL_ASSETS_POLICY, recipePreviews: 1}),
    false,
  );
  assert.equal(
    isExactGtnhVisualAssetsPolicy({...GTNH_VISUAL_ASSETS_POLICY, unexpected: 0}),
    false,
  );
});

test('GTNH attribution notice rejects missing or drifted data-only policy', () => {
  const base = {
    profile: GTNH_1710_DATASET_PROFILE,
    pack: {
      name: GTNH_PACK_NAME,
      version: GTNH_PACK_VERSION,
      identitySource: 'explicit-request',
    },
    attribution,
    web: {visualAssets: GTNH_VISUAL_ASSETS_POLICY},
  };
  assert.throws(
    () => loadedDatasetAttribution(base),
    /visual-publication/,
  );
  assert.throws(
    () => loadedDatasetAttribution({
      ...base,
      publicationPolicy: GTNH_STRUCTURED_DATA_ONLY_POLICY,
      web: {visualAssets: {...GTNH_VISUAL_ASSETS_POLICY, itemIcons: 1}},
    }),
    /visual-publication/,
  );
});
