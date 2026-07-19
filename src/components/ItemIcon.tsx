import React, {useRef, useState} from 'react';
import {Image, Platform, StyleSheet, Text, View, type ImageErrorEvent} from 'react-native';
import {useData} from '../data/DataContext';
import {
  hasItemIconUriFailed,
  type ItemIconLoadFailure,
} from '../data/itemIconDiagnostics';
import {theme} from '../theme';
import {CatalogItem} from '../types';
import {
  LOGICAL_ITEM_ICON_GRID_SIZE,
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

interface ItemIconFallbackProps {
  colorKey: string;
  label: string;
  size: number;
}

function ItemIconFallback({colorKey, label, size}: ItemIconFallbackProps) {
  const visibleLabel = label.trim() || '?';
  return (
    <View
      accessible
      accessibilityRole="image"
      accessibilityLabel={`${visibleLabel} icon unavailable`}
      style={[
        styles.fallback,
        {width: size, height: size, backgroundColor: colorFor(colorKey)},
      ]}>
      <Text style={[styles.fallbackText, {fontSize: Math.max(10, size * 0.45)}, noSelect]}>
        {visibleLabel.charAt(0).toUpperCase() || '?'}
      </Text>
    </View>
  );
}

interface UriItemIconProps extends ItemIconFallbackProps {
  uri: string;
  itemKey?: string;
  reportFailure(failure: ItemIconLoadFailure): void;
}

/** The parent keys this component by URI, so any changed asset URI starts a fresh load attempt. */
function UriItemIcon({
  uri,
  itemKey,
  colorKey,
  label,
  size,
  reportFailure,
}: UriItemIconProps) {
  const [failedUri, setFailedUri] = useState<string | null>(null);
  const reportedFailure = useRef(false);
  if (hasItemIconUriFailed(failedUri, uri)) {
    return <ItemIconFallback colorKey={colorKey} label={label} size={size} />;
  }
  const onError = (event: ImageErrorEvent) => {
    if (!reportedFailure.current) {
      reportedFailure.current = true;
      reportFailure({uri, itemKey, label, detail: event.nativeEvent.error});
    }
    setFailedUri(uri);
  };
  return (
    <Image
      source={{uri}}
      style={[{width: size, height: size}, pixelated as object]}
      resizeMode="contain"
      onError={onError}
    />
  );
}

export function ItemIcon({item, itemKey, size}: {item?: CatalogItem; itemKey?: string; size: number}) {
  if (Platform.OS === 'web' && !isPixelGridAlignedItemIconSize(size)) {
    const error = new Error(
      `ItemIcon size ${size}px is not aligned to the logical ` +
        `${LOGICAL_ITEM_ICON_GRID_SIZE}px pixel grid.`,
    );
    console.error(error.message);
    throw error;
  }
  const data = useData();
  const resolved = item ?? (itemKey ? data.itemsByKey.get(itemKey) : undefined);
  const uri = data.imageUrl(resolved?.icon);
  const label = (resolved?.n ?? itemKey ?? '?').trim() || '?';
  const colorKey = resolved?.k ?? itemKey ?? '?';
  if (uri) {
    return (
      <UriItemIcon
        key={uri}
        uri={uri}
        itemKey={resolved?.k ?? itemKey}
        colorKey={colorKey}
        label={label}
        size={size}
        reportFailure={data.reportItemIconFailure}
      />
    );
  }
  return <ItemIconFallback colorKey={colorKey} label={label} size={size} />;
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
