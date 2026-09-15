import {writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {auditItemIcons} from '../src/data/itemIconAudit.ts';
import {requireDatasetCatalog, datasetSource} from '../src/data/datasetCatalog.ts';
import {hasExactGtnhStructuredDataOnlyVisualAssets} from './visual-assets-rights-policy.mjs';

const MAX_BYTES = 8 * 1024 * 1024;
async function readJson(url, expectedBytes) {
  const response = await fetch(url, {signal: AbortSignal.timeout(30_000)});
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  const reader = response.body.getReader();
  const chunks = [];
  let length = 0;
  for (;;) {
    const {done, value} = await reader.read();
    if (done) break;
    length += value.length;
    if (length > MAX_BYTES) { await reader.cancel(); throw new Error(`${url}: exceeds bounded document size`); }
    chunks.push(value);
  }
  if (expectedBytes !== undefined && length !== expectedBytes) throw new Error(`${url}: shard byte count mismatch`);
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

export async function auditPublishedItemIcons(origin) {
  const datasets = requireDatasetCatalog(await readJson(`${origin}/api/datasets`));
  const reports = [];
  for (const descriptor of datasets) {
    const {base} = datasetSource(descriptor, origin);
    const read = (path, bytes) => readJson(`${base}/${path}?dataset=${descriptor.publicationId}`, bytes);
    const manifest = await read('manifest.json');
    const root = await read('items.json');
    const items = [];
    if (root.format === 'mrt-sharded-json-v1') {
      if (root.kind !== 'array' || !Array.isArray(root.parts) || root.parts.length > 256) throw new Error('Invalid item shard inventory');
      const paths = new Set();
      for (const part of root.parts) {
        if (!/^data\/items\/part-\d+\.json$/.test(part.path) || paths.has(part.path) ||
            part.start !== items.length || !Number.isSafeInteger(part.bytes) || part.bytes <= 0 || part.bytes > MAX_BYTES) {
          throw new Error('Invalid item shard descriptor');
        }
        paths.add(part.path);
        const shard = await read(part.path, part.bytes);
        if (!Array.isArray(shard) || shard.length !== part.count) throw new Error('Item shard count mismatch');
        for (const item of shard) items.push(item);
      }
      if (items.length !== root.count) throw new Error('Item inventory count mismatch');
    } else {
      const entries = Array.isArray(root) ? root : root.items;
      if (!Array.isArray(entries)) throw new Error('Invalid item catalog');
      for (const item of entries) items.push(item);
    }
    if (items.length !== manifest.counts.items) throw new Error('Manifest item count mismatch');
    const structured = hasExactGtnhStructuredDataOnlyVisualAssets(manifest.web?.visualAssets);
    const warnings = structured || !items.some(item => !item.icon) ? [] : await read('warnings.json');
    const audit = auditItemIcons(items, warnings, structured);
    reports.push({descriptor, ...audit});
    console.info(`${descriptor.slug}: ${audit.catalogItems} items, ${audit.itemsWithoutIconUrl} without URLs, ${audit.byReason.unexplained} unexplained`);
  }
  return reports;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const origin = process.argv[2] ?? 'https://minecraftrecipetree.craftsmannsoftware.com';
  const output = process.argv[3];
  const url = new URL(origin);
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw new Error('Expected an HTTPS origin without credentials');
  const reports = await auditPublishedItemIcons(url.origin);
  if (output) await writeFile(output, `${JSON.stringify(reports, null, 2)}\n`);
  if (reports.some(report => report.byReason.unexplained > 0)) process.exitCode = 1;
}
