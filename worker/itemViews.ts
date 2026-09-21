import {type DatasetRuntime, noStoreJson, methodNotAllowed} from './datasetRuntime.ts';
import {betaCatalogIncludesPublication} from './betaDataProxy.ts';

export const ITEM_VIEWS_ROUTE = '/api/item-views';
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** Bound bytes while reading, not after allocating an untrusted request body. */
export async function readViewBatch(request: Request): Promise<string[]> {
  if (!request.headers.get('content-type')?.startsWith('application/json')) throw new Error('JSON is required.');
  const reader = request.body?.getReader();
  if (!reader) throw new Error('A view batch is required.');
  const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) {
      const next = await reader.read();
      if (next.done) break;
      size += next.value.byteLength;
      if (size > 12_000) {await reader.cancel(); throw new Error('View batch is too large.');}
      chunks.push(next.value);
    }
  } finally {reader.releaseLock();}
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) {bytes.set(chunk, offset); offset += chunk.length;}
  const body = JSON.parse(new TextDecoder().decode(bytes));
  if (!Array.isArray(body) || body.length < 1 || body.length > 20 || body.some(key =>
    typeof key !== 'string' || key.length > 512 || !key.includes('|') || /[\u0000-\u001f\u007f]/.test(key)
  ) || new Set(body).size !== body.length) throw new Error('Expected up to 20 unique item keys.');
  return body;
}

export async function handleItemViews(request: Request, runtime: DatasetRuntime, url: URL,
  ctx?: {waitUntil(promise: Promise<unknown>): void}, cache = globalThis.caches?.default): Promise<Response> {
  if (!['GET', 'POST'].includes(request.method)) return methodNotAllowed('GET, POST');
  const slug = url.searchParams.get('packSlug');
  const publication = url.searchParams.get('publicationId');
  if (!slug || slug.length > 80 || !SLUG.test(slug) || !publication || !/^[a-f0-9]{64}$/.test(publication)
    || url.searchParams.size !== 2) return noStoreJson({error: 'Invalid pack identity.'}, 400);
  const key = new Request(`${url.origin}${ITEM_VIEWS_ROUTE}?packSlug=${slug}&publicationId=${publication}`);
  try {
    if (request.method === 'GET' && cache) {
      const hit = await cache.match(key);
      if (hit) return hit;
    }
    const db = runtime.DB;
    if (!db) throw new Error('Popularity storage is unavailable.');
    if (request.method === 'POST') {
      if (request.headers.get('origin') !== url.origin) return noStoreJson({error: 'Origin refused.'}, 403);
      const limiter = runtime.ITEM_VIEW_RATE_LIMITER;
      const ip = request.headers.get('cf-connecting-ip');
      if (!limiter || !ip) throw new Error('Popularity writes require the edge rate limiter.');
      if (!(await limiter.limit({key: ip})).success) return noStoreJson({error: 'Too many view batches.'}, 429);
      let items: string[];
      try {items = await readViewBatch(request);} catch (error) {
        console.warn('Invalid community view batch.', error);
        return noStoreJson({error: 'Invalid view batch.'}, 400);
      }
      const pack = await db.prepare('SELECT slug FROM dataset_channels WHERE slug = ? AND publication_id = ? LIMIT 1').bind(slug, publication).first();
      if (!pack && !(await betaCatalogIncludesPublication(runtime, slug, publication))) return noStoreJson({error: 'Pack is not published.'}, 404);
      const results = await db.batch(items.map(item => db.prepare(
        `INSERT INTO item_view_totals(pack_slug, item_key, views) VALUES(?, ?, 1)
         ON CONFLICT(pack_slug, item_key) DO UPDATE SET views = views + 1`,
      ).bind(slug, item)));
      if (results.some(result => !result.success)) throw new Error('View batch was not saved.');
      return noStoreJson({accepted: items.length});
    }
    // Covering index: no event-log aggregation and no full-table scan on browse.
    const rows = await db.prepare(`SELECT item_key, views FROM item_view_totals
      WHERE pack_slug = ? ORDER BY views DESC, item_key LIMIT 800`).bind(slug).all<{item_key: string; views: number}>();
    if (!rows.success) throw new Error('Popularity ranking could not be read.');
    const response = new Response(JSON.stringify({items: (rows.results ?? []).map(row => row.item_key)}), {
      headers: {'Content-Type': 'application/json', 'Cache-Control': 'public, max-age=300', 'X-Content-Type-Options': 'nosniff'},
    });
    if (cache && ctx) ctx.waitUntil(cache.put(key, response.clone()).catch(error => console.error('Popularity cache write failed.', error)));
    return response;
  } catch (error) {
    console.error('Community item views are unavailable.', error);
    return noStoreJson({error: 'Community popularity is unavailable.'}, 503);
  }
}
