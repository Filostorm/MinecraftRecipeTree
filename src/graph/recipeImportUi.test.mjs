import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const appSource = await readFile(new URL('../../App.tsx', import.meta.url), 'utf8');
const switcherSource = await readFile(
  new URL('../components/DatasetSwitcher.tsx', import.meta.url),
  'utf8',
);
const graphSource = await readFile(new URL('./GraphScreen.tsx', import.meta.url), 'utf8');
const modalSource = await readFile(new URL('./TreeShareModal.tsx', import.meta.url), 'utf8');
const detailsSource = await readFile(
  new URL('./RecipeImportDetailsModal.tsx', import.meta.url),
  'utf8',
);
const dropZoneSource = await readFile(new URL('./PortableTreeDropZone.tsx', import.meta.url), 'utf8');

test('the dedicated import dropdown opens the crafting-tree JSON flow', () => {
  assert.doesNotMatch(appSource, /accessibilityLabel="Import a crafting tree from JSON"/u);
  assert.match(switcherSource, />Import pack<\/Text>/u);
  assert.match(switcherSource, />Import crafting tree<\/Text>/u);
  assert.match(switcherSource, /accessibilityLabel=\{showImportMenu \? 'Close import menu' : 'Open import menu'\}/u);
  assert.match(switcherSource, /onImportTree\?\.\(\)/u);
  assert.doesNotMatch(appSource, /Drop or paste Recipe Tree JSON/u);
  assert.match(appSource, /setRecipeImportRequestId\(value => value \+ 1\)/u);
  assert.match(graphSource, /recipeImportRequestId/u);
  assert.match(graphSource, /setTreeTransferMode\('import'\)/u);
  assert.match(graphSource, /onRecipeImportRequestHandled\?\.\(\)/u);
  assert.match(appSource, /onRecipeImportRequestHandled=\{\(\) => setRecipeImportRequestId\(0\)\}/u);
  assert.doesNotMatch(
    graphSource,
    /setShowTreeShare\(true\);\s*onRecipeImportRequestHandled\?\.\(\)/u,
  );
  assert.match(graphSource, /setShowTreeShare\(false\);\s*onRecipeImportRequestHandled\?\.\(\)/u);
  assert.match(graphSource, /onClose=\{closeTreeShare\}/u);
  assert.match(modalSource, /mode === 'import' \? 'Import crafting tree'/u);
});

test('crafting-tree import supports file drops and pasted JSON without a clipboard prompt', () => {
  assert.match(dropZoneSource, /onDrop=/u);
  assert.match(dropZoneSource, /file\.text\(\)/u);
  assert.match(modalSource, /multiline/u);
  assert.doesNotMatch(appSource, /clipboard\.readText\(\)/u);
  assert.doesNotMatch(modalSource, /clipboard\.readText\(\)/u);
  assert.doesNotMatch(modalSource, /Copied a crafting tree/u);
  assert.doesNotMatch(modalSource, />Check clipboard<\/Text>/u);
});

test('imports return to graph immediately and persist each reconstructed selection', () => {
  assert.match(graphSource, /onRecipeImportStart\(raw\);\s*setTab\('graph'\);\s*restoreGraph/u);
  assert.match(appSource, /recipeImportJob=\{recipeImportJob\}/u);
  assert.match(graphSource, /restorePortableTreeIncrementally\(recipeImportJob\.raw, root\)/u);
  assert.match(graphSource, /restoredSelections\.push\(stored\);\s*saveProgress\(\)/u);
  assert.match(graphSource, /await expandRecipe\(node, stored\.source\.ref/u);
  assert.doesNotMatch(graphSource, /persistGraphSessionSnapshot\(data\.descriptor, session\)/u);
});

test('legacy hosted recipes resolve through their deterministic semantic identity', () => {
  assert.match(graphSource, /await portableRecipeMatchesKey\(category\.id, recipe/u);
});

test('partial imports report skipped branches instead of restoring orphaned selections', () => {
  assert.match(graphSource, /resolveConnectedPortableSelections\(/u);
  assert.match(graphSource, /A shared recipe tree was opened partially\./u);
  assert.match(graphSource, /A saved graph was reconstructed partially\./u);
  assert.match(graphSource, /Partial import\./u);
  assert.match(graphSource, /Open details/u);
  assert.match(graphSource, /onRecipeImportReportChange/u);
  assert.match(graphSource, /Dismiss partial import notice/u);
  assert.match(detailsSource, /Partial import details/u);
  assert.match(detailsSource, /Tree path:/u);
  assert.match(appSource, /recipeImportNotice=\{recipeImportNotice\}/u);
  assert.match(appSource, /onRecipeImportNoticeChange=\{setRecipeImportNotice\}/u);
  assert.match(appSource, /recipeImportReport=\{recipeImportReport\}/u);
  assert.match(appSource, /onRecipeImportReportChange=\{setRecipeImportReport\}/u);
});
