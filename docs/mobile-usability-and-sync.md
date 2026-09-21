# Mobile usability and sync

## Confirmed scope

- Browse ranking uses community-wide views, scoped to a pack, not personal history.
- Sync favorites, trees, history and preferences. Keep downloaded packs and display sizing local.
- Preserve the existing Recipe Tree visual style and mobile 44-point controls.

## Implemented in the mobile-usability branch

- Compact graph settings; collapsed UI settings; non-interactive previews do not capture scrolling.
- Email/password account creation and sign-in using the existing Supabase session.
- History deletion scoped to the exact pack publication and recipe direction.
- Shared outline navigation/gear icons, matching account-button height, floating graph info.
- A starter crafting-table tree only when no saved tree exists (legacy workbench/furnace if present).
- Persistent, bounded viewed recipe documents and preview artwork on native. Full packs remain opt-in.
- Pack icons in Downloads and an optional pack-selection download that survives pack navigation.
- Community browse ranking: indexed per-pack totals, five-minute edge/browser cache, batches of at most
  20 keys, once-per-device-per-day deduplication, and a six-batches/minute edge IP limiter.
  Counts begin when released; no historical popularity is fabricated from favorite counts.

Deployment must apply `drizzle/0011_item_views.sql` to beta before feature verification. Production
needs separate approval. Ranking failure is visible; unconfigured rate limiting refuses writes.
Popularity is approximate anonymous analytics, not a tamper-proof leaderboard. No account IDs or
raw IP addresses are saved in the popularity table. Client deduplication is not fraud prevention.

## Automatic sync — remaining implementation

Existing favorites already use authenticated server storage. Do not copy community favorites into
personal favorites. Extend account sync with entity records rather than uploading the complete
device storage or replacing a single giant snapshot:

1. Account-scoped local stores and an outbox. Bind queued changes to the originating user ID;
   never send user A's queue after switching to user B. Leave guest data separate until claimed.
2. Trees keyed by stable build ID plus pack/publication identity. Save immutable revisions and use
   optimistic concurrency; preserve both versions when devices independently edit the same tree.
3. History keyed by pack/publication, item, recipe and direction. Sync explicit deletion tombstones
   so an offline device cannot resurrect removed entries. Merge by server revision, not device clocks.
4. An explicit preference allowlist: theme/font, graph behavior, recipe stages and animation settings.
   Exclude credentials, device download paths, UI/content zoom, viewport position and diagnostics.
5. Authenticated, bounded batch endpoint with indexed `(user_id, revision)` reads and per-record
   revision checks. Pull on sign-in and foreground; debounce local writes. No perpetual polling.
6. Show syncing/offline/error/conflict state in Account with a retry action. Keep local work usable
   offline. Account deletion must delete sync records as well as favorites.

Before release, exercise two devices: offline edits, simultaneous tree edits, history deletion then
reconnection, sign-out/account switching during an upload, expired sessions, and publication changes.
Do not advertise trees/history/preferences as synced until these checks pass.

## Verification and release status

- TypeScript, 577 data regression tests, 38 iOS tests and 22 focused usability/ranking tests pass.
- Cloudflare beta build and iOS export complete successfully.
- Built Worker checked locally: fresh onboarding, starter Crafting Table, and floating graph guide.
- Native keyboard/scroll/download behavior still needs a physical-device run. No new account or
  confirmation email was created during testing.
- No beta or production deployment was made for this batch. Apply the popularity migration before
  beta verification. The local preview intentionally reports ranking unavailable without that table.
- The Vinext development server failed in its RSC runner before rendering; the built Worker preview
  worked. This development-only error remains unresolved.
