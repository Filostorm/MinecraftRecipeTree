import type {DatasetDescriptor} from '../data/datasetCatalog.ts';
import type {IngredientSelections} from '../data/ingredientAlternativeSelection.ts';
import type {RecipeRef} from '../types.ts';
import type {
  GraphSession,
  StoredGraphSelection,
  StoredGraphSource,
} from './graphSession.ts';

export const PORTABLE_TREE_FORMAT = 'minecraft-recipe-tree';
export const PORTABLE_TREE_VERSION = 1;
export const MAX_PORTABLE_TREE_BYTES = 1_048_576;

export type PortableTreeSource =
  | {
      kind: 'recipe';
      recipeKey: string;
      ref?: RecipeRef;
      allowFluidTransfer?: true;
      ingredientSelections?: IngredientSelections;
    }
  | {kind: 'mob'; mobId: string}
  | {kind: 'block'; blockKey: string};

export interface PortableTreeSelection {
  path: number[];
  itemKey: string;
  source: PortableTreeSource;
  deferred?: true;
}

export interface PortableRecipeTree {
  format: typeof PORTABLE_TREE_FORMAT;
  version: typeof PORTABLE_TREE_VERSION;
  createdAt: string;
  pack: {
    minecraftVersion: string;
    name?: string;
    version?: string;
    slug?: string;
    publicationId?: string;
  };
  rootKey: string;
  direction: GraphSession['direction'];
  productionPlan?: GraphSession['productionPlan'];
  selections: PortableTreeSelection[];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function boundedString(value: unknown, max = 512): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= max;
}

function parseRecipeRef(value: unknown): RecipeRef | undefined {
  if (value === undefined) return undefined;
  if (
    !Array.isArray(value) ||
    value.length !== 2 ||
    value.some(part => !Number.isSafeInteger(part) || part < 0)
  ) {
    throw new Error('A shared recipe has an invalid web reference.');
  }
  return [value[0], value[1]];
}

function parseSelections(value: unknown): PortableTreeSelection[] {
  if (!Array.isArray(value) || value.length > 2048) {
    throw new Error('The shared tree has too many selections.');
  }
  return value.map((entry, index) => {
    if (
      !isRecord(entry) ||
      !boundedString(entry.itemKey) ||
      !Array.isArray(entry.path) ||
      entry.path.length > 64 ||
      entry.path.some(segment => !Number.isSafeInteger(segment) || segment < 0 || segment > 4096) ||
      !isRecord(entry.source) ||
      (entry.deferred !== undefined && entry.deferred !== true)
    ) {
      throw new Error(`Shared tree selection ${index + 1} is invalid.`);
    }
    const source = entry.source;
    let parsedSource: PortableTreeSource;
    if (source.kind === 'recipe' && boundedString(source.recipeKey, 1024)) {
      const ref = parseRecipeRef(source.ref);
      const selections = source.ingredientSelections;
      if (
        (source.allowFluidTransfer !== undefined && source.allowFluidTransfer !== true) ||
        (selections !== undefined &&
          (!isRecord(selections) ||
            Object.keys(selections).length > 256 ||
            Object.entries(selections).some(
              ([key, selected]) => !boundedString(key) || !boundedString(selected),
            )))
      ) {
        throw new Error(`Shared recipe selection ${index + 1} is invalid.`);
      }
      parsedSource = {
        kind: 'recipe',
        recipeKey: source.recipeKey,
        ...(ref ? {ref} : {}),
        ...(source.allowFluidTransfer === true ? {allowFluidTransfer: true as const} : {}),
        ...(selections ? {ingredientSelections: {...selections} as IngredientSelections} : {}),
      };
    } else if (source.kind === 'mob' && boundedString(source.mobId)) {
      parsedSource = {kind: 'mob', mobId: source.mobId};
    } else if (source.kind === 'block' && boundedString(source.blockKey)) {
      parsedSource = {kind: 'block', blockKey: source.blockKey};
    } else {
      throw new Error(`Shared tree selection ${index + 1} has an unsupported source.`);
    }
    return {
      path: [...entry.path] as number[],
      itemKey: entry.itemKey,
      source: parsedSource,
      ...(entry.deferred === true ? {deferred: true as const} : {}),
    };
  });
}

export function parsePortableTree(raw: string): PortableRecipeTree {
  if (new TextEncoder().encode(raw).byteLength > MAX_PORTABLE_TREE_BYTES) {
    throw new Error('The shared tree is larger than the 1 MiB import limit.');
  }
  const value = JSON.parse(raw) as unknown;
  if (
    !isRecord(value) ||
    value.format !== PORTABLE_TREE_FORMAT ||
    value.version !== PORTABLE_TREE_VERSION ||
    !boundedString(value.createdAt, 64) ||
    Number.isNaN(Date.parse(value.createdAt)) ||
    !isRecord(value.pack) ||
    !boundedString(value.pack.minecraftVersion, 40) ||
    !boundedString(value.rootKey) ||
    (value.direction !== 'inputs' && value.direction !== 'outputs')
  ) {
    throw new Error('This is not a supported Minecraft Recipe Tree share.');
  }
  const pack = value.pack;
  const optionalPackFields = ['name', 'version', 'slug', 'publicationId'] as const;
  for (const field of optionalPackFields) {
    if (pack[field] !== undefined && !boundedString(pack[field], 160)) {
      throw new Error(`The shared tree has an invalid pack ${field}.`);
    }
  }
  let productionPlan: GraphSession['productionPlan'];
  if (value.productionPlan !== undefined) {
    const plan = value.productionPlan;
    if (
      !isRecord(plan) ||
      typeof plan.amount !== 'number' ||
      !Number.isFinite(plan.amount) ||
      plan.amount <= 0 ||
      plan.amount > 1_000_000_000_000 ||
      typeof plan.windowSeconds !== 'number' ||
      !Number.isFinite(plan.windowSeconds) ||
      plan.windowSeconds <= 0 ||
      (plan.cycleSeconds !== undefined &&
        (typeof plan.cycleSeconds !== 'number' ||
          !Number.isFinite(plan.cycleSeconds) ||
          plan.cycleSeconds <= 0))
    ) {
      throw new Error('The shared tree has an invalid production plan.');
    }
    productionPlan = {
      amount: plan.amount,
      windowSeconds: plan.windowSeconds,
      ...(typeof plan.cycleSeconds === 'number' ? {cycleSeconds: plan.cycleSeconds} : {}),
    };
  }
  return {
    format: PORTABLE_TREE_FORMAT,
    version: PORTABLE_TREE_VERSION,
    createdAt: value.createdAt,
    pack: {
      minecraftVersion: pack.minecraftVersion,
      ...Object.fromEntries(
        optionalPackFields
          .filter(field => pack[field] !== undefined)
          .map(field => [field, pack[field]]),
      ),
    },
    rootKey: value.rootKey,
    direction: value.direction,
    ...(productionPlan ? {productionPlan} : {}),
    selections: parseSelections(value.selections),
  } as PortableRecipeTree;
}

function portableSource(
  source: StoredGraphSource,
  recipeKeys: ReadonlyMap<string, string>,
): PortableTreeSource {
  if (source.kind !== 'recipe') return {...source};
  const refKey = source.ref.join(':');
  const recipeKey = recipeKeys.get(refKey);
  if (!recipeKey) {
    throw new Error(`Recipe ${refKey} has no stable JEI identity and cannot be shared cross-platform.`);
  }
  return {
    kind: 'recipe',
    recipeKey,
    ref: [...source.ref],
    ...(source.allowFluidTransfer ? {allowFluidTransfer: true as const} : {}),
    ...(source.ingredientSelections
      ? {ingredientSelections: {...source.ingredientSelections}}
      : {}),
  };
}

export function buildPortableTree(
  session: GraphSession,
  descriptor: DatasetDescriptor,
  recipeKeys: ReadonlyMap<string, string>,
  createdAt = new Date().toISOString(),
): PortableRecipeTree {
  return {
    format: PORTABLE_TREE_FORMAT,
    version: PORTABLE_TREE_VERSION,
    createdAt,
    pack: {
      minecraftVersion: descriptor.minecraftVersion,
      name: descriptor.displayName,
      version: descriptor.packVersion,
      slug: descriptor.slug,
      publicationId: descriptor.publicationId,
    },
    rootKey: session.rootKey,
    direction: session.direction,
    ...(session.productionPlan ? {productionPlan: {...session.productionPlan}} : {}),
    selections: session.selections.map(selection => ({
      path: [...selection.path],
      itemKey: selection.itemKey,
      source: portableSource(selection.source, recipeKeys),
      ...(selection.deferred ? {deferred: true as const} : {}),
    })),
  };
}

export function portableSelectionAsStored(
  selection: PortableTreeSelection,
  ref?: RecipeRef,
): StoredGraphSelection {
  if (selection.source.kind !== 'recipe') {
    return {
      path: [...selection.path],
      itemKey: selection.itemKey,
      source: {...selection.source},
      ...(selection.deferred ? {deferred: true as const} : {}),
    };
  }
  if (!ref) throw new Error(`Recipe ${selection.source.recipeKey} is unavailable in this pack.`);
  return {
    path: [...selection.path],
    itemKey: selection.itemKey,
    source: {
      kind: 'recipe',
      ref: [...ref],
      ...(selection.source.allowFluidTransfer ? {allowFluidTransfer: true as const} : {}),
      ...(selection.source.ingredientSelections
        ? {ingredientSelections: {...selection.source.ingredientSelections}}
        : {}),
    },
    ...(selection.deferred ? {deferred: true as const} : {}),
  };
}
