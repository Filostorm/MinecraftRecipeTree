# Architect's Exodus beta.29 optimization benchmark

Measured August 3-4, 2026 against an isolated Architect's Exodus 1.8.0 instance on Minecraft
1.20.1. The launcher profile and original pack files were not modified. Each comparison uses the
same pack state for the old and new exporter unless noted.

## Results at a glance

| Area | Before | beta.29 | Change |
| --- | ---: | ---: | ---: |
| Browser full-import save | 98.169 s | 83.042 s | 15.4% faster |
| Duplicate recipe overlays | 8,332 old-export candidates | 8,279 matching overlays reused | nearly all measured opportunity |
| Duplicate overlay opportunity | 25.915 MiB in the old export | no second file written for a reused overlay | approximately 4.2% of the old full ZIP targeted |
| Regular-update archive | 614.218 MiB full ZIP | 14.830 MiB delta ZIP | 97.59% smaller (41.4x) |

## 1. Bounded parallel browser writes

The importer now uses four Cache Storage write lanes with a bounded backlog of 128 entries. This
keeps decompression backpressure in place and avoids holding the entire archive in memory.

On the same full Architect's Exodus archive, the save stage improved from **98.169 seconds** with
serial writes to **83.042 seconds** with four lanes. More aggressive lane counts were intentionally
not selected: this is the conservative setting intended to behave well on mobile devices and
lower-memory browsers.

## 2. Exact recipe-overlay deduplication

The exporter fingerprints each rendered recipe overlay from its visible pixels and position, then
reuses a previously written file only when both the layout and 128-bit content fingerprint match.
The fingerprint is computed while the image is already in memory, so no second image decode is
needed.

An offline scan of the old export found 8,332 duplicate overlay files representing 25.915 MiB
(4.207% of the full archive). The new fresh export deduplicated 8,279 files. It therefore captures
nearly all of the safe opportunity without treating merely similar recipes as identical.

## Exporter cost

The exact-image checks and delta comparison add a small amount of exporter work:

| Run | beta.28 exporter | beta.29 exporter | Difference |
| --- | ---: | ---: | ---: |
| Fresh export | 73.088 s | 73.863 s | +1.1% |
| Re-export | 66.285 s | 67.469 s | +1.8% |

Fresh whole-process wall time changed from 171.210 to 173.771 seconds (+1.5%), while measured CPU
time changed from 612.452 to 616.418 seconds (+0.6%). Re-export whole-process wall time changed
from 169.946 to 171.652 seconds (+1.0%). Peak resident-memory readings varied too much between JVM
runs to support a reliable memory claim.

The fresh beta.29 export contained 41,803 items, 117,136 recipes, 265 categories, and 31 recorded
recipe failures. Failures remain non-fatal and are included in the export report.

## Delta update benchmark

The first import remains a complete, standalone export. Later successful exports also produce
`jei-exports-update.zip` when the previous snapshot is compatible and the delta is at most 80% of
the full result size.

The measured Architect's Exodus update contained:

- 1,571 changed files
- 1,423 deleted files
- 149,850 unchanged files
- 151,421 result files
- 104,662,080 changed, uncompressed bytes out of 826,460,597 result bytes
- a 15,550,106-byte delta ZIP instead of the 644,054,397-byte full ZIP

The browser verifies the base publication ID, every changed-file SHA-256, deletions, file counts,
and the exact result manifest ID. It then materializes a new standalone local pack and removes the
old base only after the new catalog entry commits, so updates do not create an archive chain.

On the test Mac, applying and flattening the delta locally took about **108.7 seconds**, compared
with approximately **94.8 seconds** for the patched full-archive import. That local step is slower
because it must copy almost 150,000 unchanged Cache Storage responses to preserve a standalone
result. The delta still avoids selecting, transferring, and parsing roughly 599 MiB on every
regular update. Pack readiness is now shown as soon as the save commits; optional GitHub error
reporting continues in the background.

## Archive compatibility finding

Finder-created ZIPs can include AppleDouble metadata and can also contain compressed byte sequences
that resemble ZIP data-descriptor signatures. The importer now ignores `__MACOSX`, `.DS_Store`, and
`._*` entries and carries a focused streaming-parser compatibility patch. The exact 614.218 MiB
archive streamed through all 151,911 entries with zero errors after the fix.

A metadata-heavy Finder ZIP measured 691,463,563 bytes and 303,821 entries, including 151,910
AppleDouble entries. Packaging without resource forks measured 644,054,397 bytes, saving about
47.4 MB and avoiding half of the entries before upload.

## Recommendation

Use beta.29's full ZIP for the first import and recovery, then use `jei-exports-update.zip` for
routine updates. The small exporter overhead is outweighed by the 97.59% update-size reduction and
the 15.4% improvement to full-import browser writes.
