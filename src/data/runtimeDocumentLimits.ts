export const MAX_NETWORK_DOCUMENT_BYTES = 8 * 1024 * 1024;
export const MAX_LEGACY_LOCAL_DOCUMENT_BYTES = 32 * 1024 * 1024;

const LOCAL_PACK_EXPORT_PATH = /^\/__local-packs\/[a-f0-9]{64}\/exports(?:\/|$)/u;

/**
 * Published documents and modern shards retain the strict network budget. Older
 * exports predate sharding and can contain a larger inline items, index, or
 * recipe document, so device-local imports get a bounded compatibility budget.
 */
export function runtimeDocumentByteLimit(url: string): number {
  let pathname: string;
  try {
    pathname = new URL(url, 'https://recipe-tree.invalid').pathname;
  } catch {
    return MAX_NETWORK_DOCUMENT_BYTES;
  }
  return LOCAL_PACK_EXPORT_PATH.test(pathname)
    ? MAX_LEGACY_LOCAL_DOCUMENT_BYTES
    : MAX_NETWORK_DOCUMENT_BYTES;
}

export function isLocalPackExportUrl(url: string): boolean {
  return runtimeDocumentByteLimit(url) === MAX_LEGACY_LOCAL_DOCUMENT_BYTES;
}
