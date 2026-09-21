import {useEffect, useState} from 'react';
import * as Crypto from 'expo-crypto';
import {Directory, File, Paths} from 'expo-file-system';

const directory = () => new Directory(Paths.document, 'minecraft-recipe-tree', 'viewed-previews');
const MAX_BYTES = 96 * 1024 * 1024;
const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const pending = new Map<string, Promise<string>>();
let queue: Promise<unknown> = Promise.resolve();

function cacheable(uri: string): boolean {
  if (!uri.startsWith('https://')) return false;
  const url = new URL(uri);
  return url.origin === 'https://minecraftrecipetree.craftsmannsoftware.com'
    && url.pathname.startsWith('/dataset/')
    && /^[a-f0-9]{64}$/.test(url.searchParams.get('dataset') ?? '');
}

async function savePreview(uri: string): Promise<string> {
  const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, uri);
  const root = directory();
  root.create({intermediates: true, idempotent: true});
  const file = new File(root, `${hash}.image`);
  if (file.exists && file.size > 0 && file.size <= MAX_IMAGE_BYTES) return file.uri;
  // Download into a temporary file so interruption never leaves a ready-looking preview.
  const temporary = new File(root, `${hash}.pending`);
  if (temporary.exists) temporary.delete();
  try {
    await File.downloadFileAsync(uri, temporary);
    if (temporary.size <= 0 || temporary.size > MAX_IMAGE_BYTES) throw new Error('Preview exceeds its download size budget.');
    const files = root.list().filter((entry): entry is File => entry instanceof File && /^[a-f0-9]{64}\.image$/.test(entry.name));
    let total = files.reduce((sum, entry) => sum + entry.size, temporary.size);
    for (const old of files.sort((a, b) => (a.modificationTime ?? 0) - (b.modificationTime ?? 0))) {
      if (total <= MAX_BYTES) break;
      total -= old.size;
      old.delete();
    }
    if (file.exists) file.delete();
    temporary.move(file);
    return file.uri;
  } finally {
    if (temporary.exists && temporary.uri !== file.uri) temporary.delete();
  }
}

function savedPreview(uri: string): Promise<string> {
  const existing = pending.get(uri);
  if (existing) return existing;
  // Bound disk/network work when a picker displays many previews at once.
  const operation = queue.then(() => savePreview(uri)).catch(error => {
    console.error('Recipe preview could not be saved locally; using its online image.', {uri, error});
    return uri;
  }).finally(() => pending.delete(uri));
  queue = operation;
  pending.set(uri, operation);
  return operation;
}

export function useSavedPreview(uri: string | undefined): string | undefined {
  const [saved, setSaved] = useState<{source: string; local: string} | null>(null);
  const shouldSave = Boolean(uri && cacheable(uri));
  useEffect(() => {
    let current = true;
    if (uri && shouldSave) void savedPreview(uri).then(local => {if (current) setSaved({source: uri, local});});
    return () => {current = false;};
  }, [uri, shouldSave]);
  if (!shouldSave) return uri;
  return saved && saved.source === uri ? saved.local : undefined;
}
