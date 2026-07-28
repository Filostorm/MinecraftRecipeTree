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
  useWindowDimensions,
} from 'react-native';
import {ItemDetailModal} from './src/components/ItemDetailModal';
import {InterfaceZoomSlider} from './src/components/InterfaceZoomSlider';
import {DatasetPicker} from './src/components/DatasetPicker';
import {DatasetSwitcher} from './src/components/DatasetSwitcher';
import {GraphGuideModal} from './src/components/GraphGuideModal';
import {ItemsScreen} from './src/components/ItemsScreen';
import {MobsScreen} from './src/components/MobsScreen';
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
import {
  INTERFACE_ZOOM_STEP,
  MAXIMUM_INTERFACE_ZOOM,
  MINIMUM_INTERFACE_ZOOM,
  loadInterfaceZoom,
  normalizeInterfaceZoom,
  persistInterfaceZoom,
} from './src/ui/interfaceZoom';

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

  const datasetControls = (
    loadedManifest: Manifest | null,
    details?: React.ReactNode,
    leadingAction?: React.ReactNode,
  ) => (
    <>
      <DatasetSwitcher
        status={catalog.state.status}
        datasets={datasets}
        selectedSlug={selectedSlug}
        loadedManifest={loadedManifest}
        onOpenPicker={() => setShowDatasetPicker(true)}
        leadingAction={leadingAction}
        details={details}
      />
      {showDatasetPicker && (
        <DatasetPicker
          visible
          datasets={datasets}
          selectedSlug={selectedSlug}
          onSelect={slug => {
            catalog.select(slug);
            setShowDatasetPicker(false);
          }}
          onClose={() => setShowDatasetPicker(false)}
        />
      )}
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
  renderControls(
    manifest: Manifest | null,
    details?: React.ReactNode,
    leadingAction?: React.ReactNode,
  ): React.ReactNode;
}) {
  return (
    <View style={styles.datasetRoot}>
      <DatasetReadinessMarker expectedPublicationId={expectedPublicationId} />
      <View style={styles.datasetContent}>
        <UiProvider>
          <Root renderControls={renderControls} />
        </UiProvider>
      </View>
    </View>
  );
}

function Root({
  renderControls,
}: {
  renderControls(
    manifest: Manifest | null,
    details?: React.ReactNode,
    leadingAction?: React.ReactNode,
  ): React.ReactNode;
}) {
  const state = useLoadState();
  if (state.status === 'loading') {
    return (
      <View style={styles.datasetRoot}>
        {renderControls(null)}
        <View style={styles.center}>
          <ActivityIndicator color={theme.accent} size="large" />
          <Text style={styles.loadingText}>loading {state.step}</Text>
        </View>
      </View>
    );
  }
  if (state.status === 'error') {
    const stale = state.kind === 'stale';
    return (
      <View style={styles.datasetRoot}>
        {renderControls(null)}
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
      </View>
    );
  }
  return <Shell renderControls={renderControls} />;
}

function Shell({
  renderControls,
}: {
  renderControls(
    manifest: Manifest | null,
    details?: React.ReactNode,
    leadingAction?: React.ReactNode,
  ): React.ReactNode;
}) {
  const data = useData();
  const {tab} = useUi();
  const {width} = useWindowDimensions();
  const [hasHydrated, setHasHydrated] = useState(Platform.OS !== 'web');
  const compactHeader = hasHydrated && width < 720;
  const [showRecipeHistory, setShowRecipeHistory] = useState(false);
  const [showGraphGuide, setShowGraphGuide] = useState(false);
  const [showDatasetDetails, setShowDatasetDetails] = useState(false);
  const [interfaceZoom, setInterfaceZoom] = useState(1);
  useEffect(() => {
    if (Platform.OS === 'web') setHasHydrated(true);
  }, []);
  useEffect(() => {
    if (tab !== 'graph' || data.indexStatus === 'ready' || data.indexStatus === 'loading') return;
    void data.ensureIndex().catch(() => {
      // DataContext logs transport and validation detail and exposes the error below.
    });
  }, [data, tab]);
  useEffect(() => {
    if (Platform.OS === 'web') setInterfaceZoom(loadInterfaceZoom());
  }, []);
  const previewInterfaceZoom = (value: number) => {
    try {
      setInterfaceZoom(normalizeInterfaceZoom(value));
    } catch (error) {
      console.error('Interface zoom slider produced an invalid value.', error);
    }
  };
  const saveInterfaceZoom = (value: number) => {
    try {
      persistInterfaceZoom(normalizeInterfaceZoom(value));
    } catch (error) {
      console.error('Interface zoom could not be saved.', error);
    }
  };
  const scaledWorkspaceStyle =
    Platform.OS === 'web'
      ? ({
          zoom: interfaceZoom,
        } as unknown as object)
      : null;
  const headerDetails = (
    <View style={styles.headerDetails}>
      <View style={styles.headerActionRow}>
        <TabBtn tab="items" label="Items" />
        <TabBtn tab="graph" label="Graph" />
        {data.capabilities.mobs && <TabBtn tab="mobs" label="Mobs" />}
        {compactHeader && (
          <TouchableOpacity
            style={[
              styles.headerDetailBtn,
              showDatasetDetails && styles.headerDetailBtnActive,
            ]}
            onPress={() => setShowDatasetDetails(value => !value)}
            accessibilityRole="button"
            accessibilityState={{expanded: showDatasetDetails}}
            accessibilityLabel="Dataset details">
            <Text
              style={[
                styles.headerDetailBtnText,
                showDatasetDetails && styles.headerDetailBtnTextActive,
              ]}>
              ⓘ Details
            </Text>
          </TouchableOpacity>
        )}
      </View>
      {(!compactHeader || showDatasetDetails) && (
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
      )}
      {Platform.OS === 'web' && (
        <View
          style={styles.interfaceZoomControls}
          accessibilityLabel="Interface zoom controls">
          <Text
            style={styles.interfaceZoomValue}
            accessibilityLabel={`Interface zoom ${Math.round(interfaceZoom * 100)} percent`}>
            UI {Math.round(interfaceZoom * 100)}%
          </Text>
          <InterfaceZoomSlider
            minimumValue={MINIMUM_INTERFACE_ZOOM}
            maximumValue={MAXIMUM_INTERFACE_ZOOM}
            step={INTERFACE_ZOOM_STEP}
            value={interfaceZoom}
            onValueChange={previewInterfaceZoom}
            onSlidingComplete={saveInterfaceZoom}
          />
        </View>
      )}
    </View>
  );
  const headerActions = (
    <View style={styles.headerUtilityRow}>
      <TouchableOpacity
        style={styles.headerUtilityButton}
        onPress={() => setShowRecipeHistory(true)}
        accessibilityRole="button"
        accessibilityLabel={`Open recipe history for ${data.descriptor.displayName}`}>
        <Text style={styles.historyHeaderIcon}>◷</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={[
          styles.headerUtilityButton,
          showGraphGuide && styles.headerUtilityButtonActive,
        ]}
        onPress={() => setShowGraphGuide(true)}
        accessibilityRole="button"
        accessibilityLabel="Open graph guide">
        <Text
          style={[
            styles.guideHeaderIcon,
            showGraphGuide && styles.guideHeaderIconActive,
          ]}>
          ?
        </Text>
      </TouchableOpacity>
    </View>
  );
  return (
    <View style={styles.shell}>
      {renderControls(data.manifest, headerDetails, headerActions)}
      <View style={styles.workspaceViewport}>
        <View style={styles.workspace}>
          {/* All tabs stay mounted so graph expansion state survives tab switches. */}
          <View style={[styles.body, tab !== 'items' && styles.hidden]}>
            <ItemsScreen interfaceZoom={interfaceZoom} />
          </View>
          <View style={[styles.body, scaledWorkspaceStyle, tab !== 'graph' && styles.hidden]}>
            {data.indexStatus === 'ready' ? (
              <GraphScreen interfaceZoom={interfaceZoom} />
            ) : (
              <View style={styles.center}>
                {data.indexStatus !== 'error' && (
                  <ActivityIndicator color={theme.accent} size="large" />
                )}
                <Text style={data.indexStatus === 'error' ? styles.errorTitle : styles.loadingText}>
                  {data.indexStatus === 'error'
                    ? 'Recipe index unavailable'
                    : 'loading recipe index…'}
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
            <View style={[styles.body, scaledWorkspaceStyle, tab !== 'mobs' && styles.hidden]}>
              <MobsScreen />
            </View>
          )}
        </View>
      </View>
      <ItemDetailModal />
      {showRecipeHistory && (
        <RecipeHistoryModal
          visible
          onClose={() => setShowRecipeHistory(false)}
        />
      )}
      {showGraphGuide && (
        <GraphGuideModal
          visible
          onClose={() => setShowGraphGuide(false)}
          packSlug={data.descriptor.slug}
          packName={data.descriptor.displayName}
        />
      )}
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
  headerDetails: {gap: 7},
  headerActionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
  },
  subtitle: {color: theme.textDim, fontSize: 11, lineHeight: 16},
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
  headerDetailBtn: {
    minHeight: 34,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    justifyContent: 'center',
  },
  headerDetailBtnActive: {borderColor: theme.accent, backgroundColor: theme.panelAlt},
  headerDetailBtnText: {color: theme.text, fontSize: 11, fontWeight: '700'},
  headerDetailBtnTextActive: {color: theme.accent},
  headerUtilityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    flexShrink: 0,
  },
  headerUtilityButton: {
    width: 34,
    minHeight: 34,
    flexShrink: 0,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  headerUtilityButtonActive: {borderColor: theme.accent},
  historyHeaderIcon: {color: theme.text, fontSize: 17, fontWeight: '700'},
  guideHeaderIcon: {color: theme.text, fontSize: 16, fontWeight: '800'},
  guideHeaderIconActive: {color: theme.accent},
  interfaceZoomControls: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: theme.borderLight,
    borderRadius: 8,
    minHeight: 38,
    paddingHorizontal: 9,
    backgroundColor: theme.panelAlt,
  },
  interfaceZoomValue: {
    minWidth: 60,
    marginRight: 5,
    color: theme.text,
    fontSize: 11,
    fontWeight: '700',
    textAlign: 'center',
  },
  workspaceViewport: {flex: 1, minHeight: 0, overflow: 'hidden'},
  workspace: {flex: 1, minHeight: 0},
  body: {flex: 1, minHeight: 0},
  hidden: {display: 'none'},
});
