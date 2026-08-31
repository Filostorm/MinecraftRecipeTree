import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const appSource = await readFile(new URL('../../App.tsx', import.meta.url), 'utf8');
const graphSource = await readFile(new URL('./GraphScreen.tsx', import.meta.url), 'utf8');
const modalSource = await readFile(new URL('./TreeShareModal.tsx', import.meta.url), 'utf8');
const dropZoneSource = await readFile(new URL('./PortableTreeDropZone.tsx', import.meta.url), 'utf8');

test('the information menu opens a dedicated recipe JSON import flow', () => {
  assert.match(appSource, /accessibilityLabel="Import a recipe tree from JSON"/u);
  assert.match(appSource, />Import recipe<\/Text>/u);
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
  assert.match(modalSource, /mode === 'import' \? 'Import recipe tree'/u);
});

test('recipe import supports file drops, pasted JSON, and explicit clipboard confirmation', () => {
  assert.match(dropZoneSource, /onDrop=/u);
  assert.match(dropZoneSource, /file\.text\(\)/u);
  assert.match(modalSource, /multiline/u);
  assert.match(modalSource, /clipboard\.readText\(\)/u);
  assert.match(modalSource, /onInspectImport\(value\)/u);
  assert.match(modalSource, /RECIPE TREE FOUND IN CLIPBOARD/u);
  assert.match(modalSource, /await onImport\(clipboardCandidate\.raw\)/u);
});

test('clipboard suggestions use the same pack-matched portable tree parser as imports', () => {
  assert.match(graphSource, /const share = parsePortableTree\(raw\)/u);
  assert.match(graphSource, /assertPortableTreePackMatches\(share, data\.descriptor\)/u);
  assert.match(graphSource, /data\.itemsByKey\.get\(share\.rootKey\)/u);
});

test('partial imports report skipped branches instead of restoring orphaned selections', () => {
  assert.match(graphSource, /resolveConnectedPortableSelections\(/u);
  assert.match(graphSource, /A shared recipe tree was opened partially\./u);
  assert.match(graphSource, /A saved graph was reconstructed partially\./u);
  assert.match(graphSource, /Partial import\./u);
  assert.match(graphSource, /Dismiss partial import notice/u);
  assert.match(appSource, /recipeImportNotice=\{recipeImportNotice\}/u);
  assert.match(appSource, /onRecipeImportNoticeChange=\{setRecipeImportNotice\}/u);
});
