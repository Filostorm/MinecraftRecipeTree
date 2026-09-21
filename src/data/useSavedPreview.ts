/** Browsers persist immutable previews through the existing service worker. */
export function useSavedPreview(uri: string | undefined): string | undefined {
  return uri;
}
