import assert from 'node:assert/strict';
import test from 'node:test';
import {GTNH_STRUCTURED_DATA_ONLY_POLICY} from './datasetAttribution.ts';
import {
  catalogVisualReferenceCounts,
  recipeVisualReferenceIndices,
  shouldFetchRecipePreviewSidecar,
} from './publicationRights.ts';

test('GTNH structured-data-only policy disables external preview-sidecar fetches', () => {
  const omitted = {web: {recipeImages: {mode: 'omitted'}}};
  assert.equal(shouldFetchRecipePreviewSidecar(omitted), true);
  assert.equal(shouldFetchRecipePreviewSidecar({
    ...omitted,
    publicationPolicy: GTNH_STRUCTURED_DATA_ONLY_POLICY,
  }), false);
});

test('visual-reference accounting treats even null/undefined own fields as policy violations', () => {
  assert.deepEqual(
    catalogVisualReferenceCounts(
      [{k: 'a'}, {k: 'b', icon: undefined}],
      [{id: 'a'}, {id: 'b', icon: null}],
      [{id: 'a'}, {id: 'b', icon: ''}],
    ),
    {itemIcons: 1, categoryIcons: 1, mobSprites: 1},
  );
  assert.deepEqual(
    recipeVisualReferenceIndices([{}, {img: undefined}, {img: 'assets/s/000-0-1.webp'}]),
    [1, 2],
  );
});
