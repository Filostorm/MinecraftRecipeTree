import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import {EXPORT_QUALITY_PROFILE_IDS, resolveQualityProfile} from './export-quality-policy.mjs';
import {
  finalizeExportTreeSnapshot,
  prepareExportTreeSnapshot,
} from './export-tree-digest.mjs';
import {validateExportData} from './validate-export-data.mjs';

const ROOT_MANIFEST_PATH = 'manifest.json';
const MAX_REPORTED_DIFFERENCES = 100;

function parseRootManifest(bytes, label) {
  if (!Buffer.isBuffer(bytes)) {
    throw new Error(`${label} secure snapshot does not contain root manifest.json bytes.`);
  }
  let manifest;
  try {
    manifest = JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(
      `${label} root manifest.json could not be parsed from its secure snapshot: ${
        error instanceof Error ? error.message : String(error)
      }`,
    );
  }
  if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) {
    throw new Error(`${label} root manifest.json must contain an object.`);
  }
  const normalized = {...manifest};
  delete normalized.generatedAt;
  delete normalized.durationMs;
  return normalized;
}

function describeJsonDifference(left, right, path = 'manifest') {
  if (isDeepStrictEqual(left, right)) return null;
  if (
    left === null ||
    right === null ||
    typeof left !== 'object' ||
    typeof right !== 'object'
  ) {
    return `${path}: ${JSON.stringify(left)} !== ${JSON.stringify(right)}`;
  }
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right)) {
      return `${path}: one value is an array and the other is not`;
    }
    if (left.length !== right.length) {
      return `${path}.length: ${left.length} !== ${right.length}`;
    }
    for (let index = 0; index < left.length; index += 1) {
      const difference = describeJsonDifference(left[index], right[index], `${path}[${index}]`);
      if (difference !== null) return difference;
    }
    return `${path}: values differ`;
  }
  const leftKeys = Object.keys(left).sort();
  const rightKeys = Object.keys(right).sort();
  if (!isDeepStrictEqual(leftKeys, rightKeys)) {
    const missing = leftKeys.find(key => !rightKeys.includes(key));
    const added = rightKeys.find(key => !leftKeys.includes(key));
    if (missing !== undefined) return `${path}.${missing}: absent from the right manifest`;
    return `${path}.${added}: absent from the left manifest`;
  }
  for (const key of leftKeys) {
    const difference = describeJsonDifference(left[key], right[key], `${path}.${key}`);
    if (difference !== null) return difference;
  }
  return `${path}: values differ`;
}

function exactFileDifferences(leftFiles, rightFiles) {
  const differences = [];
  let suppressed = 0;
  const report = message => {
    if (differences.length < MAX_REPORTED_DIFFERENCES) differences.push(message);
    else suppressed += 1;
  };
  let leftIndex = 0;
  let rightIndex = 0;
  while (leftIndex < leftFiles.length || rightIndex < rightFiles.length) {
    const left = leftFiles[leftIndex];
    const right = rightFiles[rightIndex];
    if (left?.relativePath === right?.relativePath) {
      if (
        left.relativePath !== ROOT_MANIFEST_PATH &&
        (left.bytes !== right.bytes || left.sha256 !== right.sha256)
      ) {
        report(
          `${left.relativePath}: bytes differ ` +
            `(left ${left.bytes} bytes sha256=${left.sha256}; ` +
            `right ${right.bytes} bytes sha256=${right.sha256})`,
        );
      }
      leftIndex += 1;
      rightIndex += 1;
      continue;
    }
    if (
      right === undefined ||
      (left !== undefined &&
        Buffer.compare(
          Buffer.from(left.relativePath, 'utf8'),
          Buffer.from(right.relativePath, 'utf8'),
        ) < 0)
    ) {
      report(`${left.relativePath}: present only in the left export`);
      leftIndex += 1;
    } else {
      report(`${right.relativePath}: present only in the right export`);
      rightIndex += 1;
    }
  }
  return {differences, suppressed};
}

function requireEqualRecipeImageInventories(leftSummary, rightSummary) {
  const left = leftSummary.recipeImageInventory;
  const right = rightSummary.recipeImageInventory;
  if (!left || !right) {
    throw new Error(
      'Canonical decoded-RGBA recipe-image comparison was requested, but validation did not ' +
        'produce an inventory for both exports. This mode requires raw recipe preview assets.',
    );
  }
  if (!isDeepStrictEqual(left, right)) {
    throw new Error(
      'Canonical decoded-RGBA recipe-image inventories differ: ' +
        `left=${JSON.stringify(left)} right=${JSON.stringify(right)}.`,
    );
  }
  return left;
}

/**
 * Validate and compare two independent exporter runs. Every file is bound to
 * the filesystem identity captured before validation, then read once through
 * O_NOFOLLOW descriptors and re-inventoried after hashing. Only the two
 * explicitly volatile root-manifest values are excluded from semantic equality.
 */
export async function compareDeterministicExports(
  leftExportRoot,
  rightExportRoot,
  {profile, compareRecipeImageInventory = false, logger = console} = {},
) {
  const resolvedProfile = resolveQualityProfile(profile);
  if (resolvedProfile === null) {
    throw new Error(
      `Deterministic export comparison requires --profile <${EXPORT_QUALITY_PROFILE_IDS.join('|')}>.`,
    );
  }
  const leftRoot = resolve(leftExportRoot);
  const rightRoot = resolve(rightExportRoot);
  if (leftRoot === rightRoot) {
    throw new Error('Deterministic export comparison requires two distinct export roots.');
  }

  const leftPrepared = await prepareExportTreeSnapshot(leftRoot, {
    logger,
    captureRootManifest: true,
  });
  const rightPrepared = await prepareExportTreeSnapshot(rightRoot, {
    logger,
    captureRootManifest: true,
  });
  const validationOptions = {
    profile: resolvedProfile,
    requirePackIdentity: true,
    computeRecipeImageInventory: compareRecipeImageInventory,
  };

  let leftSummary;
  try {
    leftSummary = await validateExportData(leftRoot, validationOptions);
  } catch (error) {
    throw new Error(
      `Left export failed ${resolvedProfile} validation: ${
        error instanceof Error ? error.message : String(error)
      }`,
    );
  }
  let rightSummary;
  try {
    rightSummary = await validateExportData(rightRoot, validationOptions);
  } catch (error) {
    throw new Error(
      `Right export failed ${resolvedProfile} validation: ${
        error instanceof Error ? error.message : String(error)
      }`,
    );
  }

  const leftSnapshot = await finalizeExportTreeSnapshot(leftPrepared, {logger});
  const rightSnapshot = await finalizeExportTreeSnapshot(rightPrepared, {logger});
  const {differences, suppressed} = exactFileDifferences(
    leftSnapshot.files,
    rightSnapshot.files,
  );
  if (differences.length > 0 || suppressed > 0) {
    const detail = differences.map(value => `- ${value}`).join('\n');
    const suffix = suppressed > 0 ? `\n- ...and ${suppressed} additional differences.` : '';
    throw new Error(
      `Deterministic export byte comparison failed with ` +
        `${differences.length + suppressed} difference(s):\n${detail}${suffix}`,
    );
  }

  const leftManifest = parseRootManifest(leftSnapshot.manifestBytes, 'Left export');
  const rightManifest = parseRootManifest(rightSnapshot.manifestBytes, 'Right export');
  if (!isDeepStrictEqual(leftManifest, rightManifest)) {
    throw new Error(
      'Root manifests differ after normalizing only generatedAt and durationMs. First ' +
        `difference: ${describeJsonDifference(leftManifest, rightManifest)}.`,
    );
  }

  const recipeImageInventory = compareRecipeImageInventory
    ? requireEqualRecipeImageInventories(leftSummary, rightSummary)
    : undefined;
  const nonManifestFiles = leftSnapshot.files.filter(
    file => file.relativePath !== ROOT_MANIFEST_PATH,
  );
  const result = Object.freeze({
    profile: resolvedProfile,
    leftRoot,
    rightRoot,
    files: leftSnapshot.files.length,
    nonManifestFiles: nonManifestFiles.length,
    nonManifestBytes: nonManifestFiles.reduce((sum, file) => sum + file.bytes, 0),
    ...(recipeImageInventory === undefined ? {} : {recipeImageInventory}),
  });
  logger.info(
    `[determinism] PASS ${resolvedProfile}: ${result.files} identical paths, ` +
      `${result.nonManifestFiles} byte-identical non-manifest files, and root manifests equal ` +
      'after normalizing only generatedAt and durationMs.',
  );
  return result;
}

function parseCli(args) {
  let leftRoot;
  let rightRoot;
  let profile;
  let compareRecipeImageInventory = false;
  let showHelp = false;
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') showHelp = true;
    else if (argument === '--left') {
      leftRoot = args[++index];
      if (!leftRoot) throw new Error('--left requires an export directory.');
    } else if (argument === '--right') {
      rightRoot = args[++index];
      if (!rightRoot) throw new Error('--right requires an export directory.');
    } else if (argument === '--profile') {
      profile = args[++index];
      if (!profile) throw new Error('--profile requires a quality-profile id.');
    } else if (argument === '--compare-recipe-rgba') {
      compareRecipeImageInventory = true;
    } else {
      throw new Error(`Unknown deterministic-comparison argument: ${argument}`);
    }
  }
  if (!showHelp && (!leftRoot || !rightRoot || !profile)) {
    throw new Error('--left, --right, and --profile are required.');
  }
  return {leftRoot, rightRoot, profile, compareRecipeImageInventory, showHelp};
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath && fileURLToPath(import.meta.url) === invokedPath) {
  try {
    const options = parseCli(process.argv.slice(2));
    if (options.showHelp) {
      console.log(
        'Usage: node scripts/compare-deterministic-exports.mjs ' +
          '--left <directory> --right <directory> ' +
          `--profile <${EXPORT_QUALITY_PROFILE_IDS.join('|')}> ` +
          '[--compare-recipe-rgba]',
      );
    } else {
      await compareDeterministicExports(options.leftRoot, options.rightRoot, options);
    }
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}
