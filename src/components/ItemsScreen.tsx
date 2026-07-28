import React, {useEffect, useMemo, useState} from 'react';
import {
  FlatList,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import {useData} from '../data/DataContext';
import {
  catalogTypePresentation,
  isItemCatalogEligible,
} from '../data/catalogPresentation';
import {theme} from '../theme';
import {CatalogItem} from '../types';
import {useUi} from '../ui/UiContext';
import {ItemIcon} from './ItemIcon';
import {ModFilter, SearchBar} from './SearchBar';

const MAX_RESULTS = 800;
const CELL_W = 104;

export function ItemsScreen({interfaceZoom}: {interfaceZoom: number}) {
  const data = useData();
  const {openItem} = useUi();
  const [query, setQuery] = useState('');
  const [mod, setMod] = useState<string | null>(null);
  const {width} = useWindowDimensions();

  const catalogItems = useMemo(
    () => data.items.filter(isItemCatalogEligible),
    [data.items],
  );
  const unknownCatalogTypes = useMemo(() => {
    const unknown = new Set<string>();
    for (const item of catalogItems) {
      const presentation = catalogTypePresentation(item.t);
      if (presentation && !presentation.recognized) unknown.add(item.t!);
    }
    return [...unknown].sort();
  }, [catalogItems]);

  useEffect(() => {
    if (unknownCatalogTypes.length === 0) return;
    console.warn(
      '[ItemsScreen] Some exporter ingredient types have no specific presentation label; ' +
        'rendering them as generic custom ingredients.',
      {dataset: data.descriptor.slug, types: unknownCatalogTypes},
    );
  }, [data.descriptor.slug, unknownCatalogTypes]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const out: CatalogItem[] = [];
    for (const item of catalogItems) {
      if (mod && item.m !== mod) continue;
      const typeLabel = catalogTypePresentation(item.t)?.label.toLowerCase() ?? '';
      if (
        q &&
        !item.n.toLowerCase().includes(q) &&
        !item.id.toLowerCase().includes(q) &&
        !typeLabel.includes(q)
      ) {
        continue;
      }
      out.push(item);
      if (out.length >= MAX_RESULTS + 1) break;
    }
    return out;
  }, [catalogItems, query, mod]);

  const truncated = filtered.length > MAX_RESULTS;
  const shown = truncated ? filtered.slice(0, MAX_RESULTS) : filtered;
  const columns = Math.max(3, Math.min(12, Math.floor(width / CELL_W)));
  const compactControls = width < 640;
  const scaledGridStyle =
    Platform.OS === 'web'
      ? ({
          zoom: interfaceZoom,
        } as unknown as object)
      : null;

  return (
    <View style={styles.root}>
      <View style={styles.stickyControls}>
        <View style={styles.controlsRow}>
          <SearchBar
            value={query}
            onChange={setQuery}
            placeholder={`Search ${catalogItems.length} items…`}
            style={styles.searchControl}
          />
          <ModFilter
            mods={data.mods}
            selected={mod}
            onSelect={setMod}
            style={[styles.modControl, compactControls && styles.modControlCompact]}
          />
        </View>
      </View>
      <FlatList
        style={[styles.grid, scaledGridStyle]}
        key={`grid-${columns}`}
        data={shown}
        numColumns={columns}
        keyExtractor={i => i.k}
        windowSize={7}
        initialNumToRender={60}
        maxToRenderPerBatch={60}
        contentContainerStyle={styles.gridContent}
        renderItem={({item}) => {
          const typeLabel = catalogTypePresentation(item.t)?.label;
          return (
            <TouchableOpacity
              style={[styles.cell, {width: `${100 / columns}%` as never}]}
              onPress={() => openItem(item.k)}>
              <ItemIcon item={item} size={48} />
              <Text style={styles.cellName} numberOfLines={2}>
                {item.n}
              </Text>
              {typeLabel && (
                <Text style={styles.typeBadge} numberOfLines={1}>
                  {typeLabel}
                </Text>
              )}
            </TouchableOpacity>
          );
        }}
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
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  searchControl: {
    flex: 1,
    minWidth: 0,
    marginHorizontal: 0,
    marginTop: 0,
  },
  modControl: {
    width: 220,
    marginHorizontal: 0,
    marginTop: 0,
  },
  modControlCompact: {width: 132},
  grid: {flex: 1, minHeight: 0},
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
