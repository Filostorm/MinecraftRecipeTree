import {useEffect} from 'react';
import {Platform} from 'react-native';
import {createSignalSurfaceTracker} from './signalSurface';

declare const __MRT_SIGNAL_ENABLED__: boolean;

type SignalApi = {
  setSurface(name: string, group: string): void;
};

declare global {
  interface Window {
    craftsmannMetrics?: SignalApi;
  }
}

let tracker: ReturnType<typeof createSignalSurfaceTracker> | undefined;

export function useSignalSurface(
  name: string,
  group: 'screen' | 'modal',
  active = true,
): void {
  useEffect(() => {
    if (!active || Platform.OS !== 'web' || typeof window === 'undefined') return;
    tracker ??= createSignalSurfaceTracker(window,
      typeof __MRT_SIGNAL_ENABLED__ !== 'undefined' ? __MRT_SIGNAL_ENABLED__ : true);
    return tracker.track(name, group);
  }, [active, group, name]);
}

export function signalTarget(metricsId: string): {
  dataSet?: Record<string, string>;
} {
  return Platform.OS === 'web'
    ? {dataSet: {'metrics-id': metricsId}}
    : {};
}
