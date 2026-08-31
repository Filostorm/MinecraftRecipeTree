import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const graphSource = await readFile(new URL('./GraphScreen.tsx', import.meta.url), 'utf8');
const reportSource = await readFile(
  new URL('../data/recipeRetentionReports.ts', import.meta.url),
  'utf8',
);

test('recipe-input context menus can toggle the reusable correction', () => {
  assert.match(graphSource, /nodeMenuCanToggleReusable/u);
  assert.match(graphSource, /Treat as reusable/u);
  assert.match(graphSource, /Treat as consumed/u);
  assert.match(graphSource, /onToggleReusable=\{/u);
  assert.match(graphSource, /applyManualRetentionOverrideToTree/u);
  assert.match(graphSource, /child\.retentionMode = reusable \? 'reusable' : undefined/u);
});

test('future expansions apply the saved correction before calculating consumption', () => {
  assert.match(graphSource, /manualRetentionOverrideFor\(manualRetentionOverridesRef\.current/u);
  assert.match(graphSource, /const nonConsumed = retentionOverride \?\? spec\.nonConsumed/u);
  assert.match(graphSource, /spec\.probabilityRole === 'consume' && !nonConsumed/u);
});

test('manual corrections are logged locally and sent to the report endpoint', () => {
  assert.match(graphSource, /A recipe ingredient retention override was changed\./u);
  assert.match(graphSource, /reportRecipeRetentionOverride\(/u);
  assert.match(reportSource, /\/api\/recipe-retention-reports/u);
  assert.match(graphSource, /Reusable override saved locally/u);
});
