'use client';

import {useState} from 'react';
import {PackUploadDropzone} from './PackUploadDropzone';
import styles from './publish.module.css';

const VERSION_GUIDES = [
  {
    version: '1.21.1',
    command: '/jeiexport all',
  },
  {
    version: '1.20.1',
    command: '/jeiexport all',
  },
  {
    version: '1.12.2',
    command: '/jeiexport',
  },
] as const;

type SupportedVersion = (typeof VERSION_GUIDES)[number]['version'];

export default function PublishPage() {
  const [selectedVersion, setSelectedVersion] = useState<SupportedVersion>('1.21.1');
  const guide = VERSION_GUIDES.find(entry => entry.version === selectedVersion)
    ?? VERSION_GUIDES[0];

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
          <p className={styles.eyebrow}>SHARE YOUR MODPACK</p>
          <h1 id="publish-title">Bring your pack to Recipe Tree.</h1>
          <p className={styles.heroCopy}>
            Follow the short guide for your Minecraft version, then check the ZIP before you
            share it.
          </p>
          <div className={styles.heroActions}>
            <a className={styles.primaryAction} href="#instructions">
              Get started
            </a>
            <a className={styles.secondaryAction} href="#upload">
              Check your ZIP
            </a>
          </div>
        </section>

        <section className={styles.section} id="instructions" aria-labelledby="instructions-title">
          <div className={styles.sectionHeading}>
            <div>
              <p className={styles.stepLabel}>YOUR GUIDE</p>
              <h2 id="instructions-title">Choose your Minecraft version</h2>
              <p>We&apos;ll only show the steps you need for that version.</p>
            </div>
            <div className={styles.filter}>
              <label htmlFor="minecraft-version">Minecraft version</label>
              <select
                id="minecraft-version"
                value={selectedVersion}
                onChange={event => setSelectedVersion(event.target.value as SupportedVersion)}>
                {VERSION_GUIDES.map(entry => (
                  <option value={entry.version} key={entry.version}>
                    Minecraft {entry.version}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <article className={styles.guideCard}>
            <div className={styles.guideTopline}>
              <span>Minecraft {guide.version}</span>
              <span>4 easy steps</span>
            </div>
            <h3>Export your modpack</h3>
            <p className={styles.curseForgeNotice}>
              The exporter will be available through CurseForge. There are no mod downloads on
              this site.
            </p>
            <ol className={styles.guideSteps}>
              <li>
                <span className={styles.number}>1</span>
                <div>
                  <h4>Add the exporter</h4>
                  <p>In CurseForge, add Recipe Tree Exporter to the modpack you want to share.</p>
                </div>
              </li>
              <li>
                <span className={styles.number}>2</span>
                <div>
                  <h4>Open your pack</h4>
                  <p>Start the pack and enter any single-player world.</p>
                </div>
              </li>
              <li>
                <span className={styles.number}>3</span>
                <div>
                  <h4>Run the export</h4>
                  <p>Open chat and enter:</p>
                  <code className={styles.command}>{guide.command}</code>
                </div>
              </li>
              <li>
                <span className={styles.number}>4</span>
                <div>
                  <h4>Make your ZIP</h4>
                  <p>
                    Wait for the export to finish. In CurseForge, choose Open Folder, find
                    the <code>jei-exports</code> folder, and zip it.
                  </p>
                </div>
              </li>
            </ol>
          </article>
        </section>

        <section className={styles.section} id="upload" aria-labelledby="upload-title">
          <div className={styles.sectionHeading}>
            <div>
              <p className={styles.stepLabel}>CHECK YOUR FILE</p>
              <h2 id="upload-title">Add your exporter ZIP</h2>
              <p>Drag it here or tap to choose it. We&apos;ll tell you if it&apos;s ready.</p>
            </div>
          </div>
          <PackUploadDropzone />
        </section>

        <footer className={styles.footer}>
          <a href="/">← Return to Recipe Tree</a>
          <span>Your ZIP stays on your device while it is checked.</span>
        </footer>
      </div>
    </main>
  );
}
