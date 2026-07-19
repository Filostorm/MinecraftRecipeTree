import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {
  EXPORTER_RELEASE_MANIFEST_FORMAT,
  packageExporterReleases,
} from './package-exporter-releases.mjs';

const quietLogger = Object.freeze({info() {}, warn() {}, error() {}});

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'mrt-exporter-release-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  const publicRoot = join(root, 'public', 'exporters');
  await mkdir(join(root, 'build', 'libs'), {recursive: true});
  await writeFile(join(root, 'build', 'libs', 'exporter.jar'), Buffer.from([
    0x50, 0x4b, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00,
  ]));
  return {root, publicRoot};
}

function definition(overrides = {}) {
  return {
    id: 'test-exporter',
    minecraftVersion: '1.20.1',
    recipeViewer: 'JEI 15',
    loader: 'Forge 47',
    version: '1.0.0',
    source: 'build/libs/exporter.jar',
    filename: 'recipe-tree-exporter-test.jar',
    qualityProfiles: ['generic-jei-1.20.1'],
    compatibility: 'Test compatibility',
    ...overrides,
  };
}

test('packages only the configured release JAR and writes an exact checksummed manifest', async t => {
  const {root, publicRoot} = await fixture(t);
  const manifest = await packageExporterReleases({
    workspaceRoot: root,
    publicRoot,
    definitions: [definition()],
    generatedAt: '2026-07-19T12:00:00.000Z',
    logger: quietLogger,
  });
  assert.equal(manifest.format, EXPORTER_RELEASE_MANIFEST_FORMAT);
  assert.equal(manifest.releases[0].bytes, 8);
  assert.match(manifest.releases[0].sha256, /^[a-f0-9]{64}$/);
  assert.equal(manifest.releases[0].source, undefined);
  assert.deepEqual(
    JSON.parse(await readFile(join(publicRoot, 'manifest.json'), 'utf8')),
    manifest,
  );
  assert.deepEqual(
    await readFile(join(publicRoot, definition().filename)),
    await readFile(join(root, definition().source)),
  );
});

test('rejects development filenames and non-JAR content', async t => {
  const {root, publicRoot} = await fixture(t);
  await assert.rejects(
    packageExporterReleases({
      workspaceRoot: root,
      publicRoot,
      definitions: [definition({filename: 'exporter-dev.jar'})],
      logger: quietLogger,
    }),
    /invalid release filename/,
  );
  await writeFile(join(root, 'build', 'libs', 'exporter.jar'), 'not a jar');
  await assert.rejects(
    packageExporterReleases({
      workspaceRoot: root,
      publicRoot,
      definitions: [definition()],
      logger: quietLogger,
    }),
    /ZIP\/JAR local-file signature/,
  );
});

test('rejects a symlinked build artifact without following it', async t => {
  if (process.platform === 'win32') {
    t.skip('Creating filesystem symlinks requires an elevated Windows test environment.');
    return;
  }
  const {root, publicRoot} = await fixture(t);
  const source = join(root, 'build', 'libs', 'exporter.jar');
  const target = join(root, 'build', 'libs', 'target.jar');
  await writeFile(target, Buffer.from([0x50, 0x4b, 0x03, 0x04]));
  await rm(source);
  await symlink(target, source);
  await assert.rejects(
    packageExporterReleases({
      workspaceRoot: root,
      publicRoot,
      definitions: [definition()],
      logger: quietLogger,
    }),
    /plain, non-hard-linked regular file/,
  );
});
