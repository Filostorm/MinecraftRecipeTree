import handler from 'vinext/server/app-router-entry';
import {CORE_DATASET_UPLOAD_BASE_PATH, handleCoreDatasetUpload} from './coreDatasetUpload.ts';
import {
  CORE_PUBLIC_ROUTE,
  PREVIEW_PUBLIC_ROUTE,
  handleCoreDatasetRead,
  handlePreviewDatasetRead,
  verifyCommittedDatasetPair,
} from './datasetDelivery.ts';
import {
  DATASET_CHANNEL_ACTIVATION_ROUTE,
  DATASET_CHANNEL_DELETION_ROUTE,
  handleDatasetCatalog,
  handleDatasetChannelActivation,
  handleDatasetChannelDeletion,
} from './datasetRegistry.ts';
import {type DatasetRuntime, methodNotAllowed, noStoreJson} from './datasetRuntime.ts';
import {
  PREVIEW_UPLOAD_BASE_PATH,
  handlePreviewAssetUpload,
} from './previewAssetUpload.ts';

interface LegacyModpackRow {
  id: string;
  name: string;
  minecraft_version: string;
  snapshot_json: string;
  revision: number;
  created_at: number;
  updated_at: number;
}

function serializeLegacyModpack(row: LegacyModpackRow) {
  let snapshot: unknown;
  try {
    snapshot = JSON.parse(row.snapshot_json) as unknown;
  } catch (error) {
    console.error('A legacy modpack row contains invalid snapshot JSON.', {id: row.id, error});
    throw error;
  }
  return {
    id: row.id,
    name: row.name,
    minecraftVersion: row.minecraft_version,
    snapshot,
    revision: row.revision,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

async function handleLegacyModpackApi(
  request: Request,
  runtime: DatasetRuntime,
  pathname: string,
): Promise<Response> {
  if (request.method === 'POST' || request.method === 'PATCH' || request.method === 'DELETE') {
    console.warn('An unauthenticated legacy modpack mutation was refused.', {
      method: request.method,
      pathname,
    });
    return noStoreJson(
      {error: 'Modpack mutations are disabled; use authenticated immutable publication activation.'},
      403,
    );
  }
  if (request.method !== 'GET') return methodNotAllowed('GET');
  if (pathname !== '/api/modpacks') return noStoreJson({error: 'Not found.'}, 404);
  const db = runtime.DB;
  if (!db) {
    console.error('Legacy modpack GET cannot read because the DB binding is unavailable.');
    return noStoreJson({error: 'Modpack storage is unavailable.'}, 503);
  }
  try {
    const rows = await db
      .prepare('SELECT * FROM modpacks ORDER BY updated_at DESC')
      .all<LegacyModpackRow>();
    if (!rows.success) throw new Error('D1 reported an unsuccessful legacy modpack query.');
    return noStoreJson({modpacks: (rows.results ?? []).map(serializeLegacyModpack)});
  } catch (error) {
    console.error('Legacy modpack GET failed.', error);
    return noStoreJson({error: 'Modpack storage is unavailable.'}, 503);
  }
}

const worker = {
  async fetch(
    request: Request,
    env: Parameters<typeof handler.fetch>[1],
    ctx: Parameters<typeof handler.fetch>[2],
  ): Promise<Response> {
    const runtime = (env ?? {}) as DatasetRuntime;
    try {
      const url = new URL(request.url);

      if (url.pathname.startsWith(PREVIEW_UPLOAD_BASE_PATH)) {
        return await handlePreviewAssetUpload(request, runtime, url);
      }
      if (url.pathname.startsWith(CORE_DATASET_UPLOAD_BASE_PATH)) {
        return await handleCoreDatasetUpload(request, runtime, url);
      }
      if (DATASET_CHANNEL_ACTIVATION_ROUTE.test(url.pathname)) {
        return await handleDatasetChannelActivation(
          request,
          runtime,
          url,
          (publicationId, previewAssetSetId) =>
            verifyCommittedDatasetPair(runtime, publicationId, previewAssetSetId),
        );
      }
      if (DATASET_CHANNEL_DELETION_ROUTE.test(url.pathname)) {
        return await handleDatasetChannelDeletion(request, runtime, url);
      }
      if (url.pathname === '/api/datasets') {
        return await handleDatasetCatalog(request, runtime);
      }
      if (url.pathname === '/api/modpacks' || url.pathname.startsWith('/api/modpacks/')) {
        return await handleLegacyModpackApi(request, runtime, url.pathname);
      }

      const coreMatch = CORE_PUBLIC_ROUTE.exec(url.pathname);
      if (coreMatch) {
        return await handleCoreDatasetRead(request, runtime, url, coreMatch, ctx);
      }
      const previewMatch = PREVIEW_PUBLIC_ROUTE.exec(url.pathname);
      if (previewMatch) {
        return await handlePreviewDatasetRead(request, runtime, url, previewMatch, ctx);
      }

      // These paths depended on a process-global PREVIEW_ASSET_SET_ID/static snapshot. Keeping an
      // explicit tombstone prevents old clients from silently mixing datasets after migration.
      if (
        url.pathname.startsWith('/dataset/exports/') ||
        url.pathname.startsWith('/dataset/previews/')
      ) {
        console.warn('A retired single-dataset route was requested.', {pathname: url.pathname});
        return new Response('Single-dataset route retired; refresh the application', {
          status: 410,
          headers: {'Cache-Control': 'no-store'},
        });
      }

      const response = await handler.fetch(request, env, ctx);
      if (response.headers.has('cache-control')) return response;
      const headers = new Headers(response.headers);
      headers.set('Cache-Control', 'no-store');
      return new Response(request.method === 'HEAD' ? null : response.body, {
        status: response.status,
        statusText: response.statusText,
        headers,
      });
    } catch (error) {
      console.error('Minecraft Recipe Tree request failed closed.', error);
      return noStoreJson({error: 'Request failed.'}, 500);
    }
  },
};

export default worker;
