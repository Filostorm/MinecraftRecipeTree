export interface GroupablePickerOption {
  groupKey?: string;
  groupLabel?: string;
}

export interface IndexedPickerOption<T> {
  index: number;
  option: T;
}

export interface PickerOptionGroup<T> {
  key: string;
  label: string;
  entries: IndexedPickerOption<T>[];
}

/** Groups options without changing their original selection indexes. */
export function groupPickerOptions<T extends GroupablePickerOption>(
  options: T[],
): PickerOptionGroup<T>[] {
  const groups = new Map<string, PickerOptionGroup<T>>();
  options.forEach((option, index) => {
    const key = option.groupKey ?? 'other-sources';
    const label = option.groupLabel ?? 'Other sources';
    const existing = groups.get(key);
    if (existing) {
      existing.entries.push({index, option});
      return;
    }
    groups.set(key, {key, label, entries: [{index, option}]});
  });
  return [...groups.values()];
}
