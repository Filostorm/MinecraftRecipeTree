# Recipe Tree exporter changelog

This file contains release-ready notes for the Minecraft exporter builds. The generated exporter
manifest remains the source of truth for downloadable filenames, checksums, and compatibility.

## 2026-08-08

### Forge HEI/JEI 1.12.2 — 1.1.4

Recommended for MeatballCraft, Multiblock Madness 1, and other Forge 1.12.2 packs using JEI or HEI
4.x.

#### Added

- Export placed-block structure data and block totals for all Modular Machinery Structure Preview
  recipes.
- Support the original Modular Machinery API, Modular Machinery Community Edition, compatible
  subclasses, and forks that expose `long`, boxed `Long`, or `Optional<Long>` preview selectors.
- Record exact Modular Machinery preview, success, and failure totals in exporter diagnostics.
- Allow targeted quality samples to scan the complete item catalog before exporting selected
  recipes, making structure-preview validation representative of a full export.

#### Fixed

- Use the first displayable Modular Machinery ingredient when a deterministic preview sample is
  empty or resolves to air.
- Keep air-only positions out of the material count while retaining their coordinates in the
  structure dimensions.
- Resolve machine fields through compatible wrapper class hierarchies instead of requiring one
  exact wrapper implementation.
- Report structure extraction failures with the category, recipe index, wrapper class, and bounded
  cause chain instead of silently producing an empty preview.
- Preserve the newly exported structure metadata through preview-sidecar generation and immutable
  Cloudflare publication.

#### Verified

- Fresh MeatballCraft 0.18.6 export: 196,127 items, 359,096 recipes, 674 categories, and zero
  failures.
- All 260 Modular Machinery structure previews exported successfully with placed blocks,
  dimensions, and block counts.
- Compiled against both the supported JEI 4 compatibility floor and MeatballCraft's installed
  Had Enough Items 4.28.1 API.

### Other current exporter builds

These builds are unchanged by the 2026-08-08 release:

| Minecraft | Recipe viewer | Loader | Current build | Status |
| --- | --- | --- | --- | --- |
| 1.21.1 | JEI 19 | NeoForge 21.1 | 1.0.0 | Current |
| 1.20.1 | JEI 15 | Forge 47 | 1.1.0 | Current public release; 1.2.0 beta remains in testing |
| 1.18.2 | REI 8 | Forge 40 | 1.0.52 | Current |
| 1.7.10 | NEI 2.8.44-GTNH | Forge 10.13.4 | 1.0.150 | Current |

## Copy-ready release summary

Recipe Tree Exporter 1.1.4 improves Modular Machinery support on Minecraft 1.12.2. Structure
previews now export as their actual placed blocks with correct dimensions and material totals across
the original Modular Machinery mod, Modular Machinery Community Edition, and compatible forks.
Air-only positions no longer inflate block counts, alternate display blocks are handled correctly,
and any structure extraction problem is included in the exporter diagnostics. The release was
validated on a complete MeatballCraft 0.18.6 export: all 260 Modular Machinery structures exported
successfully and the full 359,096-recipe export completed with zero failures.
