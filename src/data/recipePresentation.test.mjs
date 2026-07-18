import assert from 'node:assert/strict';
import test from 'node:test';
import {recipePresentationKind} from './recipePresentation.ts';

test('distinguishes structured recipes from explicit exporter failures', () => {
  assert.equal(recipePresentationKind({err: true}), 'failure');
  assert.equal(recipePresentationKind({img: 'assets/s/000-0-128.webp'}), 'image');
  assert.equal(recipePresentationKind({}), 'structured');
  assert.equal(recipePresentationKind({img: ''}), 'structured');
});
