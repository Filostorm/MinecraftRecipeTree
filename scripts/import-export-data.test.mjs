import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {renameSync, symlinkSync} from 'node:fs';
import {
  access,
  chmod,
  lstat,
  mkdtemp,
  mkdir,
  readFile,
  rm,
  symlink,
  writeFile,
} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join, relative, sep} from 'node:path';
import test from 'node:test';
import {promisify} from 'node:util';
import {
  importExportData,
  importWorkspaceRootForDestination,
  publishTransactional,
} from './import-export-data.mjs';
import {PUBLICATION_ID_PATTERN} from './publication-id.mjs';
import {
  createRawExportFixture,
  readJson,
  writeJson,
  writeNonUniformImage,
} from './test-export-fixture.mjs';
import {validateExportData} from './validate-export-data.mjs';

const execFileAsync = promisify(execFile);

async function pathIsMissing(path) {
  try {
    await access(path);
    return false;
  } catch (error) {
    if (error?.code === 'ENOENT') return true;
    throw error;
  }
}

function isContained(parent, child) {
  const value = relative(parent, child);
  return value === '' || (!value.startsWith(`..${sep}`) && value !== '..');
}

test('dry run stages outside public and leaves the destination unchanged', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-test-'));
  const originalLog = console.log;
  const logs = [];
  try {
    const source = join(root, 'raw-source');
    const destinationParent = join(root, 'public');
    const destination = join(destinationParent, 'exports');
    await createRawExportFixture(source);
    await mkdir(destination, {recursive: true});
    await writeFile(join(destination, 'sentinel.txt'), 'unchanged\n');

    console.log = (...args) => {
      logs.push(args.map(String).join(' '));
      originalLog(...args);
    };
    await importExportData({source, destination, dryRun: true});

    assert.equal(await readFile(join(destination, 'sentinel.txt'), 'utf8'), 'unchanged\n');
    const stagingLog = logs.find(line => line.includes('out-of-public staging:'));
    assert.ok(stagingLog, 'expected an explicit staging-path log');
    const stagingPath = stagingLog.slice(stagingLog.indexOf('staging:') + 'staging:'.length).trim();
    assert.equal(isContained(destinationParent, stagingPath), false);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    console.log = originalLog;
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a source-root symlink before creating import work data', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-symlink-test-'));
  try {
    const source = join(root, 'raw-source');
    const sourceLink = join(root, 'raw-source-link');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source);
    await symlink(source, sourceLink, 'dir');
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source: sourceLink, destination, dryRun: true}),
      /source is symlink|source-root symlinks are refused/i,
    );
    assert.equal((await lstat(sourceLink)).isSymbolicLink(), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a symlinked top-level image root before staging or optimization', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-image-root-link-test-'));
  try {
    const source = join(root, 'raw-source');
    const externalIcons = join(root, 'external-icons');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source);
    await mkdir(externalIcons);
    await writeNonUniformImage(join(externalIcons, 'stone.png'));
    await rm(join(source, 'icons'), {recursive: true});
    await symlink(externalIcons, join(source, 'icons'), 'dir');
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /unsupported filesystem entry.*icons|symlinks and special files are refused/i,
    );
    assert.equal(await pathIsMissing(join(externalIcons, 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a nested directory symlink before staging or optimization', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-nested-link-test-'));
  try {
    const source = join(root, 'raw-source');
    const externalDirectory = join(root, 'external-recipes');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source);
    await mkdir(externalDirectory);
    await symlink(externalDirectory, join(source, 'recipes', 'linked-recipes'), 'dir');
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /unsupported filesystem entry.*linked-recipes|symlinks and special files are refused/i,
    );
    assert.equal(await pathIsMissing(join(source, 'icons', 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a special filesystem entry before staging or optimization', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-special-entry-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    const fifoPath = join(source, 'export-control.fifo');
    await createRawExportFixture(source);
    await execFileAsync('/usr/bin/mkfifo', [fifoPath]);
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /unsupported filesystem entry.*export-control\.fifo|symlinks and special files are refused/i,
    );
    assert.equal(await pathIsMissing(join(source, 'icons', 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a source that becomes unsafe while the clone traversal is starting', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-staging-recheck-test-'));
  const originalLog = console.log;
  let sourceMutated = false;
  try {
    const source = join(root, 'raw-source');
    const externalIcons = join(root, 'external-icons');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source);
    await mkdir(dirname(destination), {recursive: true});

    console.log = (...args) => {
      const line = args.map(String).join(' ');
      originalLog(...args);
      if (!sourceMutated && line.includes('Source filesystem preflight accepted')) {
        renameSync(join(source, 'icons'), externalIcons);
        symlinkSync(externalIcons, join(source, 'icons'), 'dir');
        sourceMutated = true;
      }
    };

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /required copy-on-write raw export clone failed|symlinks and special files are refused/i,
    );
    assert.equal(sourceMutated, true);
    assert.equal(await pathIsMissing(join(externalIcons, 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    console.log = originalLog;
    await rm(root, {recursive: true, force: true});
  }
});

test('publishes the packed dataset atomically and removes rollback work data', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-publish-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source);
    await mkdir(destination, {recursive: true});
    await writeFile(join(destination, 'sentinel.txt'), 'old dataset\n');

    await importExportData({source, destination});

    assert.equal(await pathIsMissing(join(destination, 'sentinel.txt')), true);
    assert.equal(await pathIsMissing(join(destination, 'icons')), true);
    assert.equal(await pathIsMissing(join(destination, 'assets-index.json')), true);
    assert.equal(await pathIsMissing(join(destination, 'assets', 'pack-000.bin')), false);
    const manifest = await readJson(join(destination, 'manifest.json'));
    assert.match(manifest.publicationId, PUBLICATION_ID_PATTERN);
    assert.equal(manifest.web?.packedImages, 'coordinate-v1');
    assert.equal(manifest.web?.maxPackBytes, 1024 * 1024);
    const items = await readJson(join(destination, 'items.json'));
    const categories = await readJson(join(destination, 'categories.json'));
    assert.match(items.items[0].icon, /^assets\/s\/000-\d+-\d+\.webp$/);
    assert.match(categories.categories[0].icon, /^assets\/s\/000-\d+-\d+\.webp$/);

    const itemShardSource = `${JSON.stringify(items.items)}\n`;
    const itemShardPath = join(destination, 'data', 'items', 'part-000.json');
    await mkdir(dirname(itemShardPath), {recursive: true});
    await writeFile(itemShardPath, itemShardSource);
    await writeJson(join(destination, 'items.json'), {
      format: 'mrt-sharded-json-v1',
      kind: 'array',
      count: items.items.length,
      parts: [
        {
          path: 'data/items/part-000.json',
          start: 0,
          count: items.items.length,
          bytes: Buffer.byteLength(itemShardSource),
        },
      ],
    });
    assert.equal((await validateExportData(destination)).items, 1);

    const orphanShardPath = join(destination, 'data', 'items', 'part-999.json');
    await writeFile(orphanShardPath, '[]\n');
    await assert.rejects(validateExportData(destination), /Unreferenced JSON shard/);
    await rm(orphanShardPath);

    const coordinateMatch = categories.categories[0].icon.match(
      /^(assets\/s\/\d+-)(\d+)(-\d+\.webp)$/,
    );
    assert.ok(coordinateMatch);
    categories.categories[0].icon =
      coordinateMatch[1] + String(Number(coordinateMatch[2]) + 1) + coordinateMatch[3];
    await writeJson(join(destination, 'categories.json'), categories);
    await assert.rejects(validateExportData(destination), /gap or overlap/);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('restores the prior live dataset when the real staging rename fails', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-rollback-test-'));
  const stagingParent = join(root, 'staging-parent');
  try {
    const staging = join(stagingParent, 'staging');
    const destinationParent = join(root, 'public');
    const destination = join(destinationParent, 'exports');
    const backup = join(destinationParent, '.rollback-backup');
    await mkdir(staging, {recursive: true});
    await mkdir(destination, {recursive: true});
    await writeFile(join(staging, 'new.txt'), 'new dataset\n');
    await writeFile(join(destination, 'old.txt'), 'old dataset\n');

    // The backup rename uses destinationParent and succeeds. Removing staging
    // from its read-only parent then fails, exercising the actual rollback path.
    await chmod(stagingParent, 0o555);
    await assert.rejects(
      publishTransactional(staging, destination, backup),
      /EACCES|EPERM|permission denied|operation not permitted/i,
    );
    await chmod(stagingParent, 0o755);

    assert.equal(await readFile(join(destination, 'old.txt'), 'utf8'), 'old dataset\n');
    assert.equal(await readFile(join(staging, 'new.txt'), 'utf8'), 'new dataset\n');
    assert.equal(await pathIsMissing(backup), true);
  } finally {
    try {
      await chmod(stagingParent, 0o755);
    } catch (error) {
      if (error?.code !== 'ENOENT') {
        console.error(`Test cleanup could not restore permissions on ${stagingParent}.`, error);
      }
    }
    await rm(root, {recursive: true, force: true});
  }
});
