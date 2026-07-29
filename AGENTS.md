# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any code.

# Beta web environment

- Use `beta` as the source branch for requested application changes and deploy every completed, validated change to the separate beta Sites project configured in `.openai/hosting.json`.
- A successful beta deployment is not authorization to change the public application. Do not update or deploy the production Sites project unless the user explicitly requests production promotion.
- The beta Site must set `BETA_DATA_ORIGIN=https://minecraftrecipetree.craftsmannsoftware.com`. Its Worker may proxy only public read requests for the dataset catalog, immutable publications, immutable preview sets, and legacy modpack verification. Never proxy cookies, authorization, feedback, administration, or mutation methods.
- After every beta deployment, verify the beta URL hydrates in a fresh browser tab, exposes the stage controls, serves a hashed application asset, and returns `X-MRT-Beta-Data-Origin: https://minecraftrecipetree.craftsmannsoftware.com` on `/api/datasets`.
- When the user explicitly requests production promotion, promote the exact validated beta application changes while preserving the production project configuration, then run the production verification below.

# Production publishing

- Keep the Sites project publicly accessible. The canonical public URL is `https://minecraftrecipetree.craftsmannsoftware.com/`.
- The canonical hostname is owned by the Cloudflare Worker `craftsmann-app-subdomain-router`, which proxies it to `https://minecraft-recipe-tree.gtjoe51.chatgpt.site`. Do not attach the canonical hostname directly to Sites, replace its DNS record, or point the router at the obsolete `https://minecraftrecipetree.pages.dev` export; the modpack API requires the Sites D1 binding.
- After every production deployment, verify the canonical URL reports `x-craftsmann-app-origin: https://minecraft-recipe-tree.gtjoe51.chatgpt.site`, confirm Vinext's bootstrap import and serialized RSC asset paths resolve from the canonical hostname, load the hydrated client UI in a browser, fetch a hashed application asset, and check `/api/modpacks`. Report and log any failure; never silently fall back to an older deployment.
