import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MAX_MULTIBLOCK_PREVIEW_CELLS,
  previewStructureCells,
  projectStructureCells,
} from './multiblockProjection.ts';

test('projects every small structure cell inside a bounded preview', () => {
  const cells = [
    [0, 0, 0, 'item|controller'],
    [1, 0, 0, 'item|casing'],
    [0, 1, 0, 'item|glass'],
  ];
  const projected = projectStructureCells(cells, 320, 190, 0);
  assert.equal(projected.length, cells.length);
  for (const cell of projected) {
    assert.ok(cell.left >= -10 && cell.left <= 310);
    assert.ok(cell.top >= -10 && cell.top <= 180);
    assert.equal(cell.size, 20);
  }
});

test('rotation changes the projected orientation without changing positions', () => {
  const cells = [
    [0, 0, 0, 'item|controller'],
    [2, 0, 0, 'item|casing'],
    [0, 0, 1, 'item|glass'],
  ];
  const first = projectStructureCells(cells, 320, 190, 0);
  const rotated = projectStructureCells(cells, 320, 190, 1);
  assert.deepEqual(
    new Set(first.map(cell => cell.source.join(':'))),
    new Set(rotated.map(cell => cell.source.join(':'))),
  );
  assert.notDeepEqual(
    first.map(cell => [cell.left, cell.top]),
    rotated.map(cell => [cell.left, cell.top]),
  );
});

test('caps very large previews while retaining exact source data elsewhere', () => {
  const cells = Array.from({length: 600}, (_, index) => [
    index % 20,
    Math.floor(index / 200),
    Math.floor(index / 20) % 10,
    `item|block-${index % 5}`,
  ]);
  const selected = previewStructureCells(cells);
  assert.equal(selected.length, MAX_MULTIBLOCK_PREVIEW_CELLS);
  assert.ok(selected.every(cell => cells.includes(cell)));
});
