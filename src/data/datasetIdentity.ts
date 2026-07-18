export interface DatasetIdentityManifest {
  /** SHA-256 publication identity computed after every export transformation is complete. */
  publicationId: string;
}

const PUBLICATION_ID_PATTERN = /^[0-9a-f]{64}$/;

export function isDatasetPublicationId(value: unknown): value is string {
  return typeof value === 'string' && PUBLICATION_ID_PATTERN.test(value);
}

/**
 * Returns the content-derived identity for one immutable, fully transformed export snapshot.
 *
 * Publication IDs are deliberately strict. Metadata such as generatedAt and item counts cannot
 * identify optimizer or packer output changes, so it is not accepted as an implicit fallback.
 */
export function datasetIdentityFromManifest(value: unknown): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Dataset identity requires a manifest object.');
  }
  const {publicationId} = value as Partial<DatasetIdentityManifest>;
  if (!isDatasetPublicationId(publicationId)) {
    throw new Error(
      'Dataset identity requires manifest.publicationId to be a lowercase 64-character SHA-256 hexadecimal digest.',
    );
  }
  return publicationId;
}

export function versionExportUrl(url: string, datasetIdentity: string): string {
  if (!isDatasetPublicationId(datasetIdentity)) {
    throw new Error('Cannot version an export URL with an invalid publication identity.');
  }
  const separator = url.includes('?') ? '&' : '?';
  return `${url}${separator}dataset=${encodeURIComponent(datasetIdentity)}`;
}
