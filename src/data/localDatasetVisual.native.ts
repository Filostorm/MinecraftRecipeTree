export function localDatasetVisualUri(url: string): string {
  if (!url.startsWith('file://')) return url;
  const query = url.indexOf('?');
  const hash = url.indexOf('#');
  const end = Math.min(
    query === -1 ? url.length : query,
    hash === -1 ? url.length : hash,
  );
  return url.slice(0, end);
}
