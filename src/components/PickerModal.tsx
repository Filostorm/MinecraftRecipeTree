import React, {useMemo} from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import type {SlotSummary} from '../data/slotSummary';
import {theme} from '../theme';
import {uniformPickerRecipePreviewSize} from '../ui/interfaceZoom';
import type {GraphDirection} from '../graph/direction';
import {pixelated} from './ItemIcon';
import {ItemChip} from './RecipeCard';
import {RecipePreviewImage} from './RecipePreviewImage';
import {groupPickerOptions} from './pickerGroups';

export interface PickerOption {
  label: string;
  /** Stable category identity used by the source picker collapse controls. */
  groupKey?: string;
  /** Human-readable recipe or physical-source category. */
  groupLabel?: string;
  sublabel?: string;
  imageUri?: string;
  imageW?: number;
  imageH?: number;
  inputs?: SlotSummary[];
  outputs?: SlotSummary[];
  prerequisites?: SlotSummary[];
}

export interface PickerGroupProgress {
  loaded: number;
  total: number;
  loading?: boolean;
}

/** Generic chooser used to pick between recipes and drop sources for an item. */
export function PickerModal({
  visible,
  title,
  options,
  direction,
  onDirectionChange,
  rememberSource,
  onRememberSourceChange,
  filterLabel,
  filterHint,
  filterValue,
  onFilterValueChange,
  collapsedGroupKeys,
  onToggleGroup,
  groupProgress,
  onLoadGroup,
  onSelect,
  onClose,
  interfaceZoom = 1,
}: {
  visible: boolean;
  title: string;
  options: PickerOption[];
  direction?: GraphDirection;
  onDirectionChange?: (direction: GraphDirection) => void;
  rememberSource?: boolean;
  onRememberSourceChange?: (remember: boolean) => void;
  filterLabel?: string;
  filterHint?: string;
  filterValue?: boolean;
  onFilterValueChange?: (show: boolean) => void;
  collapsedGroupKeys?: ReadonlySet<string>;
  onToggleGroup?: (groupKey: string) => void;
  groupProgress?: Readonly<Record<string, PickerGroupProgress>>;
  onLoadGroup?: (groupKey: string) => void;
  onSelect: (index: number) => void;
  onClose: () => void;
  /** The web UI scale applied to recipe imagery without shrinking the modal viewport. */
  interfaceZoom?: number;
}) {
  const groups = useMemo(() => groupPickerOptions(options), [options]);
  const collapsedGroups = groups.filter(group => collapsedGroupKeys?.has(group.key));
  const expandedGroups = groups.filter(group => !collapsedGroupKeys?.has(group.key));
  const allCollapsed = groups.length > 0 && collapsedGroups.length === groups.length;
  const toggleGroup = (key: string) => onToggleGroup?.(key);
  const stagedProgress = Object.values(groupProgress ?? {});
  const immediateGroups = groups.filter(group => !groupProgress?.[group.key]);
  const sourceTypeCount = stagedProgress.length + immediateGroups.length;
  const loadedOptionCount =
    stagedProgress.reduce((sum, progress) => sum + progress.loaded, 0) +
    immediateGroups.reduce((sum, group) => sum + group.entries.length, 0);
  const totalOptionCount =
    stagedProgress.reduce((sum, progress) => sum + progress.total, 0) +
    immediateGroups.reduce((sum, group) => sum + group.entries.length, 0);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.card} onPress={() => {}}>
          <Text style={styles.title}>{title}</Text>
          {direction && onDirectionChange ? (
            <View
              accessibilityLabel="Tree direction"
              style={styles.directionTabs}>
              {([
                ['inputs', 'Recipes'],
                ['outputs', 'Usages'],
              ] as const).map(([value, label]) => {
                const selected = direction === value;
                return (
                  <TouchableOpacity
                    key={value}
                    accessibilityRole="button"
                    accessibilityLabel={`${label}, build tree ${value === 'inputs' ? 'toward ingredients' : 'toward products'}`}
                    accessibilityState={{selected}}
                    style={[
                      styles.directionTab,
                      selected && styles.directionTabSelected,
                    ]}
                    onPress={() => onDirectionChange(value)}>
                    <Text
                      style={[
                        styles.directionTabText,
                        selected && styles.directionTabTextSelected,
                      ]}>
                      {label}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          ) : null}
          {onRememberSourceChange && (
            <View style={styles.rememberRow}>
              <View style={styles.rememberCopy}>
                <Text style={styles.rememberTitle}>★ Use automatically in future trees</Text>
              </View>
              <Switch
                accessibilityLabel="Use automatically in future trees"
                value={rememberSource ?? true}
                onValueChange={onRememberSourceChange}
                trackColor={{false: theme.border, true: theme.accent}}
                thumbColor={theme.text}
              />
            </View>
          )}
          {filterLabel && onFilterValueChange && (
            <View style={styles.rememberRow}>
              <View style={styles.rememberCopy}>
                <Text style={styles.filterTitle}>{filterLabel}</Text>
                {filterHint ? <Text style={styles.rememberHint}>{filterHint}</Text> : null}
              </View>
              <Switch
                accessibilityLabel={filterLabel}
                value={filterValue ?? false}
                onValueChange={onFilterValueChange}
                trackColor={{false: theme.border, true: theme.accent}}
                thumbColor={theme.text}
              />
            </View>
          )}
          {groups.length > 1 ? (
            <View style={styles.groupActions}>
              <Text style={styles.groupSummary}>
                {sourceTypeCount} source types · {loadedOptionCount}/{totalOptionCount} loaded
              </Text>
              <TouchableOpacity
                accessibilityRole="button"
                style={styles.groupAction}
                onPress={() => {
                  const shouldExpand = allCollapsed;
                  for (const group of groups) {
                    if (shouldExpand === Boolean(collapsedGroupKeys?.has(group.key))) {
                      toggleGroup(group.key);
                    }
                  }
                }}>
                <Text style={styles.groupActionText}>
                  {allCollapsed ? 'Expand all' : 'Collapse all'}
                </Text>
              </TouchableOpacity>
            </View>
          ) : null}
          <ScrollView
            style={styles.optionsScroll}
            contentContainerStyle={styles.groupList}>
            {options.length === 0 ? (
              <Text style={styles.emptyText}>No standard sources are available.</Text>
            ) : null}
            {collapsedGroups.length > 0 ? (
              <View style={styles.collapsedBubbles}>
                {collapsedGroups.map(group => {
                  const progress = groupProgress?.[group.key];
                  return (
                    <TouchableOpacity
                      key={group.key}
                      accessibilityRole="button"
                      accessibilityState={{expanded: false}}
                      accessibilityLabel={`Expand ${group.label}`}
                      style={styles.collapsedBubble}
                      onPress={() => toggleGroup(group.key)}>
                      <Text style={styles.collapsedBubbleCaret}>▸</Text>
                      <Text style={styles.collapsedBubbleText}>{group.label}</Text>
                      <Text style={styles.collapsedBubbleCount}>
                        {progress ? `${progress.loaded}/${progress.total}` : group.entries.length}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            ) : null}
            {expandedGroups.map(group => {
              const progress = groupProgress?.[group.key];
              const hasMore = Boolean(progress && progress.loaded < progress.total);
              return (
                <View key={group.key} style={styles.group}>
                  <TouchableOpacity
                    accessibilityRole="button"
                    accessibilityState={{expanded: true}}
                    accessibilityLabel={`${group.label}, ${group.entries.length} option${group.entries.length === 1 ? '' : 's'}`}
                    style={styles.groupHeader}
                    onPress={() => toggleGroup(group.key)}>
                    <Text style={styles.groupVisibilityIcon}>👁</Text>
                    <Text style={styles.groupTitle}>{group.label}</Text>
                    <Text style={styles.groupCount}>
                      {progress ? `${progress.loaded}/${progress.total}` : group.entries.length}
                    </Text>
                  </TouchableOpacity>
                  <View style={styles.optionGrid}>
                    {group.entries.map(({option: opt, index: i}) => {
                      const imageSize = opt.imageUri
                        ? uniformPickerRecipePreviewSize(
                            opt.imageW ?? 160,
                            opt.imageH ?? 60,
                            interfaceZoom,
                          )
                        : null;
                      return (
                        <TouchableOpacity key={i} style={styles.option} onPress={() => onSelect(i)}>
                          <Text style={styles.optionLabel}>{opt.label}</Text>
                          {opt.sublabel ? <Text style={styles.optionSub}>{opt.sublabel}</Text> : null}
                          {opt.imageUri && imageSize ? (
                            <RecipePreviewImage
                              uri={opt.imageUri}
                              context={opt.label}
                              style={[imageSize, styles.optionImage, pixelated as object]}
                              resizeMode="contain"
                            />
                          ) : null}
                          {opt.inputs && opt.inputs.length > 0 ? (
                            <View style={styles.ingredientGroup}>
                              <Text style={styles.ingredientLabel}>Inputs</Text>
                              <View style={styles.ingredientChips}>
                                {opt.inputs.map(input => (
                                  <ItemChip
                                    key={`input-${input.tag ?? input.key}`}
                                    itemKey={input.key}
                                    amount={input.amount}
                                    variableAmount={input.variableAmount}
                                    variants={input.variants}
                                    tag={input.tag}
                                    probability={input.probability}
                                    probabilityRole="consume"
                                    interactive={false}
                                  />
                                ))}
                              </View>
                            </View>
                          ) : null}
                          {opt.outputs && opt.outputs.length > 0 ? (
                            <View style={styles.ingredientGroup}>
                              <Text style={styles.ingredientLabel}>Outputs</Text>
                              <View style={styles.ingredientChips}>
                                {opt.outputs.map(output => (
                                  <ItemChip
                                    key={`output-${output.tag ?? output.key}`}
                                    itemKey={output.key}
                                    amount={output.amount}
                                    variableAmount={output.variableAmount}
                                    variants={output.variants}
                                    tag={output.tag}
                                    probability={output.probability}
                                    probabilityRole="produce"
                                    interactive={false}
                                  />
                                ))}
                              </View>
                            </View>
                          ) : null}
                          {opt.prerequisites && opt.prerequisites.length > 0 ? (
                            <View style={styles.ingredientGroup}>
                              <Text style={styles.ingredientLabel}>Required · not consumed</Text>
                              <View style={styles.ingredientChips}>
                                {opt.prerequisites.map(input => (
                                  <ItemChip
                                    key={`prerequisite-${input.tag ?? input.key}`}
                                    itemKey={input.key}
                                    amount={input.amount}
                                    variableAmount={input.variableAmount}
                                    variants={input.variants}
                                    tag={input.tag}
                                    interactive={false}
                                  />
                                ))}
                              </View>
                            </View>
                          ) : null}
                        </TouchableOpacity>
                      );
                    })}
                  </View>
                  {hasMore && onLoadGroup ? (
                    <TouchableOpacity
                      accessibilityRole="button"
                      accessibilityLabel={`Load more ${group.label} recipes`}
                      disabled={progress?.loading}
                      style={[
                        styles.loadMoreGroup,
                        progress?.loading && styles.loadMoreGroupDisabled,
                      ]}
                      onPress={() => onLoadGroup(group.key)}>
                      <Text style={styles.loadMoreGroupText}>
                        {progress?.loading
                          ? `Loading ${group.label}…`
                          : `Load more · ${progress!.total - progress!.loaded} remaining`}
                      </Text>
                    </TouchableOpacity>
                  ) : null}
                </View>
              );
            })}
          </ScrollView>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.65)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
  },
  card: {
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 12,
    width: '100%',
    maxWidth: 920,
    maxHeight: '92%' as never,
    padding: 14,
  },
  title: {color: theme.text, fontSize: 15, fontWeight: '700', marginBottom: 10},
  directionTabs: {
    flexDirection: 'row',
    alignSelf: 'flex-start',
    gap: 6,
    marginBottom: 10,
    padding: 3,
    borderRadius: 9,
    backgroundColor: theme.panelAlt,
  },
  directionTab: {
    minWidth: 94,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 7,
    alignItems: 'center',
  },
  directionTabSelected: {
    backgroundColor: theme.radialRootPanel,
    borderColor: theme.radialRoot,
    borderWidth: 1,
  },
  directionTabText: {
    color: theme.textDim,
    fontSize: 11,
    fontWeight: '700',
  },
  directionTabTextSelected: {color: theme.radialRoot},
  rememberRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
  },
  rememberCopy: {flex: 1},
  rememberTitle: {color: theme.accent, fontSize: 12, fontWeight: '700'},
  filterTitle: {color: theme.text, fontSize: 12, fontWeight: '700'},
  rememberHint: {color: theme.textDim, fontSize: 10, marginTop: 2, lineHeight: 14},
  optionsScroll: {maxHeight: 560, flexShrink: 1},
  groupList: {gap: 8},
  collapsedBubbles: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 7,
  },
  collapsedBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 999,
    backgroundColor: theme.panelAlt,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  collapsedBubbleCaret: {color: theme.accent, fontSize: 11},
  collapsedBubbleText: {color: theme.text, fontSize: 11, fontWeight: '700'},
  collapsedBubbleCount: {color: theme.textDim, fontSize: 9, fontWeight: '700'},
  groupActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 8,
  },
  groupSummary: {color: theme.textDim, fontSize: 11, flex: 1},
  groupAction: {
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 9,
    paddingVertical: 5,
  },
  groupActionText: {color: theme.accent, fontSize: 10, fontWeight: '700'},
  group: {
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 9,
    backgroundColor: theme.panelAlt,
    overflow: 'hidden',
  },
  groupHeader: {
    minHeight: 38,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingHorizontal: 10,
  },
  groupVisibilityIcon: {fontSize: 12, width: 18},
  groupTitle: {color: theme.text, fontSize: 12, fontWeight: '700', flex: 1},
  groupCount: {
    color: theme.textDim,
    fontSize: 10,
    fontWeight: '700',
    minWidth: 24,
    textAlign: 'right',
  },
  optionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'stretch',
    gap: 8,
    padding: 8,
  },
  loadMoreGroup: {
    alignSelf: 'center',
    borderColor: theme.borderLight,
    borderWidth: 1,
    borderRadius: 7,
    marginBottom: 8,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  loadMoreGroupDisabled: {opacity: 0.55},
  loadMoreGroupText: {color: theme.accent, fontSize: 10, fontWeight: '700'},
  emptyText: {
    color: theme.textDim,
    fontSize: 12,
    paddingVertical: 14,
    textAlign: 'center',
    width: '100%',
  },
  option: {
    borderColor: theme.borderLight,
    borderWidth: 1,
    borderRadius: 8,
    backgroundColor: theme.panel,
    padding: 10,
    flexBasis: 280,
    flexGrow: 1,
    maxWidth: 420,
  },
  optionLabel: {color: theme.text, fontSize: 13, fontWeight: '600'},
  optionSub: {color: theme.textDim, fontSize: 11, marginTop: 2},
  optionImage: {marginTop: 8, borderRadius: 4},
  ingredientGroup: {marginTop: 9, gap: 5},
  ingredientLabel: {
    color: theme.textDim,
    fontSize: 10,
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  ingredientChips: {flexDirection: 'row', flexWrap: 'wrap', gap: 5},
});
