import {constants} from 'node:fs';
import {lstat, mkdir, open, rename, writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {dirname, isAbsolute, join, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

export const EXPORTER_RELEASE_MANIFEST_FORMAT = 'mrt-exporter-releases-v1';
const MAX_EXPORTER_JAR_BYTES = 16 * 1024 * 1024;
const SCRIPT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const DEFAULT_WORKSPACE_ROOT = resolve(SCRIPT_DIRECTORY, '..', '..');
const DEFAULT_PUBLIC_ROOT = resolve(SCRIPT_DIRECTORY, '..', 'public', 'exporters');
const RELEASE_FILENAME_PATTERN = /^[a-z0-9][a-z0-9.-]{0,198}\.jar$/;

export const EXPORTER_RELEASE_DEFINITIONS = Object.freeze([
  Object.freeze({
    id: 'forge-jei-1.20.1',
    minecraftVersion: '1.20.1',
    recipeViewer: 'JEI 15',
    loader: 'Forge 47',
    version: '1.0.0',
    source: 'recipe-export-mod/build/libs/jeiexport-1.0.0.jar',
    filename: 'recipe-tree-exporter-forge-1.20.1-1.0.0.jar',
    qualityProfiles: Object.freeze(['generic-jei-1.20.1']),
    compatibility: 'Forge 47 with JEI 15.x',
  }),
  Object.freeze({
    id: 'forge-rei-1.18.2',
    minecraftVersion: '1.18.2',
    recipeViewer: 'REI 8',
    loader: 'Forge 40',
    version: '1.0.0',
    source: 'recipe-export-mod-1.18.2/build/libs/recipe-export-mod-1.18.2-1.0.0.jar',
    filename: 'recipe-tree-exporter-forge-1.18.2-1.0.0.jar',
    qualityProfiles: Object.freeze(['multiblock-madness-2-1.18.2']),
    compatibility: 'Forge 40 with REI 8.4.x',
  }),
  Object.freeze({
    id: 'forge-hei-1.12.2',
    minecraftVersion: '1.12.2',
    recipeViewer: 'HEI/JEI 4',
    loader: 'Forge 14.23.5',
    version: '1.0.0',
    source: 'recipe-export-mod-1.12.2/build/libs/recipe-export-mod-1.12.2-1.0.0.jar',
    filename: 'recipe-tree-exporter-forge-1.12.2-1.0.0.jar',
    qualityProfiles: Object.freeze(['meatballcraft-1.12.2', 'multiblock-madness-1.12.2']),
    compatibility: 'Forge 14.23.5 with HEI 4.30.3 (validated target)',
  }),
  Object.freeze({
    id: 'forge-nei-gtnh-1.7.10',
    minecraftVersion: '1.7.10',
    recipeViewer: 'NEI 2.8.44-GTNH',
    loader: 'Forge 10.13.4.1614',
    version: '1.0.0',
    source: 'recipe-export-mod-1.7.10/build/libs/recipe-tree-gtnh-nei-exporter-1.0.0.jar',
    filename: 'recipe-tree-exporter-gtnh-1.7.10-1.0.0.jar',
    qualityProfiles: Object.freeze(['gtnh-1.7.10']),
    compatibility: 'GT New Horizons 2.8.4 with NEI 2.8.44-GTNH',
  }),
]);

async function readVerifiedJar(path, label) {
  const before = await lstat(path);
  if (before.isSymbolicLink() || !before.isFile() || before.nlink !== 1) {
    throw new Error(`${label} must be a plain, non-hard-linked regular file: ${path}.`);
  }
  if (before.size < 4 || before.size > MAX_EXPORTER_JAR_BYTES) {
    throw new Error(`${label} must be between 4 and ${MAX_EXPORTER_JAR_BYTES} bytes.`);
  }
  if (!Number.isSafeInteger(constants.O_NOFOLLOW)) {
    throw new Error(`${label} cannot be packaged because this runtime lacks O_NOFOLLOW.`);
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
    if (after.size !== opened.size || after.mtimeMs !== opened.mtimeMs) {
      throw new Error(`${label} changed while it was read.`);
    }
    if (bytes[0] !== 0x50 || bytes[1] !== 0x4b || bytes[2] !== 0x03 || bytes[3] !== 0x04) {
      throw new Error(`${label} does not begin with the ZIP/JAR local-file signature.`);
    }
    return bytes;
  } finally {
    await handle.close();
  }
}

async function atomicWrite(path, bytes) {
  const temporary = join(dirname(path), `.${path.split(sep).at(-1)}.${process.pid}.${Date.now()}.tmp`);
  await writeFile(temporary, bytes, {flag: 'wx'});
  await rename(temporary, path);
}

export async function packageExporterReleases({
  workspaceRoot = DEFAULT_WORKSPACE_ROOT,
  publicRoot = DEFAULT_PUBLIC_ROOT,
  definitions = EXPORTER_RELEASE_DEFINITIONS,
  generatedAt = new Date().toISOString(),
  logger = console,
} = {}) {
  if (!Number.isFinite(Date.parse(generatedAt))) {
    throw new Error('generatedAt must be an ISO-compatible timestamp.');
  }
  if (!Array.isArray(definitions) || definitions.length < 1 || definitions.length > 16) {
    throw new Error('Exporter packaging requires between 1 and 16 release definitions.');
  }
  await mkdir(publicRoot, {recursive: true});
  const releases = [];
  for (const definition of definitions) {
    if (
      typeof definition.source !== 'string' ||
      isAbsolute(definition.source) ||
      typeof definition.filename !== 'string' ||
      !RELEASE_FILENAME_PATTERN.test(definition.filename) ||
      /(?:-dev|-sources)\.jar$/i.test(definition.filename)
    ) {
      throw new Error(`Exporter release ${String(definition?.id)} has an invalid release filename.`);
    }
    const sourcePath = resolve(workspaceRoot, definition.source);
    const sourceRelative = relative(resolve(workspaceRoot), sourcePath);
    if (
      sourceRelative === '' ||
      isAbsolute(sourceRelative) ||
      sourceRelative === '..' ||
      sourceRelative.startsWith(`..${sep}`)
    ) {
      throw new Error(`Exporter release ${definition.id} resolves outside the workspace root.`);
    }
    const bytes = await readVerifiedJar(sourcePath, `Exporter release ${definition.id}`);
    const sha256 = createHash('sha256').update(bytes).digest('hex');
    const targetPath = join(publicRoot, definition.filename);
    await atomicWrite(targetPath, bytes);
    const {source: _source, ...publicDefinition} = definition;
    const release = Object.freeze({
      ...publicDefinition,
      downloadUrl: `/exporters/${definition.filename}`,
      sha256,
      bytes: bytes.length,
    });
    releases.push(release);
    logger.info(
      `[exporter-release] Packaged ${definition.id}: ${bytes.length} bytes, sha256=${sha256}.`,
    );
  }
  const manifest = Object.freeze({
    format: EXPORTER_RELEASE_MANIFEST_FORMAT,
    generatedAt,
    releases: Object.freeze(releases),
  });
  await atomicWrite(join(publicRoot, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
  logger.info(`[exporter-release] Wrote ${releases.length} checksummed releases to ${publicRoot}.`);
  return manifest;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    await packageExporterReleases();
  } catch (error) {
    console.error(`[exporter-release] ${error instanceof Error ? error.message : String(error)}`);
    process.exitCode = 1;
  }
}
