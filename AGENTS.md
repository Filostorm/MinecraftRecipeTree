# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any code.

# Release branches

- Use `beta` as the integration branch for requested application changes. Start work from `beta`, validate the completed change, commit it, and push it to `origin/beta`.
- A successful beta build is not authorization to deploy production. Do not update the production branch, push the beta commit to the Sites source repository, save a production Sites version, or deploy it unless the user explicitly requests a production promotion.
- When the user explicitly requests production promotion, deploy the exact validated `beta` commit with Sites using the existing project in `.openai/hosting.json`. Do not rebuild from a different source state.

# Production publishing

- Keep the Sites project publicly accessible. The canonical public URL is `https://minecraftrecipetree.craftsmannsoftware.com/`.
- The canonical hostname is owned by the Cloudflare Worker `craftsmann-app-subdomain-router`, which proxies it to `https://minecraft-recipe-tree.gtjoe51.chatgpt.site`. Do not attach the canonical hostname directly to Sites, replace its DNS record, or point the router at the obsolete `https://minecraftrecipetree.pages.dev` export; the modpack API requires the Sites D1 binding.
- After every production deployment, verify the canonical URL reports `x-craftsmann-app-origin: https://minecraft-recipe-tree.gtjoe51.chatgpt.site`, confirm Vinext's bootstrap import and serialized RSC asset paths resolve from the canonical hostname, load the hydrated client UI in a browser, fetch a hashed application asset, and check `/api/modpacks`. Report and log any failure; never silently fall back to an older deployment.
