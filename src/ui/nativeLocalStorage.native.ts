import {Directory, File as NativeFile, Paths} from 'expo-file-system';

/**
 * expo-sqlite/localStorage/install now provides localStorage on native, replacing the JSON-file
 * polyfill this module used to install. Builds that shipped that polyfill wrote real user state --
 * graph sessions, theme and zoom preferences, the dataset catalog snapshot -- to the file below,
 * and SQLite-backed storage starts empty, so without this the upgrade would silently lose all of
 * it: exactly the "why do I lose my recipes when the app updates" failure this codebase has been
 * chasing. Entries already present in localStorage win, since those are newer by definition.
 */
const LEGACY_ROOT_DIRECTORY_NAME = 'minecraft-recipe-tree';
const LEGACY_STORAGE_FILE_NAME = 'local-storage.json';

function legacyStorageFile(): NativeFile {
  return new NativeFile(
    new Directory(Paths.document, LEGACY_ROOT_DIRECTORY_NAME),
    LEGACY_STORAGE_FILE_NAME,
  );
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return (
    !!value &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    Object.values(value).every(entry => typeof entry === 'string')
  );
}

export function migrateLegacyNativeLocalStorage(): void {
  const storage = globalThis.localStorage;
  if (!storage) {
    console.error('Legacy native storage cannot be migrated because localStorage is unavailable.');
    return;
  }
  let file: NativeFile;
  try {
    file = legacyStorageFile();
    if (!file.exists) return;
  } catch (error) {
    console.error('Legacy native storage could not be inspected.', error);
    return;
  }

  let migrated = 0;
  try {
    const parsed = JSON.parse(file.textSync()) as unknown;
    if (!isStringRecord(parsed)) {
      throw new Error('Legacy native storage does not hold a string record.');
    }
    for (const [key, value] of Object.entries(parsed)) {
      if (storage.getItem(key) !== null) continue;
      storage.setItem(key, value);
      if (storage.getItem(key) !== value) {
        throw new Error(`Legacy storage entry ${JSON.stringify(key)} was not persisted.`);
      }
      migrated += 1;
    }
  } catch (error) {
    console.error('Legacy native storage could not be migrated; keeping the original for retry.', error);
    return;
  }

  // Only remove the original after every missing entry has been copied and read back.
  try {
    file.delete();
  } catch (error) {
    console.error('Legacy native storage could not be removed after migration.', error);
    return;
  }
  if (migrated > 0) {
    console.info('Legacy native storage was migrated into SQLite-backed localStorage.', {
      migratedEntries: migrated,
    });
  }
}
