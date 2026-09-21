import assert from 'node:assert/strict';
import test from 'node:test';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import {handleItemViews, readViewBatch} from './itemViews.ts';

const url = new URL(`https://example.com/api/item-views?packSlug=pack&publicationId=${'a'.repeat(64)}`);
function database() {
  const sql = new DatabaseSync(':memory:');
  sql.exec(readFileSync(new URL('../drizzle/0011_item_views.sql', import.meta.url), 'utf8'));
  sql.exec('CREATE TABLE dataset_channels(slug TEXT, publication_id TEXT);');
  sql.prepare('INSERT INTO dataset_channels VALUES (?, ?)').run('pack', 'a'.repeat(64));
  const prepare = (query, values = []) => ({
    bind: (...next) => prepare(query, next),
    first: async () => sql.prepare(query).get(...values) ?? null,
    all: async () => ({success: true, results: sql.prepare(query).all(...values)}),
    run: async () => {sql.prepare(query).run(...values); return {success: true};},
  });
  return {sql, DB: {prepare, batch: statements => Promise.all(statements.map(statement => statement.run()))}};
}
const post = body => new Request(url, {method: 'POST', headers: {Origin: url.origin, 'Content-Type': 'application/json', 'CF-Connecting-IP': '192.0.2.1'}, body: JSON.stringify(body)});

test('view batches enforce unique keys, count and streaming byte bounds', async () => {
  assert.deepEqual(await readViewBatch(post(['item|stone'])), ['item|stone']);
  for (const body of [[], ['item|x', 'item|x'], ['invalid'], Array(21).fill('item|x'), ['item|' + 'x'.repeat(13_000)]]) {
    await assert.rejects(readViewBatch(post(body)));
  }
});
test('ranking is pack-scoped and uses its covering index', async () => {
  const {sql, DB} = database();
  sql.exec("INSERT INTO item_view_totals VALUES ('pack', 'item|a', 2), ('pack', 'item|b', 10), ('other', 'item|c', 99)");
  const response = await handleItemViews(new Request(url), {DB}, url);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {items: ['item|b', 'item|a']});
  assert.match(response.headers.get('cache-control'), /max-age=300/);
  const plan = sql.prepare('EXPLAIN QUERY PLAN SELECT item_key, views FROM item_view_totals WHERE pack_slug = ? ORDER BY views DESC, item_key LIMIT 800').all('pack');
  assert.match(JSON.stringify(plan), /COVERING INDEX item_view_ranking/);
  assert.doesNotMatch(JSON.stringify(plan), /TEMP B-TREE|SCAN item_view_totals/);
  sql.close();
});
test('cached rankings avoid every database operation', async () => {
  const response = await handleItemViews(new Request(url), {DB: {prepare() {throw new Error('D1 must not be accessed');}}}, url, undefined, {match: async () => new Response('{"items":["item|cached"]}')});
  assert.deepEqual(await response.json(), {items: ['item|cached']});
});
test('writes are origin-checked, rate-limited and batched', async () => {
  const {sql, DB} = database();
  const runtime = {DB, ITEM_VIEW_RATE_LIMITER: {limit: async () => ({success: true})}};
  const response = await handleItemViews(post(['item|a', 'item|b']), runtime, url);
  assert.equal(response.status, 200);
  assert.equal(sql.prepare('SELECT SUM(views) AS n FROM item_view_totals').get().n, 2);
  runtime.ITEM_VIEW_RATE_LIMITER.limit = async () => ({success: false});
  assert.equal((await handleItemViews(post(['item|a']), runtime, url)).status, 429);
  const foreign = post(['item|a']); foreign.headers.set('origin', 'https://other.example');
  assert.equal((await handleItemViews(foreign, runtime, url)).status, 403);
  assert.equal(sql.prepare('SELECT SUM(views) AS n FROM item_view_totals').get().n, 2);
  sql.close();
});
