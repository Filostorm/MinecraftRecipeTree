import React from 'react';
import {Linking, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import type {DatasetDescriptor} from '../data/datasetCatalog';
import {theme} from '../theme';

type CatalogStatus = 'loading' | 'ready' | 'error';
const GTNH_SOURCE_URL = 'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4';
const GTNH_LICENSE_URL = 'https://creativecommons.org/licenses/by-nc-sa/4.0/';

function openAttributionLink(url: string, label: string) {
  void Linking.openURL(url).catch(error => {
    console.error(`Could not open the GT New Horizons ${label} link.`, {url, error});
  });
}

function openPublishGuide() {
  const url = '/publish';
  void Linking.openURL(url).catch(error => {
    console.error('Could not open the exporter download and publishing guide.', {url, error});
  });
}

export function DatasetSwitcher({
  status,
  datasets,
  selectedSlug,
  onSelect,
  onOpenPicker,
}: {
  status: CatalogStatus;
  datasets: readonly DatasetDescriptor[];
  selectedSlug: string | null;
  onSelect(slug: string): void;
  onOpenPicker(): void;
}) {
  const selectedIndex = datasets.findIndex(dataset => dataset.slug === selectedSlug);
  const selected = selectedIndex >= 0 ? datasets[selectedIndex] : null;
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

      <TouchableOpacity
        style={styles.publishLink}
        onPress={openPublishGuide}
        accessibilityRole="link"
        accessibilityLabel="Export and publish a modpack"
        accessibilityHint="Opens the exporter download and publishing guide in a new tab"
        focusable>
        <Text style={styles.publishLinkText}>Export &amp; publish ↗</Text>
      </TouchableOpacity>

      {selected?.slug === 'gt-new-horizons' && (
        <Text style={styles.attribution}>
          GT New Horizons {selected.packVersion} recipe data by the{' '}
          <Text
            style={styles.attributionLink}
            accessibilityRole="link"
            onPress={() => openAttributionLink(GTNH_SOURCE_URL, 'source')}>
            GT New Horizons contributors
          </Text>
          , adapted into this Recipe Tree database export. The GTNH-derived database is licensed
          under{' '}
          <Text
            style={styles.attributionLink}
            accessibilityRole="link"
            onPress={() => openAttributionLink(GTNH_LICENSE_URL, 'license')}>
            CC BY-NC-SA 4.0
          </Text>{' '}
          and provided as-is, without warranty. Recipe Tree is not affiliated with or endorsed by
          GT New Horizons.
        </Text>
      )}
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
  publishLink: {
    minHeight: 44,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 9,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  publishLinkText: {
    color: theme.text,
    fontSize: 11,
    fontWeight: '800',
  },
  attribution: {
    flexBasis: '100%',
    flexGrow: 1,
    color: theme.textDim,
    fontSize: 9,
    lineHeight: 13,
  },
  attributionLink: {
    color: theme.accent,
    textDecorationLine: 'underline',
  },
  disabled: {opacity: 0.46},
});
