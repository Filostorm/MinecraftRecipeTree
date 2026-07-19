# Export, validate, and publish a modpack

This guide covers the complete path from an installed client-side exporter to a stable external
viewer URL. A publication has two identities:

- an immutable SHA-256 `publicationId` for the exact exported content; and
- a stable channel slug such as `multiblock-madness`, which can be repointed atomically when the
  pack is updated.

The viewer's pack switcher reads those channel records. Switching packs changes `?pack=<slug>` in
the URL, so a selected pack can be bookmarked or shared.

## 1. Choose the matching exporter

Do not install an exporter for a different Minecraft/recipe-viewer API. The current source tree
contains these client-only builds:

| Minecraft | Recipe viewer | Exporter project | Identity configuration |
|---|---|---|---|
| 1.20.1 | JEI 15 | `recipe-export-mod` | `jeiexport.packName` / `jeiexport.packVersion` JVM properties, or bounded launcher metadata detection |
| 1.18.2 | REI 8 | `recipe-export-mod-1.18.2` | required `packName` and `packVersion` in `reiexport-request.json` |
| 1.12.2 | HEI/JEI 4 | `recipe-export-mod-1.12.2` | `packName` and `packVersion` in `jeiexport-request.json` or matching JVM properties |
| 1.7.10 | NEI 2.8.44-GTNH | `recipe-export-mod-1.7.10` | exact pinned `pack` object in `neiexport-request.json` (currently GT New Horizons 2.8.4) |

Download the version-matched release from the public
[export and publishing guide](https://minecraftrecipetree.craftsmannsoftware.com/publish). The
guide obtains each JAR URL, byte length, compatibility statement, and SHA-256 from a bounded exact
release manifest. Verify the checksum before installation. Development and `sources` JARs are
explicitly excluded from that manifest.

## 2. Install the exporter

1. Close Minecraft.
2. Open the target instance, not the launcher's global Minecraft directory.
3. Put the version-matched exporter JAR in that instance's `mods` directory.
4. Confirm the pack already contains its required JEI, REI, or HEI version.
5. Start the pack and open a disposable single-player world.

The exporter is client-side. A single-player world is important when exporting mob drops, block
drops, trades, staged recipes, or integrations that consult server/world state. An items-and-recipes
export can work without those phases, but it will not invent missing world-derived data.

Typical instance roots:

- CurseForge: `Documents/curseforge/minecraft/Instances/<pack>`
- Prism/MultiMC: the selected instance; Minecraft files are usually under `.minecraft`
- Vanilla launcher: the selected game directory (commonly `minecraft` or `.minecraft`)

## 3. Confirm pack identity

Current exporters write one integrity-bound object into `manifest.json`:

```json
{
  "pack": {
    "name": "Example Pack",
    "version": "1.4.2",
    "identitySource": "explicit-request"
  }
}
```

Use the canonical public pack name, not a local label such as `Example Pack - testing`. Explicit
configuration has highest precedence. CurseForge/Prism/Modrinth metadata is accepted only through
bounded, no-symlink parsers; malformed or conflicting metadata is logged. A game-directory-name
inference is visibly labeled `game-directory` and is intentionally rejected by hosted publication
until the user confirms the name and version explicitly. No local filesystem path, username, or
launcher account is serialized. Text limits are measured in Unicode code points, not UTF-16 code
units, and every exporter rejects C0/C1 controls plus bidirectional and zero-width formatting
characters before the identity reaches a manifest.

For 1.18.2 and 1.12.2 request files, include:

```json
{
  "packName": "Example Pack",
  "packVersion": "1.4.2"
}
```

The pinned GTNH 1.7.10 exporter instead requires this object in `neiexport-request.json`:

```json
{
  "pack": {
    "name": "GT New Horizons",
    "version": "2.8.4"
  }
}
```

Its full request also fixes `iconScale: 1` and `recipeScale: 2`; start from
`recipe-export-mod-1.7.10/example-request.json` because that exporter intentionally rejects other
pack identities and scale values.

For the current JEI exporter, automated launchers can add:

```text
-Djeiexport.packName=Example Pack
-Djeiexport.packVersion=1.4.2
```

## 4. Run and inspect the export

For the current JEI build, join the world and run:

```text
/jeiexport all
```

Publish a Forge 1.20.1 + JEI 15 export with the strict `generic-jei-1.20.1` profile. The default
command already emits that profile's required `iconScale: 4`, `recipeScale: 2`, exact failure
diagnostics, and modpack identity. Do not pass a different icon scale for a hosted snapshot.

The 4× icon canvas is a deliberate fidelity profile: it preserves high-resolution and custom JEI
ingredient renderers without post-export upscaling. Compared with native 1× rendering it has 16×
as many icon pixels, so GPU readback, PNG encoding, raw-export storage, staging I/O, and importer
work all increase (compressed byte growth depends on the texture). A future explicit native-1×
profile would be the faster, smaller alternative for pixel-art-only packs, but it can lose detail
from custom renderers. The publisher therefore rejects a scale mismatch instead of silently
resampling between those two quality/performance choices.

The older exporters consume their documented request file and use `.running-*`, `.done`, and
`.failed` markers. Wait for an explicit successful completion message or `.done` marker. Never
upload a directory while Minecraft is still writing it.

The output directory contains `manifest.json`, `items.json`, `categories.json`, `index.json`,
recipe documents/images, and optional mob/block-drop data. Before continuing:

- `manifest.aborted` must be `false`;
- `manifest.pack.name` and `.version` must be correct;
- `failures.json` must be reviewed rather than ignored;
- free disk space should exceed roughly twice the raw export when full-copy staging is used.

Custom ingredient types, item/fluid/gas quantities, tag/OreDictionary alternatives,
non-consumed catalysts, byproducts, staged/hidden recipes, custom-death mob drops, and transparent
icons are validated as structured data. Unsupported or ambiguous semantics block the matching
strict quality profile instead of silently being converted to quantity `1` or an arbitrary item.

## 5. Prepare a publication

From `viewer/`, install dependencies once, then run the guided preparer:

```bash
npm install

npm run publish:modpack -- prepare \
  --source "/absolute/path/to/jei-exports" \
  --workspace "/absolute/path/to/new-publication-workspace" \
  --profile multiblock-madness-1.12.2 \
  --slug multiblock-madness
```

For a current 1.20.1 JEI export, substitute:

```bash
--profile generic-jei-1.20.1
```

Use `npm run publish:modpack -- --help` for the live list of supported strict profiles. A profile
is not just a label: it selects version-specific completeness, image scale, diagnostics, and recipe
semantics gates. There is no generic profile fallback.

Preparation performs this transaction:

1. rejects symlinks, sockets, devices, and malformed identity before mutation;
2. stages a private copy outside the live viewer data;
3. validates every document, cross-reference, quantity, and referenced image;
4. losslessly optimizes retained assets and records exact omitted-preview accounting;
5. computes the content-derived core `publicationId`;
6. builds and validates the deduplicated recipe-preview sidecar;
7. writes `publication-plan.json` last as the local commit marker.

On macOS, APFS clone staging is the default and usually consumes little additional space. On
Windows/Linux, full-copy staging is the explicit cross-platform implementation. You can select the
mode yourself:

```bash
--staging-mode clone
--staging-mode copy
```

The importer logs the chosen mode and never changes modes after a failure. `copy` works across
platforms and filesystems but requires more time and disk. `clone` is fast and space-efficient but
requires macOS/APFS, `xcrun`, and source/workspace placement compatible with `clonefile(2)`.

If preparation fails, the workspace is retained for diagnosis and is never activated. Start a new
workspace after fixing/re-exporting; immutable publication attempts are not overwritten.

## 6. Submit or upload the prepared publication

### Contributor path

Ordinary contributors send the prepared workspace or its validation report/submission reference to
the site operator. They do **not** receive production upload tokens. This is the safe current path
until per-user publication sessions are implemented.

### Operator path

The operator reads `publication-plan.json`, then temporarily configures the preview-ingestion route
for exactly its `previewAssetSetId`:

```text
PREVIEW_UPLOAD_ENABLED=true
PREVIEW_UPLOAD_ASSET_SET_ID=<previewAssetSetId from publication-plan.json>
PREVIEW_UPLOAD_TOKEN=<fresh secret, at least 32 characters>
CORE_DATASET_UPLOAD_TOKEN=<fresh operator secret, 32..8192 bytes>
```

Apply those four values to the existing Sites project's server-side environment/secrets, then
deploy that configuration before starting the upload. Do not place any value in client-visible
configuration and do not change the canonical hostname or the
`craftsmann-app-subdomain-router`. Verify that the canonical site is serving the newly configured
Sites deployment before continuing.

Store tokens either in the operator environment or plain mode-`0600` files. Never put them in a
URL, shell history argument, `EXPO_PUBLIC_*` variable, archive, chat, or contributor machine.

Then run one command:

```bash
npm run publish:modpack -- upload \
  --workspace "/absolute/path/to/prepared-publication-workspace" \
  --channel-action create \
  --default false \
  --app-origin https://minecraftrecipetree.craftsmannsoftware.com \
  --core-token-file "/private/path/core-token" \
  --preview-token-file "/private/path/preview-token"
```

Use `--channel-action create` only for a new slug and `--channel-action update` only after reviewing
the existing channel with that slug. `--default` is also an operator decision; it is intentionally
absent from the contributor-controlled plan. The command reads the current public catalog before
loading credentials, converts that explicit intent into a compare-and-swap precondition, and
authenticates both exact ingestion targets before either bulk upload starts. It then resumes
immutable uploads, checks hashes and byte lengths, commits each manifest last, verifies both public
delivery routes, and only then activates the D1 channel. A concurrent channel update makes the
activation fail closed instead of overwriting the newer publication. Display name, Minecraft
version, and pack version come from the integrity-bound exporter manifest; the operator does not
retype them.

After success, remove the preview gate, target ID, preview token, and temporary core token from the
Sites server environment, deploy that disabled configuration, and confirm both authenticated
ingestion routes reject further writes. The command prints the share URL:

```text
https://minecraftrecipetree.craftsmannsoftware.com/?pack=<slug>
```

## 7. Publish an update

1. Change the exporter request to the new `packVersion`.
2. Export a fresh complete snapshot.
3. Prepare it in a new workspace, reusing the same channel `--slug`.
4. Upload and verify it with `--channel-action update`; choose `--default true` only if this channel
   should remain or become the catalog default.

The upload reads the current publication ID and sends it as an exact compare-and-swap condition.
The new export receives new core/preview content IDs. Channel activation is one atomic pointer
change, so readers see either the complete old pack or the complete new pack. Old immutable objects
remain rollback candidates; they are not overwritten.

## Why production does not accept anonymous raw browser uploads

Large packs can contain hundreds of thousands of files and multiple GiB. Anonymous raw uploads
would expose storage/egress denial-of-service, channel-name spam, orphaned staging objects, and
Worker CPU/subrequest exhaustion. Browser directory hashing/transcoding also behaves inconsistently
across Safari, Chromium, and mobile storage implementations.

True self-service publication should therefore use sign-in plus a short-lived, single-submission
credential bound to an exact object inventory, aggregate byte/object quotas, expiry, ownership,
rate limits, server-side hash verification, moderation state, and orphan cleanup. That design keeps
the current resumable protocol while removing the operator handoff. A local-only browser import can
be added separately for private viewing, but it does not create a durable share URL.

## Troubleshooting

- **Pack identity was inferred from the directory:** add explicit name/version and re-export.
- **Profile rejected the Minecraft or image scale:** use the exporter/request settings required by
  that exact profile; do not relabel the export.
- **A recipe quantity or direction is ambiguous:** inspect the logged recipe/category and update the
  exporter adapter. The validator intentionally has no quantity-`1` fallback.
- **Recipe previews are missing:** rerun the exporter after resolving the listed integration error.
  Hosted sidecar publication requires its exact preview inventory.
- **Full-copy staging runs out of disk:** choose a larger workspace filesystem or use APFS clone
  mode where supported; do not mutate the raw export in place.
- **Upload stops mid-run:** rerun the same upload command. Matching immutable objects are verified
  and reused; a public commit marker is not written until the full inventory exists.
- **Activation fails after uploads:** do not manually edit D1. Fix the reported metadata/token issue
  and rerun upload; immutable objects remain safe to reuse.
- **The new pack is not in the selector:** open `/api/datasets`, confirm the slug exists, then use the
  printed `?pack=` URL. The viewer never silently substitutes another pack for an invalid slug.
