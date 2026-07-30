# Recipe Tree JEI/HEI exporter for Minecraft 1.12.2

This is an isolated Forge 1.12.2 client mod for standard Just Enough Items (JEI) and the
Had Enough Items (HEI) fork. It exports the
`items + recipes` subset of Recipe Tree viewer format v1. It deliberately does not modify or
share source with the 1.20 exporter.

The generic runtime contract is Minecraft 1.12.2 with JEI/HEI
`[4.12.0.214, 5.0.0)`. The upper bound prevents Forge from loading this legacy integration
against an incompatible future major API. The default build compiles against standard JEI
4.12.0.214, the first 1.12.2 release with JEI's typed ingredient API and therefore the oldest
API supported by this exporter. JEI 4.11 and earlier use a structurally different class-based
ingredient registry; supporting those versions would require a separate semantic adapter rather
than a safe version-range expansion.
Release validation also compiles and tests against the local HEI 4.25.0 compatibility-floor
artifact. The emitted mod classes target Java 8 bytecode.

The JAR remains registered as a LaunchWrapper loading plugin so the existing audited
MeatballCraft/Multiblock Madness repairs can be enabled before mod classes load. In the generic
configuration, all repair properties are absent, the loading plugin registers zero transformers,
logs that pack bytecode is untouched, and the normal exporter has no pack-specific startup
mutation. Splitting this into generic and coremod artifacts would reduce the generic launch
surface, but it would also defeat the one-JAR drop-in workflow and make pack repair selection an
artifact-management problem. The current single-artifact design keeps the optional surface
fail-closed: every transformer requires an exact `true`, and all audited contracts retain their
version/class/opcode checks.

The build uses RetroFuturaGradle 2.0.2. RFG itself currently requires a Java 25+ Gradle runtime;
on this machine, build with the installed JDK 26:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home ./gradlew build
```

For the secondary HEI floor, provide its runtime JAR explicitly. When switching `jeiApiJar`
values or performing a from-scratch release, invoke the RFG cleanup, classpath generation, and
verification as separate Gradle processes:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home ./gradlew clean \
  -PjeiApiJar=/path/to/HadEnoughItems_1.12.2-4.25.0.jar
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home ./gradlew compileJava \
  -PjeiApiJar=/path/to/HadEnoughItems_1.12.2-4.25.0.jar
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home ./gradlew test build \
  -PjeiApiJar=/path/to/HadEnoughItems_1.12.2-4.25.0.jar
```

RetroFuturaGradle can otherwise retain a stale incremental classpath snapshot during a combined
`clean test build` invocation after the dependency changes. That failure must remain visible; do
not reuse an older JAR or silently retry against a different viewer dependency.

If `jeiApiJar` is omitted, the build logs that it is using the published standard JEI
4.12.0.214 floor. A release is complete only after both that default build and the explicit
HEI 4.25.0 build pass.

Gradle's Foojay resolver provisions the Java 8 compiler toolchain on first build. The mod jar is
written under `build/libs/`; copy that jar into a Forge 1.12.2 pack's `mods/` directory. A
compatible JEI or HEI 4.x JAR must also be present. Do not put the development `sources` jar in
the instance.

### Exporter build provenance

The `reobfJar` task seals the final distributable with
`META-INF/mrt-exporter-build.json`. Its `payloadSha256` is SHA-256 over a canonical stream of every
non-directory JAR entry except that identity resource itself: the stream starts with UTF-8
`mrt-exporter-jar-payload-v1` plus a NUL byte, then includes each entry in unsigned UTF-8 path-byte
order as `UInt32BE(path length) + path + UInt64BE(content length) + uncompressed content`.
Therefore timestamps, compression, and ZIP entry order do not perturb the identity, while the
reobfuscated classes, resources, and exact `META-INF/MANIFEST.MF` bytes remain covered.

The exporter resolves its own Forge `ModContainer` source JAR, recomputes that digest, and rejects
a missing, noncanonical, mismatched, symlinked, or directory-backed artifact. A successful export
writes the embedded canonical bytes unchanged to root-level `exporter-build.json`:

```json
{"format":"mrt-exporter-build-v1","exporterId":"forge-hei-1.12.2","minecraftVersion":"1.12.2","algorithm":"sha256","payloadSha256":"<64 lowercase hex>"}
```

This payload identity avoids the mathematical circularity of embedding a complete JAR's hash
inside that same JAR. Release tooling should still SHA-256 the complete distributable and bind
that artifact hash to the independently recomputed payload identity. Gradle's exploded development
classpath deliberately cannot produce a publishable export; there is no unverified provenance
fallback.

## One-shot request file

Copy `example-request.json` to the instance root as `jeiexport-request.json`. Prefer writing a
temporary file and renaming it into place so the client never consumes partially written JSON.
The exporter atomically renames a consumed request to a unique `*.running-<time>.json` marker,
then to `.done` or `.failed`. A failed preflight also receives an `.error.txt` sidecar.

Important MeatballCraft gating: RecipeStages and ItemStages can hide recipes until player/stage
synchronization. Use `requireWorld: true`; no title-screen fallback occurs in that mode.
`createWorld: true` explicitly calls `Minecraft.launchIntegratedServer` to create/load the named
flat creative save, then waits `waitAfterWorldTicks` after both player and world exist. The save is
not silently deleted. The automation wrapper may temporarily change the pack's RecipeStages and
ItemStages visibility settings before launch, but it must restore them afterward; this exporter
does not mutate modpack configuration.

Set `requireWorld: false` to export directly from a title screen when the pack is known not to gate
JEI/HEI data. The exporter waits briefly after the viewer runtime appears in either mode.

Supported request fields:

- `packName` and optional `packVersion`: the explicit user-facing pack identity. New exports write
  this to `manifest.json` as `pack.name`, optional `pack.version`, and `pack.identitySource`.
  Values are bounded and reject control, bidirectional-formatting, and zero-width characters so a displayed
  identity cannot contain log injection or visually reordered text.
- `output`: absolute path or path relative to the instance root; default `jei-exports`.
  Text is bounded to 4,096 Unicode code points and rejects unsafe controls.
  The transactional publisher rejects the game directory, its ancestors, operational trees such
  as `mods`, `config`, and `saves`, symbolic-link destinations, and existing non-directories.
- `iconScale` (`1..8`) and `recipeScale` (`1..4`).
- `maxMillisPerTick` (`1..250`): cooperative client-thread work budget.
- `pngThreads` (`1..4`) and `pngQueueCapacity` (`8..4096`).
- `requireWorld`, `createWorld`, `worldFolder`, `worldName`.
  World names reject unsafe Unicode controls; `worldFolder` is additionally bounded to 64 code
  points and must be a portable Windows/macOS/Linux file name, including rejection of reserved
  Windows device names such as `CON` and `COM1`.
- `waitAfterWorldTicks` and `worldTimeoutTicks`.
- `exitOnComplete`: shuts down Minecraft after either success or an explicit failure marker.
- `qualitySample.recipes`: optional, non-empty mini-test target list. Every target contains a
  `category` and exactly one selector: a nonnegative viewer `sourceIndex`, or an exact canonical
  `recipeId` such as `crafttweaker:ct_shaped-557966710`. Prefer `recipeId` when a wrapper exposes
  one because viewer source indexes can drift when the pack changes. A recipe ID must resolve exactly
  once inside its requested category; missing, ambiguous, duplicate, or aliased targets abort the
  sample without publishing it. Targets and first-seen categories retain request order.

The same exporter can be queued manually with `/jeiexport [output-directory]`. JVM one-shot mode
uses `-Djeiexport.auto=true` plus corresponding properties such as
`-Djeiexport.packName="My Modpack"`, `-Djeiexport.packVersion=1.0.0`,
`-Djeiexport.output=jei-exports`, `-Djeiexport.requireWorld=true`,
`-Djeiexport.createWorld=true`, and `-Djeiexport.exitOnComplete=true`.

Request objects reject unknown top-level fields instead of ignoring misspellings. Request files
must be non-symlink regular files, valid UTF-8, and no larger than 256 KiB.
Numeric fields must be JSON integers rather than strings or fractional values. JVM boolean
properties accept only exact lowercase `true` or `false`; a typo is logged or fails startup
instead of being silently interpreted as `false`.

Identity resolution is deterministic: explicit request/JVM values win; otherwise the exporter checks
bounded, non-symlink launcher metadata for CurseForge (`minecraftinstance.json`), Prism Launcher
(`instance.cfg` beside the `.minecraft` directory), and Modrinth (`modrinth.index.json`). A
present but malformed metadata file aborts identity resolution. When no supported metadata exists,
the exporter uses the instance-directory name, writes `identitySource: "game-directory"`, and
emits an explicit warning that no pack version was found. This last path keeps the manual
`/jeiexport` command usable without silently claiming that launcher-derived metadata was found.

For an export launch that creates an integrated world, the optional
`-Djeiexport.optimizeWorldStartup=true` coremod policy limits initial static-world construction to
dimension 0 plus registered dimension IDs whose `DimensionType.shouldLoadSpawn()` is true. The
property is deliberately a launch property rather than a request field because LaunchWrapper must
install the transformer before the request is parsed. When absent or exactly `false`, Minecraft
and Forge bytecode is untouched. Any other property value, bytecode-owner mismatch, target-method
count other than one, or exact call-site count other than one aborts startup; there is no silent
all-dimension fallback. Both `MinecraftServer.loadAllWorlds` and the client export path in
`IntegratedServer.loadAllWorlds` are guarded independently. The manifest records the policy and,
after it runs, original/selected/skipped dimension counts under
`settings.worldStartupOptimization`.

Multiblock Madness contains the exact
`ThaumicAdditionsAgriCraftCompat-0.0.3.jar` artifact whose TAACC subtype interpreter dereferences
`ItemStack.getTagCompound()` before reading the `Aspect` string. HEI catches that exception, but
its catch path omits the untagged ingredient and aborts affected recipe-wrapper registration. The
launcher hashes the candidate jar and enables
`-Djeiexport.normalizeTaaccMissingAspect=true` only for audited SHA-256
`19d282fda54fd623cae6882cf1e535c18ccc6ecfe19878de8a6d251e0e1a0599`; a differently hashed
candidate aborts instead of receiving an assumed-compatible patch. Packs without TAACC log that
the repair is disabled.

The corresponding transformer accepts only the audited Java 8 class owner, superclass,
interface/annotation/field/method table, and exact 11-instruction `AspectTagSplitter` body. It
replaces only the stack-neutral `NBTTagCompound.getString` call with a null-aware static delegate.
A present compound still executes native `getString("Aspect")` with its return value unchanged;
only a null compound becomes TAACC's intended empty subtype. Every such normalization is logged
and counted. Before publication, the runtime gate requires exactly Minecraft 1.12.2, Forge
14.23.5.2860, TAACC 0.0.3, HEI 4.25.0, and exactly one successful transform, then reports the exact
normalization count while tests and emitted bytecode assert the direct native delegation. No NBT,
ingredient, recipe, image, or export record is
fabricated. The performance cost is one small static call per TAACC subtype evaluation; in return,
the exception allocation and stack-trace logging paths are removed.

The same pack contains `TinkersComplement-1.12.2-0.4.3.jar`. Its JEI plugin blacklists three
chocolate fluids by constructing a `FluidStack`, adding that stack, creating a filled bucket, and
adding the bucket. One late alternate fluid is non-null and passes Forge's constructor check, but
its `FluidStack` delegate resolves to a fluid for which
`FluidRegistry.getFluidName(resolvedFluid)` is null. `FluidStack.writeToNBT` passes that null value
to `NBTTagCompound.setString`, so HEI catches an exception only after the first blacklist mutation
and drops the rest of the Tinkers' Complement plugin registration.

`-Djeiexport.skipTinkersComplementUnboundBlacklistFluid=true` enables an exact repair only after a
launcher has verified artifact SHA-256
`09f3ff16c8204d6ed065c9ed1a717f56c824e45c12e6eda451aae3523262656c`. The production transformer
also requires target-class SHA-256
`124861ec684552a78c9b4e8b398326005e38bf151e385b4d4985c8b9fc55f54b`, Java 8 class shape, exact
class/interface/annotation/field/method tables, and the original 14-opcode `blacklistFluid` body.
It adds a four-opcode prefix that performs a non-mutating `FluidStack` delegate preflight. A
non-null inverse registry name branches to every original instruction; a null supplied fluid,
resolved fluid, or inverse registry name is warning-logged and counted, then returns before either
HEI blacklist mutation. Constructor/delegate failures other than that exact null condition still
propagate with native behavior. Readiness and publication gates require Minecraft 1.12.2, Forge
14.23.5.2860, Tinkers' Complement 1.12.2-0.4.3, HEI 4.25.0, and exactly one successful transform.
No fluid, bucket, ingredient, recipe, image, or export record is fabricated. The preflight cost is
one transient `FluidStack` for each of the plugin's three startup-only blacklist calls; there is no
per-item or per-recipe export overhead.

## Stress-test and correctness behavior

MeatballCraft can produce a very large icon corpus. PNG encoding uses a bounded executor. When
the queue fills, the client producer blocks and logs an explicit backpressure warning instead of
allowing unbounded heap growth. Lower scales reduce PNG CPU time and disk usage, at the cost of
less sharp viewer images. Raising the queue can increase throughput burst tolerance but consumes
more heap; raising PNG threads increases CPU contention with Minecraft. Saturation logging is
aggregated after the first event, reports a cumulative checkpoint every 1,000 events, and always
reports final event and warning totals when the encoder finishes.

Recipe semantics come from a full `IIngredients` recording pass, including legacy class-based
setters and HEI subtype expansion. They are independent from GUI rendering, so a broken layout can
omit `img` without losing its recorded slots or reverse-index edges; `err` is reserved for semantic
collection failures. Reverse refs
are deduplicated per recipe/direction and stored as packed primitive longs until `index.json` is
streamed. Each category's `recipes.json` is streamed one recipe at a time; no category-wide
`JsonArray` is retained.

Some third-party 1.12 categories throw while HEI creates their GUI layout when the category receives
an empty input/output list or an empty ingredient-alternative list. This occurs in Advanced
Rocketry's Chemical Reactor category and in terminal BuildCraft heatable/coolable-fluid entries.
The exporter records the original wrapper's nonempty semantic slots first, logs the image failure,
and publishes that recipe without `img`. It deliberately does not reflectively mutate the wrapper
or byte-patch a category to invent a layout: those approaches would be mod-version-specific and
could make the image disagree with the exported recipe graph.

Ingredient display names and category titles are normalized to plain text only after their
canonical IDs are fixed. Minecraft's standard formatting remover handles vanilla codes, and a
second cosmetic-only pass removes residual section-sign control pairs such as BuildCraft 8's
nonstandard `§z` prefix. Resource IDs, HEI unique IDs, category UIDs, and reverse-index keys are
never normalized.

Images render on the client GL thread into a dedicated 1.12 `Framebuffer`, followed by direct GL
readback to `BufferedImage`. The exporter logs `GL_MAX_TEXTURE_SIZE`; a layout that is too large at
scale 1 fails explicitly. PNG worker errors are fatal to publication, because publishing JSON that
references a missing image would be an inconsistent export.

The 1.12 launcher transactionally changes exactly one `enabled=true` property in Forge's
`config/splash.properties` to `enabled=false` for every export profile, then restores the original
file byte-for-byte after the child JVM exits or the launcher aborts. Forge's legacy loading screen
moves the Display context to a splash thread and leaves a shared drawable on the client thread;
that ownership split is unsafe on modern macOS/Rosetta and made an attempted `Display.makeCurrent()`
rebind contend with the live splash thread. Forge already patches
`Minecraft.getGLMaximumTextureSize()` to delegate to `SplashProgress.getMaxTextureSize()`, whose
proxy-texture result is cached. The exporter preserves that exact delegation instead of replacing
it with a second live capability probe, then validates that the returned value is a power of two
from 2048 through Forge's 16384 first-probe ceiling. An invalid value fails explicitly; the
validator performs no OpenGL query, context rebind, retry, or fabricated capability fallback.

The explicit export graphics guard disables only Multiblocked 0.8.0's eager built-in fragment-
shader bootstrap. On Apple OpenGL 2.1, both Multiblocked's direct GL20 path and Minecraft's selected
ARB/core `OpenGlHelper` path can return shader object `0` during mod pre-initialization. That shader
subsystem powers optional blueprint-editor GUI effects and particles; it is independent of
Minecraft's `RenderItem`/baked-model path and Multiblocked's HEI recipe categories. The canonical
Multiblock Madness recipe-map configuration also uses raster progress textures rather than shader
textures. Therefore native 16x16 item/block rendering and HEI layout capture remain enabled. The
transform removes exactly the one `ClientProxy.preInit -> Shaders.init()` call and logs a warning;
all remaining client setup is preserved. If any deferred export path actually requests a
Multiblocked shader, the validated shader bridge still rejects object `0` explicitly instead of
silently drawing an invalid layout.

All output, including `exporter-build.json`, is written to a unique sibling staging directory.
`jei-exports` is replaced only after
all JSON and PNG work succeeds; publication uses an atomic same-filesystem move when available and
logs any non-atomic fallback. Per-ingredient and per-recipe problems are logged. `failures.json`
retains a bounded sample plus an omitted-count marker; `manifest.counts.failures` records its exact
serialized length, while `manifest.diagnostics` records total and omitted failure events.
`mobs.json` (`{"mobs":[]}`) and `blockdrops.json` (`{"blocks":{}}`) are emitted
explicitly because this 1.12 module is intentionally scoped to HEI items and recipes.
