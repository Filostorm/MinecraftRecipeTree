# Recipe Tree GTNH NEI exporter (Minecraft 1.7.10)

This isolated client-only Forge mod exports the runtime recipe presentation registered in
NotEnoughItems (NEI) by **GT New Horizons 2.8.4** into Recipe Tree viewer format v2. Format v2
adds an optional output-probability tuple field; the same web app remains compatible with existing
format-v1 datasets.
It is intentionally pinned to:

- Minecraft `1.7.10`
- Forge `10.13.4.1614`
- NotEnoughItems `2.8.44-GTNH`
- GT New Horizons `2.8.4`
- the exact NEI API JAR SHA-256
  `c3f0136f68a74c010593a51ecd3414c4eb8d861bebfe357a19e518a033aca92b`

The exported manifest attributes the normalized GTNH modpack recipe data to the pinned
[GT New Horizons 2.8.4 source](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4)
under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). That attribution
does not assert that third-party mod artwork, item textures, or other bundled assets are covered
by the modpack's license; those remain subject to their respective authors' licenses.

The generic path calls public Minecraft, Forge, and NEI APIs. A closed set of explicitly documented
GTNH adapters uses exact reflection where those pinned implementations expose no public complete-
category or semantic contract, including NEI's package-private transfer-rectangle descriptor.
Every such source JAR is version- and SHA-256-pinned before reflection runs; no GTNH or NEI
implementation source is copied into this module.

## Why this is a runtime exporter

GTNH recipes are assembled and mutated during mod initialization. They are not present as one
complete file-backed database. NEI is the pack's integrated recipe presentation layer and sees
normal crafting, GregTech machines, and the many mod-specific handlers after registration.
Consequently, the reproducible input is a fully initialized pinned client, not a source/config
parser.

This approach preserves what the player can actually inspect in NEI. Its limitation is equally
important: NEI exposes positioned item stacks, not every typed machine property. EU/t, duration,
chance, fluid typing, and handler-specific metadata may only exist visually inside the rendered
widget. A future typed-registry exporter can add those fields, but it must run in parallel with the
NEI coverage audit rather than silently replacing NEI semantics.

## Build

RetroFuturaGradle 2.0.2 currently requires a Gradle daemon on JDK 25 or newer. The compiled mod is
still constrained to Java 8 API signatures and class-file version 52 for Minecraft compatibility.

Every configuration requires the exact NEI, DreamCore, and GregTech API JARs explicitly. There is no Maven or
local-name fallback:

```bash
cd recipe-export-mod-1.7.10
export GTNH_EXPORT_BUILD_JAVA="$(/usr/libexec/java_home -v 26)"
JAVA_HOME="$GTNH_EXPORT_BUILD_JAVA" \
PATH="$GTNH_EXPORT_BUILD_JAVA/bin:$PATH" \
./gradlew releaseBuild \
  -PneiApiJar="/absolute/path/to/NotEnoughItems-2.8.44-GTNH.jar" \
  -PdreamCoreJar="/absolute/path/to/GTNewHorizonsCoreMod-2.7.268.jar" \
  -PgregTechApiJar="/absolute/path/to/gregtech-5.09.51.482.jar"
```

On a non-macOS builder, set `GTNH_EXPORT_BUILD_JAVA` to an installed JDK 25+ home. This JDK is
only for Gradle/RFG: the supervised GTNH client still runs on its pinned Java 17 runtime, and the
release verification must continue to report class-file major version 52.

The `1.0.78` production candidate is built at:

```text
build/libs/recipe-tree-gtnh-nei-exporter-1.0.78.jar
```

The `1.0.78` artifact is 808,599 bytes with outer SHA-256
`ffd05cef2e05169ab7b519adfb13a9a0b1b9d328b786027a182deb484699c855` and embedded canonical-
payload SHA-256 `66e8fb8f410380f3261422c79648ea4d76800bc1ccd681691c77976fbdb00ce1`.
Two clean `releaseBuild` executions reproduced it byte-for-byte with all 398 tests passing and
Java 8 class-file major version 52. Version `1.0.78` retains the complete 330-handler inventory, 22 verified
empty-category exclusions, one exact source-relative unbound material-template exclusion, the
exact three-stack ProjectBlue owner-renderer lease, and all 43 runtime-artifact pins required by
the semantic policies. It additionally source-binds GregTech Scanner row 3's synthetic Forestry
`Scanned Sapling` output to one exact recipe occurrence, verifies its 132 genetic input alternatives,
and source-binds row 8's synthetic Forestry `Scanned Pollen` output under an independent one-use
capability with its own 132-alternative digest. Any corpus, artifact, handler, or policy drift aborts
before publication.

The immutable failed `1.0.77` artifact is 790,042 bytes with outer SHA-256
`eb07b49c2e6d9af896a33874a49cc99edc6171e9cb38b29f71552a3bee4dd6c6` and embedded payload SHA-256
`b32746e253fbc869e0b3733b40ba841c76ff6e9f8992792e75dae8a2b5d18a43`. It reproduced across two
clean builds with 382 passing tests and Java 8 class-file major version 52, then failed closed on
GregTech Scanner row 8's separate synthetic Forestry `Scanned Pollen` output after successfully
consuming the exact row-3 Scanned Sapling capability. No final dataset was published. Its evidence
is preserved under `../gtnh-instance/diagnostics/failed-production-1.0.77-forestry-pollen-null-species-display-name-2026-07-21T170542Z/`.

The immutable failed `1.0.76` artifact is 770,506 bytes with outer SHA-256
`9384185dfc0b14e705044e733952dee206387adcdcb87888e2d43f6892082b70` and embedded payload SHA-256
`af66395ec62f3e2e15839e5bfcf26d9d496ef63fbfdd31afeac21867a240eb12`. It reproduced across two
clean builds with 368 passing tests and Java 8 class-file major version 52, then failed closed when
Forestry dereferenced the absent genome on GregTech Scanner row 3's intentionally synthetic,
custom-named sapling output. No final dataset was published. Its byte-verified evidence is preserved
under `../gtnh-instance/diagnostics/failed-production-1.0.76-forestry-sapling-null-species-display-name-2026-07-21T162031Z/`.

The immutable failed `1.0.75` artifact is 770,054 bytes with outer SHA-256
`19fd20e67d298c5a32d2f9abb9b0563a2b808180a69365d561f99517c42aed84` and embedded payload SHA-256
`ebbf389baa5242c16c4ab0c9c4f88c8eee67bb7d0d6b9abc5a1482fb8b680f7c`. It reproduced across two
clean builds with 368 passing tests and Java 8 class-file major version 52, then failed closed on
the first metadata-10 Runed Stone because its adapter incorrectly equated Thaumcraft's inherited
metadata-zero `ItemBlock` stack icon (`thaumcraft:obsidiantile`) with the exact metadata-10 owner
sprite (`thaumcraft:es_5`). No final dataset was published. Its byte-verified evidence is preserved
under `../gtnh-instance/diagnostics/failed-production-1.0.75-thaumcraft-stitched-owner-sprite-binding-drift-2026-07-21T155944Z/`.

The immutable failed `1.0.74` artifact is 759,308 bytes with outer SHA-256
`c66502bb237fc8fd749e11d3ea14f684dd8c0e2c0449555641ccdc87ec05621a` and embedded payload SHA-256
`654ce6b04bb4f6e8377fcf602af64da81daf98e17f7655325146caa73e76c85c`. It reproduced across two
clean builds with 362 passing tests and Java 8 class-file major version 52, then failed closed
during the full export when the real Gadomancy Runed Stone output
(`Thaumcraft:blockEldritch`, metadata 10) reached Thaumcraft's inventory renderer, which emits no
geometry for that metadata. No final dataset was published. Its evidence is preserved under
`../gtnh-instance/diagnostics/failed-production-1.0.74-thaumcraft-blockeldritch-meta10-transparent-2026-07-21T152510Z/`.

The immutable failed `1.0.73` artifact is 758,353 bytes with outer SHA-256
`0bb0e42c89192a0aad1e10410ba5cfdf4106910d7249e80281265589017a4d08` and embedded payload SHA-256
`18b6f2fb856385b595f5025890a5ef2c464eba471a0a736cbf3c92f85546f911`. It reproduced across two
clean builds with 362 passing tests and Java 8 class-file major version 52, then failed closed
before category export because its XU v2 projection conflated the distinct
`ExtraUtilities:microblocks` recipe-output and `ForgeMicroblock:microblock` placeholder items.
Its evidence is preserved under
`../gtnh-instance/diagnostics/failed-production-1.0.73-xu-material-tagged-item-mismatch-2026-07-21T150400Z/`.

`releaseBuild` runs the focused unit tests, a static guard that rejects calls to known Thaumcraft
player-research mutation APIs, and the normal RFG build/reobfuscation graph. The build fails during
configuration if any explicit API JAR is absent, unreadable, or has the wrong digest. DreamCore
is a compile-only input for the public `showConfirmExitWindow` shutdown-interception audit;
GregTech supplies the typed outputless-recipe semantic preflight. The pinned GTNH runtime supplies
the same digest-verified JARs.

### Exporter build provenance

`reobfJar` seals the final distributable with `META-INF/mrt-exporter-build.json`. Its
`payloadSha256` covers a canonical stream of every non-directory JAR entry except that identity
resource itself: unsigned UTF-8 path-byte order, exact path length and bytes, exact uncompressed
content length, and exact uncompressed content bytes. ZIP order, timestamps, and compression do
not perturb the identity, while every reobfuscated class, resource, and manifest byte remains
covered. Unsafe or duplicate ZIP paths fail the build before sealing.

At runtime the exporter resolves its own Forge `ModContainer` source JAR, recomputes the payload
digest, and rejects a missing, noncanonical, mismatched, symlinked, or directory-backed artifact.
Before transactional publication it writes the embedded canonical bytes atomically into the
staging root as `exporter-build.json`:

```json
{"format":"mrt-exporter-build-v1","exporterId":"forge-nei-gtnh-1.7.10","minecraftVersion":"1.7.10","algorithm":"sha256","payloadSha256":"<64 lowercase hex>"}
```

This payload identity avoids the circularity of embedding the complete JAR's hash inside that
same JAR. Release acceptance separately binds the complete distributable SHA-256 to these exact
exported bytes. An exploded development classpath cannot produce a publishable export; no
unverified provenance fallback exists.

CodeChickenCore 1.4.10 is a legacy coremod whose FML `InjectedModContainer` deliberately reports
the synthetic `minecraft.jar` sentinel instead of its installing JAR. Its adapter pin therefore
uses an explicit, logged source contract: the wrapper must contain the exact
`codechicken.core.asm.CodeChickenCoreModContainer`, must report exactly `minecraft.jar`, and the
wrapped class's own `file:` code source must resolve to the exact regular, non-symlink
`mods/CodeChickenCore-1.4.10.jar` and match the whole-JAR SHA-256 pin. The exporter does not scan
the mods directory or silently fall back from an invalid container source.

## Install and request automation

Place the release JAR in the pinned GTNH client's `.minecraft/mods/` directory. The mod is
client-only and polls the game directory once per second unless
`-Dgtnh.neiexport.auto=false` is set.

Create `<gameDir>/neiexport-request.json` from [example-request.json](example-request.json). The
`pack` object is mandatory: it is why the published manifest can truthfully identify its
`identitySource` as `explicit-request`. `iconScale`, `recipeScale`, and the compatibility-only
`mobCanvas` are fixed contract values; requests that change them fail instead of degrading output.
`bootstrapIntegratedWorld` must also be explicitly `true`. This is the authorization boundary for
creating or reusing the version-qualified automation save required by NEI's normal world lifecycle.

The request transaction is:

1. atomically claim `neiexport-request.json` as
   `neiexport-request.running-<uuid>.json`;
2. create or validate the owned `RecipeTree-GTNH-2.8.4-Exporter` creative-superflat save with the
   fixed nonzero seed required by GT5U's xorshift-based space-dimension initialization, then
   launch it through Minecraft's supported integrated-server API;
3. observe `NEIConfigsLoadedEvent`, then require the active world/player, enabled and loaded NEI,
   `ItemList.loadFinished`, a nonempty item list, and the union of NEI's concurrent and serial
   crafting-handler registries to remain stable for `handlerStableTicks`; the active session must
   remain the exact owned local save in dimension 0, the player controller must remain creative,
   and NEI's world path must remain the exact version-qualified local path; after that handshake,
   directly audit the authoritative `IntegratedServer.worldServers[0]` seed and `WorldInfo` without
   invoking a dimension-loading accessor;
4. export into a sibling `.<output>.staging-<uuid>` directory while continuously auditing that the
   world/player and ItemList remain identical and periodically auditing the handler fingerprint;
5. finish every queued PNG, write metadata, validate the zero-omission manifest, and atomically
   publish the staging directory;
6. rename the exact claimed request bytes to `neiexport-request.complete.json`.

Any failure leaves the previous final output untouched, preserves diagnostic staging, and renames
the request to `neiexport-request.failed.json`. With `exitOnComplete=true`, success and failure both
request the normal outer-loop shutdown after the durable terminal marker. The END-phase subscriber
does not unload the world: the enclosing 1.7.10 frame still performs sound and world rendering.
Instead it verifies that world/player/server identities remain unchanged and that DreamCore's
confirmation interceptor is disabled, calls `Minecraft.shutdown()` only to clear the loop flag,
and leaves integrated-server save/unload, renderer/display cleanup, and final JVM exit to
Minecraft's outer run path. The supervising launcher converts a failure marker into its own
nonzero result after that cleanup.

The repository's supervised launcher performs the whole install/launch/reconciliation sequence:

```bash
node gtnh-instance/launch-export-1.7.10.mjs
```

The save contains an exact exporter-ownership marker. A pre-existing directory without that marker,
a modified marker, or a symlink fails closed instead of being reused. The save is intentionally
retained for reproducible reruns. Its persisted seed, game type, feature/hardcore flags, world type,
generator options, and command permission are revalidated before reuse; no user save or remote
server is selected, mutated, or deleted.

The launcher also sets BetterLoadingScreen's `B:useMinecraft=false` through a logged, verified,
atomic configuration edit and preserves the original file alongside it. This isolates the splash
renderer from Minecraft's shared static texture buffer, avoiding a demonstrated startup race. It
changes only the loading presentation, not mod initialization, registries, recipes, or export data.

## Completeness model

NEI divides handler lookup into a concurrently queried registry and a serial registry. The
exporter snapshots both. For every registered `ICraftingHandler` prototype it:

1. requires a nonblank `getHandlerId()`, treating it as a non-unique implementation-lineage ID;
2. reflects every SHA-pinned NEI `RecipeTransferRect` as an exact `(outputId, result arity)`
   operation, separates those operations from `getOverlayIdentifier()` and
   `specifyTransferRect()`, and selects one unambiguous zero-argument complete-category call;
3. runs an aggregate structural preflight before expensive adapter discovery, rejecting missing,
   conflicting, multi-ID, argument-dependent fallback, or duplicate contracts in one diagnostic;
4. calls the public `getRecipeHandler(identifier)` contract;
5. requires a nonnull loaded handler with the same exact runtime class, handler ID, overlay
   discriminator, and at least one recipe;
6. exports the category completely before releasing it and loading the next category.

Different handlers may legitimately share an overlay identifier or raw handler ID. Category
uniqueness is based on a length-framed semantic tuple containing runtime class, raw handler ID,
overlay discriminator, selected operation, and adapter contract. Public category IDs are the
`gtnh:` prefix plus 128 bits of SHA-256; duplicate full keys and truncated-hash collisions both
fail closed. This bounds retained recipe-handler state while still producing deterministic order.

There is no catch-and-continue path. A failed category, recipe, identity, quantity, icon, widget,
or PNG aborts publication.

Forty-six pinned GTNH handler registrations require explicit semantic policy rather than the
generic category call. Twenty-five become exported categories, 20 are explicit non-recipe
exclusions, and one is an exact unbound material-template exclusion; a separate empty-category
ledger excludes 20 source-backed zero-row handlers and two empty GregTech recipe maps:

- Extra Utilities' `MicroBlocksHandler` owns four real 3x3 crafting templates, but its global NEI
  operation emits materialless carrier stacks and injects the selected ForgeMultipart `mat` NBT
  only during query/render permutation. Exporting those four carriers would fabricate invalid
  graph identities. The exporter therefore pins the exact four-row source and loaded-cache
  fingerprints, verifies the missing material binding without mutating the source registry or
  registered prototype, logs the owner's deliberate lazy-cache population, and
  records `excludedUnboundTemplateRecipeHandlers: 1`. A future complete adapter can deliberately
  expand the four templates across the pinned micro-material registry.

- AE2's eight enabled in-world acquisition pages are recovered through an exact wildcard item-query
  closure and exported as informational metadata under
  `adapter:ae2-in-world-crafting-wildcard-query-closure-v1`. The charged-Certus page is
  config-disabled and is independently required to return zero pages. These pages remain
  browseable but are excluded from executable graphs and material totals.

- BetterQuesting's handler is rebuilt from all 3,739 sorted `QuestDatabase` UUID entries. The
  pinned NEI `CachedQuestRecipe` deliberately renders at most 16 task stacks and 16 reward stacks,
  so it remains the preview source but is not used as the item-reference source. A separate exact
  adapter walks every ordered task/reward database entry implementing `ITaskItemInput` or
  `IRewardItemOutput`, expands every copied `BigItemStack` ore/wildcard alternative through the
  pinned NEI permutation contract, and emits all 8,987 task-reference slots. Regular item rewards
  remain 6,256 individual output slots; each of the 957 `RewardChoice` providers becomes one output
  slot whose ordered alternatives preserve all 3,062 choices and their heterogeneous quantities,
  producing 7,213 informational output slots from the same 9,318 flat NEI reward entries. Eighteen
  quests exceed 16 task references (maximum 55); grouped output slots have maximum 11, while the
  flat preview source still has four pages over 16 and a maximum of 19. Nothing is truncated in the
  item-reference JSON. The adapter requires the audited split of 3,632 item-bearing pages: 2,984
  with both sides, 488 informational input-only pages, and 160 output-only pages. The 107 quests
  without item references are explicitly excluded. It also verifies 136 OR-logic quests and 1,267
  optional-retrieval task references. These pages are explicitly informational under contract
  `adapter:betterquesting-complete-item-reference-pages-v1`: task AND/OR/optional/consume behavior
  and reward selection are not representable as executable v1 material semantics. Because the
  pinned preview constructor writes
  quantities into its backing stacks, the adapter snapshots, restores, and verifies those exact
  stack sizes around preview construction; quest and player state remain unchanged.
- IC2 Crop Plugin publishes breeding caches from a worker whose fields are not volatile. The
  exporter waits for that worker, performs one exporter-owned recomputation, and observes thread
  termination to establish a Java Memory Model happens-before edge. The resulting raw craft/usage
  maps are validated and retained as diagnostic telemetry only: their identity-hash/tree-bin
  membership is observably nondeterministic across JVM boots. Authority instead starts from the
  exact 159-entry `IC2NeiPlugin.ALL_CROPS` universe, evaluates the complete public 159x159 ratio
  matrix, and visits all 12,561 canonical unordered parent pairs. First-winner de-duplication is
  applied independently to each output craft bucket and each parent usage bucket, producing a
  reproducible 290,789-page graph closure: 4,595 craft winners plus 286,194 usage-only winners.
  This is a repaired graph closure, not byte-for-byte parity with the broken raw NEI cache.

  Every retained graph uses clean `BreedResult.getItemInputs()`/`getItemResult()` stacks: exactly
  two nonnull inputs and one nonnull result, positive amounts, full NBT, defensive deep copies, and
  exact identity round trips through static `CropPluginAPI.getCrop(ItemStack)`. The pinned
  `BreedResult` constructor sorts its retained input array by runtime object hash, so the adapter
  immediately restores canonical `CropCard` owner/name order before graph capture or preview
  construction. A newly constructed pinned `BreedRecipe` supplies the PNG; its `addPoints()` Lore
  suffix never enters compact graph JSON or the reverse index. Reverse-index entries intentionally
  expose every retained graph under both true parents and its output, regardless of which modeled
  query bucket selected that graph. This is recorded by handler contract
  `adapter:ic2-crop-deterministic-query-bucket-closure-v1`; no recipe count is guessed.
- Galacticraft's electric compressor, compressor, gas liquefier, and methane synthesizer classes
  are dormant in GTNH 2.8.4; stale HandlerInfo CSV rows are not live NEI registrations and no
  synthetic categories are created. GalaxySpace's eight rocket tiers and AmunRa's shuttle are live
  NASA-workbench registrations. Exact query-ID adapters export their 4-per-tier and 27-page corpora,
  canonical-sort identity-ordered Set/Map sources by full stack alternatives and coordinates, and
  pin both backing-collection identity and deterministic layout fingerprints.
- Extra Utilities' item-query-only Soul handler is recovered as one exact Etheric Sword to canonical
  NEI Soul display recipe. The adapter validates every serialized `PositionedStack.items`
  alternative, metadata/NBT-insensitive craft and usage queries, and exactly one live
  `RecipeSoul` registration. The actual crafted Soul later receives player-dependent metadata/NBT;
  the exported metadata-0 output is explicitly NEI's canonical display identity.
- TCNEIAdditions' two player-query aspect handlers, IC2's interactive lathe-state view,
  BeeBetterAtBees' recursive lineage tree, and Extra Utilities' textual item documentation have no
  executable category-wide recipe corpus and are recorded as query-only exclusions.
- Forge's fluid registry and ore-dictionary browsers, Botania's Lexica cross-reference, and
  TConstruct's tool-material statistics are category-wide presentation pages, but not executable
  recipes. Their four exact zero-argument transfer IDs are pinned and they use a distinct
  `excluded-non-recipe-presentation` action instead of being conflated with query-only handlers.
- NEI Custom Diagram's two live EnderStorage browsers are excluded as query-only state views; its
  five GregTech composite diagrams/catalogs are excluded as presentation-only pages. The NEI
  handler profiler is excluded as a debug timing view. Their exact classes, handler IDs, empty/null
  standard stack semantics, and immutable prototype structure remain pinned without invoking live
  content suppliers.

The exact external JARs and `tcneiadditions.cfg` used by these policies are version- and SHA-256-
pinned at runtime. BetterQuesting also has a canonical item-reference fingerprint over every quest
UUID, reward-provider boundary, ordered choice boundary, exact full stack identity, quantity, and
alternative. The authoritative IC2 crop fingerprint covers the full canonical ratio matrix, every
repaired bucket winner and membership bit, every canonical CropCard ID, every clean per-crop stack
identity/amount/full NBT, and every retained graph. The first immutable post-worker crop snapshot is
reused across readiness, category construction, and periodic shallow integrity checks. Explicit
pre-publication gates compactly revalidate the exact `ALL_CROPS` object/ID vector, all 25,281 ratio
cells, diagnostic raw-cache membership, and a bounded representative cover of all 159 clean crop
stacks. Because the exporter and dependency bytecode are pinned for the supervised JVM, those
derivation inputs prove the same deterministic closure without allocating another 290,789
`BreedResult` corpus. Any basis, policy, or retained-stack drift aborts rather than falling back.

NEI itself contains several upstream catch-log-and-continue paths. The exporter attaches a bounded,
thread-safe ERROR monitor before NEI plugin discovery and converts the exact plugin-class, plugin
configuration, item/metadata omission, shaped-recipe, and `getOtherStacks` error messages into
`HANDLER_UNLOADED` rather than accepting `NEIConfigsLoadedEvent` or `ItemList.loadFinished` at face
value. The monitor is an audit observer; it does not patch NEI or suppress the original diagnostics.

## Recipe semantics

For ordinary recipe index `i`, the v1 record follows the pinned NEI contract exactly:

- inputs are every `PositionedStack` returned by `getIngredientStacks(i)`;
- if `getResultStack(i)` is nonnull, it is the output slot and every `getOtherStacks(i)` entry is a
  catalyst slot;
- if `getResultStack(i)` is null, every `getOtherStacks(i)` entry is an output slot.

Every `PositionedStack.items` alternative is retained in its original order. If NEI has not yet
materialized the array, the exporter calls the public `generatePermutations()` method and then
requires a nonempty result. Every alternative's exact `ItemStack.stackSize`, including zero, is
written as the v1 quantity. Negative quantities fail with `QUANTITY_INVALID`; no guessed quantity
is substituted.

The resulting compact fields match the existing viewer:

```json
{
  "id": "handler.id#sourceIndex",
  "img": "r0.png",
  "w": 166,
  "h": 65,
  "in": [[ ["item|mod:item|meta=0|nbt=-", 1] ]],
  "out": [[ ["item|mod:result|meta=0|nbt=-", 1] ]],
  "cat": []
}
```

`cat` is omitted when empty. The reverse index records inputs and catalysts as uses and outputs as
productions.

BetterQuesting is the explicit informational exception: its PNG still renders the pinned NEI
presentation (at most 16 flat entries per side), while the compact `in`/`out` arrays retain the
complete item-reference corpus described above. `RewardChoice` alternatives remain grouped as one
output slot even though the preview displays them as consecutive flat entries. This category must
be excluded from material graph/tree totals: the association pages do not encode task AND/OR logic,
optional or consume behavior, or which reward choice is selected. The flat preview/reference
prefix is identity-checked at load time, and each referenced stack is identity-checked again when
exported; mismatch aborts instead of falling back to the truncated preview.

IC2 crop breeding uses the same presentation/graph separation for a different reason: its PNG is
a newly constructed exact `BreedRecipe` with the plugin's breeding-points and breeding-chance Lore,
while its `in`/`out` arrays and reverse index use untouched defensive copies returned by
`BreedResult`. The adapter requires canonical positional parent order, exact preview-to-clean-stack
linkage, and the exact two-line Lore-only output delta; any item, amount, NBT, CropCard, aliasing,
position, or Lore drift aborts rather than contaminating the recipe graph with presentation
metadata.

The 488 audited BetterQuesting input-only pages are the sole exception to the normal nonempty-output
invariant. They are informational objective pages, are counted separately in diagnostics, and any
other empty-output recipe aborts publication.

## Deterministic item identity

An item key includes all identity-bearing data:

```text
item|<forge-registry-name>|meta=<signed-metadata>|nbt=<sha256-or-dash>
```

Compound NBT keys are recursively sorted. List order remains significant, and the 1.7.10 public
copy/remove API is used to traverse arbitrary list element types without reflection. The full
canonical NBT payload is compared whenever an in-process key is reused, turning a hypothetical
digest collision into `ITEM_IDENTITY` rather than merging records.

This also distinguishes NBT-backed fluid-display stacks: different fluid IDs or nested identity
tags produce different keys even when the display item registry name and metadata are identical.
Fluid amount and `stackSize` are recipe-slot quantities and therefore are not part of the catalog
key.

AE2FC `1.4.120-gtnh` is version- and whole-JAR-SHA-pinned because its proxy representation needs an
exact adapter. A valid `ae2fc:fluid_drop` stores its fluid name in root string `Fluid`, optional
identity data in compound `FluidTag`, presentation-only state in `DisplayOnly=1b`, and its total
millibucket quantity in `stackSize`. AE2FC's NEI stringify handler normalizes that decoded amount to
one millibucket per drop, so the exporter validates the handler's fluid/tag identity against a
direct schema decoder but retains the original positive `stackSize` as the slot amount. Unknown
root fields, wrong NBT types, unknown fluids, nonzero metadata, or nonpositive quantities fail.

Pinned NEI constructs one bare `ae2fc:fluid_drop` and one bare `ae2fc:fluid_packet` solely while
materializing its global item browser: the former comes from NEI's metadata damage search and the
latter from vanilla `getSubItems`. Neither has a logical fluid payload. Only the completed global
`ItemList` snapshot may exclude those exact registry/class/size-one/metadata-zero/no-NBT shapes.
Each exclusion is logged, each named policy must occur exactly once, and both counts are serialized
in `diagnostics.nei`. The same undecodable proxy in a recipe, catalyst, or adapter remains an
`ITEM_IDENTITY` failure.

Blood Magic `1.7.52` is also version- and whole-JAR-SHA-pinned for two distinct internal world
helpers that vanilla registry enumeration exposes as default `ItemBlock` stacks. Exact bare
`AWWayofTime:bloodLight` and `AWWayofTime:spectralContainer` entries use the intentionally
all-alpha `BlockBloodLight` texture, have no creative tab, and drop nothing. The former is placed by
the Blood Light sigil/projectile; the latter is a replaceable, zero-volume tile-entity container
used to preserve and restore world blocks. The pinned corpus contains no recipe reference to either
identity. Only their exact size-one/metadata-zero/no-NBT entries in the completed global `ItemList`
snapshot are excluded. Each exclusion is logged and must occur exactly once under its own manifest
counter. A recipe, catalyst, category adapter, or any differently shaped stack still reaches the
normal icon/identity path and fails closed; the exporter does not accept transparent assets or
synthesize replacement icons.

ArchitectureCraft `1.11.6` is version- and whole-JAR-SHA-pinned for one material-parametric browser
artifact. Its inherited vanilla subitem enumeration contributes exactly one bare
`ArchitectureCraft:cladding` stack, but the item renderer requires root string NBT key `block` to
select the material block and texture. With no NBT it intentionally emits no geometry, and the bare
item cannot resolve a material or be applied to a shape. Only that exact size-one/metadata-zero/
no-NBT entry in the completed global `ItemList` snapshot is excluded, logged, and required exactly
once. Material-bearing cladding retains normal NBT-sensitive catalog identity, rendering, and
recipe/category validation. `ArchitectureCraft:shape` and `ArchitectureCraft:shapeSE` are not
excluded: their renderer deliberately supplies a visible oak roof-tile default when NBT is absent.

Avaritia `1.77` is version- and whole-JAR-SHA-pinned for one content-dependent browser artifact.
`ItemMatterCluster` has no creative tab and does not override vanilla subitem enumeration, so NEI's
null-tab scan synthesizes exactly one bare `Avaritia:Matter_Cluster` stack. Functional clusters are
created through `makeClusters`/`makeCluster` and always carry root `clusteritems` NBT with an item
list and positive `total`; the bare stack has no contents, right-click consumes it, and its cosmic
shader opacity is exactly `0 / 16384`. Only the exact size-one/metadata-zero/no-NBT entry in the
completed global `ItemList` snapshot is excluded, logged, and required exactly once. NBT-backed
clusters retain normal canonical identity and rendering. A bare cluster encountered later in a
recipe, catalyst, or adapted category is not covered by this catalog-only policy and fails closed;
the exporter does not substitute an icon or add a generic transparent-render fallback.

GTNewHorizonsCoreMod `2.7.268` is whole-JAR-SHA-pinned as
`da36a9e1e6675d709969fc57aa0d443a183aa496f787713acc9e2582399235a1`, and YAMCore `0.7.1` is
whole-JAR-SHA-pinned as `eaa6349c892798eb1d79a0df2078630e553b12a6f93a4ae64c3e34373a30c80d`
for the exact `dreamcraft:item.Nothing` legacy LootBag empty-reward presentation sentinel. The
owner `NHItemList.Nothing` entry and `ModSimpleBaseItem` wrapper construct the exact YAMCore
`eu.usrv.yamcore.items.ItemBase` registry object. Preflight pins that ownership chain, its `Consume`
recipe behavior, unlocalized name and generic creative tab, amount-one/metadata-zero/no-NBT stack
traits, absent block alias, and one bare creative subitem. It also pins the
`dreamcraft:itemNothing` icon binding: the owner's exact 16x16 fully transparent PNG has SHA-256
`2fef62c8e32c1e7f7dd19ecf1fd11d8a596780ac513c1054ad8764a284240eb5`, and the item has no Forge
inventory renderer.

The semantic audit found no recipe, drop, quest, loot, or survival-acquisition path. Contract
`dreamcraft-nothing-orphaned-legacy-lootbag-empty-reward-sentinel-v1` therefore excludes only the
one exact global `ItemList` entry under a strict-identity, catalog-only policy, with required count
one and metadata mask `0x1`. Any same-item amount, metadata, wildcard, or NBT drift fails closed, as
does any occurrence in a recipe, catalyst, or adapted category; no visible substitute or generic
transparent-render fallback is synthesized.

LittleTiles `1.5.14-GTNH` is version-, byte-length-, and whole-JAR-SHA-pinned as an exact dynamic
microtile carrier owner. Both its item and block intentionally enumerate no creative subitems, so
NEI's damage search probes metadata `0..15` and canonical item-list deduplication leaves one bare
metadata-zero `littletiles:BlockLittleTiles` stack. That unparameterized stack has no tile payload;
the owner item renderer declines it and the owner block's inventory renderer intentionally emits no
geometry. Legitimate LittleTiles placement and drop paths use the same registry item with NBT that
encodes the carried block, metadata, size, and tile state.

Contract `littletiles-unparameterized-microtile-carrier-nei-damage-search-v1` therefore excludes
only the exact amount-one, metadata-zero, NBT-absent global `ItemList` entry under a non-strict,
catalog-only `owner-internal-world-state` policy. NBT-bearing same-registry microtiles remain
eligible for the catalog and recipe graph. Diagnostic
`excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries` is required to equal one with
metadata mask `0x1`; any bare occurrence in a
recipe, catalyst, or adapted category still fails closed, and no replacement icon is synthesized.
The owner topology is also pinned exactly: the raw block handler and Forge item renderer share
`LittleTilesClient.renderer`, while the tile-entity special renderer is a separately constructed
instance of the same exact class. Angelica's per-thread ISBRH instances do not replace the raw
registration-map owner inspected by preflight.

MalisisDoors `1.18.2-GTNH` is version-, byte-length-, and whole-JAR-SHA-pinned for its dynamic
custom-door carrier. The unusual registry identity is exactly
`malisisdoors:item.custom_door`: registration uses the item unlocalized name as the registry name.
The exact `CustomDoorItem -> DoorItem -> ItemDoor` owner has no creative tab, a maximum stack size
of 16, and inherits vanilla `Item.getSubItems`; NEI's null-tab enumeration consequently synthesizes
exactly one amount-one, metadata-zero, NBT-absent permutation. The item deliberately registers no
sprite. Its exact `CustomDoorRenderer` instance owns both the Forge item-renderer registration and
the `CustomDoorTileEntity` TESR registration, and its pinned inventory render method intentionally
returns without geometry when the stack has no NBT.

Contract `malisisdoors-unconfigured-custom-door-carrier-nei-getsubitems-v1` excludes only that
exact bare identity under the non-strict catalog-only semantic bucket
`owner-internal-unconfigured-dynamic-item`. Door Factory and placed-tile reconstruction paths
produce same-registry stacks with descriptor and frame/material NBT; every NBT-bearing variant is
retained. Diagnostic `excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders` must equal
one, while `malisisDoorsUnconfiguredCustomDoorRecipeReferences` and
`malisisDoorsUnconfiguredCustomDoorQuestReferences` must both remain zero after their complete
post-discovery traversals. Any exact bare graph reference is logged and aborts publication.

The same pinned MalisisDoors artifact registers `malisisdoors:mixed_block` as an exact
`MixedBlockBlockItem -> ItemBlock` paired with `MixedBlock`. Neither owner overrides the vanilla
`ItemBlock.getSubItems -> Block.getSubBlocks` chain, so NEI's null-tab global enumeration creates
one amount-one, metadata-zero, NBT-absent carrier. The shared exact `MixedBlockRenderer` owns both
the Forge item renderer and the block render ID; its inventory setup rejects that bare carrier.
The Block Mixer and placed-tile reconstruction paths instead call the exact `fromItemStacks` and
`fromTileEntity` producers, which emit the same registry item with exactly four integer NBT keys:
`block1`, `block2`, `metadata1`, and `metadata2`.

Contract `malisisdoors-unconfigured-mixed-block-carrier-nei-getsubitems-v1` therefore excludes
only that exact bare identity under `owner-internal-unconfigured-dynamic-item`. Every NBT-bearing
stack—including malformed or partial state—is retained so later identity/render validation can
fail closed rather than silently treating it as the browser placeholder. Diagnostic
`excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders` must equal one;
`malisisDoorsUnconfiguredMixedBlockRecipeReferences` and
`malisisDoorsUnconfiguredMixedBlockQuestReferences` must both remain zero across category icons,
catalysts, ordinary recipe slots, semantic adapters, and BetterQuesting traversal. Any exact bare
graph reference is logged and aborts publication.

ModernMarkings `0.3.12-1.7.10` is version-, byte-length-, and whole-JAR-SHA-pinned for one exact
catalog-rendering compatibility policy. Its six public floor-crossing blocks—blue, green, orange,
red, white, and yellow—are ordinary metadata-zero vanilla `ItemBlock` registrations backed by
`modernmarkings.blocks.MarkingFloor` and are reachable through the owner Chisel variation group.
Each owner PNG is 16x16 with four opaque corner texels, while the registered
`modernmarkings.renderer.MarkingFloorRenderer` projects the texture on a horizontal 3-D inventory
quad; legacy 16x16 projection therefore produces a fully transparent capture. Contract
`modernmarkings-four-corner-crossing-owner-atlas-face-on-catalog-v1` verifies each exact registry
identity, block/renderer topology, resolved owner-resource byte length, SHA-256, dimensions, and
four-corner pixel topology before drawing the stitched owner atlas sprite face-on. It synthesizes
no artwork, changes no catalog membership, logs every application, and requires diagnostic
`adaptedModernMarkingsCrossingItemIcons` to equal six. Registry, metadata, NBT, resource, renderer,
or cardinality drift aborts publication; no generic transparent-image fallback exists.

Thaumcraft `4.2.3.5a` is whole-JAR-pinned for one exact recipe-bearing Runed Stone identity:
`Thaumcraft:blockEldritch`, metadata 10, amount one, and absent NBT. The block's registered
`BlockEldritchRenderer` deliberately emits inventory geometry only for metadata 4 through 6, even
though Gadomancy legitimately outputs metadata 10 and `BlockEldritch.getIcon` resolves all six
sides to the opaque owner sprite `thaumcraft:es_5`. Contract
`thaumcraft-runed-stone-meta10-owner-atlas-face-on-catalog-v1` therefore verifies the exact item,
block, renderer, tile-entity, stitched-atlas, resource-byte, PNG, and ARGB identities before drawing
that owner sprite face-on. It separately verifies the inherited `ItemBlock` metadata-zero stack
icon remains the distinct stitched `thaumcraft:obsidiantile` sprite and that the item remains on
the block atlas; the legacy stack icon is an owner invariant, not the metadata-10 recipe image.
The adapter changes neither recipe semantics nor catalog membership, logs its
single application, and requires `adaptedThaumcraftRunedStoneItemIcons` to equal one. There is no
transparent-render fallback or policy broadening to another metadata value.

ProjectBlue `1.2.1-GTNH` and ForgeMultipart `1.7.2` are version- and whole-JAR-SHA-pinned for three
exact control-panel material stacks. ProjectBlue's owner renderer directly calls each source
block's `getIcon`: Automagy's Nether Rune returns null for all six faces, GregTech Casings6 metadata
15 throws on faces 0 and 1, and CasingsNH metadata 15 throws on all faces. NEI catches those owner
errors and substitutes its fire texture, which is not the control panel's material. ForgeMultipart
has already resolved the same materials through its safe six-face `BlockMicroMaterial.icont`
cache. Contract `projectblue-control-panel-fmp-cached-face-icons-owner-renderer-v1` therefore leases
only the exact three canonical registry/metadata/NBT identities, temporarily supplies an
unregistered block proxy backed by those exact cached faces, and invokes ProjectBlue's unchanged
owner geometry. It verifies the per-face missing-sprite/GregTech casing topology, detects failures
outside NEI's exception-swallowing path, and restores the exact Forge renderer binding and shared
material block/name/metadata after every draw. Diagnostics
`adaptedProjectBlueControlPanelItemIcons` and
`adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations` must both equal three. No broad
ProjectBlue policy, replacement texture, or fire-texture acceptance exists.

With these policies, all 31 independent catalog policies must reconcile 56,038 raw entries to
exactly 46 exclusions, 55,992 retained entries, and 55,991 unique retained canonical identities.
The final catalog additionally includes recipe-only identities, so it must be at least 55,991
entries but must not be equated with the global ItemList entry count.

Carpenter's Blocks `3.7.0-GTNH` is version- and whole-JAR-SHA-pinned for two owner-internal
multipart block aliases. Default Forge registration exposes `CarpentersBlocks:blockCarpentersBed`
and `CarpentersBlocks:blockCarpentersDoor` as vanilla `ItemBlock` identities even though both
blocks deliberately omit the creative tab and have no supported inventory renderer. The public,
craftable placement products are the distinct `itemCarpentersBed` and `itemCarpentersDoor` items;
they own the item textures and two-block placement transactions, and both internal blocks drop and
pick those public items. The exporter preflights the exact registry objects and classes, internal
item/block bijections, internal/public non-aliasing, creative-tab and icon distinction, public
drop/pick targets, and the lack of a 3D inventory renderer. It then excludes only one exact bare
size-one/metadata-zero/no-NBT global `ItemList` entry for each internal alias. Both exclusions are
logged and independently counted. The public items remain in the catalog and recipe graph, while
either internal alias appearing later in a recipe, catalyst, or category adapter fails closed. No
public-icon substitution or generic transparent-render fallback exists.

Steve's Carts `2.3.12` is version- and whole-JAR-SHA-pinned for one unconfigured global-browser
placeholder. Vanilla subitem enumeration contributes exactly one bare metadata-zero/no-NBT
`StevesCarts:ModularCart` even though the item has no creative tab. The owner inventory renderer
needs the cart's NBT-backed module configuration to construct visible geometry, so that bare stack
does not represent an operational cart. The exporter preflights the exact registry item and runtime
class, its `ItemMinecart` inheritance, subtype/damage/stack-limit/creative-tab traits, and the exact
Forge inventory-renderer class. It also proves that an NBT-bearing cart is not captured by the
policy. Only the exact size-one/metadata-zero/no-NBT entry in the completed global `ItemList`
snapshot is excluded, logged, and required exactly once with metadata mask `0x1`. Configured NBT
carts remain cataloged with their full canonical identity. A matching bare identity found later in
a recipe, catalyst, or category adapter fails closed; no cart icon is synthesized.

TConstruct `1.13.57-GTNH` is version- and whole-JAR-SHA-pinned for two owner-internal equipment
block identities. Default Forge block registration exposes vanilla `ItemBlock`s for
`TConstruct:BattleSignBlock` and `TConstruct:HeldItemBlock`, but those blocks are only placed-world
containers whose tile entities store the actual Battle Sign or Frying Pan tool. Their owner
`BattlesignRender` and `FrypanRender` implementations deliberately have empty inventory render paths
and render only world representations; the Frying Pan world renderer additionally requires block
orientation and the stored tool's material NBT. A fully transparent bare inventory render is
therefore the expected signal for these internal identities rather than a missing public asset. The
real, craftable NBT-backed tools are the distinct public `TConstruct:battlesign` and
`TConstruct:frypan` items and remain in the catalog and recipe graph.

Before enumeration the exporter preflights each exact internal item/block registry bijection and
runtime classes, its non-aliasing with the corresponding public tool, the internal creative-tab/drop/tile-entity
traits, and the exact custom block-render ID/class with no separate Forge item renderer. It also
requires each public tool's exact registration/class, creative/damage/stack-limit traits, a nonempty
creative variant set whose every stack carries compound `InfiTool` NBT, and the exact
`FlexibleToolRenderer` inventory renderer.
Only one exact size-one/metadata-zero/no-NBT entry for each internal TConstruct block in the
completed global `ItemList` snapshot may be excluded. Each policy requires cardinality one and
metadata mask `0x1`, logs and serializes its independent count, and aborts on any missing,
duplicate, NBT-bearing, wrongly shaped, or runtime-drifted entry. The same internal identity in a
recipe, catalyst, or category adapter remains an `ITEM_IDENTITY` failure; the exporter does not
substitute a public tool icon, fabricate dummy-world state, or add a generic transparent-render
exception.

Thaumcraft `4.2.3.5` is version- and whole-JAR-SHA-pinned for the owner-internal
`Thaumcraft:blockHole` world-state `ItemBlock`. Its metadata-zero form is an unobtainable
portable-hole implementation detail: it has no creative tab, drops and picks no item, exposes no creative
sub-blocks, uses the blank block icon, and represents world state whose `TileHole` instances are
installed manually by the distinct public `Thaumcraft:FocusPortableHole` focus. The exporter
preflights the exact item/block registry bijection and runtime classes, the separate public focus
registration/class and creative presentation, tile inheritance and ordinary null tile-construction
semantics, nonopaque rendering, exact metadata-zero/metadata-15 icon identities, and absence of a
Forge inventory item renderer.

Only the exact size-one/metadata-zero/no-NBT global `ItemList` entry is catalog-excluded under
contract `thaumcraft-blockhole-internal-portable-hole-world-itemblock-v1`, with independent
`excludedThaumcraftBlockHoleInternalBlockItemListEntries` telemetry required to equal one.
Metadata 15 is an explicit pass-through sentinel: Thaumcraft deliberately uses that same registered
item with the `thaumcraft:empty` icon in compound and multiblock recipe diagrams, including the
Infernal Furnace, so it remains serializable in recipe and diagram references. The public portable-
hole focus likewise remains cataloged. Any other metadata, amount, or NBT shape for the internal
item fails closed as unmodeled; there is no broad Thaumcraft or invisible-item exclusion.

The same pinned Thaumcraft artifact registers `Thaumcraft:blockPortalEldritch` as a default vanilla
`ItemBlock` even though the block is owner-internal Eldritch Portal world state. It has no creative
tab or normal drop, is nonopaque and nonnormal with render type `-1`, exposes only the transparent
`thaumcraft:blank` block icon, and creates the live `TileEldritchPortal` used by the world TESR.
Portal construction creates only metadata zero, and there is no legitimate NBT-bearing or
nonzero-metadata item form. Thaumcraft's aspect registration for the block is world-scan metadata, not
evidence that its synthesized default `ItemBlock` is an obtainable catalog object. The preflight
therefore pins the exact registry item/block bijection, exact classes, tile class, render and drop
semantics, icon, and exact bare-stack matcher. It deliberately does not treat the inherited
pick-block result as evidence of public availability.

Only the exact size-one/metadata-zero/no-NBT global `ItemList` entry is excluded under contract
`thaumcraft-eldritch-portal-owner-internal-world-state-itemblock-v1`, with independent
`excludedThaumcraftEldritchPortalInternalBlockItemListEntries` telemetry required to equal one.
Any other shape for that same item fails closed, and the identity remains forbidden in recipes,
catalysts, and adapters: this is a global-browser cleanup, not a graph-wide omission. The separate
public `Thaumcraft:ItemEldritchObject` metadata-zero Eldritch Eye retains its exact class, creative
variant, icon, recipes, and progression role; its modeled metadata 1 through 4 variants remain
retained as their own public items. Gadomancy `1.4.8` is independently version- and whole-JAR-
SHA-pinned as `90fb7e230a18a0eac65645aedc5c205acbc53844d95154850f90a6161beeaa26`
for its separately registered portal placer. Runtime preflight requires the exact
`gadomancy:BlockAdditionalEldritchPortal` item/block registration and bijection, exact
`ItemBlockAdditionalEldritchPortal` and `BlockAdditionalEldritchPortal` classes, nonnull creative
presentation, the `gadomancy:eldritch_portal` item icon, and its one exact bare metadata-zero/no-NBT
creative subitem. That distinct item must remain outside the core exclusion matcher.

The completed NEI ItemList snapshot must retain exactly one serialized bare metadata-zero Eldritch
Eye and exactly one serialized bare Gadomancy portal placer; both retained counts are checked after
identity serialization and logged explicitly. Missing or duplicate exact entries abort. A matching
item object with an unmodeled amount, NBT payload, or metadata also aborts instead of being ignored;
the four additional modeled `ItemEldritchObject` variants are explicitly recognized as separate
retained identities. No extra manifest field is needed because this is a local snapshot-source
cardinality contract, while the whole-JAR pins and exact registration preflight protect its inputs.

Thaumic Horizons `1.7.9` is version- and whole-JAR-SHA-pinned for the two owner-internal
`ThaumicHorizons:light` and `ThaumicHorizons:lightSolar` illumination world-state `ItemBlock`s.
The registered `BlockLightSolar` extends `BlockLight`: neither block has a creative tab, normal geometry,
collision, a normal drop, or an inventory renderer, and both expose only the deliberately
alpha-zero `thaumcraft:blank` icon. Their `TileLight` supplies client particle effects from world
metadata. The public `focusIllumination` item places the Solar block only when its Solar focus
upgrade is active; otherwise it places the distinct base `light` block. The Solar specialization
only ignites undead entities that cross its world position.

Only the exact amount-one, metadata-zero, no-NBT entry for each light in the completed global
`ItemList` is excluded under the independent base- and solar-light contracts. The corresponding
`excludedThaumicHorizonsBaseLightInternalBlockItemListEntries` and
`excludedThaumicHorizonsSolarLightInternalBlockItemListEntries` diagnostics must each equal one.
All 16 public `focusIllumination` color variants must remain retained with metadata mask `0xffff`.
Any other world-light amount, metadata, or NBT shape fails closed, and a world-light identity
encountered later in a recipe or category is not accepted as a player item. No icon is synthesized
and no general transparent-render fallback exists.

Twilight Forest `2.7.13` is version- and whole-JAR-SHA-pinned as
`3f5c14c79c74824e354cc9817d03d2c603dca2a993db2a612b12c16fc381582d` for the exact
`TwilightForest:tile.TFExperiment115` boundary. `TFBlocks.registerMyBlock` registers the concrete
`twilightforest.item.ItemBlockTFMeta` subclass for the owner-internal `BlockTFExperiment115` cake
world state: the block and item have no creative tab, the block has no normal drop, and its pick
identity is the distinct public
`TwilightForest:item.experiment115` food. The block is nonopaque and nonnormal, creates the exact
`TileEntityTFCake`, and uses `RenderBlockTFCake`. That renderer advertises three-dimensional
inventory rendering but deliberately performs no inventory draw, while the placed block remains
visible through its world block/TESR rendering path. The separately registered public `ItemTFFood`
has its normal creative presentation and item icon and can place or extend the cake only through
its explicit sneaking interaction.

Only the exact amount-one, metadata-zero, no-NBT internal `ItemBlockTFMeta` entry in the completed
global `ItemList` is excluded under contract
`twilightforest-experiment115-internal-cake-world-itemblock-v1`. The independent
`excludedTwilightForestExperiment115InternalBlockItemListEntries` diagnostic must equal one, and
the snapshot must retain exactly one exact bare public Experiment 115 food entry. A missing,
duplicate, tagged, nonzero-metadata, or differently sized internal/public entry aborts. The internal
identity also remains forbidden outside the completed global ItemList snapshot, so no recipe or
category data is silently removed. No replacement icon or generic transparent-image fallback is
used.

Witching Gadgets `1.7.25-GTNH` is version- and whole-JAR-SHA-pinned as
`7c5f6af01aabe2c22814a5c0a6241eaafe226e2b29a0c425e0b937d09cca5e26` for the exact
`WitchingGadgets:WG_CustomAir` temporary-light boundary. The mod registers
`BlockModifiedAiry` through Forge's default `ItemBlock`, and its obsolete no-op sub-block overload
does not override Minecraft 1.7.10's active signature. Inherited Thaumcraft enumeration therefore
leaks one metadata-zero stack into the completed global NEI `ItemList`. The block intentionally
uses `thaumcraft:blank`, renders no normal geometry, has no collision, normal drop, or pick item,
emits light level 14, creates `TileEntityTempLight`, and removes itself after its bounded lifetime.
The public Infused Gem fire effect places this world state programmatically; there is no survival
recipe, quest, drop, or pick-block acquisition path.

Only the exact amount-one, metadata-zero, no-NBT leaked stack is excluded under contract
`witchinggadgets-custom-air-owner-internal-temporary-light-world-state-itemblock-v1`, with
`excludedWitchingGadgetsCustomAirInternalBlockItemListEntries` required to equal one. Runtime
preflight requires the exact registry item/block bijection, classes, creative enumeration leak,
blank icon, context-free world-state traits (including the owner IBlockAccess light override behind
a rejecting access guard), tile class, and absence of a Forge inventory renderer.
The whole-JAR pin attests the owner block's world-dependent collision and pick implementations;
preflight does not invoke either method against a fabricated or null world. Other stack
shapes abort, and any later recipe or category occurrence remains a fail-closed `ITEM_IDENTITY`
error. The exporter does not invent an icon for intentionally invisible world state and does not
add a generic transparent-render fallback.

Applied Energistics 2 `rv3-beta-695-GTNH` is whole-JAR-SHA-pinned for the exact
`appliedenergistics2:tile.BlockCableBus` owner-internal multipart world-host boundary. AE2's
`AECableBusFeatureHandler` registers the `BlockCableBus` and its separate `AEBaseItemBlock` under
the same owner identity, but both direct item and block subitem enumeration are empty. The bare
host drops nothing, is nonopaque and nonnormal, and carries visible world state only through its
`TileCableBus` cables, parts, and facades. Its Forge inventory dispatcher is `ItemRenderer`, whose
block-specific renderer is `RendererCableBus`; rendering an empty bare host therefore correctly
produces no visible pixels.

Only the exact amount-one, metadata-zero, no-NBT bare host entry in the completed global `ItemList`
is excluded under contract `ae2-cablebus-internal-multipart-world-host-itemblock-v1`, with
`excludedAe2CableBusInternalBlockItemListEntries` required to equal one. Runtime preflight pins the
item/block registry bijection and classes, exact enabled feature-handler definition ownership,
shared non-null creative tab, independently empty item and block subitem boundaries, null drop,
block and tile semantics, and both renderer classes. Public cables, parts, and facades remain
eligible; an internal host encountered later in a recipe or category fails closed. Preflight does
not call the world-dependent `getPickBlock`, synthesize a representative multipart state, or add a
generic transparent-render fallback.

Botania `1.12.28-GTNH` is version- and whole-JAR-SHA-pinned for seven owner-internal world-state
IDs that NEI exposes as 22 global-browser stacks: one each of `Botania:bifrost`,
`Botania:cacophoniumBlock`, `Botania:enchanter`, `Botania:fakeAir`, `Botania:manaFlame`, and
`Botania:solidVine`, plus all 16 metadata values of `Botania:buriedPetals`. These identities are
transient placement helpers or in-world state representations rather than obtainable catalog
items. In particular, a visible `Botania:petal` creates `Botania:buriedPetals` only to delegate
placement; its metadata `0..15` values encode the 16 colors, not temporal progression. Breaking or
fertilizing the placed block returns or grows the corresponding color, while its deliberately
alpha-zero block sprite leaves world feedback to colored light and sparkles.

One separate catalog-only policy excludes the single `Botania:flower_structurelib` presentation
placeholder. That stack is StructureLib's “Any Botania Flower” display token, not a gameplay item
producer or an eighth world-state family. For every one of these eight named policies, the exporter
pins the registry ID, runtime item class, runtime block class, and exact ItemBlock-to-Block object
bijection before enumeration. Only the policy's exact size-one/no-NBT stack shape in the completed
global `ItemList` snapshot may be removed. Each ID has its own fail-closed diagnostic counter; the
buried-petal policy additionally requires cardinality 16 and the exact metadata mask `0xffff`.
Missing, duplicate, wrongly shaped, out-of-range, or NBT-bearing entries abort. Matching
catalog-only identities encountered later in a recipe, catalyst, or category adapter are
explicitly rejected except for the separately pinned synthetic furnace-fuel corpus below. No
broad Botania, hidden-item, or transparent-texture exception exists.

NEI's vanilla burn-time scan leaks exactly five owner-internal placed/equipped blocks into its
3,744-page Furnace Fuel category: `Botania:cacophoniumBlock` at source 1264,
`CarpentersBlocks:blockCarpentersBed` at 1292,
`CarpentersBlocks:blockCarpentersDoor` at 1295, `TConstruct:HeldItemBlock` at 2795, and
`TConstruct:BattleSignBlock` at 2796. They are not independent obtainable inventory identities;
the public owner items remain cataloged elsewhere. Contract
`nei-furnace-fuel-owner-internal-world-state-row-exclusion-v1` scans the entire loaded fuel corpus
before export, rescans it after the handler reload, then consumes only these exact source/slot/
alternative and catalog-policy bindings. Every exclusion is logged, the completed manifest must
report `excludedOwnerInternalFurnaceFuelRows: 5`, and any additional, missing, reordered, or
shape-drifted occurrence aborts. This is not a registry-wide skip or an icon-render fallback.

`Botania:cocoon` is intentionally retained. It is the public, craftable Cocoon of Caprice and is
used as a recipe output and input in the pinned pack. Botania's inventory block renderer constructs
a fresh `TileCocoon` at `timePassed=0` and passes `partialTicks=0` to its registered TESR; the
pinned TESR then evaluates `sin(0) * log(0)`, produces a NaN rotation, and emits no fragments. The
exporter applies one exact compatibility renderer under the whole-JAR Botania pin: it temporarily
leases only the `TileCocoon` TESR binding during that synchronous inventory draw, changes the
owner-created synthetic tile from tick zero to tick one for the owner TESR call, and restores both
the tile field and original renderer binding in `finally` paths. At tick one `log(1)=0`, so the
owner's intended static model angle is exactly zero while its model, texture, outer item transforms,
and renderer remain authoritative. The same immediate lease surrounds every complete NEI recipe
widget, so cocoon inputs and outputs receive the correction without replacing Forge's item renderer.
The adapter requires the exact registry item, ItemBlock and Block classes, tile class, TESR class,
bare meta-zero stack shape, exactly one successful catalog-icon invocation, and positive successful
recipe-widget coverage. Attempt, success, and failure telemetry is checked after exact renderer-map
restoration, which exposes adapter failures even if NEI catches the exception and draws its fire
substitute. The exporter logs and serializes both coverage counters. Any pin, client-thread,
renderer-map, invocation, shape, restoration, or output-image drift fails explicitly. This is not a
replacement sprite or a generic retry for transparent renders.

`Botania:prism` is also intentionally retained. It is the public, creative-visible Mana Prism and
the pinned Botanic Horizons recipe produces one bare metadata-zero prism. Its two owner block
textures are valid 32x32 translucent assets, but Minecraft/Angelica's legacy transparent mipmap
generator gamma-averages each 2x2 texel group and hard-zeros generated alpha below 96/255. Both
Prism level-one mips have a maximum pre-cutoff alpha of 91/255 and are therefore entirely
transparent; the standard 16x16 inventory projection samples those poisoned mips. The exporter
requires the canonical stitched sprites to have the zero retained CPU frames expected after
`TextureMap` uploads a non-animated sprite, so it resolves the active `prism0.png` and `prism1.png`
resources under exact length/SHA-256 pins and reconstructs their legacy level-one pixels. Before an
intercepted draw it reads only the two 32x32 level-zero and two 16x16 level-one regions from the
canonical live block atlas through a temporary FBO and requires byte-exact agreement. It then
temporarily leases NEI's exact `GuiContainerManager.drawItems` binding and clamps only the block
atlas `GL_TEXTURE_MAX_LEVEL` to zero while that exact bare Prism stack passes through the owner
`RenderItem`. It restores and verifies the original framebuffer/read buffer, six pixel-pack fields,
atlas maximum level, texture bindings, active texture unit, renderer state, and static NEI binding.
This keeps the owner's model, bounds, side textures, tint, transforms, blend mode, and minification
filter authoritative. The same
client-thread-only wrapper surrounds complete NEI widgets but intercepts only Prism calls, with
attempt/success/failure telemetry checked outside NEI's exception-swallowing fire substitution.
Exactly one corrected catalog icon and positive recipe-widget coverage are required; any class,
stack-shape, resolved-resource, live-atlas, mip, GL-state, renderer-binding, invocation, or
output-image drift fails explicitly.

`GalacticraftCore:item.flag` is likewise intentionally retained. The bare metadata-zero identity
is public, creative-visible, and craftable, and the placed entity derives its owner and flag type
from the player and item metadata. Galacticraft's inventory renderer asks the current player's
SpaceRace for a 48-by-32 `FlagData`; the isolated exporter player has no SpaceRace, so that lookup
returns no cloth data and the otherwise valid owner renderer does not produce a usable catalog
icon. Under the exact Galacticraft Core artifact and runtime-class pins, the exporter leases only
that owner renderer for the exact bare metadata-zero/no-NBT flag. During the synchronous owner
draw it supplies canonical `FlagData(48, 32)`, a fixed safe client-world time, and a fixed safe
synthetic entity ID so renderer output cannot vary with missing player state, world ticks, or the
entity counter. The owner model, texture, transforms, and render path remain authoritative.

The adapter snapshots and restores the renderer's mutable dummy-entity fields, client-world time,
OpenGL state, and Forge renderer binding on every success and failure
path, then verifies that restoration before accepting the image. The same scoped lease surrounds
complete NEI recipe widgets but intercepts only the exact flag identity. Exactly one corrected
catalog icon and positive recipe-widget coverage are required and serialized as independent
telemetry. Any artifact, registry, class, stack-shape, owner-renderer, state, global, invocation,
restoration, or output-image drift fails explicitly. There is no flag exclusion, replacement
sprite, broad Galacticraft adapter, or transparent-render fallback.

`WR-CBE|Addons:triangulator` is also retained as a public, craftable, redstone-tab item. WR-CBE
registers its 256 triangulator icons as CodeChickenLib-managed dynamic `TextureSpecial` sprites;
metadata zero and NEI's wildcard metadata `32767` both resolve to the deterministic unconfigured
slot `wrcbe_addons:triang_0`. Angelica's visible-only animation optimization marks that sprite at
the beginning of its first owner draw, one texture tick too late for the same-frame catalog capture,
so the stitched transparent placeholder would otherwise be sampled. Under exact WR-CBE and
CodeChickenCore whole-JAR pins, the exporter replays the owner's public `TriangTexManager`
`loadTextures()` method, verifies the exact Ring/Gradient resource hashes and the resulting
140-visible-pixel owner fingerprint, binds the canonical item atlas, and invokes only the owner's
slot-zero `TextureSpecial.updateAnimation()`. A temporary FBO then verifies the exact live 16x16
atlas region before Minecraft's normal `RenderItem` draw proceeds. The shared client-thread lease
intercepts only exact amount-one/no-NBT metadata-zero and wildcard stacks, restores all touched GL
and NEI renderer state, and records exactly one catalog icon plus positive recipe-widget coverage.
Any artifact, resource, class, array/slot, stack-shape, managed-pixel, atlas, GL-state, invocation,
or output-image drift fails explicitly; there is no replacement texture or transparent fallback.

Thaumcraft visibility is knowledge-independent. The exporter requires TCNEIAdditions' exact six
crafting and five usage replacement handlers, requires `showLockedRecipes=true`, and rejects the
legacy handlers. It never grants or mutates player research. Synthetic `ItemAspect` names are
decoded from their one-entry `Aspects` NBT compound and the global Thaumcraft aspect registry;
malformed or unknown tags fail instead of falling back to the player's `Unknown` label.

## Rendering and memory pressure

- Item icons use NEI's public `GuiContainerManager.drawItem` path at exactly `iconScale=1`.
- NEI fluid-display proxies become `fluid|fluid:<registry-name>` catalog nodes, with canonical
  fluid-tag identity and the decoded `FluidStack.amount` retained per recipe slot in millibuckets.
  Real filled containers remain item nodes.
- Recipe previews instantiate NEI's public `RecipeHandlerRef`/`NEIRecipeWidget`, enable widget mode,
  and draw the exact handler background, positioned stacks, foreground, and extras at exactly
  `recipeScale=2`.
- Drawing and framebuffer readback stay on the Minecraft client/GL thread.
- PNG compression runs on `pngThreads` daemon workers behind an `ArrayBlockingQueue` of
  `pngQueueCapacity`.
- Queue saturation blocks the producer and logs aggregated backpressure events. It never expands
  the queue or drops a PNG.
- The framebuffer is reused by dimension and readback storage grows only to the largest image.

The expensive part is unavoidable: GTNH has many item variants and recipes, and every one receives
a PNG. The current design optimizes peak heap, not elapsed time or output size. A faster parallel
option is to omit previews or deduplicate visually identical PNGs, but that weakens the exact NEI
snapshot contract and complicates viewer cache identity. This implementation keeps exact previews.
If runtime disk usage becomes the bottleneck, content-addressed post-processing is the safer next
step: it preserves pixels and references but adds a packaging/rewrite phase.

## Output contract

The published directory contains:

```text
items.json
categories.json
index.json
failures.json
manifest.json
mobs.json
blockdrops.json
icons/item/<namespace>/<key-sha256>.png
recipes/<semantic-category-id>/recipes.json
recipes/<semantic-category-id>/r<N>.png
```

`mobs.json` and `blockdrops.json` are explicitly empty because this module is scoped to NEI items
and recipes. Their presence remains compatible with the same web app's v1/v2 reader.

Successful manifests contain:

- `format: 1`, `profile: "gtnh-1.7.10"`, `aborted: false`;
- exact `minecraft`, `forge`, and `nei` pins;
- `pack: {"name":"GT New Horizons","version":"2.8.4","identitySource":"explicit-request"}`;
- exact normalized-data attribution:
  `{"sourceUrl":"https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4","projectUrl":"https://www.gtnewhorizons.com/","licenseIdentifier":"CC BY-NC-SA 4.0","licenseUrl":"https://creativecommons.org/licenses/by-nc-sa/4.0/"}`;
- exact `settings: {"iconScale":1,"recipeScale":2,"mobCanvas":256}`;
- the exact 46-entry `handlerPolicies` decision ledger and a `knowledgePolicy` asserting no
  player-research mutation;
- active mod IDs mapped to their non-blank human-readable display names;
- positive item, recipe, and category counts;
- exactly `diagnostics.failureEvents`, `failureEventsOmitted`, and `nei`;
- equality between exportable handlers, loaded categories, emitted categories, enumerated recipes,
  rendered widgets, emitted recipes, rendered item icons, and emitted items; registered handlers
  must additionally equal exportable categories plus the 20 explicit non-recipe exclusions,
  22 independently verified empty-category exclusions, and one exact unbound material-template
  exclusion;
- exactly 25 adapted categories, 20 non-recipe exclusions, 22 empty-category exclusions, one
  unbound material-template exclusion, one named AE2FC fluid-drop browser
  placeholder, one named AE2FC fluid-packet browser placeholder, two named Blood Magic internal
  helpers, one named ArchitectureCraft unmaterialized-cladding browser placeholder, one named
  Avaritia empty-matter-cluster browser placeholder, seven named Botania world-state IDs totaling
  22 ItemList stacks, the exact `0xffff` buried-petal color metadata mask, one named Botania
  StructureLib “Any Botania Flower” presentation placeholder, one named Carpenter's Bed internal
  block alias, one named Carpenter's Door internal block alias, one named Steve's Carts unconfigured
  Modular Cart browser placeholder, one named TConstruct Battle Sign internal-world ItemBlock, one
  named TConstruct Held Item/Frying Pan internal-world ItemBlock, and one exact Thaumcraft
  metadata-zero internal portable-hole ItemBlock while retaining its metadata-15 diagram sentinel,
  one exact Thaumcraft metadata-zero internal Eldritch Portal ItemBlock while retaining the public
  metadata-zero Eldritch Eye and separately registered add-on portal items, two exact Thaumic
  Horizons illumination world-state ItemBlocks while retaining all 16 public illumination-focus
  colors, and one exact Twilight Forest internal Experiment 115 world-state ItemBlock while
  retaining exactly one distinct public Experiment 115 food, one exact Witching Gadgets
  temporary-light world-state ItemBlock, and one exact AE2 CableBus owner-internal multipart
  world-host ItemBlock while retaining public cables, parts, and facades,
  exactly one corrected Cocoon catalog icon, positive corrected Cocoon recipe-widget invocations,
  exactly one corrected Mana Prism catalog icon, positive corrected Mana Prism recipe-widget
  invocations, exactly one corrected Galacticraft Flag catalog icon, positive corrected Flag
  recipe-widget invocations, exactly one corrected WR-CBE Triangulator catalog icon, positive
  corrected Triangulator recipe-widget invocations, exactly three corrected ProjectBlue
  control-panel catalog icons, exactly three corrected ProjectBlue recipe-widget invocations,
  exactly one corrected Thaumcraft Runed Stone metadata-10 catalog icon,
  exactly one source-bound GregTech Scanner/Forestry Scanned Sapling recipe occurrence and exactly
  one claimed custom-name capability,
  488 informational input-only pages, and at least one knowledge-independent
  `ItemAspect` name;
- zero unloaded, ambiguous, duplicate, omitted, and general failure counts;
- an empty `failures.json`.

## Failure codes

Diagnostic staging and the game log use stable prefixes:

- `HANDLER_UNLOADED:` — readiness timeout, absent category-wide contract, null/empty category, or
  runtime pin drift;
- `HANDLER_AMBIGUOUS:` — blank/mismatched handler identity or conflicting category identifiers;
- `HANDLER_DUPLICATE:` — duplicate semantic category key, pinned policy identity, or category-ID
  hash collision;
- `RECIPE_VISIBILITY_GATED:` — locked Thaumcraft visibility/config/registry policy drift;
- `RECIPE_SEMANTICS:` — null stacks/lists, absent output, or a handler exception;
- `ITEM_IDENTITY:` — missing registry identity, invalid NBT identity, blank name, or key collision;
- `QUANTITY_INVALID:` — negative published stack amount;
- `ITEM_ICON_RENDER:` — item or handler icon could not be rendered visibly;
- `RECIPE_WIDGET_RENDER:` — exact widget construction, sizing, drawing, or readback failed;
- `PNG_WRITE:` — bounded background encoding failed.

These are failures, not warnings. A final dataset with any one of them is never published.

## Tests and verification

```bash
JAVA_HOME="$GTNH_EXPORT_BUILD_JAVA" \
PATH="$GTNH_EXPORT_BUILD_JAVA/bin:$PATH" \
./gradlew test \
  -PneiApiJar="/absolute/path/to/NotEnoughItems-2.8.44-GTNH.jar" \
  -PdreamCoreJar="/absolute/path/to/GTNewHorizonsCoreMod-2.7.268.jar" \
  -PgregTechApiJar="/absolute/path/to/gregtech-5.09.51.482.jar"
```

Focused tests cover recursive NBT canonicalization, explicit request pins/scales, category identifier
ambiguity and duplication, category-wide loading, the strict manifest reconciliation contract,
Forge 1.7.10 parsing of the exact NEI dependency metadata, OpenGL row readback, and bounded-queue
warning aggregation. They also cover explicit integrated-world authorization and fail-closed
automation-save ownership/settings, generated event-subscriber accessibility, and exact NEI
catch-and-continue error classification. Additional tests cover the authoritative server-side seed,
the exact Thaumcraft replacement registry, the 46-entry handler decision ledger, research-neutral
knowledge policy, knowledge-independent `ItemAspect` NBT naming, the two exact AE2FC browser-only
placeholder policies, two Blood Magic helper policies, one ArchitectureCraft cladding policy, and
one Avaritia empty-cluster policy, the seven exact Botania world-state policies, the 16-value
buried-petal color mask, the separate StructureLib flower presentation-placeholder policy, the
two exact Carpenter's Blocks internal multipart alias policies, the Cocoon of Caprice owner-TESR
finite-time compatibility renderer, the exact Steve's Carts unconfigured-cart placeholder policy,
the two TConstruct internal equipment ItemBlock policies and public NBT-tool separation, the exact
Thaumcraft portable-hole internal ItemBlock policy with metadata-15/public-focus separation, and
the exact Thaumcraft Eldritch Portal internal ItemBlock policy with public-Eldritch-Eye/add-on
separation, the exact Gadomancy artifact/portal-placer registration and retained ItemList
cardinality contract, the two exact Thaumic Horizons world-light policies with public-focus
`0xffff` retention, the exact Twilight Forest Experiment 115 internal/public boundary and
cardinality contract, the Witching Gadgets temporary-light policy, the exact AE2 CableBus
owner-registration/rendering boundary and catalog cardinality, the exact AE2 Matrix Frame
owner-registration/rendering boundary and catalog cardinality, and the strict Dreamcraft Nothing
legacy LootBag sentinel policy, the non-strict exact-bare LittleTiles unparameterized microtile
carrier policy with tagged same-registry retention and exact owner renderer topology, the exact
MalisisDoors bare custom-door carrier policy with NBT retention, shared renderer ownership, and
zero recipe/quest graph-reference invariants, the exact MalisisDoors bare mixed-block carrier
policy with four-key producer NBT retention, shared item/block renderer ownership, and zero
recipe/quest graph-reference invariants, plus
fluid-drop quantity preservation. Crop-focused tests lock the
complete canonical ratio-matrix fingerprint, repaired craft/usage bucket replay, adversarial object
hash independence, compact audit-basis drift checks, restored left/right array order, and positional
preview linkage rather than only an unordered parent multiset.

## Runtime-only uncertainties

Compilation and unit tests can prove API compatibility and deterministic local contracts. They
cannot prove behavior of all 200+ mods without launching the actual pack. The first supervised
2.8.4 run still needs to establish:

1. every standard handler exposes and tolerates its complete-category operation, while all 13
   exact adapters reproduce their pinned audited contracts in the initialized client;
2. every handler tolerates full-category construction in the deterministic creative-superflat
   automation world;
3. every mod's widget tolerates offscreen widget-mode drawing outside an open `GuiRecipe` screen;
4. every retained ItemList and recipe-only stack has a visible icon under the pack's active texture/shader
   state;
5. the total run time, peak heap, and PNG footprint fit the launcher's machine budget.

Each uncertainty has a fail-closed diagnostic. Automatically skipping a handler or silently falling
back to title-screen data is not an acceptable fix; this pinned NEI version does not initialize its
runtime recipe corpus at the title screen.

One upstream boundary remains important: NEI's private metadata-probing heuristic silently replaces
a tooltip exception with an empty tooltip before deciding whether visually identical damage values
are distinct. It emits no event or log that an external client mod can audit. This snapshot therefore
claims completeness for NEI's materialized player-visible runtime corpus, not every theoretical raw
item-registry metadata value. Recipe-only stacks are still discovered while enumerating handlers.
A future typed registry exporter can audit raw variants in parallel, but patching NEI's private
heuristic would make this release a fork of the pack rather than a snapshot of the official runtime.
