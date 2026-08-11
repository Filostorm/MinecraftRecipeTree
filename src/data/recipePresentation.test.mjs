import assert from 'node:assert/strict';
import test from 'node:test';
import {
  recipeHasStructurePreview,
  recipeNeedsLayoutPreviewUnavailableNotice,
  recipePresentationKind,
} from './recipePresentation.ts';

test('distinguishes structured recipes from explicit exporter failures', () => {
  assert.equal(recipePresentationKind({err: true}), 'failure');
  assert.equal(recipePresentationKind({img: 'assets/s/000-0-128.webp'}), 'image');
  assert.equal(recipePresentationKind({}), 'structured');
  assert.equal(recipePresentationKind({img: ''}), 'structured');
});

test('treats exported multiblock geometry as the complete recipe presentation', () => {
  const structure = {
    size: [3, 2, 3],
    total: 2,
    controller: 'mod|controller',
    blocks: [['mod|controller', 1], ['mod|casing', 1]],
    cells: [[0, 0, 0, 'mod|controller'], [1, 0, 0, 'mod|casing']],
  };

  assert.equal(recipeHasStructurePreview({structure}), true);
  assert.equal(recipeNeedsLayoutPreviewUnavailableNotice({structure}), false);
  assert.equal(recipeNeedsLayoutPreviewUnavailableNotice({}), true);
  assert.equal(recipeNeedsLayoutPreviewUnavailableNotice({img: 'recipe.webp'}), false);
});
