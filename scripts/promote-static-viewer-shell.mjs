import {appendFile, copyFile, lstat, readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const MIN_SHELL_BYTES = 1024;
const MAX_SHELL_BYTES = 512 * 1024;
const STATIC_SHELL_HEADERS = `
/
  Cache-Control: no-cache, no-store, must-revalidate

/index.html
  Cache-Control: no-cache, no-store, must-revalidate
`;

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function requiredRootRoute(value) {
  if (!isRecord(value) || !Array.isArray(value.routes)) {
    throw new Error('Vinext prerender report is not an object with a routes array.');
  }
  const matches = value.routes.filter(route => isRecord(route) && route.route === '/');
  if (
    matches.length !== 1 ||
    matches[0].status !== 'rendered' ||
    matches[0].revalidate !== false ||
    matches[0].router !== 'app'
  ) {
    throw new Error('The viewer root was not rendered as one immutable App Router shell.');
  }
}

async function requireRegularBoundedFile(path, minimumBytes, maximumBytes, label) {
  const metadata = await lstat(path);
  if (
    !metadata.isFile() ||
    metadata.nlink !== 1 ||
    metadata.size < minimumBytes ||
    metadata.size > maximumBytes
  ) {
    throw new Error(`${label} is not a bounded single-link regular file: ${path}`);
  }
  return metadata;
}

export async function promoteStaticViewerShell(root = process.cwd()) {
  const reportPath = resolve(root, 'dist', 'server', 'vinext-prerender.json');
  const sourcePath = resolve(root, 'dist', 'server', 'prerendered-routes', 'index.html');
  const destinationPath = resolve(root, 'dist', 'client', 'index.html');
  const headersPath = resolve(root, 'dist', 'client', '_headers');

  requiredRootRoute(JSON.parse(await readFile(reportPath, 'utf8')));
  await requireRegularBoundedFile(sourcePath, MIN_SHELL_BYTES, MAX_SHELL_BYTES, 'Static viewer shell');
  const html = await readFile(sourcePath, 'utf8');
  if (
    !html.startsWith('<!DOCTYPE html>') ||
    !html.includes('<title>Minecraft Recipe Tree</title>') ||
    !html.includes('id="_R_"') ||
    !html.includes('self.__VINEXT_RSC_DONE__=true') ||
    html.includes('\u0000')
  ) {
    throw new Error('Static viewer shell is missing its exact hydration or metadata markers.');
  }

  const assetPaths = [...new Set(html.match(/\/assets\/[A-Za-z0-9._-]+/g) ?? [])];
  if (assetPaths.length === 0) {
    throw new Error('Static viewer shell contains no hashed application assets.');
  }
  for (const assetPath of assetPaths) {
    await requireRegularBoundedFile(
      resolve(root, 'dist', 'client', assetPath.slice(1)),
      1,
      16 * 1024 * 1024,
      'Static viewer shell asset',
    );
  }

  await copyFile(sourcePath, destinationPath);
  const headers = await readFile(headersPath, 'utf8');
  if (!headers.includes(STATIC_SHELL_HEADERS.trim())) {
    await appendFile(headersPath, STATIC_SHELL_HEADERS, 'utf8');
  }
  console.info(`Promoted static viewer shell to ${destinationPath}.`);
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (invokedPath === fileURLToPath(import.meta.url)) {
  await promoteStaticViewerShell();
}
