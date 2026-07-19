import {isBulkIngredient} from '../data/ingredientQuantities.ts';
import {treeTotalIdentity} from './treeTotals.ts';
import type {TreeTotal, TreeTotals} from './treeTotals.ts';

function csvCell(value: string | number): string {
  const text = String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function buildTreeTotalsCsv(
  totals: TreeTotals,
  nameFor: (key: string, tag?: string) => string,
): string {
  const rows: Array<Array<string | number>> = [
    ['section', 'resource', 'logical_identity', 'amount', 'unit', 'variants'],
  ];
  const addSection = (section: string, entries: TreeTotal[]) => {
    for (const total of entries) {
      rows.push([
        section,
        nameFor(total.key, total.tag),
        treeTotalIdentity(total),
        total.amount ?? 'unknown',
        isBulkIngredient(total.key) ? 'mB' : 'items',
        total.variants,
      ]);
    }
  };

  addSection('input', totals.inputs);
  addSection('required_not_consumed', totals.prerequisites);
  addSection('byproduct_used', totals.byproductCredits);
  addSection('byproduct_remaining', totals.byproducts);
  return `${rows.map(row => row.map(csvCell).join(',')).join('\r\n')}\r\n`;
}

export function safeExportFilename(value: string): string {
  const normalized = value
    .normalize('NFKD')
    .replace(/[^a-zA-Z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();
  return normalized || 'recipe-tree';
}

export function downloadBlob(filename: string, blob: Blob): void {
  if (typeof document === 'undefined') {
    throw new Error('File export is only available in a web browser.');
  }
  const url = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } catch (error) {
    URL.revokeObjectURL(url);
    throw error;
  }
  // Safari may not begin consuming the object URL until after the click task returns.
  globalThis.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

export async function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  const response = await fetch(dataUrl);
  if (!response.ok) {
    throw new Error(`Generated image data could not be materialized (HTTP ${response.status}).`);
  }
  return response.blob();
}
