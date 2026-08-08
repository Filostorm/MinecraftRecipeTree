import {StatusBar} from 'expo-status-bar';
import React, {Suspense, useEffect, useMemo, useRef, useState} from 'react';
import {
  ActivityIndicator,
  Animated,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import {SafeAreaProvider, SafeAreaView, initialWindowMetrics} from './src/ui/safeArea';
import {ItemDetailModal} from './src/components/ItemDetailModal';
import {InterfaceZoomSlider} from './src/components/InterfaceZoomSlider';
import {DatasetPicker} from './src/components/DatasetPicker';
import {DatasetSwitcher} from './src/components/DatasetSwitcher';
import {GraphGuideModal} from './src/components/GraphGuideModal';
import {BugIcon} from './src/components/BugIcon';
import {IssueReportModal} from './src/components/IssueReportModal';
import type {GitHubIssueKind, IssueReportContext} from './src/components/githubIssues';
import {ItemsScreen} from './src/components/ItemsScreen';
import {MobsScreen} from './src/components/MobsScreen';
import {RecipeStageModal} from './src/components/RecipeStageModal';
import {RecipeHistoryModal} from './src/components/RecipeHistoryModal';
import {DataProvider, useData, useLoadState} from './src/data/DataContext';
import {DatasetReadinessMarker} from './src/data/DatasetReadinessMarker';
import {
  DatasetCatalogProvider,
  useDatasetCatalog,
} from './src/data/DatasetCatalogContext';
import {
  RecipeStageProvider,
  useRecipeStages,
} from './src/data/RecipeStageContext';
import {datasetMountKey} from './src/data/datasetCatalog';
import {theme} from './src/theme';
import type {Manifest} from './src/types';
import {Tab, UiProvider, useUi} from './src/ui/UiContext';
import {lightImpactFeedback, selectionFeedback} from './src/ui/haptics';
import {disclosureChevron} from './src/ui/disclosureChevron';
import {
  INTERFACE_ZOOM_STEP,
  MAXIMUM_INTERFACE_ZOOM,
  MINIMUM_INTERFACE_ZOOM,
  loadInterfaceZoom,
  normalizeInterfaceZoom,
  persistInterfaceZoom,
} from './src/ui/interfaceZoom';
import {
  CONTENT_ZOOM_STEP,
  MAXIMUM_CONTENT_ZOOM,
  MINIMUM_CONTENT_ZOOM,
  loadContentZoom,
  normalizeContentZoom,
  persistContentZoom,
} from './src/ui/contentZoom';
import {signalTarget, useSignalSurface} from './src/analytics/signal';
import {
  graphRenderRecovery,
  type GraphRenderRecovery,
} from './src/graph/graphRenderError';

const LazyGraphScreen = React.lazy(async () => {
  const module = await import('./src/graph/GraphScreen');
  return {default: module.GraphScreen};
});

class GraphErrorBoundary extends React.Component<
  {children: React.ReactNode; onReturnToItems(): void},
  {recovery: GraphRenderRecovery | null}
> {
  state: {recovery: GraphRenderRecovery | null} = {recovery: null};

  static getDerivedStateFromError(error: unknown): {recovery: GraphRenderRecovery} {
    return {recovery: graphRenderRecovery(error)};
  }

  componentDidCatch(error: unknown, info: React.ErrorInfo): void {
    console.error('The graph workspace stopped rendering.', {
      error,
      kind: graphRenderRecovery(error).kind,
      componentStack: info.componentStack,
    });
  }

  render() {
    const recovery = this.state.recovery;
    if (!recovery) return this.props.children;
    return (
      <View style={styles.graphRecovery} accessibilityRole="alert">
        <Text style={styles.errorTitle}>{recovery.title}</Text>
        <Text style={styles.errorText}>{recovery.message}</Text>
        <View style={styles.graphRecoveryActions}>
          <TouchableOpacity
            style={[styles.reloadBtn, styles.graphRecoveryButton]}
            onPress={() => this.setState({recovery: null})}>
            <Text style={styles.reloadBtnText}>Try again</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.reloadBtn, styles.graphRecoveryButton]}
            onPress={this.props.onReturnToItems}>
            <Text style={styles.reloadBtnText}>Return to Browse</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }
}

export default function App() {
  return (
    <SafeAreaProvider initialMetrics={initialWindowMetrics}>
      <SafeAreaView
        style={styles.app}
        edges={Platform.OS === 'web' ? ['top', 'right', 'bottom', 'left'] : ['top', 'right', 'left']}>
        <StatusBar style="light" />
        <DatasetCatalogProvider>
          <DatasetRoot />
        </DatasetCatalogProvider>
      </SafeAreaView>
    </SafeAreaProvider>
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
    fullWidthControls?: React.ReactNode,
    trailingAction?: React.ReactNode,
  ) => (
    <>
      <DatasetSwitcher
        status={catalog.state.status}
        datasets={datasets}
        selectedSlug={selectedSlug}
        loadedManifest={loadedManifest}
        onOpenPicker={() => setShowDatasetPicker(true)}
        leadingAction={leadingAction}
        trailingAction={trailingAction}
        fullWidthControls={fullWidthControls}
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
    fullWidthControls?: React.ReactNode,
    trailingAction?: React.ReactNode,
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
    fullWidthControls?: React.ReactNode,
    trailingAction?: React.ReactNode,
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
    const localPack = state.base.startsWith('/__local-packs/');
    return (
      <View style={styles.datasetRoot}>
        {renderControls(null)}
        <View style={styles.center}>
          <Text style={styles.errorTitle}>{stale ? 'Recipe dataset updated' : 'Export unavailable'}</Text>
          <Text style={styles.errorText}>
            {state.message}
            {!stale && !localPack && (
              <>
                {'\n\n'}Expected data at “{state.base}/manifest.json”.
                {'\n\n'}Development recovery:
                {'\n'}1. Run the version-appropriate Minecraft exporter.
                {'\n'}2. Run npm run import-data -- --source /absolute/path/to/export.
                {'\n'}3. Reload this page after validation and publication complete.
              </>
            )}
            {!stale && localPack && (
              <>
                {'\n\n'}This pack is already stored on this device. If the message above says a
                legacy document exceeds the compatibility limit, make a new export with the
                latest exporter; otherwise, add the same ZIP again to repair its saved copy.
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
  return (
    <RecipeStageProvider>
      <Shell renderControls={renderControls} />
    </RecipeStageProvider>
  );
}

function Shell({
  renderControls,
}: {
  renderControls(
    manifest: Manifest | null,
    details?: React.ReactNode,
    leadingAction?: React.ReactNode,
    fullWidthControls?: React.ReactNode,
    trailingAction?: React.ReactNode,
  ): React.ReactNode;
}) {
  const data = useData();
  const recipeStages = useRecipeStages();
  const ui = useUi();
  const {tab, setTab, graphRequestId} = ui;
  const {width} = useWindowDimensions();
  const [hasHydrated, setHasHydrated] = useState(Platform.OS !== 'web');
  const compactHeader = hasHydrated && width < 720;
  const [showRecipeHistory, setShowRecipeHistory] = useState(false);
  const [showRecipeStages, setShowRecipeStages] = useState(false);
  const [showGraphGuide, setShowGraphGuide] = useState(false);
  const [showIssueReport, setShowIssueReport] = useState(false);
  const [issueReportKind, setIssueReportKind] = useState<GitHubIssueKind>('bug');
  const [showGraphControls, setShowGraphControls] = useState(false);
  const [showDatasetDetails, setShowDatasetDetails] = useState(false);
  const [interfaceZoom, setInterfaceZoom] = useState(1);
  const [contentZoom, setContentZoom] = useState(1);
  const shellSurface = showRecipeStages
      ? 'recipe-stages'
      : showGraphGuide
        ? 'graph-guide'
        : showIssueReport
          ? 'issue-report'
          : showRecipeHistory
            ? 'recipe-history'
            : tab;
  useSignalSurface(
    shellSurface,
    showRecipeStages || showGraphGuide || showIssueReport || showRecipeHistory
      ? 'modal'
      : 'screen',
  );
  const issueReportContext = useMemo<IssueReportContext>(() => ({
    packSlug: data.descriptor.slug,
    packName: data.descriptor.displayName,
    packVersion: data.descriptor.packVersion,
    minecraftVersion: data.descriptor.minecraftVersion,
    publicationId: data.datasetIdentity,
    previewAssetSetId: data.descriptor.previewAssetSetId,
    exportGeneratedAt: data.manifest.generatedAt,
    exportFormat: data.manifest.format,
    itemCount: data.items.length,
    recipeCount: data.manifest.counts.recipes,
    categoryCount: data.categories.length,
    modCount: Object.keys(data.manifest.mods ?? {}).length,
    activeTab: tab,
    openItemKey: ui.itemStack[ui.itemStack.length - 1] ?? '',
    graphRootKey: ui.graphRootKey ?? '',
    graphDirection: ui.graphDirection,
    interfaceZoomPercent: Math.round(interfaceZoom * 100),
    contentZoomPercent: Math.round(contentZoom * 100),
  }), [contentZoom, data, interfaceZoom, tab, ui.graphDirection, ui.graphRootKey, ui.itemStack]);
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
    if (Platform.OS !== 'web') return;
    const storedInterfaceZoom = loadInterfaceZoom();
    setInterfaceZoom(storedInterfaceZoom);
    setContentZoom(loadContentZoom(storedInterfaceZoom));
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
  const previewContentZoom = (value: number) => {
    try {
      setContentZoom(normalizeContentZoom(value));
    } catch (error) {
      console.error('Recipe and item zoom slider produced an invalid value.', error);
    }
  };
  const saveContentZoom = (value: number) => {
    try {
      persistContentZoom(normalizeContentZoom(value));
    } catch (error) {
      console.error('Recipe and item zoom could not be saved.', error);
    }
  };
  const scaledHeaderStyle =
    Platform.OS === 'web'
      ? ({zoom: interfaceZoom} as unknown as object)
      : null;
  const scaledMobsWorkspaceStyle =
    Platform.OS === 'web'
      ? ({
          zoom: interfaceZoom,
        } as unknown as object)
      : null;
  const headerTabs = Platform.OS === 'web' ? (
      <View style={styles.headerActionRow}>
        <TabBtn tab="items" label="Items" />
        <TabBtn tab="graph" label="Graph" />
        {data.capabilities.mobs && <TabBtn tab="mobs" label="Mobs" />}
      </View>
    ) : null;
  const datasetDetailsButton = (
    <TouchableOpacity
      {...signalTarget('header.dataset-details')}
      style={[
        styles.headerDetailBtn,
        Platform.OS !== 'web' && styles.nativeHeaderDetailBtn,
        showDatasetDetails && styles.headerDetailBtnActive,
      ]}
      onPress={() => {
        lightImpactFeedback();
        setShowDatasetDetails(value => !value);
      }}
      accessibilityRole="button"
      accessibilityState={{expanded: showDatasetDetails}}
      accessibilityLabel="Dataset details">
      <Text
        style={[
          styles.headerDetailBtnText,
          showDatasetDetails && styles.headerDetailBtnTextActive,
        ]}>
        {Platform.OS === 'web' ? 'ⓘ Details' : 'ⓘ  Dataset details'}
      </Text>
    </TouchableOpacity>
  );
  const datasetMetadata = (
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
  );
  const interfaceZoomControls = Platform.OS === 'web' ? (
    <View style={styles.zoomControlGroup}>
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
      <View
        style={styles.interfaceZoomControls}
        accessibilityLabel="Recipe and item size controls">
        <Text
          style={[styles.interfaceZoomValue, styles.contentZoomValue]}
          accessibilityLabel={`Recipe and item size ${Math.round(contentZoom * 100)} percent`}>
          Recipe/items {Math.round(contentZoom * 100)}%
        </Text>
        <InterfaceZoomSlider
          accessibilityLabel="Recipe and item size"
          testID="content-zoom-slider"
          minimumValue={MINIMUM_CONTENT_ZOOM}
          maximumValue={MAXIMUM_CONTENT_ZOOM}
          step={CONTENT_ZOOM_STEP}
          value={contentZoom}
          onValueChange={previewContentZoom}
          onSlidingComplete={saveContentZoom}
        />
      </View>
    </View>
  ) : null;
  const headerDetails = compactHeader ? (
    <View style={styles.headerDetails}>
      <View style={styles.compactHeaderNavigation}>
        {headerTabs}
        {datasetDetailsButton}
      </View>
      {showDatasetDetails && datasetMetadata}
      {interfaceZoomControls}
    </View>
  ) : showDatasetDetails ? (
    datasetMetadata
  ) : null;
  const fullWidthHeaderControls = !compactHeader ? (
    <View style={styles.fullWidthHeaderControls}>
      {headerTabs}
      {datasetDetailsButton}
      {interfaceZoomControls}
    </View>
  ) : null;
  const headerActions = (
    <View
      style={[
        styles.headerUtilityRow,
        Platform.OS !== 'web' && styles.nativeHeaderUtilityRow,
      ]}>
      <TouchableOpacity
        {...signalTarget('header.recipe-history')}
        style={[
          styles.headerUtilityButton,
          Platform.OS !== 'web' && styles.nativeHeaderUtilityButton,
        ]}
        onPress={() => {
          lightImpactFeedback();
          setShowRecipeHistory(true);
        }}
        accessibilityRole="button"
        accessibilityLabel={`Open recipe history for ${data.descriptor.displayName}`}>
        <Text
          style={[
            styles.historyHeaderIcon,
            Platform.OS !== 'web' && styles.nativeHeaderMenuText,
          ]}>
          {Platform.OS === 'web' ? '◷' : '◷  Recipe history'}
        </Text>
      </TouchableOpacity>
      <TouchableOpacity
        {...signalTarget('header.issue-report')}
        style={[
          styles.headerUtilityButton,
          styles.issueReportHeaderButton,
          Platform.OS !== 'web' && styles.nativeHeaderUtilityButton,
          showIssueReport && styles.headerUtilityButtonActive,
        ]}
        onPress={() => {
          lightImpactFeedback();
          setIssueReportKind('bug');
          setShowIssueReport(true);
        }}
        accessibilityRole="button"
        accessibilityLabel="Report a bug or send feedback">
        <BugIcon active={showIssueReport} />
      </TouchableOpacity>
      {recipeStages.catalog.stages.length > 0 && (
        <TouchableOpacity
          {...signalTarget('header.recipe-stages')}
          style={[
            styles.headerUtilityButton,
            styles.recipeStagesHeaderButton,
            Platform.OS !== 'web' && styles.nativeHeaderUtilityButton,
            showRecipeStages && styles.headerUtilityButtonActive,
          ]}
          onPress={() => {
            lightImpactFeedback();
            setShowRecipeStages(true);
          }}
          accessibilityRole="button"
          accessibilityLabel={`Open recipe stage controls, ${recipeStages.catalog.stages.length} stages`}>
          <Text
            style={[
              styles.recipeStagesHeaderText,
              showRecipeStages && styles.recipeStagesHeaderTextActive,
            ]}>
            {Platform.OS === 'web' ? '⚑ Stages' : '⚑  Recipe stages'}
          </Text>
        </TouchableOpacity>
      )}
      <TouchableOpacity
        {...signalTarget('header.graph-guide')}
        style={[
          styles.headerUtilityButton,
          Platform.OS !== 'web' && styles.nativeHeaderUtilityButton,
          showGraphGuide && styles.headerUtilityButtonActive,
        ]}
        onPress={() => {
          lightImpactFeedback();
          setShowGraphGuide(true);
        }}
        accessibilityRole="button"
        accessibilityLabel="Open graph guide">
        <Text
          style={[
            styles.guideHeaderIcon,
            Platform.OS !== 'web' && styles.nativeHeaderMenuText,
            showGraphGuide && styles.guideHeaderIconActive,
          ]}>
          {Platform.OS === 'web' ? '?' : '?  Graph guide'}
        </Text>
      </TouchableOpacity>
    </View>
  );
  const graphControlsHeaderAction =
    compactHeader && tab === 'graph' && data.indexStatus === 'ready' ? (
      <TouchableOpacity
        {...signalTarget('graph.control.menu')}
        accessibilityRole="button"
        accessibilityLabel={
          showGraphControls ? 'Collapse graph controls' : 'Expand graph controls'
        }
        accessibilityState={{expanded: showGraphControls}}
        style={[
          styles.graphControlsHeaderButton,
          showGraphControls && styles.headerUtilityButtonActive,
        ]}
        onPress={() => {
          lightImpactFeedback();
          setShowGraphControls(value => !value);
        }}>
        <Text
          style={[
            styles.graphControlsHeaderButtonText,
            showGraphControls && styles.graphControlsHeaderButtonTextActive,
          ]}>
          {disclosureChevron(showGraphControls)}
        </Text>
      </TouchableOpacity>
    ) : null;
  return (
    <View style={styles.shell}>
      <View style={scaledHeaderStyle}>
        {renderControls(
          data.manifest,
          headerDetails,
          headerActions,
          fullWidthHeaderControls,
          graphControlsHeaderAction,
        )}
      </View>
      <View style={styles.workspaceViewport}>
        <View style={styles.workspace}>
          {/* All tabs stay mounted so graph expansion state survives tab switches. */}
          <View
            style={[
              styles.body,
              Platform.OS !== 'web' && styles.nativeWorkspacePane,
              Platform.OS !== 'web' && tab === 'items' && styles.nativeWorkspacePaneActive,
              Platform.OS !== 'web' && tab !== 'items' && styles.nativeWorkspacePaneInactive,
              Platform.OS === 'web' && tab !== 'items' && styles.hidden,
            ]}
            pointerEvents={Platform.OS !== 'web' && tab !== 'items' ? 'none' : 'auto'}
            accessibilityElementsHidden={Platform.OS !== 'web' && tab !== 'items'}
            importantForAccessibility={
              Platform.OS !== 'web' && tab !== 'items' ? 'no-hide-descendants' : 'auto'
            }>
            <ItemsScreen interfaceZoom={interfaceZoom} contentZoom={contentZoom} />
          </View>
          <View
            style={[
              styles.body,
              Platform.OS !== 'web' && styles.nativeWorkspacePane,
              Platform.OS !== 'web' && tab === 'graph' && styles.nativeWorkspacePaneActive,
              Platform.OS !== 'web' && tab !== 'graph' && styles.nativeWorkspacePaneInactive,
              Platform.OS === 'web' && tab !== 'graph' && styles.hidden,
            ]}
            pointerEvents={Platform.OS !== 'web' && tab !== 'graph' ? 'none' : 'auto'}
            accessibilityElementsHidden={Platform.OS !== 'web' && tab !== 'graph'}
            importantForAccessibility={
              Platform.OS !== 'web' && tab !== 'graph' ? 'no-hide-descendants' : 'auto'
            }>
            {data.indexStatus === 'ready' ? (
              <GraphErrorBoundary
                key={graphRequestId}
                onReturnToItems={() => setTab('items')}>
                <Suspense
                  fallback={
                    <View style={styles.center}>
                      <ActivityIndicator color={theme.accent} size="large" />
                      <Text style={styles.loadingText}>loading graph workspace…</Text>
                    </View>
                  }>
                  <LazyGraphScreen
                    interfaceZoom={interfaceZoom}
                    contentZoom={contentZoom}
                    showGraphControls={showGraphControls}
                    onToggleGraphControls={() =>
                      setShowGraphControls(value => !value)
                    }
                    controlsToggleInHeader={compactHeader}
                  />
                </Suspense>
              </GraphErrorBoundary>
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
            <View
              style={[
                styles.body,
                scaledMobsWorkspaceStyle,
                Platform.OS !== 'web' && styles.nativeWorkspacePane,
                Platform.OS !== 'web' && tab === 'mobs' && styles.nativeWorkspacePaneActive,
                Platform.OS !== 'web' && tab !== 'mobs' && styles.nativeWorkspacePaneInactive,
                Platform.OS === 'web' && tab !== 'mobs' && styles.hidden,
              ]}
              pointerEvents={Platform.OS !== 'web' && tab !== 'mobs' ? 'none' : 'auto'}
              accessibilityElementsHidden={Platform.OS !== 'web' && tab !== 'mobs'}
              importantForAccessibility={
                Platform.OS !== 'web' && tab !== 'mobs' ? 'no-hide-descendants' : 'auto'
              }>
              <MobsScreen />
            </View>
          )}
        </View>
      </View>
      {Platform.OS !== 'web' && (
        <MobileBottomNavigation hasMobs={data.capabilities.mobs} />
      )}
      <ItemDetailModal interfaceZoom={interfaceZoom} contentZoom={contentZoom} />
      {showRecipeStages && (
        <RecipeStageModal
          visible
          interfaceZoom={interfaceZoom}
          onClose={() => setShowRecipeStages(false)}
        />
      )}
      {showRecipeHistory && (
        <RecipeHistoryModal
          visible
          interfaceZoom={interfaceZoom}
          onClose={() => setShowRecipeHistory(false)}
        />
      )}
      {showGraphGuide && (
        <GraphGuideModal
          visible
          interfaceZoom={interfaceZoom}
          onClose={() => setShowGraphGuide(false)}
          onOpenIssueReport={kind => {
            setShowGraphGuide(false);
            setIssueReportKind(kind);
            setShowIssueReport(true);
          }}
        />
      )}
      {showIssueReport && (
        <IssueReportModal
          visible
          interfaceZoom={interfaceZoom}
          initialKind={issueReportKind}
          context={issueReportContext}
          onClose={() => setShowIssueReport(false)}
        />
      )}
    </View>
  );
}

function TabBtn({tab, label}: {tab: Tab; label: string}) {
  const ui = useUi();
  const active = ui.tab === tab;
  return (
    <TouchableOpacity
      {...signalTarget(`tab.${tab}`)}
      onPress={() => {
        selectionFeedback();
        ui.setTab(tab);
      }}
      style={[styles.tabBtn, active && styles.tabBtnActive]}>
      <Text style={[styles.tabBtnText, active && styles.tabBtnTextActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

function MobileBottomNavigation({hasMobs}: {hasMobs: boolean}) {
  const ui = useUi();
  const tabs = useMemo(
    () => [
      {tab: 'items' as const, icon: '☷', label: 'Browse'},
      {tab: 'graph' as const, icon: '⌘', label: 'Tree'},
      ...(hasMobs ? [{tab: 'mobs' as const, icon: '♟', label: 'Mobs'}] : []),
    ],
    [hasMobs],
  );
  const selectedIndex = Math.max(0, tabs.findIndex(item => item.tab === ui.tab));
  const animatedIndex = useRef(new Animated.Value(selectedIndex)).current;
  const [navigationWidth, setNavigationWidth] = useState(0);
  const segmentWidth = Math.max(0, (navigationWidth - 20) / tabs.length);

  useEffect(() => {
    Animated.spring(animatedIndex, {
      toValue: selectedIndex,
      damping: 22,
      stiffness: 230,
      mass: 0.8,
      useNativeDriver: true,
    }).start();
  }, [animatedIndex, selectedIndex]);

  return (
    <SafeAreaView edges={['bottom']} style={styles.mobileNavigationSafeArea}>
      <View
        style={styles.mobileNavigation}
        onLayout={event => setNavigationWidth(event.nativeEvent.layout.width)}
        accessibilityRole="tablist"
        accessibilityLabel="Main navigation">
        {segmentWidth > 0 && (
          <Animated.View
            pointerEvents="none"
            style={[
              styles.mobileTabIndicator,
              {
                width: segmentWidth,
                transform: [{translateX: Animated.multiply(animatedIndex, segmentWidth)}],
              },
            ]}
          />
        )}
        {tabs.map(item => (
          <MobileTabBtn key={item.tab} {...item} />
        ))}
      </View>
    </SafeAreaView>
  );
}

function MobileTabBtn({tab, icon, label}: {tab: Tab; icon: string; label: string}) {
  const ui = useUi();
  const active = ui.tab === tab;
  return (
    <TouchableOpacity
      {...signalTarget(`tab.${tab}`)}
      accessibilityRole="tab"
      accessibilityLabel={label}
      accessibilityState={{selected: active}}
      onPress={() => {
        selectionFeedback();
        ui.setTab(tab);
      }}
      style={[styles.mobileTab, active && styles.mobileTabActive]}>
      <Text style={[styles.mobileTabIcon, active && styles.mobileTabIconActive]}>
        {icon}
      </Text>
      <Text style={[styles.mobileTabLabel, active && styles.mobileTabLabelActive]}>
        {label}
      </Text>
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
  graphRecovery: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: theme.bg,
  },
  graphRecoveryActions: {
    marginTop: 18,
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 10,
  },
  graphRecoveryButton: {marginTop: 0},
  shell: {flex: 1, minHeight: 0},
  headerDetails: {gap: 7},
  compactHeaderNavigation: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
  },
  fullWidthHeaderControls: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 0,
    gap: 6,
  },
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
  nativeHeaderDetailBtn: {
    width: '100%',
    minHeight: 44,
    alignItems: 'flex-start',
    paddingHorizontal: 12,
    backgroundColor: theme.panelAlt,
  },
  headerUtilityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    flexShrink: 0,
  },
  nativeHeaderUtilityRow: {
    width: '100%',
    flexDirection: 'column',
    alignItems: 'stretch',
    gap: 8,
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
  nativeHeaderUtilityButton: {
    width: '100%',
    minWidth: 0,
    minHeight: 44,
    alignItems: 'flex-start',
    paddingHorizontal: 12,
  },
  headerUtilityButtonActive: {borderColor: theme.accent},
  graphControlsHeaderButton: {
    width: 44,
    minHeight: 44,
    flexShrink: 0,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  graphControlsHeaderButtonText: {
    color: theme.textDim,
    fontSize: 20,
    lineHeight: 22,
    fontWeight: '800',
  },
  graphControlsHeaderButtonTextActive: {color: theme.accent},
  historyHeaderIcon: {color: theme.text, fontSize: 17, fontWeight: '700'},
  recipeStagesHeaderButton: {width: 'auto', minWidth: 72, paddingHorizontal: 8},
  recipeStagesHeaderText: {color: theme.text, fontSize: 11, fontWeight: '800'},
  recipeStagesHeaderTextActive: {color: theme.accent},
  issueReportHeaderButton: {width: 34, minWidth: 34, paddingHorizontal: 0},
  guideHeaderIcon: {color: theme.text, fontSize: 16, fontWeight: '800'},
  guideHeaderIconActive: {color: theme.accent},
  nativeHeaderMenuText: {color: theme.text, fontSize: 12, fontWeight: '700'},
  interfaceZoomControls: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: theme.borderLight,
    borderRadius: 8,
    minHeight: 34,
    paddingHorizontal: 9,
    backgroundColor: theme.panelAlt,
  },
  zoomControlGroup: {flexDirection: 'row', alignItems: 'center', gap: 6},
  interfaceZoomValue: {
    minWidth: 60,
    marginRight: 5,
    color: theme.text,
    fontSize: 11,
    fontWeight: '700',
    textAlign: 'center',
  },
  contentZoomValue: {minWidth: 112},
  workspaceViewport: {flex: 1, minHeight: 0, overflow: 'hidden'},
  workspace: {flex: 1, minHeight: 0, position: 'relative'},
  body: {flex: 1, minHeight: 0},
  nativeWorkspacePane: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
  },
  nativeWorkspacePaneActive: {opacity: 1, zIndex: 1},
  nativeWorkspacePaneInactive: {opacity: 0, zIndex: 0},
  mobileNavigationSafeArea: {
    flexShrink: 0,
    borderTopWidth: 1,
    borderTopColor: theme.border,
    backgroundColor: theme.panel,
  },
  mobileNavigation: {
    minHeight: 62,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingTop: 6,
    paddingBottom: 5,
  },
  mobileTabIndicator: {
    position: 'absolute',
    left: 10,
    top: 6,
    height: 50,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  mobileTab: {
    flex: 1,
    minHeight: 50,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
    borderRadius: 12,
    zIndex: 1,
  },
  mobileTabActive: {},
  mobileTabIcon: {color: theme.textDim, fontSize: 20, lineHeight: 22},
  mobileTabIconActive: {color: theme.accent},
  mobileTabLabel: {color: theme.textDim, fontSize: 10, fontWeight: '700'},
  mobileTabLabelActive: {color: theme.accent},
  hidden: {display: 'none'},
});
