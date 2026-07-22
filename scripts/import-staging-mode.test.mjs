import assert from 'node:assert/strict';
import {access, mkdir, mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join} from 'node:path';
import test from 'node:test';
import {MULTIBLOCK_MADNESS_112_PROFILE} from './export-quality-policy.mjs';
import {
  importExportData,
  importWorkspaceRootForDestination,
} from './import-export-data.mjs';
import {
  configureMultiblockExportFixture,
  createRawExportFixture,
} from './test-export-fixture.mjs';

async function missing(path) {
  try {
    await access(path);
    return false;
  } catch (error) {
    if (error?.code === 'ENOENT') return true;
    throw error;
  }
}

test('explicit full-copy staging validates without selecting clone fallback', async () => {
  const root = await mkdtemp(join(tmpdir(), 'mrt-copy-stage-test-'));
  try {
    const source = join(root, 'raw');
    const destination = join(root, 'published', 'exports');
    await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
    await configureMultiblockExportFixture(source, MULTIBLOCK_MADNESS_112_PROFILE);
    await mkdir(dirname(destination), {recursive: true});

    await importExportData({
      source,
      destination,
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      dryRun: true,
      stagingMode: 'copy',
    });

    assert.equal(await missing(destination), true);
    assert.equal(await missing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
