import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MAX_MULTIBLOCK_PREVIEW_CELLS,
  previewStructureCells,
  projectStructureCells,
} from './multiblockProjection.ts';

test('projects every small structure cell as overlapping pixel-aligned blocks', () => {
  const cells = [
    [0, 0, 0, 'item|controller'],
    [1, 0, 0, 'item|casing'],
    [0, 1, 0, 'item|glass'],
  ];
  const projected = projectStructureCells(cells, 320, 220, 0);
  assert.equal(projected.length, cells.length);
  for (const cell of projected) {
    assert.ok(cell.left >= 0 && cell.left + cell.size <= 320);
    assert.ok(cell.top >= 0 && cell.top + cell.size <= 220);
    assert.equal(cell.size, 32);
  }
  const controller = projected.find(cell => cell.source[3] === 'item|controller');
  const casing = projected.find(cell => cell.source[3] === 'item|casing');
  assert.ok(controller);
  assert.ok(casing);
  assert.equal(Math.abs(casing.left - controller.left), 16);
  assert.equal(Math.abs(casing.top - controller.top), 8);
});

test('vertical neighbors overlap by half a block sprite like a placed stack', () => {
  const projected = projectStructureCells(
    [
      [0, 0, 0, 'item|lower'],
      [0, 1, 0, 'item|upper'],
    ],
    240,
    220,
    0,
  );
  const lower = projected.find(cell => cell.source[3] === 'item|lower');
  const upper = projected.find(cell => cell.source[3] === 'item|upper');
  assert.ok(lower);
  assert.ok(upper);
  assert.equal(lower.top - upper.top, 16);
  assert.ok(upper.layer > lower.layer);
});

test('rotation changes the projected orientation without changing positions', () => {
  const cells = [
    [0, 0, 0, 'item|controller'],
    [2, 0, 0, 'item|casing'],
    [0, 0, 1, 'item|glass'],
  ];
  const first = projectStructureCells(cells, 320, 220, 0);
  const rotated = projectStructureCells(cells, 320, 220, 1);
  assert.deepEqual(
    new Set(first.map(cell => cell.source.join(':'))),
    new Set(rotated.map(cell => cell.source.join(':'))),
  );
  assert.notDeepEqual(
    first.map(cell => [cell.left, cell.top]),
    rotated.map(cell => [cell.left, cell.top]),
  );
});

test('large volumes retain a bounded 16px sprite instead of fractional icon scaling', () => {
  const cells = Array.from({length: 120}, (_, index) => [
    index % 20,
    Math.floor(index / 60),
    Math.floor(index / 20) % 3,
    `item|block-${index}`,
  ]);
  const projected = projectStructureCells(cells, 240, 160, 0);
  assert.ok(projected.every(cell => cell.size === 16));
  assert.ok(projected.every(cell => cell.left >= 0 && cell.left + cell.size <= 240));
  assert.ok(projected.every(cell => cell.top >= 0 && cell.top + cell.size <= 160));
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
