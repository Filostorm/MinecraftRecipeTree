import type {CatalogItem} from '../types';
import {PROJECTE_EMC_KEY, PROJECTE_TRANSMUTATION_TABLE_KEY} from './projecteEmc.ts';

export type IconOmissionReason = 'structured-data-only' | 'native-render-empty' | 'emc-table-icon' | 'unexplained';
export interface IconOmission {
  key: string;
  id: string;
  name: string;
  mod: string;
  type: string;
  reason: IconOmissionReason;
}

/** Match exporter evidence, not a guessed item-name or mod allowlist. */
export function auditItemIcons(items: readonly CatalogItem[], warnings: unknown, structuredDataOnly = false) {
  if (!Array.isArray(warnings) || warnings.some(value => typeof value !== 'string')) {
    throw new Error('Icon omission audit requires the exporter warnings array.');
  }
  const emptyKeys = new Set<string>();
  const emptyTypesAndIds = new Set<string>();
  for (const warning of warnings as string[]) {
    const hei = /^UPSTREAM_NATIVE_ICON_UNAVAILABLE ingredient (.+): rendered image is fully transparent; both the exact 16x16 HEI draw and bounded native overscan draw produced no visible pixel; omitting the PNG and JSON icon reference so the viewer uses its explicitly named fallback$/.exec(warning);
    if (hei) emptyKeys.add(hei[1]);
    const rei = /^UPSTREAM_NATIVE_ICON_UNAVAILABLE id=([^ ]+) type=([^ ]+) valueClass=([^ ]+) itemClass=([^ ]+) blockClass=([^ ]+) visiblePixels=0 contract=(.+); exact native 16x16 render has zero visible pixels; omitted PNG\/icon field; named UI fallback used$/.exec(warning);
    // REI catalogs serialize custom ResourceLocation types with '/' instead of ':'.
    if (rei) emptyTypesAndIds.add(`${rei[2].startsWith('minecraft:') ? rei[2].slice(10) : rei[2].replace(':', '/')}|${rei[1]}`);
  }
  const tableHasIcon = items.some(item => item.k === PROJECTE_TRANSMUTATION_TABLE_KEY && !!item.icon);
  const omissions: IconOmission[] = [];
  for (const item of items) {
    if (typeof item.icon === 'string' && item.icon.length > 0) continue;
    const type = item.t ?? 'item';
    const reason: IconOmissionReason = structuredDataOnly ? 'structured-data-only'
      : item.k === PROJECTE_EMC_KEY && tableHasIcon ? 'emc-table-icon'
        : emptyKeys.has(item.k) || emptyTypesAndIds.has(`${type}|${item.id}`) ? 'native-render-empty'
          : 'unexplained';
    omissions.push({key: item.k, id: item.id, name: item.n, mod: item.m, type, reason});
  }
  const byReason: Record<IconOmissionReason, number> = {
    'structured-data-only': 0, 'native-render-empty': 0, 'emc-table-icon': 0, unexplained: 0,
  };
  for (const item of omissions) byReason[item.reason]++;
  return {catalogItems: items.length, itemsWithoutIconUrl: omissions.length, byReason, omissions};
}
