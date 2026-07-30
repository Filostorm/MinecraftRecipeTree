import {constants} from 'node:fs';
import {createHash} from 'node:crypto';
import {lstat, mkdir, open, rename, unlink, writeFile} from 'node:fs/promises';
import {dirname, isAbsolute, join, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import {
  EXPORTER_BUILD_FORMAT,
  inspectExporterJarBuild,
  requireExporterBuildIdentity,
} from './exporter-artifact-provenance.mjs';
import {qualityProfileRequirementsFor} from './export-quality-policy.mjs';
import {
  EXPORT_TREE_DIGEST_ALGORITHM,
  EXPORT_TREE_DIGEST_FORMAT,
} from './export-tree-digest.mjs';
import {
  DEFAULT_EXPORTER_PUBLIC_ROOT,
  withExporterReleaseManifestLock,
} from './exporter-release-lock.mjs';

export const EXPORTER_ACCEPTANCE_RECEIPT_FORMAT = 'mrt-exporter-acceptance-v1';
export const EXPORTER_ACCEPTANCE_POLICY_FORMAT = 'mrt-exporter-acceptance-policy-v1';
export const MAX_EXPORTER_JAR_BYTES = 16 * 1024 * 1024;
export const MAX_EXPORTER_MANIFEST_BYTES = 128 * 1024;
export const MAX_EXPORTER_ACCEPTANCE_RECEIPT_BYTES = 64 * 1024;

const SCRIPT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const PACKAGE_LOCK_PATH = resolve(SCRIPT_DIRECTORY, '..', 'package-lock.json');
const ACCEPTANCE_POLICY_FILES = Object.freeze([
  Object.freeze({
    label: 'exporter-release-acceptance.mjs',
    path: fileURLToPath(import.meta.url),
  }),
  Object.freeze({
    label: 'write-exporter-acceptance-receipt.mjs',
    path: join(SCRIPT_DIRECTORY, 'write-exporter-acceptance-receipt.mjs'),
  }),
  Object.freeze({
    label: 'validate-export-data.mjs',
    path: join(SCRIPT_DIRECTORY, 'validate-export-data.mjs'),
  }),
  Object.freeze({
    label: 'export-data-utils.mjs',
    path: join(SCRIPT_DIRECTORY, 'export-data-utils.mjs'),
  }),
  Object.freeze({
    label: 'export-quality-policy.mjs',
    path: join(SCRIPT_DIRECTORY, 'export-quality-policy.mjs'),
  }),
  Object.freeze({
    label: 'visual-assets-rights-policy.mjs',
    path: join(SCRIPT_DIRECTORY, 'visual-assets-rights-policy.mjs'),
  }),
  Object.freeze({
    label: 'pack-identity.mjs',
    path: join(SCRIPT_DIRECTORY, 'pack-identity.mjs'),
  }),
  Object.freeze({
    label: 'publication-id.mjs',
    path: join(SCRIPT_DIRECTORY, 'publication-id.mjs'),
  }),
  Object.freeze({
    label: 'recipe-image-inventory.mjs',
    path: join(SCRIPT_DIRECTORY, 'recipe-image-inventory.mjs'),
  }),
  Object.freeze({
    label: 'packed-assets.mjs',
    path: join(SCRIPT_DIRECTORY, 'packed-assets.mjs'),
  }),
  Object.freeze({
    label: 'sharded-documents.mjs',
    path: join(SCRIPT_DIRECTORY, 'sharded-documents.mjs'),
  }),
  Object.freeze({
    label: 'exporter-artifact-provenance.mjs',
    path: join(SCRIPT_DIRECTORY, 'exporter-artifact-provenance.mjs'),
  }),
  Object.freeze({
    label: 'export-tree-digest.mjs',
    path: join(SCRIPT_DIRECTORY, 'export-tree-digest.mjs'),
  }),
]);
export const DEFAULT_EXPORTER_WORKSPACE_ROOT = resolve(SCRIPT_DIRECTORY, '..');
export const DEFAULT_EXPORTER_ACCEPTANCE_ROOT = resolve(
  SCRIPT_DIRECTORY,
  '..',
  '.release-acceptance',
);

const RELEASE_ID_PATTERN = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/;
const MINECRAFT_VERSION_PATTERN = /^[0-9]+(?:\.[0-9]+){1,2}$/;
const RELEASE_FILENAME_PATTERN = /^[a-z0-9][a-z0-9.-]{0,198}\.jar$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const UNSAFE_TEXT_PATTERN =
  /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value, expected) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function requireBoundedText(value, label, maximumCodePoints) {
  if (
    typeof value !== 'string' ||
    value.trim() !== value ||
    value.length === 0 ||
    [...value].length > maximumCodePoints ||
    UNSAFE_TEXT_PATTERN.test(value)
  ) {
    throw new Error(
      `${label} must be trimmed, non-empty, at most ${maximumCodePoints} characters, and contain no control or directional-formatting characters.`,
    );
  }
  return value;
}

function requireCanonicalTimestamp(value, label) {
  if (
    typeof value !== 'string' ||
    !Number.isFinite(Date.parse(value)) ||
    new Date(value).toISOString() !== value
  ) {
    throw new Error(`${label} must be a canonical ISO timestamp.`);
  }
  return value;
}

export function sha256Hex(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

/**
 * Read a bounded regular file without following a final symlink or accepting a hard link. The
 * before/opened/after checks make in-place replacement or mutation visible to the caller.
 */
export async function readVerifiedRegularFile(
  path,
  label,
  {minimumBytes = 1, maximumBytes = Number.MAX_SAFE_INTEGER} = {},
) {
  const before = await lstat(path);
  if (before.isSymbolicLink() || !before.isFile() || before.nlink !== 1) {
    throw new Error(`${label} must be a plain, non-hard-linked regular file: ${path}.`);
  }
  if (before.size < minimumBytes || before.size > maximumBytes) {
    throw new Error(`${label} must be between ${minimumBytes} and ${maximumBytes} bytes.`);
  }
  if (!Number.isSafeInteger(constants.O_NOFOLLOW)) {
    throw new Error(`${label} cannot be read because this runtime lacks O_NOFOLLOW.`);
  }
  const handle = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const opened = await handle.stat();
    if (
      !opened.isFile() ||
      opened.nlink !== 1 ||
      opened.dev !== before.dev ||
      opened.ino !== before.ino ||
      opened.size !== before.size
    ) {
      throw new Error(`${label} changed between inspection and secure open.`);
    }
    const bytes = await handle.readFile();
    const after = await handle.stat();
    if (
      after.dev !== opened.dev ||
      after.ino !== opened.ino ||
      after.size !== opened.size ||
      after.mtimeMs !== opened.mtimeMs
    ) {
      throw new Error(`${label} changed while it was read.`);
    }
    return Object.freeze({bytes, stat: opened});
  } finally {
    await handle.close();
  }
}

export async function readVerifiedExporterJar(path, label) {
  const result = await readVerifiedRegularFile(path, label, {
    minimumBytes: 4,
    maximumBytes: MAX_EXPORTER_JAR_BYTES,
  });
  const {bytes} = result;
  if (bytes[0] !== 0x50 || bytes[1] !== 0x4b || bytes[2] !== 0x03 || bytes[3] !== 0x04) {
    throw new Error(`${label} does not begin with the ZIP/JAR local-file signature.`);
  }
  return bytes;
}

export function exporterReleaseDefinitionForId(definitions, releaseId) {
  if (typeof releaseId !== 'string' || !RELEASE_ID_PATTERN.test(releaseId)) {
    throw new Error('Release ID must be one canonical lowercase identifier.');
  }
  if (!Array.isArray(definitions) || definitions.length < 1 || definitions.length > 16) {
    throw new Error('Exporter release definitions must contain between 1 and 16 entries.');
  }
  const matches = definitions.filter(definition => definition?.id === releaseId);
  if (matches.length !== 1) {
    throw new Error(
      `Release ${JSON.stringify(releaseId)} must match exactly one configured definition; matched ${matches.length}.`,
    );
  }
  return matches[0];
}

export function resolveExporterReleaseSourcePath(definition, workspaceRoot) {
  if (
    !isRecord(definition) ||
    typeof definition.source !== 'string' ||
    isAbsolute(definition.source) ||
    typeof definition.filename !== 'string' ||
    !RELEASE_FILENAME_PATTERN.test(definition.filename) ||
    /(?:-dev|-sources)\.jar$/i.test(definition.filename)
  ) {
    throw new Error(`Exporter release ${String(definition?.id)} has an invalid source or filename.`);
  }
  const root = resolve(workspaceRoot);
  const sourcePath = resolve(root, definition.source);
  const sourceRelative = relative(root, sourcePath);
  if (
    sourceRelative === '' ||
    isAbsolute(sourceRelative) ||
    sourceRelative === '..' ||
    sourceRelative.startsWith(`..${sep}`)
  ) {
    throw new Error(`Exporter release ${definition.id} resolves outside the workspace root.`);
  }
  return sourcePath;
}

export function exporterAcceptanceReceiptPath(
  releaseId,
  qualityProfile,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
) {
  if (typeof releaseId !== 'string' || !RELEASE_ID_PATTERN.test(releaseId)) {
    throw new Error('Acceptance receipt release ID must be canonical.');
  }
  if (typeof qualityProfile !== 'string' || !RELEASE_ID_PATTERN.test(qualityProfile)) {
    throw new Error('Acceptance receipt quality profile must be canonical.');
  }
  return join(resolve(acceptanceRoot), `${releaseId}--${qualityProfile}.json`);
}

export function legacyExporterAcceptanceReceiptPath(
  releaseId,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
) {
  if (typeof releaseId !== 'string' || !RELEASE_ID_PATTERN.test(releaseId)) {
    throw new Error('Acceptance receipt release ID must be canonical.');
  }
  return join(resolve(acceptanceRoot), `${releaseId}.json`);
}

function updateDigestPart(hash, label, bytes) {
  const labelBytes = Buffer.from(label, 'utf8');
  hash.update(`${labelBytes.length}:`);
  hash.update(labelBytes);
  hash.update(`:${bytes.length}:`);
  hash.update(bytes);
  hash.update(';');
}

function isAcceptanceCorpus(value) {
  return (
    value === null ||
    (hasExactKeys(value, ['blockDrops', 'categories', 'items', 'mobs', 'recipes']) &&
      Object.values(value).every(count => Number.isSafeInteger(count) && count >= 0))
  );
}

function canonicalAcceptanceDefinition(definition, qualityProfile) {
  const artifactProvenance = requireExporterArtifactProvenance(definition);
  const qualityProfiles = definition?.qualityProfiles;
  const acceptanceCorpora = definition?.acceptanceCorpora;
  if (
    !hasExactKeys(definition, [
      'acceptanceCorpora',
      'artifactProvenance',
      'compatibility',
      'filename',
      'id',
      'loader',
      'minecraftVersion',
      'qualityProfiles',
      'recipeViewer',
      'source',
      'version',
    ]) ||
    !Array.isArray(qualityProfiles) ||
    qualityProfiles.length < 1 ||
    qualityProfiles.length > 8 ||
    new Set(qualityProfiles).size !== qualityProfiles.length ||
    qualityProfiles.some(
      profile => typeof profile !== 'string' || !RELEASE_ID_PATTERN.test(profile),
    ) ||
    !isRecord(acceptanceCorpora) ||
    !hasExactKeys(acceptanceCorpora, qualityProfiles) ||
    Object.values(acceptanceCorpora).some(corpus => !isAcceptanceCorpus(corpus)) ||
    typeof qualityProfile !== 'string' ||
    !qualityProfiles.includes(qualityProfile)
  ) {
    throw new Error(
      `Exporter release ${String(definition?.id)} must satisfy the exact per-profile acceptance, provenance, and corpus-definition contract.`,
    );
  }
  for (const profile of qualityProfiles) qualityProfileRequirementsFor(profile);
  const selectedCorpus = acceptanceCorpora[qualityProfile];
  return {
    id: definition.id,
    minecraftVersion: definition.minecraftVersion,
    recipeViewer: definition.recipeViewer,
    loader: definition.loader,
    version: definition.version,
    source: definition.source,
    filename: definition.filename,
    qualityProfiles: [...qualityProfiles],
    artifactProvenance,
    acceptance: {
      qualityProfile,
      corpus: selectedCorpus === null ? null : {...selectedCorpus},
    },
    compatibility: definition.compatibility,
  };
}

export function exporterAcceptanceCorpusForProfile(definition, qualityProfile) {
  return canonicalAcceptanceDefinition(definition, qualityProfile).acceptance.corpus;
}

export function requireExporterArtifactProvenance(definition) {
  const value = definition?.artifactProvenance;
  if (value === null) return null;
  if (!hasExactKeys(value, ['exporterId', 'format', 'minecraftVersion'])) {
    throw new Error(
      `Exporter release ${String(definition?.id)} artifactProvenance violates the exact contract.`,
    );
  }
  if (
    value.format !== EXPORTER_BUILD_FORMAT ||
    typeof value.exporterId !== 'string' ||
    !RELEASE_ID_PATTERN.test(value.exporterId) ||
    value.exporterId !== definition.id ||
    typeof value.minecraftVersion !== 'string' ||
    !MINECRAFT_VERSION_PATTERN.test(value.minecraftVersion) ||
    value.minecraftVersion !== definition.minecraftVersion
  ) {
    throw new Error(
      `Exporter release ${String(definition?.id)} artifactProvenance must bind ${EXPORTER_BUILD_FORMAT} to the exact release ID and Minecraft version.`,
    );
  }
  return Object.freeze({
    format: EXPORTER_BUILD_FORMAT,
    exporterId: value.exporterId,
    minecraftVersion: value.minecraftVersion,
  });
}

async function sharpValidationDependencyLockBytes() {
  const {bytes} = await readVerifiedRegularFile(
    PACKAGE_LOCK_PATH,
    'Exporter acceptance package-lock.json',
    {minimumBytes: 2, maximumBytes: 4 * 1024 * 1024},
  );
  let lockfile;
  try {
    lockfile = JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error('Exporter acceptance package-lock.json is not valid JSON.', {cause: error});
  }
  if (!isRecord(lockfile) || !isRecord(lockfile.packages)) {
    throw new Error('Exporter acceptance package-lock.json has no packages contract.');
  }
  const sharpEntries = Object.entries(lockfile.packages)
    .filter(
      ([path]) => path === 'node_modules/sharp' || path.startsWith('node_modules/@img/sharp-'),
    )
    .sort(([left], [right]) => left.localeCompare(right));
  if (!sharpEntries.some(([path]) => path === 'node_modules/sharp')) {
    throw new Error('Exporter acceptance package-lock.json does not pin sharp.');
  }
  return Buffer.from(JSON.stringify(sharpEntries), 'utf8');
}

/**
 * Bind a receipt to the exact validation entrypoint, profile/identity policy, receipt machinery,
 * and the shared release definition plus selected profile corpus. Tightening shared inputs
 * invalidates every profile; promoting a sibling corpus does not invalidate an independent receipt.
 */
export async function exporterAcceptancePolicySha256(definition, qualityProfile) {
  const hash = createHash('sha256');
  updateDigestPart(
    hash,
    'policy-format',
    Buffer.from(EXPORTER_ACCEPTANCE_POLICY_FORMAT, 'utf8'),
  );
  updateDigestPart(
    hash,
    'release-definition',
    Buffer.from(
      JSON.stringify(canonicalAcceptanceDefinition(definition, qualityProfile)),
      'utf8',
    ),
  );
  for (const policyFile of ACCEPTANCE_POLICY_FILES) {
    const {bytes} = await readVerifiedRegularFile(
      policyFile.path,
      `Exporter acceptance policy file ${policyFile.label}`,
      {minimumBytes: 1, maximumBytes: 1024 * 1024},
    );
    updateDigestPart(hash, policyFile.label, bytes);
  }
  updateDigestPart(
    hash,
    'sharp-package-lock-entries',
    await sharpValidationDependencyLockBytes(),
  );
  return hash.digest('hex');
}

export function requireExporterAcceptanceReceipt(value) {
  if (
    !hasExactKeys(value, [
      'acceptedAt',
      'exportTree',
      'exportManifest',
      'exporterBuild',
      'format',
      'qualityProfile',
      'release',
      'validationPolicy',
    ])
  ) {
    throw new Error('Exporter acceptance receipt violates the exact top-level contract.');
  }
  if (value.format !== EXPORTER_ACCEPTANCE_RECEIPT_FORMAT) {
    throw new Error(
      `Exporter acceptance receipt format must be ${EXPORTER_ACCEPTANCE_RECEIPT_FORMAT}.`,
    );
  }
  const acceptedAt = requireCanonicalTimestamp(value.acceptedAt, 'Acceptance receipt acceptedAt');
  const qualityProfile = requireBoundedText(
    value.qualityProfile,
    'Acceptance receipt qualityProfile',
    80,
  );
  if (!RELEASE_ID_PATTERN.test(qualityProfile)) {
    throw new Error('Acceptance receipt qualityProfile must be a canonical profile ID.');
  }
  if (!hasExactKeys(value.validationPolicy, ['format', 'sha256'])) {
    throw new Error('Acceptance receipt validationPolicy object violates the exact contract.');
  }
  if (value.validationPolicy.format !== EXPORTER_ACCEPTANCE_POLICY_FORMAT) {
    throw new Error(
      `Acceptance receipt validationPolicy.format must be ${EXPORTER_ACCEPTANCE_POLICY_FORMAT}.`,
    );
  }
  if (!SHA256_PATTERN.test(value.validationPolicy.sha256)) {
    throw new Error('Acceptance receipt validationPolicy.sha256 must be lowercase SHA-256.');
  }

  let exporterBuild = null;
  if (value.exporterBuild !== null) {
    exporterBuild = requireExporterBuildIdentity(value.exporterBuild);
  }

  if (
    !hasExactKeys(value.exportTree, ['algorithm', 'bytes', 'files', 'format', 'sha256']) ||
    value.exportTree.format !== EXPORT_TREE_DIGEST_FORMAT ||
    value.exportTree.algorithm !== EXPORT_TREE_DIGEST_ALGORITHM ||
    !SHA256_PATTERN.test(value.exportTree.sha256) ||
    !Number.isSafeInteger(value.exportTree.files) ||
    value.exportTree.files < 1 ||
    !Number.isSafeInteger(value.exportTree.bytes) ||
    value.exportTree.bytes < 1
  ) {
    throw new Error('Acceptance receipt exportTree violates the exact digest contract.');
  }

  if (!hasExactKeys(value.release, ['bytes', 'filename', 'id', 'sha256', 'version'])) {
    throw new Error('Acceptance receipt release object violates the exact contract.');
  }
  const releaseId = requireBoundedText(value.release.id, 'Acceptance receipt release.id', 80);
  if (!RELEASE_ID_PATTERN.test(releaseId)) {
    throw new Error('Acceptance receipt release.id must be canonical.');
  }
  const releaseVersion = requireBoundedText(
    value.release.version,
    'Acceptance receipt release.version',
    80,
  );
  if (
    typeof value.release.filename !== 'string' ||
    !RELEASE_FILENAME_PATTERN.test(value.release.filename) ||
    /(?:-dev|-sources)\.jar$/i.test(value.release.filename)
  ) {
    throw new Error('Acceptance receipt release.filename is invalid.');
  }
  if (!SHA256_PATTERN.test(value.release.sha256)) {
    throw new Error('Acceptance receipt release.sha256 must be lowercase SHA-256.');
  }
  if (
    !Number.isSafeInteger(value.release.bytes) ||
    value.release.bytes < 4 ||
    value.release.bytes > MAX_EXPORTER_JAR_BYTES
  ) {
    throw new Error('Acceptance receipt release.bytes is outside the exporter JAR bound.');
  }

  if (
    !hasExactKeys(value.exportManifest, [
      'bytes',
      'counts',
      'generatedAt',
      'minecraft',
      'pack',
      'sha256',
    ])
  ) {
    throw new Error('Acceptance receipt exportManifest object violates the exact contract.');
  }
  if (!SHA256_PATTERN.test(value.exportManifest.sha256)) {
    throw new Error('Acceptance receipt exportManifest.sha256 must be lowercase SHA-256.');
  }
  if (
    !Number.isSafeInteger(value.exportManifest.bytes) ||
    value.exportManifest.bytes < 2 ||
    value.exportManifest.bytes > MAX_EXPORTER_MANIFEST_BYTES
  ) {
    throw new Error('Acceptance receipt exportManifest.bytes is outside the manifest bound.');
  }
  const exportGeneratedAt = requireBoundedText(
    value.exportManifest.generatedAt,
    'Acceptance receipt exportManifest.generatedAt',
    80,
  );
  if (!Number.isFinite(Date.parse(exportGeneratedAt))) {
    throw new Error('Acceptance receipt exportManifest.generatedAt must be parseable.');
  }
  const minecraft = requireBoundedText(
    value.exportManifest.minecraft,
    'Acceptance receipt exportManifest.minecraft',
    40,
  );
  if (
    !hasExactKeys(value.exportManifest.counts, [
      'blockDrops',
      'categories',
      'items',
      'mobs',
      'recipes',
    ]) ||
    Object.values(value.exportManifest.counts).some(
      count => !Number.isSafeInteger(count) || count < 0,
    )
  ) {
    throw new Error('Acceptance receipt exportManifest.counts violates the exact corpus contract.');
  }
  if (!hasExactKeys(value.exportManifest.pack, ['identitySource', 'name', 'version'])) {
    throw new Error('Acceptance receipt exportManifest.pack violates the exact contract.');
  }
  const pack = Object.freeze({
    name: requireBoundedText(
      value.exportManifest.pack.name,
      'Acceptance receipt exportManifest.pack.name',
      120,
    ),
    version: requireBoundedText(
      value.exportManifest.pack.version,
      'Acceptance receipt exportManifest.pack.version',
      80,
    ),
    identitySource: requireBoundedText(
      value.exportManifest.pack.identitySource,
      'Acceptance receipt exportManifest.pack.identitySource',
      40,
    ),
  });

  return Object.freeze({
    format: EXPORTER_ACCEPTANCE_RECEIPT_FORMAT,
    acceptedAt,
    release: Object.freeze({
      id: releaseId,
      version: releaseVersion,
      filename: value.release.filename,
      sha256: value.release.sha256,
      bytes: value.release.bytes,
    }),
    qualityProfile,
    exporterBuild,
    exportTree: Object.freeze({
      format: EXPORT_TREE_DIGEST_FORMAT,
      algorithm: EXPORT_TREE_DIGEST_ALGORITHM,
      sha256: value.exportTree.sha256,
      files: value.exportTree.files,
      bytes: value.exportTree.bytes,
    }),
    validationPolicy: Object.freeze({
      format: EXPORTER_ACCEPTANCE_POLICY_FORMAT,
      sha256: value.validationPolicy.sha256,
    }),
    exportManifest: Object.freeze({
      sha256: value.exportManifest.sha256,
      bytes: value.exportManifest.bytes,
      generatedAt: exportGeneratedAt,
      minecraft,
      counts: Object.freeze({...value.exportManifest.counts}),
      pack,
    }),
  });
}

export function buildExporterAcceptanceReceipt({
  definition,
  sourceBytes,
  qualityProfile,
  exportManifestBytes,
  exportManifest,
  pack,
  exporterBuild,
  exportTree,
  validationPolicySha256,
  acceptedAt = new Date().toISOString(),
}) {
  const candidate = {
    format: EXPORTER_ACCEPTANCE_RECEIPT_FORMAT,
    acceptedAt,
    release: {
      id: definition.id,
      version: definition.version,
      filename: definition.filename,
      sha256: sha256Hex(sourceBytes),
      bytes: sourceBytes.length,
    },
    qualityProfile,
    exporterBuild,
    exportTree,
    validationPolicy: {
      format: EXPORTER_ACCEPTANCE_POLICY_FORMAT,
      sha256: validationPolicySha256,
    },
    exportManifest: {
      sha256: sha256Hex(exportManifestBytes),
      bytes: exportManifestBytes.length,
      generatedAt: exportManifest.generatedAt,
      minecraft: exportManifest.minecraft,
      counts: {
        items: exportManifest.counts.items,
        recipes: exportManifest.counts.recipes,
        categories: exportManifest.counts.categories,
        mobs: exportManifest.counts.mobs,
        blockDrops: exportManifest.counts.blockDrops,
      },
      pack: {
        name: pack.name,
        version: pack.version,
        identitySource: pack.identitySource,
      },
    },
  };
  return requireExporterAcceptanceReceipt(candidate);
}

async function atomicWrite(path, bytes) {
  const temporary = join(
    dirname(path),
    `.${path.split(sep).at(-1)}.${process.pid}.${Date.now()}.tmp`,
  );
  try {
    await writeFile(temporary, bytes, {flag: 'wx', mode: 0o600});
    await rename(temporary, path);
  } catch (error) {
    try {
      await unlink(temporary);
    } catch (cleanupError) {
      if (cleanupError?.code !== 'ENOENT') {
        throw new AggregateError(
          [error, cleanupError],
          `Acceptance receipt write failed and temporary cleanup also failed: ${temporary}.`,
        );
      }
    }
    throw error;
  }
}

async function writeExporterAcceptanceReceiptUnlocked({
  receipt,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  logger = console,
}) {
  const validated = requireExporterAcceptanceReceipt(receipt);
  await mkdir(acceptanceRoot, {recursive: true, mode: 0o700});
  const rootEntry = await lstat(acceptanceRoot);
  if (rootEntry.isSymbolicLink() || !rootEntry.isDirectory()) {
    throw new Error(`Exporter acceptance root must be a real directory: ${acceptanceRoot}.`);
  }
  const path = exporterAcceptanceReceiptPath(
    validated.release.id,
    validated.qualityProfile,
    acceptanceRoot,
  );
  try {
    const existing = await lstat(path);
    if (existing.isSymbolicLink() || !existing.isFile() || existing.nlink !== 1) {
      throw new Error(`Existing exporter acceptance receipt is not a plain regular file: ${path}.`);
    }
    logger.info(
      `[exporter-acceptance] Replacing the existing ${validated.release.id}/${validated.qualityProfile} receipt with a newly validated exact artifact receipt.`,
    );
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  const bytes = Buffer.from(`${JSON.stringify(validated, null, 2)}\n`, 'utf8');
  await atomicWrite(path, bytes);
  logger.info(
    `[exporter-acceptance] Wrote ${validated.release.id}/${validated.qualityProfile} receipt: exporter sha256=${validated.release.sha256}, export manifest sha256=${validated.exportManifest.sha256}.`,
  );
  return Object.freeze({path, receipt: validated});
}

export async function writeExporterAcceptanceReceipt({
  receipt,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  publicRoot = DEFAULT_EXPORTER_PUBLIC_ROOT,
  logger = console,
  testOnlyBypassManifestLock = false,
}) {
  const validated = requireExporterAcceptanceReceipt(receipt);
  if (testOnlyBypassManifestLock === true) {
    logger.warn(
      '[exporter-acceptance] TEST-ONLY manifest-lock bypass used while writing an acceptance receipt; production CLI calls cannot enable this option.',
    );
    return writeExporterAcceptanceReceiptUnlocked({
      receipt: validated,
      acceptanceRoot,
      logger,
    });
  }
  return withExporterReleaseManifestLock({
    publicRoot,
    operation: `acceptance receipt ${validated.release.id}/${validated.qualityProfile}`,
    logger,
    action: async assertLockOwned => {
      await assertLockOwned();
      const written = await writeExporterAcceptanceReceiptUnlocked({
        receipt: validated,
        acceptanceRoot,
        logger,
      });
      await assertLockOwned();
      return written;
    },
  });
}

export async function readExporterAcceptanceReceipt(
  releaseId,
  qualityProfile,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  logger = console,
) {
  const path = exporterAcceptanceReceiptPath(releaseId, qualityProfile, acceptanceRoot);
  let result;
  let migratedLegacyPath = null;
  try {
    result = await readVerifiedRegularFile(
      path,
      `Exporter acceptance receipt ${releaseId}/${qualityProfile}`,
      {minimumBytes: 2, maximumBytes: MAX_EXPORTER_ACCEPTANCE_RECEIPT_BYTES},
    );
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
    const legacyPath = legacyExporterAcceptanceReceiptPath(releaseId, acceptanceRoot);
    try {
      result = await readVerifiedRegularFile(
        legacyPath,
        `Legacy exporter acceptance receipt ${releaseId}`,
        {minimumBytes: 2, maximumBytes: MAX_EXPORTER_ACCEPTANCE_RECEIPT_BYTES},
      );
    } catch (legacyError) {
      if (legacyError?.code !== 'ENOENT') throw legacyError;
      throw new Error(
        `Exporter acceptance receipt is missing for ${releaseId}/${qualityProfile}: ${path}. Run the acceptance command against that completed full export before packaging.`,
        {cause: error},
      );
    }
    migratedLegacyPath = legacyPath;
  }
  let parsed;
  try {
    parsed = JSON.parse(result.bytes.toString('utf8'));
  } catch (error) {
    throw new Error(
      `Exporter acceptance receipt ${releaseId}/${qualityProfile} is not valid JSON.`,
      {cause: error},
    );
  }
  const receipt = requireExporterAcceptanceReceipt(parsed);
  if (receipt.qualityProfile !== qualityProfile) {
    throw new Error(
      `Exporter acceptance receipt lookup for ${releaseId}/${qualityProfile} found a receipt for ${receipt.qualityProfile}; cross-profile receipt fallback is forbidden.`,
    );
  }
  if (migratedLegacyPath !== null) {
    logger.warn(
      `[exporter-acceptance][migration] Using legacy release-only receipt ${migratedLegacyPath} for an explicit ${releaseId}/${qualityProfile} lookup. Write a fresh profile-keyed receipt before removing the legacy file.`,
    );
  }
  return receipt;
}

export async function requireAcceptedExporterRelease({
  definition,
  sourceBytes,
  qualityProfile,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  logger = console,
}) {
  const acceptanceCorpus = exporterAcceptanceCorpusForProfile(definition, qualityProfile);
  const receipt = await readExporterAcceptanceReceipt(
    definition.id,
    qualityProfile,
    acceptanceRoot,
    logger,
  );
  const expectedSha256 = sha256Hex(sourceBytes);
  const expectedPolicySha256 = await exporterAcceptancePolicySha256(
    definition,
    qualityProfile,
  );
  const qualityRequirements = qualityProfileRequirementsFor(qualityProfile);
  const artifactProvenance = requireExporterArtifactProvenance(definition);
  const sourceArtifactMatches =
    receipt.release.sha256 === expectedSha256 && receipt.release.bytes === sourceBytes.length;
  const mismatches = [];
  if (receipt.release.id !== definition.id) mismatches.push('release ID');
  if (receipt.release.version !== definition.version) mismatches.push('release version');
  if (receipt.release.filename !== definition.filename) mismatches.push('release filename');
  if (receipt.release.sha256 !== expectedSha256) mismatches.push('source JAR SHA-256');
  if (receipt.release.bytes !== sourceBytes.length) mismatches.push('source JAR byte length');
  if (receipt.validationPolicy.sha256 !== expectedPolicySha256) {
    mismatches.push('validation policy or release definition SHA-256');
  }
  if (
    !Array.isArray(definition.qualityProfiles) ||
    receipt.qualityProfile !== qualityProfile ||
    !definition.qualityProfiles.includes(receipt.qualityProfile)
  ) {
    mismatches.push('allowed quality profile');
  }
  if (receipt.exportManifest.minecraft !== definition.minecraftVersion) {
    mismatches.push('validated export Minecraft version');
  }
  if (
    qualityRequirements?.packIdentity !== undefined &&
    !isDeepStrictEqual(receipt.exportManifest.pack, qualityRequirements.packIdentity)
  ) {
    mismatches.push('validated export pack identity');
  }
  if (qualityRequirements?.requiresExporterBuildIdentity) {
    if (artifactProvenance === null) {
      mismatches.push('release artifact-provenance configuration');
    } else if (sourceArtifactMatches) {
      const jarBuild = inspectExporterJarBuild(sourceBytes);
      if (
        jarBuild.identity.format !== artifactProvenance.format ||
        jarBuild.identity.exporterId !== artifactProvenance.exporterId ||
        jarBuild.identity.minecraftVersion !== artifactProvenance.minecraftVersion
      ) {
        mismatches.push('source JAR exporter ID or Minecraft-version identity');
      }
      if (!isDeepStrictEqual(receipt.exporterBuild, jarBuild.identity)) {
        mismatches.push('exporter-emitted exact JAR build identity');
      }
    }
    if (acceptanceCorpus === null) {
      mismatches.push('pending exact full-export corpus definition');
    }
  } else if (receipt.exporterBuild !== null) {
    mismatches.push('unexpected exporter build identity');
  }
  if (
    acceptanceCorpus !== null &&
    !isDeepStrictEqual(receipt.exportManifest.counts, acceptanceCorpus)
  ) {
    mismatches.push('validated exact full-export corpus counts');
  }
  if (mismatches.length > 0) {
    throw new Error(
      `Exporter acceptance receipt for ${definition.id}/${qualityProfile} does not match the configured artifact: ${mismatches.join(', ')}. Validate the exact current JAR and full export again.`,
    );
  }
  return receipt;
}
