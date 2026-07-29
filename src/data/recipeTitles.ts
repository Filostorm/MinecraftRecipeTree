import type {Recipe} from '../types';

const ENCHANTMENT_NAMES: Record<string, string> = {
  'minecraft:binding_curse': 'Curse of Binding',
  'minecraft:sweeping': 'Sweeping Edge',
  'minecraft:vanishing_curse': 'Curse of Vanishing',
};

interface EnchantmentVariant {
  id: string;
  level: number;
}

function parseEnchantmentVariant(key: string): EnchantmentVariant | null {
  const match = key.match(
    /enchanted_book:\[enchantment\.([^.\]]+)\.([^.\]]+)\.lvl(\d+)\]/,
  );
  if (!match) return null;
  return {id: `${match[1]}:${match[2]}`, level: Number(match[3])};
}

function titleCaseIdentifier(id: string): string {
  const explicit = ENCHANTMENT_NAMES[id];
  if (explicit) return explicit;
  const path = id.split(':').pop() ?? id;
  return path
    .split('_')
    .filter(Boolean)
    .map(word => word[0].toUpperCase() + word.slice(1))
    .join(' ');
}

function romanNumeral(value: number): string {
  const numerals: Array<[number, string]> = [
    [1000, 'M'],
    [900, 'CM'],
    [500, 'D'],
    [400, 'CD'],
    [100, 'C'],
    [90, 'XC'],
    [50, 'L'],
    [40, 'XL'],
    [10, 'X'],
    [9, 'IX'],
    [5, 'V'],
    [4, 'IV'],
    [1, 'I'],
  ];
  let remaining = value;
  let result = '';
  for (const [amount, numeral] of numerals) {
    while (remaining >= amount) {
      result += numeral;
      remaining -= amount;
    }
  }
  return result;
}

function formatLevels(levels: number[]): string {
  const sorted = [...new Set(levels)].sort((a, b) => a - b);
  if (sorted.length === 0 || (sorted.length === 1 && sorted[0] === 1)) return '';
  if (sorted.length === 1) return ` ${romanNumeral(sorted[0])}`;
  const contiguous = sorted.every((level, index) => index === 0 || level === sorted[index - 1] + 1);
  return contiguous
    ? ` ${romanNumeral(sorted[0])}–${romanNumeral(sorted[sorted.length - 1])}`
    : ` ${sorted.map(romanNumeral).join('/')}`;
}

/** Human-readable enchantments encoded in JEI's enchanted-book variant keys. */
export function enchantmentRecipeName(recipe: Recipe): string | null {
  const enchantments = new Map<string, number[]>();
  for (const slot of recipe.in ?? []) {
    for (const [key] of slot) {
      const variant = parseEnchantmentVariant(key);
      if (!variant) continue;
      const levels = enchantments.get(variant.id) ?? [];
      levels.push(variant.level);
      enchantments.set(variant.id, levels);
    }
  }
  if (enchantments.size === 0) return null;
  return [...enchantments.entries()]
    .map(([id, levels]) => `${titleCaseIdentifier(id)}${formatLevels(levels)}`)
    .join(' + ');
}

/** Category plus the specific enchantment, when the recipe applies one. */
export function recipeDisplayTitle(categoryTitle: string, recipe: Recipe): string {
  const enchantment = enchantmentRecipeName(recipe);
  return enchantment ? `${categoryTitle} · ${enchantment}` : categoryTitle;
}
