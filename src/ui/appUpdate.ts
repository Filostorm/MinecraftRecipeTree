declare const __MRT_APP_BUILD_ID__: string | undefined;

export class AppUpdateAvailableError extends Error {
  constructor() {
    super('A newer Recipe Tree build is available. Reload to open the graph.');
    this.name = 'AppUpdateAvailableError';
  }
}

export function runningAppBuildId(): string | null {
  return typeof __MRT_APP_BUILD_ID__ === 'string' ? __MRT_APP_BUILD_ID__ : null;
}

let pendingCheck: Promise<boolean> | null = null;

/** Event-driven only: no polling, automatic reload, or changes to saved data. */
export function checkForAppUpdate(buildId = runningAppBuildId()): Promise<boolean> {
  if (!buildId) return Promise.resolve(false); // Native builds do not load web chunks.
  if (pendingCheck) return pendingCheck;
  pendingCheck = (async () => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    try {
      const response = await fetch('/app-version.json', {
        cache: 'no-store', signal: controller.signal,
      });
      if (!response.ok) throw new Error(`App version request returned HTTP ${response.status}.`);
      const value: unknown = await response.json();
      if (!value || typeof value !== 'object' || !('buildId' in value) ||
        typeof value.buildId !== 'string' || !value.buildId.trim()) {
        throw new Error('App version response has no valid buildId.');
      }
      return value.buildId !== buildId;
    } finally {
      clearTimeout(timer);
    }
  })().finally(() => {pendingCheck = null;});
  return pendingCheck;
}

export async function ensureCurrentAppBuild(): Promise<void> {
  let updated: boolean;
  try {
    updated = await checkForAppUpdate();
  } catch (error) {
    // Offline users may have a complete cached graph. Attempt that known build explicitly.
    console.warn('App update check failed; attempting the current cached graph build.', error);
    return;
  }
  if (updated) throw new AppUpdateAvailableError();
}
