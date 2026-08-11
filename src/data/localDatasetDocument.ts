export interface LocalDatasetDocument {
  text: string;
  bytes: number;
}

export async function readLocalDatasetDocument(
  _url: string,
): Promise<LocalDatasetDocument | null> {
  return null;
}
