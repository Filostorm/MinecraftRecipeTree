import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const viteConfig = await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8');
const dataContext = await readFile(new URL('../src/data/DataContext.tsx', import.meta.url), 'utf8');

test('Cloudflare routes export files through the publication gate', () => {
  assert.match(
    viteConfig,
    /assets:\s*\{[\s\S]*?binding:\s*['"]ASSETS['"][\s\S]*?run_worker_first:\s*\[[\s\S]*?['"]\/api\/admin\/preview-assets\/\*['"][\s\S]*?['"]\/dataset\/exports\/\*['"][\s\S]*?['"]\/dataset\/previews\/\*['"][\s\S]*?\]/,
    'vite.config.ts must run the Worker before preview ingestion and virtual dataset requests',
  );
  assert.doesNotMatch(
    viteConfig,
    /run_worker_first:\s*true/,
    'unrelated hashed application assets should retain the lower-overhead asset-first path',
  );
  assert.match(
    viteConfig,
    /cache:\s*\{\s*enabled:\s*true\s*\}/,
    'immutable Worker responses must use Cloudflare Workers Caching at the edge',
  );
  assert.match(
    viteConfig,
    /r2_buckets:\s*hostingConfig\.r2[\s\S]*?binding:\s*hostingConfig\.r2[\s\S]*?bucket_name:\s*['"]site-creator-r2['"]/,
    'preview assets must use the Sites-managed native R2 binding',
  );
  assert.match(
    dataContext,
    /Platform\.OS === ['"]web['"]\) return ['"]\/dataset\/exports['"];/,
    'the web client must use a virtual path because Sites serves existing physical files first',
  );
});
