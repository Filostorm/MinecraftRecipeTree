/**
 * Data contract for the jei-exports folder produced by the Forge mod
 * (recipe-export-mod). See the repo root README for details.
 */

export interface Manifest {
  /** Content-derived SHA-256 identity of the finalized published export. */
  publicationId: string;
  format: number;
  generatedAt: string;
  durationMs: number;
  aborted: boolean;
  minecraft: string;
  /** Export-quality profile bound into the immutable publication manifest. */
  profile?: string;
  /** Exact public-use policy for profiles whose hosted representation is intentionally restricted. */
  publicationPolicy?: string;
  /**
   * Canonical modpack identity emitted by current exporters. It remains optional while the
   * existing pre-identity publication is online; new hosted publications require it.
   */
  pack?: PackIdentity;
  /** Normalized dataset-source licensing metadata for profiles that require attribution. */
  attribution?: DatasetAttribution;
  settings: { iconScale: number; recipeScale: number; mobCanvas: number };
  counts: {
    items: number;
    recipes: number;
    categories: number;
    mobs: number;
    failures: number;
  };
  mods: Record<string, string>;
  /** Present after the export has been transformed for bounded web publication. */
  web?: {
    format: 2;
    /** Absent only when the publication carries no raster/image pack objects. */
    packedImages?: 'coordinate-v1';
    /** Absent only when the publication carries no raster/image pack objects. */
    maxPackBytes?: number;
    shardedJson: 'mrt-sharded-json-v1';
    maxShardBytes: number;
    /** Exact accounting boundary for publications that intentionally contain no exported artwork. */
    visualAssets?: {
      format: 'mrt-visual-assets-policy-v1';
      mode: 'structured-data-only';
      policy: 'gtnh-structured-data-only-v1';
      itemIcons: 0;
      categoryIcons: 0;
      recipePreviews: 0;
      mobSprites: 0;
      packedImageFiles: 0;
    };
    recipeImages?:
      | {mode: 'included'}
      | {
          mode: 'omitted';
          reason: 'hosting-archive-budget' | 'third-party-artwork-rights-not-cleared';
          /** Present only for an immutable policy-driven omission. */
          policy?: 'gtnh-structured-data-only-v1';
          references: number;
          files: number;
          /** New publications use png; absence identifies the legacy WebP-byte accounting contract. */
          encoding?: 'png';
          bytes: number;
          inventory: {
            format: 'mrt-recipe-image-inventory-v1';
            sha256: string;
            entries: number;
            previews: number;
            missing: number;
          };
        };
  };
}

export interface DatasetAttribution {
  sourceUrl: string;
  projectUrl: string;
  licenseIdentifier: string;
  licenseUrl: string;
}

export interface PackIdentity {
  /** User-facing canonical pack name, distinct from a mutable local instance name. */
  name: string;
  /** Pack release/version. Current publication tooling requires it. */
  version?: string;
  identitySource:
    | 'explicit-request'
    | 'curseforge'
    | 'prism'
    | 'modrinth-index'
    | 'game-directory';
  instanceName?: string;
  provider?: 'curseforge' | 'prism' | 'modrinth';
  projectId?: string;
  versionId?: string;
}

export interface ShardedJsonPart {
  /** Export-root-relative JSON path. */
  path: string;
  /** Logical offset; required for array documents and absent for object documents. */
  start?: number;
  count: number;
  /** Exact UTF-8 size of the JSON shard. */
  bytes: number;
}

/** Descriptor replacing a logical JSON array/object whose inline form exceeds 8 MiB. */
export interface ShardedJsonDescriptor {
  format: 'mrt-sharded-json-v1';
  kind: 'array' | 'object';
  count: number;
  parts: ShardedJsonPart[];
}

/** One ingredient: item, fluid, or any custom JEI ingredient type. */
export interface CatalogItem {
  /** Stable key other files reference: "item|minecraft:stone" */
  k: string;
  /** Registry id, e.g. "minecraft:stone" */
  id: string;
  /** Display name */
  n: string;
  /** Mod id */
  m: string;
  /** Type prefix when not "item" (e.g. "fluid", "gasstack") */
  t?: string;
  /** Icon path relative to the export root */
  icon?: string;
}

export interface Category {
  id: string;
  title: string;
  /** Directory (relative to export root) holding recipes.json and images */
  dir: string;
  count: number;
  icon?: string;
  /** Keys of machines/blocks that perform these recipes */
  catalysts: string[];
}

/**
 * [catalog key, amount, optional logical ingredient id, optional occurrence probability].
 * The third field preserves identities such as Forge 1.12 OreDictionary entries
 * (`ore:ingotCopper`) while retaining the resolved catalog alternatives used for
 * icons and source picking. Format-v2 exports use a null third field when a
 * stochastic input/output has no logical ingredient id; the fourth field is its
 * strict 0..1 exclusive consumption/production probability. Probabilities are
 * forbidden in `cat` because catalysts are deterministic retained requirements.
 * In `in`/`out`, amount <= 0 means an explicitly unknown dynamic flow.
 * In `cat`, a positive amount is the minimum non-consumed reservoir/threshold requirement.
 */
export type SlotEntry =
  | [catalogKey: string, amount: number]
  | [catalogKey: string, amount: number, logicalIngredientId: string]
  | [
      catalogKey: string,
      amount: number,
      logicalIngredientId: string | null,
      occurrenceProbability: number,
    ];

/** Compact deterministic representative of one exported multiblock structure. */
export interface RecipeStructure {
  /** Width, height, and depth in blocks. */
  size: [x: number, y: number, z: number];
  /** Exact number of occupied positions, including the controller. */
  total: number;
  /** Catalog key for the controller block. */
  controller: string;
  /** Exact build-list counts using the same representative states as the in-game preview. */
  blocks: [catalogKey: string, count: number][];
  /** Relative x/y/z positions used for the lightweight rotatable preview. */
  cells: [x: number, y: number, z: number, catalogKey: string][];
}

export interface Recipe {
  /** Present when the export of this recipe failed; image/slots missing. */
  err?: boolean;
  /** Recipe registry id when JEI knows it */
  id?: string;
  /** GameStages/RecipeStages gate required to use this recipe, when exported. */
  stage?: string;
  /** Optional legacy screenshot filename or packed coordinate; absent in structured publications. */
  img?: string;
  /** Optional shared base layer rendered beneath img for exact composited JEI previews. */
  bg?: string;
  /** Processing duration in game ticks when the exporter can determine it exactly. */
  durationTicks?: number;
  /** Optional multiblock geometry and exact material counts from a structure-preview recipe. */
  structure?: RecipeStructure;
  /** Logical (GUI-pixel) size of the image; actual PNG is scaled up */
  w?: number;
  h?: number;
  /** Input slots; each slot lists its variants (tag ingredients have many) */
  in?: SlotEntry[][];
  out?: SlotEntry[][];
  /** Catalyst slots (shown but not consumed) */
  cat?: SlotEntry[][];
}

/** [category index into categories.json, recipe index into that recipes.json] */
export type RecipeRef = [number, number];

export type RecipeIndex = Record<string, { p?: RecipeRef[]; u?: RecipeRef[] }>;

/** Sampled loot statistics for one item. */
export interface DropStat {
  k: string;
  /** Chance per kill/break (0..1) */
  c: number;
  /** Stack totals when it does drop */
  min: number;
  max: number;
  /** Average per kill/break overall */
  avg: number;
}

export interface Mob {
  id: string;
  n: string;
  m: string;
  /** Sprite sheet of `frames` square frames side by side (single frame when frames missing) */
  /** Sprite sheet path; intentionally absent in structured-data-only publications. */
  icon?: string;
  frames?: number;
  fps?: number;
  /** Bounding box in blocks */
  w: number;
  h: number;
  hp?: number;
  cat: string;
  drops?: DropStat[];
}

/** blockdrops.json: keyed by the block item's catalog key. */
export interface BlockDropEntry {
  /** Registry id of the best harvesting tool, or "hand" */
  tool: string;
  drops: DropStat[];
  /** Present when silk touch yields something different */
  silk?: DropStat[];
}

export interface BlockDropsFile {
  blocks: Record<string, BlockDropEntry>;
}
