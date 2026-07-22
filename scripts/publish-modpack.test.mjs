import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {link, mkdir, mkdtemp, realpath, rm, symlink, unlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {
  ACTIVATION_THRESHOLDS,
  COLD_BROWSER_DEFINITION,
  COLD_DATASET_REPORT_SCHEMA_VERSION,
  classifyActivationGate,
  digestBuildTree,
  digestColdDatasetBenchmarkSource,
} from './benchmark-cold-dataset.mjs';
import {
  GTNH_STRUCTURED_DATA_ONLY_POLICY,
  GTNH_VISUAL_ASSETS_POLICY,
  MAX_ACTIVATION_BENCHMARK_REPORT_BYTES,
  MAX_PUBLICATION_PLAN_BYTES,
  PUBLICATION_PLAN_FORMAT,
  loadPreparedPlan,
  parsePublishModpackArguments,
  prepareModpackPublication,
  requireApprovedPreparedAcceptanceMigration,
  requireFullPublicationManifest,
  requirePublicationPolicyBinding,
  requirePublicationPlan,
  uploadPreparedModpackPublication,
} from './publish-modpack.mjs';
import {buildPublicationExporterAcceptance} from './publication-exporter-acceptance.mjs';
import {
  configureMultiblockExportFixture,
  createRawExportFixture,
  readJson,
  writeJson,
  writeNonUniformImage,
} from './test-export-fixture.mjs';

const ID_A = 'a'.repeat(64);
const ID_B = 'b'.repeat(64);
const ID_C = 'c'.repeat(64);
const ID_D = 'd'.repeat(64);

test('prepared acceptance migration is restricted to exact historical publication evidence', () => {
  const candidate = {
    profile: 'meatballcraft-1.12.2',
    publicationId: '04c674ab74eeeaea151c9b985191f09e2be42156a879bb0493e2e29f94f3d46a',
    exporterAcceptance: {
      receiptSha256: '16d87871afd88f1f2dd06733871e845a73c0b8352ff67a82a2a07b0a9da11342',
      receipt: {
        validationPolicy: {
          sha256: '93573e17f1007453531d49b41377be69f84cc3d5e86c924d112977af4ab65008',
        },
        release: {
          sha256: '563536a2f5f034bf55c5e89e61fd3bbc91440243a8d0aad65919c4c8bed00f23',
        },
        exportTree: {
          sha256: 'e3e8627ceaf3c4426e5a2084ae415005fc32d9292c7f19a40d9d6e51db4b4d2a',
        },
      },
    },
  };
  assert.equal(
    requireApprovedPreparedAcceptanceMigration(candidate).publicationId,
    candidate.publicationId,
  );
  assert.throws(
    () => requireApprovedPreparedAcceptanceMigration({
      ...candidate,
      exporterAcceptance: {...candidate.exporterAcceptance, receiptSha256: ID_A},
    }),
    /not covered by an exact approved/,
  );
});

function exporterAcceptance(publicationPlan) {
  const releaseId = publicationPlan.minecraftVersion === '1.18.2'
    ? 'forge-rei-1.18.2'
    : publicationPlan.minecraftVersion === '1.7.10'
      ? 'forge-nei-gtnh-1.7.10'
    : 'forge-hei-1.12.2';
  return buildPublicationExporterAcceptance({
    format: 'mrt-exporter-acceptance-v1',
    acceptedAt: '2026-07-19T11:00:00.000Z',
    release: {
      id: releaseId,
      version: '1.0.1',
      filename: `recipe-tree-exporter-${releaseId}-1.0.1.jar`,
      sha256: ID_C,
      bytes: 1024,
    },
    qualityProfile: publicationPlan.profile,
    exporterBuild: {
      format: 'mrt-exporter-build-v1',
      exporterId: releaseId,
      minecraftVersion: publicationPlan.minecraftVersion,
      algorithm: 'sha256',
      payloadSha256: ID_D,
    },
    exportTree: {
      format: 'mrt-export-tree-v1',
      algorithm: 'sha256',
      sha256: ID_A,
      files: 10,
      bytes: 2048,
    },
    validationPolicy: {
      format: 'mrt-exporter-acceptance-policy-v1',
      sha256: ID_B,
    },
    exportManifest: {
      sha256: ID_C,
      bytes: 512,
      generatedAt: '2026-07-19T10:00:00.000Z',
      minecraft: publicationPlan.minecraftVersion,
      counts: {items: 1, recipes: 1, categories: 1, mobs: 0, blockDrops: 0},
      pack: publicationPlan.pack,
    },
  });
}

function plan(overrides = {}) {
  const candidate = {
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
  return {
    ...candidate,
    exporterAcceptance: Object.prototype.hasOwnProperty.call(overrides, 'exporterAcceptance')
      ? overrides.exporterAcceptance
      : exporterAcceptance(candidate),
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

function gtnhPlan(overrides = {}) {
  return plan({
    profile: 'gtnh-1.7.10',
    pack: {
      name: 'GT New Horizons',
      version: '2.8.4',
      identitySource: 'explicit-request',
    },
    minecraftVersion: '1.7.10',
    slug: 'gt-new-horizons',
    publicationPolicy: GTNH_STRUCTURED_DATA_ONLY_POLICY,
    ...overrides,
  });
}

async function activationBenchmarkFixture(t, publicationPlan = gtnhPlan(), transform = value => value) {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'mrt-publish-benchmark-test-')));
  t.after(() => rm(root, {recursive: true, force: true}));
  const dist = join(root, 'dist');
  await mkdir(join(dist, 'client'), {recursive: true});
  await writeFile(join(dist, 'client', 'app.js'), 'globalThis.__mrtTestBuild = true;\n');
  const [build, sourceSha256] = await Promise.all([
    digestBuildTree(dist),
    digestColdDatasetBenchmarkSource(),
  ]);
  const coreBootstrapPaths = [
    'blockdrops.json',
    'categories.json',
    'index.json',
    'items.json',
    'manifest.json',
    'mobs.json',
  ];
  const previewBootstrapPaths = [];
  const staticMetrics = {
    itemsBootstrapBytes: 1024,
    indexBootstrapBytes: 2048,
    combinedDatasetBootstrapBytes: 3072,
    bootstrapDocumentCount: 7,
    coreBootstrapPaths,
    previewBootstrapPaths,
    previewManifestBytes: 0,
  };
  const servedObjects = [
    {key: 'catalog:/api/datasets', kind: 'document', requests: 1, responseBodyBytes: 100},
    ...coreBootstrapPaths.map(path => ({
      key: `core:${path}`,
      kind: 'document',
      requests: path === 'manifest.json' ? 2 : 1,
      responseBodyBytes: path === 'manifest.json' ? 200 : 100,
    })),
  ];
  const runs = Array.from({length: 3}, (_, index) => ({
    run: index + 1,
    readyMs: 1000 + index,
    peakHeapBytes: 64 * 1024 * 1024 + index,
    settledHeapBytes: 48 * 1024 * 1024 + index,
    heapSampleCount: 10,
    settledHeapSamples: [
      47 * 1024 * 1024,
      48 * 1024 * 1024 + index,
      47 * 1024 * 1024 + 1,
    ],
    cdpTraffic: {
      datasetRequests: 8,
      datasetCompletedRequests: 8,
      datasetDecodedBytes: 800,
      datasetEncodedBytes: 800,
    },
    proxyTraffic: {
      requests: 12,
      datasetRequests: 8,
      catalogRequests: 1,
      documentRequests: 8,
      imageRequests: 0,
      documentBodyBytes: 800,
      imageBodyBytes: 0,
      totalDatasetBodyBytes: 800,
      bootstrapDocumentCount: 7,
      servedObjects,
    },
  }));
  const gate = classifyActivationGate(staticMetrics, runs);
  const candidate = {
    schemaVersion: COLD_DATASET_REPORT_SCHEMA_VERSION,
    generatedAt: '2026-07-20T12:00:00.000Z',
    benchmark: {
      sourceSha256,
      runs: runs.length,
      coldDefinition: COLD_BROWSER_DEFINITION,
    },
    platform: {
      node: process.version,
      platform: process.platform,
      arch: process.arch,
      release: 'test-release',
      logicalCpuCount: 8,
    },
    chrome: {
      protocolVersion: '1.3',
      product: 'Chrome/126.0.0.0',
      revision: '@test-revision',
      userAgent: 'Mozilla/5.0 test Chrome',
      jsVersion: '12.6.0',
    },
    build,
    dataset: {
      slug: publicationPlan.slug,
      displayName: publicationPlan.pack.name,
      minecraftVersion: publicationPlan.minecraftVersion,
      packVersion: publicationPlan.pack.version,
      publicationId: publicationPlan.publicationId,
      previewAssetSetId: publicationPlan.previewAssetSetId,
      isDefault: true,
    },
    staticMetrics,
    thresholds: ACTIVATION_THRESHOLDS,
    runs,
    ...gate,
  };
  const report = transform(structuredClone(candidate));
  const reportBytes = Buffer.from(`${JSON.stringify(report, null, 2)}\n`);
  const reportPath = join(root, 'activation-report.json');
  await writeFile(reportPath, reportBytes, {mode: 0o600});
  return {
    build,
    dist,
    report,
    reportPath,
    reportSha256: createHash('sha256').update(reportBytes).digest('hex'),
    sourceSha256,
  };
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
  const manifest = await configureMultiblockExportFixture(source, publicationPlan.profile);
  assert.equal(manifest.minecraft, publicationPlan.minecraftVersion);
  assert.deepEqual(manifest.pack, publicationPlan.pack);
  manifest.counts.recipes = 1;
  if (qualitySample !== undefined) manifest.qualitySample = qualitySample;
  await writeJson(join(source, 'manifest.json'), manifest);
  return {root, source, workspace};
}

const quietLogger = Object.freeze({
  info() {},
  warn() {},
  error() {},
});

function preparationAcceptanceDependencies(publicationPlan) {
  const binding = publicationPlan.exporterAcceptance;
  return {
    async loadCurrentPublicationExporterAcceptance({expectedBinding}) {
      assert.deepEqual(expectedBinding ?? binding, binding);
      return {binding};
    },
    async verifyPublicationExporterBuildFile({binding: received}) {
      assert.deepEqual(received, binding);
    },
    async verifyAcceptedRawPublicationExport({binding: received}) {
      assert.deepEqual(received, binding);
    },
  };
}

test('parses concise prepare and upload commands without accepting token values', () => {
  assert.deepEqual(parsePublishModpackArguments([
    'prepare',
    '--source', '/exports',
    '--workspace', '/work/new',
    '--profile', 'multiblock-madness-1.12.2',
    '--release', 'forge-hei-1.12.2',
    '--staging-mode', 'copy',
  ]), {
    command: 'prepare',
    source: '/exports',
    workspace: '/work/new',
    profile: 'multiblock-madness-1.12.2',
    releaseId: 'forge-hei-1.12.2',
    stagingMode: 'copy',
  });
  assert.deepEqual(parsePublishModpackArguments([
    'upload', '--workspace', '/work/new', '--channel-action', 'update', '--default', 'false',
    '--core-token-file', '/secret/core', '--benchmark-report', '/reports/cold.json',
    '--dist', '/viewer/dist',
  ]), {
    command: 'upload',
    workspace: '/work/new',
    channelAction: 'update',
    isDefault: false,
    coreTokenFile: '/secret/core',
    benchmarkReport: '/reports/cold.json',
    dist: '/viewer/dist',
  });
  assert.throws(
    () => parsePublishModpackArguments([
      'prepare', '--source', '/exports', '--workspace', '/work/new',
      '--profile', 'multiblock-madness-1.12.2', '--release', 'forge-hei-1.12.2',
      '--default', 'true',
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
  assert.throws(
    () => parsePublishModpackArguments([
      'upload', '--workspace', '/work/new', '--channel-action', 'update', '--default', 'false',
      '--benchmark-report', '/reports/cold.json',
    ]),
    /--benchmark-report and --dist must be supplied together/,
  );
  assert.throws(
    () => parsePublishModpackArguments([
      'prepare', '--source', '/exports', '--workspace', '/work/new',
      '--profile', 'gtnh-1.7.10', '--release', 'forge-nei-gtnh-1.7.10',
      '--slug', 'mutable-lookalike',
    ]),
    /requires isolated channel slug gt-new-horizons/,
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
  const validPlan = plan();
  assert.throws(
    () => requirePublicationPlan({
      ...validPlan,
      pack: {name: 'Pack', identitySource: 'curseforge'},
    }),
    /version is required/,
  );
  assert.throws(
    () => requirePublicationPlan(plan({
      profile: 'multiblock-madness-2-1.18.2',
      minecraftVersion: '1.18.2',
      exporterAcceptance: validPlan.exporterAcceptance,
    })),
    /crosses an incompatible quality profile, Minecraft version boundary/,
  );
  assert.throws(
    () => requirePublicationPlan(mm2Plan({slug: 'multiblock-madness'})),
    /requires isolated channel slug multiblock-madness-2/,
  );
  assert.equal(requirePublicationPlan(gtnhPlan()).slug, 'gt-new-horizons');
  assert.throws(
    () => requirePublicationPlan(gtnhPlan({slug: 'gtnh'})),
    /requires isolated channel slug gt-new-horizons/,
  );
});

test('GTNH publication plans require the exact structured-data-only policy while other profiles reject it', () => {
  assert.equal(
    requirePublicationPlan(gtnhPlan()).publicationPolicy,
    GTNH_STRUCTURED_DATA_ONLY_POLICY,
  );
  for (const publicationPolicy of [undefined, 'gtnh-structured-data-only-v2', '', null]) {
    assert.throws(
      () => requirePublicationPlan(gtnhPlan({publicationPolicy})),
      new RegExp(`must be exactly ${GTNH_STRUCTURED_DATA_ONLY_POLICY}`),
    );
  }
  assert.throws(
    () => requirePublicationPlan(plan({publicationPolicy: GTNH_STRUCTURED_DATA_ONLY_POLICY})),
    /reserved for gtnh-1\.7\.10/,
  );
  assert.equal(
    requirePublicationPolicyBinding('gtnh-1.7.10', GTNH_STRUCTURED_DATA_ONLY_POLICY),
    GTNH_STRUCTURED_DATA_ONLY_POLICY,
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
        releaseId: publicationPlan.exporterAcceptance.receipt.release.id,
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

test('GTNH preparation fixes its channel slug and rejects explicit mismatches before setup', async t => {
  const root = await mkdtemp(join(tmpdir(), 'mrt-publish-gtnh-slug-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  const source = join(root, 'raw');
  await mkdir(source);
  const publicationPlan = gtnhPlan();
  await writeJson(join(source, 'manifest.json'), {
    minecraft: publicationPlan.minecraftVersion,
    pack: publicationPlan.pack,
  });
  let acceptanceLoads = 0;
  await assert.rejects(
    prepareModpackPublication({
      source,
      workspace: join(root, 'mismatch'),
      profile: publicationPlan.profile,
      releaseId: publicationPlan.exporterAcceptance.receipt.release.id,
      slug: 'gtnh',
      logger: quietLogger,
      dependencies: {
        async loadCurrentPublicationExporterAcceptance() {
          acceptanceLoads += 1;
          return {binding: publicationPlan.exporterAcceptance};
        },
      },
    }),
    /requires isolated channel slug gt-new-horizons; received gtnh/,
  );
  assert.equal(acceptanceLoads, 0);
  await assert.rejects(realpath(join(root, 'mismatch')), error => error?.code === 'ENOENT');

  const messages = [];
  await assert.rejects(
    prepareModpackPublication({
      source,
      workspace: join(root, 'forced'),
      profile: publicationPlan.profile,
      releaseId: publicationPlan.exporterAcceptance.receipt.release.id,
      logger: {info(message) { messages.push(message); }, warn() {}, error() {}},
      dependencies: {
        async loadCurrentPublicationExporterAcceptance() {
          return {binding: publicationPlan.exporterAcceptance};
        },
        async verifyPublicationExporterBuildFile() {},
        async importExportData() {
          throw new Error('stop after channel resolution');
        },
      },
    }),
    /stop after channel resolution/,
  );
  assert.ok(messages.some(message => /as channel gt-new-horizons\.$/.test(message)));
});

test('preparation admits full MM1 and MM2 exports and commits production plans', async t => {
  for (const publicationPlan of [plan(), mm2Plan()]) {
    const fixture = await rawPublicationFixture(t, publicationPlan);
    const prepared = await prepareModpackPublication({
      source: fixture.source,
      workspace: fixture.workspace,
      profile: publicationPlan.profile,
      releaseId: publicationPlan.exporterAcceptance.receipt.release.id,
      slug: publicationPlan.slug,
      stagingMode: 'copy',
      concurrency: 1,
      logger: quietLogger,
      dependencies: preparationAcceptanceDependencies(publicationPlan),
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

test('prepared-plan loading preserves the exact GTNH structured-data-only decision', async t => {
  const root = await preparedWorkspace(t, gtnhPlan());
  const loaded = await loadPreparedPlan(root);
  assert.equal(loaded.plan.profile, 'gtnh-1.7.10');
  assert.equal(loaded.plan.publicationPolicy, GTNH_STRUCTURED_DATA_ONLY_POLICY);
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
    ...(publicationPlan.publicationPolicy === undefined
      ? {}
      : {
          publicationPolicy: publicationPlan.publicationPolicy,
          web: {visualAssets: GTNH_VISUAL_ASSETS_POLICY},
        }),
    ...overrides,
  })}\n`);
}

function orchestrationDependencies(events, {preflightFailure = null} = {}) {
  const record = name => async options => {
    events.push({name, options});
    return undefined;
  };
  return {
    verifyPreparedPublicationAcceptance: record('acceptance'),
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
    'acceptance',
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

test('non-GTNH upload accepts no benchmark options but rejects a partial pair through the API', async t => {
  const publicationPlan = plan();
  const root = await preparedWorkspace(t, publicationPlan);
  await writeValidPackedManifest(root, publicationPlan);
  const events = [];
  await assert.rejects(
    uploadPreparedModpackPublication({
      workspace: root,
      channelAction: 'update',
      isDefault: false,
      appOrigin: 'https://viewer.test',
      benchmarkReport: join(root, 'missing-report.json'),
      logger: quietLogger,
      dependencies: orchestrationDependencies(events),
    }),
    /--benchmark-report and --dist must be supplied together/,
  );
  assert.deepEqual(events.map(event => event.name), ['acceptance']);
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

test('upload rejects a stale exporter receipt before catalog or credential access', async t => {
  const root = await preparedWorkspace(t);
  await writeValidPackedManifest(root);
  const events = [];
  const dependencies = orchestrationDependencies(events);
  dependencies.verifyPreparedPublicationAcceptance = async options => {
    events.push({name: 'acceptance', options});
    throw new Error('exporter receipt changed after preparation');
  };
  await assert.rejects(
    uploadPreparedModpackPublication({
      workspace: root,
      channelAction: 'update',
      isDefault: false,
      appOrigin: 'https://viewer.test',
      logger: quietLogger,
      dependencies,
    }),
    /exporter receipt changed after preparation/,
  );
  assert.deepEqual(events.map(event => event.name), ['acceptance']);
});

test('GTNH upload requires paired benchmark evidence before catalog or credential access', async t => {
  const publicationPlan = gtnhPlan();
  const root = await preparedWorkspace(t, publicationPlan);
  await writeValidPackedManifest(root, publicationPlan);
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
    /GTNH activation requires --benchmark-report and --dist/,
  );
  assert.deepEqual(events.map(event => event.name), ['acceptance']);
});

test('GTNH upload rejects stale or weakened benchmark receipts before external access', async t => {
  const cases = [
    {
      name: 'unknown schema field',
      mutate(report) { report.unexpected = true; },
      pattern: /exact benchmark-report contract/,
    },
    {
      name: 'stale benchmark source',
      mutate(report) { report.benchmark.sourceSha256 = ID_D; },
      pattern: /source SHA-256 does not match/,
    },
    {
      name: 'modified thresholds',
      mutate(report) { report.thresholds.eligible.readyMs += 1; },
      pattern: /thresholds do not match/,
    },
    {
      name: 'non-eligible decision',
      mutate(report) { report.decision = 'operator-review-required'; },
      pattern: /does not reproduce a current-storage-eligible/,
    },
    {
      name: 'different dataset',
      mutate(report) { report.dataset.publicationId = ID_C; },
      pattern: /dataset identity does not match/,
    },
    {
      name: 'different build',
      mutate(report) { report.build.sha256 = ID_C; },
      pattern: /build identity does not match/,
    },
    {
      name: 'fewer than three runs',
      mutate(report) {
        report.runs.pop();
        report.benchmark.runs = report.runs.length;
      },
      pattern: /must contain between 3 and 10 runs/,
    },
  ];
  for (const fixtureCase of cases) {
    const publicationPlan = gtnhPlan();
    const root = await preparedWorkspace(t, publicationPlan);
    await writeValidPackedManifest(root, publicationPlan);
    const benchmark = await activationBenchmarkFixture(t, publicationPlan, report => {
      fixtureCase.mutate(report);
      return report;
    });
    const events = [];
    await assert.rejects(
      uploadPreparedModpackPublication({
        workspace: root,
        channelAction: 'create',
        isDefault: false,
        appOrigin: 'https://viewer.test',
        benchmarkReport: benchmark.reportPath,
        dist: benchmark.dist,
        logger: quietLogger,
        dependencies: orchestrationDependencies(events),
      }),
      fixtureCase.pattern,
      fixtureCase.name,
    );
    assert.deepEqual(events.map(event => event.name), ['acceptance'], fixtureCase.name);
  }
});

test('GTNH upload bounds its benchmark receipt before JSON parsing or external access', async t => {
  const publicationPlan = gtnhPlan();
  const root = await preparedWorkspace(t, publicationPlan);
  await writeValidPackedManifest(root, publicationPlan);
  const benchmark = await activationBenchmarkFixture(t, publicationPlan);
  await writeFile(
    benchmark.reportPath,
    Buffer.alloc(MAX_ACTIVATION_BENCHMARK_REPORT_BYTES + 1, 0x20),
  );
  const events = [];
  await assert.rejects(
    uploadPreparedModpackPublication({
      workspace: root,
      channelAction: 'create',
      isDefault: false,
      appOrigin: 'https://viewer.test',
      benchmarkReport: benchmark.reportPath,
      dist: benchmark.dist,
      logger: quietLogger,
      dependencies: orchestrationDependencies(events),
    }),
    new RegExp(`invalid byte length ${MAX_ACTIVATION_BENCHMARK_REPORT_BYTES + 1}`),
  );
  assert.deepEqual(events.map(event => event.name), ['acceptance']);
});

test('GTNH upload refuses a symlinked benchmark receipt before external access', async t => {
  if (process.platform === 'win32') {
    t.skip('Creating filesystem symlinks requires an elevated Windows test environment.');
    return;
  }
  const publicationPlan = gtnhPlan();
  const root = await preparedWorkspace(t, publicationPlan);
  await writeValidPackedManifest(root, publicationPlan);
  const benchmark = await activationBenchmarkFixture(t, publicationPlan);
  const linkedReport = join(root, 'linked-activation-report.json');
  await symlink(benchmark.reportPath, linkedReport);
  const events = [];
  await assert.rejects(
    uploadPreparedModpackPublication({
      workspace: root,
      channelAction: 'create',
      isDefault: false,
      appOrigin: 'https://viewer.test',
      benchmarkReport: linkedReport,
      dist: benchmark.dist,
      logger: quietLogger,
      dependencies: orchestrationDependencies(events),
    }),
    /Cold benchmark activation report must be a no-follow regular file/,
  );
  assert.deepEqual(events.map(event => event.name), ['acceptance']);
});

test('GTNH upload revalidates the exact data-only manifest before catalog or credential access', async t => {
  for (const overrides of [
    {publicationPolicy: undefined},
    {publicationPolicy: 'gtnh-structured-data-only-v2'},
    {web: undefined},
    {
      web: {
        visualAssets: {...GTNH_VISUAL_ASSETS_POLICY, recipePreviews: 1},
      },
    },
  ]) {
    const publicationPlan = gtnhPlan();
    const root = await preparedWorkspace(t, publicationPlan);
    await writeValidPackedManifest(root, publicationPlan, overrides);
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
      /structured-data-only|publicationPolicy|visualAssets/,
    );
    assert.deepEqual(events, []);
  }
});

test('GTNH upload logs and carries the exact data-only policy through verified activation', async t => {
  const publicationPlan = gtnhPlan();
  const root = await preparedWorkspace(t, publicationPlan);
  await writeValidPackedManifest(root, publicationPlan);
  const benchmark = await activationBenchmarkFixture(t, publicationPlan);
  const events = [];
  const messages = [];
  const result = await uploadPreparedModpackPublication({
    workspace: root,
    channelAction: 'create',
    isDefault: false,
    appOrigin: 'https://viewer.test',
    benchmarkReport: benchmark.reportPath,
    dist: benchmark.dist,
    logger: {info(message) { messages.push(message); }, warn() {}, error() {}},
    dependencies: {
      ...orchestrationDependencies(events),
      async fetchPublishingCatalog(options) {
        events.push({name: 'catalog', options});
        return [currentDescriptor()];
      },
    },
  });
  assert.equal(result.publicationPolicy, GTNH_STRUCTURED_DATA_ONLY_POLICY);
  assert.ok(messages.some(message => message.includes(
    `Revalidated ${GTNH_STRUCTURED_DATA_ONLY_POLICY}`,
  )));
  assert.ok(messages.some(message => message.includes(
    `activation benchmark report sha256=${benchmark.reportSha256}`,
  )));
  assert.ok(messages.some(message => message.includes(
    `production build sha256=${benchmark.build.sha256}`,
  )));
  assert.equal(result.benchmarkReportSha256, benchmark.reportSha256);
  assert.equal(result.benchmarkSourceSha256, benchmark.sourceSha256);
  assert.equal(result.buildSha256, benchmark.build.sha256);
  assert.equal(events.at(-1).name, 'activate-channel');
});

test('GTNH upload refuses activation if the benchmarked dist changes after upload verification', async t => {
  const publicationPlan = gtnhPlan();
  const root = await preparedWorkspace(t, publicationPlan);
  await writeValidPackedManifest(root, publicationPlan);
  const benchmark = await activationBenchmarkFixture(t, publicationPlan);
  const events = [];
  const dependencies = orchestrationDependencies(events);
  dependencies.verifyRemoteRecipePreviewSidecar = async options => {
    events.push({name: 'verify-preview', options});
    await writeFile(join(benchmark.dist, 'client', 'app.js'), 'globalThis.__mrtTestBuild = false;\n');
  };
  await assert.rejects(
    uploadPreparedModpackPublication({
      workspace: root,
      channelAction: 'create',
      isDefault: false,
      appOrigin: 'https://viewer.test',
      benchmarkReport: benchmark.reportPath,
      dist: benchmark.dist,
      logger: quietLogger,
      dependencies,
    }),
    /Production dist changed after benchmark receipt validation and before channel activation/,
  );
  assert.equal(events.at(-1).name, 'verify-preview');
  assert.equal(events.some(event => event.name === 'activate-channel'), false);
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
