const STORAGE_KEY = 'minecraft-recipe-tree.recipe-favorite-client.v1';
const CLIENT_ID_PATTERN = /^[a-f0-9-]{32,128}$/iu;
let memoryClientId: string | null = null;

function newClientId(): string {
  const crypto = globalThis.crypto;
  if (!crypto?.randomUUID) {
    throw new Error('Secure browser UUID generation is unavailable.');
  }
  return crypto.randomUUID();
}

export async function recipeFavoriteClientId(): Promise<string> {
  if (memoryClientId) return memoryClientId;
  try {
    const stored = globalThis.localStorage?.getItem(STORAGE_KEY);
    if (stored && CLIENT_ID_PATTERN.test(stored)) {
      memoryClientId = stored;
      return stored;
    }
    const created = newClientId();
    globalThis.localStorage?.setItem(STORAGE_KEY, created);
    memoryClientId = created;
    return created;
  } catch (error) {
    console.warn('Recipe favorites are using a session identifier because browser storage failed.', error);
    memoryClientId = newClientId();
    return memoryClientId;
  }
}
