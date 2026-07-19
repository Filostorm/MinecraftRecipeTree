# Recipe Tree viewer

Expo (React Native Web) app for browsing a `jei-exports` dataset produced by
[recipe-export-mod](../recipe-export-mod): searchable item grid, JEI-style recipe/usage
views, a mob gallery, and a pan/zoom crafting flowchart built from the exported recipe images.

The web publication is content-addressed. Item/block source textures retain Minecraft's native
16×16 pixel-art grid. Multiblock Madness and Multiblock Madness 2 preserve those renderer
outputs as 16×16 canvases; the already-audited MeatballCraft corpus remains pinned to its
historical 48×48 canvases. Composite HEI/REI layouts are rasterized at 2× physical resolution
but retain logical layout dimensions in recipe metadata.
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
  --source /path/to/raw/jei-exports \
  --destination /path/to/packed/multiblock-madness \
  --profile multiblock-madness-1.12.2 \
  --omit-recipe-images
```

`--profile` is mandatory and has no implicit default. Supported production profiles are:

| Profile | Minecraft | Item canvas | Recipe layout | Corpus contract |
|---|---:|---:|---:|---|
| `meatballcraft-1.12.2` | 1.12.2 | 48×48 (`iconScale=3`) | 2× | immutable audited counts/provenance |
| `multiblock-madness-1.12.2` | 1.12.2 | 16×16 (`iconScale=1`) | 2× | dynamically counted, zero missing previews |
| `multiblock-madness-2-1.18.2` | 1.18.2 | 16×16 (`iconScale=1`) | 2× | dynamically counted, zero missing previews |

Dynamic counting means the pack's item, recipe, and category totals are accepted from that
specific completed export rather than hard-coded in source. It does not weaken completeness:
manifest/document counts, failure diagnostics, semantic direction, raw/hosted identity, and
one preview per recipe still fail closed.

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
  --source /path/to/raw/jei-exports \
  --dataset-manifest /path/to/packed/multiblock-madness/manifest.json \
  --output /new/path/recipe-preview-sidecar \
  --profile multiblock-madness-1.12.2
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

## Publish an immutable dataset and activate its channel

Build and upload the core publication after the packed export passes validation. The detailed
R2 object, MRPI authorization-index, and manifest-last protocol is documented in
[`docs/core-dataset-r2-publication.md`](docs/core-dataset-r2-publication.md).

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
| `scripts/dataset-channel-admin.mjs` | strict activation/deactivation client with public-catalog verification |
| `worker/index.ts` | immutable delivery, channel registry, exact-range authorization, and edge caching |
| `scripts/make-sample-data.mjs` | zero-dependency fake dataset generator (hand-rolled PNG encoder) |

Graph interactions: tap a node to expand/collapse how the item is obtained — recipes,
mining a block, or mob drops (a picker appears when there are several options; ⇄ re-opens
it). Expanded items render as one compact node with the item name + required amount in the
header. ⓘ opens the item detail, drag pans, scroll/pinch zooms, `fit` re-centers.
