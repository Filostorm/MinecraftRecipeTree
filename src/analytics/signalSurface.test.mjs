import assert from 'node:assert/strict';
import test from 'node:test';
import {createSignalSurfaceTracker} from './signalSurface.ts';

function setup(enabled = true) {
  const listeners = new Map();
  const timers = new Map();
  const warnings = [], info = [], calls = [];
  let next = 0;
  const host = {
    addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: name => listeners.delete(name),
    setTimeout: fn => { timers.set(++next, fn); return next; },
    clearTimeout: id => timers.delete(id),
  };
  const tracker = createSignalSurfaceTracker(host, enabled, {warn: (...args) => warnings.push(args), info: (...args) => info.push(args)});
  return {host, tracker, warnings, info, calls, timers, listeners,
    ready() { host.craftsmannMetrics = {setSurface: (...args) => calls.push(args)}; listeners.get('craftsmann:metrics-ready')?.(); },
    timeout() { for (const callback of timers.values()) callback(); timers.clear(); },
  };
}

test('disabled builds log once, never wait for or call analytics', () => {
  const env = setup(false);
  env.ready();
  env.tracker.track('graph', 'screen');
  env.tracker.track('resources', 'screen');
  assert.equal(env.info.length, 1);
  assert.equal(env.timers.size, 0);
  assert.equal(env.listeners.size, 0);
  assert.equal(env.calls.length, 0);
});
test('late tracker startup receives current surface even after timeout', () => {
  const env = setup();
  const close = env.tracker.track('items', 'screen');
  env.timeout();
  close();
  env.tracker.track('resources', 'screen');
  env.ready();
  assert.equal(env.warnings.length, 1);
  assert.deepEqual(env.calls, [['resources', 'screen']]);
});
test('modal takes precedence and closing it restores the screen without duplicate events', () => {
  const env = setup();
  env.ready();
  env.tracker.track('graph', 'screen');
  const modal = env.tracker.track('source-picker', 'modal');
  env.tracker.track('graph', 'screen');
  modal();
  env.ready();
  assert.deepEqual(env.calls, [['graph', 'screen'], ['source-picker', 'modal'], ['graph', 'screen']]);
  assert.equal(env.timers.size, 0);
  env.tracker.dispose();
  assert.equal(env.listeners.size, 0);
});
test('blocked or broken tracking reports one warning instead of one per screen', () => {
  const env = setup();
  env.host.craftsmannMetrics = {setSurface() { throw new Error('blocked'); }};
  env.tracker.track('items', 'screen');
  env.tracker.track('graph', 'screen');
  env.timeout();
  assert.equal(env.warnings.length, 1);
});
