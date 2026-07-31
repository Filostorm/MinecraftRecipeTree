import {PackUploadDropzone} from './PackUploadDropzone';
import styles from './publish.module.css';

export default function PublishPage() {
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
          <p className={styles.eyebrow}>ADD YOUR MODPACK</p>
          <h1 id="publish-title">Bring your pack to Recipe Tree.</h1>
          <p className={styles.heroCopy}>
            Follow five short steps to export your pack and open it in the viewer.
          </p>
          <div className={styles.heroActions}>
            <a className={styles.primaryAction} href="#instructions">
              Get started
            </a>
            <a className={styles.secondaryAction} href="#upload">
              Add your ZIP
            </a>
          </div>
        </section>

        <section className={styles.section} id="instructions" aria-labelledby="instructions-title">
          <div className={styles.sectionHeading}>
            <div>
              <p className={styles.stepLabel}>YOUR GUIDE</p>
              <h2 id="instructions-title">Export your modpack</h2>
              <p>These steps work for Minecraft 1.12.2, 1.20.1, and 1.21.1.</p>
            </div>
          </div>

          <article className={styles.guideCard}>
            <div className={styles.guideTopline}>
              <span>CurseForge</span>
              <span>5 easy steps</span>
            </div>
            <h3>Get your pack ready</h3>
            <p className={styles.curseForgeNotice}>
              Recipe Tree Exporter will be available through CurseForge. There are no mod
              downloads on this site.
            </p>
            <ol className={styles.guideSteps}>
              <li>
                <span className={styles.number}>1</span>
                <div>
                  <h4>Add the exporter</h4>
                  <p>In CurseForge, add Recipe Tree Exporter to the modpack you want to use.</p>
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
                  <p>Open chat and use the command for your Minecraft version:</p>
                  <div className={styles.commands}>
                    <span>
                      <strong>1.20.1 or 1.21.1</strong>
                      <code className={styles.command}>/jeiexport all</code>
                    </span>
                    <span>
                      <strong>1.12.2</strong>
                      <code className={styles.command}>/jeiexport</code>
                    </span>
                  </div>
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
              <li className={styles.uploadStep} id="upload">
                <span className={styles.number}>5</span>
                <div className={styles.uploadStepContent}>
                  <h4>Add your exporter ZIP</h4>
                  <p>Drag it into the box or tap the box to choose it.</p>
                  <PackUploadDropzone />
                </div>
              </li>
            </ol>
          </article>
        </section>

        <footer className={styles.footer}>
          <a href="/">← Return to Recipe Tree</a>
        </footer>
      </div>
    </main>
  );
}
