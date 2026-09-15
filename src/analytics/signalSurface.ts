export interface SignalSurfaceApi {
  setSurface(name: string, group: string): void;
}

interface SignalHost {
  craftsmannMetrics?: SignalSurfaceApi;
  addEventListener(name: string, listener: () => void): void;
  removeEventListener(name: string, listener: () => void): void;
  setTimeout(callback: () => void, ms: number): number;
  clearTimeout(id: number): void;
}

const READY_EVENT = 'craftsmann:metrics-ready';

/** One readiness listener and warning per page; late startup still receives the current surface. */
export function createSignalSurfaceTracker(host: SignalHost, enabled: boolean, log = console) {
  const surfaces = new Map<symbol, {name: string; group: 'screen' | 'modal'}>();
  let started = false;
  let timer: number | undefined;
  let last: string | undefined;
  let warned = false;
  const warn = (message: string) => {
    if (!warned) log.warn(message);
    warned = true;
  };
  const flush = () => {
    const all = [...surfaces.values()];
    const surface = all.filter(value => value.group === 'modal').at(-1) ?? all.at(-1);
    if (!surface || !host.craftsmannMetrics) return false;
    const identity = JSON.stringify(surface);
    try {
      if (last !== identity) host.craftsmannMetrics.setSurface(surface.name, surface.group);
      last = identity;
      if (timer !== undefined) host.clearTimeout(timer);
      return true;
    } catch (error) {
      if (!warned) log.warn('[Recipe Tree] Signal surface tracking failed.', error);
      warned = true;
      return false;
    }
  };
  const onReady = () => {
    if (!flush() && surfaces.size) warn('[Recipe Tree] Signal reported ready without a working surface API.');
  };
  return {
    track(name: string, group: 'screen' | 'modal') {
      if (!enabled) {
        if (!started) log.info('[Recipe Tree] Signal tracking is intentionally disabled for this build.');
        started = true;
        return () => {};
      }
      const id = Symbol(name);
      surfaces.set(id, {name, group});
      if (!started) {
        started = true;
        host.addEventListener(READY_EVENT, onReady);
        timer = host.setTimeout(() => {
          if (!flush()) warn('[Recipe Tree] Signal surface tracking was unavailable after five seconds.');
        }, 5_000);
      }
      flush();
      return () => { surfaces.delete(id); flush(); };
    },
    dispose() {
      if (timer !== undefined) host.clearTimeout(timer);
      host.removeEventListener(READY_EVENT, onReady);
      surfaces.clear();
    },
  };
}
