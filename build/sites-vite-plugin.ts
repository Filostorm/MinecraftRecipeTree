import {access, appendFile, copyFile, cp, lstat, mkdir, readFile, readdir, rm} from 'node:fs/promises';
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
const MAX_FAVICON_BYTES = 64 * 1024;
const MAX_PACK_ICON_BYTES = 256 * 1024;
const PACK_ICON_FILES = Object.freeze([
  'gt-new-horizons.webp',
  'meatballcraft.webp',
  'multiblock-madness-2.webp',
  'multiblock-madness.webp',
]);
const STATIC_SECURITY_HEADERS = `
/*
  Content-Security-Policy: default-src 'self'; base-uri 'self'; connect-src 'self' https://metrics.craftsmannsoftware.com; font-src 'self' data:; frame-ancestors 'none'; form-action 'self'; img-src 'self' data: blob:; object-src 'none'; script-src 'self' 'unsafe-inline' https://metrics.craftsmannsoftware.com; style-src 'self' 'unsafe-inline'; worker-src 'self' blob:
  Cross-Origin-Opener-Policy: same-origin
  Cross-Origin-Resource-Policy: same-origin
  Permissions-Policy: camera=(), display-capture=(), geolocation=(), microphone=(), payment=(), usb=()
  Referrer-Policy: strict-origin-when-cross-origin
  Strict-Transport-Security: max-age=63072000; includeSubDomains
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY

/assets/*
  Cache-Control: public, max-age=31536000, immutable

/exporters/*.jar
  Cache-Control: public, max-age=31536000, immutable
  Content-Type: application/java-archive
  X-Content-Type-Options: nosniff

/exporters/manifest.json
  Cache-Control: public, max-age=300, must-revalidate
  Content-Type: application/json; charset=utf-8
  X-Content-Type-Options: nosniff
`;

async function appendHeaderBlock(root: string, block: string): Promise<void> {
  const headersPath = resolve(root, 'dist', 'client', '_headers');
  const existing = (await exists(headersPath)) ? await readFile(headersPath, 'utf8') : '';
  if (existing.includes(block.trim())) return;
  await appendFile(headersPath, `\n${block.trim()}\n`, 'utf8');
}

async function copyFavicon(root: string): Promise<void> {
  const source = resolve(root, 'public', 'favicon.svg');
  const destination = resolve(root, 'dist', 'client', 'favicon.svg');
  const metadata = await lstat(source);
  if (!metadata.isFile() || metadata.nlink !== 1 || metadata.size <= 0 || metadata.size > MAX_FAVICON_BYTES) {
    console.error(`Sites build failed: favicon is not a bounded single-link regular file: ${source}`);
    throw new Error('Application favicon violates its deployment contract');
  }
  await copyFile(source, destination);
  await appendHeaderBlock(
    root,
    '/favicon.svg\n  Content-Type: image/svg+xml\n  Cache-Control: public, max-age=86400',
  );
}

async function copyPackIcons(root: string): Promise<void> {
  const sourceDirectory = resolve(root, 'public', 'pack-icons');
  const destinationDirectory = resolve(root, 'dist', 'client', 'pack-icons');
  const entries = await readdir(sourceDirectory, {withFileTypes: true});
  const deployableEntries = entries.filter(entry => entry.name !== 'README.md');
  const names = deployableEntries.map(entry => entry.name).sort();
  if (
    names.length !== PACK_ICON_FILES.length ||
    names.some((name, index) => name !== PACK_ICON_FILES[index])
  ) {
    console.error('Sites build failed: pack icon directory does not match its exact manifest.', {
      sourceDirectory,
      expected: PACK_ICON_FILES,
      actual: names,
    });
    throw new Error('Pack icon directory violates its deployment contract');
  }

  await rm(destinationDirectory, {recursive: true, force: true});
  await mkdir(destinationDirectory, {recursive: true});
  for (const entry of deployableEntries) {
    const source = resolve(sourceDirectory, entry.name);
    const metadata = await lstat(source);
    if (
      basename(source) !== entry.name ||
      !entry.isFile() ||
      !metadata.isFile() ||
      metadata.nlink !== 1 ||
      metadata.size <= 0 ||
      metadata.size > MAX_PACK_ICON_BYTES
    ) {
      console.error(`Sites build failed: pack icon is not a bounded single-link file: ${source}`);
      throw new Error('Pack icon violates its deployment contract');
    }
    await copyFile(source, resolve(destinationDirectory, entry.name));
  }

  await appendHeaderBlock(
    root,
    '/pack-icons/*\n  Content-Type: image/webp\n  Cache-Control: public, max-age=86400',
  );
}

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
      await copyFavicon(root);
      await copyPackIcons(root);
      await appendHeaderBlock(root, STATIC_SECURITY_HEADERS);

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
