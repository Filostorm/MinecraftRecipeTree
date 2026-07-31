# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any code.

# Release environments

- Use `beta` as the integration branch for application changes and `main` as the production branch.
- Validate and deploy application changes to the private beta Site before asking for explicit production promotion.
- The beta Sites project is `appgprj_6a6a505e7bc08191acada3d05fa5d18d` at `https://minecraft-recipe-tree-beta.gtjoe51.chatgpt.site`. The `beta` branch's `.openai/hosting.json` must point to this project.
- The production Sites project is `appgprj_6a5a5da437248191a0f8accf3fb92d5d`. The `main` branch's `.openai/hosting.json` must point to this project. Never deploy with the other environment's project ID.
- The beta Site must remain private and set `BETA_DATA_ORIGIN=https://minecraftrecipetree.craftsmannsoftware.com`. Its Worker may proxy only public read requests for the dataset catalog, immutable publications, immutable preview sets, and legacy modpack verification. Never proxy cookies, authorization, feedback, administration, or mutation methods.
- After every beta deployment, verify that the beta URL hydrates in a fresh browser tab, the changed feature works, a hashed application asset loads, and `/api/datasets` returns `X-MRT-Beta-Data-Origin: https://minecraftrecipetree.craftsmannsoftware.com`.
- A successful beta deployment is not authorization to change production. Promote only after the user explicitly approves production deployment, and preserve the production project configuration while transferring the exact validated application changes.
- The complete release procedure and environment inventory are in `docs/hosting-environments.md`.

# Production publishing

- Keep the Sites project publicly accessible. The canonical public URL is `https://minecraftrecipetree.craftsmannsoftware.com/`.
- The canonical hostname is owned by the Cloudflare Worker `craftsmann-app-subdomain-router`, which proxies it to `https://minecraft-recipe-tree.gtjoe51.chatgpt.site`. Do not attach the canonical hostname directly to Sites, replace its DNS record, or point the router at the obsolete `https://minecraftrecipetree.pages.dev` export; the modpack API requires the Sites D1 binding.
- After every production deployment, verify the canonical URL reports `x-craftsmann-app-origin: https://minecraft-recipe-tree.gtjoe51.chatgpt.site`, confirm Vinext's bootstrap import and serialized RSC asset paths resolve from the canonical hostname, load the hydrated client UI in a browser, fetch a hashed application asset, and check `/api/modpacks`. Report and log any failure; never silently fall back to an older deployment.
