# Production deployment verifications

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
