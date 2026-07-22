const CONTENT_ID_PATTERN = /^[a-f0-9]{64}$/;
const PROFILE_PATTERN = /^[a-z0-9]+(?:-[a-z0-9.]+)*$/;
const UNSAFE_IDENTITY_TEXT_PATTERN =
  /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

export const GTNH_PROFILE = 'gtnh-1.7.10';
export const GTNH_SLUG = 'gt-new-horizons';
export const GTNH_PACK_NAME = 'GT New Horizons';
export const GTNH_PACK_VERSION = '2.8.4';
export const GTNH_MINECRAFT_VERSION = '1.7.10';
export const GTNH_STRUCTURED_DATA_ONLY_POLICY = 'gtnh-structured-data-only-v1';
export const GTNH_ATTRIBUTION = Object.freeze({
  sourceUrl: 'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4',
  projectUrl: 'https://www.gtnewhorizons.com/',
  licenseIdentifier: 'CC BY-NC-SA 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
});

const PACK_KEYS = Object.freeze([
  'identitySource',
  'instanceName',
  'name',
  'projectId',
  'provider',
  'version',
  'versionId',
]);
const PACK_IDENTITY_SOURCES = new Set([
  'explicit-request',
  'curseforge',
  'prism',
  'modrinth-index',
  'game-directory',
]);
const PACK_PROVIDERS = new Set(['curseforge', 'prism', 'modrinth']);

interface DatasetAttribution {
  sourceUrl: string;
  projectUrl: string;
  licenseIdentifier: string;
  licenseUrl: string;
}

interface DatasetPackIdentity {
  name: string;
  version?: string;
  identitySource: string;
  instanceName?: string;
  provider?: string;
  projectId?: string;
  versionId?: string;
}

export interface CommittedDatasetIdentity {
  publicationId: string;
  minecraft: string;
  profile?: string;
  publicationPolicy?: string;
  pack?: DatasetPackIdentity;
  attribution?: DatasetAttribution;
}

export interface ActivationIdentityLabels {
  displayName: string;
  minecraftVersion: string;
  packVersion: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, expected: readonly string[]): boolean {
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function requireBoundedText(value: unknown, label: string, maximum: number): string {
  if (
    typeof value !== 'string' ||
    [...value].length === 0 ||
    [...value].length > maximum ||
    value.trim() !== value ||
    UNSAFE_IDENTITY_TEXT_PATTERN.test(value)
  ) {
    throw new Error(label + ' is not a safe, bounded identity string.');
  }
  return value;
}

function requireOptionalBoundedText(
  value: unknown,
  label: string,
  maximum: number,
): string | undefined {
  return value === undefined ? undefined : requireBoundedText(value, label, maximum);
}

function requirePackIdentity(value: unknown): DatasetPackIdentity | undefined {
  if (value === undefined) return undefined;
  if (!isRecord(value) || Object.keys(value).some(key => !PACK_KEYS.includes(key))) {
    throw new Error('Committed dataset manifest.pack has unsupported or invalid fields.');
  }
  const name = requireBoundedText(value.name, 'Committed dataset manifest.pack.name', 120);
  const version = requireOptionalBoundedText(
    value.version,
    'Committed dataset manifest.pack.version',
    80,
  );
  const identitySource = requireBoundedText(
    value.identitySource,
    'Committed dataset manifest.pack.identitySource',
    40,
  );
  if (!PACK_IDENTITY_SOURCES.has(identitySource)) {
    throw new Error('Committed dataset manifest.pack.identitySource is unsupported.');
  }
  const instanceName = requireOptionalBoundedText(
    value.instanceName,
    'Committed dataset manifest.pack.instanceName',
    120,
  );
  const provider = requireOptionalBoundedText(
    value.provider,
    'Committed dataset manifest.pack.provider',
    40,
  );
  if (provider !== undefined && !PACK_PROVIDERS.has(provider)) {
    throw new Error('Committed dataset manifest.pack.provider is unsupported.');
  }
  const projectId = requireOptionalBoundedText(
    value.projectId,
    'Committed dataset manifest.pack.projectId',
    120,
  );
  const versionId = requireOptionalBoundedText(
    value.versionId,
    'Committed dataset manifest.pack.versionId',
    120,
  );
  if ((projectId !== undefined || versionId !== undefined) && provider === undefined) {
    throw new Error('Committed dataset manifest.pack.provider is required with provider IDs.');
  }
  return Object.freeze({
    name,
    ...(version === undefined ? {} : {version}),
    identitySource,
    ...(instanceName === undefined ? {} : {instanceName}),
    ...(provider === undefined ? {} : {provider}),
    ...(projectId === undefined ? {} : {projectId}),
    ...(versionId === undefined ? {} : {versionId}),
  });
}

function requireAttribution(value: unknown): DatasetAttribution | undefined {
  if (value === undefined) return undefined;
  const keys = Object.keys(GTNH_ATTRIBUTION);
  if (!isRecord(value) || !hasExactKeys(value, keys)) {
    throw new Error('Committed dataset manifest.attribution violates the exact metadata shape.');
  }
  const sourceUrl = requireBoundedText(value.sourceUrl, 'manifest.attribution.sourceUrl', 2048);
  const projectUrl = requireBoundedText(value.projectUrl, 'manifest.attribution.projectUrl', 2048);
  const licenseIdentifier = requireBoundedText(
    value.licenseIdentifier,
    'manifest.attribution.licenseIdentifier',
    160,
  );
  const licenseUrl = requireBoundedText(value.licenseUrl, 'manifest.attribution.licenseUrl', 2048);
  for (const [label, candidate] of [
    ['sourceUrl', sourceUrl],
    ['projectUrl', projectUrl],
    ['licenseUrl', licenseUrl],
  ] as const) {
    let parsed: URL;
    try {
      parsed = new URL(candidate);
    } catch {
      throw new Error('Committed dataset manifest.attribution.' + label + ' is not a URL.');
    }
    if (parsed.protocol !== 'https:' || parsed.username || parsed.password) {
      throw new Error(
        'Committed dataset manifest.attribution.' + label + ' must be an HTTPS URL without credentials.',
      );
    }
  }
  return Object.freeze({sourceUrl, projectUrl, licenseIdentifier, licenseUrl});
}

export function requireCommittedDatasetIdentity(
  value: unknown,
  expectedPublicationId: string,
): CommittedDatasetIdentity {
  if (!isRecord(value)) throw new Error('Committed dataset manifest.json must contain an object.');
  if (
    typeof value.publicationId !== 'string' ||
    !CONTENT_ID_PATTERN.test(value.publicationId) ||
    value.publicationId !== expectedPublicationId
  ) {
    throw new Error('Committed dataset manifest.json publicationId does not match its immutable route.');
  }
  const minecraft = requireBoundedText(value.minecraft, 'Committed dataset manifest.minecraft', 40);
  const profile = requireOptionalBoundedText(
    value.profile,
    'Committed dataset manifest.profile',
    80,
  );
  if (profile !== undefined && !PROFILE_PATTERN.test(profile)) {
    throw new Error('Committed dataset manifest.profile is not canonical.');
  }
  const publicationPolicy = requireOptionalBoundedText(
    value.publicationPolicy,
    'Committed dataset manifest.publicationPolicy',
    120,
  );
  if (
    publicationPolicy !== undefined &&
    publicationPolicy !== GTNH_STRUCTURED_DATA_ONLY_POLICY
  ) {
    throw new Error('Committed dataset manifest.publicationPolicy is unsupported.');
  }
  return Object.freeze({
    publicationId: value.publicationId,
    minecraft,
    ...(profile === undefined ? {} : {profile}),
    ...(publicationPolicy === undefined ? {} : {publicationPolicy}),
    ...(value.pack === undefined ? {} : {pack: requirePackIdentity(value.pack)}),
    ...(value.attribution === undefined ? {} : {attribution: requireAttribution(value.attribution)}),
  });
}

function exactGtnhAttribution(value: DatasetAttribution | undefined): boolean {
  return (
    value !== undefined &&
    Object.entries(GTNH_ATTRIBUTION).every(([key, expected]) =>
      value[key as keyof DatasetAttribution] === expected)
  );
}

function immutableIdentityClaimsGtnh(identity: CommittedDatasetIdentity): boolean {
  return (
    identity.profile === GTNH_PROFILE ||
    identity.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_POLICY ||
    identity.pack?.name === GTNH_PACK_NAME ||
    identity.attribution?.sourceUrl === GTNH_ATTRIBUTION.sourceUrl ||
    identity.attribution?.projectUrl === GTNH_ATTRIBUTION.projectUrl
  );
}

export function requireExactGtnhActivationBinding(
  slug: string,
  labels: ActivationIdentityLabels,
  identity: CommittedDatasetIdentity,
): void {
  const activationClaimsGtnh = slug === GTNH_SLUG || labels.displayName === GTNH_PACK_NAME;
  if (!activationClaimsGtnh && !immutableIdentityClaimsGtnh(identity)) return;
  const exactPack = identity.pack;
  if (
    identity.profile !== GTNH_PROFILE ||
    identity.minecraft !== GTNH_MINECRAFT_VERSION ||
    !(
      identity.publicationPolicy === undefined ||
      identity.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_POLICY
    ) ||
    !exactPack ||
    !hasExactKeys(exactPack as unknown as Record<string, unknown>, [
      'name',
      'version',
      'identitySource',
    ]) ||
    exactPack.name !== GTNH_PACK_NAME ||
    exactPack.version !== GTNH_PACK_VERSION ||
    exactPack.identitySource !== 'explicit-request' ||
    !exactGtnhAttribution(identity.attribution)
  ) {
    throw new Error(
      'GTNH activation requires its exact immutable profile, pack, Minecraft, attribution, ' +
        'and either runtime-rendered visuals or the legacy structured-data-only policy.',
    );
  }
  if (
    slug !== GTNH_SLUG ||
    labels.displayName !== GTNH_PACK_NAME ||
    labels.minecraftVersion !== GTNH_MINECRAFT_VERSION ||
    labels.packVersion !== GTNH_PACK_VERSION
  ) {
    throw new Error('GTNH activation labels do not match the exact immutable GTNH identity.');
  }
}
