import React, {useMemo, useState} from 'react';
import {FlatList, StyleSheet, Text, TouchableOpacity, View, useWindowDimensions} from 'react-native';
import {useData} from '../data/DataContext';
import {theme} from '../theme';
import {CatalogItem} from '../types';
import {useUi} from '../ui/UiContext';
import {ItemIcon} from './ItemIcon';
import {ModFilter, SearchBar} from './SearchBar';

const MAX_RESULTS = 800;
const CELL_W = 104;

export function ItemsScreen() {
  const data = useData();
  const {openItem} = useUi();
  const [query, setQuery] = useState('');
  const [mod, setMod] = useState<string | null>(null);
  const [mobileControlsExpanded, setMobileControlsExpanded] = useState(false);
  const {width} = useWindowDimensions();

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const out: CatalogItem[] = [];
    for (const item of data.items) {
      if (mod && item.m !== mod) continue;
      if (q && !item.n.toLowerCase().includes(q) && !item.id.toLowerCase().includes(q)) continue;
      out.push(item);
      if (out.length >= MAX_RESULTS + 1) break;
    }
    return out;
  }, [data.items, query, mod]);

  const truncated = filtered.length > MAX_RESULTS;
  const shown = truncated ? filtered.slice(0, MAX_RESULTS) : filtered;
  const columns = Math.max(3, Math.min(12, Math.floor(width / CELL_W)));
  const compactControls = width < 640;
  const controlsVisible = !compactControls || mobileControlsExpanded;
  const resultLabel = truncated
    ? `${MAX_RESULTS}+ results`
    : `${shown.length} ${shown.length === 1 ? 'item' : 'items'}`;

  return (
    <View style={styles.root}>
      <View style={styles.stickyControls}>
        {compactControls && (
          <TouchableOpacity
            style={[
              styles.mobileControlsButton,
              (mobileControlsExpanded || query.length > 0 || mod !== null) &&
                styles.mobileControlsButtonActive,
            ]}
            onPress={() => setMobileControlsExpanded(value => !value)}
            accessibilityRole="button"
            accessibilityState={{expanded: mobileControlsExpanded}}
            accessibilityLabel={
              mobileControlsExpanded ? 'Collapse item search and filters' : 'Expand item search and filters'
            }>
            <Text
              style={[
                styles.mobileControlsButtonText,
                (mobileControlsExpanded || query.length > 0 || mod !== null) &&
                  styles.mobileControlsButtonTextActive,
              ]}
              numberOfLines={1}>
              ⌕ {query || (mod ? data.mods.find(entry => entry.id === mod)?.name : 'Search & filters')}
            </Text>
            <Text style={styles.mobileResultText}>{resultLabel}</Text>
            <Text style={styles.mobileControlsChevron}>
              {mobileControlsExpanded ? '⌃' : '⌄'}
            </Text>
          </TouchableOpacity>
        )}
        {controlsVisible && (
          <View style={styles.expandedControls}>
            <SearchBar value={query} onChange={setQuery} placeholder={`Search ${data.items.length} items…`} />
            <ModFilter mods={data.mods} selected={mod} onSelect={setMod} />
            <Text style={styles.countLine}>
              {truncated ? `showing first ${MAX_RESULTS} — refine your search` : `${shown.length} items`}
            </Text>
          </View>
        )}
      </View>
      <FlatList
        style={styles.grid}
        key={`grid-${columns}`}
        data={shown}
        numColumns={columns}
        keyExtractor={i => i.k}
        windowSize={7}
        initialNumToRender={60}
        maxToRenderPerBatch={60}
        contentContainerStyle={styles.gridContent}
        renderItem={({item}) => (
          <TouchableOpacity style={[styles.cell, {width: `${100 / columns}%` as never}]} onPress={() => openItem(item.k)}>
            <ItemIcon item={item} size={48} />
            <Text style={styles.cellName} numberOfLines={2}>
              {item.n}
            </Text>
            {item.t && <Text style={styles.typeBadge}>{item.t}</Text>}
          </TouchableOpacity>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, minHeight: 0},
  stickyControls: {
    flexShrink: 0,
    backgroundColor: theme.bg,
    borderBottomColor: theme.border,
    borderBottomWidth: 1,
    paddingBottom: 6,
    paddingHorizontal: 6,
    paddingTop: 6,
    zIndex: 2,
  },
  expandedControls: {gap: 6},
  mobileControlsButton: {
    minHeight: 40,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingHorizontal: 11,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  mobileControlsButtonActive: {borderColor: theme.accent},
  mobileControlsButtonText: {
    flex: 1,
    minWidth: 0,
    color: theme.text,
    fontSize: 12,
    fontWeight: '700',
  },
  mobileControlsButtonTextActive: {color: theme.accent},
  mobileResultText: {color: theme.textDim, fontSize: 10},
  mobileControlsChevron: {color: theme.accent, fontSize: 14, fontWeight: '800'},
  grid: {flex: 1, minHeight: 0},
  countLine: {color: theme.textDim, fontSize: 11, paddingHorizontal: 12, paddingTop: 6},
  gridContent: {paddingHorizontal: 6, paddingTop: 4, paddingBottom: 24},
  cell: {
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 4,
    borderRadius: 8,
  },
  cellName: {
    color: theme.textDim,
    fontSize: 11,
    textAlign: 'center',
    marginTop: 5,
  },
  typeBadge: {
    color: theme.accentAlt,
    fontSize: 9,
    marginTop: 2,
    textTransform: 'uppercase',
  },
});
