import assert from 'node:assert/strict';
import test from 'node:test';

const {FEEDBACK_ROUTE, handleFeedback} = await import('./feedback.ts');

const ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';

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
    body: JSON.stringify(payload),
  });
}

function database({recentCount = 0} = {}) {
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
        async run() {
          return {success: true, meta: {changes: 1}};
        },
      };
    },
  };
}

test('feedback submissions store validated context without a raw client address', async () => {
  const DB = database();
  const response = await handleFeedback(
    request({
      kind: 'bug',
      message: 'The selected recipe does not expand.',
      contact: 'player@example.com',
      packSlug: 'multiblock-madness',
      packName: 'Multiblock Madness',
      page: '/?pack=multiblock-madness',
      website: '',
    }),
    {DB},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
  );

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), {submitted: true});
  assert.equal(DB.calls.length, 2);
  assert.match(DB.calls[0].sql, /SELECT COUNT/);
  assert.match(DB.calls[1].sql, /INSERT INTO feedback_reports/);
  assert.equal(DB.calls[1].values[1], 'bug');
  assert.equal(DB.calls[1].values[2], 'The selected recipe does not expand.');
  assert.equal(DB.calls[1].values[8].length, 64);
  assert.equal(DB.calls[1].values.includes('192.0.2.10'), false);
});

test('feedback endpoint rejects cross-origin writes before accessing storage', async () => {
  const DB = database();
  const response = await handleFeedback(
    request(
      {kind: 'feature', message: 'Please add another graph layout.', website: ''},
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
  const response = await handleFeedback(
    request({
      kind: 'feature',
      message: 'Please add another graph layout.',
      contact: '',
      packSlug: 'multiblock-madness',
      packName: 'Multiblock Madness',
      page: '/',
      website: '',
    }),
    {DB},
    new URL('https://minecraft-recipe-tree.gtjoe51.chatgpt.site/api/feedback'),
  );

  assert.equal(response.status, 201);
  assert.equal(DB.calls.length, 2);
});

test('feedback endpoint rejects malformed fields', async () => {
  const DB = database();
  const response = await handleFeedback(
    request({kind: 'bug', message: 'Too short', website: ''}),
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
      message: 'Please add another graph layout.',
      contact: '',
      packSlug: '',
      packName: '',
      page: '/',
      website: '',
    }),
    {DB},
    new URL(`${ORIGIN}${FEEDBACK_ROUTE}`),
  );

  assert.equal(response.status, 429);
  assert.equal(response.headers.get('retry-after'), '900');
  assert.equal(DB.calls.length, 1);
});
