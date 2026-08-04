import assert from 'node:assert/strict';
import test from 'node:test';

import router, {
  APP_ROUTES,
  rewriteLocation,
  shouldRewriteBody,
} from './cloudflare/app-subdomain-router.worker.mjs';

test('Minecraft Recipe Tree uses the native Cloudflare service binding', () => {
  const route = APP_ROUTES['minecraftrecipetree.craftsmannsoftware.com'];
  assert.equal(route.serviceBinding, 'MINECRAFT_RECIPE_TREE');
  assert.equal(
    route.deployedOrigin,
    'https://minecraft-recipe-tree-production.gtjoe51.workers.dev',
  );
});

test('router preserves the signal-edge compatibility header and reports the real Worker', async () => {
  let receivedUrl;
  const response = await router.fetch(
    new Request('https://minecraftrecipetree.craftsmannsoftware.com/api/datasets'),
    {
      MINECRAFT_RECIPE_TREE: {
        async fetch(request) {
          receivedUrl = request.url;
          return Response.json({ datasets: [] });
        },
      },
    },
  );

  assert.equal(
    receivedUrl,
    'https://minecraftrecipetree.craftsmannsoftware.com/api/datasets',
  );
  assert.equal(response.status, 200);
  assert.equal(
    response.headers.get('x-craftsmann-app-origin'),
    'https://minecraft-recipe-tree.gtjoe51.chatgpt.site',
  );
  assert.equal(
    response.headers.get('x-craftsmann-worker-origin'),
    'https://minecraft-recipe-tree-production.gtjoe51.workers.dev',
  );
});

test('router keeps existing redirects and text response handling intact', () => {
  const route = APP_ROUTES['contractoros.craftsmannsoftware.com'];
  assert.equal(
    rewriteLocation(
      '/pricing',
      route,
      'https://contractoros.craftsmannsoftware.com',
    ),
    'https://contractoros.craftsmannsoftware.com/pricing',
  );
  assert.equal(shouldRewriteBody('text/html; charset=utf-8'), true);
  assert.equal(shouldRewriteBody('image/png'), false);
});

test('router rejects unknown hostnames', async () => {
  const response = await router.fetch(
    new Request('https://unknown.craftsmannsoftware.com/'),
    {},
  );
  assert.equal(response.status, 404);
});
