import React, {useEffect, useRef, useState} from 'react';
import {
  Linking,
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

function openPackUpload() {
  const url = '/publish#upload';
  void Linking.openURL(url).catch(error => {
    console.error('Could not open the modpack upload page.', {url, error});
  });
}

export function DatasetSwitcher({
  status,
  datasets,
  selectedSlug,
  loadedManifest,
  onOpenPicker,
  leadingAction,
  fullWidthControls,
  details,
}: {
  status: CatalogStatus;
  datasets: readonly DatasetDescriptor[];
  selectedSlug: string | null;
  loadedManifest: Manifest | null;
  onOpenPicker(): void;
  leadingAction?: React.ReactNode;
  fullWidthControls?: React.ReactNode;
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
        !compact && styles.fullDatasetButton,
        !canOpen && styles.disabled,
      ]}
      disabled={!canOpen}
      onPress={onOpenPicker}
      accessibilityRole="button"
      accessibilityLabel={
        selected
          ? `Change modpack. Current pack is ${selected.displayName}`
          : 'Choose a modpack'
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
  const uploadButton = (
    <TouchableOpacity
      style={styles.uploadButton}
      onPress={openPackUpload}
      accessibilityRole="link"
      accessibilityLabel="Upload a modpack exporter ZIP"
      accessibilityHint="Opens the drag-and-drop pack upload page"
      focusable>
      <Text style={styles.uploadButtonText}>Upload pack</Text>
    </TouchableOpacity>
  );

  return (
    <View style={[styles.bar, compact && styles.barCompact]}>
      {compact ? (
        <View style={styles.compactRows}>
          <View style={styles.compactTitleRow}>
            {brand}
            {datasetButton}
          </View>
          <View style={styles.compactControlRow}>
            {leadingAction}
            {expandButton}
          </View>
        </View>
      ) : (
        <View style={styles.fullRows}>
          <View style={styles.fullTitleRow}>{brand}</View>
          <View style={styles.fullControlRow}>
            {uploadButton}
            {leadingAction}
            {datasetButton}
            {fullWidthControls}
          </View>
        </View>
      )}

      {details && (!compact || expanded) && (
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
  fullRows: {gap: 7},
  fullTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 26,
  },
  fullControlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  brand: {flexGrow: 1, flexShrink: 1, minWidth: 140},
  brandCompact: {flexGrow: 0, flexShrink: 0, minWidth: 0},
  title: {color: theme.text, fontSize: 17, fontWeight: '800'},
  compactRows: {gap: 7},
  compactTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 26,
  },
  compactControlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  expandedContent: {gap: 8, paddingTop: 8},
  uploadButton: {
    minHeight: 34,
    flexShrink: 0,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 13,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.accent,
    backgroundColor: theme.accent,
  },
  uploadButtonText: {
    color: theme.bg,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '900',
  },
  compactDatasetButton: {
    minWidth: 0,
    maxWidth: 220,
    flexShrink: 1,
    minHeight: 34,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  compactDatasetButtonExpanded: {
    flex: 1,
    maxWidth: '100%',
  },
  fullDatasetButton: {
    flexGrow: 1,
    minWidth: 140,
    maxWidth: 360,
  },
  compactDatasetText: {
    minWidth: 0,
    flex: 1,
    flexShrink: 1,
    textAlign: 'center',
    color: theme.accent,
    fontSize: 15,
    lineHeight: 19,
    fontWeight: '800',
  },
  compactDatasetChevron: {
    position: 'absolute',
    right: 10,
    color: theme.accent,
    fontSize: 13,
  },
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
