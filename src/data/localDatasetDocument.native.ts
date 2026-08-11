import {File as NativeFile} from 'expo-file-system';
import type {LocalDatasetDocument} from './localDatasetDocument';

function localFileUri(url: string): string | null {
  if (!url.startsWith('file://')) return null;
  const query = url.indexOf('?');
  const hash = url.indexOf('#');
  const end = Math.min(
    query === -1 ? url.length : query,
    hash === -1 ? url.length : hash,
  );
  return url.slice(0, end);
}

export async function readLocalDatasetDocument(
  url: string,
): Promise<LocalDatasetDocument | null> {
  const uri = localFileUri(url);
  if (uri === null) return null;
  const file = new NativeFile(uri);
  if (!file.exists) throw new Error(`Local export file is missing: ${file.name}`);
  const text = await file.text();
  return {text, bytes: new TextEncoder().encode(text).byteLength};
}
