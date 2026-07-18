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

  return (
    <View style={styles.root}>
      <View style={styles.stickyControls}>
        <SearchBar value={query} onChange={setQuery} placeholder={`Search ${data.items.length} items…`} />
        <ModFilter mods={data.mods} selected={mod} onSelect={setMod} />
        <Text style={styles.countLine}>
          {truncated ? `showing first ${MAX_RESULTS} — refine your search` : `${shown.length} items`}
        </Text>
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
    zIndex: 2,
  },
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
