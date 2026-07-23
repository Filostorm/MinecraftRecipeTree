import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
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
}: {
  status: CatalogStatus;
  datasets: readonly DatasetDescriptor[];
  selectedSlug: string | null;
  loadedManifest: Manifest | null;
  onSelect(slug: string): void;
  onOpenPicker(): void;
}) {
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
    <View style={styles.bar}>
      <View style={styles.brand}>
        <Text style={styles.title}>⛏ Recipe Tree</Text>
        <Text style={styles.catalogSummary}>
          {datasets.length > 0
            ? `${datasets.length} published ${datasets.length === 1 ? 'pack' : 'packs'}`
            : status === 'loading'
              ? 'Loading published packs'
              : 'Published pack catalog unavailable'}
        </Text>
        {loadedAttribution && <DatasetDisclaimer attribution={loadedAttribution} />}
      </View>

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

    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 10,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    backgroundColor: theme.panel,
  },
  brand: {flexGrow: 1, flexShrink: 1, minWidth: 140},
  title: {color: theme.text, fontSize: 17, fontWeight: '800'},
  catalogSummary: {color: theme.textDim, fontSize: 10, marginTop: 2},
  controls: {
    flexGrow: 1,
    flexShrink: 1,
    flexBasis: 270,
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'stretch',
    justifyContent: 'flex-end',
    gap: 7,
  },
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
    maxWidth: 360,
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
