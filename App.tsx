import {StatusBar} from 'expo-status-bar';
import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  Platform,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {ItemDetailModal} from './src/components/ItemDetailModal';
import {DatasetPicker} from './src/components/DatasetPicker';
import {DatasetSwitcher} from './src/components/DatasetSwitcher';
import {ItemsScreen} from './src/components/ItemsScreen';
import {MobsScreen} from './src/components/MobsScreen';
import {ModpackManager} from './src/components/ModpackManager';
import {RecipeHistoryModal} from './src/components/RecipeHistoryModal';
import {DataProvider, useData, useLoadState} from './src/data/DataContext';
import {DatasetReadinessMarker} from './src/data/DatasetReadinessMarker';
import {
  DatasetCatalogProvider,
  useDatasetCatalog,
} from './src/data/DatasetCatalogContext';
import {datasetMountKey} from './src/data/datasetCatalog';
import {GraphScreen} from './src/graph/GraphScreen';
import {theme} from './src/theme';
import type {Manifest} from './src/types';
import {Tab, UiProvider, useUi} from './src/ui/UiContext';

export default function App() {
  return (
    <DatasetCatalogProvider>
      <SafeAreaView style={styles.app}>
        <StatusBar style="light" />
        <DatasetRoot />
      </SafeAreaView>
    </DatasetCatalogProvider>
  );
}

function DatasetRoot() {
  const catalog = useDatasetCatalog();
  const [showDatasetPicker, setShowDatasetPicker] = useState(false);
  const datasets = catalog.state.status === 'loading' ? [] : catalog.state.datasets;
  const selectedSlug = catalog.state.status === 'ready' ? catalog.state.selected.slug : null;

  const datasetControls = (loadedManifest: Manifest | null) => (
    <>
      <DatasetSwitcher
        status={catalog.state.status}
        datasets={datasets}
        selectedSlug={selectedSlug}
        loadedManifest={loadedManifest}
        onSelect={catalog.select}
        onOpenPicker={() => setShowDatasetPicker(true)}
      />
      <DatasetPicker
        visible={showDatasetPicker}
        datasets={datasets}
        selectedSlug={selectedSlug}
        onSelect={slug => {
          catalog.select(slug);
          setShowDatasetPicker(false);
        }}
        onClose={() => setShowDatasetPicker(false)}
      />
    </>
  );

  if (catalog.state.status === 'loading') {
    return (
      <View style={styles.datasetRoot}>
        {datasetControls(null)}
        <View style={styles.center}>
          <ActivityIndicator color={theme.accent} size="large" />
          <Text style={styles.loadingText}>loading published modpacks</Text>
        </View>
      </View>
    );
  }
  if (catalog.state.status === 'error') {
    return (
      <View style={styles.datasetRoot}>
        {datasetControls(null)}
        <View style={styles.center}>
          <Text style={styles.errorTitle}>Modpack selection unavailable</Text>
          <Text style={styles.errorText}>{catalog.state.message}</Text>
          {catalog.state.datasets.length > 0 && (
            <TouchableOpacity
              style={styles.reloadBtn}
              onPress={() => setShowDatasetPicker(true)}
              accessibilityRole="button"
              accessibilityLabel="Choose a valid published modpack"
              focusable>
              <Text style={styles.reloadBtnText}>Choose a published modpack</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>
    );
  }

  const {source} = catalog.state;
  return (
    <DataProvider
      key={datasetMountKey(source.descriptor)}
      descriptor={source.descriptor}
      base={source.base}
      previewBase={source.previewBase}>
      <LoadedDatasetLayout
        expectedPublicationId={source.descriptor.publicationId}
        renderControls={datasetControls}
      />
    </DataProvider>
  );
}

function LoadedDatasetLayout({
  expectedPublicationId,
  renderControls,
}: {
  expectedPublicationId: string;
  renderControls(manifest: Manifest | null): React.ReactNode;
}) {
  const state = useLoadState();
  const manifest = state.status === 'ready' ? state.data.manifest : null;
  return (
    <View style={styles.datasetRoot}>
      <DatasetReadinessMarker expectedPublicationId={expectedPublicationId} />
      {renderControls(manifest)}
      <View style={styles.datasetContent}>
        <UiProvider>
          <Root />
        </UiProvider>
      </View>
    </View>
  );
}

function Root() {
  const state = useLoadState();
  if (state.status === 'loading') {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={theme.accent} size="large" />
        <Text style={styles.loadingText}>loading {state.step}</Text>
      </View>
    );
  }
  if (state.status === 'error') {
    const stale = state.kind === 'stale';
    return (
      <View style={styles.center}>
        <Text style={styles.errorTitle}>{stale ? 'Recipe dataset updated' : 'Export unavailable'}</Text>
        <Text style={styles.errorText}>
          {state.message}
          {!stale && (
            <>
              {'\n\n'}Expected data at “{state.base}/manifest.json”.
              {'\n\n'}Development recovery:
              {'\n'}1. Run the version-appropriate Minecraft exporter.
              {'\n'}2. Run npm run import-data -- --source /absolute/path/to/export.
              {'\n'}3. Reload this page after validation and publication complete.
            </>
          )}
        </Text>
        {stale && Platform.OS === 'web' && (
          <TouchableOpacity
            accessibilityRole="button"
            style={styles.reloadBtn}
            onPress={() => {
              if (typeof window === 'undefined') {
                console.error('Dataset reload was requested, but the browser window is unavailable.');
                return;
              }
              window.location.reload();
            }}>
            <Text style={styles.reloadBtnText}>Reload catalog and dataset</Text>
          </TouchableOpacity>
        )}
      </View>
    );
  }
  return <Shell />;
}

function Shell() {
  const data = useData();
  const {tab} = useUi();
  const [showSnapshots, setShowSnapshots] = useState(false);
  const [showRecipeHistory, setShowRecipeHistory] = useState(false);
  useEffect(() => {
    if (tab !== 'graph' || data.indexStatus === 'ready' || data.indexStatus === 'loading') return;
    void data.ensureIndex().catch(() => {
      // DataContext logs transport and validation detail and exposes the error below.
    });
  }, [data, tab]);
  return (
    <View style={styles.shell}>
      <View style={styles.header}>
        <Text style={styles.subtitle}>
          Minecraft {data.descriptor.minecraftVersion} · pack {data.descriptor.packVersion} ·{' '}
          {Object.keys(data.manifest.mods ?? {}).length} mods · {data.items.length} items ·{' '}
          {data.manifest.counts?.recipes ?? '?'} recipes
          {data.capabilities.mobs ? ` · ${data.mobs.length} mobs` : ' · mob catalog unavailable'}
          {!data.capabilities.blockDrops ? ' · block-drop catalog unavailable' : ''}
          {data.capabilities.recipePreviews
            ? ' · JEI layout previews available'
            : ' · JEI layout previews unavailable'}
        </Text>
        <TouchableOpacity
          style={styles.modpackBtn}
          onPress={() => setShowSnapshots(true)}
          accessibilityRole="button"
          accessibilityLabel="Open saved export snapshots">
          <Text style={styles.modpackBtnText}>▣ Saved snapshots</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.historyBtn}
          onPress={() => setShowRecipeHistory(true)}
          accessibilityRole="button"
          accessibilityLabel={`Open recipe history for ${data.descriptor.displayName}`}>
          <Text style={styles.historyBtnText}>↶ Recipe history</Text>
        </TouchableOpacity>
        <TabBtn tab="items" label="Items" />
        <TabBtn tab="graph" label="Graph" />
        {data.capabilities.mobs && <TabBtn tab="mobs" label="Mobs" />}
      </View>
      {/* All tabs stay mounted so graph expansion state survives tab switches. */}
      <View style={[styles.body, tab !== 'items' && styles.hidden]}>
        <ItemsScreen />
      </View>
      <View style={[styles.body, tab !== 'graph' && styles.hidden]}>
        {data.indexStatus === 'ready' ? (
          <GraphScreen />
        ) : (
          <View style={styles.center}>
            {data.indexStatus !== 'error' && <ActivityIndicator color={theme.accent} size="large" />}
            <Text style={data.indexStatus === 'error' ? styles.errorTitle : styles.loadingText}>
              {data.indexStatus === 'error' ? 'Recipe index unavailable' : 'loading recipe index…'}
            </Text>
            {data.indexStatus === 'error' && (
              <>
                <Text style={styles.errorText}>{data.indexError}</Text>
                <TouchableOpacity
                  style={styles.reloadBtn}
                  onPress={() => void data.ensureIndex().catch(() => {})}>
                  <Text style={styles.reloadBtnText}>Retry recipe index</Text>
                </TouchableOpacity>
              </>
            )}
          </View>
        )}
      </View>
      {data.capabilities.mobs && (
        <View style={[styles.body, tab !== 'mobs' && styles.hidden]}>
          <MobsScreen />
        </View>
      )}
      <ItemDetailModal />
      <ModpackManager visible={showSnapshots} onClose={() => setShowSnapshots(false)} />
      <RecipeHistoryModal
        visible={showRecipeHistory}
        onClose={() => setShowRecipeHistory(false)}
      />
    </View>
  );
}

function TabBtn({tab, label}: {tab: Tab; label: string}) {
  const ui = useUi();
  const active = ui.tab === tab;
  return (
    <TouchableOpacity onPress={() => ui.setTab(tab)} style={[styles.tabBtn, active && styles.tabBtnActive]}>
      <Text style={[styles.tabBtnText, active && styles.tabBtnTextActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  app: {flex: 1, minHeight: 0, backgroundColor: theme.bg},
  datasetRoot: {flex: 1, minHeight: 0, backgroundColor: theme.bg},
  datasetContent: {flex: 1, minHeight: 0},
  center: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24},
  loadingText: {color: theme.textDim, marginTop: 14},
  errorTitle: {color: theme.text, fontSize: 18, fontWeight: '700'},
  errorText: {
    color: theme.textDim,
    marginTop: 10,
    maxWidth: 560,
    lineHeight: 20,
    fontSize: 13,
  },
  reloadBtn: {
    marginTop: 18,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.accent,
  },
  reloadBtnText: {color: theme.accent, fontSize: 13, fontWeight: '700'},
  shell: {flex: 1, minHeight: 0},
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    backgroundColor: theme.panel,
    flexWrap: 'wrap',
    gap: 8,
  },
  subtitle: {color: theme.textDim, fontSize: 11, flexGrow: 1, flexShrink: 1, minWidth: 220},
  tabBtn: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  tabBtnActive: {backgroundColor: theme.panelAlt, borderColor: theme.border},
  tabBtnText: {color: theme.textDim, fontSize: 13},
  tabBtnTextActive: {color: theme.accent, fontWeight: '700'},
  modpackBtn: {
    minHeight: 44,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.accent,
    justifyContent: 'center',
  },
  modpackBtnText: {color: theme.accent, fontSize: 12, fontWeight: '700'},
  historyBtn: {
    minHeight: 44,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    justifyContent: 'center',
  },
  historyBtnText: {color: theme.text, fontSize: 12, fontWeight: '700'},
  body: {flex: 1, minHeight: 0},
  hidden: {display: 'none'},
});
