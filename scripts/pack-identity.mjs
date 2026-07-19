const PACK_IDENTITY_KEYS = Object.freeze([
  'identitySource',
  'instanceName',
  'name',
  'projectId',
  'provider',
  'version',
  'versionId',
]);

export const PACK_NAME_MAX_LENGTH = 120;
export const PACK_VERSION_MAX_LENGTH = 80;
export const PACK_INSTANCE_NAME_MAX_LENGTH = 120;
export const PACK_PROVIDER_MAX_LENGTH = 40;
export const PACK_PROVIDER_ID_MAX_LENGTH = 120;

const IDENTITY_SOURCES = new Set([
  'explicit-request',
  'curseforge',
  'prism',
  'modrinth-index',
  'game-directory',
]);
const PROVIDERS = new Set(['curseforge', 'prism', 'modrinth']);
// C0/C1 controls plus Unicode bidi/zero-width directional controls. These values are legal in
// JSON strings but make identity review, logs, and channel labels ambiguous.
const UNSAFE_TEXT_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function requireExactKnownKeys(value, label) {
  const unknown = Object.keys(value).filter(key => !PACK_IDENTITY_KEYS.includes(key));
  if (unknown.length > 0) {
    throw new Error(`${label} contains unsupported field(s): ${unknown.sort().join(', ')}.`);
  }
}

function requireBoundedText(value, label, maximum) {
  if (
    typeof value !== 'string' ||
    value.length === 0 ||
    [...value].length > maximum ||
    value.trim() !== value ||
    UNSAFE_TEXT_PATTERN.test(value)
  ) {
    throw new Error(
      `${label} must be trimmed, non-empty, at most ${maximum} characters, and contain no control or directional-formatting characters.`,
    );
  }
  return value;
}

function optionalBoundedText(value, label, maximum) {
  return value === undefined ? undefined : requireBoundedText(value, label, maximum);
}

/**
 * Validate the integrity-bound identity emitted by an exporter. Unknown fields fail closed so a
 * newer exporter contract cannot be accidentally published by an older pipeline.
 */
export function requirePackIdentity(value, label = 'manifest.pack') {
  if (!isRecord(value)) throw new Error(`${label} must contain an object.`);
  requireExactKnownKeys(value, label);
  const name = requireBoundedText(value.name, `${label}.name`, PACK_NAME_MAX_LENGTH);
  const identitySource = requireBoundedText(
    value.identitySource,
    `${label}.identitySource`,
    40,
  );
  if (!IDENTITY_SOURCES.has(identitySource)) {
    throw new Error(
      `${label}.identitySource must be one of ${[...IDENTITY_SOURCES].join(', ')}.`,
    );
  }
  const version = optionalBoundedText(
    value.version,
    `${label}.version`,
    PACK_VERSION_MAX_LENGTH,
  );
  const instanceName = optionalBoundedText(
    value.instanceName,
    `${label}.instanceName`,
    PACK_INSTANCE_NAME_MAX_LENGTH,
  );
  const provider = optionalBoundedText(
    value.provider,
    `${label}.provider`,
    PACK_PROVIDER_MAX_LENGTH,
  );
  if (provider !== undefined && !PROVIDERS.has(provider)) {
    throw new Error(`${label}.provider must be one of ${[...PROVIDERS].join(', ')}.`);
  }
  const projectId = optionalBoundedText(
    value.projectId,
    `${label}.projectId`,
    PACK_PROVIDER_ID_MAX_LENGTH,
  );
  const versionId = optionalBoundedText(
    value.versionId,
    `${label}.versionId`,
    PACK_PROVIDER_ID_MAX_LENGTH,
  );
  if ((projectId !== undefined || versionId !== undefined) && provider === undefined) {
    throw new Error(`${label}.provider is required when a provider project/version ID is present.`);
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

export function requirePublishablePackIdentity(value, label = 'manifest.pack') {
  const pack = requirePackIdentity(value, label);
  if (pack.version === undefined) {
    throw new Error(
      `${label}.version is required for a hosted publication. Set packVersion in the exporter request/configuration and export again.`,
    );
  }
  if (pack.identitySource === 'game-directory') {
    throw new Error(
      `${label} was inferred from the game-directory name. Confirm it by setting packName and packVersion explicitly, then export again.`,
    );
  }
  return pack;
}

export function slugForPackName(name) {
  const normalized = requireBoundedText(name, 'Pack name', PACK_NAME_MAX_LENGTH)
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/gu, '-')
    .replace(/^-+|-+$/gu, '')
    .slice(0, 80)
    .replace(/-+$/gu, '');
  if (!normalized) {
    throw new Error(
      'Pack name cannot produce a URL slug; provide --slug using lowercase ASCII letters, digits, and hyphens.',
    );
  }
  return normalized;
}
