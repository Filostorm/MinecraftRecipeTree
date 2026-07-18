import React from 'react';
import {Image, Platform, StyleSheet, Text, View} from 'react-native';
import {useData} from '../data/DataContext';
import {theme} from '../theme';
import {CatalogItem} from '../types';
import {
  NATIVE_ITEM_ICON_SIZE,
  isPixelGridAlignedItemIconSize,
} from './itemIconSizing';

/** Crisp nearest-neighbor scaling for minecraft pixel art (web only; ignored elsewhere). */
export const pixelated =
  Platform.OS === 'web' ? ({imageRendering: 'pixelated'} as unknown as object) : null;

/** Icon fallback labels are UI chrome and should not become a browser text selection. */
const noSelect = Platform.OS === 'web' ? ({userSelect: 'none'} as unknown as object) : null;

const FALLBACK_COLORS = ['#7d5ba6', '#5b8aa6', '#5ba67d', '#a6915b', '#a65b5b', '#5b5fa6', '#86a65b'];

function colorFor(key: string): string {
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) | 0;
  return FALLBACK_COLORS[Math.abs(h) % FALLBACK_COLORS.length];
}

export function ItemIcon({item, itemKey, size}: {item?: CatalogItem; itemKey?: string; size: number}) {
  if (Platform.OS === 'web' && !isPixelGridAlignedItemIconSize(size)) {
    const error = new Error(
      `ItemIcon size ${size}px is not aligned to the native ${NATIVE_ITEM_ICON_SIZE}px pixel grid.`,
    );
    console.error(error.message);
    throw error;
  }
  const data = useData();
  const resolved = item ?? (itemKey ? data.itemsByKey.get(itemKey) : undefined);
  const uri = data.imageUrl(resolved?.icon);
  if (uri) {
    return (
      <Image
        source={{uri}}
        style={[{width: size, height: size}, pixelated as object]}
        resizeMode="contain"
      />
    );
  }
  const label = (resolved?.n ?? itemKey ?? '?').trim();
  return (
    <View
      style={[
        styles.fallback,
        {width: size, height: size, backgroundColor: colorFor(resolved?.k ?? itemKey ?? '?')},
      ]}>
      <Text style={[styles.fallbackText, {fontSize: Math.max(10, size * 0.45)}, noSelect]}>
        {label.charAt(0).toUpperCase() || '?'}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  fallback: {
    borderRadius: 4,
    alignItems: 'center',
    justifyContent: 'center',
    opacity: 0.9,
  },
  fallbackText: {
    color: theme.text,
    fontWeight: '700',
  },
});
