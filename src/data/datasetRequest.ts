// Initial pack documents can be large; recipe lookups use a shorter per-transfer deadline.
export const DATASET_REQUEST_TIMEOUT_MS = 60_000;
export const RECIPE_DOCUMENT_TIMEOUT_MS = 15_000;
const revalidateNextRead = new Set<string>();
class IncompleteDatasetResponseError extends Error {}

export class ExportHttpError extends Error {
  readonly status: number;
  constructor(status: number, url: string) {
    super(`HTTP ${status} for ${url}`);
    this.status = status;
  }
}

/** Bound the whole transfer, not just response headers, so a retry starts fresh work. */
export async function fetchDatasetText(
  url: string,
  init?: RequestInit,
  timeoutMs = DATASET_REQUEST_TIMEOUT_MS,
): Promise<string> {
  for (let attempt = 0; ; attempt += 1) {
    const controller = new AbortController();
    const abort = () => controller.abort(init?.signal?.reason);
    if (init?.signal?.aborted) abort();
    else init?.signal?.addEventListener('abort', abort, {once: true});
    const timer = setTimeout(() => controller.abort(
      new Error(`Dataset request did not complete within ${timeoutMs / 1000} seconds: ${url}`),
    ), timeoutMs);
    try {
      const revalidate = revalidateNextRead.has(url);
      const headers = new Headers(init?.headers);
      if (revalidate) {
        console.warn('Retrying a failed dataset document with cache revalidation.', {url});
        headers.set('Cache-Control', 'no-cache');
      }
      const response = await fetch(url, {
        ...init, headers, signal: controller.signal,
        cache: init?.cache ?? (revalidate ? 'reload' : 'default'),
      });
      if (!response.ok) throw new ExportHttpError(response.status, url);
      const text = await response.text();
      // This header describes uncompressed UTF-8 bytes, unlike Content-Length on the wire.
      const storedBytes = response.headers.get('X-MRT-Stored-Bytes');
      if (storedBytes !== null && (!/^[1-9]\d*$/.test(storedBytes) ||
        !Number.isSafeInteger(Number(storedBytes)) ||
        new TextEncoder().encode(text).byteLength !== Number(storedBytes))) {
        throw new IncompleteDatasetResponseError(`Dataset response did not contain its declared ${storedBytes} bytes: ${url}`);
      }
      revalidateNextRead.delete(url);
      return text;
    } catch (error) {
      revalidateNextRead.add(url);
      if (revalidateNextRead.size > 128) revalidateNextRead.delete(revalidateNextRead.values().next().value!);
      console.error('Dataset document request failed.', {url, error});
      // A completed but corrupt cache response cannot improve by waiting. Revalidate it once,
      // with diagnostics, instead of retaining an empty/partial document across page reloads.
      if (!(error instanceof IncompleteDatasetResponseError) || attempt !== 0) throw error;
    } finally {
      clearTimeout(timer);
      init?.signal?.removeEventListener('abort', abort);
    }
  }
}

/** Coalesce concurrent reads, but never memoize a failed document for the session. */
export function loadCachedDocument<K, T>(
  cache: Map<K, Promise<T>>,
  key: K,
  load: () => Promise<T>,
): Promise<T> {
  const cached = cache.get(key);
  if (cached) return cached;
  const pending = Promise.resolve().then(load).catch(error => {
    if (cache.get(key) === pending) cache.delete(key);
    throw error;
  });
  cache.set(key, pending);
  return pending;
}
