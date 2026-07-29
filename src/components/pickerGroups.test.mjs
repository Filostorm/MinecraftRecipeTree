import assert from 'node:assert/strict';
import test from 'node:test';
import {groupPickerOptions} from './pickerGroups.ts';

test('groups picker options by recipe type without changing selection indexes', () => {
  const groups = groupPickerOptions([
    {label: 'Mine', groupKey: 'mining', groupLabel: 'Mining'},
    {label: 'Arc 1', groupKey: 'recipe:12', groupLabel: 'Arc Furnace'},
    {label: 'Chisel', groupKey: 'recipe:9', groupLabel: 'Chisel'},
    {label: 'Arc 2', groupKey: 'recipe:12', groupLabel: 'Arc Furnace'},
  ]);

  assert.deepEqual(
    groups.map(group => [
      group.key,
      group.label,
      group.entries.map(entry => [entry.index, entry.option.label]),
    ]),
    [
      ['mining', 'Mining', [[0, 'Mine']]],
      ['recipe:12', 'Arc Furnace', [[1, 'Arc 1'], [3, 'Arc 2']]],
      ['recipe:9', 'Chisel', [[2, 'Chisel']]],
    ],
  );
});

test('places explicitly ungrouped options in a visible other-sources section', () => {
  const [group] = groupPickerOptions([{label: 'Unknown'}]);
  assert.equal(group.key, 'other-sources');
  assert.equal(group.label, 'Other sources');
  assert.equal(group.entries[0].index, 0);
});
