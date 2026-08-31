import React, {useEffect, useRef, useState} from 'react';
import {
  Platform,
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
import {DisclosureChevron} from './DisclosureChevron';
import {DatasetDisclaimer} from './DatasetDisclaimer';

type CatalogStatus = 'loading' | 'ready' | 'error';

export function DatasetSwitcher({
  status,
  datasets,
  selectedSlug,
  loadedManifest,
  onOpenPicker,
  onImportPack,
  leadingAction,
  menuAction,
  trailingAction,
  fullWidthControls,
  details,
  compactMenuExpanded,
  onCompactMenuExpandedChange,
}: {
  status: CatalogStatus;
  datasets: readonly DatasetDescriptor[];
  selectedSlug: string | null;
  loadedManifest: Manifest | null;
  onOpenPicker(): void;
  onImportPack(): void;
  leadingAction?: React.ReactNode;
  menuAction?: React.ReactNode;
  trailingAction?: React.ReactNode;
  fullWidthControls?: React.ReactNode;
  details?: React.ReactNode;
  compactMenuExpanded?: boolean;
  onCompactMenuExpandedChange?(expanded: boolean): void;
}) {
  const {width} = useWindowDimensions();
  const nativeHeader = Platform.OS !== 'web';
  const [hasHydrated, setHasHydrated] = useState(nativeHeader);
  const compact = hasHydrated && width < 720;
  const [internalExpanded, setInternalExpanded] = useState(!compact);
  const expanded = compactMenuExpanded ?? internalExpanded;
  const setExpanded = (next: boolean) => {
    if (compactMenuExpanded === undefined) setInternalExpanded(next);
    onCompactMenuExpandedChange?.(next);
  };
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
      {!nativeHeader && <Text style={styles.title}>⛏ Recipe Tree</Text>}
      {loadedAttribution && <DatasetDisclaimer attribution={loadedAttribution} />}
    </View>
  );
  const datasetButton = (
    <TouchableOpacity
      style={[
        styles.compactDatasetButton,
        Platform.OS !== 'web' && styles.nativeTouchTarget,
        compact && styles.compactDatasetButtonExpanded,
        nativeHeader && styles.nativeDatasetButton,
        !compact && styles.fullDatasetButton,
        !canOpen && styles.disabled,
      ]}
      disabled={!canOpen}
      onPress={onOpenPicker}
      accessibilityRole="button"
      accessibilityLabel={
        selected
          ? `Change modpack. Current pack is ${selected.displayName}, version ${selected.packVersion}`
          : 'Choose a modpack'
      }>
      <View style={styles.compactDatasetLabel}>
        <Text style={styles.compactDatasetText} numberOfLines={1}>
          {selectedLabel}
        </Text>
        {selected && (
          <Text style={styles.compactDatasetVersion} numberOfLines={1}>
            {selected.packVersion}
          </Text>
        )}
      </View>
      <View style={styles.compactDatasetChevron}>
        <DisclosureChevron expanded={false} color={theme.accent} size={14} />
      </View>
    </TouchableOpacity>
  );
  const expandButton = compact || nativeHeader ? (
    <TouchableOpacity
      style={[
        styles.expandButton,
        Platform.OS !== 'web' && styles.nativeSquareTouchTarget,
        expanded && styles.expandButtonActive,
      ]}
      onPress={() => setExpanded(!expanded)}
      accessibilityRole="button"
      accessibilityLabel={expanded ? 'Close app menu' : 'Open app menu'}
      accessibilityState={{expanded}}>
      <Text style={[styles.expandButtonText, expanded && styles.expandButtonTextActive]}>
        {expanded ? '✕' : '☰'}
      </Text>
    </TouchableOpacity>
  ) : null;
  const uploadButton = (
    <TouchableOpacity
      style={[
        styles.uploadButton,
        compact && !nativeHeader && styles.compactUploadButton,
        nativeHeader && styles.nativeUploadButton,
      ]}
      onPress={
        () => {
          if (compact || nativeHeader) setExpanded(false);
          onImportPack();
        }
      }
      accessibilityRole={nativeHeader ? 'button' : 'link'}
      accessibilityLabel={nativeHeader ? 'Import a local modpack exporter ZIP' : 'Import a local modpack exporter ZIP'}
      accessibilityHint={
        nativeHeader
          ? 'Opens the on-device local pack importer'
          : 'Opens the local pack import page'
      }
      focusable>
      <Text style={[styles.uploadButtonText, nativeHeader && styles.nativeUploadButtonText]}>
        {nativeHeader ? '⇧  Import local pack' : compact ? 'Import pack' : 'Import local pack'}
      </Text>
    </TouchableOpacity>
  );

  return (
    <>
      <View style={[styles.bar, compact && styles.barCompact, nativeHeader && styles.barNative]}>
        {nativeHeader ? (
          <View style={styles.nativeRows}>
            <View style={styles.nativePickerRow}>
              {datasetButton}
              {menuAction}
              {expandButton}
              {trailingAction}
            </View>
            {expanded && (
              <View style={styles.nativeMenu} accessibilityRole="menu">
                <View style={styles.nativeMenuActions}>
                  {leadingAction}
                  {details && <View style={styles.nativeMenuDetails}>{details}</View>}
                  {loadedAttribution && (
                    <DatasetDisclaimer attribution={loadedAttribution} variant="menu" />
                  )}
                </View>
              </View>
            )}
          </View>
        ) : compact ? (
          <View style={styles.compactRows}>
            <View style={styles.compactTitleRow}>
              {brand}
              {datasetButton}
            </View>
            <View style={styles.compactControlRow}>
              {leadingAction}
              {expandButton}
              {trailingAction}
            </View>
            {expanded && (
              <View style={styles.compactWebMenu} accessibilityRole="menu">
                {details && <View style={styles.compactMenuDetails}>{details}</View>}
              </View>
            )}
          </View>
        ) : (
          <View style={styles.fullRows}>
            <View style={styles.fullTitleRow}>
              {brand}
              {datasetButton}
              {uploadButton}
              {trailingAction}
            </View>
            <View style={styles.fullControlRow}>
              {leadingAction}
              {fullWidthControls}
            </View>
          </View>
        )}

        {!nativeHeader && !compact && details && (
          <View style={styles.expandedContent}>
            {details}
          </View>
        )}
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  bar: {
    position: 'relative',
    zIndex: 100,
    overflow: 'visible',
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    backgroundColor: theme.panel,
  },
  barCompact: {paddingHorizontal: 10, paddingVertical: 7},
  barNative: {paddingVertical: 7, zIndex: 100, overflow: 'visible'},
  fullRows: {gap: 7},
  fullTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
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
  compactRows: {position: 'relative', zIndex: 100, gap: 7},
  nativeRows: {position: 'relative', zIndex: 100},
  nativePickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  nativeMenu: {
    position: 'absolute',
    top: '100%',
    left: 0,
    right: 0,
    zIndex: 101,
    elevation: 16,
    marginTop: 7,
    gap: 8,
    padding: 8,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
    shadowColor: '#000',
    shadowOpacity: 0.34,
    shadowRadius: 18,
    shadowOffset: {width: 0, height: 10},
  },
  nativeMenuActions: {
    alignItems: 'stretch',
    gap: 8,
  },
  nativeMenuDetails: {width: '100%', gap: 8},
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
  compactWebMenu: {
    position: 'absolute',
    top: '100%',
    left: 0,
    right: 0,
    zIndex: 101,
    elevation: 16,
    marginTop: 7,
    gap: 8,
    padding: 8,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
    shadowColor: '#000',
    shadowOpacity: 0.34,
    shadowRadius: 18,
    shadowOffset: {width: 0, height: 10},
  },
  compactMenuDetails: {width: '100%', gap: 8},
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
  compactUploadButton: {width: '100%', alignItems: 'flex-start', paddingHorizontal: 12},
  nativeUploadButton: {
    width: '100%',
    minHeight: 44,
    alignItems: 'flex-start',
    paddingHorizontal: 12,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  nativeUploadButtonText: {color: theme.text, fontSize: 12},
  compactDatasetButton: {
    minWidth: 0,
    maxWidth: 220,
    flexShrink: 1,
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  nativeTouchTarget: {minHeight: 44},
  nativeDatasetButton: {flex: 1, maxWidth: '100%'},
  nativeSquareTouchTarget: {width: 44, minHeight: 44},
  compactDatasetButtonExpanded: {
    flex: 1,
    maxWidth: '100%',
  },
  fullDatasetButton: {
    flexGrow: 1,
    minWidth: 140,
    maxWidth: 360,
  },
  compactDatasetLabel: {
    minWidth: 0,
    flex: 1,
    flexShrink: 1,
    alignItems: 'center',
  },
  compactDatasetText: {
    minWidth: 0,
    maxWidth: '100%',
    textAlign: 'center',
    color: theme.accent,
    fontSize: 14,
    lineHeight: 17,
    fontWeight: '800',
  },
  compactDatasetVersion: {
    maxWidth: '100%',
    textAlign: 'center',
    color: theme.textDim,
    fontSize: 10,
    lineHeight: 12,
    fontWeight: '700',
  },
  compactDatasetChevron: {
    position: 'absolute',
    right: 10,
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
