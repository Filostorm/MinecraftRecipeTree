import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {
  capturePanGestureOrigin,
  graphDisplayTransform,
  graphPinchZoomFactor,
  graphViewportPointFromClient,
  graphWheelZoomFactor,
  transformForPanGesture,
} from './panGesture.ts';

const graphScreenSource = await readFile(new URL('./GraphScreen.tsx', import.meta.url), 'utf8');

test('native graph scale avoids composited scaling and snaps to physical pixels', () => {
  assert.deepEqual(graphDisplayTransform({x: 10.24, y: 20.26, scale: 1}, 2), {
    x: 10,
    y: 20.5,
    scale: 1,
    nativeScale: true,
  });
  assert.deepEqual(graphDisplayTransform({x: 10.24, y: 20.26, scale: 0.8}, 2), {
    x: 10,
    y: 20.5,
    scale: 0.8,
    nativeScale: false,
  });
  assert.throws(
    () => graphDisplayTransform({x: 0, y: 0, scale: 1}, 0),
    /positive finite values/,
  );
});

test('web graph zoom uses layout scaling instead of a composited scale transform', () => {
  assert.match(graphScreenSource, /zoom:\s*displayTransform\.scale/u);
  const anchorMarkup = graphScreenSource.slice(
    graphScreenSource.indexOf('Keep translation outside the web scale layer'),
    graphScreenSource.indexOf('{renderedGraph?.edges.map'),
  );
  assert.match(anchorMarkup, /Platform\.OS === 'web'[\s\S]*?left:\s*displayTransform\.x/u);
  assert.match(anchorMarkup, /Platform\.OS !== 'web'[\s\S]*?translateX:\s*displayTransform\.x/u);
});

test('interface zoom scales graph menu chrome without scaling the graph canvas', () => {
  assert.match(
    graphScreenSource,
    /const graphMenuScaleStyle[\s\S]*?zoom:\s*interfaceZoom/u,
  );
  assert.match(graphScreenSource, /style=\{\[styles\.controls, graphMenuScaleStyle\]\}/u);
  assert.match(
    graphScreenSource,
    /style=\{\[styles\.ctrlBtn, styles\.fitControl, graphMenuScaleStyle\]\}/u,
  );
  assert.match(graphScreenSource, /interfaceZoom=\{interfaceZoom\}[\s\S]*?totals=\{treeTotals\}/u);
  assert.match(
    graphScreenSource,
    /style=\{\[styles\.recipeLookupCard, graphMenuScaleStyle\]\}/u,
  );
  const graphCanvasMarkup = graphScreenSource.slice(
    graphScreenSource.indexOf('<View\n        ref={setCanvasRef}'),
    graphScreenSource.indexOf('{graphLayout.fallback'),
  );
  assert.doesNotMatch(graphCanvasMarkup, /graphMenuScaleStyle|zoom:\s*interfaceZoom/u);
});

test('compact byproduct nodes support alternate recipe gestures', () => {
  assert.match(
    graphScreenSource,
    /double tap or right click to change recipe/u,
  );
  assert.match(
    graphScreenSource,
    /onContextMenu:[\s\S]*?preventDefault[\s\S]*?onChangeRecipe\(\)/u,
  );
  assert.match(
    graphScreenSource,
    /pendingTapRef\.current = setTimeout\([\s\S]*?onTap\(\)[\s\S]*?280/u,
  );
  assert.match(
    graphScreenSource,
    /treeTotals\.byproductCoverageByNode\.has\(n\.item\.id\)[\s\S]*?openPickerWithErrorHandling/u,
  );
});

test('wheel coordinates map displayed bounds into the logical graph viewport', () => {
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
