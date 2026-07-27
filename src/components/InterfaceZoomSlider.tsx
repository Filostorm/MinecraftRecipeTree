import React, {useCallback, useMemo, useRef, useState} from 'react';
import {
  PanResponder,
  Platform,
  Pressable,
  StyleSheet,
  View,
} from 'react-native';
import {theme} from '../theme';

export interface InterfaceZoomSliderProps {
  value: number;
  minimumValue: number;
  maximumValue: number;
  step: number;
  onValueChange: (value: number) => void;
  onSlidingComplete: (value: number) => void;
}

export function InterfaceZoomSlider({
  value,
  minimumValue,
  maximumValue,
  step,
  onValueChange,
  onSlidingComplete,
}: InterfaceZoomSliderProps) {
  const [trackWidth, setTrackWidth] = useState(0);
  const currentValue = useRef(value);
  currentValue.current = value;

  const valueFromTrackX = useCallback(
    (x: number) => {
      if (trackWidth <= 0) {
        console.error('Interface zoom slider cannot resolve a value before layout.');
        return null;
      }
      const fraction = Math.min(1, Math.max(0, x / trackWidth));
      const stepIndex = Math.round(
        (minimumValue + fraction * (maximumValue - minimumValue) - minimumValue) /
          step,
      );
      return Math.min(
        maximumValue,
        Math.max(minimumValue, minimumValue + stepIndex * step),
      );
    },
    [maximumValue, minimumValue, step, trackWidth],
  );

  const previewAt = useCallback(
    (x: number) => {
      const next = valueFromTrackX(x);
      if (next === null) return;
      currentValue.current = next;
      onValueChange(next);
    },
    [onValueChange, valueFromTrackX],
  );

  const complete = useCallback(() => {
    onSlidingComplete(currentValue.current);
  }, [onSlidingComplete]);

  const adjustByStep = useCallback(
    (direction: -1 | 1) => {
      const next = Math.min(
        maximumValue,
        Math.max(minimumValue, currentValue.current + direction * step),
      );
      currentValue.current = next;
      onValueChange(next);
      onSlidingComplete(next);
    },
    [maximumValue, minimumValue, onSlidingComplete, onValueChange, step],
  );

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: event => previewAt(event.nativeEvent.locationX),
        onPanResponderMove: event => previewAt(event.nativeEvent.locationX),
        onPanResponderRelease: complete,
        onPanResponderTerminate: complete,
      }),
    [complete, previewAt],
  );

  const fraction =
    (value - minimumValue) / Math.max(Number.EPSILON, maximumValue - minimumValue);
  const keyboardProps =
    Platform.OS === 'web'
      ? ({
          onKeyDown: (event: {nativeEvent: {key?: string}; preventDefault?: () => void}) => {
            const key = event.nativeEvent.key;
            if (
              key !== 'ArrowLeft' &&
              key !== 'ArrowDown' &&
              key !== 'ArrowRight' &&
              key !== 'ArrowUp'
            ) {
              return;
            }
            event.preventDefault?.();
            adjustByStep(key === 'ArrowLeft' || key === 'ArrowDown' ? -1 : 1);
          },
        } as object)
      : null;

  return (
    <Pressable
      {...panResponder.panHandlers}
      {...keyboardProps}
      accessibilityRole="adjustable"
      accessibilityLabel="Interface zoom"
      accessibilityValue={{
        min: Math.round(minimumValue * 100),
        max: Math.round(maximumValue * 100),
        now: Math.round(value * 100),
        text: `${String(Math.round(value * 100))} percent`,
      }}
      accessibilityActions={[
        {name: 'increment', label: 'Increase interface zoom'},
        {name: 'decrement', label: 'Decrease interface zoom'},
      ]}
      onAccessibilityAction={event => {
        if (event.nativeEvent.actionName === 'increment') {
          adjustByStep(1);
        } else if (event.nativeEvent.actionName === 'decrement') {
          adjustByStep(-1);
        }
      }}
      onLayout={event => setTrackWidth(event.nativeEvent.layout.width)}
      style={styles.control}>
      <View pointerEvents="none" style={styles.track}>
        <View style={[styles.fill, {width: fraction * trackWidth}]} />
      </View>
      <View
        pointerEvents="none"
        style={[styles.thumb, {left: Math.max(0, fraction * trackWidth - 8)}]}
      />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  control: {
    width: 128,
    height: 34,
    justifyContent: 'center',
  },
  track: {
    height: 4,
    borderRadius: 2,
    backgroundColor: theme.borderLight,
    overflow: 'hidden',
  },
  fill: {
    height: 4,
    borderRadius: 2,
    backgroundColor: theme.accent,
  },
  thumb: {
    position: 'absolute',
    top: 9,
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: theme.accent,
    borderWidth: 2,
    borderColor: theme.panel,
  },
});
