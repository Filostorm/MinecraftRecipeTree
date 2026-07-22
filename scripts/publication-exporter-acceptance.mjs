import {createHash} from 'node:crypto';
import {isDeepStrictEqual} from 'node:util';
import {join} from 'node:path';
import {
  DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  DEFAULT_EXPORTER_WORKSPACE_ROOT,
  exporterReleaseDefinitionForId,
  readVerifiedExporterJar,
  readVerifiedRegularFile,
  requireAcceptedExporterRelease,
  requireExporterAcceptanceReceipt,
  resolveExporterReleaseSourcePath,
} from './exporter-release-acceptance.mjs';
import {
  EXPORTER_BUILD_EXPORT_PATH,
  inspectExporterJarBuild,
  parseExporterBuildIdentityBytes,
} from './exporter-artifact-provenance.mjs';
import {resolveQualityProfile} from './export-quality-policy.mjs';
import {digestExportTree, sameExportTreeDigest} from './export-tree-digest.mjs';
import {requirePublishablePackIdentity} from './pack-identity.mjs';

export const PUBLICATION_EXPORTER_ACCEPTANCE_FORMAT =
  'mrt-publication-exporter-acceptance-v1';
export const PUBLICATION_EXPORTER_ACCEPTANCE_ALGORITHM = 'sha256';

const SHA256_PATTERN = /^[a-f0-9]{64}$/;

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function exactKeys(value, expected) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

/**
 * Serialize the normalized receipt, rather than its incidental whitespace on disk, so the
 * publication identity is stable while every semantically validated receipt field remains bound.
 */
export function canonicalPublicationAcceptanceReceiptBytes(receipt) {
  const validated = requireExporterAcceptanceReceipt(receipt);
  return Buffer.from(`${JSON.stringify(validated)}\n`, 'utf8');
}

export function buildPublicationExporterAcceptance(receipt) {
  const validated = requireExporterAcceptanceReceipt(receipt);
  const canonicalBytes = canonicalPublicationAcceptanceReceiptBytes(validated);
  return requirePublicationExporterAcceptance({
    format: PUBLICATION_EXPORTER_ACCEPTANCE_FORMAT,
    algorithm: PUBLICATION_EXPORTER_ACCEPTANCE_ALGORITHM,
    receiptSha256: createHash('sha256').update(canonicalBytes).digest('hex'),
    receiptBytes: canonicalBytes.length,
    receipt: validated,
  });
}

export function requirePublicationExporterAcceptance(value) {
  if (
    !exactKeys(value, [
      'algorithm',
      'format',
      'receipt',
      'receiptBytes',
      'receiptSha256',
    ])
  ) {
    throw new Error('Publication exporter acceptance violates the exact contract.');
  }
  if (
    value.format !== PUBLICATION_EXPORTER_ACCEPTANCE_FORMAT ||
    value.algorithm !== PUBLICATION_EXPORTER_ACCEPTANCE_ALGORITHM
  ) {
    throw new Error('Publication exporter acceptance uses an unsupported format or algorithm.');
  }
  if (!SHA256_PATTERN.test(value.receiptSha256)) {
    throw new Error('Publication exporter acceptance receiptSha256 must be lowercase SHA-256.');
  }
  const receipt = requireExporterAcceptanceReceipt(value.receipt);
  if (receipt.exporterBuild === null) {
    throw new Error(
      `Publication exporter acceptance for ${receipt.release.id} must include exact exporter-build.json identity.`,
    );
  }
  if (
    receipt.exporterBuild.exporterId !== receipt.release.id ||
    receipt.exporterBuild.minecraftVersion !== receipt.exportManifest.minecraft
  ) {
    throw new Error(
      'Publication exporter acceptance release, exporter-build, and Minecraft identities disagree.',
    );
  }
  const canonicalBytes = canonicalPublicationAcceptanceReceiptBytes(receipt);
  const expectedSha256 = createHash('sha256').update(canonicalBytes).digest('hex');
  if (
    !Number.isSafeInteger(value.receiptBytes) ||
    value.receiptBytes !== canonicalBytes.length ||
    value.receiptSha256 !== expectedSha256
  ) {
    throw new Error(
      'Publication exporter acceptance does not match its canonical receipt byte length and SHA-256.',
    );
  }
  return Object.freeze({
    format: PUBLICATION_EXPORTER_ACCEPTANCE_FORMAT,
    algorithm: PUBLICATION_EXPORTER_ACCEPTANCE_ALGORITHM,
    receiptSha256: expectedSha256,
    receiptBytes: canonicalBytes.length,
    receipt,
  });
}

function requireAcceptanceContext(binding, {profile, minecraftVersion, pack}) {
  const validated = requirePublicationExporterAcceptance(binding);
  const qualityProfile = resolveQualityProfile(profile);
  const packIdentity = requirePublishablePackIdentity(pack, 'Publication acceptance pack');
  const {receipt} = validated;
  const mismatches = [];
  if (receipt.qualityProfile !== qualityProfile) mismatches.push('quality profile');
  if (receipt.exportManifest.minecraft !== minecraftVersion) mismatches.push('Minecraft version');
  if (!isDeepStrictEqual(receipt.exportManifest.pack, packIdentity)) {
    mismatches.push('pack identity');
  }
  if (mismatches.length > 0) {
    throw new Error(
      `Publication exporter acceptance crosses an incompatible ${mismatches.join(', ')} boundary.`,
    );
  }
  return validated;
}

/**
 * Revalidate the current local receipt against the exact configured release JAR and policy. This
 * deliberately does not acquire the exporter-manifest mutation lock: it is a read-only snapshot,
 * and the immutable binding is checked again immediately before upload.
 */
export async function loadCurrentPublicationExporterAcceptance({
  releaseId,
  profile,
  minecraftVersion,
  pack,
  definitions,
  workspaceRoot = DEFAULT_EXPORTER_WORKSPACE_ROOT,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  expectedBinding = null,
  logger = console,
}) {
  const qualityProfile = resolveQualityProfile(profile);
  const definition = exporterReleaseDefinitionForId(definitions, releaseId);
  const packIdentity = requirePublishablePackIdentity(pack, 'Publication acceptance pack');
  const configurationMismatches = [];
  if (
    !Array.isArray(definition.qualityProfiles) ||
    !definition.qualityProfiles.includes(qualityProfile)
  ) {
    configurationMismatches.push('release-advertised acceptance profile');
  }
  if (definition.minecraftVersion !== minecraftVersion) {
    configurationMismatches.push('release-defined Minecraft version');
  }
  if (definition.artifactProvenance === null) {
    configurationMismatches.push('required exporter artifact provenance');
  }
  if (configurationMismatches.length > 0) {
    throw new Error(
      `Publication release ${releaseId} does not match the requested export: ${configurationMismatches.join(', ')}.`,
    );
  }

  const sourcePath = resolveExporterReleaseSourcePath(definition, workspaceRoot);
  const sourceBytes = await readVerifiedExporterJar(
    sourcePath,
    `Publication exporter release ${releaseId}`,
  );
  const receipt = await requireAcceptedExporterRelease({
    definition,
    sourceBytes,
    qualityProfile,
    acceptanceRoot,
    logger,
  });
  const binding = buildPublicationExporterAcceptance(receipt);
  requireAcceptanceContext(binding, {
    profile: qualityProfile,
    minecraftVersion,
    pack: packIdentity,
  });
  if (
    expectedBinding !== null &&
    !isDeepStrictEqual(requirePublicationExporterAcceptance(expectedBinding), binding)
  ) {
    throw new Error(
      `Publication exporter acceptance for ${releaseId} changed after preparation; refusing to use a different receipt or JAR identity.`,
    );
  }
  logger.info(
    `[publication-acceptance] Bound release ${releaseId} JAR sha256=${receipt.release.sha256} ` +
      `to receipt sha256=${binding.receiptSha256}.`,
  );
  return Object.freeze({definition, binding});
}

/**
 * Verify the current distributable JAR against an already-bound publication receipt without
 * asserting that the historical validation-policy digest equals today's policy digest. This is
 * intentionally narrower than loadCurrentPublicationExporterAcceptance and is used only after an
 * exact prepared-publication migration allowlist has been matched by the caller.
 */
export async function verifyPublicationExporterArtifactBinding({
  releaseId,
  profile,
  minecraftVersion,
  pack,
  definitions,
  binding,
  workspaceRoot = DEFAULT_EXPORTER_WORKSPACE_ROOT,
}) {
  const validated = requireAcceptanceContext(binding, {profile, minecraftVersion, pack});
  const definition = exporterReleaseDefinitionForId(definitions, releaseId);
  const {receipt} = validated;
  if (
    receipt.release.id !== releaseId ||
    definition.id !== releaseId ||
    definition.version !== receipt.release.version ||
    definition.filename !== receipt.release.filename ||
    definition.minecraftVersion !== minecraftVersion ||
    !Array.isArray(definition.qualityProfiles) ||
    !definition.qualityProfiles.includes(profile)
  ) {
    throw new Error('Prepared-publication migration does not match the configured exporter release.');
  }
  const sourcePath = resolveExporterReleaseSourcePath(definition, workspaceRoot);
  const sourceBytes = await readVerifiedExporterJar(
    sourcePath,
    `Prepared-publication migration exporter ${releaseId}`,
  );
  const sourceSha256 = createHash('sha256').update(sourceBytes).digest('hex');
  if (
    sourceBytes.length !== receipt.release.bytes ||
    sourceSha256 !== receipt.release.sha256
  ) {
    throw new Error('Prepared-publication migration exporter JAR bytes do not match its receipt.');
  }
  const jarBuild = inspectExporterJarBuild(sourceBytes);
  if (!isDeepStrictEqual(jarBuild.identity, receipt.exporterBuild)) {
    throw new Error('Prepared-publication migration exporter payload identity does not match its receipt.');
  }
  return Object.freeze({definition, binding: validated});
}

export async function verifyPublicationExporterBuildFile({
  exportRoot,
  binding,
  label = 'Prepared export',
}) {
  const validated = requirePublicationExporterAcceptance(binding);
  const {bytes} = await readVerifiedRegularFile(
    join(exportRoot, EXPORTER_BUILD_EXPORT_PATH),
    `${label}/${EXPORTER_BUILD_EXPORT_PATH}`,
    {minimumBytes: 2, maximumBytes: 4 * 1024},
  );
  const exporterBuild = parseExporterBuildIdentityBytes(
    bytes,
    `${label}/${EXPORTER_BUILD_EXPORT_PATH}`,
  );
  if (!isDeepStrictEqual(exporterBuild, validated.receipt.exporterBuild)) {
    throw new Error(
      `${label}/${EXPORTER_BUILD_EXPORT_PATH} does not match the exact accepted exporter JAR identity.`,
    );
  }
  return exporterBuild;
}

/** Verify the exact staged raw snapshot which the importer will subsequently transform. */
export async function verifyAcceptedRawPublicationExport({
  exportRoot,
  binding,
  logger = console,
}) {
  const validated = requirePublicationExporterAcceptance(binding);
  const {receipt} = validated;
  logger.info(
    `[publication-acceptance] Hashing the staged ${receipt.release.id} raw snapshot before optimization.`,
  );
  await verifyPublicationExporterBuildFile({
    exportRoot,
    binding: validated,
    label: 'Staged raw export',
  });
  const manifest = await readVerifiedRegularFile(
    join(exportRoot, 'manifest.json'),
    'Staged raw export/manifest.json',
    {minimumBytes: 2, maximumBytes: 128 * 1024},
  );
  const manifestSha256 = createHash('sha256').update(manifest.bytes).digest('hex');
  if (
    manifest.bytes.length !== receipt.exportManifest.bytes ||
    manifestSha256 !== receipt.exportManifest.sha256
  ) {
    throw new Error(
      'Staged raw export manifest.json does not match the exact accepted full-export manifest.',
    );
  }
  const tree = await digestExportTree(exportRoot, {logger});
  if (!sameExportTreeDigest(tree, receipt.exportTree)) {
    throw new Error(
      'Staged raw export tree does not match the exact exporter acceptance receipt; no publication plan will be committed.',
    );
  }
  logger.info(
    `[publication-acceptance] Accepted staged raw snapshot sha256=${tree.sha256} for ${receipt.release.id}.`,
  );
  return tree;
}

export function requirePublicationAcceptanceContext(binding, context) {
  return requireAcceptanceContext(binding, context);
}
