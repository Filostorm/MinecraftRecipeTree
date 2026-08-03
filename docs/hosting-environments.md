# Hosting environments

Minecraft Recipe Tree uses a private beta Site for live testing before an explicitly approved
production release. Keep the Sites project configuration tied to its branch so a beta build cannot
overwrite production.

| Environment | Source branch | Sites project ID | URL | Access |
| --- | --- | --- | --- | --- |
| Beta | `beta` | Cloudflare Worker `minecraft-recipe-tree-beta` | [minecraft-recipe-tree-beta.gtjoe51.workers.dev](https://minecraft-recipe-tree-beta.gtjoe51.workers.dev/) | Private release candidate |
| Production | `main` | `appgprj_6a5a5da437248191a0f8accf3fb92d5d` | [minecraftrecipetree.craftsmannsoftware.com](https://minecraftrecipetree.craftsmannsoftware.com/) | Public |

The production Sites origin is
`https://minecraft-recipe-tree.gtjoe51.chatgpt.site`. The canonical hostname is routed to that
origin by the `craftsmann-app-subdomain-router` Cloudflare Worker.

The canonical beta endpoint is `https://minecraft-recipe-tree-beta.gtjoe51.workers.dev`. Use it for
all beta acceptance testing and links. The previous `chatgpt.site` beta endpoint and the obsolete
`https://minecraftrecipetree.pages.dev` export are not the active beta application.

## Branch-specific configuration

The `beta` branch retains its `.openai/hosting.json` only for compatibility with historical Sites
builds. Active beta releases use `npm run deploy:cloudflare-beta`, which builds an isolated
Cloudflare Worker without the Sites-managed D1 or R2 bindings. Production continues to use the
production Sites project and is not changed by this beta deployment path.

Each branch stores its own `.openai/hosting.json`:

- `beta` must use project `appgprj_6a6a505e7bc08191acada3d05fa5d18d`.
- `main` must use project `appgprj_6a5a5da437248191a0f8accf3fb92d5d`.

Check the project ID before saving or deploying a version. Do not copy the beta hosting file onto
`main`, copy the production hosting file onto `beta`, attach the canonical production hostname
directly to Sites, or change its DNS record.

## Beta data access

The beta Site has this environment variable:

```text
BETA_DATA_ORIGIN=https://minecraftrecipetree.craftsmannsoftware.com
```

The beta Worker uses that origin only for public, read-only access to the dataset catalog, immutable
publications, immutable preview sets, and legacy modpack verification. It must strip cookies and
authorization and refuse feedback, administration, and mutation requests. This lets a private beta
exercise production-shaped data without sharing production bindings or accepting writes.

## Release workflow

1. Reconcile the latest `main` application changes into `beta` while retaining the beta project ID
   and beta-only data proxy.
2. Implement and validate the change on `beta`.
3. Mirror the exact tested commit into `Filostorm/CraftsmannSoftware` and deploy it with
   `npm run deploy:cloudflare-beta` from `sites/minecraft-recipe-tree`.
4. Verify the beta page hydrates in a fresh browser tab, the changed feature works, a hashed
   application asset loads, and `/api/datasets` returns
   `X-MRT-Beta-Data-Origin: https://minecraftrecipetree.craftsmannsoftware.com`.
5. Share the Cloudflare beta URL for acceptance testing. A successful beta deployment does not
   authorize a production release.
6. After explicit production approval, transfer the exact validated application changes to `main`
   while retaining the production `.openai/hosting.json`.
7. Save and deploy the production commit, then perform every production check listed in
   `AGENTS.md`.

If beta validation fails, fix and redeploy beta. Do not silently fall back to an older beta or
promote an unverified commit.
