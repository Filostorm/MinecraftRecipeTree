import React from 'react';
import {SafeAreaView as ReactNativeSafeAreaView} from 'react-native';

type Edge = 'top' | 'right' | 'bottom' | 'left';

export const initialWindowMetrics = null;

export function SafeAreaProvider({children}: {
  children: React.ReactNode;
  initialMetrics?: unknown;
}) {
  return <>{children}</>;
}

export function SafeAreaView({
  edges: _edges,
  ...props
}: React.ComponentProps<typeof ReactNativeSafeAreaView> & {edges?: readonly Edge[]}) {
  return <ReactNativeSafeAreaView {...props} />;
}

export function useSafeAreaInsets() {
  return {top: 0, right: 0, bottom: 0, left: 0};
}
