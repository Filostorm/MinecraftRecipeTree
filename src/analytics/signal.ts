import {useEffect} from 'react';
import {Platform} from 'react-native';

type SignalApi = {
  setSurface(name: string, group: string): void;
};

declare global {
  interface Window {
    craftsmannMetrics?: SignalApi;
  }
}

const TRACKER_READY_EVENT = 'craftsmann:metrics-ready';
const TRACKER_WAIT_MS = 5_000;

function applySurface(name: string, group: string): boolean {
  const api = window.craftsmannMetrics;
  if (!api) return false;
  api.setSurface(name, group);
  return true;
}

export function useSignalSurface(
  name: string,
  group: 'screen' | 'modal',
): void {
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') return;
    if (applySurface(name, group)) return;

    const onReady = () => {
      window.clearTimeout(timeout);
      if (!applySurface(name, group)) {
        console.warn(
          '[Recipe Tree] Signal reported ready without exposing its surface API.',
          {name, group},
        );
      }
    };
    const timeout = window.setTimeout(() => {
      window.removeEventListener(TRACKER_READY_EVENT, onReady);
      console.warn(
        '[Recipe Tree] Signal surface tracking was unavailable after five seconds.',
        {name, group},
      );
    }, TRACKER_WAIT_MS);
    window.addEventListener(TRACKER_READY_EVENT, onReady, {once: true});

    return () => {
      window.clearTimeout(timeout);
      window.removeEventListener(TRACKER_READY_EVENT, onReady);
    };
  }, [group, name]);
}

export function signalTarget(metricsId: string): {
  dataSet?: Record<string, string>;
} {
  return Platform.OS === 'web'
    ? {dataSet: {'metrics-id': metricsId}}
    : {};
}
