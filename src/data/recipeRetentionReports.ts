import {Platform} from 'react-native';
import {accountFetch} from '../account/supabaseClient';
import type {RecipeRef} from '../types';
import type {DatasetDescriptor} from './datasetCatalog';
import {recipeFavoriteClientId} from './recipeFavoriteClient';

const RETENTION_REPORTS_ENDPOINT = Platform.OS === 'web'
  ? '/api/recipe-retention-reports'
  : 'https://minecraftrecipetree.craftsmannsoftware.com/api/recipe-retention-reports';

function isLocalOnlyPack(descriptor: DatasetDescriptor): boolean {
  return /^local-[a-f0-9]{16}$/u.test(descriptor.slug);
}

export async function reportRecipeRetentionOverride(
  descriptor: DatasetDescriptor,
  recipeRef: RecipeRef,
  itemKey: string,
  reusable: boolean,
): Promise<void> {
  if (isLocalOnlyPack(descriptor)) {
    console.info('A local-pack retention override was not sent to the hosted report log.', {
      packSlug: descriptor.slug,
      recipeRef,
      itemKey,
      reusable,
    });
    return;
  }
  const response = await accountFetch(RETENTION_REPORTS_ENDPOINT, {
    method: 'PUT',
    headers: {'Content-Type': 'application/json', Accept: 'application/json'},
    credentials: 'include',
    body: JSON.stringify({
      packSlug: descriptor.slug,
      publicationId: descriptor.publicationId,
      clientId: await recipeFavoriteClientId(),
      recipeRef,
      itemKey,
      reusable,
    }),
  });
  if (!response.ok) {
    throw new Error(`Recipe retention report returned HTTP ${response.status}.`);
  }
}
