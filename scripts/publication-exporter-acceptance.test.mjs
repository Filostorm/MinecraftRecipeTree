import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {strToU8, zipSync} from 'fflate';
import {
  buildExporterAcceptanceReceipt,
  exporterAcceptancePolicySha256,
  sha256Hex,
  writeExporterAcceptanceReceipt,
} from './exporter-release-acceptance.mjs';
import {
  EXPORTER_BUILD_ALGORITHM,
  EXPORTER_BUILD_FORMAT,
  EXPORTER_BUILD_RESOURCE_PATH,
  canonicalExporterBuildIdentityBytes,
  canonicalExporterPayloadSha256,
} from './exporter-artifact-provenance.mjs';
import {digestExportTree} from './export-tree-digest.mjs';
import {
  buildPublicationExporterAcceptance,
  canonicalPublicationAcceptanceReceiptBytes,
  loadCurrentPublicationExporterAcceptance,
  requirePublicationAcceptanceContext,
  requirePublicationExporterAcceptance,
  verifyAcceptedRawPublicationExport,
  verifyPublicationExporterBuildFile,
} from './publication-exporter-acceptance.mjs';

const quietLogger = Object.freeze({info() {}, warn() {}, error() {}});

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'mrt-publication-acceptance-test-'));
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
  const sourceBytes = Buffer.from(zipSync({
    ...Object.fromEntries(payload),
    [EXPORTER_BUILD_RESOURCE_PATH]: exporterBuildBytes,
  }));
  await writeFile(join(workspaceRoot, source), sourceBytes);

  const exportRoot = join(root, 'full-export');
  await mkdir(exportRoot);
  const pack = {
    name: 'Multiblock Madness 2',
    version: '1.0.0',
    identitySource: 'explicit-request',
  };
  const manifest = {
    format: 1,
    generatedAt: '2026-07-20T03:00:00.000Z',
    durationMs: 100,
    aborted: false,
    minecraft: '1.18.2',
    pack,
    settings: {iconScale: 3, recipeScale: 2, mobCanvas: 256},
    counts: {items: 10, recipes: 20, categories: 2, mobs: 0, blockDrops: 0, failures: 0},
    mods: {minecraft: 'Minecraft'},
  };
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`);
  await writeFile(join(exportRoot, 'manifest.json'), manifestBytes);
  await writeFile(join(exportRoot, 'exporter-build.json'), exporterBuildBytes);
  await writeFile(join(exportRoot, 'items.json'), '{"items":[]}\n');
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
  const receipt = buildExporterAcceptanceReceipt({
    definition,
    sourceBytes,
    qualityProfile: definition.qualityProfiles[0],
    exportManifestBytes: manifestBytes,
    exportManifest: manifest,
    pack,
    exporterBuild,
    exportTree: await digestExportTree(exportRoot, {logger: quietLogger}),
    validationPolicySha256: await exporterAcceptancePolicySha256(
      definition,
      definition.qualityProfiles[0],
    ),
    acceptedAt: '2026-07-20T03:10:00.000Z',
  });
  const acceptanceRoot = join(root, 'acceptance');
  await writeExporterAcceptanceReceipt({
    receipt,
    acceptanceRoot,
    logger: quietLogger,
    testOnlyBypassManifestLock: true,
  });
  return {
    root,
    workspaceRoot,
    exportRoot,
    acceptanceRoot,
    definition,
    receipt,
    pack,
    exporterBuildBytes,
  };
}

test('publication binding hashes every normalized receipt field and rejects tampering', async t => {
  const value = await fixture(t);
  const binding = buildPublicationExporterAcceptance(value.receipt);
  assert.equal(
    binding.receiptBytes,
    canonicalPublicationAcceptanceReceiptBytes(value.receipt).length,
  );
  assert.match(binding.receiptSha256, /^[a-f0-9]{64}$/);
  assert.deepEqual(requirePublicationExporterAcceptance(binding), binding);
  assert.throws(
    () => requirePublicationExporterAcceptance({...binding, receiptSha256: '0'.repeat(64)}),
    /does not match its canonical receipt byte length and SHA-256/,
  );
  assert.throws(
    () => requirePublicationExporterAcceptance({
      ...binding,
      receipt: {...binding.receipt, qualityProfile: 'multiblock-madness-1.12.2'},
    }),
    /does not match its canonical receipt byte length and SHA-256/,
  );
});

test('current release verification binds the exact receipt, JAR, profile, and pack', async t => {
  const value = await fixture(t);
  const current = await loadCurrentPublicationExporterAcceptance({
    releaseId: value.definition.id,
    profile: value.definition.qualityProfiles[0],
    minecraftVersion: value.definition.minecraftVersion,
    pack: value.pack,
    definitions: [value.definition],
    workspaceRoot: value.workspaceRoot,
    acceptanceRoot: value.acceptanceRoot,
    logger: quietLogger,
  });
  assert.equal(
    current.binding.receipt.release.sha256,
    sha256Hex(await readFile(join(value.workspaceRoot, value.definition.source))),
  );
  await verifyPublicationExporterBuildFile({
    exportRoot: value.exportRoot,
    binding: current.binding,
  });
  await verifyAcceptedRawPublicationExport({
    exportRoot: value.exportRoot,
    binding: current.binding,
    logger: quietLogger,
  });
  assert.throws(
    () => requirePublicationAcceptanceContext(current.binding, {
      profile: 'multiblock-madness-1.12.2',
      minecraftVersion: '1.12.2',
      pack: {name: 'Multiblock Madness', version: '3.2.3', identitySource: 'explicit-request'},
    }),
    /crosses an incompatible quality profile, Minecraft version, pack identity boundary/,
  );
});

test('staged-tree mutation and receipt replacement fail closed', async t => {
  const value = await fixture(t);
  const initial = await loadCurrentPublicationExporterAcceptance({
    releaseId: value.definition.id,
    profile: value.definition.qualityProfiles[0],
    minecraftVersion: value.definition.minecraftVersion,
    pack: value.pack,
    definitions: [value.definition],
    workspaceRoot: value.workspaceRoot,
    acceptanceRoot: value.acceptanceRoot,
    logger: quietLogger,
  });
  await writeFile(join(value.exportRoot, 'items.json'), '{"items":["changed"]}\n');
  await assert.rejects(
    verifyAcceptedRawPublicationExport({
      exportRoot: value.exportRoot,
      binding: initial.binding,
      logger: quietLogger,
    }),
    /tree does not match the exact exporter acceptance receipt/,
  );

  const replacement = {...value.receipt, acceptedAt: '2026-07-20T03:20:00.000Z'};
  await writeExporterAcceptanceReceipt({
    receipt: replacement,
    acceptanceRoot: value.acceptanceRoot,
    logger: quietLogger,
    testOnlyBypassManifestLock: true,
  });
  await assert.rejects(
    loadCurrentPublicationExporterAcceptance({
      releaseId: value.definition.id,
      profile: value.definition.qualityProfiles[0],
      minecraftVersion: value.definition.minecraftVersion,
      pack: value.pack,
      definitions: [value.definition],
      workspaceRoot: value.workspaceRoot,
      acceptanceRoot: value.acceptanceRoot,
      expectedBinding: initial.binding,
      logger: quietLogger,
    }),
    /changed after preparation/,
  );
});
