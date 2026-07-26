import assert from 'node:assert/strict';
import test from 'node:test';
import {
  capturePanGestureOrigin,
  transformForPanGesture,
} from './panGesture.ts';

test('delayed responder grant does not reapply the activation movement', () => {
  const transform = {x: 240, y: 130, scale: 0.75};
  const origin = capturePanGestureOrigin(transform, 7, 2);

  assert.deepEqual(transformForPanGesture(origin, 7, 2), transform);
  assert.deepEqual(transformForPanGesture(origin, 27, -8), {
    x: 260,
    y: 120,
    scale: 0.75,
  });
});

test('rebasing after a pinch preserves the current transform before panning resumes', () => {
  const postPinchTransform = {x: -340, y: 415, scale: 1.4};
  const origin = capturePanGestureOrigin(postPinchTransform, 126, -44);

  assert.deepEqual(transformForPanGesture(origin, 126, -44), postPinchTransform);
  assert.deepEqual(transformForPanGesture(origin, 116, -14), {
    x: -350,
    y: 445,
    scale: 1.4,
  });
});
