import {access, copyFile, cp, lstat, mkdir, readdir, rm} from 'node:fs/promises';
import {basename, resolve} from 'node:path';
import type {Plugin} from 'vite';

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') {
      return false;
    }
    throw error;
  }
}

const MAX_EXPORTER_RELEASE_FILE_BYTES = 64 * 1024 * 1024;
const MAX_EXPORTER_RELEASE_DIRECTORY_BYTES = 128 * 1024 * 1024;

async function copyExporterReleases(root: string): Promise<void> {
  const sourceDirectory = resolve(root, 'public', 'exporters');
  const destinationDirectory = resolve(root, 'dist', 'client', 'exporters');

  if (!(await exists(sourceDirectory))) {
    console.error(`Sites build failed: missing exporter release directory ${sourceDirectory}`);
    throw new Error('Exporter releases must be packaged before building the public site');
  }

  const entries = await readdir(sourceDirectory, {withFileTypes: true});
  if (!entries.some(entry => entry.name === 'manifest.json')) {
    console.error(`Sites build failed: ${sourceDirectory} does not contain manifest.json`);
    throw new Error('Exporter release manifest is missing');
  }

  let totalBytes = 0;
  const files: Array<{name: string; source: string}> = [];
  for (const entry of entries) {
    const source = resolve(sourceDirectory, entry.name);
    if (
      basename(source) !== entry.name ||
      (!entry.name.endsWith('.jar') && entry.name !== 'manifest.json')
    ) {
      console.error(`Sites build failed: unsupported exporter release entry ${source}`);
      throw new Error('Exporter release directory contains an unsupported path');
    }

    const metadata = await lstat(source);
    if (!entry.isFile() || !metadata.isFile() || metadata.nlink !== 1) {
      console.error(`Sites build failed: exporter release entry is not a single-link regular file: ${source}`);
      throw new Error('Exporter release directory contains a symlink, hard link, or special file');
    }
    if (metadata.size <= 0 || metadata.size > MAX_EXPORTER_RELEASE_FILE_BYTES) {
      console.error(`Sites build failed: exporter release file has an invalid byte length: ${source}`);
      throw new Error('Exporter release file exceeds its bounded deployment contract');
    }

    totalBytes += metadata.size;
    if (totalBytes > MAX_EXPORTER_RELEASE_DIRECTORY_BYTES) {
      console.error(`Sites build failed: exporter release directory exceeds its byte budget: ${sourceDirectory}`);
      throw new Error('Exporter release directory exceeds its bounded deployment contract');
    }
    files.push({name: entry.name, source});
  }

  await rm(destinationDirectory, {recursive: true, force: true});
  await mkdir(destinationDirectory, {recursive: true});
  for (const file of files) {
    await copyFile(file.source, resolve(destinationDirectory, file.name));
  }
}

/** Copies Sites metadata into the deployable artifact after Vite builds. */
export function sites(): Plugin {
  let root = process.cwd();

  return {
    name: 'sites',
    apply: 'build',
    configResolved(config) {
      root = config.root;
    },
    async closeBundle() {
      const retiredStaticExportDirectory = resolve(root, 'dist', 'client', 'exports');
      if (await exists(retiredStaticExportDirectory)) {
        console.error(
          `Sites build failed: retired static dataset output exists at ${retiredStaticExportDirectory}`,
        );
        throw new Error('Static dataset exports must not be bundled; publish immutable datasets through R2');
      }

      await copyExporterReleases(root);

      const outputDirectory = resolve(root, 'dist', '.openai');
      const hostingConfig = resolve(root, '.openai', 'hosting.json');

      await rm(outputDirectory, {recursive: true, force: true});
      await mkdir(outputDirectory, {recursive: true});

      if (!(await exists(hostingConfig))) {
        console.error(`Sites build failed: missing ${hostingConfig}`);
        throw new Error('Missing Sites hosting configuration');
      }

      await cp(hostingConfig, resolve(outputDirectory, 'hosting.json'));
    },
  };
}
