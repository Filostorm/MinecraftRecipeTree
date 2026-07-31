# JEI Recipe Exporter (Forge 1.20.1)

Client-side mod that exports everything JEI knows about to `<gameDir>/jei-exports/`:

## In-game recipe planner

Press **G** while holding an item or hovering an item in JEI to open its Recipe Tree planner.
The key is configurable under Minecraft's **Controls → Recipe Tree** category. The planner:

- draws recipes with JEI's own renderer, so modded layouts and ingredient alternatives remain familiar;
- exports exact vanilla cooking durations and common modded JEI recipe timing accessors for
  production planning in the site viewer; recipes that expose total energy and energy per tick are
  converted to an exact cycle length when the ratio is integral;
- lets you click an ingredient slot to continue planning that next resource;
- calculates the minimum parallel machines for an output amount, recipe cycle time, and deadline;
- uses exported JEI/mod timing when available and leaves other machine cycle times editable because
  JEI has no universal duration field;
- remembers observed inventory items and can treat a machine as unlocked when one of its JEI crafting
  recipes uses only observed items (for example, a furnace after cobblestone is observed);
- lets players manually check a machine or disable progression gating entirely; and
- saves the most recent plan for each target locally in `config/recipe-tree-plans.json`.

The planner only scans the player's small inventory once per second and resolves machine recipes
lazily, keeping the normal game loop independent from large JEI recipe catalogs. Account sync is not
part of this first release; the local plan file is the migration boundary for a later opt-in sync
service.

## Exporting a pack

- **Recipes** — every recipe of every visible JEI category, rendered offscreen through JEI's own
  `IRecipeLayoutDrawable` into PNGs (it looks exactly like the in-game recipe screen), plus
  per-category `recipes.json` with inputs/outputs/catalysts.
- **Ingredients** — every registered JEI ingredient (items, fluids, custom types like gases) rendered
  through its `IIngredientRenderer` into icons. 3D blocks keep their GUI pose, special renderers
  (chests, banners, tridents, glint) are correct because it's the real render path.
- **Mobs** — every `LivingEntity` type from every mod, rendered with the real entity renderers
  into a 16-frame animated sprite sheet (game time + walk cycle advance between frames), plus
  `mobs.json` with stats and loot drops sampled from the real loot tables (600 player-kill rolls
  per mob) and custom death hooks (64 isolated probes) on the integrated server.
- **Block drops** — every block's loot table sampled 512× with the best valid harvesting tool
  (respecting `requiresCorrectToolForDrops`), silk-touch variant included when it differs;
  crops are sampled fully grown. Written to `blockdrops.json`.

## Build

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build
# -> build/libs/jeiexport-1.2.0-beta.1.jar
```

Gradle 8.1.1 / ForgeGradle 6 / Forge 1.20.1. The release accepts Forge 47.1–47.x and
JEI 15.2–15.x. It compiles against the JEI 15.2.0.21 compatibility baseline while dev runs
exercise JEI 15.20.0.130, preventing accidental linkage to newer-only JEI methods.

## Use

Install the jar + JEI 15.x in the pack, join a world, then `/jeiexport all`.
See the [repo root README](../README.md) for the full command reference, output format, and the viewer.

The default command renders 64×64 ingredient canvases (`iconScale: 4`) and 2× JEI recipe
layouts. Those settings, complete failure telemetry in `manifest.diagnostics`, and one preview
PNG per declared recipe are the strict `generic-jei-1.20.1` publication contract. Do not override
`iconScale` for a hosted export; a different scale is valid raw data but intentionally fails that
profile rather than being silently resized.

The 4× canvas preserves high-resolution/custom JEI renderers and avoids viewer-side upscaling,
but it contains 16× the pixels of native 1× icons. That increases GPU readback, PNG encoding,
raw-export storage, staging I/O, and publication processing; compression means file bytes do not
necessarily grow by the full 16×. A separate native-1× quality profile would be the smaller/faster
option for pixel-art-only packs, with the tradeoff that custom-renderer detail may be lost.

### Modpack identity

Every `manifest.json` now includes publication identity alongside the Minecraft version:

```json
"pack": {
  "name": "My Modpack",
  "version": "2.4.1",
  "identitySource": "explicit-request"
}
```

For a deterministic name and version, launch Minecraft with both properties. The exporter permits
an omitted version for private raw snapshots, but hosted publication requires it:

```text
-Djeiexport.packName=My Modpack
-Djeiexport.packVersion=2.4.1
```

The resolver uses this precedence order: explicit JVM properties, CurseForge
`minecraftinstance.json`, Prism/MultiMC's parent `instance.cfg`, Modrinth
`modrinth.index.json`, then the game-directory name. Launcher files are read as bounded UTF-8
regular files (maximum 256 KiB); symlinks, malformed JSON, invalid Unicode controls, conflicting
metadata, and fallback selection are written to the Minecraft log instead of being hidden.
Pack names are limited to 120 Unicode code points and versions to 80. C0/C1 controls plus the
canonical bidirectional and zero-width formatting ranges are rejected. A derived directory name
is convenient for an interactive export, but publishers should use the JVM properties so moving
or renaming an instance cannot change its public identity.

The manifest also records exact, non-truncated failure accounting:

```json
"diagnostics": {
  "failureEvents": 0,
  "failureEventsOmitted": 0
}
```

`diagnostics.failureEvents` always equals `counts.failures` and the number of entries in
`failures.json`; `failureEventsOmitted` is always zero. The publisher rejects schema drift,
partial diagnostics, semantic-error recipes, missing recipe previews, and `QUANTITY_INVALID`
events. Unknown custom ingredient amounts remain explicit as `-1` in the raw snapshot and are
logged as publication-blocking diagnostics; they are never silently converted to quantity `1`.
Custom ingredient classes with no quantity API are explicitly logged and treated as categorical
unit values; item, fluid, and quantified custom stacks retain their runtime counts.

Recipe slot alternatives and category catalysts are exported in full. Sets above 512 entries emit
a warning because large tags can materially increase JSON size and export time, but they are not
silently truncated.

Exports are transactional: the run writes to `.jei-exports.staging-<uuid>` and promotes that
directory only after PNG writes and metadata complete. A failed or cancelled run retains its
staging data for diagnosis and leaves the previous `jei-exports/` snapshot intact. This can
temporarily require disk space for both snapshots.

## Headless / automatic export

Launching the game with `-Djeiexport.auto=all` (or `items`/`recipes`/`mobs`, plus optional
`-Djeiexport.iconScale=N`, `-Djeiexport.packName=...`, and `-Djeiexport.packVersion=...`) starts
the export automatically ~5s after world load — no command needed.

For an unattended CurseForge smoke test, also add
`-Djeiexport.createWorld=true -Djeiexport.worldFolder=RecipeTree-Exporter-Test
-Djeiexport.worldName=RecipeTreeExport -Djeiexport.exitOnComplete=true` to the profile JVM
arguments. The exporter creates one new disposable single-player world, exports after JEI is
ready, and closes Minecraft only after success. It refuses to reuse an existing world folder.

In this dev workspace there's a one-shot task that boots straight into a test world
and exports everything:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew runExportClient
# world 'export-test' must exist in run/saves (generate once via: ./gradlew runServer, then
# copy run/world to run/saves/export-test)
```

Verified on vanilla 1.20.1 + JEI 15.20.0.130, and on a real-mod test pack (Create 6.0.8,
Mekanism 10.4.16, Farmer's Delight 1.3.2, Alex's Mobs 1.22.9): 3,533 items (incl. Mekanism
gas/infusion/pigment/slurry types), 10,345 recipes across 70 categories (incl. Create's
sequenced assembly), 178 mobs, 2 failures (Mekanism blocks whose loot needs a block entity),
10.2s. The test mods are wired in build.gradle as `runtimeOnly fg.deobf("maven.modrinth:…")`
dev-run dependencies — remove or swap them freely; they're not compile deps and don't ship.
Note the `mixin.env.disableRefMap=true` run property: production mods' mixin refmaps target
SRG names and won't apply in the mojmap dev runtime without it.

## Implementation notes

- `JeiExportPlugin` (`@JeiPlugin`) captures `IJeiRuntime` on world load.
- `ExportJob` is a tick-driven state machine (45ms budget per client tick) so the game stays
  responsive; PNG encoding happens on Minecraft's IO pool.
- `OffscreenRenderer` owns a `TextureTarget` framebuffer and mirrors vanilla's GUI projection
  (`setOrtho(0,w,h,0,1000,guiFarPlane)`), reads back with alpha and un-flips.
- `ItemCatalog` dedupes by `<type>|<jei-uid>` key; the recipe phase routes every slot ingredient
  through it, so recipes can never reference a missing catalog entry.
- Every per-recipe/per-entity step is wrapped in catch-all error handling; failures land in
  `failures.json` and the export keeps going (important for big modpacks with broken edge cases).
