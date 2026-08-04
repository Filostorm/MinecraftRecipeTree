# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any code.

# Release environments

- Use `beta` as the integration branch for application changes and `main` as the production branch.
- Validate and deploy application changes to the beta Cloudflare Worker before production promotion.
- The beta Worker is `minecraft-recipe-tree-beta` at `https://minecraft-recipe-tree-beta.gtjoe51.workers.dev`. Deploy it with `npm run deploy:cloudflare-beta`; the historical beta Sites project is not an active release target.
- Until the storage migration and router cutover are verified, the production Sites project `appgprj_6a5a5da437248191a0f8accf3fb92d5d` remains the rollback source. Do not delete or detach its managed storage during the migration soak.
- The beta Worker must set `BETA_DATA_ORIGIN=https://minecraftrecipetree.craftsmannsoftware.com`. It may proxy only public read requests for the dataset catalog, immutable publications, immutable preview sets, and legacy modpack verification. Never proxy cookies, authorization, feedback, administration, or mutation methods.
- After every beta deployment, verify that the beta URL hydrates in a fresh browser tab, the changed feature works, a hashed application asset loads, and `/api/datasets` returns `X-MRT-Beta-Data-Origin: https://minecraftrecipetree.craftsmannsoftware.com`.
- A successful beta deployment is not authorization to change production. Promote only after the user explicitly approves production deployment, and preserve the production project configuration while transferring the exact validated application changes.
- The complete release procedure and environment inventory are in `docs/hosting-environments.md`.

# Production publishing

- The canonical public URL is `https://minecraftrecipetree.craftsmannsoftware.com/` and remains owned by the existing Cloudflare router chain; do not replace its DNS record.
- The standalone production Worker is `minecraft-recipe-tree-production`. Its native bindings are D1 `minecraft-recipe-tree-production` and R2 `minecraft-recipe-tree-production-assets`.
- Cut production over by changing `craftsmann-app-subdomain-router` to a service binding for the standalone Worker. Keep the Sites version available for rollback until database, object inventory, upload, feedback, and failure-report checks pass and the migration has soaked.
- After every production deployment, confirm Vinext's bootstrap import and serialized RSC asset paths resolve from the canonical hostname, load the hydrated client UI, fetch a hashed asset, and verify `/api/datasets`, `/api/modpacks`, uploads, feedback, and failure reporting. Report every failure; never silently fall back.
