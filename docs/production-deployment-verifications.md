# Production deployment verifications

## 2026-08-30 — accounts, donations, EMC, and portable recipe imports

- Application commit: `27d818bfe3df07ed4b7d71b73e2af73911352573`
- Standalone Worker: `minecraft-recipe-tree-production`
- Standalone Worker version: `8286e0d0-7db6-4254-b1c1-dadae6a218de`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`
- D1 migrations: `0006_lumpy_ricochet.sql`, `0007_simple_madrox.sql`, and
  `0008_lucky_lord_hawal.sql`

Passed:

- All 64 focused graph, session, import, account, and donation tests passed. TypeScript validation
  and the repository diff check also passed.
- The three pending D1 migrations completed before the production Worker was deployed.
- Fresh canonical and direct-Worker requests returned `200`, the canonical interface hydrated in a
  fresh browser tab, and a hashed application asset loaded successfully.
- Importing a deliberately unavailable root recipe restored the usable portion of the tree, showed
  the partial-import notice, skipped the unavailable selection, and produced no orphan nodes or
  generic tree-open failure.
- `/api/datasets`, `/api/modpacks`, `/api/auth/session`, and `/api/donations` returned `200`.
  Dataset upload authorization, feedback, and failure-report routes rejected unauthenticated,
  unsupported, or invalid requests without persisting test data.
- The canonical router retained its application-origin, Worker-origin, and signal-edge headers.

Open data diagnostic:

- One existing JEI recipe did not include an exporter-generated layout preview. The structured
  recipe remained visible and functional, and the missing preview was reported in the browser
  console rather than silently concealed.

## 2026-08-27 — static far-zoom canvas rendering

- Application commit: `1ea570a31ee94b4766b957db67a83eeb3f0ca65f`
- Beta Worker version: `e6784b48-b257-478c-9bd2-4d6b5e1f560e`
- Production Worker: `minecraft-recipe-tree-production`
- Production Worker version: `d5a04c11-5529-4ffc-903d-f9bcf552d67a`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`

Passed:

- The exact seven-file beta-validated graph change was promoted onto the latest production branch
  without modifying the unrelated work in the primary checkout. The complete release suite passed
  722/722 tests, TypeScript validation passed, and both Worker bundles built cleanly.
- A generated 161-item local exporter fixture exercised the real dense-tree threshold through the
  released beta and production interfaces. Fitting its 161-node graph mounted exactly one
  viewport-sized canvas with `pointer-events: none` and zero interactive graph-node buttons.
- Fresh beta and canonical production browser tabs hydrated MeatballCraft with 189,137 searchable
  items. The beta catalog retained its required production data-origin header.
- The canonical homepage, Vinext bootstrap import, serialized RSC stylesheet, lazy graph bundle,
  local-pack service worker, `/publish`, `/api/datasets`, and `/api/modpacks` returned `200` with
  their expected content types. The canonical router retained the signal-edge compatibility header
  and identified the standalone production Worker as the active application origin.
- Upload administration and feedback inbox reads returned `401`; unsupported failure-report reads
  returned `405`; deliberately invalid same-origin feedback and failure reports returned `400`;
  and legacy modpack mutation remained forbidden with `403`.
- Remote D1 readback retained four dataset channels, two legacy modpacks, three feedback reports,
  and zero exporter-failure reports. The verification query reported zero rows written.

## 2026-08-26 — large-tree rendering and required Unique mode

- Application commit: `82bc5bfe8e7f4566dcf11386a27c820176299ae9`
- Standalone Worker: `minecraft-recipe-tree-production`
- Standalone Worker version: `56635931-edbc-4159-90bb-ddf6464cb795`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`

Passed:

- The three beta-validated large-tree commits were promoted onto the latest production branch
  without modifying the unrelated work in the primary checkout. The complete release suite passed
  720/720 tests, TypeScript validation passed, and the production Worker bundle built cleanly.
- A fresh canonical browser tab hydrated MeatballCraft with 189,137 searchable items. The publish
  page also hydrated and exposed its local exporter ZIP drop zone.
- The canonical homepage, Vinext bootstrap import, serialized RSC stylesheet, lazy graph bundle,
  local-pack service worker, `/api/datasets`, `/api/modpacks`, and `/publish` returned `200` with
  their expected content types. The graph bundle contains the required Unique-mode lock and its
  large-tree performance explanation.
- The canonical router retained the signal-edge compatibility header and identified the standalone
  production Worker as the active application origin.
- Upload administration and feedback inbox reads returned `401`; unsupported failure-report reads
  returned `405`; and deliberately invalid same-origin feedback and failure reports returned `400`.
  Remote D1 readback retained four dataset channels, two legacy modpacks, three feedback reports,
  and zero exporter-failure reports with zero rows written.

## 2026-08-26 — Worker request reduction release

- Application commit: `303fdcfa0e07fc7f7f1324e7f9b795ede1a5affa`
- Standalone Worker: `minecraft-recipe-tree-production`
- Standalone Worker version: `d7b0ea5c-1ff1-45c0-8906-359ec6735a43`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`

Passed:

- The exact beta release was promoted onto the latest production branch. The complete data suite
  passed 545/545 tests, TypeScript validation passed, and the production build promoted a validated
  prerendered root shell before deployment with the existing native D1 and R2 bindings.
- The direct production hostname returned the static homepage with HTTP `200`. Its Vinext bootstrap
  import, serialized RSC stylesheet, hashed application bundle, lazy graph bundle, and versioned
  local-pack service worker all returned `200` with their expected content types.
- A fresh direct-production browser tab hydrated the application and explicitly rendered the
  catalog HTTP `429` error. This confirms the static viewer shell runs without a Worker invocation
  and does not silently conceal the unavailable Worker-backed catalog.

Production verification failure:

- Cloudflare's account-wide Free-plan Worker limit remains exhausted. The canonical hostname still
  requires the app-router Worker and returned HTTP `429` for the homepage, hashed assets, and the
  local-pack service worker. Both the direct and canonical hostnames returned `429` for
  `/api/datasets`, `/api/modpacks`, upload authorization, feedback, and failure-report routing.
- The new coalesced whole-pack endpoints therefore could not be exercised against production data;
  their authorization, integrity, range, and service-worker behavior passed in the 545-test release
  suite. No synthetic production data or mutation request was created.
- The release is deployed, but the canonical application and every Worker-backed API remain
  unavailable until Cloudflare resets the account quota or the account is upgraded.

## 2026-08-26 — large-tree graph performance release

- Application commit: `e868cfdccf046c86427bfea1db6fc2ad19eac625`
- Standalone Worker: `minecraft-recipe-tree-production`
- Standalone Worker version: `beb494c3-4656-474b-8b92-ca3e85d594a1`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`

Passed:

- The exact beta source was promoted onto the latest production branch without modifying the
  unrelated local EMC-preview work in the primary checkout.
- The focused graph suite passed 24/24 tests, TypeScript validation passed, and the production
  bundle deployed successfully with the existing native D1 and R2 bindings.
- Before the provider limit was reached, a fresh canonical browser session hydrated the current
  MeatballCraft release and loaded the release's hashed application assets.

Production verification failure:

- Cloudflare began returning HTTP `429` with Error 1027 immediately after deployment, stating that
  the account owner had reached plan limits. The failure affects both the canonical hostname and
  the direct `minecraft-recipe-tree-production.gtjoe51.workers.dev` hostname.
- The already-hydrated browser session subsequently logged explicit reverse-index shard load
  failures, and a fresh `/publish` session rendered Cloudflare's temporary rate-limit page.
- Because the provider rejected every subsequent request, `/api/datasets`, `/api/modpacks`, the
  hashed bootstrap, stylesheet, and lazy graph bundle, upload authorization, feedback, and failure
  reporting could not complete their required post-deployment checks. No synthetic production
  data was created.

## 2026-08-26 — graph planning fixes and GTNH 2.8.4 refresh

- Application commit: `a194471f140d869947868f86653632f32ca66b43`
- Standalone Worker: `minecraft-recipe-tree-production`
- Standalone Worker version: `fccbfaa4-1e99-4b42-9822-37116643da7b`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`
- GTNH core publication: `645b42d21ecb44a6e844cdbd02a88266b6123039557cf7aa49321c06d35c0b0f`
- GTNH preview set: `75a9410ccc9c90813140ce8d22b6380a4f441e2a23f989182de92e31dc13487d`

Passed:

- The beta Worker hydrated first and exposed Auto expand, hid `thermaldynamics:cover`, displayed
  deterministic selected-output surplus in Byproducts remaining, loaded a hashed application
  asset, and retained the required production data-origin header.
- The production build and TypeScript validation passed. The complete data suite passed 541/541
  tests after the sandbox-blocked loopback suite was rerun with local-network permission; 32
  focused Auto expand, byproduct, catalog, EMC, and recipe-favorite tests also passed.
- A fresh canonical browser session hydrated MeatballCraft, showed Auto expand, hid the Thermal
  Dynamics cover carrier, and listed the overproduced TARDIS Structure Controller in Byproducts
  remaining. A fresh GTNH session hydrated version 2.8.4 without browser errors, and `/publish`
  rendered the local exporter ZIP drop zone.
- The GTNH channel moved from its prior publication with an exact compare-and-swap guard. The
  public catalog now exposes the new core and preview identities, while retaining four channels
  and exactly one default.
- The canonical homepage, Vinext bootstrap import, serialized RSC stylesheet, lazy graph bundle,
  `/api/datasets`, `/api/modpacks`, GTNH core manifest, and GTNH preview manifest returned `200`.
  The GTNH manifests report 143,882 items, 568,820 recipes, 287 categories, zero failures, and
  1,063 preview packs.
- Unauthenticated dataset upload and feedback-inbox requests returned `401`; invalid same-origin
  feedback and failure-report submissions returned `400`; unsupported failure-report reads
  returned `405`; and legacy modpack mutation remained forbidden with `403`. No verification row,
  feedback report, failure report, GitHub issue, or upload object was created.

Verification notes:

- The first immutable-manifest probes omitted the routes' required content-addressed query and
  correctly returned `400`. Repeating both probes with the canonical query returned `200` with
  immutable caching and exact GTNH identities.
- Opening the saved MeatballCraft TARDIS graph logged its existing explicit diagnostic that one
  legacy JEI layout preview is unavailable; the structured recipe remained visible and the graph
  stayed functional. Fresh GTNH and publish sessions logged no browser errors.

## 2026-08-05 — local-only imports and large-tree stabilization

- Application commit: `fa766d6dc7e2eab40e6645d95578b3267da07cde`
- Standalone Worker: `minecraft-recipe-tree-production`
- Standalone Worker version: `a85ed172-824d-4a2f-be47-19eedf1e7a11`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`

Passed:

- The complete release suite passed 478/478 tests; the focused local-pack, exporter-failure, and
  large-tree viewport suite passed 16/16 tests; and the production Worker bundle built cleanly.
- A fresh browser tab hydrated MeatballCraft, opened Stone's recipe picker, added a recipe to the
  graph, exposed the fit control, and recorded no browser errors or graph recovery state.
- The homepage, current hashed JavaScript bundle, `/api/datasets`, `/api/modpacks`, the default
  core manifest, and its paired preview manifest all returned `200` through the canonical router.
- The catalog retained four channels. Remote D1 readback retained two legacy modpacks, three
  feedback reports, and zero exporter-failure reports without writing any rows.
- The default MeatballCraft manifest remained available with 196,161 items, 359,215 recipes, and
  its 130 logged recipe failures; its paired preview manifest retained 443 packs and 698 category
  documents.
- Feedback inbox reads, exporter-failure method handling, and dataset upload administration all
  remained fail-closed. No synthetic feedback, upload, failure report, or GitHub issue was created.
- Canonical responses retained the signal-edge compatibility header and identified the standalone
  Worker as the actual application origin.

## 2026-08-04 — native Cloudflare production cutover

- Application branch: `main`
- Standalone Worker: `minecraft-recipe-tree-production`
- Standalone Worker version: `7f45f00b-13e2-4e28-b3b9-5f9ae7532c55`
- D1 database: `minecraft-recipe-tree-production`
  (`e6624ef2-8bd9-49e5-8d32-0671351c61c3`)
- R2 bucket: `minecraft-recipe-tree-production-assets`
- App-router version: `67124577-753c-4aea-aef0-7be11cb8eb9f`
- App-router rollback version: `c05bc89e-687c-4b01-b4e4-086f1ea456ab`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`
- Direct diagnostic URL:
  `https://minecraft-recipe-tree-production.gtjoe51.workers.dev/`

Passed:

- The authenticated Sites export and native D1 import matched exactly: 11 dataset publications,
  four channels, two legacy modpacks, three feedback records, and zero exporter-failure rows.
- The source and destination R2 inventories matched exactly at 18,585 objects and 6,790,050,371
  bytes, including HTTP metadata, storage class, custom metadata, and checksummed migrated bodies.
- The app router was canaried at 5%. A canary catalog response matched the direct standalone
  response after normalized JSON comparison, then the router version was promoted to 100%.
- The canonical homepage, a hashed stylesheet, `/api/datasets`, `/api/modpacks`, a core dataset
  manifest, and a recipe-preview manifest all returned `200` through the standalone Worker.
- A fresh browser tab hydrated MeatballCraft with 196,160 searchable items. The intentionally
  misspelled fuzzy query `furnce` returned Furnace and related machine results.
- An authenticated, idempotent `POST /api/admin/core-datasets/begin` replay returned the exact
  committed publication through both the direct Worker and canonical hostname. A wrong upload
  token remained fail-closed.
- A temporary canonical feedback submission returned `201`, appeared in the authenticated inbox,
  and was deleted from D1; the inbox returned to its original three records.
- A temporary canonical exporter-failure report returned `201`; repeating it returned `200` with
  `duplicate: true`, the same GitHub issue, the same `errors.json` file, and no issue comments. The
  file used `mrt-export-failure-file-v1`. The synthetic issue was closed and its report file and D1
  dedupe row were removed after verification.
- Canonical responses retain the signal-edge compatibility contract in `X-Craftsmann-App-Origin`
  and identify the actual destination as
  `X-Craftsmann-Worker-Origin: https://minecraft-recipe-tree-production.gtjoe51.workers.dev`.
- The Sites origin remains live and unchanged as the rollback source. Migration bridge secrets
  remain installed only for the rollback soak and must be removed before Sites is retired.

Operational improvements made during migration:

- The R2 migration command now resumes objects whose exact immutable inventory already matches,
  rejects destination-only keys, and retries bounded transient read and upload failures with a
  fresh stream per upload attempt.
- Standalone production explicitly enables token-authenticated dataset administration while
  anonymous legacy mutations and unscoped preview ingestion remain disabled.

## 2026-07-31 — hide upload action on mobile

- Application commit: `dc27c6bd38a42c8610a4ae4c6c793de45616b236`
- Sites version: `appgprj_6a5a5da437248191a0f8accf3fb92d5d~appgver_a8a656d2ed6c8191b12161c6e7358db4`
- Sites deployment: `appgdep_6a6ca5ec13c88191b3e07eb3fb8adddc`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`
- Sites origin: `https://minecraft-recipe-tree.gtjoe51.chatgpt.site`

Passed:

- The canonical homepage returned `200`, and a fresh browser tab hydrated the viewer with the
  published MeatballCraft dataset and 196,160 searchable items.
- Vinext's canonical bootstrap import, `/assets/index-Ps-NnNTM.js`, returned `200` as
  `text/javascript`.
- The serialized RSC stylesheet path, `/assets/index-CKJ5bCQJ.css`, returned `200` from the
  canonical hostname as `text/css`.
- The hashed bootstrap asset returned `200` with 81,340 bytes.
- `/api/modpacks` returned `200` with valid JSON containing two published modpacks.

Open infrastructure warning:

- The canonical response did not include the required
  `X-Craftsmann-App-Origin: https://minecraft-recipe-tree.gtjoe51.chatgpt.site` header.
- It did include `X-Craftsmann-Signal-Edge: v1`.
- Production version 145 remains live; the verification failure was reported without falling back
  to an older deployment.

## 2026-07-31 — five-step exporter guide

- Application commit: `63430abcb621bbcef2461108e3c86c2d28e499fb`
- Beta version 7 deployed successfully to
  `https://minecraft-recipe-tree-beta.gtjoe51.chatgpt.site`.
- Production version 144 was saved as
  `appgprj_6a5a5da437248191a0f8accf3fb92d5d~appgver_399ec4a8de808191ad44eef0e0fd0780`.

Production deployment is blocked at the hosting provider:

- Deployment `appgdep_6a6c8eb35f708191854309a33a6cf247` failed with Cloudflare status 521.
- Retry `appgdep_6a6c8ec9cfc081919be18c09dcd30923` failed with Cloudflare status 522.
- A final deployment request timed out before returning a deployment ID.
- A cache-bypassed request to the canonical `/publish` page confirmed that production still serves
  version 143. Do not report version 144 as deployed until its saved version reaches `succeeded` and
  every canonical verification in `AGENTS.md` passes.

## 2026-07-31 — upload-to-viewer release

- Application commit: `af0cc8e45d31a1f98a2954146bc6d080063cd64e`
- Sites version: `appgprj_6a5a5da437248191a0f8accf3fb92d5d~appgver_7a536fed42288191b20ed0036e2538d2`
- Sites deployment: `appgdep_6a6c8c18a220819185b937d7ef1d3a2e`
- Canonical URL: `https://minecraftrecipetree.craftsmannsoftware.com/`
- Sites origin: `https://minecraft-recipe-tree.gtjoe51.chatgpt.site`

Passed:

- The canonical homepage and Sites origin returned `200`.
- The canonical response contained Vinext bootstrap data and serialized RSC asset paths.
- All eight application assets referenced by the canonical homepage returned `200`.
- The viewer hydrated in a browser and loaded the published MeatballCraft dataset.
- The production upload page hydrated and showed the drag-and-drop file control.
- `/local-pack-sw.js` returned `200`.
- `/api/modpacks` returned `200` with the published modpack catalog.

Open infrastructure warning:

- The canonical response did not include the required
  `X-Craftsmann-App-Origin: https://minecraft-recipe-tree.gtjoe51.chatgpt.site` header.
- It did include `X-Craftsmann-Signal-Edge: v1`.
- The active `craftsmann-app-subdomain-router` deployment was confirmed through Wrangler as
  version `c05bc89e-687c-4b01-b4e4-086f1ea456ab`, created on 2026-07-18.
- Application traffic is reaching the correct Sites origin, as shown by matching uncached HTML,
  working hashed assets, the D1-backed modpack API, and the hydrated browser UI. The missing
  verification header remains an infrastructure follow-up and must not be treated as a passed
  check.
