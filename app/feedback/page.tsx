'use client';

import {FormEvent, useState} from 'react';
import styles from './feedback.module.css';

interface FeedbackReport {
  id: string;
  kind: 'bug' | 'feedback' | 'feature';
  title: string;
  message: string;
  contact: string | null;
  packSlug: string | null;
  packName: string | null;
  page: string | null;
  userAgent: string | null;
  createdAt: number;
}

type InboxState =
  | {status: 'locked'}
  | {status: 'loading'}
  | {status: 'ready'; reports: FeedbackReport[]}
  | {status: 'error'; message: string};

function formatDate(value: number): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

export default function FeedbackPage() {
  const [token, setToken] = useState('');
  const [inbox, setInbox] = useState<InboxState>({status: 'locked'});

  const loadReports = async (event?: FormEvent) => {
    event?.preventDefault();
    if (!token || inbox.status === 'loading') return;
    setInbox({status: 'loading'});
    try {
      const response = await fetch('/api/feedback', {
        cache: 'no-store',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
      });
      const payload = (await response.json()) as {
        reports?: FeedbackReport[];
        error?: string;
      };
      if (!response.ok || !Array.isArray(payload.reports)) {
        throw new Error(payload.error || `Inbox request failed with status ${response.status}.`);
      }
      setInbox({status: 'ready', reports: payload.reports});
    } catch (error) {
      console.error('Feedback inbox could not be loaded.', error);
      setInbox({
        status: 'error',
        message: error instanceof Error ? error.message : 'Feedback inbox could not be loaded.',
      });
    }
  };

  const lockInbox = () => {
    setToken('');
    setInbox({status: 'locked'});
  };

  return (
    <main className={styles.page}>
      <div className={styles.shell}>
        <header className={styles.header}>
          <a className={styles.brand} href="/">
            <span aria-hidden="true">⛏</span> Recipe Tree
          </a>
          {inbox.status === 'ready' ? (
            <button className={styles.secondaryButton} type="button" onClick={lockInbox}>
              Lock inbox
            </button>
          ) : (
            <a className={styles.secondaryButton} href="/">
              Open viewer
            </a>
          )}
        </header>

        <section className={styles.intro}>
          <p className={styles.eyebrow}>OPERATOR TOOL</p>
          <h1>Feedback inbox</h1>
          <p>Review bug reports and feedback submitted through the Recipe Tree app.</p>
        </section>

        {inbox.status !== 'ready' ? (
          <form className={styles.unlockPanel} onSubmit={event => void loadReports(event)}>
            <label htmlFor="feedback-token">Feedback admin token</label>
            <div className={styles.unlockRow}>
              <input
                id="feedback-token"
                type="password"
                value={token}
                onChange={event => setToken(event.target.value)}
                autoComplete="off"
                autoCapitalize="none"
                spellCheck={false}
                minLength={32}
                placeholder="Enter the server-side access token"
              />
              <button type="submit" disabled={!token || inbox.status === 'loading'}>
                {inbox.status === 'loading' ? 'Opening…' : 'Open inbox'}
              </button>
            </div>
            <p>The token remains in this tab’s memory and is never placed in the URL or storage.</p>
            {inbox.status === 'error' ? (
              <div className={styles.error} role="alert">
                {inbox.message}
              </div>
            ) : null}
          </form>
        ) : (
          <section aria-live="polite">
            <div className={styles.toolbar}>
              <p>
                {inbox.reports.length} {inbox.reports.length === 1 ? 'report' : 'reports'}
              </p>
              <button
                className={styles.secondaryButton}
                type="button"
                onClick={() => void loadReports()}>
                Refresh
              </button>
            </div>
            {inbox.reports.length === 0 ? (
              <div className={styles.empty}>No feedback has been submitted yet.</div>
            ) : (
              <div className={styles.reportList}>
                {inbox.reports.map(report => (
                  <article className={styles.report} key={report.id}>
                    <div className={styles.reportHeading}>
                      <span className={report.kind === 'bug' ? styles.bug : styles.feature}>
                        {report.kind === 'bug' ? 'Bug report' : 'Feedback'}
                      </span>
                      <time dateTime={new Date(report.createdAt).toISOString()}>
                        {formatDate(report.createdAt)}
                      </time>
                    </div>
                    <h2 className={styles.reportTitle}>
                      {report.title || 'Legacy report without a title'}
                    </h2>
                    <p className={styles.message}>{report.message}</p>
                    <dl className={styles.metadata}>
                      <div>
                        <dt>Modpack</dt>
                        <dd>{report.packName || report.packSlug || 'Not supplied'}</dd>
                      </div>
                      <div>
                        <dt>Page</dt>
                        <dd>
                          {report.page ? <a href={report.page}>{report.page}</a> : 'Not supplied'}
                        </dd>
                      </div>
                      <div>
                        <dt>Contact</dt>
                        <dd>
                          {report.contact ? (
                            <a href={`mailto:${report.contact}`}>{report.contact}</a>
                          ) : (
                            'Not supplied'
                          )}
                        </dd>
                      </div>
                      <div>
                        <dt>Browser</dt>
                        <dd>{report.userAgent || 'Not supplied'}</dd>
                      </div>
                    </dl>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}
      </div>
    </main>
  );
}
