import type {User} from '@supabase/supabase-js';

export interface RecipeTreeUserIdentity {
  id: string;
  displayName: string;
  email: string | null;
  provider: string | null;
}

const UNSAFE_TEXT_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

function usableName(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const name = value.trim();
  if (!name) return null;
  if (name.length > 80 || UNSAFE_TEXT_PATTERN.test(name)) {
    throw new Error('The signed-in account has an invalid display name.');
  }
  return name;
}

function metadataName(metadata: Record<string, unknown> | undefined): string | null {
  if (!metadata) return null;
  const candidates = [
    metadata.display_name,
    metadata.global_name,
    metadata.full_name,
    metadata.name,
    metadata.username,
    metadata.user_name,
    metadata.preferred_username,
  ];
  for (const candidate of candidates) {
    const name = usableName(candidate);
    if (name) return name;
  }
  return null;
}

/** Prefer an app username, then the Discord identity's public username. */
export function recipeTreeUserIdentity(user: User): RecipeTreeUserIdentity {
  const discordIdentity = user.identities?.find(identity => identity.provider === 'discord');
  const provider = discordIdentity
    ? 'discord'
    : typeof user.app_metadata?.provider === 'string'
      ? user.app_metadata.provider
      : null;
  const discordName = discordIdentity
    ? metadataName(discordIdentity.identity_data as Record<string, unknown> | undefined)
    : provider === 'discord'
      ? metadataName(user.user_metadata)
      : null;
  if (provider === 'discord' && !discordName) {
    console.warn('Discord account metadata did not include a usable display name.');
  }
  const customName = usableName(user.user_metadata?.display_name);
  const displayName = customName
    ?? discordName
    ?? metadataName(user.user_metadata)
    ?? usableName(user.email)
    ?? 'Recipe Tree user';
  return {id: user.id, displayName, email: user.email ?? null, provider};
}
