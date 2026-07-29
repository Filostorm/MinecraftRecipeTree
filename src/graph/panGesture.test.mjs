import assert from 'node:assert/strict';
import test from 'node:test';
import {
  capturePanGestureOrigin,
  graphPinchZoomFactor,
  graphViewportPointFromClient,
  graphWheelZoomFactor,
  transformForPanGesture,
} from './panGesture.ts';

test('wheel coordinates compensate for interface-level CSS zoom', () => {
  assert.deepEqual(
    graphViewportPointFromClient(
      450,
      300,
      {left: 150, top: 75, width: 900, height: 600},
      {width: 600, height: 400},
    ),
    {x: 200, y: 150},
  );
});

test('wheel zoom is continuous and normalizes browser delta modes', () => {
  assert.equal(graphWheelZoomFactor(0, 0), 1);
  assert.ok(graphWheelZoomFactor(-8, 0) > 1);
  assert.ok(graphWheelZoomFactor(8, 0) < 1);
  assert.equal(graphWheelZoomFactor(10, 1), graphWheelZoomFactor(160, 0));
  assert.throws(() => graphWheelZoomFactor(10, 3), /invalid/);
});

test('pinch zoom amplifies finger travel without frame-dependent accumulation', () => {
  assert.equal(graphPinchZoomFactor(100, 100), 1);
  assert.ok(graphPinchZoomFactor(110, 100) > 1.2);
  assert.ok(graphPinchZoomFactor(90, 100) < 0.8);
  assert.ok(
    Math.abs(
      graphPinchZoomFactor(110, 100) *
        graphPinchZoomFactor(100, 110) -
        1,
    ) < Number.EPSILON * 4,
  );
  assert.throws(() => graphPinchZoomFactor(0, 100), /positive finite numbers/);
});

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
