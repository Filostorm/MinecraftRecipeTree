import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const viteConfig = await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8');
const sitesVitePlugin = await readFile(
  new URL('../build/sites-vite-plugin.ts', import.meta.url),
  'utf8',
);
const datasetCatalog = await readFile(new URL('../src/data/datasetCatalog.ts', import.meta.url), 'utf8');
const datasetCatalogContext = await readFile(
  new URL('../src/data/DatasetCatalogContext.tsx', import.meta.url),
  'utf8',
);
const environmentExample = await readFile(new URL('../.env.example', import.meta.url), 'utf8');

test('Cloudflare routes catalog, immutable datasets, and administration through the Worker', () => {
  assert.match(
    viteConfig,
    /publicDir:\s*false(?:\s+as\s+const)?/,
    'production builds must not copy the retired public/exports corpus into dist',
  );
  assert.match(
    sitesVitePlugin,
    /resolve\(root, ['"]dist['"], ['"]client['"], ['"]exports['"]\)[\s\S]*?if \(await exists\(retiredStaticExportDirectory\)\)[\s\S]*?throw new Error\(['"]Static dataset exports must not be bundled/,
    'the build must fail closed if any retired static export directory reaches dist/client',
  );
  assert.match(
    sitesVitePlugin,
    /resolve\(root, ['"]public['"], ['"]exporters['"]\)[\s\S]*?resolve\(root, ['"]dist['"], ['"]client['"], ['"]exporters['"]\)[\s\S]*?lstat\(source\)[\s\S]*?metadata\.nlink !== 1[\s\S]*?copyFile/,
    'only bounded single-link exporter release files may be copied into the deployable client',
  );
  const workerFirstSource = /run_worker_first:\s*\[([\s\S]*?)\]/.exec(viteConfig)?.[1];
  assert.ok(workerFirstSource, 'vite.config.ts must define an explicit run_worker_first route list');
  const workerFirstRoutes = [...workerFirstSource.matchAll(/['"]([^'"]+)['"]/g)].map(
    match => match[1],
  );
  assert.deepEqual(workerFirstRoutes, [
    '/api/admin/preview-assets/*',
    '/api/admin/core-datasets/*',
    '/api/admin/dataset-channels/*',
    '/api/datasets',
    '/api/modpacks*',
    '/dataset/publications/*',
    '/dataset/preview-sets/*',
  ]);
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
});

test('the client derives catalog-bound publication and preview-set routes', () => {
  assert.match(
    datasetCatalogContext,
    /return \{catalogUrl: ['"]\/api\/datasets['"], assetOrigin: ['"]['"]\};/,
    'web clients must load the same-origin immutable dataset catalog',
  );
  assert.match(
    datasetCatalog,
    /base:\s*`\$\{prefix\}\/dataset\/publications\/\$\{descriptor\.publicationId\}\/exports`/,
    'core exports must be scoped by the selected publication ID',
  );
  assert.match(
    datasetCatalog,
    /previewBase:\s*`\$\{prefix\}\/dataset\/preview-sets\/\$\{descriptor\.previewAssetSetId\}`/,
    'recipe previews must be scoped by the selected preview asset-set ID',
  );
  assert.doesNotMatch(
    datasetCatalog,
    /\/dataset\/(?:exports|previews)(?:\/|['"`])/,
    'the client must not retain retired process-global dataset routes',
  );
});

test('the environment template exposes only the current server-side operator contract', () => {
  const entries = [...environmentExample.matchAll(/^([A-Z][A-Z0-9_]*)=(.*)$/gm)];
  const values = Object.fromEntries(entries.map(([, name, value]) => [name, value]));
  assert.deepEqual(Object.keys(values), [
    'CORE_DATASET_UPLOAD_TOKEN',
    'PREVIEW_UPLOAD_ENABLED',
    'PREVIEW_UPLOAD_ASSET_SET_ID',
    'PREVIEW_UPLOAD_TOKEN',
  ]);
  assert.equal(values.PREVIEW_UPLOAD_ENABLED, 'false');
  assert.ok(values.CORE_DATASET_UPLOAD_TOKEN.length >= 32);
  assert.doesNotMatch(values.CORE_DATASET_UPLOAD_TOKEN, /[\s\u0000-\u001f\u007f]/);
  assert.ok(values.PREVIEW_UPLOAD_TOKEN.length >= 32);
  assert.doesNotMatch(environmentExample, /^PREVIEW_ASSET_SET_ID=/m);
  assert.doesNotMatch(environmentExample, /^EXPO_PUBLIC_.*TOKEN=/m);
});
