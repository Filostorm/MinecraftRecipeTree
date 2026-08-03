import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {strToU8, zipSync} from 'fflate';
import {
  EXPORTER_ACCEPTANCE_RECEIPT_FORMAT,
  buildExporterAcceptanceReceipt,
  exporterAcceptancePolicySha256,
  exporterAcceptanceReceiptPath,
  legacyExporterAcceptanceReceiptPath,
  readExporterAcceptanceReceipt,
  requireAcceptedExporterRelease,
  requireExporterAcceptanceReceipt,
  sha256Hex,
  writeExporterAcceptanceReceipt,
} from './exporter-release-acceptance.mjs';
import {
  acceptExporterRelease,
  parseExporterAcceptanceArguments,
} from './write-exporter-acceptance-receipt.mjs';
import {withExporterReleaseManifestLock} from './exporter-release-lock.mjs';
import {
  EXPORTER_BUILD_ALGORITHM,
  EXPORTER_BUILD_FORMAT,
  EXPORTER_BUILD_RESOURCE_PATH,
  canonicalExporterBuildIdentityBytes,
  canonicalExporterPayloadSha256,
} from './exporter-artifact-provenance.mjs';
import {digestExportTree} from './export-tree-digest.mjs';

const quietLogger = Object.freeze({info() {}, warn() {}, error() {}});

function completeValidationSummary(root, recipes = 20) {
  return {
    root,
    recipes,
    recipeImageInventory: {
      format: 'mrt-recipe-image-inventory-v1',
      sha256: 'a'.repeat(64),
      entries: recipes,
      previews: recipes,
      missing: 0,
    },
  };
}

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'mrt-exporter-acceptance-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  const workspaceRoot = join(root, 'workspace');
  const source = 'build/libs/exporter.jar';
  await mkdir(join(workspaceRoot, 'build', 'libs'), {recursive: true});
  const payload = [
    ['META-INF/MANIFEST.MF', strToU8('Manifest-Version: 1.0\r\n\r\n')],
    ['com/example/Exporter.class', Uint8Array.from([0xca, 0xfe, 0xba, 0xbe])],
  ];
  const exporterBuild = {
    format: EXPORTER_BUILD_FORMAT,
    exporterId: 'forge-rei-1.18.2',
    minecraftVersion: '1.18.2',
    algorithm: EXPORTER_BUILD_ALGORITHM,
    payloadSha256: canonicalExporterPayloadSha256(payload),
  };
  const exporterBuildBytes = canonicalExporterBuildIdentityBytes(exporterBuild);
  const sourceBytes = Buffer.from(
    zipSync({
      ...Object.fromEntries(payload),
      [EXPORTER_BUILD_RESOURCE_PATH]: exporterBuildBytes,
    }),
  );
  await writeFile(join(workspaceRoot, source), sourceBytes);
  const exportRoot = join(root, 'full-export');
  await mkdir(exportRoot);
  const publicRoot = join(root, 'public', 'exporters');
  await mkdir(publicRoot, {recursive: true});
  const manifest = {
    format: 1,
    generatedAt: '2026-07-19T20:59:06.454724Z',
    durationMs: 100,
    aborted: false,
    minecraft: '1.18.2',
    pack: {
      name: 'Multiblock Madness 2',
      version: '1.0.0',
      identitySource: 'explicit-request',
    },
    settings: {iconScale: 3, recipeScale: 2, mobCanvas: 256},
    counts: {items: 10, recipes: 20, categories: 2, mobs: 0, blockDrops: 0, failures: 0},
    mods: {minecraft: 'Minecraft'},
  };
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`);
  await writeFile(join(exportRoot, 'manifest.json'), manifestBytes);
  await writeFile(join(exportRoot, 'exporter-build.json'), exporterBuildBytes);
  const definition = {
    id: 'forge-rei-1.18.2',
    minecraftVersion: '1.18.2',
    recipeViewer: 'REI 8',
    loader: 'Forge 40',
    version: '1.0.1',
    source,
    filename: 'recipe-tree-exporter-forge-1.18.2-1.0.1.jar',
    qualityProfiles: ['multiblock-madness-2-1.18.2'],
    artifactProvenance: {
      format: EXPORTER_BUILD_FORMAT,
      exporterId: 'forge-rei-1.18.2',
      minecraftVersion: '1.18.2',
    },
    acceptanceCorpora: {
      'multiblock-madness-2-1.18.2': {
        items: 10,
        recipes: 20,
        categories: 2,
        mobs: 0,
        blockDrops: 0,
      },
    },
    compatibility: 'Test compatibility',
  };
  return {
    root,
    workspaceRoot,
    acceptanceRoot: join(root, 'acceptance'),
    publicRoot,
    exportRoot,
    sourceBytes,
    exporterBuild,
    exporterBuildBytes,
    manifest,
    manifestBytes,
    definition,
  };
}

test('builds, writes, reads, and verifies an exact SHA-bound acceptance receipt', async t => {
  const value = await fixture(t);
  const receipt = buildExporterAcceptanceReceipt({
    definition: value.definition,
    sourceBytes: value.sourceBytes,
    qualityProfile: value.definition.qualityProfiles[0],
    exportManifestBytes: value.manifestBytes,
    exportManifest: value.manifest,
    pack: value.manifest.pack,
    exporterBuild: value.exporterBuild,
    exportTree: await digestExportTree(value.exportRoot, {logger: quietLogger}),
    validationPolicySha256: await exporterAcceptancePolicySha256(
      value.definition,
      value.definition.qualityProfiles[0],
    ),
    acceptedAt: '2026-07-20T03:00:00.000Z',
  });
  assert.equal(receipt.format, EXPORTER_ACCEPTANCE_RECEIPT_FORMAT);
  assert.equal(receipt.release.sha256, sha256Hex(value.sourceBytes));
  assert.equal(receipt.exportManifest.sha256, sha256Hex(value.manifestBytes));
  assert.equal(Object.isFrozen(receipt.exportManifest.pack), true);

  const written = await writeExporterAcceptanceReceipt({
    receipt,
    acceptanceRoot: value.acceptanceRoot,
    publicRoot: value.publicRoot,
    logger: quietLogger,
  });
  assert.equal(
    written.path,
    exporterAcceptanceReceiptPath(
      value.definition.id,
      value.definition.qualityProfiles[0],
      value.acceptanceRoot,
    ),
  );
  assert.deepEqual(
    await readExporterAcceptanceReceipt(
      value.definition.id,
      value.definition.qualityProfiles[0],
      value.acceptanceRoot,
      quietLogger,
    ),
    receipt,
  );
  assert.deepEqual(
    await requireAcceptedExporterRelease({
      definition: value.definition,
      sourceBytes: value.sourceBytes,
      qualityProfile: value.definition.qualityProfiles[0],
      acceptanceRoot: value.acceptanceRoot,
      logger: quietLogger,
    }),
    receipt,
  );
});

test('profile-keyed paths isolate receipts and legacy release-only discovery is explicit', async t => {
  const value = await fixture(t);
  const qualityProfile = value.definition.qualityProfiles[0];
  const receipt = buildExporterAcceptanceReceipt({
    definition: value.definition,
    sourceBytes: value.sourceBytes,
    qualityProfile,
    exportManifestBytes: value.manifestBytes,
    exportManifest: value.manifest,
    pack: value.manifest.pack,
    exporterBuild: value.exporterBuild,
    exportTree: await digestExportTree(value.exportRoot, {logger: quietLogger}),
    validationPolicySha256: await exporterAcceptancePolicySha256(
      value.definition,
      qualityProfile,
    ),
    acceptedAt: '2026-07-20T03:00:00.000Z',
  });
  await mkdir(value.acceptanceRoot, {recursive: true});
  const legacyPath = legacyExporterAcceptanceReceiptPath(
    value.definition.id,
    value.acceptanceRoot,
  );
  await writeFile(legacyPath, `${JSON.stringify(receipt)}\n`, {mode: 0o600});
  const warnings = [];
  const logger = {info() {}, error() {}, warn(message) { warnings.push(message); }};

  assert.deepEqual(
    await readExporterAcceptanceReceipt(
      value.definition.id,
      qualityProfile,
      value.acceptanceRoot,
      logger,
    ),
    receipt,
  );
  assert.equal(warnings.length, 1);
  assert.match(warnings[0], /\[migration\].*legacy release-only receipt/i);
  assert.notEqual(
    exporterAcceptanceReceiptPath(value.definition.id, qualityProfile, value.acceptanceRoot),
    legacyPath,
  );
  await assert.rejects(
    readExporterAcceptanceReceipt(
      value.definition.id,
      'generic-jei-1.20.1',
      value.acceptanceRoot,
      logger,
    ),
    /cross-profile receipt fallback is forbidden/,
  );
});

test('profile policy identities bind the selected corpus without coupling sibling corpus promotion', async t => {
  const value = await fixture(t);
  const primaryProfile = value.definition.qualityProfiles[0];
  const siblingProfile = 'generic-jei-1.20.1';
  const definition = {
    ...value.definition,
    qualityProfiles: [primaryProfile, siblingProfile],
    acceptanceCorpora: {
      [primaryProfile]: value.definition.acceptanceCorpora[primaryProfile],
      [siblingProfile]: null,
    },
  };
  const promotedSibling = {
    ...definition,
    acceptanceCorpora: {
      ...definition.acceptanceCorpora,
      [siblingProfile]: {items: 1, recipes: 2, categories: 3, mobs: 0, blockDrops: 0},
    },
  };

  assert.equal(
    await exporterAcceptancePolicySha256(definition, primaryProfile),
    await exporterAcceptancePolicySha256(promotedSibling, primaryProfile),
  );
  assert.notEqual(
    await exporterAcceptancePolicySha256(definition, siblingProfile),
    await exporterAcceptancePolicySha256(promotedSibling, siblingProfile),
  );
});

test('acceptance action validates a full export once and writes the bound receipt', async t => {
  const value = await fixture(t);
  let validationCalls = 0;
  const result = await acceptExporterRelease({
    releaseId: value.definition.id,
    profile: value.definition.qualityProfiles[0],
    exportRoot: value.exportRoot,
    workspaceRoot: value.workspaceRoot,
    acceptanceRoot: value.acceptanceRoot,
    publicRoot: value.publicRoot,
    definitions: [value.definition],
    acceptedAt: '2026-07-20T03:00:00.000Z',
    logger: quietLogger,
    async testOnlyValidateExport(root, options) {
      validationCalls += 1;
      assert.equal(root, value.exportRoot);
      assert.deepEqual(options, {
        profile: value.definition.qualityProfiles[0],
        requirePackIdentity: true,
        assetMode: 'raw',
        computeRecipeImageInventory: true,
      });
      return completeValidationSummary(root);
    },
  });
  assert.equal(validationCalls, 1);
  assert.equal(result.receipt.release.sha256, sha256Hex(value.sourceBytes));
  assert.equal(result.receipt.exportManifest.sha256, sha256Hex(value.manifestBytes));
  assert.deepEqual(result.summary, completeValidationSummary(value.exportRoot));
});

test('acceptance action rejects an incomplete decoded recipe-preview inventory', async t => {
  const value = await fixture(t);
  await assert.rejects(
    acceptExporterRelease({
      releaseId: value.definition.id,
      profile: value.definition.qualityProfiles[0],
      exportRoot: value.exportRoot,
      workspaceRoot: value.workspaceRoot,
      acceptanceRoot: value.acceptanceRoot,
      publicRoot: value.publicRoot,
      definitions: [value.definition],
      logger: quietLogger,
      async testOnlyValidateExport(root, options) {
        assert.equal(root, value.exportRoot);
        assert.equal(options.computeRecipeImageInventory, true);
        return {
          ...completeValidationSummary(root),
          recipeImageInventory: {
            format: 'mrt-recipe-image-inventory-v1',
            sha256: 'b'.repeat(64),
            entries: value.manifest.counts.recipes,
            previews: value.manifest.counts.recipes - 1,
            missing: 1,
          },
        };
      },
    }),
    /requires one decoded recipe preview per recipe; recipes\/previews\/missing=20\/19\/1/,
  );
  await assert.rejects(
    readFile(
      exporterAcceptanceReceiptPath(
        value.definition.id,
        value.definition.qualityProfiles[0],
        value.acceptanceRoot,
      ),
    ),
    error => error?.code === 'ENOENT',
  );
});

test('acceptance cannot pair a rebuilt source JAR with an export from the prior build', async t => {
  const value = await fixture(t);
  const changedPayload = [
    ['META-INF/MANIFEST.MF', strToU8('Manifest-Version: 1.0\r\n\r\n')],
    ['com/example/Exporter.class', Uint8Array.from([0xca, 0xfe, 0xba, 0xbf])],
  ];
  const changedIdentity = {
    format: EXPORTER_BUILD_FORMAT,
    exporterId: 'forge-rei-1.18.2',
    minecraftVersion: '1.18.2',
    algorithm: EXPORTER_BUILD_ALGORITHM,
    payloadSha256: canonicalExporterPayloadSha256(changedPayload),
  };
  await writeFile(
    join(value.workspaceRoot, value.definition.source),
    Buffer.from(
      zipSync({
        ...Object.fromEntries(changedPayload),
        [EXPORTER_BUILD_RESOURCE_PATH]: canonicalExporterBuildIdentityBytes(changedIdentity),
      }),
    ),
  );
  let validationCalled = false;
  await assert.rejects(
    acceptExporterRelease({
      releaseId: value.definition.id,
      profile: value.definition.qualityProfiles[0],
      exportRoot: value.exportRoot,
      workspaceRoot: value.workspaceRoot,
      acceptanceRoot: value.acceptanceRoot,
      publicRoot: value.publicRoot,
      definitions: [value.definition],
      logger: quietLogger,
      async testOnlyValidateExport() {
        validationCalled = true;
      },
    }),
    /not byte-identical to the exact source JAR build identity/,
  );
  assert.equal(validationCalled, false);
});

test('acceptance receipt replacement serializes with release-manifest transactions', async t => {
  const value = await fixture(t);
  const receipt = buildExporterAcceptanceReceipt({
    definition: value.definition,
    sourceBytes: value.sourceBytes,
    qualityProfile: value.definition.qualityProfiles[0],
    exportManifestBytes: value.manifestBytes,
    exportManifest: value.manifest,
    pack: value.manifest.pack,
    exporterBuild: value.exporterBuild,
    exportTree: await digestExportTree(value.exportRoot, {logger: quietLogger}),
    validationPolicySha256: await exporterAcceptancePolicySha256(
      value.definition,
      value.definition.qualityProfiles[0],
    ),
    acceptedAt: '2026-07-20T03:00:00.000Z',
  });
  let markStarted;
  const started = new Promise(resolve => {
    markStarted = resolve;
  });
  let releaseTransaction;
  const held = new Promise(resolve => {
    releaseTransaction = resolve;
  });
  const transaction = withExporterReleaseManifestLock({
    publicRoot: value.publicRoot,
    operation: 'held packaging transaction',
    logger: quietLogger,
    action: async () => {
      markStarted();
      await held;
    },
  });
  await started;

  await assert.rejects(
    writeExporterAcceptanceReceipt({
      receipt,
      acceptanceRoot: value.acceptanceRoot,
      publicRoot: value.publicRoot,
      logger: quietLogger,
    }),
    /transaction lock already exists/,
  );
  await assert.rejects(
    readFile(
      exporterAcceptanceReceiptPath(
        value.definition.id,
        value.definition.qualityProfiles[0],
        value.acceptanceRoot,
      ),
    ),
    error => error?.code === 'ENOENT',
  );
  releaseTransaction();
  await transaction;

  await writeExporterAcceptanceReceipt({
    receipt,
    acceptanceRoot: value.acceptanceRoot,
    publicRoot: value.publicRoot,
    logger: quietLogger,
  });
  assert.deepEqual(
    await readExporterAcceptanceReceipt(
      value.definition.id,
      value.definition.qualityProfiles[0],
      value.acceptanceRoot,
      quietLogger,
    ),
    receipt,
  );
});

test('rejects diagnostic mini exports and manifests that mutate during validation', async t => {
  const value = await fixture(t);
  const manifestPath = join(value.exportRoot, 'manifest.json');
  await writeFile(
    manifestPath,
    `${JSON.stringify({...value.manifest, qualitySample: {enabled: true}})}\n`,
  );
  let validationCalled = false;
  await assert.rejects(
    acceptExporterRelease({
      releaseId: value.definition.id,
      profile: value.definition.qualityProfiles[0],
      exportRoot: value.exportRoot,
      workspaceRoot: value.workspaceRoot,
      acceptanceRoot: value.acceptanceRoot,
      publicRoot: value.publicRoot,
      definitions: [value.definition],
      logger: quietLogger,
      async testOnlyValidateExport() {
        validationCalled = true;
      },
    }),
    /diagnostic mini export/,
  );
  assert.equal(validationCalled, false);

  await writeFile(manifestPath, value.manifestBytes);
  await assert.rejects(
    acceptExporterRelease({
      releaseId: value.definition.id,
      profile: value.definition.qualityProfiles[0],
      exportRoot: value.exportRoot,
      workspaceRoot: value.workspaceRoot,
      acceptanceRoot: value.acceptanceRoot,
      publicRoot: value.publicRoot,
      definitions: [value.definition],
      logger: quietLogger,
      async testOnlyValidateExport() {
        await writeFile(manifestPath, `${JSON.stringify({...value.manifest, durationMs: 101})}\n`);
        return completeValidationSummary(value.exportRoot);
      },
    }),
    /changed during validation/,
  );

  await writeFile(manifestPath, value.manifestBytes);
  const nonManifestPath = join(value.exportRoot, 'items.json');
  await writeFile(nonManifestPath, '{"items":[]}\n');
  await assert.rejects(
    acceptExporterRelease({
      releaseId: value.definition.id,
      profile: value.definition.qualityProfiles[0],
      exportRoot: value.exportRoot,
      workspaceRoot: value.workspaceRoot,
      acceptanceRoot: value.acceptanceRoot,
      publicRoot: value.publicRoot,
      definitions: [value.definition],
      logger: quietLogger,
      async testOnlyValidateExport() {
        await writeFile(nonManifestPath, '{"items":[{"changed":true}]}\n');
        return completeValidationSummary(value.exportRoot);
      },
    }),
    /export tree changed during exhaustive validation/,
  );
  await assert.rejects(
    readFile(
      exporterAcceptanceReceiptPath(
        value.definition.id,
        value.definition.qualityProfiles[0],
        value.acceptanceRoot,
      ),
    ),
    error => error?.code === 'ENOENT',
  );
});

test('receipt verification rejects artifact, profile, and contract drift', async t => {
  const value = await fixture(t);
  const receipt = buildExporterAcceptanceReceipt({
    definition: value.definition,
    sourceBytes: value.sourceBytes,
    qualityProfile: value.definition.qualityProfiles[0],
    exportManifestBytes: value.manifestBytes,
    exportManifest: value.manifest,
    pack: value.manifest.pack,
    exporterBuild: value.exporterBuild,
    exportTree: await digestExportTree(value.exportRoot, {logger: quietLogger}),
    validationPolicySha256: await exporterAcceptancePolicySha256(
      value.definition,
      value.definition.qualityProfiles[0],
    ),
    acceptedAt: '2026-07-20T03:00:00.000Z',
  });
  await writeExporterAcceptanceReceipt({
    receipt,
    acceptanceRoot: value.acceptanceRoot,
    publicRoot: value.publicRoot,
    logger: quietLogger,
  });
  await assert.rejects(
    requireAcceptedExporterRelease({
      definition: value.definition,
      sourceBytes: Buffer.concat([value.sourceBytes, Buffer.from([0x00])]),
      qualityProfile: value.definition.qualityProfiles[0],
      acceptanceRoot: value.acceptanceRoot,
      logger: quietLogger,
    }),
    /source JAR SHA-256, source JAR byte length/,
  );
  await assert.rejects(
    requireAcceptedExporterRelease({
      definition: {
        ...value.definition,
        qualityProfiles: ['generic-jei-1.20.1'],
        acceptanceCorpora: {'generic-jei-1.20.1': null},
      },
      sourceBytes: value.sourceBytes,
      qualityProfile: value.definition.qualityProfiles[0],
      acceptanceRoot: value.acceptanceRoot,
      logger: quietLogger,
    }),
    /exact per-profile acceptance/,
  );
  await assert.rejects(
    requireAcceptedExporterRelease({
      definition: {...value.definition, compatibility: 'Changed validation target'},
      sourceBytes: value.sourceBytes,
      qualityProfile: value.definition.qualityProfiles[0],
      acceptanceRoot: value.acceptanceRoot,
      logger: quietLogger,
    }),
    /validation policy or release definition SHA-256/,
  );
  await assert.rejects(
    requireAcceptedExporterRelease({
      definition: {
        ...value.definition,
        artifactProvenance: {
          ...value.definition.artifactProvenance,
          exporterId: 'forge-hei-1.12.2',
        },
      },
      sourceBytes: value.sourceBytes,
      qualityProfile: value.definition.qualityProfiles[0],
      acceptanceRoot: value.acceptanceRoot,
      logger: quietLogger,
    }),
    /artifactProvenance must bind.*exact release ID and Minecraft version/,
  );

  const wrongPackReceipt = buildExporterAcceptanceReceipt({
    definition: value.definition,
    sourceBytes: value.sourceBytes,
    qualityProfile: value.definition.qualityProfiles[0],
    exportManifestBytes: value.manifestBytes,
    exportManifest: value.manifest,
    pack: {name: 'Different Pack', version: '1.0.0', identitySource: 'explicit-request'},
    exporterBuild: value.exporterBuild,
    exportTree: await digestExportTree(value.exportRoot, {logger: quietLogger}),
    validationPolicySha256: await exporterAcceptancePolicySha256(
      value.definition,
      value.definition.qualityProfiles[0],
    ),
    acceptedAt: '2026-07-20T04:00:00.000Z',
  });
  await writeExporterAcceptanceReceipt({
    receipt: wrongPackReceipt,
    acceptanceRoot: value.acceptanceRoot,
    publicRoot: value.publicRoot,
    logger: quietLogger,
  });
  await assert.rejects(
    requireAcceptedExporterRelease({
      definition: value.definition,
      sourceBytes: value.sourceBytes,
      qualityProfile: value.definition.qualityProfiles[0],
      acceptanceRoot: value.acceptanceRoot,
      logger: quietLogger,
    }),
    /validated export pack identity/,
  );
  assert.throws(
    () => requireExporterAcceptanceReceipt({...receipt, unexpected: true}),
    /exact top-level contract/,
  );
});

test('acceptance action rejects a profile the release does not advertise', async t => {
  const value = await fixture(t);
  await assert.rejects(
    acceptExporterRelease({
      releaseId: value.definition.id,
      profile: 'generic-jei-1.20.1',
      exportRoot: value.exportRoot,
      workspaceRoot: value.workspaceRoot,
      acceptanceRoot: value.acceptanceRoot,
      publicRoot: value.publicRoot,
      definitions: [value.definition],
      logger: quietLogger,
      async testOnlyValidateExport() {
        throw new Error('validator must not run for the wrong gate profile');
      },
    }),
    /does not advertise acceptance profile "generic-jei-1\.20\.1"/,
  );
});

test('receipt reads refuse symlinks and the CLI parser requires an exact action', async t => {
  if (process.platform !== 'win32') {
    const value = await fixture(t);
    await mkdir(value.acceptanceRoot);
    const target = join(value.root, 'receipt-target.json');
    await writeFile(target, '{}\n');
    await symlink(
      target,
      exporterAcceptanceReceiptPath(
        value.definition.id,
        value.definition.qualityProfiles[0],
        value.acceptanceRoot,
      ),
    );
    await assert.rejects(
      readExporterAcceptanceReceipt(
        value.definition.id,
        value.definition.qualityProfiles[0],
        value.acceptanceRoot,
        quietLogger,
      ),
      /plain, non-hard-linked regular file/,
    );
  }
  assert.throws(
    () => parseExporterAcceptanceArguments([]),
    /requires an explicit action/,
  );
  assert.deepEqual(parseExporterAcceptanceArguments(['--help']), {command: 'help'});
  assert.deepEqual(
    parseExporterAcceptanceArguments([
      '--release',
      'forge-rei-1.18.2',
      '--profile',
      'multiblock-madness-2-1.18.2',
      '--export-root',
      '/tmp/export',
    ]),
    {
      command: 'accept',
      releaseId: 'forge-rei-1.18.2',
      profile: 'multiblock-madness-2-1.18.2',
      exportRoot: '/tmp/export',
    },
  );
  assert.throws(
    () => parseExporterAcceptanceArguments(['--release', 'forge-rei-1.18.2']),
    /Missing exporter acceptance argument/,
  );
  assert.throws(
    () => parseExporterAcceptanceArguments(['--release', 'forge-rei-1.18.2', '--help']),
    /Help must be requested as the only argument/,
  );
});
