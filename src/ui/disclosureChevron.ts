export const EXPANDED_DISCLOSURE_CHEVRON = '⌃';
export const COLLAPSED_DISCLOSURE_CHEVRON = '⌄';

export function disclosureChevron(expanded: boolean): string {
  return expanded ? EXPANDED_DISCLOSURE_CHEVRON : COLLAPSED_DISCLOSURE_CHEVRON;
}
