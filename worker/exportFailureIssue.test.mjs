import assert from 'node:assert/strict';
import test from 'node:test';

const {
  EXPORT_FAILURE_ROUTE,
  buildFailureCommentBodies,
  handleExportFailureIssue,
} = await import('./exportFailureIssue.ts');

const ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';
const TOKEN = 'github_test_token_abcdefghijklmnopqrstuvwxyz';

function report() {
  return {
    format: 'mrt-export-failure-report-v1',
    packName: 'Broken Machines',
    packVersion: '4.2.0',
    minecraftVersion: '1.20.1',
    exporterId: 'jeiexport',
    exporterVersion: '1.2.0-beta.23',
    exporterBuild: 'a'.repeat(64),
    generatedAt: '2026-08-02T12:00:00Z',
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

test('automatic exporter reporting creates one issue with all contextual failure details', async () => {
  const calls = [];
  const githubFetch = async (url, init = {}) => {
    calls.push({url: String(url), init});
    if (String(url).includes('/search/issues')) return json({items: []});
    if (String(url).endsWith('/issues') && init.method === 'POST') {
      const body = JSON.parse(init.body);
      return json({number: 91, html_url: 'https://github.com/Filostorm/MinecraftRecipeTree/issues/91', body: body.body}, 201);
    }
    if (String(url).includes('/issues/91/comments?')) return json([]);
    if (String(url).endsWith('/issues/91/comments') && init.method === 'POST') {
      return json({id: 1}, 201);
    }
    throw new Error(`Unexpected GitHub request: ${url}`);
  };

  const response = await handleExportFailureIssue(
    request(),
    {GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
    githubFetch,
  );

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), {
    issueUrl: 'https://github.com/Filostorm/MinecraftRecipeTree/issues/91',
    duplicate: false,
  });
  const createIssue = calls.find(call => call.url.endsWith('/issues'));
  const issuePayload = JSON.parse(createIssue.init.body);
  assert.match(issuePayload.title, /Broken Machines 4\.2\.0: 1 unique failure/);
  assert.match(issuePayload.body, /Minecraft: `1\.20\.1`/);
  assert.match(issuePayload.body, /Exporter: `jeiexport 1\.2\.0-beta\.23`/);
  const createComment = calls.find(call => call.url.endsWith('/issues/91/comments'));
  const commentPayload = JSON.parse(createComment.init.body);
  assert.match(commentPayload.body, /Mod: `brokenmod`/);
  assert.match(commentPayload.body, /Recipe: `brokenmod:crushed_ore`/);
  assert.match(commentPayload.body, /IllegalStateException: layout failed/);
  assert.equal(calls.every(call => call.init.headers.Authorization === `Bearer ${TOKEN}`), true);
});

test('an existing fingerprint returns the stored GitHub issue without another API call', async () => {
  let githubCalls = 0;
  const DB = {
    async batch(statements) {
      assert.equal(statements.length, 3);
      return statements.map(() => ({success: true}));
    },
    prepare() {
      return {
        bind() { return this; },
        async first() {
          return {
            fingerprint: 'known',
            issue_number: 12,
            issue_url: 'https://github.com/Filostorm/MinecraftRecipeTree/issues/12',
            status: 'reported',
            updated_at: Date.now(),
          };
        },
      };
    },
  };
  const response = await handleExportFailureIssue(
    request(),
    {DB, GITHUB_ISSUES_TOKEN: TOKEN},
    new URL(`${ORIGIN}${EXPORT_FAILURE_ROUTE}`),
    async () => {
      githubCalls += 1;
      throw new Error('GitHub should not be called');
    },
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    issueUrl: 'https://github.com/Filostorm/MinecraftRecipeTree/issues/12',
    duplicate: true,
  });
  assert.equal(githubCalls, 0);
});

test('failure comment chunks retain every unique failure', () => {
  const payload = report();
  payload.failures = Array.from({length: 600}, (_, index) => ({
    ...payload.failures[0],
    recipeId: `brokenmod:recipe_${index}`,
    recipeIndex: index,
  }));
  const comments = buildFailureCommentBodies(payload, 'f'.repeat(64));
  assert.ok(comments.length > 1);
  for (let index = 0; index < payload.failures.length; index += 1) {
    assert.equal(
      comments.filter(comment => comment.includes(`Recipe: \`brokenmod:recipe_${index}\``)).length,
      1,
    );
  }
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
