const LOCAL_PACK_CACHE = 'minecraft-recipe-tree-local-packs-v1';
const LOCAL_PACK_ROUTE_PREFIX = '/__local-packs/';

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(Promise.resolve());
});

self.addEventListener('activate', event => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (
    event.request.method !== 'GET' ||
    url.origin !== self.location.origin ||
    !url.pathname.startsWith(LOCAL_PACK_ROUTE_PREFIX)
  ) {
    return;
  }

  event.respondWith(
    caches.open(LOCAL_PACK_CACHE).then(async cache => {
      const response = await cache.match(event.request, {ignoreSearch: true});
      return response ?? new Response('Local pack file not found.', {
        status: 404,
        headers: {
          'Cache-Control': 'no-store',
          'Content-Type': 'text/plain; charset=utf-8',
        },
      });
    }),
  );
});
