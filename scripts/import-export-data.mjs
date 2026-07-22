import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {
  cp,
  lstat,
  mkdir,
  realpath,
  rename,
  rm,
  rmdir,
  stat,
} from 'node:fs/promises';
import {basename, dirname, join, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  assertPlainDirectoryTree,
  isRecord,
  readJsonDocument,
} from './export-data-utils.mjs';
import {
  EXPORT_QUALITY_PROFILE_IDS,
  exportQualityIssues,
  MULTIBLOCK_MADNESS_112_PROFILE,
  qualityProfileRequirementsFor,
  resolveQualityProfile,
} from './export-quality-policy.mjs';
import {readArrayDocument} from './sharded-documents.mjs';
import {
  GTNH_STRUCTURED_DATA_ONLY_POLICY_ID,
  usesStructuredDataOnlyPublication,
} from './visual-assets-rights-policy.mjs';

const scriptRoot = dirname(fileURLToPath(import.meta.url));
const defaultDestination = join(process.cwd(), 'public', 'exports');

function usage() {
  return (
    'Usage: node scripts/import-export-data.mjs --source <raw-export-directory> ' +
    `--profile <${EXPORT_QUALITY_PROFILE_IDS.join('|')}> ` +
    '[--destination <directory>] [--dry-run] [--omit-recipe-images] ' +
    '[--staging-mode <clone|copy>]'
  );
}

function parseArguments(args) {
  let source = null;
  let destination = defaultDestination;
  let profile = null;
  let dryRun = false;
  let omitRecipeImages = false;
  let showHelp = false;
  let stagingMode = null;
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') {
      showHelp = true;
    } else if (argument === '--source') {
      source = args[++index];
      if (!source) throw new Error('--source requires a directory path.');
    } else if (argument === '--destination') {
      destination = args[++index];
      if (!destination) throw new Error('--destination requires a directory path.');
    } else if (argument === '--profile') {
      const value = args[++index];
      if (!value) throw new Error('--profile requires a profile name.');
      profile = resolveQualityProfile(value);
    } else if (argument === '--dry-run') {
      dryRun = true;
    } else if (argument === '--omit-recipe-images') {
      omitRecipeImages = true;
    } else if (argument === '--staging-mode') {
      stagingMode = args[++index];
      if (stagingMode !== 'clone' && stagingMode !== 'copy') {
        throw new Error('--staging-mode must be exactly clone or copy.');
      }
    } else {
      throw new Error(`Unknown import argument: ${argument}`);
    }
  }
  if (!showHelp && !source) throw new Error(`A raw export source is required.\n${usage()}`);
  if (!showHelp && !profile) {
    throw new Error(`An explicit export quality profile is required.\n${usage()}`);
  }
  return {
    source: source ? resolve(source) : null,
    destination: resolve(destination),
    profile,
    dryRun,
    omitRecipeImages,
    showHelp,
    stagingMode,
  };
}

async function pathKindNoFollow(path) {
  try {
    const info = await lstat(path);
    if (info.isSymbolicLink()) return 'symlink';
    if (info.isDirectory()) return 'directory';
    if (info.isFile()) return 'file';
    return 'other';
  } catch (error) {
    if (error?.code === 'ENOENT') return 'missing';
    throw error;
  }
}

function containsPath(parent, child) {
  const childRelative = relative(parent, child);
  return (
    childRelative !== '' &&
    childRelative !== '..' &&
    !childRelative.startsWith(`..${sep}`) &&
    !childRelative.startsWith(sep)
  );
}

function assertNoOverlap(left, leftLabel, right, rightLabel) {
  if (left === right || containsPath(left, right) || containsPath(right, left)) {
    throw new Error(
      `${leftLabel} and ${rightLabel} must be disjoint real paths; received ${left} and ${right}.`,
    );
  }
}

async function existingRealDirectory(path, label) {
  const kind = await pathKindNoFollow(path);
  if (kind !== 'directory') {
    throw new Error(`${label} is ${kind}, not a real directory: ${path}`);
  }
  return realpath(path);
}

async function potentialRealDirectory(path, label) {
  const kind = await pathKindNoFollow(path);
  if (kind === 'symlink') {
    throw new Error(`${label} must not be a symbolic link: ${path}`);
  }
  if (kind === 'directory') return realpath(path);
  if (kind !== 'missing') {
    throw new Error(`${label} is ${kind}, not a directory or unused path: ${path}`);
  }
  const parent = await existingRealDirectory(dirname(path), `${label} parent`);
  return join(parent, basename(path));
}

export function importWorkspaceRootForDestination(destination) {
  const destinationParent = dirname(resolve(destination));
  return join(dirname(destinationParent), '.import-work');
}

async function assertQualityMetadata(exportRoot, label, profile) {
  const manifest = await readJsonDocument(
    join(exportRoot, 'manifest.json'),
    `${label}/manifest.json`,
  );
  const failures = await readJsonDocument(
    join(exportRoot, 'failures.json'),
    `${label}/failures.json`,
  );
  const warnings =
    profile === MULTIBLOCK_MADNESS_112_PROFILE
      ? await readJsonDocument(
          join(exportRoot, 'warnings.json'),
          `${label}/warnings.json`,
        )
      : undefined;
  const issues = exportQualityIssues(
    {manifest, failures, warnings, semanticErrorRecipes: 0},
    profile,
  );
  if (issues.length > 0) {
    const requirements = qualityProfileRequirementsFor(profile);
    throw new Error(
      `${label} failed the ${requirements.label} metadata quality gate with ` +
        `${issues.length} issue(s):\n` +
        issues.map(issue => `- ${issue}`).join('\n'),
    );
  }
}

async function assertPackedRecipePreviewCompleteness(exportRoot, profile) {
  const requirements = qualityProfileRequirementsFor(profile);
  const manifest = await readJsonDocument(
    join(exportRoot, 'manifest.json'),
    'Packed export/manifest.json',
  );
  const expectedRecipes = manifest?.counts?.recipes;
  if (!Number.isSafeInteger(expectedRecipes) || expectedRecipes < 0) {
    throw new Error(
      `${requirements.label} packed manifest has an invalid counts.recipes value.`,
    );
  }

  const recipeImages = manifest?.web?.recipeImages;
  if (recipeImages?.mode === 'omitted') {
    const previews = recipeImages.inventory?.previews;
    const missing = recipeImages.inventory?.missing;
    if (
      previews !== expectedRecipes ||
      missing !== 0 ||
      recipeImages.references !== expectedRecipes ||
      recipeImages.files !== expectedRecipes
    ) {
      throw new Error(
        `${requirements.label} requires one recipe preview per recipe; packed omission ` +
          `accounting reports recipes/previews/missing/references/files=${expectedRecipes}/` +
          `${String(previews)}/${String(missing)}/${String(recipeImages.references)}/` +
          `${String(recipeImages.files)}.`,
      );
    }
    console.log(
      `[import-data] ${requirements.label} zero-missing preview gate accepted ` +
        `${previews} omission-inventoried recipe preview(s).`,
    );
    return;
  }
  if (recipeImages?.mode !== 'included') {
    throw new Error(
      `${requirements.label} packed manifest must declare web.recipeImages.mode as ` +
        '"included" or "omitted".',
    );
  }

  const categoriesDocument = await readJsonDocument(
    join(exportRoot, 'categories.json'),
    'Packed export/categories.json',
  );
  if (!isRecord(categoriesDocument) || !Array.isArray(categoriesDocument.categories)) {
    throw new Error(
      `${requirements.label} packed categories.json must contain a categories array.`,
    );
  }
  let recipes = 0;
  let missing = 0;
  let firstMissing = null;
  for (const [categoryIndex, category] of categoriesDocument.categories.entries()) {
    if (!isRecord(category) || typeof category.dir !== 'string') {
      throw new Error(`${requirements.label} packed category ${categoryIndex} is malformed.`);
    }
    const recipesPath = join(exportRoot, ...category.dir.split('/'), 'recipes.json');
    const recipesDocument = await readJsonDocument(
      recipesPath,
      `Packed export/${category.dir}/recipes.json`,
    );
    const categoryRecipes = (
      await readArrayDocument(
        exportRoot,
        recipesDocument,
        `Packed export/${category.dir}/recipes.json`,
      )
    ).value;
    recipes += categoryRecipes.length;
    for (const [recipeIndex, recipe] of categoryRecipes.entries()) {
      if (!isRecord(recipe) || recipe.img === undefined) {
        missing += 1;
        firstMissing ??= `${category.id ?? categoryIndex} recipe ${recipeIndex}`;
      }
    }
  }
  if (recipes !== expectedRecipes || missing !== 0) {
    throw new Error(
      `${requirements.label} requires one recipe preview per recipe; packed included-image ` +
        `scan reports recipes/expected/missing=${recipes}/${expectedRecipes}/${missing}` +
        `${firstMissing ? `; first missing: ${firstMissing}` : ''}.`,
    );
  }
  console.log(
    `[import-data] ${requirements.label} zero-missing preview gate accepted ` +
      `${recipes} included recipe preview(s).`,
  );
}

async function runStage(label, scriptName, args) {
  const scriptPath = join(scriptRoot, scriptName);
  await runCommand(label, process.execPath, [scriptPath, ...args]);
}

async function runCommand(label, executable, args) {
  console.log(`[import-data] Starting ${label}.`);
  await new Promise((resolveStage, rejectStage) => {
    const child = spawn(executable, args, {
      cwd: process.cwd(),
      stdio: 'inherit',
    });
    child.once('error', error => {
      rejectStage(new Error(`${label} could not start: ${error.message}`, {cause: error}));
    });
    child.once('exit', (code, signal) => {
      if (signal) {
        rejectStage(new Error(`${label} was terminated by signal ${signal}.`));
      } else if (code !== 0) {
        rejectStage(new Error(`${label} failed with exit code ${String(code)}.`));
      } else {
        resolveStage();
      }
    });
  });
  console.log(`[import-data] Completed ${label}.`);
}

async function cloneRawExport(source, destination, runRoot) {
  if (process.platform !== 'darwin') {
    throw new Error(
      'Copy-on-write import staging currently requires macOS clonefile(2); no full-copy fallback was attempted.',
    );
  }
  const helperSource = join(scriptRoot, 'darwin-clone-tree.c');
  const helperExecutable = join(runRoot, 'darwin-clone-tree');
  await runCommand('macOS clonefile helper compilation', '/usr/bin/xcrun', [
    'clang',
    '-std=c11',
    '-Wall',
    '-Wextra',
    '-Werror',
    helperSource,
    '-o',
    helperExecutable,
  ]);
  await runCommand('required copy-on-write raw export clone', helperExecutable, [
    source,
    destination,
  ]);
}

async function copyRawExport(source, destination) {
  console.warn(
    '[import-data] Full-copy staging was selected. This is cross-platform, but it duplicates the ' +
      'raw export temporarily and can take substantially longer than APFS clonefile staging.',
  );
  await cp(source, destination, {
    recursive: true,
    errorOnExist: true,
    force: false,
    dereference: false,
    preserveTimestamps: true,
    verbatimSymlinks: true,
  });
}

function resolveStagingMode(requested) {
  if (requested !== null && requested !== undefined) return requested;
  const selected = process.platform === 'darwin' ? 'clone' : 'copy';
  console.info(
    `[import-data] No staging mode was specified; selected ${selected} for platform ${process.platform}. ` +
      'The importer never switches modes after a staging failure.',
  );
  return selected;
}

function retainImportWork(error) {
  Object.defineProperty(error, 'retainImportWork', {value: true});
  return error;
}

export async function publishTransactional(stagingRoot, destination, backupRoot) {
  const stagingReal = await existingRealDirectory(stagingRoot, 'Validated staging root');
  const destinationParentReal = await existingRealDirectory(
    dirname(destination),
    'Live destination parent',
  );
  const destinationKind = await pathKindNoFollow(destination);
  if (destinationKind !== 'directory' && destinationKind !== 'missing') {
    throw new Error(
      `Live export destination is ${destinationKind}, not a real directory or unused path: ${destination}`,
    );
  }
  const destinationReal =
    destinationKind === 'directory'
      ? await realpath(destination)
      : join(destinationParentReal, basename(destination));
  const backupReal = await potentialRealDirectory(backupRoot, 'Rollback backup');
  assertNoOverlap(stagingReal, 'Validated staging root', destinationReal, 'live destination');
  assertNoOverlap(stagingReal, 'Validated staging root', backupReal, 'rollback backup');
  assertNoOverlap(destinationReal, 'Live destination', backupReal, 'rollback backup');
  const [stagingDevice, destinationDevice] = await Promise.all([
    stat(stagingReal),
    stat(destinationParentReal),
  ]);
  if (stagingDevice.dev !== destinationDevice.dev) {
    throw new Error(
      'Validated staging and the live destination are on different filesystems; atomic publication is unavailable.',
    );
  }

  let previousMoved = false;
  let stagingPublished = false;
  try {
    if (destinationKind === 'directory') {
      await rename(destination, backupRoot);
      previousMoved = true;
    }
    await rename(stagingRoot, destination);
    stagingPublished = true;

    const publishedReal = await existingRealDirectory(destination, 'Published export destination');
    if (publishedReal !== destinationReal) {
      throw new Error(
        `Published export resolved to ${publishedReal}, not the verified destination ${destinationReal}.`,
      );
    }
  } catch (publishError) {
    console.error('[import-data] Publication failed; starting rollback.', publishError);
    const rollbackErrors = [];
    if (stagingPublished) {
      try {
        await rename(destination, stagingRoot);
        stagingPublished = false;
      } catch (error) {
        rollbackErrors.push(error);
        console.error('[import-data] Could not move the failed new dataset out of the live path.', error);
      }
    }
    if (previousMoved) {
      try {
        await rename(backupRoot, destination);
        previousMoved = false;
      } catch (error) {
        rollbackErrors.push(error);
        console.error('[import-data] Could not restore the previous live dataset.', error);
      }
    }
    if (rollbackErrors.length > 0) {
      throw retainImportWork(
        new AggregateError(
          [publishError, ...rollbackErrors],
          'Dataset publication and rollback both failed; recovery paths were retained.',
        ),
      );
    }
    throw publishError;
  }

  if (previousMoved) {
    try {
      await rm(backupRoot, {recursive: true, force: false});
    } catch (error) {
      console.error(
        `[import-data] The new dataset is live and validated, but its rollback backup remains at ${backupRoot}.`,
        error,
      );
      throw retainImportWork(
        new Error('Dataset publication succeeded but rollback-backup cleanup failed.', {cause: error}),
      );
    }
  }
  console.log(`[import-data] Published the validated dataset at ${destination}.`);
}

async function removeImportWorkRootIfEmpty(workRoot) {
  try {
    await rmdir(workRoot);
    console.log(`[import-data] Removed empty import work root: ${workRoot}`);
  } catch (error) {
    if (error?.code === 'ENOENT') return;
    if (error?.code === 'ENOTEMPTY' || error?.code === 'EEXIST') {
      console.info(
        `[import-data] Import work root remains because it contains another run or retained recovery data: ${workRoot}`,
      );
      return;
    }
    throw error;
  }
}

export async function importExportData({
  source,
  profile,
  destination = defaultDestination,
  dryRun = false,
  omitRecipeImages = false,
  stagingMode = null,
  verifyStagedSource = null,
}) {
  if (typeof source !== 'string' || !source) {
    throw new Error('A raw export source directory is required.');
  }
  const resolvedProfile = resolveQualityProfile(profile);
  const structuredDataOnly = usesStructuredDataOnlyPublication(resolvedProfile);
  const resolvedStagingMode = resolveStagingMode(stagingMode);
  if (resolvedStagingMode !== 'clone' && resolvedStagingMode !== 'copy') {
    throw new Error('stagingMode must be exactly clone or copy.');
  }
  if (resolvedProfile === null) {
    throw new Error(
      `An explicit export quality profile is required. Supported profiles: ` +
        EXPORT_QUALITY_PROFILE_IDS.join(', '),
    );
  }
  if (verifyStagedSource !== null && typeof verifyStagedSource !== 'function') {
    throw new Error('verifyStagedSource must be a function when a staged-source gate is requested.');
  }
  const resolvedSource = resolve(source);
  const resolvedDestination = resolve(destination);
  const sourceKind = await pathKindNoFollow(resolvedSource);
  if (sourceKind !== 'directory') {
    throw new Error(
      `Raw export source is ${sourceKind}, not a real directory (source-root symlinks are refused): ${resolvedSource}`,
    );
  }
  const sourceReal = await realpath(resolvedSource);

  const destinationParent = dirname(resolvedDestination);
  const destinationParentReal = await existingRealDirectory(
    destinationParent,
    'Destination parent',
  );
  const destinationKind = await pathKindNoFollow(resolvedDestination);
  if (destinationKind !== 'directory' && destinationKind !== 'missing') {
    throw new Error(
      `Live export destination is ${destinationKind}, not a real directory or unused path: ${resolvedDestination}`,
    );
  }
  const destinationReal =
    destinationKind === 'directory'
      ? await realpath(resolvedDestination)
      : join(destinationParentReal, basename(resolvedDestination));
  assertNoOverlap(sourceReal, 'Raw export source', destinationReal, 'live destination');

  // Traverse the complete source before creating staging. This prevents image
  // roots or nested paths from redirecting the optimizer through symlinks and
  // rejects sockets/FIFOs/devices before any publication side effect.
  try {
    const sourceFileCount = await assertPlainDirectoryTree(sourceReal);
    console.log(
      `[import-data] Source filesystem preflight accepted ${sourceFileCount} regular file(s) and no symlinks or special entries.`,
    );
  } catch (error) {
    console.error(
      '[import-data] Source filesystem preflight rejected the raw export before staging was created.',
      error,
    );
    throw error;
  }

  // Fail fast before a potentially large copy. The staged copy is checked
  // again by the exhaustive validator, so source mutation cannot bypass gates.
  await assertQualityMetadata(sourceReal, 'Raw source export', resolvedProfile);

  const workRoot = importWorkspaceRootForDestination(resolvedDestination);
  const workKind = await pathKindNoFollow(workRoot);
  if (workKind === 'missing') {
    await mkdir(workRoot);
    console.log(`[import-data] Created import work root outside public: ${workRoot}`);
  } else if (workKind !== 'directory') {
    throw new Error(`Import work root is ${workKind}, not a real directory: ${workRoot}`);
  }
  const workRootReal = await realpath(workRoot);
  if (workRootReal === destinationParentReal || containsPath(destinationParentReal, workRootReal)) {
    throw new Error(
      `Import work root must remain outside the public destination parent: ${workRootReal}`,
    );
  }
  assertNoOverlap(sourceReal, 'Raw export source', workRootReal, 'import work root');
  assertNoOverlap(destinationReal, 'Live destination', workRootReal, 'import work root');
  const [sourceDevice, destinationDevice, workDevice] = await Promise.all([
    stat(sourceReal),
    stat(destinationParentReal),
    stat(workRootReal),
  ]);
  if (destinationDevice.dev !== workDevice.dev) {
    throw new Error(
      'Import work root and live destination are on different filesystems; atomic rename publication is unavailable.',
    );
  }
  if (resolvedStagingMode === 'clone' && sourceDevice.dev !== workDevice.dev) {
    throw new Error(
      'Raw export source and import work root are on different filesystems; required copy-on-write cloning is unavailable.',
    );
  }

  const runRoot = join(workRootReal, `import-${process.pid}-${randomUUID()}`);
  let runRootReal = runRoot;
  let runRootCreated = false;
  let primaryError = null;
  let preserveRunRoot = false;
  try {
    await mkdir(runRoot);
    runRootCreated = true;
    runRootReal = await existingRealDirectory(runRoot, 'Import run root');
    const stagingRoot = join(runRootReal, 'staging');
    const backupRoot = join(runRootReal, 'rollback-backup');
    const stagingPotentialReal = await potentialRealDirectory(stagingRoot, 'Staging root');
    const backupPotentialReal = await potentialRealDirectory(backupRoot, 'Rollback backup');
    assertNoOverlap(sourceReal, 'Raw export source', stagingPotentialReal, 'staging root');
    assertNoOverlap(destinationReal, 'Live destination', stagingPotentialReal, 'staging root');
    assertNoOverlap(sourceReal, 'Raw export source', backupPotentialReal, 'rollback backup');
    assertNoOverlap(destinationReal, 'Live destination', backupPotentialReal, 'rollback backup');
    assertNoOverlap(stagingPotentialReal, 'Staging root', backupPotentialReal, 'rollback backup');

    console.log(
      `[import-data] out-of-public staging: ${stagingRoot} (explicit ${resolvedStagingMode} mode)`,
    );
    if (resolvedStagingMode === 'clone') {
      await cloneRawExport(sourceReal, stagingRoot, runRootReal);
    } else {
      await copyRawExport(sourceReal, stagingRoot);
    }
    const stagingReal = await existingRealDirectory(stagingRoot, 'Copied staging root');
    if (stagingReal !== stagingPotentialReal) {
      throw new Error(
        `Copied staging root resolved to ${stagingReal}, not the verified path ${stagingPotentialReal}.`,
      );
    }
    assertNoOverlap(sourceReal, 'Raw export source', stagingReal, 'copied staging root');
    assertNoOverlap(destinationReal, 'Live destination', stagingReal, 'copied staging root');
    try {
      const stagedFileCount = await assertPlainDirectoryTree(stagingReal);
      console.log(
        `[import-data] Staged filesystem preflight accepted ${stagedFileCount} regular file(s) and no symlinks or special entries.`,
      );
    } catch (error) {
      console.error(
        '[import-data] Staged filesystem preflight rejected the copied export before optimization began.',
        error,
      );
      throw error;
    }
    await assertQualityMetadata(stagingReal, 'Staged raw export', resolvedProfile);
    if (verifyStagedSource !== null) {
      console.log('[import-data] Starting the caller-required staged-source integrity gate.');
      await verifyStagedSource(stagingReal);
      console.log('[import-data] Completed the caller-required staged-source integrity gate.');
    }

    if (structuredDataOnly) {
      console.warn(
        `[import-data][rights-policy] ${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID} keeps original ` +
          'visual files only through exhaustive raw decoding and exclusion accounting. The ' +
          'lossless WebP optimization stage is intentionally skipped because no visual file may ' +
          'enter the public dataset.',
      );
      if (omitRecipeImages) {
        console.info(
          '[import-data][rights-policy] The explicit omitRecipeImages request is subsumed by ' +
            'the stricter GTNH policy, which omits every visual asset class.',
        );
      }
    } else {
      const optimizationArgs = ['--root', stagingReal];
      if (omitRecipeImages) optimizationArgs.push('--omit-recipe-images');
      await runStage(
        omitRecipeImages
          ? 'omission-aware retained-image optimization'
          : 'lossless image optimization',
        'optimize-export-assets.mjs',
        optimizationArgs,
      );
    }
    const packingArgs = ['--root', stagingReal, '--profile', resolvedProfile];
    if (omitRecipeImages && !structuredDataOnly) packingArgs.push('--omit-recipe-images');
    await runStage(
      'exhaustive raw validation, asset packing, and exact publication validation',
      'pack-export-assets.mjs',
      packingArgs,
    );
    await assertPackedRecipePreviewCompleteness(stagingReal, resolvedProfile);
    await existingRealDirectory(stagingRoot, 'Final validated staging root');

    if (dryRun) {
      console.log('[import-data] Dry run passed every gate; the live dataset was not modified.');
    } else {
      await publishTransactional(stagingRoot, resolvedDestination, backupRoot);
    }
  } catch (error) {
    primaryError = error;
    preserveRunRoot = error?.retainImportWork === true;
    console.error(
      '[import-data] Import did not complete cleanly; inspect the publication and rollback logs above. No fallback dataset was selected.',
      error,
    );
  }

  if (preserveRunRoot) {
    console.error(`[import-data] Recovery data was retained at ${runRootReal}.`);
  } else {
    try {
      if (!runRootCreated) {
        console.info(
          `[import-data] No owned import run directory was created; no cleanup was attempted at ${runRootReal}.`,
        );
      } else {
        await rm(runRootReal, {recursive: true, force: false});
        console.log(`[import-data] Removed import run data: ${runRootReal}`);
      }
    } catch (cleanupError) {
      console.error(`[import-data] Import-run cleanup failed; data remains at ${runRootReal}.`, cleanupError);
      if (primaryError) {
        throw new AggregateError([primaryError, cleanupError], 'Import and staging cleanup both failed.');
      }
      throw cleanupError;
    }
  }

  try {
    await removeImportWorkRootIfEmpty(workRootReal);
  } catch (cleanupError) {
    console.error(`[import-data] Empty import-work cleanup failed: ${workRootReal}.`, cleanupError);
    if (primaryError) {
      throw new AggregateError([primaryError, cleanupError], 'Import and work-root cleanup both failed.');
    }
    throw cleanupError;
  }
  if (primaryError) throw primaryError;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath && fileURLToPath(import.meta.url) === invokedPath) {
  let options;
  try {
    options = parseArguments(process.argv.slice(2));
    if (options.showHelp) {
      console.log(usage());
    } else {
      await importExportData(options);
    }
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}
