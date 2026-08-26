# In-game Recipe Tree viewer port plan: Minecraft 1.12.2 and 1.21.1

Status: Draft
Baseline: the Forge 1.20.1 in-game Recipe Tree planner/viewer in this repository
Targets: Forge 1.12.2 with JEI/HEI 4, and NeoForge 1.21.1 with JEI 19

## 1. Goal

Port the current 1.20.1 in-game Recipe Tree experience—not merely the recipe exporter—to exact
Minecraft targets 1.12.2 and 1.21.1. The resulting client mods should let a player open a live,
interactive production tree from an item, choose recipes for each branch, inspect native recipe
layouts, calculate quantities, preserve plans and history, and exchange compatible tree files with
the web/mobile viewers.

The recommended delivery order is:

1. Freeze and test the 1.20.1 behavior and portable format.
2. Port the viewer to 1.21.1 with full JEI parity.
3. Port the proven behavior contract to 1.12.2 in stages, beginning with a compact tree and a
   one-to-one native recipe preview.
4. Promote the more fragile 1.12.2 native-layout and optional integration features only after real
   pack testing.

The target is 1.21.1 specifically, not every 1.21 minor release. Other 1.21 releases require their
own mappings, dependency metadata, build, and smoke test.

## 2. Scope

### In scope

- The configurable G-key entry point from a held item, a hovered inventory slot, and the recipe
  viewer overlay.
- Live recipe and usage queries against JEI/HEI.
- Compact and Details tree modes, native recipe previews, item/ingredient alternatives, pan, zoom,
  tooltips, and recipe selection.
- Exact quantities, repeated-input grouping, craft counts, multiple outputs, process summaries,
  material summaries, and byproduct allocation.
- Multiple starting outputs, favorites, **No recipe**, history, comparison, discovery/recipe-book
  behavior, and local persistence.
- `.mrtree.json` import/export for branches whose item and recipe identities are portable.
- Typed non-item ingredients, including fluids and mod-defined JEI ingredient types.
- Compatibility and regression testing for the exporter code that shares a target module or helper.

### Out of scope for the first ports

- Porting or redesigning the exporter pipeline itself, except for the narrowly required work that
  makes the exporter emit the same versioned ingredient/recipe identities the viewer and web
  resolver consume.
- Cross-Minecraft-version plan migration. A 1.20.1 tree must not be assumed valid in 1.21.1 or
  1.12.2, even when registry names happen to match.
- A 1.21.1 REI compatibility layer. The existing 1.21.1 target and the first port are JEI-only.
- Parsing data-pack or CraftTweaker files as an alternate recipe source. The live JEI/HEI registry
  remains the source of truth.
- Account sync.
- New 1.12.2 coremods or pack mutation added solely for the viewer.

## 3. Baseline that must be frozen first

The port baseline is the current working-tree implementation in
[`recipe-export-mod-1.20.1`](../recipe-export-mod-1.20.1), not the older committed screen. It is a
large uncommitted feature set: `RecipeTreeScreen` is approximately 5,800 lines and currently has
about 2,600 changed lines relative to `HEAD`, in addition to new REI integration files. Create a
reviewed commit and tag for the accepted viewer behavior before either port branch is created.

The main source surface is:

| Source | Current responsibility | Porting treatment |
| --- | --- | --- |
| [`RecipeTreeClient.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/RecipeTreeClient.java) | Key registration, target precedence, discovery, open/reopen, and logout handling | Replace with a target-specific client/platform adapter |
| [`RecipeTreeScreen.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/RecipeTreeScreen.java) | Screens, live JEI queries, tree model, rendering, summaries, history, and portable JSON | Split by responsibility before copying; do not backport the monolith mechanically |
| [`RecipeTreeProgress.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/RecipeTreeProgress.java) | Plans, favorites, history, discovery, collapsed categories, and last-viewed state | Add an explicit state envelope and exact ingredient identities |
| [`RecipeQuantityMath.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/RecipeQuantityMath.java) | Pure quantity calculations | Preserve as a conformance-tested algorithm |
| [`SupplementalRecipeInputs.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/SupplementalRecipeInputs.java) and [`IngredientOptionSets.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/IngredientOptionSets.java) | Input normalization and alternatives | Preserve behavior through shared fixtures |
| [`RecipeTreeShareFiles.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/RecipeTreeShareFiles.java) | Share file location and selection | Backport the behavior with Java 8 file APIs |
| [`Ae2DiscoveryBridge.java`](../recipe-export-mod-1.20.1/src/main/java/com/recipetree/jeiexport/Ae2DiscoveryBridge.java) and the REI adapter | Optional, version-sensitive integrations | Keep outside the portable core and port separately, if supported |

Before freezing the baseline, capture acceptance behavior for:

- target priority: JEI overlay, JEI recipe GUI, hovered container slot, then hands;
- left-click input expansion and right-click usage selection;
- compact/details layout, transformed hit testing, picker scrolling, and category collapse;
- quantities, alternatives, favorites, **No recipe**, multiple roots, summaries, byproducts, history,
  comparison, and discovery mode;
- save/restore, disconnect/reconnect, and `.mrtree.json` round trips; and
- the current safety limits: at most 16 starting roots, 2,048 shared selections, 1 MiB share files,
  12 in-game branch levels, and 32 inputs per node.

The existing helper tests are useful but do not exercise the screen, client hooks, persistence,
restoration, live recipe queries, or portable serialization. Those gaps must be addressed with
behavior fixtures before the same logic exists in three versions.

## 4. Pre-port release gates

### Gate A: reconcile the portable schema

The current Java viewer emits and imports a multi-root `roots` array, while
[`src/graph/portableTree.ts`](../src/graph/portableTree.ts) models and parses only a single
`rootKey` plus top-level `selections`. The documentation already promises multi-root sharing, so
the implementations and the stated contract are inconsistent.

Recommended resolution:

- Keep accepting format version 1 as a single-root history.
- Introduce format version 2 for multi-root histories, with each root carrying its own `rootKey`,
  `productionPlan`, and `selections`.
- Teach web/mobile and all three in-game viewers to read both versions before any target writes
  version 2.
- Check in canonical single-root and multi-root JSON fixtures. Every implementation must parse the
  same fixtures and produce equivalent normalized output.
- Apply the same pack checks everywhere: exact Minecraft version plus every pack identity field
  carried by the share. Never resolve a stable recipe ID against a different pack publication.
- Decide explicitly whether the in-game depth cap remains 12 or is raised after profiling. Until
  then, reject deeper web histories with a clear capability error instead of partially restoring
  them.

The canonical contract remains documented in
[`docs/portable-recipe-trees.md`](portable-recipe-trees.md).

### Gate B: define separate identity contracts

Runtime identity and portable identity have different requirements and must not be represented by
one convenient string.

1. **Planner ingredient identity** must distinguish the JEI ingredient type and the recipe-relevant
   subtype. For items this includes metadata/subtype data on 1.12.2 and recipe-relevant components
   on 1.21.1. It is used for plan equality, favorites, history, caches, and persistence. A portable
   ingredient key uses the same semantics through a versioned project codec.
2. **Discovery identity** is a separate product policy. Preserve the current registry-item-level
   discovery behavior by default, so discovering one subtype reveals the item family. Any change
   to subtype-sensitive discovery is a deliberate, versioned behavior change with migration tests.
3. **Portable recipe identity** must be reproducible by the target exporter and the web resolver.
   The preferred form is `category-id|recipe-id` when JEI exposes a stable recipe registry name.

Moving plans and favorites from the current registry-item-only key to the planner ingredient key is
an intentional subtype-safety change, not frozen parity. Cover its migration and user-visible
favorite behavior with fixtures. Discovery remains separately coarse unless the product behavior is
explicitly changed.

The current 1.20.1 recipe-key fallback uses `recipe.toString()`. That may contain a process-local
identity hash and must never be written to a portable share. Likewise, registry-name-only item
state can merge potions, NBT variants, metadata variants, or component-bearing stacks.

For recipes without a registry name, implement a versioned semantic fingerprint in the target
exporter and viewer: hash the category ID and canonical role/type groups, ordered slots, ingredients,
quantities, and alternatives, sorting unordered groups and alternatives deterministically. Keep
configuration-derived timing outside recipe identity. A fingerprint collision must not silently
select one wrapper: prove the recipes interchangeable across all relevant semantics, add a
deterministic shared disambiguator, or mark the collision group unshareable. Until that fingerprint
has matching exporter/web tests, the recipe may be used in-session but any affected branch is
explicitly unshareable.

Portable references such as `[categoryIndex, recipeIndex]` remain optional same-publication fast
paths. The importer must verify the stable recipe identity before using a reference.

### Gate C: version persistence

Add a state envelope before cloning `RecipeTreeProgress`:

```json
{
  "schemaVersion": 2,
  "minecraftVersion": "1.21.1",
  "ingredientIdentityVersion": 1,
  "data": {}
}
```

The exact schema number is illustrative; the requirements are not:

- save Minecraft version, resolved pack identity, and identity-codec version;
- preserve unknown or unresolved records on load, but leave their branches collapsed;
- index ingredients once per JEI runtime instead of rescanning the entire registry for every
  imported selection;
- write through a temporary file and atomically replace the last complete state;
- back up a pre-migration state file before the first schema-changing write; and
- never serialize live recipe-layout objects or retain them across a runtime reset.

## 5. Architecture for both ports

Define the same behavioral boundaries in each target, even when their source cannot be shared:

| Boundary | Responsibilities |
| --- | --- |
| `ClientPlatform` | Key registration, tick/screen events, hovered inventory target, clipboard, config path, screen opening, and logout |
| `ViewerBridge` | Runtime lifecycle, typed ingredient conversion, focus creation, lazy recipe/usage queries, category metadata, and native layout creation |
| `IngredientKeyCodec` | Exact runtime/persistent keys, display names, amounts, copies, rendering, and tooltips |
| `DiscoveryKeyCodec` | Deliberately coarser discovery identity, independent from plan/persistence equality |
| `RecipeIdentity` | Registry IDs, semantic fingerprints, portable-share eligibility, and verification |
| `PlannerModel` | Roots, plan nodes, selections, quantities, alternatives, summaries, byproducts, favorites, and history descriptors |
| `PlannerScreen` | Layout, drawing, hit testing, picker/history sub-screens, pan/zoom, and input focus |
| `ProgressStore` / `PortableTreeCodec` | Versioned local state and bounded, validated share files |

`PlannerModel` and serialization DTOs must not hold Minecraft, JEI, screen, or native-layout
objects. History stores descriptors and keys; a live screen reconstructs layouts lazily after the
viewer runtime is available.

Do not introduce a compiled cross-version source dependency for the first port. The 1.12.2 module
is intentionally isolated and must remain Java 8 compatible. Share the contract, names, golden JSON
fixtures, and algorithm test vectors. A small pure Java 8 planner kernel can be extracted later if
the duplication proves costly, after the seams have been validated by the 1.21.1 port.

Packaging is a release decision. The preferred public distribution is a viewer-only client artifact
per Minecraft version so a 1.12.2 user does not need exporter commands or optional pack-repair
loading-plugin code merely to use the tree. Before splitting, assign a distinct mod ID, config/state
ownership, and dependency metadata, then test co-installation with the existing `jeiexport` artifact;
two artifacts with the same mod ID cannot coexist. It is still reasonable to develop against the
existing target modules first because they already contain tested JEI identity and rendering
adapters. If a combined JAR is retained, planner packages must not depend on the export coordinator,
and exporter startup must remain unchanged when the planner is unused.

## 6. Minecraft 1.21.1 port

### Compatibility target

- Minecraft 1.21.1 only.
- NeoForge 21.1.244 as the initial tested minimum, with an upper bound before the next incompatible
  NeoForge line. Do not retain the current unverified `[21.1,)` release range.
- Java 21.
- JEI 19.21.2.313 as the initial tested minimum and an upper bound before JEI 20. Raise the minimum
  only when a required API and the migration are tested.
- JEI only for the first release.

The existing [`recipe-export-mod-1.21.1`](../recipe-export-mod-1.21.1) module already establishes
NeoForge, Java 21, JEI 19, modern `ResourceLocation` construction, `NeoForgeTypes.FLUID_STACK`, and
component-aware exporter behavior. Reuse those target adaptations without coupling the planner to
the exporter job.

### Platform and lifecycle mapping

| 1.20.1 behavior | 1.21.1 implementation |
| --- | --- |
| Register G key | `RegisterKeyMappingsEvent` on the mod event bus |
| Open from normal gameplay | Poll the binding from `ClientTickEvent.Post` |
| Open while another screen is active | `ScreenEvent.KeyPressed.Pre`, respecting focused text fields and cancellation |
| Clear world-bound state | `ClientPlayerNetworkEvent.LoggingOut` |
| Clear JEI-bound state | `IModPlugin.onRuntimeUnavailable()` |
| Draw screens and JEI layouts | `GuiGraphics` with explicit transformed coordinates and clipping |
| Vertical mouse wheel | Use the 1.21 four-argument `mouseScrolled(mouseX, mouseY, scrollX, scrollY)` signature |

Use the client subscriber style established by the existing
[`ExportEvents.java`](../recipe-export-mod-1.21.1/src/main/java/com/recipetree/jeiexport/ExportEvents.java),
but put key registration on a separate mod-bus client subscriber; the tick, screen, and logout
events belong on the game bus. Clear cached layouts, ingredient indexes, restored screen instances,
and optional discovery handles on either logout or JEI runtime unavailability.

### JEI 19 bridge

- Wrap `IRecipeManager` focus/category/recipe lookups in a `Jei19RecipeCatalog`; keep them lazy and
  bounded.
- Create JEI-owned `IRecipeLayoutDrawable` instances only for visible cards or visible Details
  nodes. Use direct `tick()` and bordered-bounds APIs rather than carrying forward the 1.20.1
  reflection shims.
- Prefer the error-safe layout-creation API available at the selected JEI floor, so one broken mod
  recipe does not close the planner.
- At the current JEI 19.21.2.313 floor, isolate the deprecated slot-tooltip read and render the
  resulting tooltip at absolute screen coordinates. Raise the JEI floor before adopting the newer
  slot `drawTooltip` API.
- Transform recipe-local hit coordinates through the same pan/zoom transform used for drawing, but
  place the final tooltip at absolute screen coordinates.
- Use JEI's typed ingredient manager for custom types and amounts. Do not special-case only item and
  fluid stacks.

### 1.21.1 identity work

ItemStack NBT was replaced by data components before 1.21.1, so use
`ItemStack.isSameItemSameComponents` for exact item comparisons and
`FluidStack.isSameFluidSameComponents` for exact fluid comparisons that ignore amount. Use JEI's
semantic subtype identity—not indiscriminate full-component equality—when deciding whether an
ingredient satisfies a recipe slot. Renames or unrelated custom components must not make an
otherwise valid recipe ingredient unusable.

Create a `PersistentIngredientKeyCodec` that combines the JEI ingredient-type UID with a
project-owned, canonical subtype encoding. Do not serialize an arbitrary UID object's
`toString()`. If a deprecated JEI unique-ID method is retained for the first release, confine it to
the codec, pin the tested JEI floor, version its output, and add component-bearing fixtures before
writing real state.

Migration from a 1.20.1 state file is best-effort only. Simple registry-only items may migrate;
custom JEI types and component-bearing stacks must remain unresolved unless a tested alias exists.
Never bind an unresolved key to a visually similar item.

### 1.21.1 implementation slices

1. Add the versioned model, codecs, state fixtures, and JEI 19 bridge with no screen.
2. Deliver the G-key vertical slice: held/overlay/container target, compact tree, recipe picker,
   selection, quantity propagation, pan, and zoom.
3. Add native Details nodes, tooltips, alternatives, responsive controls, and all nested screens.
4. Add favorites, **No recipe**, multi-root, history, comparison, summaries, byproducts, and
   discovery behavior.
5. Add v1/v2 portable import/export with exact pack matching and unshareable-branch diagnostics.
6. Add optional AE2 discovery behind a target-specific adapter.
7. Run the full conformance, GUI, lifecycle, and real-pack test matrix.

## 7. Minecraft 1.12.2 port

### Compatibility target

- Minecraft 1.12.2.
- Forge 14.23.5.2860 as the initial development and pack-smoke target. A viewer-only artifact may
  publish a wider 14.23.5 range only after testing its chosen floor and ceiling. If the viewer
  remains in the combined exporter artifact, existing pack-repair paths remain separately gated to
  exact Forge 14.23.5.2860.
- Java 8 bytecode and Java 8 library APIs.
- JEI/HEI `[4.12.0.214, 5.0.0)`, compiling and testing both the JEI 4.12.0.214 floor and the
  supported HEI floor.

The existing [`recipe-export-mod-1.12.2`](../recipe-export-mod-1.12.2) module already captures the
two runtime pieces that JEI 4 requires: `IIngredientRegistry` during plugin registration and
`IJeiRuntime` when JEI becomes available. Preserve that lifecycle.

### Legacy API mapping

| Modern viewer concept | Forge 1.12.2 / JEI 4 implementation |
| --- | --- |
| `Screen` | `GuiScreen` |
| `init`, `render`, `tick`, `removed` | `initGui`, `drawScreen`, `updateScreen`, `onGuiClosed` |
| `Button`, `EditBox` | `GuiButton`, `GuiTextField` |
| GLFW input | LWJGL2 `Keyboard` and `Mouse` |
| `GuiGraphics` and pose transforms | `GlStateManager`, `Tessellator`, `BufferBuilder`, and explicit matrix/scissor state |
| `KeyMapping` registration | `KeyBinding` plus `ClientRegistry.registerKeyBinding` |
| Client tick | `TickEvent.ClientTickEvent` at `END` phase |
| Screen key interception | cancelable `GuiScreenEvent.KeyboardInputEvent.Pre` |
| Hovered container item | public `GuiContainer.getSlotUnderMouse()` |
| `IRecipeManager` | `IRecipeRegistry` |
| Modern typed ingredient | a small `{IIngredientType<T>, T}` value object backed by `IIngredientRegistry` |
| Modern semantic slot view | `IRecipeWrapper.getIngredients(IIngredients)` recorded by a planner adapter |
| Native recipe card | `IRecipeLayoutDrawable.setPosition`, `drawRecipe`, and foreground `drawOverlays` |

Convert GUI coordinates through `ScaledResolution` before setting `glScissor`. Every native JEI
draw must save and restore matrix, blend, depth, color, texture, and scissor state, because old
custom categories often assume a particular OpenGL state.

### Reuse the legacy semantic adapters

Do not derive 1.12.2 plan semantics only from visible recipe-layout slots. Layouts expose less role
information, and some broken categories can fail to build a drawable even though their wrappers
still describe valid ingredients.

Extract a read-only planner adapter from the existing:

- [`RecordingIngredients.java`](../recipe-export-mod-1.12.2/src/main/java/com/recipetree/jeiexport112/RecordingIngredients.java), which records typed and deprecated class-based `IIngredients`
  setters and preserves nested alternatives;
- [`IngredientQuantity.java`](../recipe-export-mod-1.12.2/src/main/java/com/recipetree/jeiexport112/IngredientQuantity.java), which already recognizes item stacks, fluids, aspects, and conventional custom quantity accessors;
- [`LegacyIngredientIdentity.java`](../recipe-export-mod-1.12.2/src/main/java/com/recipetree/jeiexport112/LegacyIngredientIdentity.java), for custom ingredient types whose helpers omit semantic fields; and
- [`OreDictionarySlotIdentity.java`](../recipe-export-mod-1.12.2/src/main/java/com/recipetree/jeiexport112/OreDictionarySlotIdentity.java), for logical interchangeable-input identity.

Also preserve the relevant `RecipePhase` transformations and `ZeroQuantityPolicy`: coalesce
flattened OreDictionary alternatives, promote returned inputs, rebuild indexes after promotion, and
classify zero-quantity slots. Do not confuse a per-recipe retained input/tool with
`IRecipeRegistry.getRecipeCatalysts()`, whose catalysts describe category-wide machines or crafting
stations and are not material requirements.

Use a deterministic type key plus JEI helper unique ID for exporter-compatible portable keys; never
use `IIngredientType.toString()`. JEI 4's item helper normally covers registry name, metadata, and a
registered subtype-interpreter result, but does not promise arbitrary NBT coverage. When local plan
equality or persistence must distinguish additional NBT, use a separate versioned exact local codec,
excluding stack count. Store the selected concrete alternative separately from an OreDictionary
slot identity. There are no modern tags to substitute for this behavior.

Unknown custom ingredient quantities must be represented as unsupported or unit-categorical in
production math; do not silently invent an exact amount of one. This is intentional hardening from
the current 1.20.1 fallback, which warns and assumes one. Define the unsupported/unit UI and summary
behavior in acceptance tests, and extract an explicit exact/unit/unsupported result rather than
reusing the legacy fallback unchanged.

### Stable 1.12.2 recipe identity

This is the largest release blocker. The current exporter obtains a registry name from
`ICraftingRecipeWrapper`, but most legacy machine wrappers have no registry ID. Implement the
semantic fingerprint from Gate B in both the 1.12.2 exporter and planner, then publish the same
identity in the web dataset. If the fingerprint is absent or ambiguous, local planning remains
available but the affected branch cannot be shared.

### Staged user interface

Old JEI recipe renderers frequently assume unscaled coordinates and legacy scissor state. Do not
make transformed native layouts a blocker for the first useful 1.12.2 release.

**Stage 1: robust compact viewer**

- G over the JEI overlay, a container slot, or a held item.
- Typed compact ingredient tree, quantities, alternatives, recipe picker, pan, and zoom.
- Render the selected native JEI recipe preview at one-to-one scale in a fixed inspector outside
  the transformed tree canvas.
- Favorites, **No recipe**, persistence, history, summaries, byproducts, and multiple roots.
- Portable sharing for branches with verified stable identities.

**Stage 2: compatibility-gated parity**

- Native JEI recipe boxes inside Details nodes after GUI-scale and real-pack testing.
- Recipe-GUI hover opening. Keep the JEI 4.12 floor and feature-detect the later public
  `IRecipesGui.getIngredientUnderMouse()` method; fall back to overlay/container/hand targets when
  unavailable. Detect it reflectively so JEI 4.12 floor bytecode does not link the later method, and
  do not link to viewer implementation internals.
- Recipe-book discovery and optional AE2 rv6 terminal discovery through separately versioned
  adapters.
- Any category-specific layout or quantity adapters justified by a reproducible fixture.

Query visible recipes only after a player/world and the JEI/HEI runtime are available. RecipeStages
and ItemStages can change visibility while the player remains in the same world. Re-query on screen
and picker opening, avoid cross-screen wrapper-query caches unless a stage-change epoch invalidates
them, and validate a saved favorite against the current focused query before applying it. Also clear
query/layout caches on disconnect and never precompute a title-screen catalog for staged packs.

### Java 8 rules

The port cannot use records, pattern-matching `instanceof`, switch expressions, `var`, `List.of`,
`List.copyOf`, stream `.toList()`, or `Files.readString`/`writeString`. Use immutable Java 8 POJOs,
defensive collection copies, explicit types, and buffered UTF-8 file I/O. Enforce `--release 8` in
the planner compilation path, as the existing target build already does.

## 8. Feature delivery matrix

| Capability | 1.21.1 first release | 1.12.2 Stage 1 | 1.12.2 Stage 2 |
| --- | --- | --- | --- |
| Held, overlay, and container G-key target | Required | Required | Required |
| JEI recipe-page hover target | Required | Fallback permitted | Feature-detected |
| Compact tree, picker, quantities, alternatives | Required | Required | Required |
| Typed fluids/custom ingredients | Required | Required | Required |
| Native fixed recipe preview | Required | Required | Required |
| Native recipe layouts in transformed Details nodes | Required | Deferred | Required per compatible category |
| Favorites and **No recipe** | Required | Required | Required |
| Multiple roots, history, comparison | Required | Required | Required |
| Materials, processes, byproducts | Required | Required | Required |
| Local versioned persistence | Required | Required | Required |
| v1/v2 portable import/export | Required | Stable-ID branches only | Stable-ID branches only |
| Recipe-book discovery | Required | Deferred | Required |
| AE2 discovery | Optional adapter | Deferred | Optional adapter |
| REI | Out of scope | Not applicable | Not applicable |

## 9. Verification plan

### Contract and unit tests for every target

- exact ingredient identity for ordinary and subtype-bearing ingredients;
- stable recipe identity and deterministic alternative ordering;
- requested amounts, output counts, repeated inputs, retained tools, multiple outputs, and
  byproducts;
- path restoration, multiple roots, favorites, **No recipe**, history, comparison, and missing
  recipes;
- state migration, atomic replacement, malformed/truncated input, unknown fields, and unresolved
  records;
- v1 single-root and v2 multi-root golden portable fixtures, wrong Minecraft version, wrong pack,
  oversized file, too many selections, excessive depth, and unshareable recipes; and
- runtime-unavailable/logout invalidation with reconstruction from descriptors.

### 1.21.1 fixtures and smoke packs

- Vanilla shaped, shapeless, cooking, smithing, brewing, and multi-output recipes.
- Same base item with different components: potions, enchanted books, fireworks, goat horns, and
  custom-data stacks.
- Component-bearing fluids, large tags, and at least one real custom JEI ingredient type.
- Broken or no-ID recipe layouts.
- A minimal NeoForge + JEI instance and a real pack with representative Create, Mekanism, and AE2
  recipes.
- World leave/rejoin, resource reload, JEI runtime loss, multiplayer join, and dedicated-server boot
  to prove the client-only artifact does not load client classes on the server.

### 1.12.2 fixtures and smoke packs

- JSON crafting, programmatic furnace, and machine wrappers.
- Item metadata 0/1/wildcard, NBT subtypes, OreDictionary alternatives, and repeated requirements.
- Fluids, Thaumcraft aspects, and a custom typed ingredient with a quantity and renderer.
- Retained tools/catalysts, multiple outputs, missing recipe IDs, semantically duplicate wrappers,
  reordered wrappers, and valid semantics with a failing layout.
- Recipes hidden before and revealed after stage synchronization.
- Standard JEI 4.12.0.214, the supported HEI floor, a large HEI pack, Multiblock Madness, and
  MeatballCraft as a stress/compatibility gate.

### GUI and performance checks

Run both ports at GUI scales 1–4, narrow and wide windows, Compact and Details modes, and multiple
zoom levels. Verify input focus, rebound keys, picker scrolling, pan/zoom, clipping, z-order,
tooltips, and click targets. Instrument and bound recipe queries and layout caches; only visible
recipe cards are created, ticked, and drawn. A large recipe catalog must not add an unbounded scan
to every client tick or frame.

Each target release must also run its existing exporter test/build matrix. A viewer port is not
complete if it changes an export, loading policy, or dependency floor unintentionally.

## 10. Work packages and exit criteria

### WP0 — Freeze the contract

- Commit/tag the accepted 1.20.1 viewer baseline.
- Add behavioral test vectors and golden portable fixtures.
- Resolve multi-root schema, exact pack validation, item subtype persistence, and shareable recipe
  identity.

Exit: web/mobile and 1.20.1 agree on the same checked-in single-root and multi-root histories.

### WP1 — Establish the seams in 1.20.1

- Separate platform events, JEI queries, identity, model, screen, persistence, and portable JSON
  responsibilities without changing user behavior.
- Replace portable `recipe.toString()` output with stable or explicitly unshareable identities.

Exit: the frozen 1.20.1 acceptance scenarios still pass and the model has no live layout objects.

### WP2 — Deliver 1.21.1

- Implement the NeoForge/JEI 19 adapters and component-aware identity codec.
- Complete the vertical slice, then full feature parity and portable round trips.
- Validate minimal and real modpacks.

Exit: all required 1.21.1 matrix rows pass, mod → web → mod round trips work for a matching 1.21.1
dataset, and wrong-version/wrong-pack imports fail clearly.

### WP3 — Deliver the 1.12.2 compact viewer

- Backport the model as Java 8 POJOs.
- Build the JEI/HEI 4 query and semantic adapters from the existing exporter helpers.
- Ship Stage 1 UI, persistence, histories, summaries, and stable-ID portable sharing.

Exit: the JEI and HEI floor builds pass, the compact viewer is usable in the target large packs,
and a broken native layout cannot break planning semantics.

### WP4 — Close 1.12.2 parity gaps

- Validate transformed Details layouts by category and GUI scale.
- Add feature-detected recipe-GUI hover, discovery, and optional AE2 support.
- Record any remaining category-specific limitation in release notes.

Exit: every Stage 2 requirement either passes the compatibility matrix or is deliberately gated
with a user-visible fallback.

### WP5 — Package and document

- Finalize separate-viewer versus combined-JAR packaging.
- Update target READMEs, mod metadata, control descriptions, portable-format documentation, and the
  exporter changelog/release manifest as applicable.
- Publish no production artifact until the exact distributable JAR has passed its version-specific
  smoke matrix.

## 11. Definition of done

The port is complete for a target when:

- a player can reliably open the viewer from every required target source;
- typed ingredients, quantities, recipe selection, multi-root planning, summaries, persistence, and
  lifecycle restoration match the frozen behavior contract;
- native rendering failures are isolated to a card/category and never destroy the plan;
- local and portable identities distinguish target-specific subtypes and never use unstable object
  strings;
- portable histories round-trip against the matching published dataset and reject mismatched packs
  and Minecraft versions;
- queries and layout caches remain lazy and bounded in stress packs;
- all target build, unit, contract, GUI, lifecycle, and real-pack checks pass; and
- the existing exporter remains behaviorally unchanged unless an identity-contract change is
  intentionally versioned and tested end to end.

## 12. API references

- [NeoForge 1.21.1 key mappings](https://docs.neoforged.net/docs/1.21.1/misc/keymappings/)
- [NeoForge 1.21.1 screens](https://docs.neoforged.net/docs/1.21.1/gui/screens/)
- [NeoForge 1.21.1 events](https://docs.neoforged.net/docs/1.21.1/concepts/events/)
- [NeoForge 1.21.1 data components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)
- [JEI 1.21.1 `IRecipeManager`](https://github.com/mezz/JustEnoughItems/blob/1.21.1/CommonApi/src/main/java/mezz/jei/api/recipe/IRecipeManager.java)
- [JEI 1.21.1 `IRecipeLayoutDrawable`](https://github.com/mezz/JustEnoughItems/blob/1.21.1/CommonApi/src/main/java/mezz/jei/api/gui/IRecipeLayoutDrawable.java)
- [JEI 1.21.1 `IIngredientHelper`](https://github.com/mezz/JustEnoughItems/blob/1.21.1/CommonApi/src/main/java/mezz/jei/api/ingredients/IIngredientHelper.java)
- [Forge 1.12.2 registries](https://docs.minecraftforge.net/en/1.12.x/concepts/registries/)
- [Forge 1.12.2 recipes](https://docs.minecraftforge.net/en/1.12.x/utilities/recipes/)
- [Forge 1.12.2 OreDictionary](https://docs.minecraftforge.net/en/1.12.x/utilities/oredictionary/)
- [Forge 1.12.2 `GuiScreen` JavaDocs](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraft/client/gui/GuiScreen.html)
- [Forge 1.12.2 `GuiContainer` JavaDocs](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraft/client/gui/inventory/GuiContainer.html)
- [Forge 1.12.2 `ClientRegistry` JavaDocs](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraftforge/fml/client/registry/ClientRegistry.html)
