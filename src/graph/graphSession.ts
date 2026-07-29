import type {DatasetDescriptor} from '../data/datasetCatalog';
import type {IngredientSelections} from '../data/ingredientAlternativeSelection';
import type {RecipeRef} from '../types';
import type {GraphDirection} from './direction';
import type {ItemTreeNode, SourceTreeNode} from './model';

const GRAPH_SESSION_VERSION = 1;
const MAX_ITEM_KEY_LENGTH = 512;
const MAX_SOURCE_ID_LENGTH = 512;
const MAX_TREE_DEPTH = 64;
export const MAX_GRAPH_SESSION_SELECTIONS = 2048;

export type StoredGraphSource =
  | {
      kind: 'recipe';
      ref: RecipeRef;
      allowFluidTransfer?: true;
      ingredientSelections?: IngredientSelections;
    }
  | {kind: 'mob'; mobId: string}
  | {kind: 'block'; blockKey: string};

export interface StoredGraphSelection {
  path: number[];
  itemKey: string;
  source: StoredGraphSource;
  deferred?: true;
}

export interface GraphSession {
  version: typeof GRAPH_SESSION_VERSION;
  rootKey: string;
  direction: GraphDirection;
  selections: StoredGraphSelection[];
}

let warnedUnavailableLoad = false;
let warnedUnavailableSave = false;

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, expected: readonly string[]): boolean {
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  return (
    actual.length === required.length &&
    actual.every((key, index) => key === required[index])
  );
}

function isBoundedString(value: unknown, maximum: number): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= maximum;
}

function requireRecipeRef(value: unknown, label: string): RecipeRef {
  if (
    !Array.isArray(value) ||
    value.length !== 2 ||
    !Number.isSafeInteger(value[0]) ||
    value[0] < 0 ||
    !Number.isSafeInteger(value[1]) ||
    value[1] < 0
  ) {
    throw new Error(`${label} is not a valid recipe reference.`);
  }
  return [value[0], value[1]];
}

function requireIngredientSelections(value: unknown, label: string): IngredientSelections {
  if (
    !isRecord(value) ||
    Object.keys(value).length > 256 ||
    Object.entries(value).some(
      ([selectionKey, selectedKey]) =>
        !isBoundedString(selectionKey, MAX_ITEM_KEY_LENGTH) ||
        !isBoundedString(selectedKey, MAX_ITEM_KEY_LENGTH),
    )
  ) {
    throw new Error(`${label} has invalid ingredient selections.`);
  }
  return Object.fromEntries(Object.entries(value)) as IngredientSelections;
}

function requireStoredSource(value: unknown, index: number): StoredGraphSource {
  if (!isRecord(value) || typeof value.kind !== 'string') {
    throw new Error(`Graph selection ${index} has no valid source.`);
  }
  if (value.kind === 'recipe') {
    const expected = [
      ...(value.allowFluidTransfer === undefined ? [] : ['allowFluidTransfer']),
      ...(value.ingredientSelections === undefined ? [] : ['ingredientSelections']),
      'kind',
      'ref',
    ];
    if (
      !hasExactKeys(value, expected) ||
      (value.allowFluidTransfer !== undefined && value.allowFluidTransfer !== true)
    ) {
      throw new Error(`Graph selection ${index} has an invalid recipe source.`);
    }
    const ref = requireRecipeRef(value.ref, `Graph selection ${index} recipe`);
    const ingredientSelections =
      value.ingredientSelections === undefined
        ? undefined
        : requireIngredientSelections(
            value.ingredientSelections,
            `Graph selection ${index} recipe`,
          );
    return {
      kind: 'recipe',
      ref,
      ...(value.allowFluidTransfer === true ? {allowFluidTransfer: true as const} : {}),
      ...(ingredientSelections ? {ingredientSelections} : {}),
    };
  }
  if (
    value.kind === 'mob' &&
    hasExactKeys(value, ['kind', 'mobId']) &&
    isBoundedString(value.mobId, MAX_SOURCE_ID_LENGTH)
  ) {
    return {kind: 'mob', mobId: value.mobId};
  }
  if (
    value.kind === 'block' &&
    hasExactKeys(value, ['blockKey', 'kind']) &&
    isBoundedString(value.blockKey, MAX_ITEM_KEY_LENGTH)
  ) {
    return {kind: 'block', blockKey: value.blockKey};
  }
  throw new Error(`Graph selection ${index} has an unsupported source contract.`);
}

function requireSelection(value: unknown, index: number): StoredGraphSelection {
  const expectedKeys = [
    ...(value && typeof value === 'object' && 'deferred' in value ? ['deferred'] : []),
    'itemKey',
    'path',
    'source',
  ];
  if (
    !isRecord(value) ||
    !hasExactKeys(value, expectedKeys) ||
    (value.deferred !== undefined && value.deferred !== true) ||
    !isBoundedString(value.itemKey, MAX_ITEM_KEY_LENGTH) ||
    !Array.isArray(value.path) ||
    value.path.length > MAX_TREE_DEPTH ||
    value.path.some(
      segment => !Number.isSafeInteger(segment) || segment < 0 || segment > 4096,
    )
  ) {
    throw new Error(`Graph selection ${index} does not satisfy the storage contract.`);
  }
  const source = requireStoredSource(value.source, index);
  if (value.deferred === true && source.kind !== 'recipe') {
    throw new Error(`Graph selection ${index} defers a non-recipe source.`);
  }
  return {
    path: [...value.path] as number[],
    itemKey: value.itemKey,
    source,
    ...(value.deferred === true ? {deferred: true as const} : {}),
  };
}

export function parseGraphSession(raw: string): GraphSession {
  const parsed = JSON.parse(raw) as unknown;
  if (
    !isRecord(parsed) ||
    !hasExactKeys(parsed, ['direction', 'rootKey', 'selections', 'version']) ||
    parsed.version !== GRAPH_SESSION_VERSION ||
    !isBoundedString(parsed.rootKey, MAX_ITEM_KEY_LENGTH) ||
    (parsed.direction !== 'inputs' && parsed.direction !== 'outputs') ||
    !Array.isArray(parsed.selections) ||
    parsed.selections.length > MAX_GRAPH_SESSION_SELECTIONS
  ) {
    throw new Error('Saved graph does not satisfy the versioned storage contract.');
  }
  const selections = parsed.selections.map(requireSelection);
  const expandedPaths = new Set<string>();
  const selectedPaths = new Set<string>();
  for (let index = 0; index < selections.length; index += 1) {
    const selection = selections[index];
    const path = selection.path;
    const pathKey = path.join('.');
    if (selectedPaths.has(pathKey)) {
      throw new Error(`Graph selection ${index} repeats a selected node path.`);
    }
    selectedPaths.add(pathKey);
    if (path.length > 0) {
      const parentKey = path.slice(0, -1).join('.');
      if (!expandedPaths.has(parentKey)) {
        throw new Error(`Graph selection ${index} has no previously expanded parent.`);
      }
    }
    if (!selection.deferred) expandedPaths.add(pathKey);
    if (index === 0 && path.length !== 0) {
      throw new Error(`Graph selection ${index} has no expanded parent.`);
    }
    if (index === 0 && selection.deferred) {
      throw new Error('The graph root cannot be a deferred recipe expansion.');
    }
  }
  return {
    version: GRAPH_SESSION_VERSION,
    rootKey: parsed.rootKey,
    direction: parsed.direction,
    selections,
  };
}

function storedSourceFromNode(source: SourceTreeNode): StoredGraphSource {
  if (source.kind === 'recipe') {
    if (!source.ref) throw new Error(`Expanded recipe source ${source.id} has no recipe reference.`);
    return {
      kind: 'recipe',
      ref: [...source.ref] as RecipeRef,
      ...(source.allowFluidTransfer ? {allowFluidTransfer: true as const} : {}),
      ...(source.ingredientSelections
        ? {ingredientSelections: {...source.ingredientSelections}}
        : {}),
    };
  }
  if (source.kind === 'mob') {
    if (!source.mob?.id) throw new Error(`Expanded mob source ${source.id} has no mob identity.`);
    return {kind: 'mob', mobId: source.mob.id};
  }
  if (!source.blockKey) {
    throw new Error(`Expanded block source ${source.id} has no block identity.`);
  }
  return {kind: 'block', blockKey: source.blockKey};
}

export function serializeGraphSession(
  root: ItemTreeNode,
  direction: GraphDirection,
): GraphSession {
  if (!isBoundedString(root.key, MAX_ITEM_KEY_LENGTH)) {
    throw new Error('The graph root has an invalid item key.');
  }
  const selections: StoredGraphSelection[] = [];
  const visit = (node: ItemTreeNode, path: number[]) => {
    if (path.length > MAX_TREE_DEPTH) {
      throw new Error(`The graph exceeds the persisted depth limit of ${MAX_TREE_DEPTH}.`);
    }
    if (node.deferredRecipeExpansion) {
      selections.push({
        path,
        itemKey: node.key,
        source: {
          kind: 'recipe',
          ref: [...node.deferredRecipeExpansion.ref],
          ...(node.deferredRecipeExpansion.allowFluidTransfer
            ? {allowFluidTransfer: true as const}
            : {}),
          ...(node.deferredRecipeExpansion.ingredientSelections
            ? {
                ingredientSelections: {
                  ...node.deferredRecipeExpansion.ingredientSelections,
                },
              }
            : {}),
        },
        deferred: true,
      });
      if (selections.length > MAX_GRAPH_SESSION_SELECTIONS) {
        throw new Error(
          `The graph exceeds the persisted expansion limit of ${MAX_GRAPH_SESSION_SELECTIONS}.`,
        );
      }
      return;
    }
    if (!node.source) return;
    selections.push({
      path,
      itemKey: node.key,
      source: storedSourceFromNode(node.source),
    });
    if (selections.length > MAX_GRAPH_SESSION_SELECTIONS) {
      throw new Error(
        `The graph exceeds the persisted expansion limit of ${MAX_GRAPH_SESSION_SELECTIONS}.`,
      );
    }
    node.source.inputs.forEach((child, childIndex) => visit(child, [...path, childIndex]));
  };
  visit(root, []);
  return {
    version: GRAPH_SESSION_VERSION,
    rootKey: root.key,
    direction,
    selections,
  };
}

export function graphSessionStorageKey(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
): string {
  return `graphSession:v${GRAPH_SESSION_VERSION}:${descriptor.slug}:${descriptor.publicationId}`;
}

export function loadGraphSession(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
): GraphSession | null {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableLoad) {
        warnedUnavailableLoad = true;
        console.warn('Saved graph restoration is unavailable because localStorage is not present.');
      }
      return null;
    }
    const raw = storage.getItem(graphSessionStorageKey(descriptor));
    return raw === null ? null : parseGraphSession(raw);
  } catch (error) {
    console.error('The saved graph could not be loaded from localStorage.', error);
    try {
      globalThis.localStorage?.removeItem(graphSessionStorageKey(descriptor));
    } catch (cleanupError) {
      console.error('The invalid saved graph could not be removed from localStorage.', cleanupError);
    }
    return null;
  }
}

export function persistGraphSession(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
  root: ItemTreeNode,
  direction: GraphDirection,
): void {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableSave) {
        warnedUnavailableSave = true;
        console.warn('Graph changes are not persistent because localStorage is unavailable.');
      }
      return;
    }
    storage.setItem(
      graphSessionStorageKey(descriptor),
      JSON.stringify(serializeGraphSession(root, direction)),
    );
  } catch (error) {
    console.error('The current graph could not be saved to localStorage.', error);
  }
}

export function clearGraphSession(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
): void {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      console.warn('The invalid saved graph could not be cleared because localStorage is unavailable.');
      return;
    }
    storage.removeItem(graphSessionStorageKey(descriptor));
  } catch (error) {
    console.error('The invalid saved graph could not be cleared from localStorage.', error);
  }
}
