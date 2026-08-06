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
import {
  mobSpriteAnimationDurationSeconds,
  mobSpriteKeyframes,
  mobSpritePlaybackFrames,
} from './mobSpriteAnimation';

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

function webAnimationName(frameCount: number): string {
  return `mrt-mob-sprite-${String(frameCount)}`;
}

function ensureWebAnimation(frameCount: number): void {
  const animationName = webAnimationName(frameCount);
  if (Platform.OS !== 'web' || typeof document === 'undefined') return;
  const styleId = `${animationName}-keyframes`;
  if (!document.getElementById(styleId)) {
    const style = document.createElement('style');
    style.id = styleId;
    style.textContent = mobSpriteKeyframes(frameCount, animationName);
    document.head.appendChild(style);
  }
}

/**
 * Plays a mob's sprite sheet (N square frames side by side).
 *
 * On web this is a pure CSS stepped-keyframe animation — no JS timers or React re-renders —
 * so dozens of animated sprites cost nothing. (A per-component interval here froze
 * the whole page: ~60 sprites × 10 setState/sec saturated the event loop.)
 * Native falls back to a timer per sprite.
 */
export function MobSprite({mob, size, animate = true}: {mob: Mob; size: number; animate?: boolean}) {
  const data = useData();
  const frames = Number.isSafeInteger(mob.frames) && mob.frames! > 0 ? mob.frames! : 1;
  const fps = Number.isFinite(mob.fps) && mob.fps! > 0 ? mob.fps! : 10;
  const uri = mob.icon ? data.imageUrl(mob.icon) : undefined;
  const [failedUri, setFailedUri] = useState<string | null>(null);
  const unavailable = uri === undefined || failedUri === uri;
  const playback = mobSpritePlaybackFrames(frames);
  useEffect(() => {
    if (frames > 1) ensureWebAnimation(frames);
  }, [frames]);

  const useTimer = !unavailable && animate && frames > 1 && Platform.OS !== 'web';
  const [animationStep, setAnimationStep] = useState(0);
  useEffect(() => {
    if (!useTimer) return;
    const id = setInterval(
      () => setAnimationStep(step => (step + 1) % playback.length),
      1000 / fps,
    );
    return () => clearInterval(id);
  }, [useTimer, playback.length, fps]);

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
          animationName: webAnimationName(frames),
          animationDuration: `${mobSpriteAnimationDurationSeconds(frames, fps).toFixed(3)}s`,
          animationTimingFunction: 'step-end',
          animationIterationCount: 'infinite',
        } as object)
      : null;
  const frame = useTimer ? playback[animationStep % playback.length] : 0;

  return (
    <View style={{width: size, height: size, overflow: 'hidden'}}>
      <Image
        source={{uri}}
        style={[
          {width: strip, height: size},
          webAnimation ?? {transform: [{translateX: -frame * size}]},
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
