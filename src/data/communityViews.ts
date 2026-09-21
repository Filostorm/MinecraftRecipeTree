import {Platform} from 'react-native';
import type {DatasetDescriptor} from './datasetCatalog';

const origin = Platform.OS === 'web' ? '' : 'https://minecraftrecipetree.craftsmannsoftware.com';
export function communityViewsUrl(pack: DatasetDescriptor): string {
  return `${origin}/api/item-views?${new URLSearchParams({packSlug: pack.slug, publicationId: pack.publicationId})}`;
}
export async function loadCommunityViews(pack: DatasetDescriptor, signal: AbortSignal): Promise<string[]> {
  if (pack.slug.startsWith('local-')) return [];
  const response = await fetch(communityViewsUrl(pack), {signal, credentials: 'omit'});
  if (!response.ok) throw new Error(`Community popularity returned HTTP ${response.status}.`);
  const body = await response.json();
  if (!Array.isArray(body.items) || body.items.length > 800 || body.items.some((key: unknown) => typeof key !== 'string' || key.length > 512)) throw new Error('Invalid popularity ranking.');
  return body.items;
}

/** Anonymous aggregate counts only: no account ID, recipe selections or tree contents. */
export async function sendCommunityViews(pack: DatasetDescriptor, keys: string[]): Promise<void> {
  const response = await fetch(communityViewsUrl(pack), {
    method: 'POST', credentials: 'omit', keepalive: Platform.OS === 'web',
    headers: {'Content-Type': 'application/json', ...(Platform.OS === 'web' ? {} : {Origin: origin})},
    body: JSON.stringify(keys),
  });
  if (!response.ok) throw new Error(`Community view batch returned HTTP ${response.status}.`);
}
