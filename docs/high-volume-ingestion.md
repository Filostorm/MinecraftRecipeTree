# High-volume modpack ingestion

The production dataset path is deliberately split into two tiers:

1. The deployed operator path accepts only already-prepared, content-addressed publications. It is
   resumable, verifies every object by SHA-256 and byte length, commits manifests last, and changes
   the public D1 channel pointer only after both the core and preview publications verify.
2. A self-service contributor path must issue short-lived, single-submission capabilities. It must
   not expose the operator bearer tokens or proxy multi-gigabyte raw exports through the app Worker.

## Self-service topology

The browser remains part of the same web app, but bulk bytes travel directly to R2:

```text
signed-in contributor
  -> POST submission inventory (hashes, lengths, pack identity)
  -> D1 submission row + quota/rate-limit decision
  -> short-lived capability bound to that exact inventory
  -> direct R2 multipart uploads
  -> finalize request
  -> Cloudflare Queue validation job
  -> isolated validator/packer worker
  -> moderation state
  -> immutable core + preview commit markers
  -> atomic D1 channel activation
```

The capability must bind the account, submission ID, object keys, per-object hashes and lengths,
aggregate bytes, aggregate object count, expiry, and maximum multipart count. Adding an object or
changing one byte requires a new capability. Finalization is idempotent and uses a D1 state-machine
compare-and-swap: `draft -> uploading -> validating -> review -> published` or an explicit terminal
failure state. No state transition silently retries a different artifact.

## Capacity and security policy

- Upload directly to R2 with multipart requests; the Worker issues scoped authorization and handles
  metadata only. Proxying pack bytes through the Worker is simpler, but consumes Worker CPU,
  subrequests, and request-duration budget and becomes the central throughput bottleneck.
- Enforce account, IP, submission, object-count, and aggregate-byte quotas before issuing upload
  authorization. Reserve quota transactionally in D1 and release it during expiry cleanup.
- Use a queue for validation backpressure. Queue concurrency is independent from browser upload
  concurrency, so a traffic spike increases wait time instead of exhausting the interactive app.
- Deduplicate only after SHA-256 and byte-length verification. Content-addressed objects are
  immutable; an existing matching object is reused and a conflicting object is rejected.
- Scan archive paths before extraction, reject symlinks/special files, cap decompressed size and file
  count, and validate exporter provenance and quality profile in an isolated worker.
- Delete expired multipart uploads and uncommitted staging prefixes with a scheduled orphan sweeper.
  Published objects remain rollback candidates and are governed by a separate retention policy.
- Keep a human-review state for public channel creation. Updating an owned channel can use an exact
  previous-publication compare-and-swap after automated validation passes.

## Current operating envelope

The shipped operator uploader uses concurrency `8`. Each active request is bounded to roughly
8 MiB, so the normal upload working set is approximately 64 MiB plus Node/runtime overhead. Tests
also exercise concurrency cancellation: after the first failure it stops dequeuing, drains active
workers, logs secondary failures, and preserves the primary error. Concurrency `32` raises the
request working set toward 256 MiB without demonstrating a useful GTNH throughput improvement.

This operator path is appropriate for the initial four-pack launch. Before enabling a public
"Upload modpack" button, provision the Queue consumer and scoped capability issuer described above;
anonymous raw upload and shared production bearer tokens must remain disabled.
