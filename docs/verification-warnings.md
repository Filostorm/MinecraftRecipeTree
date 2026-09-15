# Startup verification warnings

## Hydration

Web catalog and theme providers begin with the same state as the server. Saved browser
preferences and the catalog are restored after hydration; native catalog startup stays cached.
The fresh catalog request still runs, and a late cache read cannot replace its result.
`src/ui/hydrationStartup.test.mjs` compares actual provider markup with and without saved state.

## Dataset icon omissions

The public immutable catalog audit found the following counts on 2026-09-15:

| Pack | Catalog entries | No icon URL | Explanation |
| --- | ---: | ---: | --- |
| MeatballCraft | 196,920 | 44 | 43 documented transparent native renders; 1 EMC entry uses the transmutation table icon |
| GT New Horizons | 143,882 | 0 | None |
| Multiblock Madness | 88,262 | 31 | Documented transparent native renders |
| Multiblock Madness 2 | 68,551 | 16 | Documented transparent native renders |

All omissions have matching exporter evidence. This does not imply that every referenced image
URL has been fetched: actual image-load failures retain their separate bounded failure reporting.
No publication or asset was modified to obtain these results.

Reproduce the read-only audit with:

```sh
node scripts/audit-published-item-icons.mjs https://minecraftrecipetree.craftsmannsoftware.com /tmp/mrt-icon-audit.json
```

The client requests exporter diagnostics once for an iconless catalog, outside its critical
loading path. Accounted-for omissions are informational; unexplained omissions or unavailable
diagnostics remain warnings. Export validation rejects unexplained omissions before publication.

## Analytics

The direct beta Worker and local builds intentionally disable Signal surface tracking. The
canonical production hostname receives Signal through its existing router chain and keeps it
enabled. Disabled builds log one informational message without waiting for a nonexistent tracker.

Enabled builds retain one readiness listener, including after a delayed-start warning. Only active
surfaces participate; modal surfaces take priority, closing a modal restores the current screen,
and identical updates are deduplicated. Missing or broken tracking is warned once per page.

## Release checks

Verify a fresh tab and a reload with saved pack/tab/theme settings. Expect no recoverable
hydration mismatch, no unexplained icon warnings, and one intentional analytics-disabled message
on beta. Check a hashed application asset and the beta dataset-origin response header. Production
promotion requires separate approval and a production check with Signal available and blocked.
