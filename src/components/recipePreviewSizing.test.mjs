import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {
  RECIPE_CARD_HORIZONTAL_CHROME,
  recipeCardImageMaxWidth,
  responsiveRecipePreviewSize,
} from './recipePreviewSizing.ts';

// ItemDetailModal uses 16px backdrop padding, then a 1px border and 14px
// padding on each side. These are the actual list widths reported by onLayout.
const measuredListWidth = viewportWidth =>
  Math.min(760, viewportWidth - 2 * 16) - 2 * (1 + 14);

const itemDetailSource = await readFile(
  new URL('./ItemDetailModal.tsx', import.meta.url),
  'utf8',
);
const recipeCardSource = await readFile(new URL('./RecipeCard.tsx', import.meta.url), 'utf8');

test('item-detail recipe cards open the first exported JEI machine catalyst', () => {
  assert.match(itemDetailSource, /const machineKey = category\.catalysts\[0\]/u);
  assert.match(
    itemDetailSource,
    /<RecipeCard[\s\S]*?machineKey=\{machineKey\}[\s\S]*?machineLabel=\{machineLabel\}/u,
  );
  assert.match(recipeCardSource, />View machine recipe<\/Text>/u);
  assert.match(recipeCardSource, /openItem\(machineKey\)/u);
});

test('390px mobile keeps the Machine Frame preview physical-size and contains wider layouts', () => {
  const availableCardWidth = measuredListWidth(390);
  assert.equal(availableCardWidth, 328);
  assert.equal(RECIPE_CARD_HORIZONTAL_CHROME, 22);
  assert.equal(recipeCardImageMaxWidth(availableCardWidth), 306);
  assert.deepEqual(responsiveRecipePreviewSize(124, 62, 2, availableCardWidth), {
    width: 248,
    height: 124,
    scale: 2,
    mode: 'physical',
  });
  assert.deepEqual(responsiveRecipePreviewSize(174, 108, 2, availableCardWidth), {
    width: 174,
    height: 108,
    scale: 1,
    mode: 'integer-downscale',
  });
});

test('375px mobile retains integer scaling before the fractional last resort', () => {
  const availableCardWidth = measuredListWidth(375);
  assert.equal(availableCardWidth, 313);
  assert.equal(recipeCardImageMaxWidth(availableCardWidth), 291);
  assert.deepEqual(responsiveRecipePreviewSize(154, 77, 2, availableCardWidth), {
    width: 154,
    height: 77,
    scale: 1,
    mode: 'integer-downscale',
  });
  assert.deepEqual(responsiveRecipePreviewSize(300, 100, 2, availableCardWidth), {
    width: 291,
    height: 97,
    scale: 0.97,
    mode: 'fractional-downscale',
  });
});

test('desktop uses natural physical pixels without the previous 360px ceiling', () => {
  const availableCardWidth = measuredListWidth(1440);
  assert.equal(availableCardWidth, 730);
  assert.equal(recipeCardImageMaxWidth(availableCardWidth), 708);
  assert.deepEqual(responsiveRecipePreviewSize(192, 228, 2, availableCardWidth), {
    width: 384,
    height: 456,
    scale: 2,
    mode: 'physical',
  });
  assert.deepEqual(responsiveRecipePreviewSize(348, 100, 2, availableCardWidth), {
    width: 696,
    height: 200,
    scale: 2,
    mode: 'physical',
  });
});

test('applies recipe and item zoom to previews without overflowing the card', () => {
  const mobileWidth = measuredListWidth(390);
  assert.deepEqual(responsiveRecipePreviewSize(124, 62, 2, mobileWidth, 0.75), {
    width: 186,
    height: 93,
    scale: 1.5,
    mode: 'fractional-downscale',
  });
  assert.deepEqual(responsiveRecipePreviewSize(124, 62, 2, mobileWidth, 1.5), {
    width: 306,
    height: 153,
    scale: 306 / 124,
    mode: 'fractional-upscale',
  });

  const desktopWidth = measuredListWidth(1440);
  assert.deepEqual(responsiveRecipePreviewSize(192, 228, 2, desktopWidth, 1.5), {
    width: 576,
    height: 684,
    scale: 3,
    mode: 'integer-upscale',
  });
});

test('rejects invalid layout inputs rather than silently inventing a size', () => {
  assert.throws(() => recipeCardImageMaxWidth(0), /positive finite/);
  assert.throws(() => responsiveRecipePreviewSize(124, 62, 0, 328), /positive values/);
  assert.throws(() => responsiveRecipePreviewSize(124, 62, 2, 328, 0), /positive values/);
});
