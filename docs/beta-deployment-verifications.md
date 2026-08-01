# Beta deployment verifications

## 2026-07-31 — Recipe production planning

- Application commit: `c2929917eafb0692d336832af677de2beaecc358`
- Sites version: `appgprj_6a6a505e7bc08191acada3d05fa5d18d~appgver_c0a77f9919908191a04f81fdf5b0e988` (version 8)
- Deployment: `appgdep_6a6cdea9b1b08191a5232b1b7696e34d`
- URL: `https://minecraft-recipe-tree-beta.gtjoe51.chatgpt.site`

Deployment status was `succeeded`. The application production build, focused graph tests, TypeScript validation, and the Forge 1.20.1 build passed before deployment.

Live acceptance verification could not get past the private Sites authentication gate. A fresh browser tab and a direct request to `/api/datasets` both received HTTP 401 before the application or beta data proxy ran. Consequently, hydration, production-planner interaction, hashed asset loading, and the `X-MRT-Beta-Data-Origin` response header remain pending an authenticated beta session. Production was not changed.

## 2026-07-31 — Visible recipe-card production controls

- Application commit: `7d574d49832e592cd2ba65c03286c5675a604d76`
- Sites version: `appgprj_6a6a505e7bc08191acada3d05fa5d18d~appgver_ad8cfa025c94819199a757348d487fd2` (version 9)
- Deployment: `appgdep_6a6ce653e17481919a16c9af60da0b9d`
- URL: `https://minecraft-recipe-tree-beta.gtjoe51.chatgpt.site`

Deployment status was `succeeded`. The application production build, TypeScript validation, 24 focused graph/picker tests, and the Forge 1.20.1 and 1.12.2 timing-extraction builds passed before deployment.

Owner-authenticated HTTP verification passed for the application document, `/api/datasets`, the hashed page bundle, and the lazy-loaded hashed graph-viewer bundle. `/api/datasets` returned `X-MRT-Beta-Data-Origin: https://minecraftrecipetree.craftsmannsoftware.com`. The deployed graph bundle contains the new `Production target`, `View machine recipe`, timing-unavailable fallback, and parallel-machine guidance copy.

The automated interactive browser cannot attach the private Sites owner-verification header and still stops at the sign-in gate. Hydrated tap/click acceptance therefore remains pending a signed-in browser session. Production was not changed.

## 2026-07-31 — Multiblock previews and block counts

- Application commit: `0e068dec04bbdd5cee79a42bbf5b6d0f4572737c`
- Sites version: `appgprj_6a6a505e7bc08191acada3d05fa5d18d~appgver_8f2fed70110c8191904553fc6a62f455` (version 10)
- Deployment: `appgdep_6a6d3e768dfc8191b8f25fa976fb7cad`
- URL: `https://minecraft-recipe-tree-beta.gtjoe51.chatgpt.site`

Deployment status was `succeeded`. The application production build, TypeScript validation, 24 focused multiblock/export-validation tests, and the Forge 1.12.2 exporter test/build passed before deployment.

Owner-authenticated HTTP verification passed for the application document, `/api/datasets`, the hashed application and graph-viewer bundles, and the lazy-loaded 69,553-byte Multiblock Madness 3.2.3 structure bundle. `/api/datasets` returned `X-MRT-Beta-Data-Origin: https://minecraftrecipetree.craftsmannsoftware.com`, and the deployed structure bundle contains the expected low-gravity deposition chamber data.

A fresh interactive browser tab reached the private Sites sign-in flow, but the automation does not have a signed-in owner session and no account credentials were entered. Hydrated tap/click acceptance on the private URL therefore remains pending a signed-in browser session; the renderer, rotation/capping behavior, structure validation, exact block counts, and current-pack compatibility data are covered by the focused tests. Production was not changed.
