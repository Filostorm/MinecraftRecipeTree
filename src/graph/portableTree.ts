import type {DatasetDescriptor} from '../data/datasetCatalog.ts';
import type {IngredientSelections} from '../data/ingredientAlternativeSelection.ts';
import type {Recipe, RecipeRef, SlotEntry} from '../types.ts';
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

export interface ConnectedPortableSelectionSkip {
  selection: PortableTreeSelection;
  reason: 'unavailable' | 'dependent' | 'duplicate';
  error?: unknown;
}

export interface ConnectedPortableSelectionResolution {
  selections: StoredGraphSelection[];
  skipped: ConnectedPortableSelectionSkip[];
}

function plainDecimal(value: number): string {
  if (!Number.isFinite(value)) {
    throw new Error(`Recipe quantity ${String(value)} cannot be used in a stable recipe identity.`);
  }
  const raw = String(value);
  if (!/[eE]/u.test(raw)) return raw;
  const [coefficient, exponentText] = raw.toLowerCase().split('e');
  const exponent = Number(exponentText);
  const negative = coefficient.startsWith('-');
  const unsigned = negative ? coefficient.slice(1) : coefficient;
  const [integer, fraction = ''] = unsigned.split('.');
  const digits = integer + fraction;
  const decimalIndex = integer.length + exponent;
  const expanded =
    decimalIndex <= 0
      ? `0.${'0'.repeat(-decimalIndex)}${digits}`
      : decimalIndex >= digits.length
        ? `${digits}${'0'.repeat(decimalIndex - digits.length)}`
        : `${digits.slice(0, decimalIndex)}.${digits.slice(decimalIndex)}`;
  return negative ? `-${expanded}` : expanded;
}

function appendIdentityField(target: string[], value: string): void {
  // Java String.length() and JavaScript string.length both count UTF-16 code units.
  target.push(`${value.length}:${value};`);
}

function appendIdentitySlots(
  target: string[],
  role: 'inputs' | 'outputs',
  slots: readonly (readonly SlotEntry[])[],
): void {
  appendIdentityField(target, role);
  target.push(`${slots.length};`);
  for (const slot of slots) {
    if (slot.length === 0) {
      throw new Error(`Stable recipe identity cannot encode an empty ${role} slot.`);
    }
    const alternatives = slot
      .map(entry => `${entry[0]}\0${plainDecimal(entry[1])}`)
      .sort();
    target.push(`${alternatives.length};`);
    for (const alternative of alternatives) appendIdentityField(target, alternative);
  }
}

async function sha256Hex(value: string): Promise<string> {
  const cryptoApi = globalThis.crypto;
  if (!cryptoApi?.subtle) {
    throw new Error('Web Crypto is required to resolve semantic recipe identities.');
  }
  const digest = await cryptoApi.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)]
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Reconstruct the exact identity emitted by the 1.12 in-game recipe viewer.
 * Older hosted publications do not carry recipe.id for every JEI wrapper, so
 * matching their ordered input/output semantics is the only stable option.
 */
export async function portableRecipeKey(
  categoryId: string,
  recipe: Recipe,
): Promise<string | null> {
  if (recipe.id) return `${categoryId}|${recipe.id}`;
  if (!recipe.out?.length) return null;
  const canonical = ['recipe-tree-semantic-v1;'];
  appendIdentityField(canonical, categoryId);
  appendIdentitySlots(canonical, 'inputs', recipe.in ?? []);
  appendIdentitySlots(canonical, 'outputs', recipe.out);
  return `${categoryId}|semantic-v1:${await sha256Hex(canonical.join(''))}`;
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

/** Refuse to resolve stable recipe IDs against a different selected pack release. */
export function assertPortableTreePackMatches(
  share: PortableRecipeTree,
  descriptor: Pick<
    DatasetDescriptor,
    'minecraftVersion' | 'displayName' | 'packVersion' | 'slug' | 'publicationId'
  >,
): void {
  if (share.pack.minecraftVersion !== descriptor.minecraftVersion) {
    throw new Error(
      `This history is for Minecraft ${share.pack.minecraftVersion}; the selected pack uses ${descriptor.minecraftVersion}.`,
    );
  }
  if (share.pack.name !== undefined && share.pack.name !== descriptor.displayName) {
    throw new Error(
      `This history is for ${share.pack.name}; the selected pack is ${descriptor.displayName}. Select the matching pack before opening it.`,
    );
  }
  if (
    share.pack.publicationId !== undefined &&
    share.pack.publicationId !== descriptor.publicationId
  ) {
    throw new Error(
      `This history belongs to a different publication of ${share.pack.name ?? 'the pack'}. Select its matching pack version before opening it.`,
    );
  }
  if (share.pack.slug !== undefined && share.pack.slug !== descriptor.slug) {
    throw new Error(
      `This history is for ${share.pack.name ?? share.pack.slug}; select that pack before opening it.`,
    );
  }
  if (share.pack.version !== undefined && share.pack.version !== descriptor.packVersion) {
    throw new Error(
      `This history is for pack version ${share.pack.version}; the selected ${descriptor.displayName} publication is ${descriptor.packVersion}.`,
    );
  }
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

/**
 * Resolve only selections whose expanded parent was successfully retained.
 * A missing recipe therefore removes its dependent branch without dropping
 * valid sibling branches or leaving selections that cannot be reconstructed.
 */
export async function resolveConnectedPortableSelections(
  selections: readonly PortableTreeSelection[],
  resolveSelection: (
    selection: PortableTreeSelection,
  ) => Promise<StoredGraphSelection>,
): Promise<ConnectedPortableSelectionResolution> {
  const resolved: StoredGraphSelection[] = [];
  const skipped: ConnectedPortableSelectionSkip[] = [];
  const selectedPaths = new Set<string>();
  const expandedPaths = new Set<string>();

  for (const selection of selections) {
    const pathKey = selection.path.join('.');
    if (selectedPaths.has(pathKey)) {
      skipped.push({selection, reason: 'duplicate'});
      continue;
    }
    selectedPaths.add(pathKey);

    if (selection.path.length > 0) {
      const parentPath = selection.path.slice(0, -1).join('.');
      if (!expandedPaths.has(parentPath)) {
        skipped.push({selection, reason: 'dependent'});
        continue;
      }
    }

    try {
      const stored = await resolveSelection(selection);
      resolved.push(stored);
      if (!stored.deferred) expandedPaths.add(pathKey);
    } catch (error) {
      skipped.push({selection, reason: 'unavailable', error});
    }
  }

  return {selections: resolved, skipped};
}
