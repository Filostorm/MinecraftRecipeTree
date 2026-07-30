import assert from 'node:assert/strict';
import test from 'node:test';

import {buildGitHubIssueUrl} from './githubIssues.ts';

test('bug reports open a prefilled GitHub issue with runtime context', () => {
  const url = new URL(
    buildGitHubIssueUrl({
      kind: 'bug',
      packSlug: 'meatballcraft',
      packName: 'MeatballCraft',
      page: '/?tab=graph',
      browser: 'Test Browser/1.0',
    }),
  );

  assert.equal(url.origin, 'https://github.com');
  assert.equal(url.pathname, '/Filostorm/MinecraftRecipeTree/issues/new');
  assert.equal(url.searchParams.get('labels'), 'bug');
  assert.equal(url.searchParams.get('title'), '[Bug] ');
  assert.match(url.searchParams.get('body'), /## Steps to reproduce/);
  assert.match(url.searchParams.get('body'), /MeatballCraft \(`meatballcraft`\)/);
  assert.match(url.searchParams.get('body'), /\/\?tab=graph/);
  assert.match(url.searchParams.get('body'), /Test Browser\/1\.0/);
});

test('feature requests open a prefilled enhancement issue', () => {
  const url = new URL(
    buildGitHubIssueUrl({
      kind: 'feature',
      packSlug: 'gtnh',
      packName: 'GT New Horizons',
      page: '',
      browser: '',
    }),
  );

  assert.equal(url.searchParams.get('labels'), 'enhancement');
  assert.equal(url.searchParams.get('title'), '[Feature] ');
  assert.match(url.searchParams.get('body'), /## Proposed feature/);
  assert.match(url.searchParams.get('body'), /## Why would this help\?/);
  assert.match(url.searchParams.get('body'), /Page: Unavailable/);
  assert.match(url.searchParams.get('body'), /Browser: Unavailable/);
});

