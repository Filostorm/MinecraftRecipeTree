export function mobSpritePlaybackFrames(frameCount: number): number[] {
  if (!Number.isSafeInteger(frameCount) || frameCount <= 0) {
    throw new Error('Mob sprite frame count must be a positive safe integer.');
  }
  if (frameCount === 1) return [0];

  const forward = Array.from({length: frameCount}, (_, frame) => frame);
  const reverseInterior = Array.from(
    {length: frameCount - 2},
    (_, offset) => frameCount - 2 - offset,
  );
  return [...forward, ...reverseInterior];
}

export function mobSpriteAnimationDurationSeconds(
  frameCount: number,
  framesPerSecond: number,
): number {
  if (!Number.isFinite(framesPerSecond) || framesPerSecond <= 0) {
    throw new Error('Mob sprite frame rate must be a positive finite number.');
  }
  return mobSpritePlaybackFrames(frameCount).length / framesPerSecond;
}

function percentage(value: number): string {
  return Number(value.toFixed(6)).toString();
}

/**
 * Build discrete forward-then-reverse keyframes. The final declaration matches
 * frame zero, so the infinite CSS animation has no discontinuity at its boundary.
 */
export function mobSpriteKeyframes(frameCount: number, animationName: string): string {
  if (!/^[-_a-zA-Z][-_a-zA-Z0-9]*$/.test(animationName)) {
    throw new Error('Mob sprite animation name must be a valid CSS identifier.');
  }
  const playback = mobSpritePlaybackFrames(frameCount);
  const declarations = playback.map((frame, step) => {
    const progress = percentage((step / playback.length) * 100);
    const offset = percentage((frame / frameCount) * 100);
    return `${progress}% { transform: translateX(-${offset}%); }`;
  });
  declarations.push('100% { transform: translateX(0); }');
  return `@keyframes ${animationName} { ${declarations.join(' ')} }`;
}
