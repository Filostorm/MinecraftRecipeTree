const PACK_ICON_PATH_BY_SLUG: Readonly<Record<string, string>> = Object.freeze({
  meatballcraft: '/pack-icons/meatballcraft.webp?v=1',
  'gt-new-horizons': '/pack-icons/gt-new-horizons.webp?v=1',
  'multiblock-madness': '/pack-icons/multiblock-madness.webp?v=1',
  'multiblock-madness-2': '/pack-icons/multiblock-madness-2.webp?v=1',
});

export function datasetPackIconPath(slug: string): string | null {
  return PACK_ICON_PATH_BY_SLUG[slug] ?? null;
}
