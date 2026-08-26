import {randomUUID} from 'expo-crypto';
import {File, Paths} from 'expo-file-system';

const CLIENT_ID_PATTERN = /^[a-f0-9-]{32,128}$/iu;
const clientFile = new File(Paths.document, 'recipe-tree-favorite-client-id.txt');
let memoryClientId: string | null = null;

export async function recipeFavoriteClientId(): Promise<string> {
  if (memoryClientId) return memoryClientId;
  try {
    if (clientFile.exists) {
      const stored = (await clientFile.text()).trim();
      if (CLIENT_ID_PATTERN.test(stored)) {
        memoryClientId = stored;
        return stored;
      }
    }
    const created = randomUUID();
    clientFile.write(created);
    memoryClientId = created;
    return created;
  } catch (error) {
    console.warn('Recipe favorites are using a session identifier because device storage failed.', error);
    memoryClientId = randomUUID();
    return memoryClientId;
  }
}
