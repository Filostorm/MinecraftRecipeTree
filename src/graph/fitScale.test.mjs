import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MAX_AUTOMATIC_GRAPH_SCALE,
  automaticGraphFitScale,
} from './fitScale.ts';

test('automatic graph fit never fractionally enlarges pixel art', () => {
  assert.equal(MAX_AUTOMATIC_GRAPH_SCALE, 1);
  assert.equal(automaticGraphFitScale(1000, 800, 172, 58), 1);
});

test('automatic graph fit fractionally reduces only oversized graphs', () => {
  assert.equal(automaticGraphFitScale(300, 240, 480, 360), 0.5);
  assert.throws(() => automaticGraphFitScale(0, 240, 480, 360), /positive finite/);
});
