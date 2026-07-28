import type {DatasetDescriptor} from '../data/datasetCatalog';
import type {RecipeRef} from '../types';
import type {GraphDirection} from './direction';
import type {ItemTreeNode, SourceTreeNode} from './model';

const GRAPH_SESSION_VERSION = 1;
const MAX_ITEM_KEY_LENGTH = 512;
const MAX_SOURCE_ID_LENGTH = 512;
const MAX_TREE_DEPTH = 64;
export const MAX_GRAPH_SESSION_SELECTIONS = 2048;

export type StoredGraphSource =
  | {kind: 'recipe'; ref: RecipeRef; allowFluidTransfer?: true}
  | {kind: 'mob'; mobId: string}
  | {kind: 'block'; blockKey: string};

export interface StoredGraphSelection {
  path: number[];
  itemKey: string;
  source: StoredGraphSource;
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
  return actual.length === required.length && actual.every((key, index) => key === required[index]);
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

function requireStoredSource(value: unknown, index: number): StoredGraphSource {
  if (!isRecord(value) || typeof value.kind !== 'string') {
    throw new Error(`Graph selection ${index} has no valid source.`);
  }
  if (value.kind === 'recipe') {
    const expected =
      value.allowFluidTransfer === undefined
        ? ['kind', 'ref']
        : ['allowFluidTransfer', 'kind', 'ref'];
    if (
      !hasExactKeys(value, expected) ||
      (value.allowFluidTransfer !== undefined && value.allowFluidTransfer !== true)
    ) {
      throw new Error(`Graph selection ${index} has an invalid recipe source.`);
    }
    const ref = requireRecipeRef(value.ref, `Graph selection ${index} recipe`);
    return value.allowFluidTransfer === true
      ? {kind: 'recipe', ref, allowFluidTransfer: true}
      : {kind: 'recipe', ref};
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
  if (
    !isRecord(value) ||
    !hasExactKeys(value, ['itemKey', 'path', 'source']) ||
    !isBoundedString(value.itemKey, MAX_ITEM_KEY_LENGTH) ||
    !Array.isArray(value.path) ||
    value.path.length > MAX_TREE_DEPTH ||
    value.path.some(segment => !Number.isSafeInteger(segment) || segment < 0 || segment > 4096)
  ) {
    throw new Error(`Graph selection ${index} does not satisfy the storage contract.`);
  }
  return {
    path: [...value.path] as number[],
    itemKey: value.itemKey,
    source: requireStoredSource(value.source, index),
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
  for (let index = 0; index < selections.length; index += 1) {
    const path = selections[index].path;
    const pathKey = path.join('.');
    if (expandedPaths.has(pathKey)) {
      throw new Error(`Graph selection ${index} repeats an expanded node path.`);
    }
    if (path.length > 0) {
      const parentKey = path.slice(0, -1).join('.');
      if (!expandedPaths.has(parentKey)) {
        throw new Error(`Graph selection ${index} has no previously expanded parent.`);
      }
    }
    expandedPaths.add(pathKey);
    if (index === 0 && path.length !== 0) {
      throw new Error(`Graph selection ${index} has no expanded parent.`);
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
    return source.allowFluidTransfer
      ? {kind: 'recipe', ref: [...source.ref] as RecipeRef, allowFluidTransfer: true}
      : {kind: 'recipe', ref: [...source.ref] as RecipeRef};
  }
  if (source.kind === 'mob') {
    if (!source.mob?.id) throw new Error(`Expanded mob source ${source.id} has no mob identity.`);
    return {kind: 'mob', mobId: source.mob.id};
  }
  if (!source.blockKey) throw new Error(`Expanded block source ${source.id} has no block identity.`);
  return {kind: 'block', blockKey: source.blockKey};
}

export function serializeGraphSession(root: ItemTreeNode, direction: GraphDirection): GraphSession {
  if (!isBoundedString(root.key, MAX_ITEM_KEY_LENGTH)) {
    throw new Error('The graph root has an invalid item key.');
  }
  const selections: StoredGraphSelection[] = [];
  const visit = (node: ItemTreeNode, path: number[]) => {
    if (!node.source) return;
    if (path.length > MAX_TREE_DEPTH) {
      throw new Error(`The graph exceeds the persisted depth limit of ${MAX_TREE_DEPTH}.`);
    }
    selections.push({path, itemKey: node.key, source: storedSourceFromNode(node.source)});
    if (selections.length > MAX_GRAPH_SESSION_SELECTIONS) {
      throw new Error(
        `The graph exceeds the persisted expansion limit of ${MAX_GRAPH_SESSION_SELECTIONS}.`,
      );
    }
    node.source.inputs.forEach((child, childIndex) => visit(child, [...path, childIndex]));
  };
  visit(root, []);
  return {version: GRAPH_SESSION_VERSION, rootKey: root.key, direction, selections};
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
