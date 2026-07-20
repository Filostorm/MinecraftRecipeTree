import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import {
  DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  DEFAULT_EXPORTER_WORKSPACE_ROOT,
  MAX_EXPORTER_MANIFEST_BYTES,
  buildExporterAcceptanceReceipt,
  exporterAcceptancePolicySha256,
  exporterReleaseDefinitionForId,
  requireExporterArtifactProvenance,
  readVerifiedExporterJar,
  readVerifiedRegularFile,
  resolveExporterReleaseSourcePath,
  writeExporterAcceptanceReceipt,
} from './exporter-release-acceptance.mjs';
import {EXPORTER_RELEASE_DEFINITIONS} from './package-exporter-releases.mjs';
import {DEFAULT_EXPORTER_PUBLIC_ROOT} from './exporter-release-lock.mjs';
import {
  qualityProfileRequirementsFor,
  resolveQualityProfile,
} from './export-quality-policy.mjs';
import {requirePublishablePackIdentity} from './pack-identity.mjs';
import {validateExportData} from './validate-export-data.mjs';
import {
  EXPORTER_BUILD_EXPORT_PATH,
  EXPORTER_BUILD_FORMAT,
  inspectExporterJarBuild,
  requireMatchingExportedBuildIdentity,
} from './exporter-artifact-provenance.mjs';
import {digestExportTree, sameExportTreeDigest} from './export-tree-digest.mjs';

const SCRIPT_PATH = fileURLToPath(import.meta.url);

function usage() {
  return [
    'Validate one completed full export and write a SHA-256-bound exporter acceptance receipt:',
    '  node scripts/write-exporter-acceptance-receipt.mjs',
    '    --release <release-id> --profile <quality-profile> --export-root <directory>',
    '',
    `Receipts are written under ${DEFAULT_EXPORTER_ACCEPTANCE_ROOT}.`,
    'Diagnostic qualitySample exports are rejected; use the completed full export.',
  ].join('\n');
}

function valueAfter(argv, index, flag) {
  const value = argv[index + 1];
  if (value === undefined || value.startsWith('--')) {
    throw new Error(`${flag} requires a value.`);
  }
  return value;
}

export function parseExporterAcceptanceArguments(argv) {
  if (argv.length === 1 && ['--help', '-h'].includes(argv[0])) {
    return {command: 'help'};
  }
  if (argv.length === 0) {
    throw new Error(`Exporter acceptance requires an explicit action.\n${usage()}`);
  }
  const values = {};
  const names = new Map([
    ['--release', 'releaseId'],
    ['--profile', 'profile'],
    ['--export-root', 'exportRoot'],
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    if (flag === '--help' || flag === '-h') {
      throw new Error(`Help must be requested as the only argument.\n${usage()}`);
    }
    const name = names.get(flag);
    if (!name) throw new Error(`Unsupported exporter acceptance argument: ${flag}.\n${usage()}`);
    if (values[name] !== undefined) throw new Error(`${flag} was provided more than once.`);
    values[name] = valueAfter(argv, index, flag);
    index += 1;
  }
  const missing = ['releaseId', 'profile', 'exportRoot'].filter(name => values[name] === undefined);
  if (missing.length > 0) {
    throw new Error(`Missing exporter acceptance argument(s): ${missing.join(', ')}.\n${usage()}`);
  }
  return {command: 'accept', ...values};
}

function requireContainedExportRoot(exportRoot) {
  if (typeof exportRoot !== 'string' || exportRoot.length === 0) {
    throw new Error('--export-root must be a non-empty directory path.');
  }
  return resolve(exportRoot);
}

function parseManifest(bytes, label) {
  let value;
  try {
    value = JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`${label} is not valid JSON.`, {cause: error});
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must contain an object.`);
  }
  return value;
}

export async function acceptExporterRelease({
  releaseId,
  profile,
  exportRoot,
  workspaceRoot = DEFAULT_EXPORTER_WORKSPACE_ROOT,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  publicRoot = DEFAULT_EXPORTER_PUBLIC_ROOT,
  definitions = EXPORTER_RELEASE_DEFINITIONS,
  acceptedAt = new Date().toISOString(),
  logger = console,
  testOnlyValidateExport = validateExportData,
} = {}) {
  const definition = exporterReleaseDefinitionForId(definitions, releaseId);
  const qualityProfile = resolveQualityProfile(profile);
  const qualityRequirements = qualityProfileRequirementsFor(qualityProfile);
  if (
    !Array.isArray(definition.qualityProfiles) ||
    qualityProfile !== definition.acceptanceProfile ||
    !definition.qualityProfiles.includes(qualityProfile)
  ) {
    const allowedProfiles = Array.isArray(definition.qualityProfiles)
      ? definition.qualityProfiles.join(', ')
      : '(invalid configuration)';
    throw new Error(
      `Release ${releaseId} requires acceptance profile ${JSON.stringify(definition.acceptanceProfile)}; ` +
        `received ${JSON.stringify(qualityProfile)}. Advertised profiles: ${allowedProfiles}.`,
    );
  }
  const artifactProvenance = requireExporterArtifactProvenance(definition);
  if (qualityRequirements?.requiresExporterBuildIdentity && artifactProvenance === null) {
    throw new Error(
      `Release ${releaseId} must configure exact ${EXPORTER_BUILD_FORMAT} provenance before acceptance.`,
    );
  }
  if (!qualityRequirements?.requiresExporterBuildIdentity && artifactProvenance !== null) {
    throw new Error(
      `Release ${releaseId} configures exporter provenance, but its acceptance profile does not require it.`,
    );
  }
  if (qualityRequirements?.requiresExporterBuildIdentity && definition.acceptanceCorpus === null) {
    throw new Error(
      `Release ${releaseId} has no exact acceptanceCorpus yet. Populate its full-export counts after the completed run before issuing a receipt.`,
    );
  }
  const sourcePath = resolveExporterReleaseSourcePath(definition, workspaceRoot);
  const sourceBytes = await readVerifiedExporterJar(
    sourcePath,
    `Exporter release ${definition.id}`,
  );
  const jarBuild = qualityRequirements?.requiresExporterBuildIdentity
    ? inspectExporterJarBuild(sourceBytes)
    : null;
  if (
    jarBuild !== null &&
    (jarBuild.identity.format !== artifactProvenance.format ||
      jarBuild.identity.exporterId !== artifactProvenance.exporterId ||
      jarBuild.identity.minecraftVersion !== artifactProvenance.minecraftVersion)
  ) {
    throw new Error(
      `Release ${releaseId} source JAR identity does not match its configured exporter ID and Minecraft version.`,
    );
  }
  const validationPolicySha256 = await exporterAcceptancePolicySha256(definition);
  const root = requireContainedExportRoot(exportRoot);
  const exportTreeBefore = await digestExportTree(root, {logger});
  const manifestPath = join(root, 'manifest.json');
  const manifestBefore = await readVerifiedRegularFile(
    manifestPath,
    'Acceptance export manifest.json',
    {minimumBytes: 2, maximumBytes: MAX_EXPORTER_MANIFEST_BYTES},
  );
  const manifest = parseManifest(manifestBefore.bytes, 'Acceptance export manifest.json');
  if (Object.prototype.hasOwnProperty.call(manifest, 'qualitySample')) {
    throw new Error(
      'Acceptance export manifest.json contains qualitySample and is a diagnostic mini export. Run and validate the completed full export.',
    );
  }
  if (manifest.minecraft !== definition.minecraftVersion) {
    throw new Error(
      `Acceptance export Minecraft version ${JSON.stringify(manifest.minecraft)} does not match release ${definition.id} (${definition.minecraftVersion}).`,
    );
  }
  const pack = requirePublishablePackIdentity(manifest.pack, 'Acceptance export manifest.pack');
  if (
    qualityRequirements?.packIdentity !== undefined &&
    !isDeepStrictEqual(pack, qualityRequirements.packIdentity)
  ) {
    throw new Error(
      `Acceptance export pack identity does not match profile ${qualityProfile}: expected ` +
        `${JSON.stringify(qualityRequirements.packIdentity)}, received ${JSON.stringify(pack)}.`,
    );
  }
  const corpus = {
    items: manifest.counts?.items,
    recipes: manifest.counts?.recipes,
    categories: manifest.counts?.categories,
    mobs: manifest.counts?.mobs,
    blockDrops: manifest.counts?.blockDrops,
  };
  if (
    definition.acceptanceCorpus !== null &&
    !isDeepStrictEqual(corpus, definition.acceptanceCorpus)
  ) {
    throw new Error(
      `Acceptance export corpus does not match release ${releaseId}: expected ` +
        `${JSON.stringify(definition.acceptanceCorpus)}, received ${JSON.stringify(corpus)}.`,
    );
  }
  let exporterBuild = null;
  if (jarBuild !== null) {
    const exportedBuild = await readVerifiedRegularFile(
      join(root, EXPORTER_BUILD_EXPORT_PATH),
      `Acceptance ${EXPORTER_BUILD_EXPORT_PATH}`,
      {minimumBytes: 2, maximumBytes: 4 * 1024},
    );
    exporterBuild = requireMatchingExportedBuildIdentity(exportedBuild.bytes, jarBuild);
  }

  logger.info(
    `[exporter-acceptance] Validating the complete ${definition.id} export before issuing a receipt.`,
  );
  const summary = await testOnlyValidateExport(root, {
    profile: qualityProfile,
    requirePackIdentity: true,
    assetMode: 'raw',
  });

  const manifestAfter = await readVerifiedRegularFile(
    manifestPath,
    'Acceptance export manifest.json after validation',
    {minimumBytes: 2, maximumBytes: MAX_EXPORTER_MANIFEST_BYTES},
  );
  if (!manifestAfter.bytes.equals(manifestBefore.bytes)) {
    throw new Error(
      'Acceptance export manifest.json changed during validation; no receipt was written. Stop the exporter and validate the completed directory again.',
    );
  }
  const exportTreeAfter = await digestExportTree(root, {logger});
  if (!sameExportTreeDigest(exportTreeAfter, exportTreeBefore)) {
    throw new Error(
      'Acceptance export tree changed during exhaustive validation; no receipt was written. Stop the exporter and validate the completed immutable directory again.',
    );
  }

  const receipt = buildExporterAcceptanceReceipt({
    definition,
    sourceBytes,
    qualityProfile,
    exportManifestBytes: manifestAfter.bytes,
    exportManifest: manifest,
    pack,
    exporterBuild,
    exportTree: exportTreeAfter,
    validationPolicySha256,
    acceptedAt,
  });
  const written = await writeExporterAcceptanceReceipt({
    receipt,
    acceptanceRoot,
    publicRoot,
    logger,
  });
  return Object.freeze({...written, summary});
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === SCRIPT_PATH) {
  try {
    const options = parseExporterAcceptanceArguments(process.argv.slice(2));
    if (options.command === 'help') {
      console.log(usage());
    } else {
      await acceptExporterRelease(options);
    }
  } catch (error) {
    console.error(
      `[exporter-acceptance] ${error instanceof Error ? error.message : String(error)}`,
    );
    process.exitCode = 1;
  }
}
