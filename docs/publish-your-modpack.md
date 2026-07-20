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

The two Multiblock Madness publication profiles deliberately keep the source renderer at
Minecraft's native 16×16 grid and rasterize the complete recipe-viewer layout at 2×:

| Pack profile | Request contract | Hosted quality contract |
|---|---|---|
| `multiblock-madness-1.12.2` | `jeiexport-request.json` with `iconScale: 1`, `recipeScale: 2`, explicit `packName`/`packVersion`, and `exitOnComplete: true` | select `--profile multiblock-madness-1.12.2` during validation/preparation; do not add a `profile` field to the 1.12.2 request because that exporter rejects unknown keys |
| `multiblock-madness-2-1.18.2` | `reiexport-request.json` with the exact matching `profile`, `iconScale: 1`, `recipeScale: 2`, `exitOnComplete: true`, `failOnError: true`, and `pngQueueCapacity` in the validated `8..128` range | select `--profile multiblock-madness-2-1.18.2` during validation/preparation |

Use `qualitySample` only for a bounded diagnostic mini export. A production full request omits
that field; the publisher rejects a manifest containing `qualitySample` instead of silently
publishing a six-recipe diagnostic corpus. The bounded PNG queue provides encoder backpressure:
raising the 1.18.2 capacity above 128 would increase retained raster memory and is rejected by both
the launcher and exporter rather than treated as a performance fallback.

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

The completed GTNH manifest must contain this exact normalized-data attribution contract:

```json
{
  "attribution": {
    "sourceUrl": "https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4",
    "projectUrl": "https://www.gtnewhorizons.com/",
    "licenseIdentifier": "CC BY-NC-SA 4.0",
    "licenseUrl": "https://creativecommons.org/licenses/by-nc-sa/4.0/"
  }
}
```

The importer and preview-sidecar builder require exact raw/hosted preservation of this object.
Its license fields describe the normalized GTNH modpack data source; they do not relicense or
claim coverage over third-party mod artwork and textures.

The pinned GTNH adapters classify AE2 in-world crafting's complete wildcard-query closure and
BetterQuesting pages as informational associations. BetterQuesting preserves all item references
and groups each choice reward as one logical slot, but it intentionally does not translate task
`AND`/`OR` logic, optional-task behavior, possession-versus-consumption rules, or a player's
selected reward into material edges. The viewer keeps both categories browsable under **Info** and
excludes them from graphs and totals.

Publication also requires the exact, lexicographically ordered 17-entry special-handler ledger:
eight version-pinned adapters and nine explicitly excluded non-recipe handlers (five query-only
views plus four presentation-only browsers). The registered-handler count therefore equals the
exported category count plus nine; unknown, missing, or reordered policies fail closed.

Crop Plugin authority comes from the deterministic, canonical replay of all 159 `ALL_CROPS`
entries and 12,561 two-parent pairs, not from the plugin's identity-hash-dependent raw cache. The
repaired closure contains 290,789 pages (4,595 craft winners and 286,194 usage-only winners) after
independent first-winner de-duplication in each craft and parent-usage query bucket. Its graph uses
clean `BreedResult` item stacks, while breeding-points/chance lore remains presentation-only in the
retained NEI preview. A publication whose handler-policy ledger does not declare these exact
adapter contracts is rejected rather than treated as an older compatible fallback.

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

### Exporter-build acceptance for site operators

This step is for operators releasing a new exporter JAR, not ordinary modpack contributors. Use
the acceptance action in place of a separate validation pass so a large export is traversed only
once:

```bash
npm run accept:exporter -- \
  --release forge-hei-1.12.2 \
  --profile multiblock-madness-1.12.2 \
  --export-root "/absolute/path/to/completed-full-export"

npm run package:exporter:1.12.2
```

The pinned GTNH release uses `npm run package:exporter:1.7.10` after its own full
`gtnh-1.7.10` acceptance export.

The ignored local receipt binds the configured release identity, exact source-JAR SHA-256/length,
the release-defined acceptance profile, the exact release-definition/validation-policy source
digest, the pinned Sharp/libvips lock entries, and the validated full export manifest's
SHA-256/length. It contains no local path or
credential. Packaging refuses diagnostic `qualitySample` output and any missing, malformed,
symlinked, profile-mismatched, policy-stale, definition-stale, or stale-JAR receipt. Receipts are
local operator attestations, not third-party signatures; create one only from the full export
produced while testing that exact installed JAR.

For the version-isolated 1.7.10, 1.12.2, and 1.18.2 releases, the JAR carries a canonical
`META-INF/mrt-exporter-build.json` identity containing its exporter ID, Minecraft version, and a
SHA-256 digest over the rest of its canonical payload. The exporter writes the same bytes to root
`exporter-build.json`; acceptance rejects a missing, noncanonical, mismatched, or stale identity.
It also streams every file twice to compare exhaustive pre/post tree digests, rejecting symlinks,
hard links, and mutation during validation. This extra I/O is the security cost of avoiding a
second full-size immutable snapshot. The digest workers are bounded and never buffer whole export
files. Before the first release of GTNH or either Multiblock Madness pack, populate that release's
exact `acceptanceCorpus` counts from its completed full export; the initial `null` value
deliberately prevents packaging a diagnostic or partial corpus.

Exporter release packaging is serialized with an exclusive manifest lock. Concurrent packaging
fails immediately. An existing lock is never silently treated as stale or removed: first verify
that no packaging process is active, then inspect and manually remove the leftover lock. Direct
script execution must explicitly select `--release <id>` or `--all`. Receipt authorization is
rechecked inside the acquired lock immediately before public mutation. Receipt creation or
replacement uses the same lock, so it cannot race a packaging transaction.

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

This is currently an operator-controlled phase because it requires the ignored local acceptance
receipt and configured source JAR; neither is an upload credential, but the receipt is a local
operator attestation and is not accepted from an untrusted contributor. From `viewer/`, install
dependencies once, then run the guided preparer:

```bash
npm install

npm run publish:modpack -- prepare \
  --source "/absolute/path/to/jei-exports" \
  --workspace "/absolute/path/to/new-publication-workspace" \
  --profile multiblock-madness-1.12.2 \
  --release forge-hei-1.12.2 \
  --slug multiblock-madness
```

Multiblock Madness 2 uses its independently accepted 1.18.2 release:

```bash
--profile multiblock-madness-2-1.18.2 --release forge-rei-1.18.2
```

Use `npm run publish:modpack -- --help` for the live list of supported strict profiles. A profile
is not just a label: it selects version-specific completeness, image scale, diagnostics, and recipe
semantics gates. `--release` is also mandatory: it selects exactly one configured exporter artifact
and its local acceptance receipt. There is no generic profile, release, or receipt fallback. A
release without canonical `exporter-build.json` provenance or a completed exact-corpus receipt is
not publication-eligible yet.

Preparation performs this transaction:

1. revalidates the selected receipt against the exact configured JAR bytes, release definition,
   validation policy, profile, Minecraft version, pack identity, and emitted `exporter-build.json`;
2. rejects symlinks, sockets, devices, and malformed identity before transformation;
3. stages a private copy outside the live viewer data;
4. streams a SHA-256 digest over that exact staged snapshot and requires it to equal the receipt's
   exhaustive accepted-tree digest before any optimizer runs;
5. validates every document, cross-reference, quantity, and referenced image;
6. losslessly optimizes retained assets and records exact omitted-preview accounting;
7. computes the content-derived core `publicationId` and builds the preview sidecar;
8. revalidates that the receipt/JAR did not change, then writes `publication-plan.json` last with
   the full normalized receipt plus its canonical SHA-256 identity.

The extra tree digest is one bounded streaming read of the staged raw export. This has an I/O cost
on very large packs, but does not buffer whole files and does not create another full snapshot.
Checking only `manifest.json` and `exporter-build.json` would be faster, but could authorize recipes
or images that were not part of the accepted export, so production preparation deliberately does
not offer that weaker mode.

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

Ordinary contributors send the completed raw export plus its request/completion evidence to the
site operator. The operator performs release acceptance and preparation, or receives a prepared
workspace from another explicitly trusted operator whose identical local receipt/JAR can be
revalidated. Contributors do **not** receive production upload tokens or supply an acceptance
receipt. This is the safe current path until signed per-user publication sessions are implemented.

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
absent from the contributor-controlled plan. Before reading the public catalog or credentials, the
command revalidates the plan's exact receipt identity against the current version-specific JAR,
policy, profile, pack, and packed `exporter-build.json`. A replaced receipt, rebuilt JAR, or MM1/MM2
workspace swap therefore fails before any network or credential access. It then converts the
explicit channel intent into a compare-and-swap precondition and
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
