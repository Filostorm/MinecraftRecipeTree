import React, {useEffect, useState} from 'react';
import {
  Image,
  Platform,
  StyleSheet,
  Text,
  View,
  type ImageErrorEvent,
} from 'react-native';
import {useData} from '../data/DataContext';
import {theme} from '../theme';
import {Mob} from '../types';
import {pixelated} from './ItemIcon';
import {
  generatedMobPlaceholderColor,
  generatedMobPlaceholderLabel,
} from './mobPlaceholder';

function GeneratedMobPlaceholder({mob, size}: {mob: Mob; size: number}) {
  return (
    <View
      accessible
      accessibilityRole="image"
      accessibilityLabel={`${mob.n} generated placeholder; exported mob artwork is unavailable`}
      style={[
        styles.placeholder,
        {width: size, height: size, backgroundColor: generatedMobPlaceholderColor(mob.id)},
      ]}>
      <Text style={[styles.placeholderGlyph, {fontSize: Math.max(11, size * 0.28)}]}>◈</Text>
      <Text
        numberOfLines={1}
        style={[styles.placeholderText, {fontSize: Math.max(9, size * 0.17)}]}>
        {generatedMobPlaceholderLabel(mob.n)}
      </Text>
    </View>
  );
}

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
  const uri = mob.icon ? data.imageUrl(mob.icon) : undefined;
  const [failedUri, setFailedUri] = useState<string | null>(null);
  const unavailable = uri === undefined || failedUri === uri;

  const useTimer = !unavailable && animate && frames > 1 && Platform.OS !== 'web';
  const [frame, setFrame] = useState(0);
  useEffect(() => {
    if (!useTimer) return;
    const id = setInterval(() => setFrame(f => (f + 1) % frames), 1000 / fps);
    return () => clearInterval(id);
  }, [useTimer, frames, fps]);

  if (unavailable) return <GeneratedMobPlaceholder mob={mob} size={size} />;

  const onError = (event: ImageErrorEvent) => {
    console.error('A mob sprite failed to load; rendering its deterministic generated placeholder.', {
      mobId: mob.id,
      mobName: mob.n,
      uri,
      detail: event.nativeEvent.error,
    });
    setFailedUri(uri);
  };

  if (frames <= 1) {
    return (
      <Image
        source={{uri}}
        style={[{width: size, height: size}, pixelated as object]}
        resizeMode="contain"
        onError={onError}
      />
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
        onError={onError}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  placeholder: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: theme.borderLight,
    borderRadius: 8,
    overflow: 'hidden',
  },
  placeholderGlyph: {
    color: theme.text,
    fontWeight: '900',
  },
  placeholderText: {
    maxWidth: '82%',
    color: theme.text,
    fontWeight: '900',
    letterSpacing: 0.6,
  },
});
