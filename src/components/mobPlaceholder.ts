const PLACEHOLDER_COLORS = Object.freeze([
  '#624d8f',
  '#376f82',
  '#3f7659',
  '#8a6736',
  '#88494d',
  '#4c568f',
  '#61783d',
]);

/** Stable identifier-derived color; it never depends on array order or runtime randomness. */
export function generatedMobPlaceholderColor(id: string): string {
  let hash = 0;
  for (const character of id) hash = (hash * 31 + character.codePointAt(0)!) | 0;
  return PLACEHOLDER_COLORS[Math.abs(hash) % PLACEHOLDER_COLORS.length];
}

/** Compact deterministic label used in place of omitted third-party sprite artwork. */
export function generatedMobPlaceholderLabel(name: string): string {
  const words = name.trim().split(/\s+/u).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return [...words[0]].slice(0, 2).join('').toUpperCase();
  return `${[...words[0]][0] ?? ''}${[...words.at(-1)!][0] ?? ''}`.toUpperCase() || '?';
}
