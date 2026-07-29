const STORAGE_KEY_PREFIX = 'hiddenRecipeStages:v1:';
const STORAGE_VERSION = 1;
const MAX_HIDDEN_STAGES = 512;
const DATASET_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const RECIPE_STAGE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}$/;

interface StoredRecipeStagePreferences {
  version: typeof STORAGE_VERSION;
  hidden: string[];
}

let warnedUnavailableLoad = false;
let warnedUnavailableSave = false;

function storageKey(datasetSlug: string): string {
  if (!DATASET_SLUG_PATTERN.test(datasetSlug) || datasetSlug.length > 80) {
    throw new Error('Recipe stage preferences require a canonical dataset slug.');
  }
  return `${STORAGE_KEY_PREFIX}${datasetSlug}`;
}

export function parseHiddenRecipeStages(raw: string): Set<string> {
  const parsed = JSON.parse(raw) as Partial<StoredRecipeStagePreferences>;
  if (
    !parsed ||
    typeof parsed !== 'object' ||
    Array.isArray(parsed) ||
    parsed.version !== STORAGE_VERSION ||
    !Array.isArray(parsed.hidden) ||
    parsed.hidden.length > MAX_HIDDEN_STAGES ||
    parsed.hidden.some(stage => typeof stage !== 'string' || !RECIPE_STAGE_PATTERN.test(stage))
  ) {
    throw new Error('Hidden recipe stages do not satisfy the storage contract.');
  }
  return new Set(parsed.hidden);
}

export function loadHiddenRecipeStages(datasetSlug: string): Set<string> {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableLoad) {
        warnedUnavailableLoad = true;
        console.warn('Recipe stage visibility settings are unavailable because localStorage is not present.');
      }
      return new Set();
    }
    const raw = storage.getItem(storageKey(datasetSlug));
    return raw === null ? new Set() : parseHiddenRecipeStages(raw);
  } catch (error) {
    console.error('Recipe stage visibility settings could not be loaded from localStorage.', error);
    return new Set();
  }
}

export function persistHiddenRecipeStages(
  datasetSlug: string,
  hiddenStages: ReadonlySet<string>,
): void {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableSave) {
        warnedUnavailableSave = true;
        console.warn('Recipe stage visibility changes are not persistent because localStorage is unavailable.');
      }
      return;
    }
    const hidden = [...hiddenStages].sort();
    if (
      hidden.length > MAX_HIDDEN_STAGES ||
      hidden.some(stage => !RECIPE_STAGE_PATTERN.test(stage))
    ) {
      console.error('Recipe stage visibility settings exceeded their persistence contract.', {
        stageCount: hidden.length,
        maximum: MAX_HIDDEN_STAGES,
      });
      return;
    }
    const stored: StoredRecipeStagePreferences = {
      version: STORAGE_VERSION,
      hidden,
    };
    storage.setItem(storageKey(datasetSlug), JSON.stringify(stored));
  } catch (error) {
    console.error('Recipe stage visibility settings could not be saved to localStorage.', error);
  }
}

export function toggleHiddenRecipeStage(
  hiddenStages: ReadonlySet<string>,
  stage: string,
): Set<string> {
  if (!RECIPE_STAGE_PATTERN.test(stage)) {
    throw new Error(`Cannot toggle invalid recipe stage ${JSON.stringify(stage)}.`);
  }
  const next = new Set(hiddenStages);
  if (next.has(stage)) next.delete(stage);
  else next.add(stage);
  return next;
}
