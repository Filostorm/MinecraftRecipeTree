import React, {useEffect, useMemo, useState} from 'react';
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
import {useData} from '../data/DataContext';
import {
  AUTOMATED_SHAPED_CATEGORY_ID,
  compareRecipeCategories,
  isDefaultDisabledRecipeCategory,
} from '../data/recipeCategories';
import {slotSummary} from '../data/slotSummary';
import {theme} from '../theme';
import {Recipe, RecipeRef} from '../types';
import {useUi} from '../ui/UiContext';
import {DropList, DropRow, formatDropStat} from './DropList';
import {ItemIcon} from './ItemIcon';
import {MobSprite} from './MobSprite';
import {ItemChip, RecipeCard} from './RecipeCard';

const PAGE = 15;

function recipeRefKey([categoryIndex, recipeIndex]: RecipeRef): string {
  return `${categoryIndex}:${recipeIndex}`;
}

function toolLabel(tool: string): string {
  return tool === 'hand' ? 'bare hand' : tool.split(':').pop()!.replace(/_/g, ' ');
}

export function ItemDetailModal() {
  const data = useData();
  const {itemStack, popItem, closeItems} = useUi();
  const key = itemStack[itemStack.length - 1];
  /** 'p' | 'u' | 'd' | a secondary category index */
  const [side, setSide] = useState<'p' | 'u' | 'd' | number>('p');

  useEffect(() => setSide('p'), [key]);

  if (!key) return null;
  const item = data.itemsByKey.get(key);
  const entry = data.index[key];
  const visible = (refs?: RecipeRef[]) =>
    (refs ?? []).filter(r => !data.metaCategories.has(r[0]));
  const produced = visible(entry?.p).filter(r => !data.secondaryCategories.has(r[0]));
  const used = visible(entry?.u).filter(r => !data.secondaryCategories.has(r[0]));
  // Secondary categories (anvil, smithing, trading): a repair/trade both "produces"
  // and "uses" the item — merge produced+used per category and dedupe.
  const secondarySeen = new Set<string>();
  const secondaryByCat = new Map<number, RecipeRef[]>();
  for (const r of [...visible(entry?.p), ...visible(entry?.u)]) {
    if (!data.secondaryCategories.has(r[0])) continue;
    const k = `${r[0]}:${r[1]}`;
    if (secondarySeen.has(k)) continue;
    secondarySeen.add(k);
    const list = secondaryByCat.get(r[0]) ?? [];
    list.push(r);
    secondaryByCat.set(r[0], list);
  }
  const secondaryGroups = [...secondaryByCat.entries()]
    .map(([catIdx, groupRefs]) => ({
      catIdx,
      title: data.categories[catIdx]?.title ?? 'Other',
      refs: groupRefs,
    }))
    .sort((a, b) => a.catIdx - b.catIdx);
  const refs =
    side === 'p'
      ? produced
      : side === 'u'
        ? used
        : typeof side === 'number'
          ? secondaryGroups.find(g => g.catIdx === side)?.refs ?? []
          : [];

  const blockDrop = data.blockDrops[key];
  const droppedBy = data.droppedByMobs.get(key) ?? [];
  const minedFrom = data.minedFrom.get(key) ?? [];
  const dropsCount = (blockDrop ? blockDrop.drops.length + (blockDrop.silk?.length ?? 0) : 0)
    + droppedBy.length + minedFrom.length;

  return (
    <Modal visible transparent animationType="fade" onRequestClose={popItem}>
      <Pressable style={styles.backdrop} onPress={closeItems}>
        <Pressable style={styles.card} onPress={() => {}}>
          <View style={styles.header}>
            {itemStack.length > 1 && (
              <TouchableOpacity onPress={popItem} style={styles.headerBtn}>
                <Text style={styles.headerBtnText}>‹ back</Text>
              </TouchableOpacity>
            )}
            <View style={{flex: 1}} />
            <TouchableOpacity onPress={closeItems} style={styles.headerBtn}>
              <Text style={styles.headerBtnText}>✕</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.titleRow}>
            <ItemIcon item={item} itemKey={key} size={48} />
            <View style={{flex: 1, marginLeft: 12}}>
              <Text style={styles.title}>{item?.n ?? key}</Text>
              <Text style={styles.subtitle} numberOfLines={1}>
                {item?.id ?? key} · {data.manifest.mods?.[item?.m ?? ''] ?? item?.m ?? '?'}
                {item?.t ? ` · ${item.t}` : ''}
              </Text>
            </View>
          </View>

          <View style={styles.tabsRow}>
            <SideTab label={`Recipes (${produced.length})`} active={side === 'p'} onPress={() => setSide('p')} />
            <SideTab label={`Usages (${used.length})`} active={side === 'u'} onPress={() => setSide('u')} />
            {secondaryGroups.map(g => (
              <SideTab
                key={g.catIdx}
                label={`${g.title} (${g.refs.length})`}
                active={side === g.catIdx}
                onPress={() => setSide(g.catIdx)}
              />
            ))}
            {dropsCount > 0 && (
              <SideTab label={`Drops (${dropsCount})`} active={side === 'd'} onPress={() => setSide('d')} />
            )}
          </View>

          <ScrollView style={styles.body} contentContainerStyle={{paddingBottom: 16}}>
            {side === 'd' ? (
              <View>
                {blockDrop && (
                  <DropList
                    title={`Breaking this block drops (${toolLabel(blockDrop.tool)})`}
                    drops={blockDrop.drops}
                  />
                )}
                {blockDrop?.silk && <DropList title="With silk touch" drops={blockDrop.silk} />}
                {droppedBy.length > 0 && (
                  <View style={styles.dropSection}>
                    <Text style={styles.dropTitle}>Dropped by mobs</Text>
                    {droppedBy.map(({mob, stat}) => (
                      <View key={mob.id} style={styles.mobRow}>
                        <View style={styles.mobChip}>
                          <MobSprite mob={mob} size={26} animate={false} />
                          <Text style={styles.mobName} numberOfLines={1}>
                            {mob.n}
                          </Text>
                        </View>
                        <Text style={styles.mobStat}>{formatDropStat(stat)}</Text>
                      </View>
                    ))}
                  </View>
                )}
                {minedFrom.length > 0 && (
                  <View style={styles.dropSection}>
                    <Text style={styles.dropTitle}>Mined from</Text>
                    {minedFrom.slice(0, 40).map(({blockKey, stat}) => (
                      <View key={blockKey} style={styles.mobRow}>
                        <ItemChip itemKey={blockKey} />
                        <Text style={styles.mobStat}>{formatDropStat(stat)}</Text>
                      </View>
                    ))}
                  </View>
                )}
              </View>
            ) : refs.length === 0 ? (
              <Text style={styles.emptyText}>
                {side === 'p' ? 'Nothing crafts this item.' : side === 'u' ? 'Not used in any recipe.' : 'Nothing here.'}
              </Text>
            ) : (
              <RefsList key={`${key}:${side}`} refs={refs} />
            )}
          </ScrollView>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

function SideTab({label, active, onPress}: {label: string; active: boolean; onPress: () => void}) {
  return (
    <TouchableOpacity onPress={onPress} style={[styles.sideTab, active && styles.sideTabActive]}>
      <Text style={[styles.sideTabText, active && styles.sideTabTextActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

/** Loads only the bounded recipe shards containing the currently visible references. */
function RefsList({refs}: {refs: RecipeRef[]}) {
  const data = useData();
  const {openRecipeInGraph} = useUi();
  const [visible, setVisible] = useState(PAGE);
  const [categoryFilter, setCategoryFilter] = useState<number | null>(null);
  const [sortMode, setSortMode] = useState<'type' | 'source'>('type');
  const [showAutomatedShaped, setShowAutomatedShaped] = useState(false);
  const [recipesByRef, setRecipesByRef] = useState<Map<string, Recipe>>(() => new Map());
  const [availableCardWidth, setAvailableCardWidth] = useState<number | null>(null);

  const categoryGroups = useMemo(() => {
    const counts = new Map<number, number>();
    for (const [catIdx] of refs) counts.set(catIdx, (counts.get(catIdx) ?? 0) + 1);
    return [...counts.entries()]
      .map(([catIdx, count]) => ({catIdx, count, category: data.categories[catIdx]}))
      .filter(group => Boolean(group.category))
      .sort((a, b) => compareRecipeCategories(a.category!, b.category!));
  }, [refs, data.categories]);
  const automatedShapedCount =
    categoryGroups.find(group => group.category?.id === AUTOMATED_SHAPED_CATEGORY_ID)?.count ?? 0;
  const visibleCategoryGroups = categoryGroups.filter(
    group => showAutomatedShaped || !isDefaultDisabledRecipeCategory(group.category),
  );
  const filteredRefs = useMemo(() => {
    const filtered = refs.filter(([catIdx]) => {
      const category = data.categories[catIdx];
      if (!showAutomatedShaped && isDefaultDisabledRecipeCategory(category)) return false;
      return categoryFilter == null || catIdx === categoryFilter;
    });
    if (sortMode === 'source') return filtered;
    return [...filtered].sort(([aCat, aRecipe], [bCat, bRecipe]) => {
      const a = data.categories[aCat];
      const b = data.categories[bCat];
      if (!a || !b) return aCat - bCat || aRecipe - bRecipe;
      return compareRecipeCategories(a, b) || aRecipe - bRecipe;
    });
  }, [refs, data.categories, categoryFilter, showAutomatedShaped, sortMode]);
  const shown = useMemo(() => filteredRefs.slice(0, visible), [filteredRefs, visible]);

  useEffect(() => setVisible(PAGE), [categoryFilter, showAutomatedShaped, sortMode]);

  // Retain resolved cards across pagination/filter changes, while the data layer keeps the
  // underlying parsed-shard cache bounded.
  useEffect(() => {
    const missing = shown.filter(ref => !recipesByRef.has(recipeRefKey(ref)));
    if (missing.length === 0) return;
    let alive = true;
    (async () => {
      try {
        const loaded = await data.getRecipes(missing);
        if (!alive) return;
        setRecipesByRef(prev => {
          const next = new Map(prev);
          missing.forEach((ref, index) => next.set(recipeRefKey(ref), loaded[index]));
          return next;
        });
      } catch (error) {
        console.error('The visible recipe references could not be displayed.', error);
      }
    })();
    return () => {
      alive = false;
    };
  }, [shown, recipesByRef, data]);

  return (
    <View
      style={styles.recipeList}
      onLayout={event => {
        const measuredWidth = Math.floor(event.nativeEvent.layout.width);
        if (!Number.isFinite(measuredWidth) || measuredWidth <= 0) {
          console.error('Recipe list reported an invalid available width.', {measuredWidth});
          return;
        }
        setAvailableCardWidth(current =>
          current === measuredWidth ? current : measuredWidth,
        );
      }}>
      {categoryGroups.length > 1 && (
        <View style={styles.recipeFilters}>
          <View style={styles.filterHeader}>
            <Text style={styles.filterTitle}>Crafting type</Text>
            <View style={styles.sortControls}>
              <Text style={styles.sortLabel}>Sort</Text>
              <FilterChip
                label="Type"
                active={sortMode === 'type'}
                onPress={() => setSortMode('type')}
              />
              <FilterChip
                label="Source order"
                active={sortMode === 'source'}
                onPress={() => setSortMode('source')}
              />
            </View>
          </View>
          <View style={styles.filterChips}>
            <FilterChip
              label={`All (${visibleCategoryGroups.reduce((sum, group) => sum + group.count, 0)})`}
              active={categoryFilter == null}
              onPress={() => setCategoryFilter(null)}
            />
            {visibleCategoryGroups.map(group => (
              <FilterChip
                key={group.catIdx}
                label={`${group.category!.title} (${group.count})`}
                active={categoryFilter === group.catIdx}
                onPress={() => setCategoryFilter(group.catIdx)}
              />
            ))}
          </View>
          {automatedShapedCount > 0 && (
            <View style={styles.disabledTypeRow}>
              <View style={styles.disabledTypeCopy}>
                <Text style={styles.disabledTypeTitle}>Automated Shaped Crafting</Text>
                <Text style={styles.disabledTypeHint}>
                  Hidden by default · {automatedShapedCount} duplicate recipes
                </Text>
              </View>
              <Switch
                accessibilityLabel="Show Automated Shaped Crafting recipes"
                value={showAutomatedShaped}
                onValueChange={show => {
                  setShowAutomatedShaped(show);
                  if (!show) setCategoryFilter(null);
                }}
                trackColor={{false: theme.border, true: theme.accent}}
                thumbColor={theme.text}
              />
            </View>
          )}
        </View>
      )}
      {filteredRefs.length === 0 ? (
        <Text style={styles.emptyText}>No recipes match this crafting type.</Text>
      ) : null}
      {shown.map(([catIdx, recipeIdx]) => {
        const cat = data.categories[catIdx];
        const recipe = recipesByRef.get(recipeRefKey([catIdx, recipeIdx]));
        const outputKey = recipe ? slotSummary(recipe.out)[0]?.key : undefined;
        if (!cat) return null;
        return (
          <View key={`${catIdx}-${recipeIdx}`}>
            {recipe && availableCardWidth !== null ? (
              <RecipeCard
                recipe={recipe}
                dir={cat.dir}
                catTitle={cat.title}
                availableCardWidth={availableCardWidth}
                onPress={
                  outputKey
                    ? () => openRecipeInGraph(outputKey, [catIdx, recipeIdx])
                    : undefined
                }
              />
            ) : recipe ? (
              <Text style={styles.loadingText}>measuring {cat.title} layout…</Text>
            ) : (
              <Text style={styles.loadingText}>loading {cat.title}…</Text>
            )}
          </View>
        );
      })}
      {filteredRefs.length > visible && (
        <TouchableOpacity style={styles.moreBtn} onPress={() => setVisible(v => v + PAGE)}>
          <Text style={styles.moreBtnText}>Show {Math.min(PAGE, filteredRefs.length - visible)} more of {filteredRefs.length - visible}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

function FilterChip({label, active, onPress}: {label: string; active: boolean; onPress: () => void}) {
  return (
    <TouchableOpacity
      accessibilityRole="button"
      accessibilityState={{selected: active}}
      onPress={onPress}
      style={[styles.filterChip, active && styles.filterChipActive]}>
      <Text style={[styles.filterChipText, active && styles.filterChipTextActive]}>{label}</Text>
    </TouchableOpacity>
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
    maxWidth: 760,
    maxHeight: '88%' as never,
    padding: 14,
  },
  header: {flexDirection: 'row', alignItems: 'center'},
  headerBtn: {padding: 6},
  headerBtnText: {color: theme.textDim, fontSize: 14},
  titleRow: {flexDirection: 'row', alignItems: 'center', marginTop: 2},
  title: {color: theme.text, fontSize: 18, fontWeight: '700'},
  subtitle: {color: theme.textDim, fontSize: 12, marginTop: 2},
  tabsRow: {flexDirection: 'row', marginTop: 12, gap: 8},
  sideTab: {
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: theme.panelAlt,
  },
  sideTabActive: {borderColor: theme.accent, backgroundColor: '#1c2b22'},
  sideTabText: {color: theme.textDim, fontSize: 13},
  recipeFilters: {
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 10,
    padding: 10,
    marginBottom: 12,
    gap: 9,
  },
  recipeList: {width: '100%'},
  filterHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 8,
  },
  filterTitle: {color: theme.text, fontSize: 12, fontWeight: '700'},
  sortControls: {flexDirection: 'row', alignItems: 'center', gap: 5},
  sortLabel: {color: theme.textDim, fontSize: 10, marginRight: 2},
  filterChips: {flexDirection: 'row', flexWrap: 'wrap', gap: 6},
  filterChip: {
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 7,
    backgroundColor: theme.panel,
    paddingHorizontal: 8,
    paddingVertical: 5,
  },
  filterChipActive: {borderColor: theme.accent, backgroundColor: '#1c2b22'},
  filterChipText: {color: theme.textDim, fontSize: 10},
  filterChipTextActive: {color: theme.accent, fontWeight: '700'},
  disabledTypeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderTopColor: theme.border,
    borderTopWidth: 1,
    paddingTop: 8,
    gap: 10,
  },
  disabledTypeCopy: {flex: 1},
  disabledTypeTitle: {color: theme.text, fontSize: 11, fontWeight: '600'},
  disabledTypeHint: {color: theme.textDim, fontSize: 9, marginTop: 2},
  sideTabTextActive: {color: theme.accent, fontWeight: '700'},
  body: {marginTop: 12},
  emptyText: {color: theme.textDim, padding: 10},
  dropSection: {marginTop: 14},
  dropTitle: {color: theme.textDim, fontSize: 11, textTransform: 'uppercase', marginBottom: 6},
  mobRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    marginBottom: 6,
    flexWrap: 'wrap',
  },
  mobChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 3,
  },
  mobName: {color: theme.text, fontSize: 11, maxWidth: 180},
  mobStat: {color: theme.textDim, fontSize: 11},
  loadingText: {color: theme.textDim, fontSize: 12, marginBottom: 10},
  moreBtn: {
    alignSelf: 'flex-start',
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginTop: 4,
  },
  moreBtnText: {color: theme.text, fontSize: 12},
});
