const PACK_ICON_PATH_BY_SLUG: Readonly<Record<string, string>> = Object.freeze({
  meatballcraft: '/pack-icons/meatballcraft.webp',
  'gt-new-horizons': '/pack-icons/gt-new-horizons.webp',
  'multiblock-madness': '/pack-icons/multiblock-madness.webp',
  'multiblock-madness-2': '/pack-icons/multiblock-madness-2.webp',
});

export function datasetPackIconPath(slug: string): string | null {
  return PACK_ICON_PATH_BY_SLUG[slug] ?? null;
}
