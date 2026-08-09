import assert from 'node:assert/strict';
import test from 'node:test';

const {
  EXPORT_FAILURE_ROUTE,
  handleExportFailureIssue,
  reportDedupeIdentity,
} = await import('./exportFailureIssue.ts');

const ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';
const TOKEN = 'github_test_token_abcdefghijklmnopqrstuvwxyz';
const ISSUE_URL = 'https://github.com/Filostorm/MinecraftRecipeTree/issues/91';
const FILE_URL = 'https://raw.githubusercontent.com/Filostorm/MinecraftRecipeTree/export-failure-reports/report/errors.json';
const FILE_HTML_URL = 'https://github.com/Filostorm/MinecraftRecipeTree/blob/export-failure-reports/report/errors.json';

function report() {
  return {
    format: 'mrt-export-failure-report-v1',
    packName: 'Broken Machines',
    packVersion: '4.2.0',
    minecraftVersion: '1.20.1',
    exporterId: 'jeiexport',
    exporterVersion: '1.2.0-beta.24',
    exporterBuild: 'a'.repeat(64),
    generatedAt: '2026-08-02T12:00:00Z',
    modVersions: {brokenmod: '7.3.1'},
    failures: [
      {
        scope: 'recipe',
        modId: 'brokenmod',
        categoryId: 'brokenmod:crusher',
        recipeId: 'brokenmod:crushed_ore',
        recipeIndex: 17,
        recipeClass: 'brokenmod.recipe.CrusherRecipe',
        errorType: 'java.lang.IllegalStateException',
        message: 'Recipe layout failed.',
        details: 'java.lang.IllegalStateException: layout failed\n\tat brokenmod.Crusher.render',
      },
    ],
  };
}

function request(payload = report(), origin = ORIGIN) {
  return new Request(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Origin: origin,
      'CF-Connecting-IP': '192.0.2.40',
      'User-Agent': 'Recipe Tree exporter report test',
    },
    body: JSON.stringify(payload),
  });
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function githubMock({existingIssue = false, existingFile = false} = {}) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    const target = String(url);
    calls.push({url: target, init});
    if (target.endsWith('/git/ref/heads/export-failure-reports')) {
      return existingFile ? json({object: {sha: 'report-branch'}}) : json({message: 'Not Found'}, 404);
    }
    if (target === 'https://api.github.com/repos/Filostorm/MinecraftRecipeTree') {
      return json({default_branch: 'main'});
    }
    if (target.endsWith('/git/ref/heads/main')) return json({object: {sha: 'main-sha'}});
    if (target.endsWith('/git/refs') && init.method === 'POST') return json({ref: 'refs/heads/export-failure-reports'}, 201);
    if (target.includes('/contents/export-failure-reports/') && target.includes('?ref=')) {
      return existingFile ? json({sha: 'existing-file-sha'}) : json({message: 'Not Found'}, 404);
    }
    if (target.includes('/contents/export-failure-reports/') && init.method === 'PUT') {
      return json({content: {html_url: FILE_HTML_URL, download_url: FILE_URL}}, existingFile ? 200 : 201);
    }
    if (target.includes('/search/issues')) {
      return json({items: existingIssue ? [{number: 91, html_url: ISSUE_URL}] : []});
    }
    if (target.endsWith('/issues') && init.method === 'POST') {
      return json({number: 91, html_url: ISSUE_URL}, 201);
    }
    if (target.endsWith('/issues/91') && init.method === 'PATCH') {
      return json({number: 91, html_url: ISSUE_URL});
    }
    throw new Error(`Unexpected GitHub request: ${url}`);
  };
  return {calls, fetch};
}

test('stores all exporter failures in errors.json and links one issue without comments', async () => {
  const github = githubMock();
  const response = await handleExportFailureIssue(
    request(),
    {GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
    github.fetch,
  );

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), {
    issueUrl: ISSUE_URL,
    fileUrl: FILE_URL,
    duplicate: false,
  });
  const writeFile = github.calls.find(call =>
    call.url.includes('/contents/export-failure-reports/') && call.init.method === 'PUT');
  const filePayload = JSON.parse(writeFile.init.body);
  const errorsFile = JSON.parse(Buffer.from(filePayload.content, 'base64').toString('utf8'));
  assert.equal(errorsFile.format, 'mrt-export-failure-file-v1');
  assert.equal(errorsFile.report.failures.length, 1);
  assert.equal(errorsFile.report.failures[0].recipeId, 'brokenmod:crushed_ore');
  assert.deepEqual(errorsFile.report.modVersions, {brokenmod: '7.3.1'});
  const createIssue = github.calls.find(call => call.url.endsWith('/issues'));
  const issuePayload = JSON.parse(createIssue.init.body);
  assert.match(issuePayload.title, /Broken Machines 4\.2\.0 export failures/);
  assert.match(issuePayload.body, /Download errors\.json/);
  assert.match(issuePayload.body, /brokenmod 7\.3\.1/);
  assert.doesNotMatch(issuePayload.body, /Deduplicated by:/);
  assert.doesNotMatch(issuePayload.body, /Repeating this report/);
  assert.equal(github.calls.some(call => call.url.includes('/comments')), false);
  assert.equal(github.calls.every(call => call.init.headers.Authorization === `Bearer ${TOKEN}`), true);
});

test('rejects reports containing only expected compatibility fallbacks', async () => {
  const payload = report();
  payload.failures = [
    {...payload.failures[0], message: 'mob example_mod:invisible_helper rendered fully transparent and was omitted'},
    {
      ...payload.failures[0],
      message: 'blockdrops another_mod:machine_casing: no standard candidate tool satisfies requiresCorrectToolForDrops; probing with a netherite pickaxe',
    },
  ];
  const github = githubMock();
  const response = await handleExportFailureIssue(
    request(payload),
    {GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
    github.fetch,
  );

  assert.equal(response.status, 400);
  assert.equal(github.calls.length, 0);
});

test('deduplicates different failures from the same pack version', () => {
  const first = report();
  const second = report();
  second.failures[0].recipeId = 'brokenmod:different_recipe';
  second.failures[0].message = 'A different layout failed.';
  assert.deepEqual(reportDedupeIdentity(first), reportDedupeIdentity(second));
  assert.deepEqual(reportDedupeIdentity(first), {
    kind: 'pack-version',
    packName: 'Broken Machines',
    packVersion: '4.2.0',
    minecraftVersion: '1.20.1',
  });
});

test('uses affected mod versions when a pack version is unavailable', () => {
  const payload = report();
  payload.packVersion = 'Unknown';
  payload.modVersions = {secondmod: '2.0.0', brokenmod: '7.3.1'};
  payload.failures.push({...payload.failures[0], modId: 'secondmod', recipeId: 'secondmod:bad'});
  assert.deepEqual(reportDedupeIdentity(payload), {
    kind: 'mod-versions',
    minecraftVersion: '1.20.1',
    mods: {brokenmod: '7.3.1', secondmod: '2.0.0'},
  });
});

test('updates the existing file and issue for a duplicate identity', async () => {
  const github = githubMock({existingIssue: true, existingFile: true});
  const response = await handleExportFailureIssue(
    request(),
    {GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
    github.fetch,
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    issueUrl: ISSUE_URL,
    fileUrl: FILE_URL,
    duplicate: true,
  });
  const writeFile = github.calls.find(call => call.init.method === 'PUT');
  assert.equal(JSON.parse(writeFile.init.body).sha, 'existing-file-sha');
  assert.equal(github.calls.some(call => call.url.endsWith('/issues/91') && call.init.method === 'PATCH'), true);
  assert.equal(github.calls.some(call => call.url.includes('/comments')), false);
});

test('D1 reservation prevents concurrent updates to one versioned report file', async () => {
  const DB = {
    async batch(statements) {
      return statements.map(() => ({success: true}));
    },
    prepare(sql) {
      return {
        bind() { return this; },
        async first() {
          return sql.includes('FROM export_failure_reports WHERE fingerprint')
            ? {
                fingerprint: 'known',
                issue_number: 91,
                issue_url: ISSUE_URL,
                status: 'reported',
                updated_at: Date.now(),
              }
            : null;
        },
        async run() { return {success: true, meta: {changes: 0}}; },
      };
    },
  };
  let githubCalls = 0;
  const response = await handleExportFailureIssue(
    request(),
    {DB, GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
    async () => {
      githubCalls += 1;
      throw new Error('GitHub should not be called after a lost reservation race.');
    },
  );
  assert.equal(response.status, 409);
  assert.equal(githubCalls, 0);
});

test('accepts legacy reports without modVersions using an Unknown fallback', async () => {
  const payload = report();
  delete payload.modVersions;
  const github = githubMock();
  const response = await handleExportFailureIssue(
    request(payload),
    {GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
    github.fetch,
  );
  assert.equal(response.status, 201);
  const writeFile = github.calls.find(call => call.init.method === 'PUT');
  const filePayload = JSON.parse(writeFile.init.body);
  const errorsFile = JSON.parse(Buffer.from(filePayload.content, 'base64').toString('utf8'));
  assert.deepEqual(errorsFile.report.modVersions, {brokenmod: 'Unknown'});
});

test('automatic exporter reporting rejects cross-origin and malformed requests', async () => {
  const crossOrigin = await handleExportFailureIssue(
    request(report(), 'https://example.com'),
    {GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
  );
  assert.equal(crossOrigin.status, 403);

  const malformed = report();
  malformed.failures[0].recipeIndex = -1;
  const invalid = await handleExportFailureIssue(
    request(malformed),
    {GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
  );
  assert.equal(invalid.status, 400);
});
