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
  onSelect,
  onOpenPicker,
  details,
}: {
  status: CatalogStatus;
  datasets: readonly DatasetDescriptor[];
  selectedSlug: string | null;
  loadedManifest: Manifest | null;
  onSelect(slug: string): void;
  onOpenPicker(): void;
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
  const canCycle = status === 'ready' && selectedIndex >= 0 && datasets.length > 1;
  const canOpen = status !== 'loading' && datasets.length > 0;

  const selectRelative = (offset: -1 | 1) => {
    if (!canCycle) {
      console.error('Relative modpack selection was requested while pack cycling is unavailable.', {
        status,
        selectedSlug,
        datasetCount: datasets.length,
      });
      return;
    }
    const nextIndex = (selectedIndex + offset + datasets.length) % datasets.length;
    onSelect(datasets[nextIndex].slug);
  };

  const positionLabel = selected
    ? `PACK ${selectedIndex + 1} OF ${datasets.length}`
    : status === 'loading'
      ? 'LOADING MODPACKS'
      : datasets.length > 0
        ? `${datasets.length} PUBLISHED ${datasets.length === 1 ? 'PACK' : 'PACKS'}`
        : 'MODPACK CATALOG';
  const selectedLabel = selected?.displayName ?? (
    status === 'loading' ? 'Loading catalog…' : 'Choose a modpack'
  );

  return (
    <View style={[styles.bar, compact && styles.barCompact]}>
      <View style={styles.topRow}>
        <View style={styles.brand}>
          <Text style={styles.title}>⛏ Recipe Tree</Text>
          {!compact && (
            <Text style={styles.catalogSummary}>
              {datasets.length > 0
                ? `${datasets.length} published ${datasets.length === 1 ? 'pack' : 'packs'}`
                : status === 'loading'
                  ? 'Loading published packs'
                  : 'Published pack catalog unavailable'}
            </Text>
          )}
          {loadedAttribution && <DatasetDisclaimer attribution={loadedAttribution} />}
        </View>
        {compact && (
          <>
            <TouchableOpacity
              style={[styles.compactDatasetButton, !canOpen && styles.disabled]}
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
          </>
        )}
      </View>

      {(!compact || expanded) && (
        <View style={styles.expandedContent}>
          <View style={styles.controls}>
            <TouchableOpacity
              style={[styles.cycleButton, !canCycle && styles.disabled]}
              disabled={!canCycle}
              onPress={() => selectRelative(-1)}
              accessibilityRole="button"
              accessibilityLabel={
                selected ? `Previous modpack before ${selected.displayName}` : 'Previous modpack'
              }
              accessibilityHint="Cycles to the previous published recipe dataset"
              accessibilityState={{disabled: !canCycle}}
              focusable>
              <Text style={styles.cycleButtonText}>‹</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.datasetButton, !canOpen && styles.disabled]}
              disabled={!canOpen}
              onPress={onOpenPicker}
              accessibilityRole="button"
              accessibilityLabel={
                selected
                  ? `Change modpack. Current pack is ${selected.displayName}`
                  : status === 'loading'
                    ? 'Published modpacks are loading'
                    : 'Choose a published modpack'
              }
              accessibilityHint={canOpen ? 'Opens the searchable published modpack picker' : undefined}
              accessibilityState={{disabled: !canOpen}}
              focusable>
              <Text style={styles.datasetButtonLabel}>{positionLabel}</Text>
              <Text style={styles.datasetButtonText} numberOfLines={1}>
                {selectedLabel}{canOpen ? ' ▾' : ''}
              </Text>
              {selected && (
                <Text style={styles.datasetButtonMeta} numberOfLines={1}>
                  Minecraft {selected.minecraftVersion} · {selected.packVersion}
                </Text>
              )}
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.cycleButton, !canCycle && styles.disabled]}
              disabled={!canCycle}
              onPress={() => selectRelative(1)}
              accessibilityRole="button"
              accessibilityLabel={
                selected ? `Next modpack after ${selected.displayName}` : 'Next modpack'
              }
              accessibilityHint="Cycles to the next published recipe dataset"
              accessibilityState={{disabled: !canCycle}}
              focusable>
              <Text style={styles.cycleButtonText}>›</Text>
            </TouchableOpacity>
          </View>
          {details}
          {compact && (
            <Text style={styles.catalogSummary}>
              {datasets.length > 0
                ? `${datasets.length} published ${datasets.length === 1 ? 'pack' : 'packs'}`
                : status === 'loading'
                  ? 'Loading published packs'
                  : 'Published pack catalog unavailable'}
            </Text>
          )}
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
  title: {color: theme.text, fontSize: 17, fontWeight: '800'},
  catalogSummary: {color: theme.textDim, fontSize: 10, marginTop: 2},
  controls: {
    flexDirection: 'row',
    alignItems: 'stretch',
    gap: 7,
  },
  expandedContent: {gap: 8, paddingTop: 8},
  compactDatasetButton: {
    minWidth: 0,
    maxWidth: 190,
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
  cycleButton: {
    width: 44,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 9,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
  },
  cycleButtonText: {color: theme.accent, fontSize: 26, lineHeight: 28, fontWeight: '700'},
  datasetButton: {
    flexGrow: 1,
    flexShrink: 1,
    minWidth: 0,
    minHeight: 48,
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: theme.accent,
    justifyContent: 'center',
  },
  datasetButtonLabel: {color: theme.textDim, fontSize: 9, fontWeight: '800'},
  datasetButtonText: {color: theme.accent, fontSize: 13, fontWeight: '800', marginTop: 1},
  datasetButtonMeta: {color: theme.textDim, fontSize: 9, marginTop: 1},
  disabled: {opacity: 0.46},
});
