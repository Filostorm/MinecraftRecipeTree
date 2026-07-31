# Beta deployment verifications

## 2026-07-31 — Recipe production planning

- Application commit: `c2929917eafb0692d336832af677de2beaecc358`
- Sites version: `appgprj_6a6a505e7bc08191acada3d05fa5d18d~appgver_c0a77f9919908191a04f81fdf5b0e988` (version 8)
- Deployment: `appgdep_6a6cdea9b1b08191a5232b1b7696e34d`
- URL: `https://minecraft-recipe-tree-beta.gtjoe51.chatgpt.site`

Deployment status was `succeeded`. The application production build, focused graph tests, TypeScript validation, and the Forge 1.20.1 build passed before deployment.

Live acceptance verification could not get past the private Sites authentication gate. A fresh browser tab and a direct request to `/api/datasets` both received HTTP 401 before the application or beta data proxy ran. Consequently, hydration, production-planner interaction, hashed asset loading, and the `X-MRT-Beta-Data-Origin` response header remain pending an authenticated beta session. Production was not changed.
