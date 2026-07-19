# Recipe Tree viewer

Expo (React Native Web) app for browsing a `jei-exports` dataset produced by
[recipe-export-mod](../recipe-export-mod): searchable item grid, JEI-style recipe/usage
views, a mob gallery, and a pan/zoom crafting flowchart built from the exported recipe images.

The web publication is content-addressed. Item/block source textures retain Minecraft's native
16×16 pixel-art grid, while the production 1.12 exporter rasterizes the real item renderer into
48×48 canvases for a 1:1 match with the dominant 48 CSS-pixel UI slot. Composite JEI layouts are
rasterized at 2× physical resolution but retain logical layout dimensions in recipe metadata.
They live in a separate immutable sidecar so the 359k-preview MeatballCraft corpus does not
inflate the Sites deployment archive or create one storage object per recipe.

## Run (web)

```bash
npm install
npm run sample-data        # optional: fake demo dataset, no Minecraft needed
npm run web                # http://localhost:8081
```

The production dataset in `public/exports/` is a validated, packed publication rather than a
raw exporter directory. Use the transactional importer for a new core publication; do not copy
raw files directly over the live dataset:

```bash
npm run import-data -- \
  --source /path/to/raw/jei-exports \
  --omit-recipe-images
```

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

## External JEI preview sidecar

Build the sidecar from the raw exporter output and the exact local hosted publication:

```bash
npm run build:recipe-previews -- \
  --source /path/to/raw/jei-exports \
  --dataset-manifest public/exports/manifest.json \
  --output /new/path/recipe-preview-sidecar
```

The builder verifies the hosted publication hash twice, proves raw/hosted recipe identity,
binds ordered decoded RGBA pixels to the publication, deduplicates equal visuals, and emits
approximately 1 MiB payload packs plus exact-range authorization indexes. It fails rather than
falling back when provenance, image decoding, or output isolation cannot be proven.

Seed the generated directory through the temporary authenticated ingestion API. The operator
CLI never receives R2 credentials: it sends an authorization token only to the app Worker,
which validates the staged manifest and writes through its native `PREVIEW_ASSETS` binding.
Set the hosted `PREVIEW_UPLOAD_ENABLED=true` feature gate, the new sidecar's
`PREVIEW_UPLOAD_ASSET_SET_ID`, and a fresh `PREVIEW_UPLOAD_TOKEN` before deploying an operator
upload session. Keep the serving `PREVIEW_ASSET_SET_ID` pinned to the current committed
sidecar while the new immutable object set is staged and committed, then switch it only in the
final publication deployment. The ingestion route fails explicitly when the upload identity is
missing or malformed; it never falls back to the serving identity.
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
| `scripts/upload-recipe-preview-sidecar.mjs` | resumable authenticated manifest-last R2 seed client |
| `scripts/verify-recipe-preview-sidecar-remote.mjs` | exhaustive local and two-phase remote verifier |
| `worker/index.ts` | publication gate, exact-range authorization, and edge caching |
| `scripts/make-sample-data.mjs` | zero-dependency fake dataset generator (hand-rolled PNG encoder) |

Graph interactions: tap a node to expand/collapse how the item is obtained — recipes,
mining a block, or mob drops (a picker appears when there are several options; ⇄ re-opens
it). Expanded items render as one compact node with the item name + required amount in the
header. ⓘ opens the item detail, drag pans, scroll/pinch zooms, `fit` re-centers.
