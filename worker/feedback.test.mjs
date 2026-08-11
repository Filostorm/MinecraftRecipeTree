import assert from 'node:assert/strict';
import test from 'node:test';

const {FEEDBACK_ROUTE, handleFeedback} = await import('./feedback.ts');

const ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';
const GITHUB_TOKEN = 'g'.repeat(40);
const ISSUE_URL = 'https://github.com/Filostorm/MinecraftRecipeTree/issues/123';
const DIAGNOSTICS = {
  packVersion: '0.18.6',
  minecraftVersion: '1.12.2',
  publicationId: 'a'.repeat(64),
  previewAssetSetId: 'b'.repeat(64),
  exportGeneratedAt: '2026-08-06T20:00:00Z',
  exportFormat: '2',
  itemCount: '196160',
  recipeCount: '200000',
  categoryCount: '180',
  modCount: '320',
  activeTab: 'graph',
  openItemKey: '',
  graphRootKey: 'item|thaumcraft:cluster',
  graphDirection: 'inputs',
  interfaceZoom: '125%',
  contentZoom: '175%',
  platform: 'web',
  userAgent: 'Recipe Tree feedback test',
  viewport: '1280×720 @2x',
  language: 'en-US',
  online: 'yes',
};

function request(payload, headers = {}) {
  return new Request(`${ORIGIN}${FEEDBACK_ROUTE}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Origin: ORIGIN,
      'CF-Connecting-IP': '192.0.2.10',
      'User-Agent': 'Recipe Tree feedback test',
      ...headers,
    },
    body: JSON.stringify({...payload, diagnostics: payload.diagnostics ?? DIAGNOSTICS}),
  });
}

function github({status = 201, issueUrl = ISSUE_URL} = {}) {
  const calls = [];
  return {
    calls,
    async fetch(url, init) {
      calls.push({url, init});
      return new Response(JSON.stringify(status === 201 ? {html_url: issueUrl} : {message: 'failed'}), {
        status,
        headers: {'Content-Type': 'application/json'},
      });
    },
  };
}

function inboxRequest(token) {
  return new Request(`${ORIGIN}${FEEDBACK_ROUTE}`, {
    headers: token ? {Authorization: `Bearer ${token}`} : {},
  });
}

function database({recentCount = 0, reports = []} = {}) {
  const calls = [];
  return {
    calls,
    prepare(sql) {
      const call = {sql, values: []};
      calls.push(call);
      return {
        bind(...values) {
          call.values = values;
          return this;
        },
        async first() {
          return {count: recentCount};
        },
        async all() {
          return {success: true, results: reports};
        },
        async run() {
          return {success: true, meta: {changes: 1}};
        },
      };
    },
  };
}

test('feedback inbox rejects unauthenticated reads before accessing storage', async () => {
  const DB = database();
  const response = await handleFeedback(
    inboxRequest(),
    {DB, FEEDBACK_ADMIN_TOKEN: 'a'.repeat(32)},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
  );

  assert.equal(response.status, 401);
  assert.equal(response.headers.get('www-authenticate'), 'Bearer realm="feedback-inbox"');
  assert.equal(DB.calls.length, 0);
});

test('feedback inbox returns recent reports without client fingerprints', async () => {
  const token = 'b'.repeat(32);
  const DB = database({
    reports: [
      {
        id: 'report-1',
        kind: 'bug',
        title: 'Branch does not expand',
        message: 'A branch does not expand.',
        contact: 'player@example.com',
        pack_slug: 'meatballcraft',
        pack_name: 'MeatballCraft',
        page_url: '/?pack=meatballcraft',
        user_agent: 'Safari',
        fingerprint_hash: 'must-not-leak',
        created_at: 1234,
      },
    ],
  });
  const response = await handleFeedback(
    inboxRequest(token),
    {DB, FEEDBACK_ADMIN_TOKEN: token},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
  );

  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body, {
    reports: [
      {
        id: 'report-1',
        kind: 'bug',
        title: 'Branch does not expand',
        message: 'A branch does not expand.',
        contact: 'player@example.com',
        packSlug: 'meatballcraft',
        packName: 'MeatballCraft',
        page: '/?pack=meatballcraft',
        userAgent: 'Safari',
        createdAt: 1234,
      },
    ],
  });
  assert.equal(JSON.stringify(body).includes('fingerprint'), false);
  assert.match(DB.calls[0].sql, /ORDER BY created_at DESC/);
});

test('feedback submissions store validated context without a raw client address', async () => {
  const DB = database();
  const GitHub = github();
  const response = await handleFeedback(
    request({
      kind: 'bug',
      title: 'Recipe expansion fails',
      message: 'The selected recipe does not expand.',
      contact: 'player@example.com',
      packSlug: 'multiblock-madness',
      packName: 'Multiblock Madness',
      page: '/?pack=multiblock-madness',
      website: '',
    }),
    {DB, GITHUB_ISSUES_TOKEN: GITHUB_TOKEN},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
    GitHub.fetch,
  );

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), {submitted: true, issueUrl: ISSUE_URL});
  assert.equal(DB.calls.length, 2);
  assert.match(DB.calls[0].sql, /SELECT COUNT/);
  assert.match(DB.calls[1].sql, /INSERT INTO feedback_reports/);
  assert.equal(DB.calls[1].values[1], 'bug');
  assert.equal(DB.calls[1].values[2], 'Recipe expansion fails');
  assert.equal(DB.calls[1].values[3], 'The selected recipe does not expand.');
  assert.equal(DB.calls[1].values[9].length, 64);
  assert.equal(DB.calls[1].values.includes('192.0.2.10'), false);
  assert.equal(GitHub.calls.length, 1);
  const issue = JSON.parse(GitHub.calls[0].init.body);
  assert.equal(issue.title, '[Bug] Recipe expansion fails');
  assert.deepEqual(issue.labels, ['bug']);
  assert.match(issue.body, /## Diagnostics/);
  assert.match(issue.body, /0\.18\.6/);
  assert.match(issue.body, /item\|thaumcraft:cluster/);
});

test('feedback keeps accepting reports from clients without the new content zoom diagnostic', async () => {
  const DB = database();
  const GitHub = github();
  const {contentZoom: _contentZoom, ...legacyDiagnostics} = DIAGNOSTICS;
  const response = await handleFeedback(
    request({
      kind: 'bug',
      title: 'Cached client report',
      message: 'This report came from the prior application bundle.',
      contact: '',
      packSlug: 'meatballcraft',
      packName: 'MeatballCraft',
      page: '/?pack=meatballcraft',
      website: '',
      diagnostics: legacyDiagnostics,
    }),
    {DB, GITHUB_ISSUES_TOKEN: GITHUB_TOKEN},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
    GitHub.fetch,
  );

  assert.equal(response.status, 201);
});

test('feedback endpoint rejects cross-origin writes before accessing storage', async () => {
  const DB = database();
  const response = await handleFeedback(
    request(
      {
        kind: 'feature',
        title: 'Another graph layout',
        message: 'Please add another graph layout.',
        website: '',
      },
      {Origin: 'https://example.com'},
    ),
    {DB},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
  );

  assert.equal(response.status, 403);
  assert.equal(DB.calls.length, 0);
});

test('feedback endpoint accepts the canonical origin through the Sites proxy', async () => {
  const DB = database();
  const GitHub = github();
  const response = await handleFeedback(
    request({
      kind: 'feature',
      title: 'Another graph layout',
      message: 'Please add another graph layout.',
      contact: '',
      packSlug: 'multiblock-madness',
      packName: 'Multiblock Madness',
      page: '/',
      website: '',
    }),
    {DB, GITHUB_ISSUES_TOKEN: GITHUB_TOKEN},
    new URL('https://minecraft-recipe-tree.gtjoe51.chatgpt.site/api/feedback'),
    GitHub.fetch,
  );

  assert.equal(response.status, 201);
  assert.equal(DB.calls.length, 2);
});

test('feedback endpoint rejects malformed fields', async () => {
  const DB = database();
  const response = await handleFeedback(
    request({
      kind: 'bug',
      message: 'This report is missing its required title.',
      website: '',
    }),
    {DB},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
  );

  assert.equal(response.status, 400);
  assert.equal(DB.calls.length, 0);
});

test('feedback endpoint enforces the hashed-client cooldown', async () => {
  const DB = database({recentCount: 3});
  const response = await handleFeedback(
    request({
      kind: 'feature',
      title: 'Another graph layout',
      message: 'Please add another graph layout.',
      contact: '',
      packSlug: '',
      packName: '',
      page: '/',
      website: '',
    }),
    {DB, GITHUB_ISSUES_TOKEN: GITHUB_TOKEN},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
  );

  assert.equal(response.status, 429);
  assert.equal(response.headers.get('retry-after'), '900');
  assert.equal(DB.calls.length, 1);
});

test('feedback submissions normalize legacy feature requests and use the enhancement label', async () => {
  const DB = database();
  const GitHub = github();
  const response = await handleFeedback(
    request({
      kind: 'feature',
      title: 'Keep totals visible',
      message: 'Please keep totals visible while the graph is panned.',
      contact: '',
      packSlug: 'meatballcraft',
      packName: 'MeatballCraft',
      page: '/',
      website: '',
    }),
    {DB, GITHUB_ISSUES_TOKEN: GITHUB_TOKEN},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
    GitHub.fetch,
  );
  assert.equal(response.status, 201);
  assert.equal(DB.calls[1].values[1], 'feedback');
  const issue = JSON.parse(GitHub.calls[0].init.body);
  assert.equal(issue.title, '[Feedback] Keep totals visible');
  assert.deepEqual(issue.labels, ['enhancement']);
});

test('failed GitHub issue creation removes the stored reservation so the user can retry', async () => {
  const DB = database();
  const GitHub = github({status: 502});
  const response = await handleFeedback(
    request({
      kind: 'bug',
      title: 'Graph is blank',
      message: 'The graph becomes blank after opening a large tree.',
      contact: '',
      packSlug: 'meatballcraft',
      packName: 'MeatballCraft',
      page: '/',
      website: '',
    }),
    {DB, GITHUB_ISSUES_TOKEN: GITHUB_TOKEN},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
    GitHub.fetch,
  );
  assert.equal(response.status, 502);
  assert.match((await response.json()).error, /try again/i);
  assert.match(DB.calls.at(-1).sql, /DELETE FROM feedback_reports/);
});
