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
  /**
   * Canonical modpack identity emitted by current exporters. It remains optional while the
   * existing pre-identity publication is online; new hosted publications require it.
   */
  pack?: PackIdentity;
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
    packedImages: 'coordinate-v1';
    maxPackBytes: number;
    shardedJson: 'mrt-sharded-json-v1';
    maxShardBytes: number;
    recipeImages?:
      | {mode: 'included'}
      | {
          mode: 'omitted';
          reason: 'hosting-archive-budget';
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
 * [catalog key, amount, optional logical ingredient id]. The third field preserves
 * identities such as Forge 1.12 OreDictionary entries (`ore:ingotCopper`) while
 * retaining the resolved catalog alternatives used for icons and source picking.
 * In `in`/`out`, amount <= 0 means an explicitly unknown dynamic flow.
 * In `cat`, a positive amount is the minimum non-consumed reservoir/threshold requirement.
 */
export type SlotEntry = [string, number, string?];

export interface Recipe {
  /** Present when the export of this recipe failed; image/slots missing. */
  err?: boolean;
  /** Recipe registry id when JEI knows it */
  id?: string;
  /** Optional legacy screenshot filename or packed coordinate; absent in structured publications. */
  img?: string;
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
  icon: string;
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
