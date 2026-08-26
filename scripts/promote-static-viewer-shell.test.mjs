import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {promoteStaticViewerShell} from './promote-static-viewer-shell.mjs';

async function fixture(status = 'rendered') {
  const root = await mkdtemp(join(tmpdir(), 'mrt-static-shell-'));
  const client = join(root, 'dist', 'client');
  const prerendered = join(root, 'dist', 'server', 'prerendered-routes');
  await mkdir(join(client, 'assets'), {recursive: true});
  await mkdir(prerendered, {recursive: true});
  await writeFile(join(client, '_headers'), '/*\n  X-Content-Type-Options: nosniff\n');
  await writeFile(join(client, 'assets', 'index-AbCd1234.js'), 'export default true;\n');
  await writeFile(
    join(root, 'dist', 'server', 'vinext-prerender.json'),
    JSON.stringify({
      routes: [
        status === 'rendered'
          ? {route: '/', status, revalidate: false, router: 'app'}
          : {route: '/', status, reason: 'dynamic'},
      ],
    }),
  );
  await writeFile(
    join(prerendered, 'index.html'),
    '<!DOCTYPE html><html><head><title>Minecraft Recipe Tree</title></head><body>' +
      '<script id="_R_">import("/assets/index-AbCd1234.js")</script>' +
      `${'viewer shell '.repeat(90)}self.__VINEXT_RSC_DONE__=true</body></html>`,
  );
  return root;
}

test('promotes only a validated prerendered root into the asset-first directory', async () => {
  const root = await fixture();
  await promoteStaticViewerShell(root);
  const html = await readFile(join(root, 'dist', 'client', 'index.html'), 'utf8');
  const headers = await readFile(join(root, 'dist', 'client', '_headers'), 'utf8');
  assert.match(html, /self\.__VINEXT_RSC_DONE__=true/u);
  assert.match(headers, /^\/$/mu);
  assert.match(headers, /^\/index\.html$/mu);
});

test('fails instead of publishing a root Vinext classified as dynamic', async () => {
  const root = await fixture('skipped');
  await assert.rejects(
    promoteStaticViewerShell(root),
    /viewer root was not rendered/u,
  );
});
