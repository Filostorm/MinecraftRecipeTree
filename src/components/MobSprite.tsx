import React, {useEffect, useState} from 'react';
import {Image, Platform, View} from 'react-native';
import {useData} from '../data/DataContext';
import {Mob} from '../types';
import {pixelated} from './ItemIcon';

// One global keyframes rule drives every sprite: -100% of the strip's own width
// is exactly frames × frameSize, so it works for any sprite size.
if (Platform.OS === 'web' && typeof document !== 'undefined') {
  const style = document.createElement('style');
  style.textContent =
    '@keyframes mobsprite-scroll { from { transform: translateX(0); } to { transform: translateX(-100%); } }';
  document.head.appendChild(style);
}

/**
 * Plays a mob's sprite sheet (N square frames side by side).
 *
 * On web this is a pure CSS steps() animation — no JS timers, no React re-renders —
 * so dozens of animated sprites cost nothing. (A per-component interval here froze
 * the whole page: ~60 sprites × 10 setState/sec saturated the event loop.)
 * Native falls back to a timer per sprite.
 */
export function MobSprite({mob, size, animate = true}: {mob: Mob; size: number; animate?: boolean}) {
  const data = useData();
  const frames = mob.frames ?? 1;
  const fps = mob.fps ?? 10;
  const uri = data.imageUrl(mob.icon);

  const useTimer = animate && frames > 1 && Platform.OS !== 'web';
  const [frame, setFrame] = useState(0);
  useEffect(() => {
    if (!useTimer) return;
    const id = setInterval(() => setFrame(f => (f + 1) % frames), 1000 / fps);
    return () => clearInterval(id);
  }, [useTimer, frames, fps]);

  if (frames <= 1) {
    return (
      <Image source={{uri}} style={[{width: size, height: size}, pixelated as object]} resizeMode="contain" />
    );
  }

  const strip = size * frames;
  const webAnimation =
    Platform.OS === 'web' && animate
      ? ({
          animationName: 'mobsprite-scroll',
          animationDuration: `${(frames / fps).toFixed(2)}s`,
          animationTimingFunction: `steps(${frames})`,
          animationIterationCount: 'infinite',
        } as object)
      : null;

  return (
    <View style={{width: size, height: size, overflow: 'hidden'}}>
      <Image
        source={{uri}}
        style={[
          {width: strip, height: size},
          webAnimation ?? {transform: [{translateX: -(useTimer ? frame : 0) * size}]},
          pixelated as object,
        ]}
        resizeMode="stretch"
      />
    </View>
  );
}
