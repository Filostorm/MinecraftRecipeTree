import assert from 'node:assert/strict';
import test from 'node:test';
import {
  mobSpriteAnimationDurationSeconds,
  mobSpriteKeyframes,
  mobSpritePlaybackFrames,
} from './mobSpriteAnimation.ts';

test('mob sprite playback returns to frame zero through adjacent reverse frames', () => {
  assert.deepEqual(mobSpritePlaybackFrames(4), [0, 1, 2, 3, 2, 1]);
  assert.deepEqual(mobSpritePlaybackFrames(2), [0, 1]);
  assert.deepEqual(mobSpritePlaybackFrames(1), [0]);
});

test('mob sprite playback preserves the requested frame rate across the full cycle', () => {
  assert.equal(mobSpriteAnimationDurationSeconds(16, 10), 3);
  assert.equal(mobSpriteAnimationDurationSeconds(4, 8), 0.75);
});

test('mob sprite keyframes select whole frames and close on frame zero', () => {
  const css = mobSpriteKeyframes(4, 'mob-loop-4');
  assert.match(css, /^@keyframes mob-loop-4/);
  assert.match(css, /50% \{ transform: translateX\(-75%\); \}/);
  assert.match(css, /100% \{ transform: translateX\(0\); \}/);
  assert.doesNotMatch(css, /translateX\(-100%\)/);
});

test('mob sprite animation inputs fail closed', () => {
  assert.throws(() => mobSpritePlaybackFrames(0), /positive safe integer/);
  assert.throws(() => mobSpriteAnimationDurationSeconds(4, 0), /positive finite/);
  assert.throws(() => mobSpriteKeyframes(4, 'bad name'), /valid CSS identifier/);
});
