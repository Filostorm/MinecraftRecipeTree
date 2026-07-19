import assert from 'node:assert/strict';
import {link, mkdir, mkdtemp, realpath, rm, symlink, unlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {
  MAX_PUBLICATION_PLAN_BYTES,
  PUBLICATION_PLAN_FORMAT,
  loadPreparedPlan,
  parsePublishModpackArguments,
  prepareModpackPublication,
  requireFullPublicationManifest,
  requirePublicationPlan,
  uploadPreparedModpackPublication,
} from './publish-modpack.mjs';
import {
  createRawExportFixture,
  readJson,
  writeJson,
  writeNonUniformImage,
} from './test-export-fixture.mjs';

const ID_A = 'a'.repeat(64);
const ID_B = 'b'.repeat(64);
const ID_C = 'c'.repeat(64);
const ID_D = 'd'.repeat(64);

function plan(overrides = {}) {
  return {
    format: PUBLICATION_PLAN_FORMAT,
    createdAt: '2026-07-19T12:00:00.000Z',
    profile: 'multiblock-madness-1.12.2',
    pack: {name: 'Multiblock Madness', version: '3.2.3', identitySource: 'explicit-request'},
    minecraftVersion: '1.12.2',
    slug: 'multiblock-madness',
    publicationId: ID_A,
    previewAssetSetId: ID_B,
    paths: {
      packedExport: 'packed-export',
      corePublication: 'core-publication/publication.json',
      previewSidecar: 'preview-sidecar',
    },
    ...overrides,
  };
}

function mm2Plan(overrides = {}) {
  return plan({
    profile: 'multiblock-madness-2-1.18.2',
    pack: {
      name: 'Multiblock Madness 2',
      version: '1.0.0',
      identitySource: 'explicit-request',
    },
    minecraftVersion: '1.18.2',
    slug: 'multiblock-madness-2',
    ...overrides,
  });
}

async function preparedWorkspace(t, publicationPlan = plan()) {
  const root = await mkdtemp(join(tmpdir(), 'mrt-publish-modpack-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  await mkdir(join(root, 'packed-export'));
  await mkdir(join(root, 'core-publication'));
  await mkdir(join(root, 'preview-sidecar'));
  await writeFile(join(root, 'publication-plan.json'), `${JSON.stringify(publicationPlan)}\n`);
  await writeFile(join(root, 'packed-export', 'manifest.json'), '{}\n');
  await writeFile(join(root, 'core-publication', 'publication.json'), '{}\n');
  await writeFile(join(root, 'preview-sidecar', 'manifest.json'), '{}\n');
  return root;
}

async function rawPublicationFixture(t, publicationPlan, {qualitySample} = {}) {
  const root = await mkdtemp(join(tmpdir(), 'mrt-publish-modpack-raw-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  const source = join(root, 'raw');
  const workspace = join(root, 'publication');
  await createRawExportFixture(source, {iconScale: 1, recipeScale: 2});
  await writeNonUniformImage(join(source, 'recipes', 'minecraft_crafting', 'r0.png'), 32);
  await writeJson(join(source, 'recipes', 'minecraft_crafting', 'recipes.json'), [{
    id: 'minecraft:test',
    img: 'r0.png',
    w: 16,
    h: 16,
    in: [[['minecraft:stone', 1]]],
    out: [[['minecraft:stone', 1]]],
  }]);
  await writeJson(join(source, 'index.json'), {
    'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
  });
  const categories = await readJson(join(source, 'categories.json'));
  categories.categories[0].count = 1;
  await writeJson(join(source, 'categories.json'), categories);
  const manifest = await readJson(join(source, 'manifest.json'));
  manifest.minecraft = publicationPlan.minecraftVersion;
  manifest.pack = publicationPlan.pack;
  manifest.counts.recipes = 1;
  if (publicationPlan.profile === 'multiblock-madness-2-1.18.2') {
    manifest.counts.nativeIconCorrections = 0;
    manifest.diagnostics.nativeIconCorrections = 0;
    manifest.diagnostics.transparentIcons = 0;
  }
  if (qualitySample !== undefined) manifest.qualitySample = qualitySample;
  await writeJson(join(source, 'manifest.json'), manifest);
  return {root, source, workspace};
}

const quietLogger = Object.freeze({
  info() {},
  warn() {},
  error() {},
});

test('parses concise prepare and upload commands without accepting token values', () => {
  assert.deepEqual(parsePublishModpackArguments([
    'prepare',
    '--source', '/exports',
    '--workspace', '/work/new',
    '--profile', 'multiblock-madness-1.12.2',
    '--staging-mode', 'copy',
  ]), {
    command: 'prepare',
    source: '/exports',
    workspace: '/work/new',
    profile: 'multiblock-madness-1.12.2',
    stagingMode: 'copy',
  });
  assert.deepEqual(parsePublishModpackArguments([
    'upload', '--workspace', '/work/new', '--channel-action', 'update', '--default', 'false',
    '--core-token-file', '/secret/core',
  ]), {
    command: 'upload',
    workspace: '/work/new',
    channelAction: 'update',
    isDefault: false,
    coreTokenFile: '/secret/core',
  });
  assert.throws(
    () => parsePublishModpackArguments([
      'prepare', '--source', '/exports', '--workspace', '/work/new',
      '--profile', 'multiblock-madness-1.12.2', '--default', 'true',
    ]),
    /Unsupported prepare argument: --default/,
  );
  assert.throws(
    () => parsePublishModpackArguments(['upload', '--workspace', '/work/new']),
    /channelAction, isDefault/,
  );
  assert.throws(
    () => parsePublishModpackArguments(['upload', '--workspace', '/work/new', '--token', 'secret']),
    /Unsupported upload argument/,
  );
});

test('publication plan validation binds pack metadata and exact artifact paths', () => {
  assert.deepEqual(requirePublicationPlan(plan()).pack, plan().pack);
  assert.throws(() => requirePublicationPlan({...plan(), unexpected: true}), /exact top-level/);
  assert.throws(
    () => requirePublicationPlan(plan({paths: {...plan().paths, packedExport: '../escape'}})),
    /fixed canonical path packed-export/,
  );
  assert.throws(
    () => requirePublicationPlan(plan({
      paths: {...plan().paths, corePublication: 'core-publication/other.json'},
    })),
    /fixed canonical path core-publication\/publication\.json/,
  );
  assert.throws(
    () => requirePublicationPlan(plan({pack: {name: 'Pack', identitySource: 'curseforge'}})),
    /version is required/,
  );
});

test('production manifest gate rejects own qualitySample fields and admits full manifests', () => {
  for (const publicationPlan of [plan(), mm2Plan()]) {
    const full = {
      minecraft: publicationPlan.minecraftVersion,
      pack: publicationPlan.pack,
    };
    assert.equal(requireFullPublicationManifest(full, 'Fixture manifest'), full);
    assert.throws(
      () => requireFullPublicationManifest({...full, qualitySample: {}}, 'Fixture manifest'),
      /Fixture manifest contains manifest\.qualitySample.*diagnostic mini export/,
    );
  }
});

test('preparation rejects MM1 and MM2 mini exports before creating a workspace', async t => {
  for (const publicationPlan of [plan(), mm2Plan()]) {
    const fixture = await rawPublicationFixture(t, publicationPlan, {qualitySample: {}});
    await assert.rejects(
      prepareModpackPublication({
        source: fixture.source,
        workspace: fixture.workspace,
        profile: publicationPlan.profile,
        slug: publicationPlan.slug,
        stagingMode: 'copy',
        concurrency: 1,
        logger: quietLogger,
      }),
      /Raw manifest\.json contains manifest\.qualitySample.*full exporter result/,
    );
    await assert.rejects(realpath(fixture.workspace), error => error?.code === 'ENOENT');
  }
});

test('preparation admits full MM1 and MM2 exports and commits production plans', async t => {
  for (const publicationPlan of [plan(), mm2Plan()]) {
    const fixture = await rawPublicationFixture(t, publicationPlan);
    const prepared = await prepareModpackPublication({
      source: fixture.source,
      workspace: fixture.workspace,
      profile: publicationPlan.profile,
      slug: publicationPlan.slug,
      stagingMode: 'copy',
      concurrency: 1,
      logger: quietLogger,
    });
    assert.equal(prepared.profile, publicationPlan.profile);
    assert.equal(prepared.slug, publicationPlan.slug);
    assert.deepEqual(prepared.pack, publicationPlan.pack);
    assert.match(prepared.publicationId, /^[a-f0-9]{64}$/);
    assert.match(prepared.previewAssetSetId, /^[a-f0-9]{64}$/);
  }
});

test('prepared-plan loading accepts only the fixed plain artifact tree', async t => {
  const root = await preparedWorkspace(t);
  const loaded = await loadPreparedPlan(root);
  const canonicalRoot = await realpath(root);
  assert.equal(loaded.plan.slug, 'multiblock-madness');
  assert.deepEqual(loaded.paths, {
    packedExport: join(canonicalRoot, 'packed-export'),
    corePublication: join(canonicalRoot, 'core-publication', 'publication.json'),
    previewSidecar: join(canonicalRoot, 'preview-sidecar'),
  });
  assert.deepEqual(loaded.counts, {directories: 3, files: 3});
});

test('prepared-plan loading bounds publication-plan.json before parsing it', async t => {
  const root = await preparedWorkspace(t);
  await writeFile(
    join(root, 'publication-plan.json'),
    Buffer.alloc(MAX_PUBLICATION_PLAN_BYTES + 1, 0x20),
  );
  await assert.rejects(
    loadPreparedPlan(root),
    new RegExp(`invalid byte length ${MAX_PUBLICATION_PLAN_BYTES + 1}`),
  );
});

test('prepared-plan loading rejects a symlinked plan without following it', async t => {
  if (process.platform === 'win32') {
    t.skip('Creating filesystem symlinks requires an elevated Windows test environment.');
    return;
  }
  const root = await preparedWorkspace(t);
  const planPath = join(root, 'publication-plan.json');
  const target = join(root, 'untrusted-plan.json');
  await writeFile(target, `${JSON.stringify(plan())}\n`);
  await unlink(planPath);
  await symlink(target, planPath);
  await assert.rejects(loadPreparedPlan(root), /no-follow regular file/);
});

test('prepared-plan loading rejects a symlinked intermediate artifact directory', async t => {
  if (process.platform === 'win32') {
    t.skip('Creating filesystem symlinks requires an elevated Windows test environment.');
    return;
  }
  const root = await preparedWorkspace(t);
  const target = join(root, 'sidecar-target');
  await mkdir(target);
  await writeFile(join(target, 'manifest.json'), '{}\n');
  await rm(join(root, 'preview-sidecar'), {recursive: true});
  await symlink(target, join(root, 'preview-sidecar'), 'dir');
  await assert.rejects(loadPreparedPlan(root), /no-follow plain directory/);
});

test('upload preflight rejects artifact symlinks before token files are loaded', async t => {
  if (process.platform === 'win32') {
    t.skip('Creating filesystem symlinks requires an elevated Windows test environment.');
    return;
  }
  const root = await preparedWorkspace(t);
  await symlink('../packed-export/manifest.json', join(root, 'preview-sidecar', 'linked.json'));
  await assert.rejects(
    uploadPreparedModpackPublication({
      workspace: root,
      channelAction: 'update',
      isDefault: false,
      coreTokenFile: join(root, 'missing-core-token'),
      previewTokenFile: join(root, 'missing-preview-token'),
      logger: quietLogger,
    }),
    /must not contain symbolic links/,
  );
});

test('prepared artifact trees reject hard-linked regular files', async t => {
  const root = await preparedWorkspace(t);
  await link(
    join(root, 'preview-sidecar', 'manifest.json'),
    join(root, 'preview-sidecar', 'manifest-copy.json'),
  );
  await assert.rejects(loadPreparedPlan(root), /must not contain hard-linked files/);
});

function currentDescriptor() {
  return {
    slug: 'multiblock-madness',
    displayName: 'Multiblock Madness',
    minecraftVersion: '1.12.2',
    packVersion: '3.2.2',
    publicationId: ID_C,
    previewAssetSetId: ID_D,
    isDefault: true,
  };
}

async function writeValidPackedManifest(root, publicationPlan = plan(), overrides = {}) {
  await writeFile(join(root, 'packed-export', 'manifest.json'), `${JSON.stringify({
    publicationId: publicationPlan.publicationId,
    minecraft: publicationPlan.minecraftVersion,
    pack: publicationPlan.pack,
    ...overrides,
  })}\n`);
}

function orchestrationDependencies(events, {preflightFailure = null} = {}) {
  const record = name => async options => {
    events.push({name, options});
    return undefined;
  };
  return {
    fetchPublishingCatalog: async options => {
      events.push({name: 'catalog', options});
      return [currentDescriptor()];
    },
    readCoreDatasetIngestToken: async options => {
      events.push({name: 'core-token', options});
      return 'core-token-'.padEnd(48, 'c');
    },
    readPreviewIngestToken: async options => {
      events.push({name: 'preview-token', options});
      return 'preview-token-'.padEnd(48, 'p');
    },
    preflightIngestionEndpoints: async options => {
      events.push({name: 'ingestion-preflight', options});
      if (preflightFailure) throw preflightFailure;
      return {coreState: 'absent', previewState: 'absent'};
    },
    uploadCoreDatasetPublication: record('upload-core'),
    uploadRecipePreviewSidecar: record('upload-preview'),
    verifyPublicCoreDatasetPublication: record('verify-core'),
    verifyRemoteRecipePreviewSidecar: record('verify-preview'),
    administerDatasetChannel: record('activate-channel'),
  };
}

test('upload orchestration preflights both targets and CAS-activates only after verification', async t => {
  const root = await preparedWorkspace(t);
  await writeValidPackedManifest(root);
  const events = [];
  const result = await uploadPreparedModpackPublication({
    workspace: root,
    channelAction: 'update',
    isDefault: true,
    appOrigin: 'https://viewer.test',
    logger: quietLogger,
    dependencies: orchestrationDependencies(events),
  });
  assert.deepEqual(events.map(event => event.name), [
    'catalog',
    'core-token',
    'preview-token',
    'ingestion-preflight',
    'upload-core',
    'upload-preview',
    'verify-core',
    'verify-preview',
    'activate-channel',
  ]);
  const activation = events.at(-1).options;
  assert.equal(activation.expectedPreviousPublicationId, ID_C);
  assert.equal(activation.isDefault, true);
  assert.equal(result.channelAction, 'update');
  assert.equal(result.shareUrl, 'https://viewer.test/?pack=multiblock-madness');
});

test('upload rejects prepared MM1 and MM2 mini exports before catalog or credential access', async t => {
  for (const publicationPlan of [plan(), mm2Plan()]) {
    const root = await preparedWorkspace(t, publicationPlan);
    await writeValidPackedManifest(root, publicationPlan, {qualitySample: {}});
    const events = [];
    await assert.rejects(
      uploadPreparedModpackPublication({
        workspace: root,
        channelAction: 'create',
        isDefault: false,
        appOrigin: 'https://viewer.test',
        logger: quietLogger,
        dependencies: orchestrationDependencies(events),
      }),
      /Packed manifest\.json contains manifest\.qualitySample.*full exporter result/,
    );
    assert.deepEqual(events, []);
  }
});

test('authenticated endpoint-preflight failure starts no bulk upload', async t => {
  const root = await preparedWorkspace(t);
  await writeValidPackedManifest(root);
  const events = [];
  await assert.rejects(
    uploadPreparedModpackPublication({
      workspace: root,
      channelAction: 'update',
      isDefault: true,
      appOrigin: 'https://viewer.test',
      logger: quietLogger,
      dependencies: orchestrationDependencies(events, {
        preflightFailure: new Error('preview target is not configured'),
      }),
    }),
    /preview target is not configured/,
  );
  assert.equal(events.some(event => event.name.startsWith('upload-')), false);
  assert.equal(events.some(event => event.name === 'activate-channel'), false);
});
