# Recipe Tree viewer

For the complete contributor/operator workflow—from installing an exporter through preparing,
uploading, updating, and sharing a pack—see
[`docs/publish-your-modpack.md`](docs/publish-your-modpack.md). The guided two-phase command is:

```bash
npm run publish:modpack -- --help
```

The deployed [/publish](https://minecraftrecipetree.craftsmannsoftware.com/publish) guide exposes
version-matched exporter metadata and generated SHA-256 checksums. Exporter JARs remain in their
version-specific local `build/libs` directories for external distribution and are never copied
into the site.

### Package one validated exporter release

Externally distributed exporter files are immutable release artifacts. Recipe Tree hosts only the
checksummed metadata catalog, not the JARs. If a rebuilt JAR changes bytes, increment
both its `version` and `filename` in `scripts/package-exporter-releases.mjs`; never replace an
already published filename. First run the acceptance action against that exact JAR's completed
full export. It performs the exhaustive profile validator once and writes a local receipt binding
the release ID/version/filename, JAR SHA-256 and byte length, the explicitly selected advertised
profile, and the validated export manifest's SHA-256 and byte length. It also binds the exact release
definition, complete local validator source set, and pinned Sharp/libvips lock entries, so tightening
a gate or changing the image-validation runtime invalidates older receipts:

```bash
npm run accept:exporter -- \
  --release forge-hei-1.12.2 \
  --profile meatballcraft-1.12.2 \
  --export-root "/absolute/path/to/completed-meatballcraft-full-export"

npm run accept:exporter -- \
  --release forge-hei-1.12.2 \
  --profile multiblock-madness-1.12.2 \
  --export-root "/absolute/path/to/completed-mm1-full-export"

# Build each accepted exporter in its version-specific Gradle project, then
# distribute the checksummed build/libs artifact outside Recipe Tree.
```

Receipts live under ignored `.release-acceptance/`, contain no local paths or credentials, and are
operator attestations rather than remote signatures. Each receipt is independently keyed by
`release--profile`; packaging requires one current receipt for every profile the release advertises.
It rejects an incomplete profile set plus every malformed, symlinked, cross-profile, policy-stale,
definition-stale, or stale-JAR receipt. A
diagnostic `qualitySample` export cannot produce a receipt. If the JAR or validation policy changes
by one byte, run the affected full acceptance export again. Legacy release-only receipt filenames
are discovered only for an explicit matching profile and emit a migration warning; they are never
used across profiles. Writing a new receipt always uses the profile-keyed filename.

The GTNH 1.7.10, 1.12.2, and 1.18.2 exporters additionally embed a canonical
`META-INF/mrt-exporter-build.json` record and emit those exact bytes as root
`exporter-build.json`. Acceptance recomputes the canonical digest of every non-directory JAR entry
except that self-identity record, verifies its explicit exporter ID and Minecraft version, then
requires the completed export to carry the byte-identical identity. It also computes exhaustive
pre/post digests of every export file and rejects symlinks, hard links, or any concurrent mutation.
This intentionally adds two bounded streaming reads of the export tree; it avoids an equally large
immutable snapshot and does not retain file bodies in memory. Profiles remain fail-closed while
their configured `acceptanceCorpora[profile]` is `null`; after each authoritative full run, copy
its exact item/recipe/category/mob/block-drop counts into that profile's release definition before
issuing its independent receipt. A corpus change for one profile does not rewrite another profile's
receipt identity, while shared artifact or validator changes still invalidate every affected receipt.

The former on-site JAR packaging CLI is disabled by distribution policy. Its transaction code
remains covered by tests for offline external-release assembly, but production site builds contain
only `public/exporters/manifest.json`; invoking the CLI fails explicitly instead of restoring a
same-origin binary or silently substituting another location.

Publication preparation also requires an explicit version-specific `--release`. It securely
revalidates that release's current receipt and JAR, then compares the receipt's exhaustive export
tree SHA-256 with the importer's exact staged raw snapshot before optimization. The normalized
receipt and its canonical SHA-256 are committed into `publication-plan.json` v4. Upload revalidates
that same immutable receipt/JAR identity and the packed `exporter-build.json` before reading the
catalog or credentials, so GTNH 1.7.10, 1.12.2, and 1.18.2 workspaces cannot be cross-packaged.
This adds one bounded streaming read of the staged export but no additional full-size copy and no
silent weaker fallback.

`npm run package:exporters` and targeted package commands now fail closed. Build, checksum, and
externally distribute each JAR from its version-specific project instead.

Expo (React Native Web) app for browsing a `jei-exports` dataset produced by
[recipe-export-mod](../recipe-export-mod): searchable item grid, JEI-style recipe/usage
views, a mob gallery, and a pan/zoom crafting flowchart built from the exported recipe images.

The web publication is content-addressed. Runtime-rendered item/block captures retain each
profile's audited pixel grid. Multiblock Madness and
Multiblock Madness 2 preserve those renderer outputs as 16×16 canvases; the already-audited
MeatballCraft corpus remains
pinned to its historical 48×48 canvases. Composite NEI/HEI/REI layouts are rasterized at 2×
physical resolution but retain logical layout dimensions in recipe metadata.
They live in a separate immutable sidecar so the 359k-preview MeatballCraft corpus does not
inflate the Sites deployment archive or create one storage object per recipe.

## Run (web)

```bash
npm install
npm run sample-data        # optional: fake demo dataset, no Minecraft needed
npm run web                # http://localhost:8081
```

Each production dataset begins as a validated local packed publication outside `public/`.
Vite's public-directory copier is disabled deliberately: complete exports are uploaded to R2
and never embedded in the application deployment. Use the transactional importer; do not copy
raw exporter files into the viewer:

```bash
npm run import-data -- \
  --source /path/to/raw/gtnh-2.8.4-export \
  --destination /path/to/packed/gtnh-2.8.4 \
  --profile gtnh-1.7.10 \
  --omit-recipe-images
```

`--profile` is mandatory and has no implicit default. Supported production profiles are:

| Profile | Minecraft | Item canvas | Recipe layout | Corpus contract |
|---|---:|---:|---:|---|
| `generic-jei-1.20.1` | 1.20.1 | 64×64 (`iconScale=4`) | 2× JEI | dynamically counted, exact diagnostics, zero missing previews |
| `meatballcraft-1.12.2` | 1.12.2 | 48×48 (`iconScale=3`) | 2× | immutable audited counts/provenance |
| `multiblock-madness-1.12.2` | 1.12.2 | 16×16 (`iconScale=1`) | 2× | dynamically counted, zero missing previews |
| `multiblock-madness-2-1.18.2` | 1.18.2 | 16×16 (`iconScale=1`) | 2× | dynamically counted, zero missing previews |
| `gtnh-1.7.10` | 1.7.10 | generated UI placeholders | structured records, no NEI rasters | GTNH 2.8.4, dynamically counted, zero failures, exact rights-policy accounting |

Dynamic counting means the pack's item, recipe, and category totals are accepted from that
specific completed export rather than hard-coded in source. It does not weaken completeness:
manifest/document counts, failure diagnostics, semantic direction, and raw/hosted identity still
fail closed. Visual profiles require one preview per recipe. GTNH instead proves every raw visual
before a post-validation rights transform removes all dataset-carried raster references and files.

The GTNH profile pins the latest stable pack release, 2.8.4, plus Forge `10.13.4.1614`
and NotEnoughItems `2.8.44-GTNH`. Its raw manifest must identify the pack as
`{name: "GT New Horizons", version: "2.8.4", identitySource: "explicit-request"}` and include
the exact 86-field NEI telemetry schema. It must also preserve an exact `attribution` object for the
normalized GTNH 2.8.4 recipe data: the pinned source and project URLs, `CC BY-NC-SA 4.0`
identifier, and Creative Commons license URL. This data attribution does not claim that every
third-party mod texture or other artwork is licensed by GTNH; those assets retain their respective
licenses. Public GTNH output is therefore locked to
`publicationPolicy: "gtnh-structured-data-only-v1"` and an exact zero-count
`web.visualAssets` ledger. The web client generates deterministic identifier-based placeholders;
it does not fetch a preview sidecar for this policy. Publication still requires the item list to
load, every registered handler admitted to category
export to produce exactly one loaded category, every enumerated recipe widget and item icon to
render, all handler-anomaly counters to remain zero, and `failures.json` to be empty. The exact
46-entry special-handler ledger admits 25 version-pinned adapters, excludes
20 non-recipe/debug handlers, and separately excludes one unbound template category. The final
GTNH corpus partitions all 330 registered handlers into
287 exported categories, `excludedNonRecipeHandlers: 20`, `excludedEmptyRecipeHandlers: 22`, and
`excludedUnboundTemplateRecipeHandlers: 1`. The empty-category ledger contains 20 exact
source-backed zero-row handlers plus the two registered but empty GregTech maps
`gt.recipe.entropic-processing` (category `gtnh:f3a25a72c53a1f1c494b208f5e99ffd0`) and
`gt.recipe.spaceResearch` (category `gtnh:bbc9b803242009c80d39be2aaad5786d`). Each exclusion is
revalidated against its authoritative source registry, prototype cache, freshly loaded cache, and
promoted corpus fingerprint before planning can omit it. Missing or unknown telemetry fields are
schema drift and abort the import; they are not treated as compatibility fallbacks. The raw/private export retains
its render evidence for validation, while the public packed export omits item/category icons, mob
sprites, recipe screenshots, and image packs.

The separately accounted unbound-template exclusion is
`com.rwtema.extrautils.nei.MicroBlocksHandler`. Its four authoritative `RecipeMicroBlocks` rows are
material-parameterized templates: the complete-category path has no concrete ForgeMultipart
material and therefore emits NBT-less carrier outputs, while item-specific queries bind the
required `mat` identity. Publishing those globally would create semantically invalid recipe nodes.
Any source, prototype, cache, or material-binding drift invalidates the exclusion instead of
silently hiding the handler.

The item-icon ledger requires exactly six
`adaptedModernMarkingsCrossingItemIcons`. These are the six pinned four-corner
ModernMarkings floor-crossing variants rendered through the audited owner-atlas face-on adapter;
any missing, duplicate, or broadened adaptation fails publication.

The same ledger requires exactly one `adaptedThaumcraftRunedStoneItemIcons`. This is the real
Gadomancy infusion output `Thaumcraft:blockEldritch` metadata 10, rendered from Thaumcraft's exact
`thaumcraft:es_5` owner atlas sprite because the owner's inventory renderer emits no geometry for
that valid metadata. Registry, runtime class, renderer topology, resource bytes, and telemetry are
all pinned. The inherited metadata-zero `ItemBlock` stack icon is separately required to remain
the distinct stitched `thaumcraft:obsidiantile` sprite; no transparent-image fallback or icon-path
conflation is accepted.

The ProjectBlue control-panel ledger requires exactly three
`adaptedProjectBlueControlPanelItemIcons` and exactly three
`adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations`. These are the three pinned
ProjectBlue control-panel material variants rendered through the exporter-side exact
owner-renderer lease; a missing variant, extra variant, or invocation-count drift fails
publication.

Thaumcraft NEI aspect-cost inputs use the exact
`thaumcraft-nei-aspect-cost-meta1-to-meta0-semantic-proxy-v1` knowledge policy. It admits only the
pinned metadata-1 TCNA cost proxy and normalizes its semantic item key to the owner-supported
metadata-0 aspect form while retaining the NBT aspect identity and recipe quantity. The exporter
must report positive `normalizedTcnaAspectCostInputOccurrences` and
`normalizedTcnaAspectCostDistinctKeys` counters across exactly four
`normalizedTcnaAspectCostHandlerCategories`. Missing telemetry, a zero corpus, a different handler
cardinality, or a broader normalization is rejected rather than treated as a compatibility fallback.

The two synthetic Forestry Scanner names use independent, source-bound capability contracts.
Scanned Sapling pins `knowledgePolicy.forestryScannedSaplingDisplayName` and
`knowledgePolicy.forestryScannedSaplingSourceBinding`; Scanned Pollen pins the corresponding
`forestryScannedPollenDisplayName` and `forestryScannedPollenSourceBinding` fields. Each preflight
authorizes exactly one recipe output occurrence, and each display-name resolver consumes exactly
one matching capability. Publication consequently requires all four adapter/occurrence counters to
equal one. Matching item NBT outside its authorized recipe locus is rejected, so these contracts
cannot silently broaden into global display-name fallbacks.

GregTech outputless rows are admitted only when
`knowledgePolicy.gregTechOutputlessRecipeSemantics` equals
`gregtech-outputless-semantic-preflight-v2`. The exporter pins the measured GTNH 2.8.4
snapshot: 289 fuel-sink recipes across 14 categories, 49 Large Boiler fuel-sink recipes in one
category, 104 Radio Hatch information recipes, 27 Quantum Component information recipes, and two
Space Project information recipes. These total exactly 471 semantic recipes across 18 categories;
one additional Large Boiler presentation-only row is excluded. The preflight scanned 148 GregTech
categories and 162,842 recipes and recorded 472 classified rows. Its order-independent
sorted-multiset fingerprint is
`7950c0741cb841a857428e327f407d0c8303954b0d6aa7a36a9189e30ea350f9`: canonical semantic-row
entries are sorted before hashing while duplicate entries retain their multiplicity, so handler
traversal order cannot perturb the identity. Every persisted counter is an exact publication
constraint, and `gregTechOutputlessSemanticRecipes` must also equal the sum of the five family
counters. Missing or inconsistent telemetry blocks publication rather than weakening generic
output validation.

Five GregTech recycling rows retain stale, unregistered vanilla `ItemDoor` object identities after
MalisisDoors replaces the registered wooden and iron door items. The exact
`knowledgePolicy.gregTechStaleDoorRecyclingExclusion` contract is
`gregtech-unregistered-itemdoor-recycling-exclusion-v1`, and
`excludedGregTechUnregisteredDoorRecyclingRows` must be exactly five: two Macerator rows, two Arc
Furnace rows, and one Fluid Extractor row. After proving the replacement relationship and
non-matchability against the live GregTech runtime, the exporter excludes each complete recipe
row. Dropping only the invalid input would manufacture a free-output recipe, while aliasing it to
the replacement door would invent an unsupported recipe edge. Any missing row, additional row, or
policy drift blocks publication.

The catalog exclusion ledger includes exactly one unconfigured MalisisDoors custom-door carrier
(`malisisdoors:item.custom_door`, metadata `0`, absent NBT). Parameterized custom-door stacks remain
exportable; a missing, duplicate, or broadened exclusion is rejected through the exact NEI telemetry
contract. The same contract requires both post-discovery recipe and quest reference counters to
remain zero.

AE2 in-world crafting's complete wildcard-query closure and BetterQuesting's item-reference index
are retained as informational categories, not executable recipe categories. BetterQuesting choice
rewards remain one logical slot with their exact alternatives, but quest task `AND`/`OR` logic,
optional tasks, possession-versus-consumption rules, and the player's selected reward cannot be
expressed by the recipe format. The app therefore exposes both categories under an **Info** tab
while excluding them from crafting graphs and material totals.

IC2 Crop Plugin data comes from a deterministic repair of the pinned plugin's nondeterministic
identity-hash cache. The adapter evaluates all 159 `ALL_CROPS` entries and 12,561 canonical parent
pairs, then independently preserves the first winner in each craft and parent-usage query bucket.
That produces 290,789 graph pages: 4,595 craft winners plus 286,194 usage-only winners. The raw
plugin cache remains structural diagnostic telemetry and is never authoritative. Clean
`BreedResult` display stacks define graph identity; breeding-points/chance lore exists only in the
NEI preview, so presentation-only NBT cannot fragment crop dependencies.

In omission mode, the importer losslessly converts and packs retained item, category, and mob
assets, but deliberately leaves declared recipe PNGs in their original encoding until the raw
validator has decoded every file and proven an exact one-reference/one-file inventory. The
transactional packer then records the original PNG byte total and removes `img`/`w`/`h` together
with those raw PNGs. This avoids a redundant lossless-WebP pass before the R2 sidecar performs
its own encoding; missing, duplicate, or unexpected PNGs abort with no fallback publication.

Before committing a full MeatballCraft stress export, run the two-recipe quality gate against
the audited scale-1 export. It is read-only: lossless WebP payloads are encoded and decoded in
memory, not written into either export. The gate requires a 16×16 logical item grid rendered to
48×48, 2× JEI previews with unchanged logical coordinates, genuine sub-cell rerender detail,
the exact Machine Frame recipes, and zero sample failures. Its JSON report includes per-asset
PNG/WebP bytes, p95 icon size, estimated cold-modal transfer, encode time, and observed RSS.

```bash
npm run validate:quality-sample -- \
  --baseline-root ../meatballcraft-export \
  --sample-root ../meatballcraft-quality-sample

npm run render:quality-sample -- \
  --baseline ../meatballcraft-export \
  --sample ../meatballcraft-quality-sample \
  --output ../meatballcraft-quality-comparison.png
```

For the optimized bulk-readback rerun, add
`--reference-sample-root ../meatballcraft-quality-sample` to the validator. It then compares
canonical decoded RGBA for every PNG in both sample trees, catching channel-order, row-order,
or stale-frame regressions before the full export is authorized.

### Repair the audited 1.12 compatibility previews

The three legacy layout integrations that crash only during the bulk pass are repaired from a
strict 27-target compatibility sample, never synthesized. The repair command requires the
completed full export and the fresh output to be siblings on APFS. It uses
`COPYFILE_CLONE_FORCE`, refuses an existing output, and has no full-copy fallback:

```bash
npm run repair:recipe-previews -- \
  --full ../meatballcraft-export-3x2 \
  --sample ../meatballcraft-compatibility-sample \
  --output ../meatballcraft-export-3x2-repaired
```

The immutable contract is 25 Advanced Rocketry chemical-reactor previews plus one BuildCraft
heatable and one coolable preview. It requires 359,215 full recipes, 359,188 pre-repair recipe
PNGs, the audited category counts and logical/physical dimensions, exactly 27 clean sample PNGs,
and zero sample failures. Each sample recipe must canonically deep-match exactly one image-less
full recipe after only `img`/`w`/`h` are stripped; sample array position is never an identity.
The transaction copies exactly those 27 PNG bytes, adds only their three image fields, removes
only the 27 exact historical layout-crash strings while retaining all unrelated diagnostics,
hashes both trees, enforces an exact pre/post path whitelist, decodes the copied outputs again,
and publishes by one sibling rename only after the final missing-image count reaches zero.
`manifest.json` records deterministic source, sample, normalized-tree, raw-PNG, and decoded-RGBA
SHA-256 provenance for later auditing.

## External recipe-preview sidecar

Build the sidecar from the raw exporter output and the exact local hosted publication:

```bash
npm run build:recipe-previews -- \
  --source /path/to/raw/gtnh-2.8.4-export \
  --dataset-manifest /path/to/packed/gtnh-2.8.4/manifest.json \
  --output /new/path/gtnh-2.8.4-recipe-preview-sidecar \
  --profile gtnh-1.7.10
```

The profile is mandatory here too. The builder verifies the hosted publication hash twice,
proves raw/hosted recipe identity,
binds ordered decoded RGBA pixels to the publication, deduplicates equal visuals, and emits
approximately 1 MiB payload packs plus exact-range authorization indexes. It fails rather than
falling back when provenance, image decoding, or output isolation cannot be proven.

Seed the generated directory through the temporary authenticated ingestion API. The operator
CLI never receives R2 credentials: it sends an authorization token only to the app Worker,
which validates the staged manifest and writes through its native `PREVIEW_ASSETS` binding.
Set the hosted `PREVIEW_UPLOAD_ENABLED=true` feature gate, the new sidecar's
`PREVIEW_UPLOAD_ASSET_SET_ID`, and a fresh `PREVIEW_UPLOAD_TOKEN` before deploying an operator
upload session. These variables authorize only the temporary ingestion route; public delivery
is selected later by the D1 dataset-channel descriptor. The ingestion route fails explicitly
when the upload identity is missing or malformed and never infers another asset-set identity.
Prefer the `PREVIEW_UPLOAD_TOKEN` environment variable; a `--token-file` must be a plain file
with mode `0600`. Never use an `EXPO_PUBLIC_*` variable for this secret.

```bash
PREVIEW_UPLOAD_TOKEN='<operator-secret>' npm run upload:recipe-previews -- \
  --local /path/to/recipe-preview-sidecar \
  --ingest-base-url https://<app-origin>/api/admin/preview-assets

npm run upload:recipe-previews -- \
  --local /path/to/recipe-preview-sidecar \
  --ingest-base-url https://<app-origin>/api/admin/preview-assets \
  --token-file /private/path/preview-ingest-token
```

The uploader stages the exact manifest, resumes objects whose remote SHA-256 and byte
length already match, uploads all other manifest-declared objects with `If-None-Match: *`, and
asks the Worker to commit only after every object passes `HEAD` verification. The Worker writes
the public `<assetSetId>/manifest.json` last, so an interrupted upload is not visible as a valid
publication. Rerunning is idempotent. After a successful seed, delete the hosted
`PREVIEW_UPLOAD_TOKEN`, `PREVIEW_UPLOAD_ASSET_SET_ID`, and `PREVIEW_UPLOAD_ENABLED`; a missing
feature gate disables the retained route with an explicit HTTP 503 before touching R2.
Re-enable all three only for an authorized operator upload. Future end-user publishing
must use authenticated upload sessions and per-user quotas rather than sharing this operator token.

The guided publisher keeps that boundary explicit: contributors run the local `prepare` phase,
while an operator runs `upload` with private credentials. The command derives the catalog label and
version from integrity-bound exporter metadata rather than asking the operator to retype them.

## Publish an immutable dataset and activate its channel

Build and upload the core publication after the packed export passes validation. The detailed
R2 object, MRPI authorization-index, and manifest-last protocol is documented in
[`docs/core-dataset-r2-publication.md`](docs/core-dataset-r2-publication.md).
Before deploying the temporary operator session, set `DATASET_ADMIN_ENABLED=true` together with a
fresh `CORE_DATASET_UPLOAD_TOKEN`. Remove both after channel activation; a missing gate returns a
logged HTTP 503 before authentication, R2 access, or D1 access.

```bash
npm run build:core-publication -- \
  --root /path/to/packed/multiblock-madness \
  --output /new/path/multiblock-madness-core

npm run upload:core-publication -- \
  --root /path/to/packed/multiblock-madness \
  --publication /new/path/multiblock-madness-core/publication.json \
  --ingest-base-url https://<app-origin>/api/admin/core-datasets \
  --token-file /private/path/dataset-operator-token

npm run verify:core-publication-remote -- \
  --root /path/to/packed/multiblock-madness \
  --publication /new/path/multiblock-madness-core/publication.json \
  --base-url https://<app-origin>/dataset/publications
```

The public verifier re-derives the complete local control bundle, checks every public JSON
object through `HEAD`, compares `manifest.json` byte-for-byte, and downloads the first, middle,
and last MRPI-authorized image in every pack. This bounds client-side verification bandwidth
while forcing the Worker to validate every pack's full authorization index.

### Cold-browser gate before channel activation

Large publications such as GTNH must pass the deterministic cold-browser gate before the D1
channel pointer is activated. Build the exact production artifact first, then run the gate against
the already validated local core bundle and preview sidecar. Chrome selection is deliberately
explicit; the command never guesses an executable or substitutes another browser.

```bash
npm run build

npm run benchmark:cold-dataset -- \
  --slug gt-new-horizons \
  --dist /absolute/path/to/viewer/dist \
  --export-root /absolute/path/to/packed/gtnh-2.8.4 \
  --publication /absolute/path/to/gtnh-core/publication.json \
  --preview-sidecar /absolute/path/to/gtnh-preview-sidecar \
  --chrome "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --output /new/path/gtnh-cold-browser-report.json
```

The harness exhaustively validates both local publications before and after measurement and
re-digests the complete production build after all browser runs. Its output parent is canonicalized
up front and must be outside the build, export, publication-bundle, and preview-sidecar trees. It starts
the existing production Worker on IPv4 loopback with a fresh Miniflare persistence directory and
an empty environment file plus isolated home, XDG, and temporary roots, then places a
manifest-authorized proxy in front of it. The proxy serves
only exact publication JSON paths and exact MRPI-authorized image ranges. It neither reads nor
mutates production D1/R2, receives no operator credentials, activates no channel, and writes the
report with exclusive-create semantics. A missing build, readiness marker, selected publication
identity, Chrome executable, heap/traffic signal, HTTP response, browser request, console/runtime
diagnostic, postflight validation, or cleanup confirmation is a hard failure with a logged error.

The default is three fresh Chrome processes and profiles. Each run records the React post-commit
`mrt:dataset-ready` mark for the exact selected publication, 25 ms CDP JavaScript-heap samples,
three post-GC settled-heap samples, and proxy/CDP traffic counters. Static metrics use exact bytes
from `items.json`/its shards and `index.json`/its shards. The gate derives the unique
bootstrap-document count from retained per-path proxy evidence, rejects any additional eager JSON,
and requires the catalog, core manifest, categories, mobs, block drops, and preview manifest.

| Metric | Eligible with current eager loader | Operator-review ceiling | Hard outcome above ceiling |
|---|---:|---:|---|
| item + index bootstrap bytes | 72 MiB | 80 MiB | lazy index required |
| index bootstrap bytes | 40 MiB | 48 MiB | lazy index required |
| unique bootstrap documents | 12 | 16 | lazy index required |
| worst settled JS heap | 300 MiB | 320 MiB | lazy index required |
| worst observed JS-heap peak | 400 MiB | 400 MiB | lazy index required |
| worst post-commit ready time | 8,000 ms | 8,000 ms | lazy index required |

`current-storage-eligible` exits `0`. `operator-review-required` and `lazy-index-required` write
their evidence report but exit `2`, so activation automation must stop. Validation or runtime
failure exits `1` and may not be interpreted as a performance result.

This is a **cold-browser**, not cold-machine, measurement: the local Worker and operating-system
file cache remain warm across runs. CDP `Runtime.getHeapUsage` measures page JavaScript heap, not
GPU/native memory, and a 25 ms sampler can miss a shorter transient. Loopback traffic excludes
Internet latency and CDN compression. Compare reports only on an otherwise idle machine with the
same OS, CPU architecture, Chrome build, Node version, and production artifact digest.

The current eager loader is simplest and minimizes request coordination, but it retains the entire
item and recipe index in JavaScript memory. If the gate requires a lazy index, the parallel design
is to shard by canonical item-key range and load bounded shards on search/graph demand. That lowers
cold transfer and retained heap at the cost of more request/cache state, delayed first access to an
uncached key range, and a more complex immutable routing contract. Do not raise these ceilings as a
storage workaround; choose either the evidenced eager path or the explicit lazy-index migration.

Once both immutable uploads are committed, activate a D1 channel pointer. The CLI accepts only
canonical identifiers and exact response shapes, then independently reads `/api/datasets` and
requires the requested descriptor to be visible. Stale or transient catalog reads are retried
exactly three times with bounded 200/500/1000 ms backoff; the authenticated mutation itself is
never repeated. If all four reads remain inconclusive after an exact mutation receipt, the CLI
exits with status `2` and explicitly reports `mutation committed; verification inconclusive`.
This is distinct from status `1`, where no committed mutation receipt was accepted. The token
file must be a plain mode-`0600` file; `CORE_DATASET_UPLOAD_TOKEN` is accepted when
`--token-file` is omitted.

```bash
npm run activate:dataset-channel -- \
  --slug multiblock-madness \
  --display-name "Multiblock Madness" \
  --minecraft-version 1.12.2 \
  --pack-version 3.2.3 \
  --publication-id <core-publication-sha256> \
  --preview-asset-set-id <preview-asset-set-sha256> \
  --default false \
  --admin-base-url https://<app-origin>/api/admin/dataset-channels \
  --token-file /private/path/dataset-operator-token
```

Rollback removes only a non-default channel pointer; it deliberately retains all immutable R2
objects for later reactivation. Deactivation requires the exact current core-publication and
preview-set identities and applies them in the D1 `DELETE` predicate. A stale operator command
therefore cannot remove a channel that another publication has repointed. The Worker refuses
deletion of the default channel, so promote a replacement first when rolling back the current
default.

```bash
npm run deactivate:dataset-channel -- \
  --slug multiblock-madness \
  --publication-id <currently-selected-core-publication-sha256> \
  --preview-asset-set-id <currently-selected-preview-asset-set-sha256> \
  --admin-base-url https://<app-origin>/api/admin/dataset-channels \
  --token-file /private/path/dataset-operator-token
```

## Run (iOS/Android via Expo)

The dataset is too big to bundle, so serve it over HTTP and point the app at it. Publications
that omit JEI layouts also require `EXPO_PUBLIC_PREVIEW_DATA_URL` to point at a compatible
preview gateway:

```bash
npx serve --cors -l 8787 /path/to/jei-exports
EXPO_PUBLIC_DATA_URL=http://<your-lan-ip>:8787 npx expo start
```

## Code map

| Path | What |
|---|---|
| `src/types.ts` | the jei-exports data contract |
| `src/data/DataContext.tsx` | loads manifest/items/categories/index/mobs, lazy per-category recipe cache |
| `src/data/previewAssets.ts` | validates and versions the external JEI preview sidecar |
| `src/components/ItemsScreen.tsx` | searchable, mod-filterable virtualized grid |
| `src/components/ItemDetailModal.tsx` | JEI-style Recipes/Usages panel |
| `src/components/MobsScreen.tsx` | mob gallery + stats modal |
| `src/graph/model.ts` | flowchart tree model (item nodes ↔ chosen recipe nodes) |
| `src/graph/layout.ts` | tidy top-down tree layout + elbow edges |
| `src/graph/GraphScreen.tsx` | pan/zoom canvas, expansion, multi-recipe picker |
| `scripts/build-recipe-preview-sidecar.mjs` | provenance-bound, deduplicated preview pack builder |
| `scripts/repair-missing-recipe-previews.mjs` | deterministic APFS repair overlay for the 27 audited legacy-layout previews |
| `scripts/build-core-dataset-publication.mjs` | immutable R2 control bundle and exact-range index builder |
| `scripts/upload-core-dataset-publication.mjs` | resumable authenticated core-publication uploader |
| `scripts/verify-core-dataset-publication-remote.mjs` | bounded public core/object and MRPI delivery verifier |
| `scripts/upload-recipe-preview-sidecar.mjs` | resumable authenticated manifest-last R2 seed client |
| `scripts/verify-recipe-preview-sidecar-remote.mjs` | exhaustive local and two-phase remote verifier |
| `scripts/benchmark-cold-dataset.mjs` | credential-free cold-Chrome activation gate for one exact local publication pair |
| `scripts/dataset-channel-admin.mjs` | strict activation/deactivation client with public-catalog verification |
| `worker/index.ts` | immutable delivery, channel registry, exact-range authorization, and edge caching |
| `scripts/make-sample-data.mjs` | zero-dependency fake dataset generator (hand-rolled PNG encoder) |

Graph interactions: tap a node to expand/collapse how the item is obtained — recipes,
mining a block, or mob drops (a picker appears when there are several options; ⇄ re-opens
it). Expanded items render as one compact node with the item name + required amount in the
header. ⓘ opens the item detail, drag pans, scroll/pinch zooms, `fit` re-centers.
