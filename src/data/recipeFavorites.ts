import {Platform} from 'react-native';
import type {RecipeRef} from '../types';
import type {DatasetDescriptor} from './datasetCatalog';
import {recipeFavoriteClientId} from './recipeFavoriteClient';
import {accountFetch} from '../account/supabaseClient';

const FAVORITES_ENDPOINT = Platform.OS === 'web'
  ? '/api/recipe-favorites'
  : 'https://minecraftrecipetree.craftsmannsoftware.com/api/recipe-favorites';
const MAX_FAVORITES = 50_000;

export interface CommunityRecipeFavorite {
  itemKey: string;
  recipeRef: RecipeRef;
  count: number;
}

export interface PersonalRecipeFavorite {
  itemKey: string;
  recipeRef: RecipeRef;
  updatedAt: number;
}

export interface RecipeFavoriteLeaderboardEntry {
  displayName: string;
  count: number;
  isAnonymous: boolean;
  isCurrent: boolean;
}

function isLocalOnlyPack(descriptor: DatasetDescriptor): boolean {
  return /^local-[a-f0-9]{16}$/u.test(descriptor.slug);
}

function isRecipeRef(value: unknown): value is RecipeRef {
  return (
    Array.isArray(value) &&
    value.length === 2 &&
    value.every(part => Number.isSafeInteger(part) && part >= 0)
  );
}

export function parseCommunityRecipeFavorites(value: unknown): CommunityRecipeFavorite[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Recipe favorite response is not an object.');
  }
  const record = value as Record<string, unknown>;
  if (Object.keys(record).length !== 1 || !Array.isArray(record.favorites)) {
    throw new Error('Recipe favorite response has an invalid shape.');
  }
  if (record.favorites.length > MAX_FAVORITES) {
    throw new Error('Recipe favorite response is too large.');
  }
  const seen = new Set<string>();
  return record.favorites.map((entry, index) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
      throw new Error(`Recipe favorite ${index} is invalid.`);
    }
    const favorite = entry as Record<string, unknown>;
    if (
      Object.keys(favorite).length !== 3 ||
      typeof favorite.itemKey !== 'string' ||
      favorite.itemKey.length === 0 ||
      favorite.itemKey.length > 512 ||
      seen.has(favorite.itemKey) ||
      !isRecipeRef(favorite.recipeRef) ||
      !Number.isSafeInteger(favorite.count) ||
      (favorite.count as number) < 1
    ) {
      throw new Error(`Recipe favorite ${index} is invalid.`);
    }
    seen.add(favorite.itemKey);
    return {
      itemKey: favorite.itemKey,
      recipeRef: favorite.recipeRef,
      count: favorite.count as number,
    };
  });
}

export async function loadCommunityRecipeFavorites(
  descriptor: DatasetDescriptor,
): Promise<CommunityRecipeFavorite[]> {
  if (isLocalOnlyPack(descriptor)) return [];
  const query = new URLSearchParams({
    packSlug: descriptor.slug,
    publicationId: descriptor.publicationId,
  });
  const response = await fetch(`${FAVORITES_ENDPOINT}?${query}`, {
    headers: {Accept: 'application/json'},
    cache: 'no-store',
  });
  if (!response.ok) throw new Error(`Recipe favorites returned HTTP ${response.status}.`);
  return parseCommunityRecipeFavorites(await response.json());
}

export async function loadRecipeFavoriteLeaderboard(
  descriptor: DatasetDescriptor,
): Promise<RecipeFavoriteLeaderboardEntry[]> {
  if (isLocalOnlyPack(descriptor)) return [];
  const query = new URLSearchParams({
    packSlug: descriptor.slug,
    publicationId: descriptor.publicationId,
  });
  const response = await accountFetch(`${FAVORITES_ENDPOINT}/leaderboard?${query}`, {
    headers: {
      Accept: 'application/json',
      'X-MRT-Favorite-Client': await recipeFavoriteClientId(),
    },
    cache: 'no-store',
    credentials: 'include',
  });
  if (!response.ok) throw new Error(`Recipe favorite leaderboard returned HTTP ${response.status}.`);
  const value = await response.json() as unknown;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Recipe favorite leaderboard response is not an object.');
  }
  const record = value as Record<string, unknown>;
  if (Object.keys(record).length !== 1 || !Array.isArray(record.entries)) {
    throw new Error('Recipe favorite leaderboard response has an invalid shape.');
  }
  if (record.entries.length > 100) {
    throw new Error('Recipe favorite leaderboard response is too large.');
  }
  return record.entries.map((entry, index) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
      throw new Error(`Recipe favorite leaderboard entry ${index} is invalid.`);
    }
    const rankedUser = entry as Record<string, unknown>;
    if (
      Object.keys(rankedUser).length !== 4 ||
      typeof rankedUser.displayName !== 'string' ||
      rankedUser.displayName.length === 0 ||
      rankedUser.displayName.length > 80 ||
      rankedUser.displayName.trim() !== rankedUser.displayName ||
      !Number.isSafeInteger(rankedUser.count) ||
      (rankedUser.count as number) < 1 ||
      typeof rankedUser.isAnonymous !== 'boolean' ||
      typeof rankedUser.isCurrent !== 'boolean'
    ) {
      throw new Error(`Recipe favorite leaderboard entry ${index} is invalid.`);
    }
    return {
      displayName: rankedUser.displayName,
      count: rankedUser.count as number,
      isAnonymous: rankedUser.isAnonymous,
      isCurrent: rankedUser.isCurrent,
    };
  });
}

export async function loadPersonalRecipeFavorites(
  descriptor: DatasetDescriptor,
): Promise<PersonalRecipeFavorite[]> {
  if (isLocalOnlyPack(descriptor)) return [];
  const query = new URLSearchParams({
    packSlug: descriptor.slug,
    publicationId: descriptor.publicationId,
  });
  const response = await accountFetch(`${FAVORITES_ENDPOINT}/mine?${query}`, {
    headers: {Accept: 'application/json'},
    cache: 'no-store',
    credentials: 'include',
  });
  if (!response.ok) throw new Error(`Personal recipe favorites returned HTTP ${response.status}.`);
  const value = await response.json() as unknown;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Personal recipe favorites response is not an object.');
  }
  const record = value as Record<string, unknown>;
  if (Object.keys(record).length !== 1 || !Array.isArray(record.favorites)) {
    throw new Error('Personal recipe favorites response has an invalid shape.');
  }
  if (record.favorites.length > MAX_FAVORITES) {
    throw new Error('Personal recipe favorites response is too large.');
  }
  const seen = new Set<string>();
  return record.favorites.map((entry, index) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
      throw new Error(`Personal recipe favorite ${index} is invalid.`);
    }
    const favorite = entry as Record<string, unknown>;
    if (
      Object.keys(favorite).length !== 3 ||
      typeof favorite.itemKey !== 'string' ||
      favorite.itemKey.length === 0 ||
      favorite.itemKey.length > 512 ||
      seen.has(favorite.itemKey) ||
      !isRecipeRef(favorite.recipeRef) ||
      !Number.isSafeInteger(favorite.updatedAt) ||
      (favorite.updatedAt as number) < 0
    ) {
      throw new Error(`Personal recipe favorite ${index} is invalid.`);
    }
    seen.add(favorite.itemKey);
    return {
      itemKey: favorite.itemKey,
      recipeRef: favorite.recipeRef,
      updatedAt: favorite.updatedAt as number,
    };
  });
}

export async function claimAnonymousRecipeFavorites(
  descriptor: DatasetDescriptor,
  favorites: ReadonlyArray<Pick<PersonalRecipeFavorite, 'itemKey' | 'recipeRef'>>,
): Promise<void> {
  if (isLocalOnlyPack(descriptor)) return;
  const clientId = await recipeFavoriteClientId();
  const chunks = favorites.length > 0
    ? Array.from({length: Math.ceil(favorites.length / 100)}, (_, index) =>
        favorites.slice(index * 100, (index + 1) * 100),
      )
    : [[]];
  for (const chunk of chunks) {
    const response = await accountFetch(`${FAVORITES_ENDPOINT}/claim`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json', Accept: 'application/json'},
      credentials: 'include',
      body: JSON.stringify({
        clientId,
        packSlug: descriptor.slug,
        publicationId: descriptor.publicationId,
        favorites: chunk,
      }),
    });
    if (!response.ok) throw new Error(`Recipe favorite claim returned HTTP ${response.status}.`);
  }
}

export async function updateCommunityRecipeFavorite(
  descriptor: DatasetDescriptor,
  itemKey: string,
  recipeRef: RecipeRef | null,
): Promise<void> {
  if (isLocalOnlyPack(descriptor)) return;
  const response = await accountFetch(FAVORITES_ENDPOINT, {
    method: 'PUT',
    headers: {'Content-Type': 'application/json', Accept: 'application/json'},
    credentials: 'include',
    body: JSON.stringify({
      packSlug: descriptor.slug,
      publicationId: descriptor.publicationId,
      clientId: await recipeFavoriteClientId(),
      itemKey,
      recipeRef,
    }),
  });
  if (!response.ok) throw new Error(`Recipe favorite update returned HTTP ${response.status}.`);
}
