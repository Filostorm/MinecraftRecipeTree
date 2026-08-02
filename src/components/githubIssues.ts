export type GitHubIssueKind = 'bug' | 'feature';

export const GITHUB_REPOSITORY = 'Filostorm/MinecraftRecipeTree';
export const GITHUB_ISSUES_URL = `https://github.com/${GITHUB_REPOSITORY}/issues/new`;

interface GitHubIssueContext {
  kind: GitHubIssueKind;
  packSlug: string;
  packName: string;
  page: string;
  browser: string;
}

export function buildGitHubIssueUrl({
  kind,
  packSlug,
  packName,
  page,
  browser,
}: GitHubIssueContext): string {
  const isBug = kind === 'bug';
  const url = new URL(GITHUB_ISSUES_URL);
  url.searchParams.set('labels', isBug ? 'bug' : 'enhancement');
  url.searchParams.set('title', isBug ? '[Bug] ' : '[Feature] ');
  url.searchParams.set(
    'body',
    [
      isBug ? '## What happened?' : '## Proposed feature',
      isBug
        ? 'Describe the problem clearly.'
        : 'Describe the improvement you would like to see.',
      '',
      isBug ? '## Steps to reproduce' : '## Why would this help?',
      isBug ? '1. ' : 'Explain the use case and expected benefit.',
      '',
      isBug ? '## Expected behavior' : '## Suggested behavior',
      isBug
        ? 'Describe what you expected to happen.'
        : 'Describe how the feature should work.',
      '',
      '## Recipe Tree context',
      `- Modpack: ${packName} (\`${packSlug}\`)`,
      `- Page: ${page || 'Unavailable'}`,
      `- Browser: ${browser || 'Unavailable'}`,
    ].join('\n'),
  );
  return url.toString();
}
