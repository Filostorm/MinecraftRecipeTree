import {createHash} from 'node:crypto';
import {constants} from 'node:fs';
import {lstat, mkdir, open, readdir, realpath, rename, writeFile} from 'node:fs/promises';
import {basename, dirname, isAbsolute, join, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  assertSameBuildTree,
  digestBuildTree,
  digestColdDatasetBenchmarkSource,
  requireCurrentStorageEligibleBenchmarkReport,
} from './benchmark-cold-dataset.mjs';
import {buildCoreDatasetPublication} from './build-core-dataset-publication.mjs';
import {buildRecipePreviewSidecar} from './build-recipe-preview-sidecar.mjs';
import {administerDatasetChannel} from './dataset-channel-admin.mjs';
import {readJsonDocument} from './export-data-utils.mjs';
import {EXPORT_QUALITY_PROFILE_IDS, resolveQualityProfile} from './export-quality-policy.mjs';
import {importExportData} from './import-export-data.mjs';
import {
  requirePublicationAcceptanceContext,
  requirePublicationExporterAcceptance,
  loadCurrentPublicationExporterAcceptance,
  verifyAcceptedRawPublicationExport,
  verifyPublicationExporterBuildFile,
} from './publication-exporter-acceptance.mjs';
import {EXPORTER_RELEASE_DEFINITIONS} from './package-exporter-releases.mjs';
import {
  DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  DEFAULT_EXPORTER_WORKSPACE_ROOT,
} from './exporter-release-acceptance.mjs';
import {
  requirePublishablePackIdentity,
  slugForPackName,
} from './pack-identity.mjs';
import {
  fetchPublishingCatalog,
  preflightIngestionEndpoints,
  resolveChannelExpectation,
} from './publication-upload-preflight.mjs';
import {
  readCoreDatasetIngestToken,
  uploadCoreDatasetPublication,
} from './upload-core-dataset-publication.mjs';
import {
  readPreviewIngestToken,
  uploadRecipePreviewSidecar,
} from './upload-recipe-preview-sidecar.mjs';
import {verifyPublicCoreDatasetPublication} from './verify-core-dataset-publication-remote.mjs';
import {verifyRemoteRecipePreviewSidecar} from './verify-recipe-preview-sidecar-remote.mjs';
import {
  GTNH_STRUCTURED_DATA_ONLY_POLICY_ID,
  GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS,
  hasExactGtnhStructuredDataOnlyVisualAssets,
} from './visual-assets-rights-policy.mjs';

export const PUBLICATION_PLAN_FORMAT = 'mrt-modpack-publication-plan-v4';
export const MAX_PUBLICATION_PLAN_BYTES = 64 * 1024;
export const MAX_ACTIVATION_BENCHMARK_REPORT_BYTES = 4 * 1024 * 1024;
export const DEFAULT_APP_ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';
export const GTNH_STRUCTURED_DATA_ONLY_POLICY = GTNH_STRUCTURED_DATA_ONLY_POLICY_ID;
export const GTNH_VISUAL_ASSETS_POLICY = GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS;
const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const CONTENT_ID_PATTERN = /^[a-f0-9]{64}$/;
const VERSION_ISOLATED_PROFILE_SLUGS = Object.freeze({
  'gtnh-1.7.10': 'gt-new-horizons',
  'multiblock-madness-1.12.2': 'multiblock-madness',
  'multiblock-madness-2-1.18.2': 'multiblock-madness-2',
});
const REQUIRED_ARTIFACT_PATHS = Object.freeze({
  packedExport: 'packed-export',
  corePublication: 'core-publication/publication.json',
  previewSidecar: 'preview-sidecar',
});

function usage() {
  return [
    'Prepare a new immutable publication:',
    '  npm run publish:modpack -- prepare --source <jei-exports> --workspace <new-directory>',
    `    --profile <${EXPORT_QUALITY_PROFILE_IDS.join('|')}> --release <exporter-release-id>`,
    '    [--slug <stable-pack-slug>] [--staging-mode <clone|copy>]',
    '',
    'Upload, verify, and activate an already prepared publication:',
    '  npm run publish:modpack -- upload --workspace <prepared-directory>',
    '    --channel-action <create|update> --default <true|false>',
    '    [--benchmark-report <cold-browser-report.json> --dist <production-dist>]',
    '    [--app-origin <https-origin>] [--core-token-file <mode-0600-file>]',
    '    [--preview-token-file <mode-0600-file>] [--concurrency <1-32>]',
    '',
    'The upload phase also accepts CORE_DATASET_UPLOAD_TOKEN and PREVIEW_UPLOAD_TOKEN from the',
    'operator environment. Tokens are never accepted as command-line values or URL parameters.',
  ].join('\n');
}

function valueAfter(argv, index, flag) {
  const value = argv[index + 1];
  if (value === undefined || value.startsWith('--')) throw new Error(`${flag} requires a value.`);
  return value;
}

function parseBoolean(value, flag) {
  if (value === 'true') return true;
  if (value === 'false') return false;
  throw new Error(`${flag} must be exactly true or false.`);
}

function parseInteger(value, flag, minimum, maximum) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${flag} must be an integer in [${minimum}, ${maximum}].`);
  }
  return parsed;
}

function duplicate(options, name, flag) {
  if (options[name] !== undefined) throw new Error(`${flag} was provided more than once.`);
}

export function parsePublishModpackArguments(argv) {
  if (argv.length === 0 || argv[0] === '--help' || argv[0] === '-h') return {command: 'help'};
  const [command, ...rest] = argv;
  if (command !== 'prepare' && command !== 'upload') {
    throw new Error(`The first argument must be prepare or upload.\n${usage()}`);
  }
  const options = {command};
  const allowed = command === 'prepare'
    ? new Map([
        ['--source', 'source'],
        ['--workspace', 'workspace'],
        ['--profile', 'profile'],
        ['--release', 'releaseId'],
        ['--slug', 'slug'],
        ['--staging-mode', 'stagingMode'],
        ['--concurrency', 'concurrency'],
      ])
    : new Map([
        ['--workspace', 'workspace'],
        ['--app-origin', 'appOrigin'],
        ['--core-token-file', 'coreTokenFile'],
        ['--preview-token-file', 'previewTokenFile'],
        ['--channel-action', 'channelAction'],
        ['--default', 'isDefault'],
        ['--benchmark-report', 'benchmarkReport'],
        ['--dist', 'dist'],
        ['--concurrency', 'concurrency'],
      ]);
  for (let index = 0; index < rest.length; index += 1) {
    const flag = rest[index];
    if (flag === '--help' || flag === '-h') return {command: 'help'};
    const name = allowed.get(flag);
    if (!name) throw new Error(`Unsupported ${command} argument: ${flag}.`);
    duplicate(options, name, flag);
    const value = valueAfter(rest, index, flag);
    options[name] = name === 'isDefault'
      ? parseBoolean(value, flag)
      : name === 'concurrency'
        ? parseInteger(value, flag, 1, 32)
        : value;
    index += 1;
  }
  const required = command === 'prepare'
    ? ['source', 'workspace', 'profile', 'releaseId']
    : ['workspace', 'channelAction', 'isDefault'];
  const missing = required.filter(name => options[name] === undefined);
  if (missing.length > 0) {
    throw new Error(`Missing ${command} argument(s): ${missing.join(', ')}.\n${usage()}`);
  }
  if (options.stagingMode !== undefined && !['clone', 'copy'].includes(options.stagingMode)) {
    throw new Error('--staging-mode must be exactly clone or copy.');
  }
  if (options.channelAction !== undefined && !['create', 'update'].includes(options.channelAction)) {
    throw new Error('--channel-action must be exactly create or update.');
  }
  if ((options.benchmarkReport === undefined) !== (options.dist === undefined)) {
    throw new Error('--benchmark-report and --dist must be supplied together.');
  }
  if (options.slug !== undefined && (!SLUG_PATTERN.test(options.slug) || options.slug.length > 80)) {
    throw new Error('--slug must be at most 80 lowercase ASCII letters/digits separated by hyphens.');
  }
  if (options.profile !== undefined) options.profile = resolveQualityProfile(options.profile);
  const requiredSlug = VERSION_ISOLATED_PROFILE_SLUGS[options.profile];
  if (options.slug !== undefined && requiredSlug !== undefined && options.slug !== requiredSlug) {
    throw new Error(`Profile ${options.profile} requires isolated channel slug ${requiredSlug}.`);
  }
  return options;
}

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

export function requireFullPublicationManifest(value, label = 'Publication manifest') {
  if (isRecord(value) && Object.prototype.hasOwnProperty.call(value, 'qualitySample')) {
    throw new Error(
      `${label} contains manifest.qualitySample and is a diagnostic mini export. ` +
        'Production publication requires a full exporter result without qualitySample; ' +
        'run the full export request and publish its new output directory.',
    );
  }
  return value;
}

function exactKeys(value, expected) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function profilePublicationPolicy(profile) {
  return profile === 'gtnh-1.7.10' ? GTNH_STRUCTURED_DATA_ONLY_POLICY : undefined;
}

export function requirePublicationPolicyBinding(profile, value, label = 'publication policy') {
  const expected = profilePublicationPolicy(profile);
  if (expected === undefined) {
    if (value !== undefined) {
      throw new Error(`${label} is reserved for gtnh-1.7.10 and must be absent for ${profile}.`);
    }
    return undefined;
  }
  if (value !== expected) {
    throw new Error(`${label} must be exactly ${expected} for ${profile}.`);
  }
  return expected;
}

function requirePackedPublicationPolicy(manifest, profile, label) {
  const policy = requirePublicationPolicyBinding(profile, manifest?.publicationPolicy, `${label}.publicationPolicy`);
  if (policy === undefined) {
    if (manifest?.web?.visualAssets !== undefined) {
      throw new Error(`${label}.web.visualAssets is reserved for gtnh-1.7.10 publications.`);
    }
    return undefined;
  }
  if (!hasExactGtnhStructuredDataOnlyVisualAssets(manifest?.web?.visualAssets)) {
    throw new Error(`${label}.web.visualAssets violates the exact GTNH structured-data-only contract.`);
  }
  return policy;
}

export function requirePublicationPlan(value) {
  const requiredKeys = [
    'createdAt',
    'exporterAcceptance',
    'format',
    'minecraftVersion',
    'pack',
    'paths',
    'profile',
    'publicationId',
    'previewAssetSetId',
    'slug',
  ];
  const allowedKeys = [...requiredKeys, 'publicationPolicy'];
  if (
    !isRecord(value) ||
    requiredKeys.some(key => !Object.prototype.hasOwnProperty.call(value, key)) ||
    Object.keys(value).some(key => !allowedKeys.includes(key))
  ) {
    throw new Error('publication-plan.json violates the exact top-level contract.');
  }
  if (value.format !== PUBLICATION_PLAN_FORMAT) {
    throw new Error(`publication-plan.json format must be ${PUBLICATION_PLAN_FORMAT}.`);
  }
  if (
    typeof value.createdAt !== 'string' ||
    !Number.isFinite(Date.parse(value.createdAt)) ||
    typeof value.minecraftVersion !== 'string' ||
    value.minecraftVersion.length === 0 ||
    !SLUG_PATTERN.test(value.slug) ||
    value.slug.length > 80 ||
    !CONTENT_ID_PATTERN.test(value.publicationId) ||
    !CONTENT_ID_PATTERN.test(value.previewAssetSetId)
  ) {
    throw new Error('publication-plan.json contains invalid identity or publication fields.');
  }
  const profile = resolveQualityProfile(value.profile);
  const publicationPolicy = requirePublicationPolicyBinding(
    profile,
    value.publicationPolicy,
    'publication-plan.json publicationPolicy',
  );
  const pack = requirePublishablePackIdentity(value.pack, 'publication-plan.json pack');
  const exporterAcceptance = requirePublicationExporterAcceptance(value.exporterAcceptance);
  requirePublicationAcceptanceContext(exporterAcceptance, {
    profile,
    minecraftVersion: value.minecraftVersion,
    pack,
  });
  const requiredSlug = VERSION_ISOLATED_PROFILE_SLUGS[profile];
  if (requiredSlug !== undefined && value.slug !== requiredSlug) {
    throw new Error(
      `publication-plan.json profile ${profile} requires isolated channel slug ${requiredSlug}.`,
    );
  }
  if (!exactKeys(value.paths, ['corePublication', 'packedExport', 'previewSidecar'])) {
    throw new Error('publication-plan.json paths must contain exactly the three prepared artifacts.');
  }
  for (const [name, requiredPath] of Object.entries(REQUIRED_ARTIFACT_PATHS)) {
    if (value.paths[name] !== requiredPath) {
      throw new Error(
        `publication-plan.json paths.${name} must be the fixed canonical path ${requiredPath}.`,
      );
    }
  }
  const paths = REQUIRED_ARTIFACT_PATHS;
  return Object.freeze({
    ...value,
    exporterAcceptance,
    pack,
    profile,
    ...(publicationPolicy === undefined ? {} : {publicationPolicy}),
    paths,
  });
}

async function requireMissingWorkspace(path) {
  try {
    const info = await lstat(path);
    throw new Error(
      `Preparation workspace already exists as ${info.isDirectory() ? 'a directory' : 'a filesystem entry'}: ${path}. Use a new workspace for each immutable publication attempt.`,
    );
  } catch (error) {
    if (error?.code === 'ENOENT') return;
    throw error;
  }
}

async function atomicWriteJson(path, value) {
  const temporary = join(dirname(path), `.${basename(path)}.${process.pid}.tmp`);
  await writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, {encoding: 'utf8', flag: 'wx'});
  await rename(temporary, path);
}

function planPaths(workspace) {
  return {
    packedExport: join(workspace, 'packed-export'),
    coreBundle: join(workspace, 'core-publication'),
    corePublication: join(workspace, 'core-publication', 'publication.json'),
    previewSidecar: join(workspace, 'preview-sidecar'),
    plan: join(workspace, 'publication-plan.json'),
  };
}

const DEFAULT_PREPARE_DEPENDENCIES = Object.freeze({
  importExportData,
  loadCurrentPublicationExporterAcceptance,
  verifyAcceptedRawPublicationExport,
  verifyPublicationExporterBuildFile,
});

function requirePrepareDependencies(overrides) {
  const dependencies = {...DEFAULT_PREPARE_DEPENDENCIES, ...overrides};
  for (const [name, dependency] of Object.entries(dependencies)) {
    if (typeof dependency !== 'function') {
      throw new Error(`Preparation dependency ${name} must be a function.`);
    }
  }
  return dependencies;
}

export async function prepareModpackPublication({
  source,
  workspace,
  profile,
  releaseId,
  slug,
  stagingMode = null,
  concurrency,
  logger = console,
  releaseDefinitions = EXPORTER_RELEASE_DEFINITIONS,
  exporterWorkspaceRoot = DEFAULT_EXPORTER_WORKSPACE_ROOT,
  acceptanceRoot = DEFAULT_EXPORTER_ACCEPTANCE_ROOT,
  dependencies: dependencyOverrides = {},
}) {
  const dependencies = requirePrepareDependencies(dependencyOverrides);
  const requestedSourceRoot = resolve(source);
  const requestedSourceInfo = await lstat(requestedSourceRoot);
  if (requestedSourceInfo.isSymbolicLink() || !requestedSourceInfo.isDirectory()) {
    throw new Error(
      `Raw publication source must be a no-follow plain directory: ${requestedSourceRoot}.`,
    );
  }
  const sourceRoot = await realpath(requestedSourceRoot);
  const workspaceRoot = resolve(workspace);
  await requireMissingWorkspace(workspaceRoot);
  const rawManifest = requireFullPublicationManifest(
    await readJsonDocument(join(sourceRoot, 'manifest.json'), 'Raw manifest.json'),
    'Raw manifest.json',
  );
  const pack = requirePublishablePackIdentity(rawManifest?.pack);
  if (typeof rawManifest.minecraft !== 'string' || rawManifest.minecraft.length === 0) {
    throw new Error('Raw manifest.minecraft must be a non-empty string.');
  }
  const requiredSlug = VERSION_ISOLATED_PROFILE_SLUGS[profile];
  if (slug !== undefined && requiredSlug !== undefined && slug !== requiredSlug) {
    throw new Error(
      `Profile ${profile} requires isolated channel slug ${requiredSlug}; received ${slug}.`,
    );
  }
  const channelSlug = requiredSlug ?? slug ?? slugForPackName(pack.name);
  const publicationPolicy = profilePublicationPolicy(profile);
  if (publicationPolicy !== undefined) {
    logger.info(
      `[publish-modpack] Enforcing ${publicationPolicy}: the public GTNH dataset will contain ` +
        'structured recipe data and generated UI placeholders, with exported visual assets omitted.',
    );
  }
  const initialAcceptance = await dependencies.loadCurrentPublicationExporterAcceptance({
    releaseId,
    profile,
    minecraftVersion: rawManifest.minecraft,
    pack,
    definitions: releaseDefinitions,
    workspaceRoot: exporterWorkspaceRoot,
    acceptanceRoot,
    logger,
  });
  await dependencies.verifyPublicationExporterBuildFile({
    exportRoot: sourceRoot,
    binding: initialAcceptance.binding,
    label: 'Raw source export',
  });
  await mkdir(workspaceRoot);
  const paths = planPaths(workspaceRoot);
  try {
    logger.info(
      `[publish-modpack] Preparing ${pack.name} ${pack.version} as channel ${channelSlug}.`,
    );
    await dependencies.importExportData({
      source: sourceRoot,
      destination: paths.packedExport,
      profile,
      omitRecipeImages: true,
      ...(publicationPolicy === undefined ? {} : {publicationPolicy}),
      stagingMode,
      verifyStagedSource: stagedRoot => dependencies.verifyAcceptedRawPublicationExport({
        exportRoot: stagedRoot,
        binding: initialAcceptance.binding,
        logger,
      }),
    });
    const initiallyPackedManifest = await readJsonDocument(
      join(paths.packedExport, 'manifest.json'),
      'Packed manifest.json',
    );
    requirePackedPublicationPolicy(
      initiallyPackedManifest,
      profile,
      'Packed manifest.json',
    );
    const preview = await buildRecipePreviewSidecar({
      source: sourceRoot,
      datasetManifest: join(paths.packedExport, 'manifest.json'),
      output: paths.previewSidecar,
      profile,
      ...(publicationPolicy === undefined ? {} : {publicationPolicy}),
      ...(concurrency === undefined ? {} : {concurrency}),
      logger,
    });
    const core = await buildCoreDatasetPublication({
      exportRoot: paths.packedExport,
      output: paths.coreBundle,
      ...(publicationPolicy === undefined ? {} : {publicationPolicy}),
      ...(concurrency === undefined ? {} : {concurrency}),
      logger,
    });
    if (preview.datasetPublicationId !== core.publicationId) {
      throw new Error(
        `Prepared preview/core identities disagree: ${preview.datasetPublicationId} vs ${core.publicationId}.`,
      );
    }
    const packedManifest = await readJsonDocument(
      join(paths.packedExport, 'manifest.json'),
      'Packed manifest.json',
    );
    requirePackedPublicationPolicy(packedManifest, profile, 'Packed manifest.json');
    const packedPack = requirePublishablePackIdentity(packedManifest?.pack, 'Packed manifest.pack');
    if (JSON.stringify(packedPack) !== JSON.stringify(pack)) {
      throw new Error('Pack identity changed while the export was optimized and packed.');
    }
    await dependencies.verifyPublicationExporterBuildFile({
      exportRoot: paths.packedExport,
      binding: initialAcceptance.binding,
      label: 'Packed export',
    });
    const committedAcceptance = await dependencies.loadCurrentPublicationExporterAcceptance({
      releaseId,
      profile,
      minecraftVersion: rawManifest.minecraft,
      pack,
      definitions: releaseDefinitions,
      workspaceRoot: exporterWorkspaceRoot,
      acceptanceRoot,
      expectedBinding: initialAcceptance.binding,
      logger,
    });
    const plan = requirePublicationPlan({
      format: PUBLICATION_PLAN_FORMAT,
      createdAt: new Date().toISOString(),
      profile,
      ...(publicationPolicy === undefined ? {} : {publicationPolicy}),
      exporterAcceptance: committedAcceptance.binding,
      pack,
      minecraftVersion: rawManifest.minecraft,
      slug: channelSlug,
      publicationId: core.publicationId,
      previewAssetSetId: preview.assetSetId,
      paths: {
        packedExport: 'packed-export',
        corePublication: 'core-publication/publication.json',
        previewSidecar: 'preview-sidecar',
      },
    });
    await atomicWriteJson(paths.plan, plan);
    logger.info(`[publish-modpack] Prepared and committed ${paths.plan}.`);
    logger.info(
      '[publish-modpack] Before upload, configure a temporary preview-ingestion session with ' +
        `PREVIEW_UPLOAD_ENABLED=true and PREVIEW_UPLOAD_ASSET_SET_ID=${plan.previewAssetSetId}.`,
    );
    return plan;
  } catch (error) {
    logger.error(
      `[publish-modpack] Preparation failed. The workspace is retained for diagnostics at ${workspaceRoot}.`,
      error,
    );
    throw error;
  }
}

function requireHttpsOrigin(value) {
  let url;
  try {
    url = new URL(value);
  } catch (error) {
    throw new Error(`--app-origin must be an absolute HTTPS origin: ${error.message}`, {cause: error});
  }
  if (
    url.protocol !== 'https:' ||
    url.username ||
    url.password ||
    url.pathname !== '/' ||
    url.search ||
    url.hash
  ) {
    throw new Error('--app-origin must be an absolute HTTPS origin without a path or credentials.');
  }
  return url.origin;
}

function isInsideOrEqual(root, path) {
  const key = relative(root, path);
  return key === '' || (!isAbsolute(key) && key !== '..' && !key.startsWith(`..${sep}`));
}

function sameFilesystemEntry(left, right) {
  return left.dev === right.dev && left.ino === right.ino;
}

async function inspectNoFollow(path, label) {
  try {
    return await lstat(path);
  } catch (error) {
    throw new Error(`${label} could not be inspected at ${path}: ${error.message}`, {cause: error});
  }
}

async function requirePlainDirectory(path, label) {
  const info = await inspectNoFollow(path, label);
  if (info.isSymbolicLink() || !info.isDirectory()) {
    throw new Error(`${label} must be a no-follow plain directory: ${path}.`);
  }
  return info;
}

async function requirePlainRegularFile(path, label) {
  const info = await inspectNoFollow(path, label);
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`${label} must be a no-follow regular file: ${path}.`);
  }
  if (info.nlink !== 1) {
    throw new Error(`${label} must not be hard-linked: ${path}.`);
  }
  return info;
}

async function readBoundedNoFollowFile(path, label, maximumBytes) {
  const before = await requirePlainRegularFile(path, label);
  if (!Number.isSafeInteger(before.size) || before.size <= 0 || before.size > maximumBytes) {
    throw new Error(
      `${label} has invalid byte length ${before.size}; maximum is ${maximumBytes}.`,
    );
  }
  if (!Number.isSafeInteger(constants.O_NOFOLLOW)) {
    throw new Error(
      `${label} cannot be read securely because this Node.js runtime lacks O_NOFOLLOW support.`,
    );
  }
  let handle;
  try {
    handle = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  } catch (error) {
    throw new Error(`${label} could not be opened without following links: ${error.message}`, {
      cause: error,
    });
  }
  try {
    const opened = await handle.stat();
    if (
      !opened.isFile() ||
      opened.nlink !== 1 ||
      !sameFilesystemEntry(before, opened) ||
      opened.size !== before.size
    ) {
      throw new Error(`${label} changed between its no-follow inspection and open.`);
    }
    const bytes = Buffer.alloc(opened.size);
    let offset = 0;
    while (offset < bytes.length) {
      const {bytesRead} = await handle.read(bytes, offset, bytes.length - offset, offset);
      if (bytesRead === 0) throw new Error(`${label} was truncated while being read.`);
      offset += bytesRead;
    }
    const probe = Buffer.allocUnsafe(1);
    const trailing = await handle.read(probe, 0, 1, bytes.length);
    const afterOpen = await handle.stat();
    if (
      trailing.bytesRead !== 0 ||
      !sameFilesystemEntry(opened, afterOpen) ||
      afterOpen.size !== opened.size
    ) {
      throw new Error(`${label} changed while being read.`);
    }
    return bytes;
  } finally {
    await handle.close();
    const afterClose = await requirePlainRegularFile(path, label);
    if (!sameFilesystemEntry(before, afterClose) || before.size !== afterClose.size) {
      throw new Error(`${label} changed before its secure read completed.`);
    }
  }
}

function expectedBenchmarkDataset(plan) {
  return {
    slug: plan.slug,
    displayName: plan.pack.name,
    minecraftVersion: plan.minecraftVersion,
    packVersion: plan.pack.version,
    publicationId: plan.publicationId,
    previewAssetSetId: plan.previewAssetSetId,
    // The benchmark uses an isolated one-entry catalog. This is deliberately independent of the
    // operator's eventual mutable channel-default decision.
    isDefault: true,
  };
}

async function loadActivationBenchmarkReceipt({
  benchmarkReport,
  dist,
  plan,
  dependencies,
  logger,
}) {
  const hasReport = benchmarkReport !== undefined;
  const hasDist = dist !== undefined;
  if (hasReport !== hasDist) {
    throw new Error('--benchmark-report and --dist must be supplied together.');
  }
  if (!hasReport) {
    if (plan.profile === 'gtnh-1.7.10') {
      throw new Error(
        'GTNH activation requires --benchmark-report and --dist before catalog or credential access.',
      );
    }
    return null;
  }

  if (
    typeof benchmarkReport !== 'string' ||
    benchmarkReport.length === 0 ||
    benchmarkReport.includes('\0') ||
    typeof dist !== 'string' ||
    dist.length === 0 ||
    dist.includes('\0')
  ) {
    throw new Error('--benchmark-report and --dist must be non-empty filesystem paths.');
  }

  const reportPath = resolve(benchmarkReport);
  const distRoot = resolve(dist);
  const reportBytes = await readBoundedNoFollowFile(
    reportPath,
    'Cold benchmark activation report',
    MAX_ACTIVATION_BENCHMARK_REPORT_BYTES,
  );
  let reportValue;
  try {
    reportValue = JSON.parse(reportBytes.toString('utf8'));
  } catch (error) {
    throw new Error(`Cold benchmark activation report contains invalid JSON: ${error.message}`, {
      cause: error,
    });
  }
  const [build, sourceSha256] = await Promise.all([
    dependencies.digestBuildTree(distRoot),
    dependencies.digestColdDatasetBenchmarkSource(),
  ]);
  const report = requireCurrentStorageEligibleBenchmarkReport(reportValue, {
    expectedBuild: build,
    expectedDataset: expectedBenchmarkDataset(plan),
    expectedSourceSha256: sourceSha256,
  });
  const reportSha256 = createHash('sha256').update(reportBytes).digest('hex');
  logger.info(
    `[publish-modpack] Revalidated activation benchmark report sha256=${reportSha256}, ` +
      `sourceSha256=${sourceSha256}, buildSha256=${build.sha256} before catalog or credential access.`,
  );
  return Object.freeze({
    build,
    buildSha256: build.sha256,
    distRoot,
    report,
    reportSha256,
    sourceSha256,
  });
}

async function requireCanonicalTreeRoot(workspaceRoot, path, label) {
  if (!isInsideOrEqual(workspaceRoot, path)) {
    throw new Error(`${label} resolves outside the prepared workspace: ${path}.`);
  }
  await requirePlainDirectory(path, label);
  const canonicalPath = await realpath(path);
  if (!isInsideOrEqual(workspaceRoot, canonicalPath) || relative(path, canonicalPath) !== '') {
    throw new Error(`${label} contains a symbolic-linked intermediate path: ${path}.`);
  }
}

async function inspectPlainTree(workspaceRoot, treeRoot, label) {
  await requireCanonicalTreeRoot(workspaceRoot, treeRoot, label);
  const pending = [treeRoot];
  let directories = 0;
  let files = 0;
  while (pending.length > 0) {
    const directory = pending.pop();
    const before = await requirePlainDirectory(directory, `${label} directory`);
    let entries;
    try {
      entries = await readdir(directory, {withFileTypes: true});
    } catch (error) {
      throw new Error(`${label} could not enumerate ${directory}: ${error.message}`, {cause: error});
    }
    directories += 1;
    for (const entry of entries) {
      const path = join(directory, entry.name);
      if (!isInsideOrEqual(workspaceRoot, path)) {
        throw new Error(`${label} entry resolves outside the prepared workspace: ${path}.`);
      }
      const info = await inspectNoFollow(path, `${label} entry`);
      if (info.isSymbolicLink()) {
        throw new Error(`${label} must not contain symbolic links: ${path}.`);
      }
      if (info.isDirectory()) {
        pending.push(path);
      } else if (info.isFile()) {
        if (info.nlink !== 1) {
          throw new Error(`${label} must not contain hard-linked files: ${path}.`);
        }
        files += 1;
      } else {
        throw new Error(`${label} must contain only regular files and directories: ${path}.`);
      }
    }
    const after = await requirePlainDirectory(directory, `${label} directory`);
    if (!sameFilesystemEntry(before, after)) {
      throw new Error(`${label} directory changed while it was enumerated: ${directory}.`);
    }
  }
  return {directories, files};
}

async function preflightPreparedWorkspace(workspaceRoot) {
  const packedExport = join(workspaceRoot, REQUIRED_ARTIFACT_PATHS.packedExport);
  const corePublication = join(workspaceRoot, REQUIRED_ARTIFACT_PATHS.corePublication);
  const coreBundle = dirname(corePublication);
  const previewSidecar = join(workspaceRoot, REQUIRED_ARTIFACT_PATHS.previewSidecar);
  const packedCounts = await inspectPlainTree(
    workspaceRoot,
    packedExport,
    'Prepared packed export',
  );
  const coreCounts = await inspectPlainTree(
    workspaceRoot,
    coreBundle,
    'Prepared core publication',
  );
  await requirePlainRegularFile(corePublication, 'Prepared core publication.json');
  const previewCounts = await inspectPlainTree(
    workspaceRoot,
    previewSidecar,
    'Prepared preview sidecar',
  );
  return {
    paths: {packedExport, corePublication, previewSidecar},
    counts: {
      directories: packedCounts.directories + coreCounts.directories + previewCounts.directories,
      files: packedCounts.files + coreCounts.files + previewCounts.files,
    },
  };
}

export async function loadPreparedPlan(workspace) {
  const requestedRoot = resolve(workspace);
  await requirePlainDirectory(requestedRoot, 'Prepared publication workspace');
  const workspaceRoot = await realpath(requestedRoot);
  const planPath = join(workspaceRoot, 'publication-plan.json');
  const bytes = await readBoundedNoFollowFile(
    planPath,
    'Prepared publication plan',
    MAX_PUBLICATION_PLAN_BYTES,
  );
  let value;
  try {
    value = JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`Prepared publication plan contains invalid JSON: ${error.message}`, {
      cause: error,
    });
  }
  const plan = requirePublicationPlan(value);
  const preflight = await preflightPreparedWorkspace(workspaceRoot);
  return {workspaceRoot, plan, ...preflight};
}

const DEFAULT_UPLOAD_DEPENDENCIES = Object.freeze({
  digestBuildTree,
  digestColdDatasetBenchmarkSource,
  fetchPublishingCatalog,
  readCoreDatasetIngestToken,
  readPreviewIngestToken,
  preflightIngestionEndpoints,
  uploadCoreDatasetPublication,
  uploadRecipePreviewSidecar,
  verifyPublicCoreDatasetPublication,
  verifyRemoteRecipePreviewSidecar,
  administerDatasetChannel,
  async verifyPreparedPublicationAcceptance({plan, packedExport, logger}) {
    const releaseId = plan.exporterAcceptance.receipt.release.id;
    await loadCurrentPublicationExporterAcceptance({
      releaseId,
      profile: plan.profile,
      minecraftVersion: plan.minecraftVersion,
      pack: plan.pack,
      definitions: EXPORTER_RELEASE_DEFINITIONS,
      expectedBinding: plan.exporterAcceptance,
      logger,
    });
    await verifyPublicationExporterBuildFile({
      exportRoot: packedExport,
      binding: plan.exporterAcceptance,
      label: 'Prepared packed export',
    });
  },
});

function requireUploadDependencies(overrides) {
  const dependencies = {...DEFAULT_UPLOAD_DEPENDENCIES, ...overrides};
  for (const [name, dependency] of Object.entries(dependencies)) {
    if (typeof dependency !== 'function') {
      throw new Error(`Publishing dependency ${name} must be a function.`);
    }
  }
  return dependencies;
}

export async function uploadPreparedModpackPublication({
  workspace,
  channelAction,
  isDefault,
  appOrigin = DEFAULT_APP_ORIGIN,
  benchmarkReport,
  coreTokenFile,
  dist,
  previewTokenFile,
  concurrency,
  logger = console,
  dependencies: dependencyOverrides = {},
}) {
  const origin = requireHttpsOrigin(appOrigin);
  if (channelAction !== 'create' && channelAction !== 'update') {
    throw new Error('channelAction must be exactly create or update.');
  }
  if (typeof isDefault !== 'boolean') {
    throw new Error('isDefault must be an explicit boolean operator decision.');
  }
  const dependencies = requireUploadDependencies(dependencyOverrides);
  logger.info('[publish-modpack] Preflighting the prepared workspace before loading credentials.');
  const {plan, paths, counts} = await loadPreparedPlan(workspace);
  const {packedExport, corePublication, previewSidecar} = paths;
  logger.info(
    `[publish-modpack] Prepared workspace passed plain-tree preflight: ` +
      `${counts.files} files in ${counts.directories} directories.`,
  );
  const packedManifest = await readJsonDocument(join(packedExport, 'manifest.json'), 'Packed manifest.json');
  requireFullPublicationManifest(packedManifest, 'Packed manifest.json');
  const packedPublicationPolicy = requirePackedPublicationPolicy(
    packedManifest,
    plan.profile,
    'Packed manifest.json',
  );
  if (packedPublicationPolicy !== plan.publicationPolicy) {
    throw new Error('Prepared publication plan no longer matches its packed publication policy.');
  }
  const packedPack = requirePublishablePackIdentity(packedManifest?.pack, 'Packed manifest.pack');
  if (
    packedManifest?.publicationId !== plan.publicationId ||
    packedManifest?.minecraft !== plan.minecraftVersion ||
    JSON.stringify(packedPack) !== JSON.stringify(plan.pack)
  ) {
    throw new Error('Prepared publication plan no longer matches its packed manifest.');
  }
  await dependencies.verifyPreparedPublicationAcceptance({
    plan,
    packedExport,
    logger,
  });
  logger.info(
    `[publish-modpack] Revalidated exporter receipt sha256=${plan.exporterAcceptance.receiptSha256} ` +
      `for release ${plan.exporterAcceptance.receipt.release.id} before catalog or credential access.`,
  );
  if (packedPublicationPolicy !== undefined) {
    logger.info(
      `[publish-modpack] Revalidated ${packedPublicationPolicy} before catalog access or ` +
        'credential loading; GTNH visual-asset publication remains disabled.',
    );
  }

  const benchmarkReceipt = await loadActivationBenchmarkReceipt({
    benchmarkReport,
    dist,
    plan,
    dependencies,
    logger,
  });

  const datasets = await dependencies.fetchPublishingCatalog({appOrigin: origin});
  const {expectedPreviousPublicationId} = resolveChannelExpectation({
    datasets,
    action: channelAction,
    plan,
  });
  logger.info(
    `[publish-modpack] Operator authorized channel ${channelAction} for ${plan.slug}; ` +
      `CAS predecessor=${expectedPreviousPublicationId ?? 'absent'}.`,
  );
  const [coreToken, previewToken] = await Promise.all([
    dependencies.readCoreDatasetIngestToken({tokenFile: coreTokenFile}),
    dependencies.readPreviewIngestToken({tokenFile: previewTokenFile}),
  ]);
  await dependencies.preflightIngestionEndpoints({
    appOrigin: origin,
    publicationId: plan.publicationId,
    previewAssetSetId: plan.previewAssetSetId,
    coreToken,
    previewToken,
    logger,
  });
  const bounded = concurrency === undefined ? {} : {concurrency};
  await dependencies.uploadCoreDatasetPublication({
    exportRoot: packedExport,
    publication: corePublication,
    ingestBaseUrl: `${origin}/api/admin/core-datasets`,
    token: coreToken,
    ...bounded,
    logger,
  });
  await dependencies.uploadRecipePreviewSidecar({
    local: previewSidecar,
    ingestBaseUrl: `${origin}/api/admin/preview-assets`,
    token: previewToken,
    ...bounded,
    logger,
  });
  await dependencies.verifyPublicCoreDatasetPublication({
    exportRoot: packedExport,
    publication: corePublication,
    baseUrl: `${origin}/dataset/publications`,
    ...bounded,
    logger,
  });
  await dependencies.verifyRemoteRecipePreviewSidecar({
    local: previewSidecar,
    baseUrl: `${origin}/dataset/preview-sets`,
    mode: 'committed',
    ...bounded,
    logger,
  });
  if (benchmarkReceipt !== null) {
    const preActivationBuild = await dependencies.digestBuildTree(benchmarkReceipt.distRoot);
    try {
      assertSameBuildTree(benchmarkReceipt.build, preActivationBuild);
    } catch (error) {
      throw new Error(
        'Production dist changed after benchmark receipt validation and before channel activation.',
        {cause: error},
      );
    }
    logger.info(
      `[publish-modpack] Revalidated production build sha256=${benchmarkReceipt.buildSha256} ` +
        'immediately before channel activation.',
    );
  }
  await dependencies.administerDatasetChannel({
    operation: 'activate',
    slug: plan.slug,
    displayName: plan.pack.name,
    minecraftVersion: plan.minecraftVersion,
    packVersion: plan.pack.version,
    publicationId: plan.publicationId,
    previewAssetSetId: plan.previewAssetSetId,
    isDefault,
    expectedPreviousPublicationId,
    adminBaseUrl: `${origin}/api/admin/dataset-channels`,
    token: coreToken,
    logger,
  });
  const shareUrl = `${origin}/?pack=${encodeURIComponent(plan.slug)}`;
  logger.info(`[publish-modpack] Publication is active and externally viewable at ${shareUrl}`);
  return {
    ...plan,
    channelAction,
    isDefault,
    shareUrl,
    ...(benchmarkReceipt === null
      ? {}
      : {
          benchmarkReportSha256: benchmarkReceipt.reportSha256,
          benchmarkSourceSha256: benchmarkReceipt.sourceSha256,
          buildSha256: benchmarkReceipt.buildSha256,
        }),
  };
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    const options = parsePublishModpackArguments(process.argv.slice(2));
    if (options.command === 'help') {
      console.log(usage());
    } else if (options.command === 'prepare') {
      await prepareModpackPublication(options);
    } else {
      await uploadPreparedModpackPublication(options);
    }
  } catch (error) {
    console.error(`[publish-modpack] ${error instanceof Error ? error.message : String(error)}`);
    process.exitCode = error?.mutationCommitted === true ? 2 : 1;
  }
}
