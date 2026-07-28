import React, {useEffect, useRef, useState} from 'react';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import type {DatasetDescriptor} from '../data/datasetCatalog';
import {loadedDatasetAttribution} from '../data/datasetAttribution';
import {theme} from '../theme';
import type {Manifest} from '../types';
import {DatasetDisclaimer} from './DatasetDisclaimer';

type CatalogStatus = 'loading' | 'ready' | 'error';

export function DatasetSwitcher({
  status,
  datasets,
  selectedSlug,
  loadedManifest,
  onOpenPicker,
  leadingAction,
  details,
}: {
  status: CatalogStatus;
  datasets: readonly DatasetDescriptor[];
  selectedSlug: string | null;
  loadedManifest: Manifest | null;
  onOpenPicker(): void;
  leadingAction?: React.ReactNode;
  details?: React.ReactNode;
}) {
  const {width} = useWindowDimensions();
  const [hasHydrated, setHasHydrated] = useState(false);
  const compact = hasHydrated && width < 720;
  const [expanded, setExpanded] = useState(!compact);
  const priorCompact = useRef(compact);
  useEffect(() => {
    setHasHydrated(true);
  }, []);
  useEffect(() => {
    if (priorCompact.current === compact) return;
    priorCompact.current = compact;
    setExpanded(!compact);
  }, [compact]);
  const selectedIndex = datasets.findIndex(dataset => dataset.slug === selectedSlug);
  const selected = selectedIndex >= 0 ? datasets[selectedIndex] : null;
  const loadedAttribution = loadedDatasetAttribution(loadedManifest);
  const canOpen = status !== 'loading' && datasets.length > 0;
  const selectedLabel = selected?.displayName ?? (
    status === 'loading' ? 'Loading catalog…' : 'Choose a modpack'
  );
  const brand = (
    <View style={[styles.brand, compact && styles.brandCompact]}>
      <Text style={styles.title}>⛏ Recipe Tree</Text>
      {loadedAttribution && <DatasetDisclaimer attribution={loadedAttribution} />}
    </View>
  );
  const datasetButton = (
    <TouchableOpacity
      style={[
        styles.compactDatasetButton,
        compact && styles.compactDatasetButtonExpanded,
        !canOpen && styles.disabled,
      ]}
      disabled={!canOpen}
      onPress={onOpenPicker}
      accessibilityRole="button"
      accessibilityLabel={
        selected
          ? `Change modpack. Current pack is ${selected.displayName}`
          : 'Choose a published modpack'
      }>
      <Text style={styles.compactDatasetText} numberOfLines={1}>
        {selectedLabel}
      </Text>
      <Text style={styles.compactDatasetChevron}>⌄</Text>
    </TouchableOpacity>
  );
  const expandButton = compact ? (
    <TouchableOpacity
      style={[styles.expandButton, expanded && styles.expandButtonActive]}
      onPress={() => setExpanded(value => !value)}
      accessibilityRole="button"
      accessibilityLabel={expanded ? 'Collapse site header' : 'Expand site header'}
      accessibilityState={{expanded}}>
      <Text style={[styles.expandButtonText, expanded && styles.expandButtonTextActive]}>
        {expanded ? '⌃' : '☰'}
      </Text>
    </TouchableOpacity>
  ) : null;

  return (
    <View style={[styles.bar, compact && styles.barCompact]}>
      {compact ? (
        <View style={styles.compactRows}>
          <View style={styles.compactTitleRow}>{brand}</View>
          <View style={styles.compactControlRow}>
            {leadingAction}
            {datasetButton}
            {expandButton}
          </View>
        </View>
      ) : (
        <View style={styles.topRow}>
          {brand}
          {leadingAction}
          {datasetButton}
        </View>
      )}

      {(!compact || expanded) && (
        <View style={styles.expandedContent}>
          {details}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    backgroundColor: theme.panel,
  },
  barCompact: {paddingHorizontal: 10, paddingVertical: 7},
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  brand: {flexGrow: 1, flexShrink: 1, minWidth: 140},
  brandCompact: {minWidth: 0},
  title: {color: theme.text, fontSize: 17, fontWeight: '800'},
  compactRows: {gap: 7},
  compactTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 26,
  },
  compactControlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  expandedContent: {gap: 8, paddingTop: 8},
  compactDatasetButton: {
    minWidth: 0,
    maxWidth: 220,
    flexShrink: 1,
    minHeight: 34,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 9,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  compactDatasetButtonExpanded: {
    flex: 1,
    maxWidth: '100%',
  },
  compactDatasetText: {
    minWidth: 0,
    flexShrink: 1,
    color: theme.accent,
    fontSize: 11,
    fontWeight: '800',
  },
  compactDatasetChevron: {color: theme.accent, fontSize: 12},
  expandButton: {
    width: 36,
    minHeight: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  expandButtonActive: {borderColor: theme.accent},
  expandButtonText: {color: theme.text, fontSize: 16, fontWeight: '800'},
  expandButtonTextActive: {color: theme.accent},
  disabled: {opacity: 0.46},
});
