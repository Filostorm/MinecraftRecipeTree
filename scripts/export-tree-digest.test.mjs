import assert from 'node:assert/strict';
import {link, mkdtemp, mkdir, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {
  EXPORT_TREE_DIGEST_FORMAT,
  digestExportTree,
  sameExportTreeDigest,
} from './export-tree-digest.mjs';

const quietLogger = Object.freeze({info() {}, warn() {}, error() {}});

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'mrt-export-tree-digest-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  await mkdir(join(root, 'recipes', 'example'), {recursive: true});
  await writeFile(join(root, 'manifest.json'), '{"format":1}\n');
  await writeFile(join(root, 'recipes', 'example', 'r0.png'), Buffer.from([1, 2, 3, 4]));
  return root;
}

test('builds a deterministic inventory-complete digest and detects non-manifest mutation', async t => {
  const root = await fixture(t);
  const first = await digestExportTree(root, {logger: quietLogger});
  const second = await digestExportTree(root, {logger: quietLogger});
  assert.equal(first.format, EXPORT_TREE_DIGEST_FORMAT);
  assert.equal(first.files, 2);
  assert.equal(first.bytes, 17);
  assert.equal(sameExportTreeDigest(first, second), true);

  await writeFile(join(root, 'recipes', 'example', 'r0.png'), Buffer.from([1, 2, 3, 5]));
  const changed = await digestExportTree(root, {logger: quietLogger});
  assert.equal(sameExportTreeDigest(first, changed), false);
});

test('refuses symlinked and hard-linked export files', async t => {
  if (process.platform === 'win32') {
    t.skip('Symlink creation requires an elevated Windows test environment.');
    return;
  }
  const symlinkRoot = await fixture(t);
  const outside = join(symlinkRoot, '..', `outside-${Date.now()}.json`);
  t.after(() => rm(outside, {force: true}));
  await writeFile(outside, '{}\n');
  await symlink(outside, join(symlinkRoot, 'linked.json'));
  await assert.rejects(
    digestExportTree(symlinkRoot, {logger: quietLogger}),
    /symlink or special filesystem entry/,
  );

  const hardlinkRoot = await fixture(t);
  await link(
    join(hardlinkRoot, 'manifest.json'),
    join(hardlinkRoot, 'manifest-copy.json'),
  );
  await assert.rejects(
    digestExportTree(hardlinkRoot, {logger: quietLogger}),
    /non-hard-linked regular file/,
  );
});
