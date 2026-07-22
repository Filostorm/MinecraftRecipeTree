'use client';

import {useEffect, useMemo, useState} from 'react';
import {
  EXPORTER_RELEASE_MANIFEST_PATH,
  type ExporterReleaseManifest,
  requireExporterReleaseManifest,
} from '../../src/data/exporterReleases';
import styles from './publish.module.css';

const MAX_RELEASE_MANIFEST_BYTES = 128 * 1024;
const ALL_VERSIONS = 'all';

type ManifestState =
  | {status: 'loading'}
  | {status: 'ready'; manifest: ExporterReleaseManifest}
  | {status: 'error'; message: string};

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KiB', 'MiB', 'GiB'];
  let value = bytes / 1024;
  let unit = units[0];
  for (let index = 1; index < units.length && value >= 1024; index += 1) {
    value /= 1024;
    unit = units[index];
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ${unit}`;
}

async function fetchReleaseManifest(signal: AbortSignal): Promise<ExporterReleaseManifest> {
  const response = await fetch(EXPORTER_RELEASE_MANIFEST_PATH, {
    cache: 'no-store',
    headers: {accept: 'application/json'},
    signal,
  });
  if (!response.ok) {
    throw new Error(`Release catalog request failed with HTTP ${response.status}.`);
  }
  const contentLength = response.headers.get('content-length');
  if (contentLength !== null) {
    const declaredBytes = Number(contentLength);
    if (!Number.isSafeInteger(declaredBytes) || declaredBytes < 0) {
      throw new Error('Release catalog returned an invalid Content-Length header.');
    }
    if (declaredBytes > MAX_RELEASE_MANIFEST_BYTES) {
      throw new Error(`Release catalog exceeds the ${MAX_RELEASE_MANIFEST_BYTES}-byte limit.`);
    }
  }
  const body = await response.text();
  if (new TextEncoder().encode(body).byteLength > MAX_RELEASE_MANIFEST_BYTES) {
    throw new Error(`Release catalog exceeds the ${MAX_RELEASE_MANIFEST_BYTES}-byte limit.`);
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(body);
  } catch (error) {
    throw new Error(`Release catalog is not valid JSON: ${errorMessage(error)}`);
  }
  return requireExporterReleaseManifest(parsed);
}

export default function PublishPage() {
  const [manifestState, setManifestState] = useState<ManifestState>({status: 'loading'});
  const [selectedVersion, setSelectedVersion] = useState(ALL_VERSIONS);
  const [requestRevision, setRequestRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setManifestState({status: 'loading'});
    fetchReleaseManifest(controller.signal)
      .then(manifest => setManifestState({status: 'ready', manifest}))
      .catch(error => {
        if (controller.signal.aborted) return;
        const message = errorMessage(error);
        console.error('Exporter release manifest could not be loaded or validated.', error);
        setManifestState({status: 'error', message});
      });
    return () => controller.abort();
  }, [requestRevision]);

  const versions = useMemo(() => {
    if (manifestState.status !== 'ready') return [];
    return [...new Set(manifestState.manifest.releases.map(release => release.minecraftVersion))]
      .sort((left, right) => right.localeCompare(left, undefined, {numeric: true}));
  }, [manifestState]);

  const visibleReleases = useMemo(() => {
    if (manifestState.status !== 'ready') return [];
    return selectedVersion === ALL_VERSIONS
      ? manifestState.manifest.releases
      : manifestState.manifest.releases.filter(
          release => release.minecraftVersion === selectedVersion,
        );
  }, [manifestState, selectedVersion]);

  return (
    <main className={styles.page}>
      <div className={styles.shell}>
        <header className={styles.header}>
          <a className={styles.brand} href="/" aria-label="Minecraft Recipe Tree home">
            <span aria-hidden="true">⛏</span> Recipe Tree
          </a>
          <a className={styles.viewerLink} href="/">
            Open viewer
          </a>
        </header>

        <section className={styles.hero} aria-labelledby="publish-title">
          <p className={styles.eyebrow}>MODPACK IMPORT WORKFLOW</p>
          <h1 id="publish-title">Import a modpack into Recipe Tree.</h1>
          <p className={styles.heroCopy}>
            Create an integrity-checked snapshot with the exporter that exactly matches your
            Minecraft and recipe-viewer versions, then submit it for validated import and a
            durable Recipe Tree link.
          </p>
          <div className={styles.heroActions}>
            <a className={styles.primaryAction} href="#downloads">
              Identify an exporter
            </a>
            <a className={styles.secondaryAction} href="#workflow">
              Read the full workflow
            </a>
          </div>
        </section>

        <aside className={styles.operatorNotice} aria-labelledby="operator-notice-title">
          <div className={styles.noticeMarker} aria-hidden="true">CURRENT IMPORT MODEL</div>
          <div>
            <h2 id="operator-notice-title">Durable imports are operator-mediated</h2>
            <p>
              Contributors can download, export, validate, and prepare a pack themselves. A site
              operator performs the final authenticated import; production tokens never belong on
              contributor machines. Fully self-service publishing requires authenticated,
              quota-bound, short-lived submission sessions and is not available yet.
            </p>
          </div>
        </aside>

        <section className={styles.section} id="downloads" aria-labelledby="downloads-title">
          <div className={styles.sectionHeading}>
            <div>
              <p className={styles.stepLabel}>STEP 1</p>
              <h2 id="downloads-title">Identify the exact externally distributed exporter</h2>
              <p>
                Minecraft, mod loader, and JEI/REI/HEI/NEI APIs are version-specific. A near match
                is not compatible. The records below are verification metadata, not hosted
                downloads.
              </p>
            </div>
            {manifestState.status === 'ready' && (
              <div className={styles.filter}>
                <label htmlFor="minecraft-version">Minecraft version</label>
                <select
                  id="minecraft-version"
                  value={selectedVersion}
                  onChange={event => setSelectedVersion(event.target.value)}>
                  <option value={ALL_VERSIONS}>All supported versions</option>
                  {versions.map(version => (
                    <option value={version} key={version}>
                      Minecraft {version}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>

          {manifestState.status === 'loading' && (
            <div className={styles.statusPanel} role="status" aria-live="polite">
              <span className={styles.spinner} aria-hidden="true" />
              Loading the checksummed release catalog…
            </div>
          )}

          {manifestState.status === 'error' && (
            <div className={styles.errorPanel} role="alert">
              <div>
                <strong>Exporter release metadata is unavailable.</strong>
                <p>{manifestState.message}</p>
                <p>No unverified fallback download has been substituted.</p>
              </div>
              <button type="button" onClick={() => setRequestRevision(revision => revision + 1)}>
                Retry catalog
              </button>
            </div>
          )}

          {manifestState.status === 'ready' && (
            <>
              {visibleReleases.length === 0 ? (
                <div className={styles.statusPanel} role="status">
                  No published exporter matches Minecraft {selectedVersion}. Choose another
                  version from the filter.
                </div>
              ) : (
                <div className={styles.releaseGrid}>
                  {visibleReleases.map(release => (
                    <article className={styles.releaseCard} key={release.id}>
                      <div className={styles.releaseTopline}>
                        <span>Minecraft {release.minecraftVersion}</span>
                        <span>Exporter {release.version}</span>
                      </div>
                      <h3>{release.recipeViewer}</h3>
                      <p className={styles.compatibility}>{release.compatibility}</p>
                      <dl className={styles.releaseFacts}>
                        <div>
                          <dt>Loader</dt>
                          <dd>{release.loader}</dd>
                        </div>
                        <div>
                          <dt>Size</dt>
                          <dd>{formatBytes(release.bytes)}</dd>
                        </div>
                        <div>
                          <dt>Quality profile</dt>
                          <dd>{release.qualityProfiles.join(', ')}</dd>
                        </div>
                        <div>
                          <dt>Release ID</dt>
                          <dd><code>{release.id}</code></dd>
                        </div>
                      </dl>
                      <div className={styles.downloadButton} aria-label="Exporter distributed off-site">
                        External distribution only · {release.filename}
                      </div>
                      <div className={styles.checksum}>
                        <span>SHA-256</span>
                        <code>{release.sha256}</code>
                      </div>
                    </article>
                  ))}
                </div>
              )}
              <p className={styles.catalogStamp}>
                Release index generated{' '}
                <time dateTime={manifestState.manifest.generatedAt}>
                  {new Date(manifestState.manifest.generatedAt).toLocaleString()}
                </time>
                . Every filename and checksum above passed the viewer&apos;s exact release contract.
                Exporter JARs are intentionally not hosted by Recipe Tree.
              </p>
            </>
          )}
        </section>

        <section className={styles.section} id="workflow" aria-labelledby="workflow-title">
          <div className={styles.sectionHeading}>
            <div>
              <p className={styles.stepLabel}>STEPS 2–6</p>
              <h2 id="workflow-title">Install, export, validate, and import</h2>
              <p>Keep the raw export unchanged; publication preparation works from a staged copy.</p>
            </div>
          </div>

          <ol className={styles.workflow}>
            <li>
              <span className={styles.number}>2</span>
              <div>
                <h3>Verify and install the JAR</h3>
                <p>
                  Obtain the JAR from the operator&apos;s external distribution channel, compare it
                  against the SHA-256 above, close Minecraft, and put
                  the JAR in the target instance&apos;s <code>mods</code> directory—not the
                  launcher&apos;s global directory. Confirm the matching recipe viewer is already
                  installed.
                </p>
                <div className={styles.commandGrid}>
                  <pre><code>shasum -a 256 &quot;/path/to/exporter.jar&quot;</code></pre>
                  <pre><code>Get-FileHash &quot;C:\path\to\exporter.jar&quot; -Algorithm SHA256</code></pre>
                </div>
              </div>
            </li>
            <li>
              <span className={styles.number}>3</span>
              <div>
                <h3>Export inside a disposable single-player world</h3>
                <p>
                  A loaded world lets the exporter capture world-dependent mob drops, block drops,
                  staged recipes, and server-backed integrations. For Minecraft 1.20.1 + JEI 15,
                  run <code>/jeiexport all</code>. Older releases consume their exact request file
                  from the instance root:
                </p>
                <ul>
                  <li><code>reiexport-request.json</code> for 1.18.2</li>
                  <li><code>jeiexport-request.json</code> for 1.12.2</li>
                  <li><code>neiexport-request.json</code> for the pinned GTNH 1.7.10 release</li>
                </ul>
                <p>
                  The Multiblock Madness profiles require native <code>iconScale: 1</code> item
                  canvases and complete <code>recipeScale: 2</code> layouts. The 1.18.2 request also
                  requires the exact <code>multiblock-madness-2-1.18.2</code> profile,
                  {' '}<code>failOnError: true</code>, and a bounded <code>pngQueueCapacity</code> from
                  8 through 128. The 1.12.2 request has no <code>profile</code> field; select its
                  listed profile during validation and publication preparation.
                </p>
                <p>
                  Omit <code>qualitySample</code> from a full request. That field identifies a
                  diagnostic mini export, and the production publisher rejects it rather than
                  exposing an incomplete recipe corpus.
                </p>
                <p>
                  Wait for the success message or completion marker. Never copy an export while
                  Minecraft is still writing it.
                </p>
              </div>
            </li>
            <li>
              <span className={styles.number}>4</span>
              <div>
                <h3>Validate identity and completeness</h3>
                <p>
                  Open <code>manifest.json</code>. Require <code>aborted: false</code>, the correct
                  {' '}<code>pack.name</code> and <code>pack.version</code>, and the expected Minecraft
                  version. Review <code>failures.json</code>; do not ignore or delete diagnostics to
                  make an export appear complete.
                </p>
                <p>
                  The strict profile checks item/fluid/gas amounts, tag and OreDictionary
                  alternatives, non-consumed catalysts, byproducts, custom ingredient renderers,
                  recipe previews, mobs, and drops. Unsupported or ambiguous semantics must be
                  reported and fixed—they are never silently converted to quantity 1.
                </p>
              </div>
            </li>
            <li>
              <span className={styles.number}>5</span>
              <div>
                <h3>Have the operator prepare an immutable publication workspace</h3>
                <p>
                  The current fail-closed preparer requires the operator&apos;s local acceptance receipt
                  and configured source JAR. Use the quality profile and release ID shown on the
                  exporter card, and pick the stable isolated slug assigned to this pack.
                </p>
                <pre><code>{`npm install
npm run publish:modpack -- prepare \\
  --source "/absolute/path/to/export" \\
  --workspace "/absolute/path/to/new-workspace" \\
  --profile "<quality-profile-from-download-card>" \\
  --release "<release-id-from-download-card>" \\
  --slug "stable-pack-slug"`}</code></pre>
                <p>
                  Preparation requires the operator&apos;s exact accepted release receipt, hashes the
                  staged raw snapshot against it, validates cross-references, computes immutable
                  content IDs, and writes the receipt identity into <code>publication-plan.json</code>
                  last. It never substitutes a different profile, release, or receipt.
                  If it fails, keep the workspace for diagnosis and prepare a new one after fixing
                  the exporter or source data.
                </p>
              </div>
            </li>
            <li>
              <span className={styles.number}>6</span>
              <div>
                <h3>Submit the completed raw export for import</h3>
                <p>
                  Transfer the completed raw export and its completion evidence through the
                  operator&apos;s approved channel. The operator creates the acceptance-bound prepared
                  workspace. Do not request, store, or transmit production upload tokens or provide
                  an untrusted replacement acceptance receipt.
                </p>
                <p>
                  The operator authenticates both exact ingestion targets, resumes immutable
                  uploads, verifies public hashes and byte lengths, and then atomically points the
                  stable pack channel at the new content. After activation, the pack appears in the
                  viewer switcher and receives a shareable <code>?pack=stable-pack-slug</code> URL.
                </p>
              </div>
            </li>
          </ol>
        </section>

        <section className={styles.securitySection} aria-labelledby="self-service-title">
          <p className={styles.stepLabel}>WHY THE HANDOFF EXISTS</p>
          <h2 id="self-service-title">Safe self-service needs bounded publication sessions</h2>
          <p>
            Large modpacks can produce hundreds of thousands of files and multiple GiB of data.
            Anonymous browser uploads would expose storage and egress denial-of-service, channel
            spam, abandoned objects, and inconsistent mobile/browser hashing behavior.
          </p>
          <p>
            The parallel self-service design is sign-in plus a short-lived credential bound to one
            declared object inventory, with byte/object quotas, ownership, rate limits, server-side
            hash verification, expiration, moderation state, and orphan cleanup. That adds account
            and lifecycle complexity, but removes the operator handoff without placing durable
            production credentials on user devices.
          </p>
        </section>

        <footer className={styles.footer}>
          <a href="/">← Return to Recipe Tree</a>
          <span>Imported data stays immutable; stable pack channels make updates easy to share.</span>
        </footer>
      </div>
    </main>
  );
}
