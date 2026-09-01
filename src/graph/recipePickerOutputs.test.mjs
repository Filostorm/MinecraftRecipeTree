import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const graphSource = await readFile(new URL('./GraphScreen.tsx', import.meta.url), 'utf8');
const pickerSource = await readFile(new URL('../components/PickerModal.tsx', import.meta.url), 'utf8');

test('recipe picker options include exported outputs in every graph direction', () => {
  assert.match(
    graphSource,
    /outputs:\s*presentedRecipe\s*\?\s*slotSummary\(presentedRecipe\.out\)\s*:\s*undefined/u,
  );
  assert.doesNotMatch(
    graphSource,
    /outputs:\s*presentedRecipe\s*&&\s*direction\s*===\s*'outputs'/u,
  );
  assert.match(pickerSource, /<Text style=\{styles\.ingredientLabel\}>Outputs<\/Text>/u);
  assert.match(pickerSource, /opt\.outputs\.map\(output/u);
  assert.match(pickerSource, /probabilityRole="produce"/u);
});
