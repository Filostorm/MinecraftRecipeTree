import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  ActivityIndicator,
  PanResponder,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {formatDropStat} from '../components/DropList';
import {ItemIcon, pixelated} from '../components/ItemIcon';
import {
  RADIAL_ROOT_ITEM_ICON_SIZE,
} from '../components/itemIconSizing';
import {MobSprite} from '../components/MobSprite';
import {
  PickerGroupProgress,
  PickerModal,
  PickerOption,
} from '../components/PickerModal';
import {slotSummary} from '../data/slotSummary';
import {
  applyIngredientSelections,
  selectSlotAlternative,
  type IngredientSelections,
} from '../data/ingredientAlternativeSelection';
import {signalTarget, useSignalSurface} from '../analytics/signal';
import {RecipePreviewImage} from '../components/RecipePreviewImage';
import {recipeImagePath, useData} from '../data/DataContext';
import {
  formatIngredientQuantity,
  shouldShowIngredientQuantity,
} from '../data/ingredientQuantities';
import {displayIngredientName} from '../data/ingredientTags';
import {isDefaultDisabledRecipeCategory} from '../data/recipeCategories';
import {
  loadCollapsedRecipeCategories,
  persistCollapsedRecipeCategories,
  toggleCollapsedRecipeCategory,
} from '../data/recipeCategoryPreferences';
import {useRecipeStages} from '../data/RecipeStageContext';
import {isRecipeVisibleForStages} from '../data/recipeStages';
import {isFluidContainerTransferRecipe} from '../data/recipeVisibility';
import {recipeDisplayTitle} from '../data/recipeTitles';
import {theme} from '../theme';
import {DropStat, Mob, Recipe, RecipeRef} from '../types';
import {useUi} from '../ui/UiContext';
import {
  EXPANDED_DISCLOSURE_CHEVRON,
  disclosureChevron,
} from '../ui/disclosureChevron';
import {
  COMPACT_LABEL_WIDTH,
  COMPACT_ITEM_SIZE,
  COMPACT_ROOT_DIAMOND_SIZE,
  COMPACT_ROOT_LABEL_GAP,
  COMPACT_ROOT_SIZE,
  ITEM_H,
  ITEM_W,
  ROOT_ATTACHED_ACTIONS_WIDTH,
  ROOT_SOURCE_ACTIONS_WIDTH,
  SOURCE_HEADER,
  attachedRootVisualX,
  layoutTree,
  recipeImageDisplay,
} from './layout';
import {
  RADIAL_ITEM_SIZE,
  RADIAL_ROOT_DIAMOND_SIZE,
  RADIAL_ROOT_SIZE,
  layoutRadialTree,
} from './radialLayout';
import {
  PreferredSource,
  PreferredSources,
  loadPreferredSources,
  persistPreferredSources,
} from './preferredSources';
import {preferredSourceTargets} from './preferencePropagation';
import {automaticGraphFitScale} from './fitScale';
import {
  capturePanGestureOrigin,
  graphDisplayTransform,
  graphPinchZoomFactor,
  graphViewportPointFromClient,
  graphWheelZoomFactor,
  transformForPanGesture,
} from './panGesture';
import type {GraphTransform, PanGestureOrigin} from './panGesture';
import {recordRecipeHistory} from './recipeHistory';
import {planRecipePickerChoices} from './recipePickerPlan';
import {
  materialInputSummary,
  recipeChildrenForDirection,
  usageGraphStart,
  type GraphDirection,
} from './direction';
import {
  clearGraphSession,
  loadGraphSession,
  parseGraphSession,
  persistGraphSession,
  serializeGraphSession,
  type GraphSession,
  type StoredGraphSelection,
} from './graphSession';
import {
  buildPortableTree,
  parsePortableTree,
  portableSelectionAsStored,
  type PortableTreeSelection,
} from './portableTree';
import {
  pickPortableTreeFile,
  savePortableTreeToInstance,
  sharePortableTree,
} from './portableTreeTransfer';
import {TreeShareModal} from './TreeShareModal';
import {
  createDeferredRecipeSourceResolver,
  deferredRecipeExpansionNodes,
  duplicateRecipeExpansions,
  findRecipeExpansionOwner,
  recipeExpansionFromSource,
  recipeExpansionIdentity,
} from './expansionOwnership';
import {isRecursiveItemNode, makeRoot} from './model';
import type {
  DeferredRecipeExpansion,
  ItemTreeNode,
  SourceTreeNode,
} from './model';
import {
  buildTreeTotalsCsv,
  downloadBlob,
  safeExportFilename,
} from './treeExports';
import {calculateTreeTotals, requiredAmountFor} from './treeTotals';
import type {
  NodeByproductCoverage,
  TreeCalculation,
  TreeTotal,
  TreeTotals,
} from './treeTotals';
import {visibleGraphElements} from './viewportCulling';
import {
  findTreeTotalTarget,
  type TreeTotalTargetKind,
} from './treeTotalTargets';
import {
  estimateParallelMachines,
  recipeCycleSeconds,
  selectedRecipeOutput,
  type ProductionPlan,
} from './machineParallels';

/** One way to obtain an item: craft it, kill for it, or mine for it. */
type SourceChoice =
  | {
      t: 'recipe';
      ref: RecipeRef;
      allowFluidTransfer?: true;
      ingredientSelections?: IngredientSelections;
    }
  | {t: 'mob'; mob: Mob; stat: DropStat}
  | {t: 'block'; blockKey: string; stat: DropStat};

interface PickerEntry {
  option: PickerOption;
  choice: SourceChoice;
  recipe?: Recipe;
}

type RecipeSourceChoice = Extract<SourceChoice, {t: 'recipe'}>;

interface PickerState {
  requestId: number;
  direction: GraphDirection;
  title: string;
  standardEntries: PickerEntry[];
  fluidTransferEntries: PickerEntry[];
  showFluidTransfers: boolean;
  identifiedFluidTransferCount: number;
  remainingRecipeChoices: Record<string, RecipeSourceChoice[]>;
  recipeGroupProgress: Record<string, PickerGroupProgress>;
  target: ItemTreeNode;
  byproductCoverage?: NodeByproductCoverage;
  rememberSource: boolean;
  productionPlan?: ProductionPlan;
  collapsedGroupKeys: Set<string>;
}

function visiblePickerEntries(
  picker: PickerState,
  hiddenRecipeStages: ReadonlySet<string>,
): PickerEntry[] {
  const visibleStandard = picker.standardEntries.filter(
    entry =>
      !entry.recipe ||
      isRecipeVisibleForStages(entry.recipe, hiddenRecipeStages),
  );
  if (!picker.showFluidTransfers) return visibleStandard;
  return [
    ...visibleStandard,
    ...picker.fluidTransferEntries.filter(
      entry =>
        !entry.recipe ||
        isRecipeVisibleForStages(entry.recipe, hiddenRecipeStages),
    ),
  ];
}

function pickerRecipeStageCounts(picker: PickerState): {stage: string; count: number}[] {
  const counts = new Map<string, number>();
  for (const entry of [...picker.standardEntries, ...picker.fluidTransferEntries]) {
    const stage = entry.recipe?.stage;
    if (stage) counts.set(stage, (counts.get(stage) ?? 0) + 1);
  }
  return [...counts.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([stage, count]) => ({stage, count}));
}

function preferredSourceFromChoice(choice: SourceChoice): PreferredSource {
  if (choice.t === 'recipe') {
    return {
      t: 'recipe',
      ref: choice.ref,
      ...(choice.allowFluidTransfer ? {allowFluidTransfer: true as const} : {}),
      ...(choice.ingredientSelections
        ? {ingredientSelections: {...choice.ingredientSelections}}
        : {}),
    };
  }
  if (choice.t === 'mob') return {t: 'mob', mobId: choice.mob.id};
  return {t: 'block', blockKey: choice.blockKey};
}

function choiceMatchesPreference(choice: SourceChoice, preferred: PreferredSource): boolean {
  if (choice.t !== preferred.t) return false;
  if (choice.t === 'recipe' && preferred.t === 'recipe') {
    return choice.ref[0] === preferred.ref[0] && choice.ref[1] === preferred.ref[1];
  }
  if (choice.t === 'mob' && preferred.t === 'mob') return choice.mob.id === preferred.mobId;
  return choice.t === 'block' && preferred.t === 'block' && choice.blockKey === preferred.blockKey;
}

function releaseByproductFulfillments(
  root: ItemTreeNode | null,
  removedNode: ItemTreeNode,
): void {
  const removedSourceIds = new Set<string>();
  const removedStack = [removedNode];
  while (removedStack.length > 0) {
    const current = removedStack.pop()!;
    if (!current.source) continue;
    removedSourceIds.add(current.source.id);
    for (const child of current.source.inputs) removedStack.push(child);
  }
  if (removedSourceIds.size === 0 || !root) return;

  let releasedAmount = 0;
  const treeStack = [root];
  while (treeStack.length > 0) {
    const current = treeStack.pop()!;
    const fulfillment = current.byproductFulfillment;
    if (fulfillment) {
      const retainedAllocations = fulfillment.allocations.filter(allocation => {
        if (!removedSourceIds.has(allocation.producerSourceId)) return true;
        releasedAmount += allocation.amount;
        return false;
      });
      const retainedAmount = retainedAllocations.reduce(
        (sum, allocation) => sum + allocation.amount,
        0,
      );
      if (retainedAmount > 0) {
        current.byproductFulfillment = {
          creditedAmount: retainedAmount,
          allocations: retainedAllocations,
        };
      } else {
        current.byproductFulfillment = undefined;
      }
    }
    for (const child of current.source?.inputs ?? []) treeStack.push(child);
  }
  if (releasedAmount > 0) {
    console.info('Byproduct fulfillment was released because its producing recipe left the tree.', {
      releasedAmount,
      removedProducerCount: removedSourceIds.size,
    });
  }
}

/** Dragging the canvas must never start a text selection (web). */
const noSelect = Platform.OS === 'web' ? ({userSelect: 'none'} as unknown as object) : null;
const COMPACT_MODE_KEY = 'graphCompactMode';
const RADIAL_LAYOUT_KEY = 'graphRadialLayout';
const LEGACY_PACKED_LAYOUT_KEY = 'graphPackedLayout';
const USE_BYPRODUCTS_KEY = 'graphUseByproducts';
const EXPAND_RECIPES_ONCE_KEY = 'graphExpandRecipesOnce';
const MAX_RECIPE_PICKER_CHOICES = 40;
const RECIPE_PICKER_GROUP_PAGE = 40;
const GRAPH_EXPORT_PADDING = 48;
const GRAPH_EXPORT_PIXEL_RATIO = 3;

function blockRecursiveExpansion(node: ItemTreeNode, interaction: string): boolean {
  if (!isRecursiveItemNode(node)) return false;
  console.info('Recursive graph input expansion was blocked.', {
    nodeId: node.id,
    itemKey: node.key,
    interaction,
    ancestorDepth: node.ancestors.length,
  });
  return true;
}

function recipeRefKey([categoryIndex, recipeIndex]: RecipeRef): string {
  return `${categoryIndex}:${recipeIndex}`;
}

function nodeForStoredSelection(
  root: ItemTreeNode,
  selection: StoredGraphSelection,
  restoredNodesByPath: ReadonlyMap<string, ItemTreeNode>,
): ItemTreeNode {
  if (selection.path.length === 0) {
    if (root.key !== selection.itemKey) {
      throw new Error(
        `Saved graph root resolved to ${JSON.stringify(root.key)} instead of ${JSON.stringify(selection.itemKey)}.`,
      );
    }
    return root;
  }

  const parentPath = selection.path.slice(0, -1).join('.');
  const parent = restoredNodesByPath.get(parentPath);
  if (!parent) {
    throw new Error(
      `Saved graph parent path ${parentPath} was not reconstructed.`,
    );
  }
  const storedChildIndex = selection.path[selection.path.length - 1];
  const indexedChild = parent.source?.inputs[storedChildIndex];
  if (indexedChild?.key === selection.itemKey) return indexedChild;

  const matchingChildren = (parent.source?.inputs ?? []).filter(
    child => child.key === selection.itemKey,
  );
  if (matchingChildren.length === 1) {
    console.info(
      'A saved graph path was remapped after presentation-only recipe inputs were removed.',
      {
        storedPath: selection.path,
        itemKey: selection.itemKey,
        storedChildIndex,
        reconstructedChildIndex: parent.source!.inputs.indexOf(matchingChildren[0]),
      },
    );
    return matchingChildren[0];
  }
  throw new Error(
    `Saved graph path ${selection.path.join('.')} could not uniquely resolve ` +
      `${JSON.stringify(selection.itemKey)} in the reconstructed parent.`,
  );
}

function loadCompactMode(): boolean {
  try {
    return globalThis.localStorage?.getItem(COMPACT_MODE_KEY) === '1';
  } catch (error) {
    console.error('Compact graph mode could not be loaded from localStorage.', error);
    return false;
  }
}

function loadRadialLayout(): boolean {
  try {
    const storage = globalThis.localStorage;
    const saved = storage?.getItem(RADIAL_LAYOUT_KEY);
    if (saved !== null && saved !== undefined) return saved !== '0';
    const legacyPacked = storage?.getItem(LEGACY_PACKED_LAYOUT_KEY);
    if (legacyPacked !== null && legacyPacked !== undefined) {
      console.info('Migrating the Packed graph preference to Radial mode.');
      storage?.setItem(RADIAL_LAYOUT_KEY, legacyPacked);
      return legacyPacked !== '0';
    }
    return true;
  } catch (error) {
    console.error('Radial graph layout could not be loaded from localStorage.', error);
    return true;
  }
}

function loadUseByproducts(): boolean {
  try {
    return globalThis.localStorage?.getItem(USE_BYPRODUCTS_KEY) === '1';
  } catch (error) {
    console.error('Byproduct-credit preference could not be loaded from localStorage.', error);
    return false;
  }
}

function loadExpandRecipesOnce(): boolean {
  try {
    return globalThis.localStorage?.getItem(EXPAND_RECIPES_ONCE_KEY) === '1';
  } catch (error) {
    console.error('Expand-once graph preference could not be loaded from localStorage.', error);
    return false;
  }
}

function nodeDepthBucket(
  node: ItemTreeNode,
): 'root' | 'depth-1' | 'depth-2' | 'depth-3-plus' {
  if (node.id === 'root' || node.ancestors.length === 0) return 'root';
  if (node.ancestors.length === 1) return 'depth-1';
  if (node.ancestors.length === 2) return 'depth-2';
  return 'depth-3-plus';
}

export function GraphScreen({
  interfaceZoom = 1,
  showGraphControls,
  onToggleGraphControls,
  controlsToggleInHeader = false,
}: {
  interfaceZoom?: number;
  showGraphControls: boolean;
  onToggleGraphControls(): void;
  controlsToggleInHeader?: boolean;
}) {
  const data = useData();
  const {
    hiddenStages: hiddenRecipeStages,
    toggleStage: toggleRecipeStage,
  } = useRecipeStages();
  const {
    graphRootKey,
    graphRequestId,
    graphRecipeRef,
    graphDirection,
    changeGraphDirection,
    openRecipeInGraph,
    restoreGraph,
    openItem,
    tab,
    setTab,
    animateMobs,
  } = useUi();

  const [root, setRoot] = useState<ItemTreeNode | null>(null);
  const rootRef = useRef<ItemTreeNode | null>(null);
  rootRef.current = root;
  const [version, setVersion] = useState(0);
  const bump = useCallback(() => setVersion(v => v + 1), []);
  const [picker, setPicker] = useState<PickerState | null>(null);
  const [pickerLookup, setPickerLookup] = useState<{
    requestId: number;
    title: string;
  } | null>(null);
  const pickerRef = useRef<PickerState | null>(null);
  pickerRef.current = picker;
  const [showRootActions, setShowRootActions] = useState(false);
  const [showTreeShare, setShowTreeShare] = useState(false);
  useEffect(() => setShowRootActions(false), [graphRequestId]);
  useEffect(() => {
    if (tab !== 'graph') setShowRootActions(false);
  }, [tab]);
  useSignalSurface(
    tab === 'graph' && picker
      ? 'graph/source-picker'
      : tab === 'graph' && pickerLookup
        ? 'graph/source-lookup'
        : tab,
    tab === 'graph' && (picker || pickerLookup) ? 'modal' : 'screen',
  );
  const pickerGroupLoadsRef = useRef(new Set<string>());
  const pickerRequestIdRef = useRef(0);
  const pendingRootChoiceRef = useRef<{
    key: string;
    direction: GraphDirection;
    choice: SourceChoice;
  } | null>(null);
  const pendingGraphSessionRef = useRef<GraphSession | null>(null);
  const graphSessionRestoreAttemptedRef = useRef(false);
  const restoringGraphSessionRef = useRef(false);
  const [compactMode, setCompactMode] = useState(loadCompactMode);
  const [radialLayout, setRadialLayout] = useState(loadRadialLayout);
  const [showTreeTotals, setShowTreeTotals] = useState(true);
  const [useByproducts, setUseByproducts] = useState(loadUseByproducts);
  const [expandRecipesOnce, setExpandRecipesOnce] = useState(loadExpandRecipesOnce);
  const expandRecipesOnceRef = useRef(expandRecipesOnce);
  expandRecipesOnceRef.current = expandRecipesOnce;
  const pendingRecipeExpansionOwnersRef = useRef(new Map<string, ItemTreeNode>());
  const [exportingTree, setExportingTree] = useState(false);
  const [exportMessage, setExportMessage] = useState<string | null>(null);
  const [preferredSources, setPreferredSources] =
    useState<PreferredSources>(loadPreferredSources);
  const preferredSourcesRef = useRef(preferredSources);
  preferredSourcesRef.current = preferredSources;

  const [transform, setTransform] = useState<GraphTransform>({x: 60, y: 60, scale: 1});
  const transformRef = useRef(transform);
  transformRef.current = transform;
  const displayTransform = graphDisplayTransform(
    transform,
    Platform.OS === 'web' && typeof window !== 'undefined' ? window.devicePixelRatio : 1,
  );
  const applyTransform = useCallback((next: GraphTransform) => {
    // Gesture events may arrive before React commits the preceding render. Keep
    // the imperative reference synchronized so every event sees the newest transform.
    transformRef.current = next;
    setTransform(next);
  }, []);
  const viewportRef = useRef({w: 0, h: 0});
  const [viewportSize, setViewportSize] = useState({w: 0, h: 0});
  const needsFitRef = useRef(false);
  const wrapRef = useRef<View>(null);
  const anchorRef = useRef<View>(null);

  const recipesFor = useCallback(
    (key: string): RecipeRef[] => {
      const all = (data.index[key]?.p ?? []).filter(
        r =>
          !data.metaCategories.has(r[0]) &&
          !isDefaultDisabledRecipeCategory(data.categories[r[0]]),
      );
      // Prefer real recipes; then smithing/trading; anvil repairs only as a last resort.
      const primary = all.filter(r => !data.secondaryCategories.has(r[0]));
      if (primary.length > 0) return primary;
      const nonRepair = all.filter(r => !data.repairCategories.has(r[0]));
      return nonRepair.length > 0 ? nonRepair : all;
    },
    [
      data.index,
      data.categories,
      data.metaCategories,
      data.secondaryCategories,
      data.repairCategories,
    ],
  );

  const usagesFor = useCallback(
    (key: string): RecipeRef[] => {
      const all = (data.index[key]?.u ?? []).filter(
        ref =>
          !data.metaCategories.has(ref[0]) &&
          !isDefaultDisabledRecipeCategory(data.categories[ref[0]]),
      );
      const primary = all.filter(ref => !data.secondaryCategories.has(ref[0]));
      if (primary.length > 0) return primary;
      const nonRepair = all.filter(ref => !data.repairCategories.has(ref[0]));
      return nonRepair.length > 0 ? nonRepair : all;
    },
    [
      data.index,
      data.categories,
      data.metaCategories,
      data.secondaryCategories,
      data.repairCategories,
    ],
  );

  const recipeRefsFor = useCallback(
    (key: string, direction: GraphDirection = graphDirection): RecipeRef[] =>
      direction === 'outputs' ? usagesFor(key) : recipesFor(key),
    [graphDirection, recipesFor, usagesFor],
  );

  /** Direction-appropriate recipe choices, plus physical sources for ingredient trees. */
  const choicesFor = useCallback(
    (key: string, direction: GraphDirection = graphDirection): SourceChoice[] => {
      const recipes = recipeRefsFor(key, direction).map(
        ref => ({t: 'recipe', ref}) as SourceChoice,
      );
      if (direction === 'outputs') return recipes;
      return [
        ...recipes,
        ...(data.minedFrom.get(key) ?? []).map(
          ({blockKey, stat}) => ({t: 'block', blockKey, stat}) as SourceChoice,
        ),
        ...(data.droppedByMobs.get(key) ?? []).map(
          ({mob, stat}) => ({t: 'mob', mob, stat}) as SourceChoice,
        ),
      ];
    },
    [graphDirection, recipeRefsFor, data.minedFrom, data.droppedByMobs],
  );

  const preferredSourceFor = useCallback(
    (key: string): SourceChoice | null => {
      if (graphDirection === 'outputs') return null;
      const preferred = preferredSourcesRef.current[key];
      if (!preferred) return null;
      if (preferred.t === 'recipe') {
        const exists = (data.index[key]?.p ?? []).some(
          ref =>
            !data.metaCategories.has(ref[0]) &&
            !isDefaultDisabledRecipeCategory(data.categories[ref[0]]) &&
            ref[0] === preferred.ref[0] &&
            ref[1] === preferred.ref[1],
        );
        return exists
          ? {
              t: 'recipe',
              ref: preferred.ref,
              ...(preferred.allowFluidTransfer ? {allowFluidTransfer: true as const} : {}),
              ...(preferred.ingredientSelections
                ? {ingredientSelections: {...preferred.ingredientSelections}}
                : {}),
            }
          : null;
      }
      return choicesFor(key).find(choice => choiceMatchesPreference(choice, preferred)) ?? null;
    },
    [graphDirection, choicesFor, data.index, data.categories, data.metaCategories],
  );

  const setPreferredSource = useCallback((key: string, choice: SourceChoice | null) => {
    const next = {...preferredSourcesRef.current};
    if (choice) next[key] = preferredSourceFromChoice(choice);
    else delete next[key];
    preferredSourcesRef.current = next;
    persistPreferredSources(next);
    setPreferredSources(next);
  }, []);

  const applyChoiceRef = useRef<((node: ItemTreeNode, choice: SourceChoice) => void) | null>(null);

  const expandRecipe = useCallback(
    async (
      node: ItemTreeNode,
      ref: RecipeRef,
      {
        allowFluidTransfer = false,
        expandPreferredChildren = true,
        recordHistory = true,
        ingredientSelections,
      }: {
        allowFluidTransfer?: boolean;
        expandPreferredChildren?: boolean;
        recordHistory?: boolean;
        ingredientSelections?: IngredientSelections;
      } = {},
    ): Promise<boolean> => {
      node.loading = true;
      bump();
      try {
        const [recipe] = await data.getRecipes([ref]);
        const cat = data.categories[ref[0]];
        if (!recipe || !cat || recipe.err) {
          console.error('The selected graph recipe is unavailable or invalid.', {
            itemKey: node.key,
            recipeRef: ref,
            recipeLoaded: !!recipe,
            categoryLoaded: !!cat,
            recipeError: recipe?.err,
          });
          return false;
        }
        const selectedRecipe = applyIngredientSelections(recipe, ingredientSelections);
        if (
          !allowFluidTransfer &&
          isFluidContainerTransferRecipe(selectedRecipe, data.itemsByKey)
        ) {
          console.info('A fluid container-transfer recipe was suppressed by the default filter.', {
            itemKey: node.key,
            recipeRef: ref,
          });
          const stored = preferredSourcesRef.current[node.key];
          if (
            stored?.t === 'recipe' &&
            stored.ref[0] === ref[0] &&
            stored.ref[1] === ref[1]
          ) {
            const next = {...preferredSourcesRef.current};
            delete next[node.key];
            preferredSourcesRef.current = next;
            persistPreferredSources(next);
            setPreferredSources(next);
          }
          return false;
        }
        if (graphDirection === 'outputs') {
          const anchor = materialInputSummary(selectedRecipe).find(
            input =>
              input.key === node.key || input.alternatives.includes(node.key),
          );
          if (!anchor) {
            console.error('An output-directed recipe does not use its graph anchor item.', {
              itemKey: node.key,
              recipeRef: ref,
            });
            return false;
          }
          node.amount = anchor.amount;
        } else if (node.id === 'root') {
          const output = slotSummary(selectedRecipe.out).find(
            candidate =>
              candidate.key === node.key || candidate.alternatives.includes(node.key),
          );
          if (!output) {
            console.error('An ingredient-directed root recipe does not produce its graph root item.', {
              itemKey: node.key,
              recipeRef: ref,
            });
            return false;
          }
          node.amount = output.amount;
        }
        const sourceId = `${node.id}.s`;
        const childSpecs = recipeChildrenForDirection(selectedRecipe, graphDirection);
        const children = childSpecs.map((spec, i) => {
          const child: ItemTreeNode = {
            id: `${sourceId}.${i}`,
            key: spec.key,
            amount: spec.amount,
            variantCount: spec.variants,
            alternatives: spec.alternatives,
            tag: spec.tag,
            nonConsumed: spec.nonConsumed,
            consumptionProbability:
              spec.probabilityRole === 'consume' && !spec.nonConsumed
                ? spec.probability
                : undefined,
            productionProbability:
              spec.probabilityRole === 'produce' ? spec.probability : undefined,
            ancestors: [...node.ancestors, node.key],
            cyclic: node.ancestors.includes(spec.key) || spec.key === node.key,
          };
          return child;
        });
        node.source = {
          id: sourceId,
          kind: 'recipe',
          ref,
          recipe: selectedRecipe,
          dir: cat.dir,
          catTitle: recipeDisplayTitle(cat.title, recipe),
          direction: graphDirection,
          ingredientSelections:
            ingredientSelections && Object.keys(ingredientSelections).length > 0
              ? {...ingredientSelections}
              : undefined,
          allowFluidTransfer: allowFluidTransfer || undefined,
          inputs: children,
        };
        if (node.id === 'root' && recordHistory) {
          recordRecipeHistory(data.descriptor, {
            itemKey: node.key,
            ref,
            title: recipeDisplayTitle(cat.title, recipe),
            recipeId: recipe.id ?? null,
            openedAt: Date.now(),
            direction: graphDirection,
          });
        }
        if (expandPreferredChildren) {
          for (const child of children) {
            if (child.cyclic) continue;
            const preferred =
              graphDirection === 'inputs' ? preferredSourceFor(child.key) : null;
            if (preferred) applyChoiceRef.current?.(child, preferred);
          }
        }
        return true;
      } catch (error) {
        console.error('The selected graph recipe could not be expanded.', error);
        return false;
      } finally {
        node.loading = false;
        bump();
      }
    },
    [data, bump, graphDirection, preferredSourceFor],
  );

  /** Replace every eligible occurrence with the newly preferred source. */
  const applyPreferredSourceAcrossTree = useCallback(
    (target: ItemTreeNode, choice: SourceChoice) => {
      const currentRoot = rootRef.current;
      const matches = preferredSourceTargets(currentRoot, target);
      for (const match of matches) {
        if (match.source) releaseByproductFulfillments(currentRoot, match);
        match.source = undefined;
      }
      for (const match of matches) {
        applyChoiceRef.current?.(match, choice);
      }
    },
    [],
  );

  const applyRecipeChoice = useCallback(
    async (node: ItemTreeNode, choice: RecipeSourceChoice): Promise<boolean> => {
      const identity = recipeExpansionIdentity(node.key, graphDirection, choice);
      if (expandRecipesOnceRef.current) {
        const owner =
          findRecipeExpansionOwner(
            rootRef.current,
            node.key,
            graphDirection,
            choice,
            node,
          ) ?? pendingRecipeExpansionOwnersRef.current.get(identity);
        if (owner && owner !== node) {
          node.source = undefined;
          node.deferredRecipeExpansion = {
            ref: [...choice.ref],
            ...(choice.allowFluidTransfer ? {allowFluidTransfer: true as const} : {}),
            ...(choice.ingredientSelections
                ? {ingredientSelections: {...choice.ingredientSelections}}
                : {}),
          };
          // Deferring a duplicate changes expansion ownership, not the user's
          // viewport intent. Preserve the current pan and zoom transform.
          bump();
          return true;
        }
      }

      node.deferredRecipeExpansion = undefined;
      pendingRecipeExpansionOwnersRef.current.set(identity, node);
      try {
        const expanded = await expandRecipe(node, choice.ref, {
          allowFluidTransfer: choice.allowFluidTransfer === true,
          ingredientSelections: choice.ingredientSelections,
        });
        if (!expanded) {
          console.error('The requested recipe expansion could not claim its graph position.', {
            nodeId: node.id,
            itemKey: node.key,
            recipeRef: choice.ref,
          });
        }
        return expanded;
      } finally {
        if (pendingRecipeExpansionOwnersRef.current.get(identity) === node) {
          pendingRecipeExpansionOwnersRef.current.delete(identity);
        }
      }
    },
    [bump, expandRecipe, graphDirection],
  );

  const applyChoice = useCallback(
    (node: ItemTreeNode, choice: SourceChoice) => {
      if (blockRecursiveExpansion(node, 'apply source choice')) return;
      if (choice.t === 'recipe') {
        void applyRecipeChoice(node, choice);
        return;
      }
      node.deferredRecipeExpansion = undefined;
      const sourceId = `${node.id}.s`;
      node.source =
        choice.t === 'mob'
          ? {id: sourceId, kind: 'mob', mob: choice.mob, stat: choice.stat, inputs: []}
          : {id: sourceId, kind: 'block', blockKey: choice.blockKey, stat: choice.stat, inputs: []};
      bump();
    },
    [applyRecipeChoice, bump],
  );
  applyChoiceRef.current = applyChoice;

  const restoreExpandedGraph = useCallback(
    async (newRoot: ItemTreeNode, session: GraphSession) => {
      try {
        if (session.direction !== graphDirection) {
          throw new Error(
            `Saved graph direction ${session.direction} does not match active direction ${graphDirection}.`,
          );
        }
        const restoredNodesByPath = new Map<string, ItemTreeNode>();
        newRoot.productionPlan = session.productionPlan
          ? {...session.productionPlan}
          : undefined;
        for (const selection of session.selections) {
          const node = nodeForStoredSelection(
            newRoot,
            selection,
            restoredNodesByPath,
          );
          restoredNodesByPath.set(selection.path.join('.'), node);
          if (isRecursiveItemNode(node)) {
            throw new Error(
              `Saved graph tries to expand recursive item ${JSON.stringify(node.key)}.`,
            );
          }
          if (selection.source.kind === 'recipe') {
            if (selection.deferred) {
              node.deferredRecipeExpansion = {
                ref: [...selection.source.ref],
                ...(selection.source.allowFluidTransfer
                  ? {allowFluidTransfer: true as const}
                  : {}),
                ...(selection.source.ingredientSelections
                  ? {ingredientSelections: {...selection.source.ingredientSelections}}
                  : {}),
              };
              continue;
            }
            const expanded = await expandRecipe(node, selection.source.ref, {
              allowFluidTransfer: selection.source.allowFluidTransfer === true,
              ingredientSelections: selection.source.ingredientSelections,
              expandPreferredChildren: false,
              recordHistory: false,
            });
            if (!expanded) {
              throw new Error(
                `Saved recipe ${selection.source.ref.join(':')} could not be reconstructed.`,
              );
            }
            continue;
          }
          const storedSource = selection.source;
          const sourceChoice = choicesFor(node.key, session.direction).find(choice =>
            storedSource.kind === 'mob'
              ? choice.t === 'mob' && choice.mob.id === storedSource.mobId
              : choice.t === 'block' && choice.blockKey === storedSource.blockKey,
          );
          if (!sourceChoice) {
            throw new Error(
              `Saved ${selection.source.kind} source for ${JSON.stringify(node.key)} is unavailable.`,
            );
          }
          applyChoice(node, sourceChoice);
        }
        needsFitRef.current = true;
      } catch (error) {
        console.error('The saved graph could not be reconstructed; its snapshot was discarded.', error);
        clearGraphSession(data.descriptor);
        const cleanRoot = makeRoot(session.rootKey);
        rootRef.current = cleanRoot;
        setRoot(cleanRoot);
        needsFitRef.current = true;
      } finally {
        restoringGraphSessionRef.current = false;
        bump();
      }
    },
    [applyChoice, bump, choicesFor, data.descriptor, expandRecipe, graphDirection],
  );

  const pickerEntryFor = useCallback(
    (
      targetKey: string,
      choice: SourceChoice,
      recipe?: Recipe,
      direction: GraphDirection = graphDirection,
    ): PickerEntry => {
      const currentPreferred =
        direction === 'inputs' ? preferredSourcesRef.current[targetKey] : undefined;
      const favoritePrefix =
        currentPreferred && choiceMatchesPreference(choice, currentPreferred) ? '★ ' : '';
      const itemName = (key: string) => data.itemsByKey.get(key)?.n ?? key;
      if (choice.t === 'recipe') {
        const presentedRecipe = recipe
          ? applyIngredientSelections(recipe, choice.ingredientSelections)
          : recipe;
        const [categoryIndex, recipeIndex] = choice.ref;
        const category = data.categories[categoryIndex];
        if (!category) {
          console.error('A recipe-source picker option references a missing category.', {
            itemKey: targetKey,
            categoryIndex,
            recipeIndex,
          });
        }
        const title =
          presentedRecipe && category
            ? recipeDisplayTitle(category.title, presentedRecipe)
            : category?.title;
        return {
          choice,
          recipe: presentedRecipe,
          option: {
            label: `${favoritePrefix}${title ?? `category ${categoryIndex}`}`,
            groupKey: category?.id ?? `recipe-category:${categoryIndex}`,
            groupLabel: category?.title ?? `Recipe category ${categoryIndex}`,
            sublabel:
              [
                presentedRecipe?.id,
                presentedRecipe?.stage
                  ? `Requires stage ${presentedRecipe.stage}`
                  : undefined,
                presentedRecipe && !presentedRecipe.img
                  ? 'JEI layout preview unavailable'
                  : undefined,
              ]
                .filter((value): value is string => !!value)
                .join(' · ') || undefined,
            imageUri:
              presentedRecipe?.img && category
                ? data.imageUrl(recipeImagePath(category.dir, presentedRecipe.img))
                : undefined,
            imageBackgroundUri:
              presentedRecipe?.bg && category
                ? data.imageUrl(recipeImagePath(category.dir, presentedRecipe.bg))
                : undefined,
            imageW: presentedRecipe?.w,
            imageH: presentedRecipe?.h,
            inputs:
              recipe && direction === 'inputs'
                ? materialInputSummary(recipe).map(input => {
                    const selectedEntry = Object.entries(
                      choice.ingredientSelections ?? {},
                    ).find(([selectionKey]) =>
                      input.alternatives.includes(selectionKey),
                    );
                    const selected = selectedEntry?.[1];
                    return selected
                      ? selectSlotAlternative(
                          {...input, selectionKey: selectedEntry[0]},
                          selected,
                        )
                      : input;
                  })
                : undefined,
            outputs:
              presentedRecipe && direction === 'outputs'
                ? slotSummary(presentedRecipe.out)
                : undefined,
            durationTicks: presentedRecipe?.durationTicks,
            cycleSeconds:
              presentedRecipe && category
                ? recipeCycleSeconds(presentedRecipe, category.id) ?? undefined
                : undefined,
            outputPerCycle:
              presentedRecipe && direction === 'inputs'
                ? selectedRecipeOutput(presentedRecipe, targetKey) ?? undefined
                : undefined,
            machineKey: category?.catalysts[0],
            machineLabel: category?.catalysts[0]
              ? itemName(category.catalysts[0])
              : undefined,
          },
        };
      }
      if (choice.t === 'block') {
        return {
          choice,
          option: {
            label: `${favoritePrefix}Mining · ${itemName(choice.blockKey)}`,
            groupKey: 'physical:mining',
            groupLabel: 'Mining',
            sublabel: formatDropStat(choice.stat),
          },
        };
      }
      return {
        choice,
        option: {
          label: `${favoritePrefix}Mob drop · ${choice.mob.n}`,
          groupKey: 'physical:mob-drops',
          groupLabel: 'Mob drops',
          sublabel: formatDropStat(choice.stat),
        },
      };
    },
    [data, graphDirection],
  );

  const openPicker = useCallback(
    async (
      target: ItemTreeNode,
      byproductCoverage?: NodeByproductCoverage,
      direction: GraphDirection = graphDirection,
    ) => {
      if (blockRecursiveExpansion(target, 'open source picker')) return;
      const requestId = ++pickerRequestIdRef.current;
      const itemName = data.itemsByKey.get(target.key)?.n ?? target.key;
      const lookupTitle =
        direction === 'outputs' ? `Find uses for ${itemName}` : `Find recipes for ${itemName}`;
      setPickerLookup({requestId, title: lookupTitle});
      try {
      const currentPreferred =
        direction === 'inputs' ? preferredSourcesRef.current[target.key] : undefined;
      const allChoices = choicesFor(target.key, direction);
      const physicalChoices = allChoices.filter(choice => choice.t !== 'recipe');
      const recipeChoices = allChoices.filter(
        (choice): choice is RecipeSourceChoice => choice.t === 'recipe',
      );
      const plan = planRecipePickerChoices(
        recipeChoices,
        data.categories,
        MAX_RECIPE_PICKER_CHOICES,
      );
      const recipesByRef = new Map<string, Awaited<ReturnType<typeof data.getRecipes>>[number]>();
      const standardRecipeChoices: RecipeSourceChoice[] = [];
      const fluidTransferChoices: RecipeSourceChoice[] = [];
      const loadedRefKeys = new Set<string>();
      let identifiedFluidTransferCount = 0;
      const initialRecipes = await data.getRecipes(
        plan.initialChoices.map(choice => choice.ref),
      );
      plan.initialChoices.forEach((choice, index) => {
        const recipe = initialRecipes[index];
        const refKey = recipeRefKey(choice.ref);
        recipesByRef.set(refKey, recipe);
        loadedRefKeys.add(refKey);
        if (isFluidContainerTransferRecipe(recipe, data.itemsByKey)) {
          identifiedFluidTransferCount += 1;
          fluidTransferChoices.push({...choice, allowFluidTransfer: true});
        } else {
          standardRecipeChoices.push(choice);
        }
      });

      // Preserve a saved source even when it is a later variant in a staged group.
      if (currentPreferred?.t === 'recipe') {
        const preferredChoice = recipeChoices.find(choice =>
          choiceMatchesPreference(choice, currentPreferred),
        );
        if (preferredChoice && !recipesByRef.has(recipeRefKey(preferredChoice.ref))) {
          const [recipe] = await data.getRecipes([preferredChoice.ref]);
          const preferredRefKey = recipeRefKey(preferredChoice.ref);
          recipesByRef.set(preferredRefKey, recipe);
          loadedRefKeys.add(preferredRefKey);
          if (isFluidContainerTransferRecipe(recipe, data.itemsByKey)) {
            identifiedFluidTransferCount += 1;
            const explicitChoice = {...preferredChoice, allowFluidTransfer: true as const};
            fluidTransferChoices.push(explicitChoice);
          } else {
            standardRecipeChoices.push(preferredChoice);
          }
        }
      }

      const remainingRecipeChoices: Record<string, RecipeSourceChoice[]> = {};
      const recipeGroupProgress: Record<string, PickerGroupProgress> = {};
      for (const group of plan.groups) {
        const remaining = group.choices.filter(
          choice => !loadedRefKeys.has(recipeRefKey(choice.ref)),
        );
        remainingRecipeChoices[group.groupKey] = remaining;
        recipeGroupProgress[group.groupKey] = {
          loaded: group.choices.length - remaining.length,
          total: group.choices.length,
        };
      }

      const withCurrentPreference = (
        choice: RecipeSourceChoice,
      ): RecipeSourceChoice =>
        currentPreferred?.t === 'recipe' &&
        choiceMatchesPreference(choice, currentPreferred)
          ? {
              ...choice,
              ...(currentPreferred.allowFluidTransfer
                ? {allowFluidTransfer: true as const}
                : {}),
              ...(currentPreferred.ingredientSelections
                ? {
                    ingredientSelections: {
                      ...currentPreferred.ingredientSelections,
                    },
                  }
                : {}),
            }
          : choice;
      if (requestId !== pickerRequestIdRef.current) {
        console.info('A stale recipe-source picker request was discarded.', {
          itemKey: target.key,
          requestId,
          currentRequestId: pickerRequestIdRef.current,
        });
        return;
      }
      setPicker({
        requestId,
        direction,
        title:
          direction === 'outputs'
            ? `Use ${itemName} to produce`
            : `Obtain ${itemName}`,
        standardEntries: [
          ...physicalChoices.map(choice =>
            pickerEntryFor(target.key, choice, undefined, direction),
          ),
          ...standardRecipeChoices.map(choice =>
            pickerEntryFor(
              target.key,
              withCurrentPreference(choice),
              recipesByRef.get(recipeRefKey(choice.ref)),
              direction,
            ),
          ),
        ],
        fluidTransferEntries: fluidTransferChoices.map(choice =>
          pickerEntryFor(
            target.key,
            withCurrentPreference(choice),
            recipesByRef.get(recipeRefKey(choice.ref)),
            direction,
          ),
        ),
        showFluidTransfers: false,
        identifiedFluidTransferCount,
        remainingRecipeChoices,
        recipeGroupProgress,
        target,
        byproductCoverage,
        rememberSource: direction === 'inputs',
        productionPlan:
          target.id === 'root' && direction === 'inputs'
            ? target.productionPlan ?? {
                amount: Math.max(1, target.amount ?? 1),
                windowSeconds: 1,
              }
            : undefined,
        collapsedGroupKeys: loadCollapsedRecipeCategories(),
      });
      } finally {
        setPickerLookup(current =>
          current?.requestId === requestId ? null : current,
        );
      }
    },
    [data, choicesFor, graphDirection, pickerEntryFor],
  );

  const loadPickerRecipeGroup = useCallback(
    async (groupKey: string) => {
      const snapshot = pickerRef.current;
      const remaining = snapshot?.remainingRecipeChoices[groupKey] ?? [];
      if (!snapshot || remaining.length === 0 || pickerGroupLoadsRef.current.has(groupKey)) {
        return;
      }
      const batch = remaining.slice(0, RECIPE_PICKER_GROUP_PAGE);
      pickerGroupLoadsRef.current.add(groupKey);
      setPicker(current => {
        if (!current || current.requestId !== snapshot.requestId) return current;
        return {
          ...current,
          recipeGroupProgress: {
            ...current.recipeGroupProgress,
            [groupKey]: {...current.recipeGroupProgress[groupKey], loading: true},
          },
        };
      });
      try {
        const recipes = await data.getRecipes(batch.map(choice => choice.ref));
        const standardEntries: PickerEntry[] = [];
        const fluidTransferEntries: PickerEntry[] = [];
        let identifiedFluidTransferCount = 0;
        batch.forEach((choice, index) => {
          const recipe = recipes[index];
          if (isFluidContainerTransferRecipe(recipe, data.itemsByKey)) {
            identifiedFluidTransferCount += 1;
            const explicitChoice = {...choice, allowFluidTransfer: true as const};
            fluidTransferEntries.push(
              pickerEntryFor(
                snapshot.target.key,
                explicitChoice,
                recipe,
                snapshot.direction,
              ),
            );
          } else {
            standardEntries.push(
              pickerEntryFor(
                snapshot.target.key,
                choice,
                recipe,
                snapshot.direction,
              ),
            );
          }
        });
        setPicker(current => {
          if (!current || current.requestId !== snapshot.requestId) return current;
          const currentRemaining = current.remainingRecipeChoices[groupKey] ?? [];
          const loadedKeys = new Set(batch.map(choice => recipeRefKey(choice.ref)));
          const nextRemaining = currentRemaining.filter(
            choice => !loadedKeys.has(recipeRefKey(choice.ref)),
          );
          const progress = current.recipeGroupProgress[groupKey];
          return {
            ...current,
            standardEntries: [...current.standardEntries, ...standardEntries],
            fluidTransferEntries: [
              ...current.fluidTransferEntries,
              ...fluidTransferEntries,
            ],
            identifiedFluidTransferCount:
              current.identifiedFluidTransferCount + identifiedFluidTransferCount,
            remainingRecipeChoices: {
              ...current.remainingRecipeChoices,
              [groupKey]: nextRemaining,
            },
            recipeGroupProgress: {
              ...current.recipeGroupProgress,
              [groupKey]: {
                loaded: (progress?.loaded ?? 0) + batch.length,
                total: progress?.total ?? batch.length,
                loading: false,
              },
            },
          };
        });
      } catch (error) {
        console.error('Additional recipe-source variants could not be loaded.', {
          itemKey: snapshot.target.key,
          groupKey,
          requestedRecipes: batch.length,
          error,
        });
        setPicker(current => {
          if (!current || current.requestId !== snapshot.requestId) return current;
          return {
            ...current,
            recipeGroupProgress: {
              ...current.recipeGroupProgress,
              [groupKey]: {
                ...current.recipeGroupProgress[groupKey],
                loading: false,
              },
            },
          };
        });
      } finally {
        pickerGroupLoadsRef.current.delete(groupKey);
      }
    },
    [data, pickerEntryFor],
  );

  const togglePickerGroup = useCallback(
    (groupKey: string) => {
      setPicker(current => {
        if (!current) return current;
        const nextCollapsed = toggleCollapsedRecipeCategory(
          current.collapsedGroupKeys,
          groupKey,
        );
        if (data.categories.some(category => category.id === groupKey)) {
          const categoryIds = new Set(data.categories.map(category => category.id));
          persistCollapsedRecipeCategories(
            new Set([...nextCollapsed].filter(id => categoryIds.has(id))),
          );
        }
        return {...current, collapsedGroupKeys: nextCollapsed};
      });
    },
    [data.categories],
  );

  const openPickerWithErrorHandling = useCallback(
    (
      node: ItemTreeNode,
      byproductCoverage?: NodeByproductCoverage,
      direction: GraphDirection = graphDirection,
    ) => {
      void openPicker(node, byproductCoverage, direction).catch(error => {
        console.error('The recipe-source picker could not be opened.', error);
      });
    },
    [graphDirection, openPicker],
  );

  const cancelPickerLookup = useCallback(() => {
    pickerRequestIdRef.current += 1;
    setPickerLookup(null);
  }, []);

  useEffect(() => {
    if (tab !== 'graph' && pickerLookup) cancelPickerLookup();
  }, [cancelPickerLookup, pickerLookup, tab]);

  const updateRootRequestedAmount = useCallback(
    (requestedAmount: number) => {
      const currentRoot = rootRef.current;
      if (!currentRoot || !Number.isFinite(requestedAmount)) return;
      const amount = Math.min(1_000_000_000_000, Math.max(1, Math.floor(requestedAmount)));
      currentRoot.productionPlan = {
        ...currentRoot.productionPlan,
        amount,
        // Legacy saved graphs require this field. Parallel suggestions now target one cycle.
        windowSeconds: currentRoot.productionPlan?.windowSeconds ?? 1,
      };
      bump();
    },
    [bump],
  );

  const openRootPicker = useCallback(
    (direction: GraphDirection) => {
      const currentRoot = rootRef.current;
      if (!currentRoot) return;
      setShowRootActions(false);
      openPickerWithErrorHandling(currentRoot, undefined, direction);
    },
    [openPickerWithErrorHandling],
  );

  const applyOnlyChoice = useCallback(
    async (node: ItemTreeNode, choice: SourceChoice) => {
      if (graphDirection === 'outputs') {
        applyChoice(node, choice);
        return;
      }
      if (choice.t === 'recipe') {
        const [recipe] = await data.getRecipes([choice.ref]);
        if (
          isFluidContainerTransferRecipe(recipe, data.itemsByKey) ||
          (recipe.stage && hiddenRecipeStages.has(recipe.stage))
        ) {
          await openPicker(node);
          return;
        }
      }
      setPreferredSource(node.key, choice);
      applyPreferredSourceAcrossTree(node, choice);
    },
    [
      applyChoice,
      applyPreferredSourceAcrossTree,
      data,
      graphDirection,
      hiddenRecipeStages,
      openPicker,
      setPreferredSource,
    ],
  );

  const applyOnlyChoiceWithErrorHandling = useCallback(
    (node: ItemTreeNode, choice: SourceChoice) => {
      void applyOnlyChoice(node, choice).catch(error => {
        console.error('The only recipe source could not be classified and applied.', error);
      });
    },
    [applyOnlyChoice],
  );

  const releaseByproductFulfillmentsFromSubtree = useCallback(
    (removedNode: ItemTreeNode) => {
      releaseByproductFulfillments(rootRef.current, removedNode);
    },
    [],
  );

  const transferDeferredRecipeExpansion = useCallback(
    async (node: ItemTreeNode, expansion: DeferredRecipeExpansion) => {
      const owner = findRecipeExpansionOwner(
        rootRef.current,
        node.key,
        graphDirection,
        expansion,
        node,
      );
      if (!owner) {
        console.error('A deferred recipe node has no expanded owner; expanding it directly.', {
          nodeId: node.id,
          itemKey: node.key,
          recipeRef: expansion.ref,
        });
        node.deferredRecipeExpansion = undefined;
        await applyRecipeChoice(node, {t: 'recipe', ...expansion});
        return;
      }
      const ownerExpansion = recipeExpansionFromSource(owner.source);
      if (!ownerExpansion) {
        console.error('The expanded recipe owner has no transferable recipe metadata.', {
          ownerNodeId: owner.id,
          targetNodeId: node.id,
        });
        return;
      }

      releaseByproductFulfillmentsFromSubtree(owner);
      owner.source = undefined;
      owner.deferredRecipeExpansion = ownerExpansion;
      node.deferredRecipeExpansion = undefined;
      // Moving the visible occurrence of a recipe must not behave like Fit.
      bump();

      const expanded = await applyRecipeChoice(node, {t: 'recipe', ...expansion});
      if (expanded && node.source) return;

      console.error('Recipe expansion ownership transfer failed; restoring the previous owner.', {
        ownerNodeId: owner.id,
        targetNodeId: node.id,
        recipeRef: expansion.ref,
      });
      node.deferredRecipeExpansion = expansion;
      owner.deferredRecipeExpansion = undefined;
      const restored = await applyRecipeChoice(owner, {t: 'recipe', ...ownerExpansion});
      if (!restored || !owner.source) {
        console.error('The previous recipe expansion owner could not be restored.', {
          ownerNodeId: owner.id,
          recipeRef: ownerExpansion.ref,
        });
      }
    },
    [
      applyRecipeChoice,
      bump,
      graphDirection,
      releaseByproductFulfillmentsFromSubtree,
    ],
  );

  const onItemTap = useCallback(
    (node: ItemTreeNode) => {
      if (node.loading) return;
      if (blockRecursiveExpansion(node, 'tap graph node')) return;
      if (node.deferredRecipeExpansion) {
        void transferDeferredRecipeExpansion(node, node.deferredRecipeExpansion);
        return;
      }
      if (node.source) {
        const collapsedExpansion = recipeExpansionFromSource(node.source);
        if (expandRecipesOnceRef.current && collapsedExpansion) {
          const collapsedIdentity = recipeExpansionIdentity(
            node.key,
            graphDirection,
            collapsedExpansion,
          );
          for (const candidate of deferredRecipeExpansionNodes(rootRef.current)) {
            const deferred = candidate.deferredRecipeExpansion;
            if (
              deferred &&
              recipeExpansionIdentity(candidate.key, graphDirection, deferred) ===
                collapsedIdentity
            ) {
              candidate.deferredRecipeExpansion = undefined;
            }
          }
        }
        releaseByproductFulfillmentsFromSubtree(node);
        node.source = undefined;
        bump();
        return;
      }
      const choices = choicesFor(node.key);
      if (choices.length === 0) return;
      const preferred = preferredSourceFor(node.key);
      if (preferred) {
        applyChoice(node, preferred);
      } else if (choices.length === 1) {
        applyOnlyChoiceWithErrorHandling(node, choices[0]);
      } else {
        openPickerWithErrorHandling(node);
      }
    },
    [
      bump,
      applyChoice,
      openPickerWithErrorHandling,
      choicesFor,
      preferredSourceFor,
      applyOnlyChoiceWithErrorHandling,
      releaseByproductFulfillmentsFromSubtree,
      transferDeferredRecipeExpansion,
      graphDirection,
    ],
  );

  useEffect(() => {
    if (graphSessionRestoreAttemptedRef.current) return;
    graphSessionRestoreAttemptedRef.current = true;
    if (graphRootKey) return;
    const session = loadGraphSession(data.descriptor);
    if (!session) return;
    pendingGraphSessionRef.current = session;
    restoringGraphSessionRef.current = true;
    restoreGraph(session.rootKey, session.direction);
  }, [data.descriptor, graphRootKey, restoreGraph]);

  // (Re)build and refit for every request. The request id is intentionally
  // included so selecting the same item again still resets an off-screen or
  // previously expanded chart.
  useEffect(() => {
    if (!graphRootKey) return;
    const newRoot = makeRoot(graphRootKey);
    rootRef.current = newRoot;
    setRoot(newRoot);
    needsFitRef.current = true;
    const pendingGraphSession = pendingGraphSessionRef.current;
    if (pendingGraphSession) {
      pendingGraphSessionRef.current = null;
      if (
        pendingGraphSession.rootKey !== graphRootKey ||
        pendingGraphSession.direction !== graphDirection
      ) {
        console.error('The saved graph did not match the requested restoration root.', {
          savedRootKey: pendingGraphSession.rootKey,
          graphRootKey,
          savedDirection: pendingGraphSession.direction,
          graphDirection,
        });
        restoringGraphSessionRef.current = false;
        clearGraphSession(data.descriptor);
      } else {
        void restoreExpandedGraph(newRoot, pendingGraphSession);
        return;
      }
    }
    const pendingRootChoice = pendingRootChoiceRef.current;
    if (pendingRootChoice) {
      pendingRootChoiceRef.current = null;
      if (
        pendingRootChoice.key !== graphRootKey ||
        pendingRootChoice.direction !== graphDirection
      ) {
        console.error('A pending root recipe selection did not match the rebuilt graph.', {
          pendingItemKey: pendingRootChoice.key,
          graphRootKey,
          pendingDirection: pendingRootChoice.direction,
          graphDirection,
        });
      } else {
        applyChoice(newRoot, pendingRootChoice.choice);
        return;
      }
    }
    if (graphRecipeRef) {
      const requestedChoice: SourceChoice = {
        t: 'recipe',
        ref: graphRecipeRef,
        allowFluidTransfer: true,
      };
      if (graphDirection === 'inputs') {
        setPreferredSource(graphRootKey, requestedChoice);
      }
      applyChoice(newRoot, requestedChoice);
      return;
    }
    const choices = choicesFor(graphRootKey);
    const preferred = preferredSourceFor(graphRootKey);
    if (preferred) {
      applyChoice(newRoot, preferred);
    } else if (choices.length === 1) {
      const onlyChoice = choices[0];
      applyOnlyChoiceWithErrorHandling(newRoot, onlyChoice);
    }
  }, [
    graphRootKey,
    graphRequestId,
    graphRecipeRef,
    graphDirection,
    applyChoice,
    choicesFor,
    preferredSourceFor,
    data.descriptor,
    restoreExpandedGraph,
    setPreferredSource,
    applyOnlyChoiceWithErrorHandling,
  ]);

  useEffect(() => {
    if (!root || restoringGraphSessionRef.current) return;
    persistGraphSession(data.descriptor, root, graphDirection);
  }, [data.descriptor, graphDirection, root, version]);

  const graphLayout = useMemo(() => {
    if (!root) return {graph: null, fallback: null as string | null};
    if (!radialLayout) {
      return {
        graph: layoutTree(root, compactMode, true, showRootActions),
        fallback: null as string | null,
      };
    }
    try {
      return {
        graph: layoutRadialTree(
          root,
          compactMode,
          graphDirection === 'outputs'
            ? item => usagesFor(item.key).length === 0
            : undefined,
          true,
          showRootActions,
        ),
        fallback: null as string | null,
      };
    } catch (error) {
      console.error(
        'Radial layout could not place this tree; the standard large-tree layout is being used.',
        error,
      );
      return {
        graph: layoutTree(root, compactMode, true, showRootActions),
        fallback: 'This tree is too complex for Radial placement, so the standard layout is shown.',
      };
    }
  },
    [
      root,
      version,
      compactMode,
      radialLayout,
      graphDirection,
      usagesFor,
      showRootActions,
    ],
  );
  const graph = graphLayout.graph;
  const renderedGraph = useMemo(
    () =>
      graph
        ? exportingTree
          ? {nodes: graph.nodes, edges: graph.edges, culled: false}
          : visibleGraphElements(graph, transform, viewportSize)
        : null,
    [exportingTree, graph, transform, viewportSize],
  );
  const graphRef = useRef(graph);
  graphRef.current = graph;
  const treeTotals = useMemo(() => {
    if (!root || graphDirection === 'outputs') {
      return {
        inputs: [],
        prerequisites: [],
        byproductCredits: [],
        byproducts: [],
        requiredByNode: new Map(),
        byproductCoverageByNode: new Map(),
      } as TreeCalculation;
    }
    const totals = calculateTreeTotals(root, useByproducts, {
      resolveDeferredRecipeSource: expandRecipesOnce
        ? createDeferredRecipeSourceResolver(root, graphDirection)
        : undefined,
    });
    const byName = (a: TreeTotal, b: TreeTotal) =>
      (data.itemsByKey.get(a.key)?.n ?? a.key).localeCompare(data.itemsByKey.get(b.key)?.n ?? b.key);
    totals.inputs.sort(byName);
    totals.prerequisites.sort(byName);
    totals.byproductCredits.sort(byName);
    totals.byproducts.sort(byName);
    return totals;
  }, [
    root,
    version,
    data.itemsByKey,
    graphDirection,
    useByproducts,
    expandRecipesOnce,
  ]);
  const displayedAmountFor = useCallback(
    (node: ItemTreeNode) =>
      graphDirection === 'outputs'
        ? node.amount === undefined
          ? 1
          : node.amount
        : requiredAmountFor(node, treeTotals),
    [graphDirection, treeTotals],
  );

  const [focusedSourceId, setFocusedSourceId] = useState<string | null>(null);
  const focusByproductProducer = useCallback(
    (sourceId: string) => {
      const laidSource = graphRef.current?.nodes.find(node => node.id === sourceId);
      const viewport = viewportRef.current;
      if (!laidSource || viewport.w <= 0 || viewport.h <= 0) {
        console.error('The byproduct-producing recipe could not be located in the current graph.', {
          sourceId,
        });
        return;
      }
      const scale = transformRef.current.scale;
      applyTransform({
        x: viewport.w / 2 - (laidSource.x + laidSource.w / 2) * scale,
        y: viewport.h / 2 - (laidSource.y + laidSource.h / 2) * scale,
        scale,
      });
      setFocusedSourceId(sourceId);
    },
    [applyTransform],
  );

  const handleCollapsedIngredientTap = useCallback(
    (node: ItemTreeNode, defaultAction: () => void) => {
      if (blockRecursiveExpansion(node, 'tap collapsed ingredient')) return;
      if (node.deferredRecipeExpansion) {
        defaultAction();
        return;
      }
      const coverage = treeTotals.byproductCoverageByNode.get(node.id);
      if (!coverage) {
        defaultAction();
        return;
      }
      if (coverage.remainingAmount > 0) {
        openPickerWithErrorHandling(node, coverage);
        return;
      }
      const producer = [...coverage.allocations].sort(
        (left, right) => right.amount - left.amount,
      )[0];
      if (!producer) {
        console.error('A completed byproduct ingredient has no producing recipe allocation.', {
          nodeId: node.id,
          itemKey: node.key,
        });
        return;
      }
      focusByproductProducer(producer.producerSourceId);
    },
    [focusByproductProducer, openPickerWithErrorHandling, treeTotals],
  );

  const handleTreeTotalIngredientTap = useCallback(
    (total: TreeTotal, kind: TreeTotalTargetKind) => {
      const node = findTreeTotalTarget(rootRef.current, total, kind);
      if (!node) {
        console.error('A tree-total ingredient could not be resolved to a graph node.', {
          itemKey: total.key,
          tag: total.tag,
          totalKind: kind,
        });
        return;
      }
      handleCollapsedIngredientTap(node, () => {
        if (compactMode && !radialLayout) {
          openPickerWithErrorHandling(node);
          return;
        }
        onItemTap(node);
      });
    },
    [
      compactMode,
      handleCollapsedIngredientTap,
      onItemTap,
      openPickerWithErrorHandling,
      radialLayout,
    ],
  );

  const rootExportName = useMemo(
    () => {
      const rootKey = root?.key ?? 'recipe-tree';
      return safeExportFilename(data.itemsByKey.get(rootKey)?.n ?? rootKey);
    },
    [data.itemsByKey, root?.key],
  );

  const portableTreeJson = useCallback(async () => {
    const currentRoot = rootRef.current;
    if (!currentRoot) throw new Error('There is no recipe tree to share.');
    const session = serializeGraphSession(currentRoot, graphDirection);
    const recipeRefs = session.selections
      .filter(selection => selection.source.kind === 'recipe')
      .map(selection => selection.source.kind === 'recipe' ? selection.source.ref : null)
      .filter((ref): ref is RecipeRef => ref !== null);
    const uniqueRefs = [...new Map(recipeRefs.map(ref => [ref.join(':'), ref])).values()];
    const recipes = await data.getRecipes(uniqueRefs);
    const recipeKeys = new Map<string, string>();
    uniqueRefs.forEach((ref, index) => {
      const category = data.categories[ref[0]];
      const recipe = recipes[index];
      if (!category || !recipe?.id) return;
      recipeKeys.set(ref.join(':'), `${category.id}|${recipe.id}`);
    });
    return JSON.stringify(
      buildPortableTree(session, data.descriptor, recipeKeys),
      null,
      2,
    );
  }, [data, graphDirection]);

  const shareCurrentTree = useCallback(async () => {
    const json = await portableTreeJson();
    return sharePortableTree(`${rootExportName}-tree.mrtree.json`, json);
  }, [portableTreeJson, rootExportName]);

  const saveCurrentTreeToInstance = useCallback(async () => {
    const json = await portableTreeJson();
    return savePortableTreeToInstance(`${rootExportName}-tree.mrtree.json`, json);
  }, [portableTreeJson, rootExportName]);

  const importPortableTree = useCallback(
    async (raw: string) => {
      const share = parsePortableTree(raw);
      if (share.pack.minecraftVersion !== data.descriptor.minecraftVersion) {
        throw new Error(
          `This tree is for Minecraft ${share.pack.minecraftVersion}; the selected pack uses ${data.descriptor.minecraftVersion}.`,
        );
      }
      if (!data.itemsByKey.has(share.rootKey)) {
        throw new Error('The shared starting item is not available in the selected modpack.');
      }
      const recipeRefCache = new Map<string, RecipeRef>();
      const resolveRecipeRef = async (selection: PortableTreeSelection): Promise<RecipeRef> => {
        if (selection.source.kind !== 'recipe') {
          throw new Error('Only recipe sources have recipe references.');
        }
        const recipeSource = selection.source;
        const cacheKey = `${selection.itemKey}\n${recipeSource.recipeKey}`;
        const cached = recipeRefCache.get(cacheKey);
        if (cached) return [...cached];
        const candidates = choicesFor(selection.itemKey, share.direction)
          .filter((choice): choice is RecipeSourceChoice => choice.t === 'recipe');
        const ordered = recipeSource.ref
          ? [
              ...candidates.filter(choice =>
                choice.ref[0] === recipeSource.ref?.[0] &&
                choice.ref[1] === recipeSource.ref?.[1],
              ),
              ...candidates.filter(choice =>
                choice.ref[0] !== recipeSource.ref?.[0] ||
                choice.ref[1] !== recipeSource.ref?.[1],
              ),
            ]
          : candidates;
        const recipes = await data.getRecipes(ordered.map(choice => choice.ref));
        for (let index = 0; index < ordered.length; index += 1) {
          const candidate = ordered[index];
          const category = data.categories[candidate.ref[0]];
          const recipe = recipes[index];
          if (
            category &&
            recipe?.id &&
            `${category.id}|${recipe.id}` === recipeSource.recipeKey
          ) {
            recipeRefCache.set(cacheKey, candidate.ref);
            return [...candidate.ref];
          }
        }
        throw new Error(
          `Recipe ${recipeSource.recipeKey} for ${selection.itemKey} is unavailable in this modpack.`,
        );
      };

      const selections: StoredGraphSelection[] = [];
      for (const selection of share.selections) {
        selections.push(
          portableSelectionAsStored(
            selection,
            selection.source.kind === 'recipe'
              ? await resolveRecipeRef(selection)
              : undefined,
          ),
        );
      }
      const session = parseGraphSession(JSON.stringify({
        version: 2,
        rootKey: share.rootKey,
        direction: share.direction,
        ...(share.productionPlan ? {productionPlan: share.productionPlan} : {}),
        selections,
      }));
      pendingGraphSessionRef.current = session;
      restoringGraphSessionRef.current = true;
      setShowTreeShare(false);
      setExportMessage('Shared tree imported.');
      restoreGraph(session.rootKey, session.direction);
    },
    [choicesFor, data, restoreGraph],
  );

  const exportTotals = useCallback(() => {
    try {
      const csv = buildTreeTotalsCsv(treeTotals, (key, tag) =>
        displayIngredientName(
          data.itemsByKey.get(key)?.n ?? key,
          tag,
          data.descriptor.minecraftVersion,
        ),
      );
      downloadBlob(
        `${rootExportName}-resources.csv`,
        new Blob([csv], {type: 'text/csv;charset=utf-8'}),
      );
      setExportMessage('Resource list exported.');
    } catch (error) {
      console.error('Tree resource-list export failed.', error);
      setExportMessage(error instanceof Error ? error.message : 'Resource-list export failed.');
    }
  }, [
    data.descriptor.minecraftVersion,
    data.itemsByKey,
    rootExportName,
    treeTotals,
  ]);

  const exportTreeImage = useCallback(async () => {
    if (Platform.OS !== 'web') {
      const message = 'High-quality tree export is currently available in the web viewer.';
      console.error(message);
      setExportMessage(message);
      return;
    }
    const source = anchorRef.current as unknown as HTMLElement | null;
    const currentGraph = graphRef.current;
    if (!source || !currentGraph) {
      const message = 'The tree export surface is not ready.';
      console.error(message);
      setExportMessage(message);
      return;
    }

    setExportingTree(true);
    setExportMessage('Rendering high-quality PNG…');
    try {
      const width = Math.ceil(currentGraph.maxX - currentGraph.minX + GRAPH_EXPORT_PADDING * 2);
      const height = Math.ceil(currentGraph.maxY - currentGraph.minY + GRAPH_EXPORT_PADDING * 2);
      const {renderTiledPng} = await import('./tiledPng');
      const result = await renderTiledPng({
        source,
        logicalWidth: width,
        logicalHeight: height,
        sourceLeft: GRAPH_EXPORT_PADDING - currentGraph.minX,
        sourceTop: GRAPH_EXPORT_PADDING - currentGraph.minY,
        requestedScale: GRAPH_EXPORT_PIXEL_RATIO,
        backgroundColor: theme.bg,
        onProgress: (completedTiles, totalTiles) => {
          setExportMessage(`Rendering PNG tile ${completedTiles} of ${totalTiles}…`);
        },
      });
      if (result.plan.scale < GRAPH_EXPORT_PIXEL_RATIO) {
        console.warn('Tree PNG resolution was capped by the tiled export safety budget.', {
          requestedScale: GRAPH_EXPORT_PIXEL_RATIO,
          appliedScale: result.plan.scale,
          outputWidth: result.plan.outputWidth,
          outputHeight: result.plan.outputHeight,
          outputPixels: result.plan.outputPixels,
          tiles: result.plan.totalTiles,
        });
      }
      downloadBlob(`${rootExportName}-tree.png`, result.blob);
      setExportMessage(
        `Tree exported at ${result.plan.scale}× resolution using ` +
          `${result.plan.totalTiles} ${result.plan.totalTiles === 1 ? 'tile' : 'tiles'}.`,
      );
    } catch (error) {
      console.error('High-quality tree PNG export failed.', error);
      setExportMessage(error instanceof Error ? error.message : 'Tree PNG export failed.');
    } finally {
      setExportingTree(false);
    }
  }, [rootExportName]);

  /** @returns false when the viewport isn't measurable yet (hidden tab) */
  const fitView = useCallback(() => {
    const g = graphRef.current;
    const vp = viewportRef.current;
    if (!g || vp.w === 0 || vp.h === 0) return false;
    const bw = Math.max(60, g.maxX - g.minX);
    const bh = Math.max(60, g.maxY - g.minY);
    const scale = automaticGraphFitScale(vp.w, vp.h, bw, bh);
    applyTransform({
      x: vp.w / 2 - (g.minX + bw / 2) * scale,
      y: vp.h / 2 - (g.minY + bh / 2) * scale,
      scale,
    });
    return true;
  }, [applyTransform]);

  useEffect(() => {
    if (needsFitRef.current && fitView()) {
      needsFitRef.current = false;
    }
  }, [graph, fitView]);

  const zoomAt = useCallback((px: number, py: number, factor: number) => {
    const current = transformRef.current;
    const scale = Math.min(4, Math.max(0.12, current.scale * factor));
    const k = scale / current.scale;
    applyTransform({
      x: px - (px - current.x) * k,
      y: py - (py - current.y) * k,
      scale,
    });
  }, [applyTransform]);

  const toggleCompactMode = useCallback(() => {
    setCompactMode(current => {
      const next = !current;
      needsFitRef.current = true;
      try {
        const storage = globalThis.localStorage;
        if (storage) storage.setItem(COMPACT_MODE_KEY, next ? '1' : '0');
        else if (Platform.OS === 'web') {
          console.warn('Compact graph mode is using memory only because localStorage is unavailable.');
        }
      } catch (error) {
        console.error('Compact graph mode could not be saved to localStorage.', error);
      }
      return next;
    });
  }, []);

  const toggleRadialLayout = useCallback(() => {
    setRadialLayout(current => {
      const next = !current;
      needsFitRef.current = true;
      try {
        const storage = globalThis.localStorage;
        if (storage) storage.setItem(RADIAL_LAYOUT_KEY, next ? '1' : '0');
        else if (Platform.OS === 'web') {
          console.warn('Radial graph layout is using memory only because localStorage is unavailable.');
        }
      } catch (error) {
        console.error('Radial graph layout could not be saved to localStorage.', error);
      }
      return next;
    });
  }, []);

  const updateExpandRecipesOnce = useCallback(
    (value: boolean) => {
      expandRecipesOnceRef.current = value;
      setExpandRecipesOnce(value);
      needsFitRef.current = true;
      try {
        const storage = globalThis.localStorage;
        if (storage) storage.setItem(EXPAND_RECIPES_ONCE_KEY, value ? '1' : '0');
        else if (Platform.OS === 'web') {
          console.warn('Expand-once graph mode is using memory only because localStorage is unavailable.');
        }
      } catch (error) {
        console.error('Expand-once graph preference could not be saved to localStorage.', error);
      }

      if (value) {
        const currentRoot = rootRef.current;
        const duplicates = duplicateRecipeExpansions(currentRoot, graphDirection);
        for (const {node, expansion} of duplicates) {
          releaseByproductFulfillments(currentRoot, node);
          node.source = undefined;
          node.deferredRecipeExpansion = expansion;
        }
      } else {
        for (const node of deferredRecipeExpansionNodes(rootRef.current)) {
          const expansion = node.deferredRecipeExpansion;
          if (!expansion) continue;
          node.deferredRecipeExpansion = undefined;
          void applyRecipeChoice(node, {t: 'recipe', ...expansion});
        }
      }
      bump();
    },
    [applyRecipeChoice, bump, graphDirection],
  );

  const updateUseByproducts = useCallback((value: boolean) => {
    setUseByproducts(value);
    try {
      const storage = globalThis.localStorage;
      if (storage) storage.setItem(USE_BYPRODUCTS_KEY, value ? '1' : '0');
      else if (Platform.OS === 'web') {
        console.warn('Byproduct-credit preference is using memory only because localStorage is unavailable.');
      }
    } catch (error) {
      console.error('Byproduct-credit preference could not be saved to localStorage.', error);
    }
  }, []);

  // Native web listeners handle browser behaviors that React Native Web's
  // responder and inherited userSelect style do not consistently suppress in Safari.
  // The canvas mounts/unmounts with the empty state, so attach via callback ref.
  const canvasCleanup = useRef<(() => void) | null>(null);
  const setCanvasRef = useCallback(
    (view: View | null) => {
      wrapRef.current = view;
      canvasCleanup.current?.();
      canvasCleanup.current = null;
      const el = view as unknown as HTMLElement | null;
      if (el && typeof el.addEventListener === 'function') {
        const onWheel = (e: WheelEvent) => {
          e.preventDefault();
          const rect = el.getBoundingClientRect();
          const viewport = viewportRef.current;
          if (viewport.w <= 0 || viewport.h <= 0) {
            console.error('Graph wheel zoom was ignored because the viewport is not measurable.', {
              viewport,
            });
            return;
          }
          const point = graphViewportPointFromClient(
            e.clientX,
            e.clientY,
            rect,
            {width: viewport.w, height: viewport.h},
          );
          zoomAt(point.x, point.y, graphWheelZoomFactor(e.deltaY, e.deltaMode));
        };
        const preventNativeDrag = (e: Event) => e.preventDefault();
        const preventPagePinch = (event: Event) => {
          const touchEvent = event as TouchEvent;
          if (!touchEvent.touches || touchEvent.touches.length >= 2) {
            event.preventDefault();
          }
        };

        // Safari can begin text selection before PanResponder crosses its movement
        // threshold. Capture selection and image-drag events at the canvas boundary.
        el.style.setProperty('user-select', 'none');
        el.style.setProperty('-webkit-user-select', 'none');
        el.style.setProperty('touch-action', 'none');
        el.style.setProperty('overscroll-behavior', 'contain');
        el.addEventListener('wheel', onWheel, {passive: false});
        el.addEventListener('touchstart', preventPagePinch, {passive: false});
        el.addEventListener('touchmove', preventPagePinch, {passive: false});
        el.addEventListener('gesturestart', preventNativeDrag, {passive: false});
        el.addEventListener('gesturechange', preventNativeDrag, {passive: false});
        el.addEventListener('selectstart', preventNativeDrag, true);
        el.addEventListener('dragstart', preventNativeDrag, true);
        canvasCleanup.current = () => {
          el.removeEventListener('wheel', onWheel);
          el.removeEventListener('touchstart', preventPagePinch);
          el.removeEventListener('touchmove', preventPagePinch);
          el.removeEventListener('gesturestart', preventNativeDrag);
          el.removeEventListener('gesturechange', preventNativeDrag);
          el.removeEventListener('selectstart', preventNativeDrag, true);
          el.removeEventListener('dragstart', preventNativeDrag, true);
        };
      } else if (Platform.OS === 'web' && view) {
        console.warn('Graph canvas could not attach native pan-suppression listeners.');
      }
    },
    [zoomAt],
  );

  // Drag to pan, two-finger pinch to zoom.
  const panOrigin = useRef<PanGestureOrigin | null>(null);
  const pinchDist = useRef(0);
  const pinching = useRef(false);
  const clearWebSelection = useCallback(() => {
    if (Platform.OS !== 'web') return;
    try {
      globalThis.getSelection?.()?.removeAllRanges();
    } catch (error) {
      console.warn('Graph canvas could not clear the active browser selection.', error);
    }
  }, []);
  const responder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: event => event.nativeEvent.touches?.length === 2,
        onStartShouldSetPanResponderCapture: event => event.nativeEvent.touches?.length === 2,
        onMoveShouldSetPanResponder: (_e, g) =>
          Math.abs(g.dx) + Math.abs(g.dy) > 6 || g.numberActiveTouches === 2,
        onMoveShouldSetPanResponderCapture: (_e, g) =>
          Math.abs(g.dx) + Math.abs(g.dy) > 6 || g.numberActiveTouches === 2,
        onPanResponderGrant: (_event, gesture) => {
          clearWebSelection();
          panOrigin.current = capturePanGestureOrigin(
            transformRef.current,
            gesture.dx,
            gesture.dy,
          );
          pinchDist.current = 0;
          pinching.current = false;
        },
        onPanResponderMove: (e, g) => {
          const touches = e.nativeEvent.touches;
          if (touches && touches.length === 2) {
            const dx = touches[0].pageX - touches[1].pageX;
            const dy = touches[0].pageY - touches[1].pageY;
            const dist = Math.hypot(dx, dy);
            if (pinchDist.current > 0) {
              const cx = (touches[0].locationX + touches[1].locationX) / 2;
              const cy = (touches[0].locationY + touches[1].locationY) / 2;
              zoomAt(cx, cy, graphPinchZoomFactor(dist, pinchDist.current));
            }
            pinchDist.current = dist;
            pinching.current = true;
            return;
          }
          pinchDist.current = 0;
          if (pinching.current) {
            // PanResponder's dx/dy continue across the entire gesture. Rebase
            // after a pinch so lifting one finger cannot snap back to the old origin.
            pinching.current = false;
            panOrigin.current = capturePanGestureOrigin(transformRef.current, g.dx, g.dy);
            return;
          }
          if (!panOrigin.current) {
            console.error('Graph pan received movement without a gesture origin.');
            return;
          }
          applyTransform(transformForPanGesture(panOrigin.current, g.dx, g.dy));
        },
        onPanResponderTerminationRequest: () => false,
        onPanResponderRelease: () => {
          panOrigin.current = null;
          pinchDist.current = 0;
          pinching.current = false;
        },
        onPanResponderTerminate: () => {
          panOrigin.current = null;
          pinchDist.current = 0;
          pinching.current = false;
        },
      }),
    [applyTransform, clearWebSelection, zoomAt],
  );

  if (!graphRootKey || !root) {
    return (
      <View style={styles.emptyWrap}>
        <Text style={styles.emptyTitle}>No item selected</Text>
        <Text style={styles.emptyText}>
          Open an item and tap one of its recipe cards to start a crafting tree. Tap nodes to
          expand how each item is obtained — recipes, mining, or mob drops.
        </Text>
        <TouchableOpacity
          {...signalTarget('graph.empty.browse-items')}
          style={styles.emptyBtn}
          onPress={() => setTab('items')}>
          <Text style={styles.emptyBtnText}>Browse items</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const rootNodeActions: RootNodeActionProps | undefined = showRootActions
    ? {
        amount: root.productionPlan?.amount ?? root.amount ?? 1,
        onAmountChange: updateRootRequestedAmount,
        onChangeRecipe: () => openRootPicker('inputs'),
        onAddUsedBy: () => openRootPicker('outputs'),
      }
    : undefined;

  return (
    <View style={styles.root}>
      <View
        ref={setCanvasRef}
        style={[styles.canvas, noSelect]}
        onLayout={e => {
          const nextViewport = {
            w: e.nativeEvent.layout.width,
            h: e.nativeEvent.layout.height,
          };
          viewportRef.current = nextViewport;
          setViewportSize(current =>
            current.w === nextViewport.w && current.h === nextViewport.h
              ? current
              : nextViewport,
          );
          // The graph tab mounts hidden; fit once it actually gets a size.
          if (needsFitRef.current && fitView()) {
            needsFitRef.current = false;
          }
        }}
        {...responder.panHandlers}>
        {/* 0x0 anchor so translate/scale apply around the top-left origin */}
        <View
          ref={anchorRef}
          collapsable={false}
          style={[
            styles.anchor,
            Platform.OS !== 'web' && styles.nativeAnchor,
            Platform.OS === 'web' && displayTransform.nativeScale
              ? {
                  left: displayTransform.x,
                  top: displayTransform.y,
                }
              : {
                  transform: [
                    {translateX: displayTransform.x},
                    {translateY: displayTransform.y},
                    {scale: displayTransform.scale},
                  ],
                },
          ]}>
          {renderedGraph?.edges.map((e, i) => (
            <View
              key={`e${i}`}
              style={[
                styles.edge,
                {
                  left: e.x,
                  top: e.y,
                  width: e.w,
                  height: e.h,
                  transform:
                    e.angle === undefined
                      ? undefined
                      : [{rotate: `${String(e.angle)}rad`}],
                },
              ]}
            />
          ))}
          {renderedGraph?.nodes.map(n =>
            compactMode || n.radial ? (
              <CompactItemNodeView
                key={n.id}
                x={
                  compactMode && n.item.id === 'root'
                    ? attachedRootVisualX(
                        n.x,
                        n.w,
                        radialLayout,
                        showRootActions,
                      )
                    : n.x
                }
                y={n.y}
                node={n.item}
                requiredAmount={displayedAmountFor(n.item)}
                byproductCoverage={treeTotals.byproductCoverageByNode.get(n.item.id)}
                isRoot={n.item.id === 'root'}
                selectable={
                  n.item.id === 'root' ||
                  (!isRecursiveItemNode(n.item) &&
                    (!!n.item.deferredRecipeExpansion ||
                      treeTotals.byproductCoverageByNode.has(n.item.id) ||
                      choicesFor(n.item.key).length > 0))
                }
                terminal={
                  isRecursiveItemNode(n.item) ||
                  (!n.item.deferredRecipeExpansion &&
                    choicesFor(n.item.key).length === 0)
                }
                terminalLabel={
                  isRecursiveItemNode(n.item)
                    ? 'recursive input, expansion disabled'
                    : graphDirection === 'outputs'
                      ? 'no outputs'
                      : 'no inputs'
                }
                radial={n.radial === true}
                radialRoot={radialLayout && n.item.id === 'root'}
                branchLabel={n.compactBranch === true}
                showLabel
                deferredDuplicate={!!n.item.deferredRecipeExpansion}
                rootActions={n.item.id === 'root' ? rootNodeActions : undefined}
                onTap={() =>
                  n.item.id === 'root'
                    ? setShowRootActions(value => !value)
                    : handleCollapsedIngredientTap(n.item, () =>
                        n.item.deferredRecipeExpansion || n.radial
                          ? onItemTap(n.item)
                          : openPickerWithErrorHandling(n.item),
                      )
                }
              />
            ) : n.kind === 'item' ? (
              <ItemNodeView
                key={n.id}
                x={n.x}
                y={n.y}
                node={n.item}
                requiredAmount={displayedAmountFor(n.item)}
                byproductCoverage={treeTotals.byproductCoverageByNode.get(n.item.id)}
                isRoot={n.item.id === 'root'}
                expandable={
                  !isRecursiveItemNode(n.item) &&
                  (!!n.item.deferredRecipeExpansion ||
                    choicesFor(n.item.key).length > 0)
                }
                deferredDuplicate={!!n.item.deferredRecipeExpansion}
                terminalLabel={
                  isRecursiveItemNode(n.item)
                    ? 'recursive input, expansion disabled'
                    : graphDirection === 'outputs'
                      ? 'no outputs'
                      : 'no inputs'
                }
                rootActions={n.item.id === 'root' ? rootNodeActions : undefined}
                onTap={() =>
                  n.item.id === 'root'
                    ? setShowRootActions(value => !value)
                    : handleCollapsedIngredientTap(n.item, () => onItemTap(n.item))
                }
                onInfo={() => openItem(n.item.key)}
              />
            ) : (
              <SourceNodeView
                key={n.id}
                x={n.x}
                y={n.y}
                w={n.w}
                h={n.h}
                item={n.item}
                requiredAmount={displayedAmountFor(n.item)}
                byproductCoverage={treeTotals.byproductCoverageByNode.get(n.item.id)}
                source={n.source!}
                isRoot={n.item.id === 'root'}
                radialRoot={radialLayout && n.item.id === 'root'}
                focused={n.source?.id === focusedSourceId}
                animateMobs={animateMobs}
                rootActions={n.item.id === 'root' ? rootNodeActions : undefined}
                canSwap={
                  n.item.id !== 'root' &&
                  !isRecursiveItemNode(n.item) &&
                  choicesFor(n.item.key).length > 1
                }
                onCollapse={() =>
                  n.item.id === 'root'
                    ? setShowRootActions(value => !value)
                    : onItemTap(n.item)
                }
                onSwap={() => openPickerWithErrorHandling(n.item)}
                onInfo={() => openItem(n.item.key)}
              />
            ),
          )}
        </View>
      </View>

      {graphLayout.fallback && (
        <View style={styles.layoutFallbackNotice} accessibilityRole="alert">
          <Text style={[styles.layoutFallbackText, noSelect]}>{graphLayout.fallback}</Text>
        </View>
      )}

      <View style={styles.controls}>
        {showGraphControls && (
          <View style={styles.controlOptions}>
            {graphDirection === 'inputs' && (
              <CtrlBtn
                label={`Totals ${disclosureChevron(showTreeTotals)}`}
                accessibilityLabel={showTreeTotals ? 'Collapse tree totals' : 'Expand tree totals'}
                metricsId="graph.control.totals"
                active={showTreeTotals}
                onPress={() => setShowTreeTotals(value => !value)}
              />
            )}
            <CtrlBtn
              label="Radial"
              metricsId="graph.control.radial"
              active={radialLayout}
              onPress={toggleRadialLayout}
            />
            <CtrlBtn
              label="Compact"
              metricsId="graph.control.compact"
              active={compactMode}
              onPress={toggleCompactMode}
            />
            <CtrlBtn
              label="Unique"
              accessibilityLabel="Use unique recipes"
              metricsId="graph.control.expand-once"
              active={expandRecipesOnce}
              onPress={() => updateExpandRecipesOnce(!expandRecipesOnce)}
            />
            <CtrlBtn
              label="Share"
              metricsId="graph.control.share"
              onPress={() => setShowTreeShare(true)}
            />
          </View>
        )}
        {!controlsToggleInHeader && (
          <TouchableOpacity
            {...signalTarget('graph.control.menu')}
            accessibilityRole="button"
            accessibilityLabel={showGraphControls ? 'Collapse graph controls' : 'Expand graph controls'}
            accessibilityState={{expanded: showGraphControls}}
            style={[styles.ctrlBtn, styles.controlMenuBtn, showGraphControls && styles.ctrlBtnActive]}
            onPress={onToggleGraphControls}>
            <Text
              style={[
                styles.ctrlBtnText,
                styles.controlMenuBtnText,
                showGraphControls && styles.ctrlBtnTextActive,
              ]}>
              {disclosureChevron(showGraphControls)}
            </Text>
          </TouchableOpacity>
        )}
      </View>
      <TouchableOpacity
        {...signalTarget('graph.control.fit')}
        accessibilityRole="button"
        accessibilityLabel="Fit graph to view"
        style={[styles.ctrlBtn, styles.fitControl]}
        onPress={fitView}>
        <Text style={[styles.ctrlBtnText, styles.fitControlIcon]}>⛶</Text>
      </TouchableOpacity>
      {showGraphControls && graphDirection === 'inputs' && showTreeTotals && (
        <TreeTotalsPanel
          totals={treeTotals}
          useByproducts={useByproducts}
          exportingTree={exportingTree}
          exportMessage={exportMessage}
          onUseByproductsChange={updateUseByproducts}
          onExportTotals={exportTotals}
          onExportTree={() => void exportTreeImage()}
          onIngredientTap={handleTreeTotalIngredientTap}
          onOpenItem={openItem}
        />
      )}
      {pickerLookup && (
        <View
          style={styles.recipeLookupBackdrop}
          accessibilityViewIsModal
          accessibilityLabel={pickerLookup.title}>
          <View style={styles.recipeLookupCard}>
            <ActivityIndicator color={theme.accent} size="large" />
            <Text style={styles.recipeLookupTitle}>{pickerLookup.title}</Text>
            <Text style={styles.recipeLookupHint}>Loading recipe options…</Text>
            <TouchableOpacity
              {...signalTarget('graph.source-lookup.cancel')}
              accessibilityRole="button"
              accessibilityLabel="Cancel recipe lookup"
              style={styles.recipeLookupCancel}
              onPress={cancelPickerLookup}>
              <Text style={styles.recipeLookupCancelText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
      {picker && (
        <PickerModal
          visible
          interfaceZoom={interfaceZoom}
          title={picker.title}
          direction={picker.target.id === 'root' ? picker.direction : undefined}
          onDirectionChange={
            picker.target.id === 'root'
              ? direction => {
                  if (direction === picker.direction) return;
                  void openPicker(
                    picker.target,
                    picker.byproductCoverage,
                    direction,
                  ).catch(error => {
                    console.error('The root recipe direction could not be changed.', {
                      itemKey: picker.target.key,
                      direction,
                      error,
                    });
                  });
                }
              : undefined
          }
          options={visiblePickerEntries(picker, hiddenRecipeStages).map(entry => entry.option)}
          rememberSource={picker.rememberSource}
          onRememberSourceChange={
            picker.direction === 'inputs'
              ? rememberSource =>
                  setPicker(current => (current ? {...current, rememberSource} : current))
              : undefined
          }
          filterLabel={
            picker.identifiedFluidTransferCount > 0
              ? 'Fluid container transfers'
              : undefined
          }
          filterHint={
            picker.identifiedFluidTransferCount > 0
              ? `Hidden by default · ${picker.identifiedFluidTransferCount} identified option${picker.identifiedFluidTransferCount === 1 ? '' : 's'}`
              : undefined
          }
          filterValue={picker.showFluidTransfers}
          onFilterValueChange={
            picker.identifiedFluidTransferCount > 0
              ? showFluidTransfers =>
                  setPicker(current => (current ? {...current, showFluidTransfers} : current))
              : undefined
          }
          recipeStageCounts={pickerRecipeStageCounts(picker)}
          hiddenRecipeStages={hiddenRecipeStages}
          onToggleRecipeStage={toggleRecipeStage}
          collapsedGroupKeys={picker.collapsedGroupKeys}
          onToggleGroup={togglePickerGroup}
          groupProgress={picker.recipeGroupProgress}
          onLoadGroup={groupKey => void loadPickerRecipeGroup(groupKey)}
          onClose={() => setPicker(null)}
          onSelectAlternative={(i, selectionKey, selectedKey) => {
            setPicker(current => {
              if (!current) {
                console.error(
                  'An ingredient alternative was selected after its source picker closed.',
                  {selectedIndex: i, selectionKey, selectedKey},
                );
                return current;
              }
              const visibleEntries = visiblePickerEntries(current, hiddenRecipeStages);
              const entry = visibleEntries[i];
              if (entry?.choice.t !== 'recipe' || !entry.recipe) {
                console.error(
                  'An ingredient alternative was selected for a non-recipe source.',
                  {
                    selectedIndex: i,
                    selectionKey,
                    selectedKey,
                    optionCount: visibleEntries.length,
                  },
                );
                return current;
              }
              const displayedSlot = entry.option.inputs?.find(
                input =>
                  (input.selectionKey ?? input.key) === selectionKey &&
                  input.alternatives.includes(selectedKey),
              );
              if (!displayedSlot) {
                console.error(
                  'The selected ingredient alternative is not present in the displayed recipe slot.',
                  {selectedIndex: i, selectionKey, selectedKey},
                );
                return current;
              }
              const choice: RecipeSourceChoice = {
                ...entry.choice,
                ingredientSelections: {
                  ...entry.choice.ingredientSelections,
                  [selectionKey]: selectedKey,
                },
              };
              const nextEntry = pickerEntryFor(
                current.target.key,
                choice,
                entry.recipe,
                current.direction,
              );
              const standardIndex = current.standardEntries.indexOf(entry);
              if (standardIndex >= 0) {
                const standardEntries = [...current.standardEntries];
                standardEntries[standardIndex] = nextEntry;
                return {...current, standardEntries};
              }
              if (!current.showFluidTransfers) {
                console.error(
                  'A hidden fluid-transfer recipe received an ingredient alternative selection.',
                  {selectedIndex: i, selectionKey, selectedKey},
                );
                return current;
              }
              const fluidIndex = current.fluidTransferEntries.indexOf(entry);
              const fluidTransferEntries = [...current.fluidTransferEntries];
              if (!fluidTransferEntries[fluidIndex]) {
                console.error(
                  'The selected fluid-transfer recipe index is outside the picker entries.',
                  {selectedIndex: i, fluidIndex},
                );
                return current;
              }
              fluidTransferEntries[fluidIndex] = nextEntry;
              return {...current, fluidTransferEntries};
            });
          }}
          productionPlan={picker.productionPlan}
          onOpenMachine={machineKey => {
            setPicker(null);
            openItem(machineKey);
          }}
          onSelect={i => {
            const p = picker;
            const entries = visiblePickerEntries(p, hiddenRecipeStages);
            const selectedEntry = entries[i];
            const choice = selectedEntry?.choice;
            if (!choice) {
              console.error('The selected source index was not present in the picker.', {
                selectedIndex: i,
                optionCount: entries.length,
              });
              return;
            }
            if (blockRecursiveExpansion(p.target, 'select picker source')) {
              setPicker(null);
              return;
            }
            if (p.productionPlan && choice.t === 'recipe') {
              p.target.productionPlan = {...p.productionPlan};
            }
            if (p.target.id === 'root' && p.direction === 'outputs') {
              if (choice.t !== 'recipe' || !selectedEntry.recipe) {
                console.error('A root usage selection was missing its loaded recipe.', {
                  itemKey: p.target.key,
                  selectedIndex: i,
                  recipeRef: choice.t === 'recipe' ? choice.ref : undefined,
                });
                return;
              }
              const usageStart = usageGraphStart(selectedEntry.recipe);
              if (!usageStart) {
                console.error('A root usage recipe has no product to promote to the graph root.', {
                  itemKey: p.target.key,
                  recipeRef: choice.ref,
                });
                return;
              }
              setPicker(null);
              openRecipeInGraph(usageStart.rootKey, choice.ref, usageStart.direction);
              return;
            }
            setPicker(null);
            if (p.target.id === 'root' && p.direction !== graphDirection) {
              if (p.direction === 'inputs') {
                setPreferredSource(p.target.key, p.rememberSource ? choice : null);
              }
              pendingRootChoiceRef.current = {
                key: p.target.key,
                direction: p.direction,
                choice,
              };
              changeGraphDirection(p.direction);
              return;
            }
            if (p.direction === 'outputs') {
              p.target.source = undefined;
              applyChoice(p.target, choice);
              return;
            }
            if (p.target.source) {
              releaseByproductFulfillmentsFromSubtree(p.target);
            }
            p.target.source = undefined;
            if (p.byproductCoverage && p.byproductCoverage.remainingAmount > 0) {
              p.target.byproductFulfillment = {
                creditedAmount: p.byproductCoverage.creditedAmount,
                allocations: p.byproductCoverage.allocations.map(allocation => ({
                  ...allocation,
                })),
              };
            }
            setPreferredSource(p.target.key, p.rememberSource ? choice : null);
            if (p.rememberSource) {
              applyPreferredSourceAcrossTree(p.target, choice);
              return;
            }
            applyChoice(p.target, choice);
          }}
        />
      )}
      <TreeShareModal
        visible={showTreeShare}
        onClose={() => setShowTreeShare(false)}
        onShare={shareCurrentTree}
        onImport={importPortableTree}
        onChooseFile={pickPortableTreeFile}
        onSaveToInstance={saveCurrentTreeToInstance}
      />
    </View>
  );
}

type RootNodeActionProps = {
  amount: number;
  onAmountChange: (amount: number) => void;
  onChangeRecipe: () => void;
  onAddUsedBy: () => void;
};

const ROOT_AMOUNT_STEPPER_HEIGHT = 86;

function RootAmountStepper({
  amount,
  onAmountChange,
}: Pick<RootNodeActionProps, 'amount' | 'onAmountChange'>) {
  const [amountText, setAmountText] = useState(String(amount));
  useEffect(() => setAmountText(String(amount)), [amount]);
  const adjustAmount = (direction: -1 | 1) => {
    onAmountChange(amount + direction);
  };
  const updateAmountText = (value: string) => {
    setAmountText(value);
    const parsed = Number(value);
    if (Number.isFinite(parsed) && parsed >= 1) onAmountChange(parsed);
  };

  return (
    <View style={styles.rootNodeAmountRail}>
      <TouchableOpacity
        {...signalTarget('graph.root-actions.amount.increase')}
        accessibilityRole="button"
        accessibilityLabel="Increase requested amount"
        style={[styles.rootNodeStepButton, styles.rootNodeIncreaseButton]}
        onPress={() => adjustAmount(1)}>
        <Text style={[styles.rootNodeStepText, styles.rootNodeIncreaseText]}>+</Text>
      </TouchableOpacity>
      <TextInput
        accessibilityLabel="Amount requested"
        style={styles.rootNodeAmountInput}
        value={amountText}
        onChangeText={updateAmountText}
        onBlur={() => setAmountText(String(amount))}
        keyboardType="number-pad"
        inputMode="numeric"
        selectTextOnFocus
      />
      <TouchableOpacity
        {...signalTarget('graph.root-actions.amount.decrease')}
        accessibilityRole="button"
        accessibilityLabel="Decrease requested amount"
        style={styles.rootNodeStepButton}
        onPress={() => adjustAmount(-1)}>
        <Text style={styles.rootNodeStepText}>−</Text>
      </TouchableOpacity>
    </View>
  );
}

function RootActionButtons({
  onChangeRecipe,
  onAddUsedBy,
}: Pick<RootNodeActionProps, 'onChangeRecipe' | 'onAddUsedBy'>) {
  return (
    <View style={styles.rootNodeActionButtons}>
      <TouchableOpacity
        {...signalTarget('graph.root-actions.change-recipe')}
        accessibilityRole="button"
        style={styles.rootNodeSecondaryAction}
        onPress={onChangeRecipe}>
        <Text style={styles.rootNodeSecondaryActionText}>Change recipe</Text>
      </TouchableOpacity>
      <TouchableOpacity
        {...signalTarget('graph.root-actions.add-used-by')}
        accessibilityRole="button"
        style={styles.rootNodePrimaryAction}
        onPress={onAddUsedBy}>
        <Text style={styles.rootNodePrimaryActionText}>Add used by</Text>
      </TouchableOpacity>
    </View>
  );
}

function AttachedRootActions({
  actions,
  nodeX,
  nodeY,
  nodeWidth,
  nodeHeight,
  buttonWidth,
}: {
  actions: RootNodeActionProps;
  nodeX: number;
  nodeY: number;
  nodeWidth: number;
  nodeHeight: number;
  buttonWidth: number;
}) {
  const wrapperLeft = nodeX - (buttonWidth - nodeWidth) / 2;
  const nodeLeft = (buttonWidth - nodeWidth) / 2;
  return (
    <View
      pointerEvents="box-none"
      style={[
        styles.attachedRootActions,
        {
          left: wrapperLeft,
          top: nodeY,
          width: Math.max(buttonWidth, nodeLeft + nodeWidth + 40),
          height: nodeHeight + 62,
        },
      ]}>
      <View
        style={[
          styles.attachedRootStepper,
          {
            left: nodeLeft + nodeWidth + 6,
            top: Math.max(0, (nodeHeight - ROOT_AMOUNT_STEPPER_HEIGHT) / 2),
          },
        ]}>
        <RootAmountStepper {...actions} />
      </View>
      <View
        style={[
          styles.attachedRootButtons,
          {left: 0, top: nodeHeight + 24, width: buttonWidth},
        ]}>
        <RootActionButtons {...actions} />
      </View>
    </View>
  );
}

function TreeTotalsPanel({
  totals,
  useByproducts,
  exportingTree,
  exportMessage,
  onUseByproductsChange,
  onExportTotals,
  onExportTree,
  onIngredientTap,
  onOpenItem,
}: {
  totals: TreeTotals;
  useByproducts: boolean;
  exportingTree: boolean;
  exportMessage: string | null;
  onUseByproductsChange: (value: boolean) => void;
  onExportTotals: () => void;
  onExportTree: () => void;
  onIngredientTap: (total: TreeTotal, kind: TreeTotalTargetKind) => void;
  onOpenItem: (key: string) => void;
}) {
  return (
    <View style={styles.totalsPanel}>
      <View style={styles.totalsHeader}>
        <Text style={[styles.totalsTitle, noSelect]}>Tree totals</Text>
        <TouchableOpacity
          {...signalTarget('graph.totals.use-byproducts')}
          accessibilityRole="checkbox"
          accessibilityState={{checked: useByproducts}}
          style={[styles.totalsOption, useByproducts && styles.totalsOptionActive]}
          onPress={() => onUseByproductsChange(!useByproducts)}>
          <Text style={[styles.totalsOptionText, useByproducts && styles.totalsOptionTextActive]}>
            {useByproducts ? '✓ ' : ''}Use byproducts
          </Text>
        </TouchableOpacity>
      </View>
      <View style={styles.exportActions}>
        <TouchableOpacity
          {...signalTarget('graph.totals.export-csv')}
          style={styles.exportBtn}
          onPress={onExportTotals}>
          <Text style={styles.exportBtnText}>Export resources CSV</Text>
        </TouchableOpacity>
        <TouchableOpacity
          {...signalTarget('graph.totals.export-png')}
          style={[styles.exportBtn, exportingTree && styles.exportBtnDisabled]}
          disabled={exportingTree}
          onPress={onExportTree}>
          <Text style={styles.exportBtnText}>{exportingTree ? 'Rendering…' : 'Export HQ tree PNG'}</Text>
        </TouchableOpacity>
      </View>
      {exportMessage && <Text style={[styles.exportMessage, noSelect]}>{exportMessage}</Text>}
      <ScrollView style={styles.totalsScroll} contentContainerStyle={styles.totalsContent}>
        <TreeTotalsSection
          title={useByproducts ? 'Inputs still needed' : 'Inputs'}
          totals={totals.inputs}
          onPress={total => onIngredientTap(total, 'input')}
        />
        {useByproducts && (
          <TreeTotalsSection
            title="Byproducts used"
            totals={totals.byproductCredits}
            onPress={total => onIngredientTap(total, 'input')}
          />
        )}
        <TreeTotalsSection
          title={useByproducts ? 'Byproducts remaining' : 'Byproducts'}
          totals={totals.byproducts}
          onPress={total => onOpenItem(total.key)}
        />
      </ScrollView>
    </View>
  );
}

function TreeTotalsSection({
  title,
  totals,
  onPress,
}: {
  title: string;
  totals: TreeTotal[];
  onPress: (total: TreeTotal) => void;
}) {
  const data = useData();
  return (
    <View style={styles.totalsSection}>
      <Text style={[styles.totalsSectionTitle, noSelect]}>{title}</Text>
      {totals.length === 0 ? (
        <Text style={[styles.totalsEmpty, noSelect]}>None</Text>
      ) : (
        totals.map(total => {
          const item = data.itemsByKey.get(total.key);
          return (
            <TouchableOpacity
              {...signalTarget('graph.totals.item.open')}
              key={`${total.key}:${total.tag ?? ''}`}
              style={styles.totalRow}
              accessibilityRole="button"
              onPress={() => onPress(total)}>
              <ItemIcon item={item} itemKey={total.key} size={16} />
              <Text style={[styles.totalName, noSelect]} numberOfLines={1}>
                {displayIngredientName(
                  item?.n ?? total.key,
                  total.tag,
                  data.descriptor.minecraftVersion,
                )}
              </Text>
              <Text style={[styles.totalAmount, noSelect]}>
                {formatIngredientQuantity(total.key, total.amount)}
              </Text>
            </TouchableOpacity>
          );
        })
      )}
    </View>
  );
}

function CompactItemNodeView({
  x,
  y,
  node,
  requiredAmount,
  byproductCoverage,
  isRoot,
  selectable,
  terminal,
  terminalLabel,
  radial = false,
  radialRoot = false,
  branchLabel = false,
  showLabel,
  deferredDuplicate,
  rootActions,
  onTap,
}: {
  x: number;
  y: number;
  node: ItemTreeNode;
  requiredAmount: number | null;
  byproductCoverage?: NodeByproductCoverage;
  isRoot: boolean;
  selectable: boolean;
  terminal: boolean;
  terminalLabel: string;
  radial?: boolean;
  radialRoot?: boolean;
  branchLabel?: boolean;
  showLabel: boolean;
  deferredDuplicate: boolean;
  rootActions?: RootNodeActionProps;
  onTap: () => void;
}) {
  const data = useData();
  const item = data.itemsByKey.get(node.key);
  const name = displayIngredientName(
    item?.n ?? node.key,
    node.tag,
    data.descriptor.minecraftVersion,
  );
  const amount = formatIngredientQuantity(
    node.key,
    byproductCoverage?.remainingAmount ?? requiredAmount,
  );
  const byproductLabel = byproductCoverage
    ? byproductCoverage.remainingAmount === 0
      ? `completed by byproduct ${formatIngredientQuantity(node.key, byproductCoverage.creditedAmount)}`
      : `${formatIngredientQuantity(node.key, byproductCoverage.creditedAmount)} supplied by byproduct, ${formatIngredientQuantity(node.key, byproductCoverage.remainingAmount)} still needed`
    : null;
  return (
    <>
      <Pressable
      {...signalTarget(`graph.node.expand.${nodeDepthBucket(node)}`)}
      accessibilityRole={selectable ? 'button' : undefined}
      accessibilityLabel={`${name}, quantity ${formatIngredientQuantity(node.key, requiredAmount)}${terminal ? `, ${terminalLabel}` : ''}${deferredDuplicate ? ', recipe expanded elsewhere, tap to move expansion here' : ''}${byproductLabel ? `, ${byproductLabel}` : ''}${node.nonConsumed ? ', not consumed' : ''}${node.consumptionProbability !== undefined ? `, ${node.consumptionProbability == null ? 'unknown' : `${String(Math.round(node.consumptionProbability * 10_000) / 100)} percent`} consume chance` : ''}${node.productionProbability !== undefined ? `, ${node.productionProbability == null ? 'unknown' : `${String(Math.round(node.productionProbability * 10_000) / 100)} percent`} produce chance` : ''}${isRoot ? ', open amount and recipe controls' : selectable && !deferredDuplicate ? byproductCoverage?.remainingAmount === 0 ? ', navigate to producing recipe' : ', choose source' : ''}`}
      disabled={!selectable || node.loading}
      onPress={onTap}
      style={[
        radial ? styles.radialItemNode : styles.compactItemNode,
        branchLabel && styles.compactBranchNode,
        {left: x, top: y},
        node.nonConsumed && styles.nodePrerequisite,
        isRecursiveItemNode(node) && styles.nodeCyclic,
        terminal && !isRecursiveItemNode(node) && styles.nodeTerminal,
        node.loading && styles.nodeLoading,
        byproductCoverage?.remainingAmount === 0 && styles.nodeByproductComplete,
        byproductCoverage &&
          byproductCoverage.remainingAmount > 0 &&
          styles.nodeByproductPartial,
        deferredDuplicate && styles.nodeDeferredRecipe,
        isRoot && !radialRoot && styles.compactRootNode,
        radialRoot && styles.radialRootNode,
      ]}>
      {isRoot && (
        <View
          pointerEvents="none"
          style={[
            radialRoot ? styles.radialRootDiamond : styles.compactRootDiamond,
            rootActions && styles.rootDiamondSelected,
          ]}
        />
      )}
      <ItemIcon
        item={item}
        itemKey={node.key}
        size={radialRoot ? RADIAL_ROOT_ITEM_ICON_SIZE : 32}
      />
      <View
        style={[
          styles.compactCountBadge,
          isRoot && !radialRoot && styles.compactRootCountBadge,
          radialRoot && styles.radialRootCountBadge,
          byproductCoverage && styles.compactByproductCountBadge,
        ]}>
        <Text
          style={[
            styles.compactCountText,
            byproductCoverage && styles.compactByproductCountText,
            noSelect,
          ]}>
          {byproductCoverage?.remainingAmount === 0 ? '✓' : amount}
        </Text>
      </View>
      {showLabel && (
        <View
          pointerEvents="none"
          style={[
            styles.compactBranchLabel,
            isRoot && !radialRoot && styles.compactRootBranchLabel,
            radialRoot && styles.radialRootBranchLabel,
          ]}>
          <Text style={[styles.compactBranchLabelText, noSelect]} numberOfLines={1}>
            {name}
          </Text>
        </View>
      )}
      </Pressable>
      {isRoot && rootActions && (
        <AttachedRootActions
          actions={rootActions}
          nodeX={x}
          nodeY={y}
          nodeWidth={radialRoot ? RADIAL_ROOT_SIZE : COMPACT_ROOT_SIZE}
          nodeHeight={radialRoot ? RADIAL_ROOT_SIZE : COMPACT_ROOT_SIZE}
          buttonWidth={ROOT_ATTACHED_ACTIONS_WIDTH}
        />
      )}
    </>
  );
}

function ItemNodeView({
  x,
  y,
  node,
  requiredAmount,
  byproductCoverage,
  isRoot,
  expandable,
  deferredDuplicate,
  terminalLabel,
  rootActions,
  onTap,
  onInfo,
}: {
  x: number;
  y: number;
  node: ItemTreeNode;
  requiredAmount: number | null;
  byproductCoverage?: NodeByproductCoverage;
  isRoot: boolean;
  expandable: boolean;
  deferredDuplicate: boolean;
  terminalLabel: string;
  rootActions?: RootNodeActionProps;
  onTap: () => void;
  onInfo: () => void;
}) {
  const data = useData();
  const item = data.itemsByKey.get(node.key);
  const name = displayIngredientName(
    item?.n ?? node.key,
    node.tag,
    data.descriptor.minecraftVersion,
  );
  const glyph =
    node.loading
      ? '…'
      : byproductCoverage?.remainingAmount === 0
        ? '↗'
        : deferredDuplicate
          ? '⇥'
          : expandable
          ? '▸'
          : '·';
  const byproductText = byproductCoverage
    ? byproductCoverage.remainingAmount === 0
      ? `  ✓ ${formatIngredientQuantity(node.key, byproductCoverage.creditedAmount)} byproduct`
      : `  ${formatIngredientQuantity(node.key, byproductCoverage.remainingAmount)} needed · ${formatIngredientQuantity(node.key, byproductCoverage.creditedAmount)} byproduct`
    : '';
  return (
    <>
      <Pressable
      {...signalTarget(`graph.node.expand.${nodeDepthBucket(node)}`)}
      onPress={onTap}
      accessibilityRole="button"
      accessibilityLabel={`${name}, quantity ${formatIngredientQuantity(node.key, requiredAmount)}${!expandable ? `, ${terminalLabel}` : ''}${deferredDuplicate ? ', recipe expanded elsewhere, tap to move expansion here' : ''}${node.productionProbability !== undefined ? `, ${node.productionProbability == null ? 'unknown' : `${String(Math.round(node.productionProbability * 10_000) / 100)} percent`} produce chance` : ''}${isRoot ? ', open amount and recipe controls' : byproductCoverage ? byproductCoverage.remainingAmount === 0 ? ', completed by byproduct, navigate to producing recipe' : `, partially completed by byproduct, ${formatIngredientQuantity(node.key, byproductCoverage.remainingAmount)} still needed, choose source` : expandable && !deferredDuplicate ? ', choose source' : ''}`}
      style={[
        styles.itemNode,
        {left: x, top: y, width: ITEM_W, height: ITEM_H},
        node.nonConsumed && styles.nodePrerequisite,
        isRecursiveItemNode(node) && styles.nodeCyclic,
        !expandable && !isRecursiveItemNode(node) && styles.nodeTerminal,
        byproductCoverage?.remainingAmount === 0 && styles.nodeByproductComplete,
        byproductCoverage &&
          byproductCoverage.remainingAmount > 0 &&
          styles.nodeByproductPartial,
        deferredDuplicate && styles.nodeDeferredRecipe,
        isRoot && styles.nodeRoot,
        isRoot && rootActions && styles.rootNodeSelected,
      ]}>
      {isRoot ? (
        <View style={styles.rootItemIconFrame}>
          <ItemIcon item={item} itemKey={node.key} size={32} />
        </View>
      ) : (
        <ItemIcon item={item} itemKey={node.key} size={32} />
      )}
      <View style={{flex: 1, marginLeft: 7}}>
        <Text style={[styles.itemNodeName, noSelect]} numberOfLines={2}>
          {name}
        </Text>
        <Text style={[styles.itemNodeSub, noSelect]} numberOfLines={1}>
          {glyph}
          {shouldShowIngredientQuantity(node.key, requiredAmount)
            ? `  ${formatIngredientQuantity(node.key, requiredAmount)}`
            : ''}
          {isRecursiveItemNode(node) ? '  ↻' : ''}
          {node.nonConsumed ? '  retained' : ''}
          {node.consumptionProbability !== undefined
            ? `  ${node.consumptionProbability == null ? '?' : `${String(Math.round(node.consumptionProbability * 10_000) / 100)}%`} consume`
            : ''}
          {node.productionProbability !== undefined
            ? `  ${node.productionProbability == null ? '?' : `${String(Math.round(node.productionProbability * 10_000) / 100)}%`} produce`
            : ''}
          {byproductText}
        </Text>
      </View>
      <TouchableOpacity
        {...signalTarget(`graph.node.info.${nodeDepthBucket(node)}`)}
        onPress={onInfo}
        style={styles.infoBtn}
        hitSlop={6}>
        <Text style={[styles.smallBtnText, noSelect]}>ⓘ</Text>
      </TouchableOpacity>
      </Pressable>
      {isRoot && rootActions && (
        <AttachedRootActions
          actions={rootActions}
          nodeX={x}
          nodeY={y}
          nodeWidth={ITEM_W}
          nodeHeight={ITEM_H}
          buttonWidth={ITEM_W}
        />
      )}
    </>
  );
}

/** Expanded item: one node with the item + amount in the header and the source below. */
function SourceNodeView({
  x,
  y,
  w,
  h,
  item,
  requiredAmount,
  byproductCoverage,
  source,
  isRoot,
  radialRoot,
  focused,
  animateMobs,
  rootActions,
  canSwap,
  onCollapse,
  onSwap,
  onInfo,
}: {
  x: number;
  y: number;
  w: number;
  h: number;
  item: ItemTreeNode;
  requiredAmount: number | null;
  byproductCoverage?: NodeByproductCoverage;
  source: SourceTreeNode;
  isRoot: boolean;
  radialRoot: boolean;
  focused: boolean;
  animateMobs: boolean;
  rootActions?: RootNodeActionProps;
  canSwap: boolean;
  onCollapse: () => void;
  onSwap: () => void;
  onInfo: () => void;
}) {
  const data = useData();
  const catalogItem = data.itemsByKey.get(item.key);
  const concreteName = catalogItem?.n ?? item.key;
  const logicalName = displayIngredientName(
    concreteName,
    item.tag,
    data.descriptor.minecraftVersion,
  );
  const name =
    item.tag && logicalName !== concreteName
      ? `${logicalName} · ${concreteName}`
      : concreteName;
  const amountText = shouldShowIngredientQuantity(item.key, requiredAmount)
    ? ` ${formatIngredientQuantity(item.key, requiredAmount)}`
    : '';
  const context =
    source.kind === 'recipe'
      ? source.direction === 'outputs'
        ? `Usage · ${source.catTitle ?? 'Recipe'}`
        : source.catTitle
      : source.kind === 'mob'
        ? 'Mob drop'
        : 'Mining';
  const category = source.ref ? data.categories[source.ref[0]] : undefined;
  const machineEstimate =
    source.kind === 'recipe' && source.recipe && item.productionPlan
      ? estimateParallelMachines(
          source.recipe,
          item.key,
          category?.id,
          item.productionPlan,
        )
      : null;
  const machineKey = category?.catalysts[0];
  const machineName = machineKey
    ? data.itemsByKey.get(machineKey)?.n ?? machineKey
    : 'machine';
  const sourceCardWidth =
    isRoot && rootActions ? w - ROOT_SOURCE_ACTIONS_WIDTH : w;
  const headerCopy = (
    <Text style={[styles.sourceHeaderText, noSelect]} numberOfLines={1}>
      {name}
      <Text style={[styles.sourceHeaderAmount, noSelect]}>{amountText}</Text>
      <Text style={[styles.sourceHeaderContext, noSelect]}> · {context}</Text>
      {byproductCoverage && (
        <Text style={[styles.sourceHeaderByproduct, noSelect]}>
          {' · '}
          {formatIngredientQuantity(item.key, byproductCoverage.creditedAmount)} byproduct
        </Text>
      )}
      {machineEstimate && (
        <Text style={[styles.sourceHeaderParallel, noSelect]}>
          {' · '}
          {machineEstimate.machines}× {machineName}
        </Text>
      )}
    </Text>
  );

  return (
    <Pressable
      {...(isRoot ? signalTarget('graph.root-actions.toggle-expanded') : {})}
      accessibilityRole={isRoot ? 'button' : undefined}
      accessibilityLabel={
        isRoot
          ? `${rootActions ? 'Close' : 'Open'} amount and recipe controls for ${name}`
          : undefined
      }
      onPress={isRoot ? onCollapse : undefined}
      style={[
        styles.sourceNode,
        {left: x, top: y, width: sourceCardWidth, height: h},
        item.nonConsumed && styles.nodePrerequisite,
        isRecursiveItemNode(item) && styles.nodeCyclic,
        source.inputs.length === 0 && !isRecursiveItemNode(item) && styles.nodeTerminal,
        byproductCoverage?.remainingAmount === 0 && styles.nodeByproductComplete,
        byproductCoverage &&
          byproductCoverage.remainingAmount > 0 &&
          styles.nodeByproductPartial,
        focused && styles.nodeByproductTarget,
        isRoot && !radialRoot && styles.nodeRoot,
        radialRoot && styles.radialExpandedRootNode,
        isRoot && rootActions &&
          (radialRoot ? styles.radialRootSelected : styles.rootNodeSelected),
      ]}>
      <Pressable
        {...signalTarget(`graph.node.collapse.${nodeDepthBucket(item)}`)}
        pointerEvents={isRoot ? 'box-none' : 'auto'}
        accessibilityRole={isRoot ? undefined : 'button'}
        onPress={isRoot ? undefined : onCollapse}
        style={styles.sourceHeader}>
        {isRoot ? (
          <View style={styles.rootSourceIconFrame}>
            <ItemIcon item={catalogItem} itemKey={item.key} size={16} />
          </View>
        ) : (
          <ItemIcon item={catalogItem} itemKey={item.key} size={16} />
        )}
        {headerCopy}
        {canSwap && (
          <TouchableOpacity
            {...signalTarget(`graph.node.swap.${nodeDepthBucket(item)}`)}
            onPress={onSwap}
            hitSlop={6}
            style={styles.headerBtn}>
            <Text style={[styles.smallBtnText, noSelect]}>⇄</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity
          {...signalTarget(`graph.node.info.${nodeDepthBucket(item)}`)}
          onPress={onInfo}
          hitSlop={6}
          style={styles.headerBtn}>
          <Text style={[styles.smallBtnText, noSelect]}>ⓘ</Text>
        </TouchableOpacity>
        <Text style={[styles.smallBtnText, noSelect]}>{EXPANDED_DISCLOSURE_CHEVRON}</Text>
      </Pressable>

      {source.kind === 'recipe' && source.recipe?.img && source.dir && (
        <RecipePreviewImage
          uri={data.imageUrl(recipeImagePath(source.dir, source.recipe.img))!}
          backgroundUri={
            source.recipe.bg
              ? data.imageUrl(recipeImagePath(source.dir, source.recipe.bg))
              : undefined
          }
          context={source.recipe.id ?? `${source.dir} graph recipe`}
          style={[
            {
              width: recipeImageDisplay(source.recipe).w,
              height: recipeImageDisplay(source.recipe).h,
              alignSelf: 'center' as const,
            },
            pixelated as object,
          ]}
          resizeMode="contain"
        />
      )}
      {source.kind === 'recipe' && source.recipe && (!source.recipe.img || !source.dir) && (
        <Text style={[styles.dropStat, noSelect]}>Structured recipe · layout preview unavailable</Text>
      )}
      {source.kind === 'mob' && source.mob && (
        <View style={styles.dropRow}>
          <MobSprite mob={source.mob} size={56} animate={animateMobs} />
          <View style={{flex: 1, marginLeft: 6}}>
            <Text style={[styles.dropName, noSelect]} numberOfLines={1}>
              {source.mob.n}
            </Text>
            {source.stat && (
              <Text style={[styles.dropStat, noSelect]}>{formatDropStat(source.stat)}</Text>
            )}
          </View>
        </View>
      )}
      {source.kind === 'block' && source.blockKey && (
        <View style={styles.dropRow}>
          <ItemIcon itemKey={source.blockKey} size={32} />
          <View style={{flex: 1, marginLeft: 8}}>
            <Text style={[styles.dropName, noSelect]} numberOfLines={1}>
              {data.itemsByKey.get(source.blockKey)?.n ?? source.blockKey}
            </Text>
            {source.stat && (
              <Text style={[styles.dropStat, noSelect]}>{formatDropStat(source.stat)}</Text>
            )}
          </View>
        </View>
      )}
      {isRoot && rootActions && (
        <>
          <View
            style={[
              styles.rootSourceAmountStepper,
              {top: Math.max(0, (h - ROOT_AMOUNT_STEPPER_HEIGHT) / 2)},
            ]}>
            <RootAmountStepper {...rootActions} />
          </View>
          <View style={styles.rootSourceActionButtons}>
            <RootActionButtons {...rootActions} />
          </View>
        </>
      )}
    </Pressable>
  );
}

function CtrlBtn({
  label,
  accessibilityLabel,
  metricsId,
  active = false,
  onPress,
}: {
  label: string;
  accessibilityLabel?: string;
  metricsId: string;
  active?: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      {...signalTarget(metricsId)}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{selected: active}}
      style={[styles.ctrlBtn, active && styles.ctrlBtnActive]}
      onPress={onPress}>
      <Text style={[styles.ctrlBtnText, active && styles.ctrlBtnTextActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1},
  canvas: {flex: 1, overflow: 'hidden', backgroundColor: theme.bg},
  anchor: {position: 'absolute', left: 0, top: 0, width: 0, height: 0},
  nativeAnchor: {width: 1, height: 1},
  edge: {position: 'absolute', backgroundColor: theme.borderLight},
  itemNode: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 9,
    paddingHorizontal: 8,
  },
  compactItemNode: {
    position: 'absolute',
    width: COMPACT_ITEM_SIZE,
    height: COMPACT_ITEM_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 9,
  },
  compactBranchNode: {
    borderColor: theme.accent,
    borderWidth: 2,
    borderRadius: COMPACT_ITEM_SIZE / 2,
  },
  compactRootNode: {
    width: COMPACT_ROOT_SIZE,
    height: COMPACT_ROOT_SIZE,
    borderWidth: 0,
    borderRadius: 0,
    backgroundColor: 'transparent',
  },
  radialRootNode: {
    width: RADIAL_ROOT_SIZE,
    height: RADIAL_ROOT_SIZE,
    borderWidth: 0,
    borderRadius: 0,
    backgroundColor: 'transparent',
  },
  radialRootDiamond: {
    position: 'absolute',
    width: RADIAL_ROOT_DIAMOND_SIZE,
    height: RADIAL_ROOT_DIAMOND_SIZE,
    borderRadius: 17,
    borderColor: theme.radialRoot,
    borderWidth: 1,
    backgroundColor: theme.radialRootPanel,
    transform: [{rotate: '45deg'}],
  },
  compactRootDiamond: {
    position: 'absolute',
    width: COMPACT_ROOT_DIAMOND_SIZE,
    height: COMPACT_ROOT_DIAMOND_SIZE,
    borderRadius: 12,
    borderColor: theme.radialRoot,
    borderWidth: 1,
    backgroundColor: theme.radialRootPanel,
    transform: [{rotate: '45deg'}],
  },
  radialRootBranchLabel: {
    top: RADIAL_ROOT_SIZE + COMPACT_ROOT_LABEL_GAP,
    left: -(COMPACT_LABEL_WIDTH - RADIAL_ROOT_SIZE) / 2,
  },
  compactRootBranchLabel: {
    top: COMPACT_ROOT_SIZE + COMPACT_ROOT_LABEL_GAP,
    left: -(COMPACT_LABEL_WIDTH - COMPACT_ROOT_SIZE) / 2,
  },
  compactBranchLabel: {
    position: 'absolute',
    top: COMPACT_ITEM_SIZE + 4,
    left: -(COMPACT_LABEL_WIDTH - COMPACT_ITEM_SIZE) / 2,
    width: COMPACT_LABEL_WIDTH,
    alignItems: 'center',
  },
  compactBranchLabelText: {
    color: theme.textDim,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    textAlign: 'center',
  },
  radialItemNode: {
    position: 'absolute',
    width: RADIAL_ITEM_SIZE,
    height: RADIAL_ITEM_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: RADIAL_ITEM_SIZE / 2,
  },
  compactCountBadge: {
    position: 'absolute',
    right: 2,
    bottom: 2,
    minWidth: 19,
    height: 16,
    paddingHorizontal: 3,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 5,
    backgroundColor: 'rgba(14,17,22,0.9)',
  },
  compactCountText: {color: theme.text, fontSize: 9, fontWeight: '700'},
  compactRootCountBadge: {
    right: 8,
    bottom: 8,
  },
  radialRootCountBadge: {
    right: 18,
    bottom: 18,
  },
  compactByproductCountBadge: {
    borderColor: theme.accentAlt,
    borderWidth: 1,
    backgroundColor: 'rgba(24,53,88,0.96)',
  },
  compactByproductCountText: {color: theme.accentAlt},
  nodeLoading: {opacity: 0.55},
  nodeRoot: {
    borderColor: theme.radialRoot,
    borderWidth: 1,
    backgroundColor: theme.radialRootPanel,
  },
  rootNodeSelected: {borderWidth: 2},
  radialExpandedRootNode: {
    backgroundColor: theme.radialRootPanel,
    borderColor: theme.radialRoot,
    borderWidth: 1,
    borderRadius: 22,
  },
  radialRootSelected: {borderWidth: 3},
  rootDiamondSelected: {borderWidth: 3},
  nodePrerequisite: {borderColor: theme.borderLight, borderStyle: 'dashed'},
  nodeCyclic: {borderColor: theme.warn},
  nodeTerminal: {borderColor: theme.textDim, borderWidth: 2},
  nodeByproductComplete: {
    borderColor: theme.accentAlt,
    borderWidth: 2,
  },
  nodeByproductPartial: {
    borderColor: theme.accentAlt,
    borderWidth: 2,
    borderStyle: 'dashed',
  },
  nodeByproductTarget: {
    borderColor: theme.accentAlt,
    borderWidth: 3,
  },
  nodeDeferredRecipe: {
    borderColor: theme.transfer,
    borderWidth: 2,
    borderStyle: 'dotted',
  },
  rootItemIconFrame: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rootSourceIconFrame: {
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  itemNodeName: {color: theme.text, fontSize: 11, lineHeight: 14},
  itemNodeSub: {color: theme.textDim, fontSize: 10, marginTop: 2},
  infoBtn: {paddingLeft: 4},
  smallBtnText: {color: theme.textDim, fontSize: 12},
  sourceNode: {
    position: 'absolute',
    backgroundColor: theme.panelAlt,
    borderColor: theme.borderLight,
    borderWidth: 1,
    borderRadius: 9,
    padding: 5,
  },
  sourceHeader: {
    height: SOURCE_HEADER - 5,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginBottom: 3,
  },
  sourceHeaderText: {color: theme.text, fontSize: 11, fontWeight: '600', flex: 1},
  sourceHeaderAmount: {color: theme.accent, fontWeight: '700'},
  sourceHeaderContext: {color: theme.textDim, fontWeight: '400'},
  sourceHeaderByproduct: {color: theme.accentAlt, fontWeight: '600'},
  sourceHeaderParallel: {color: theme.warn, fontWeight: '700'},
  headerBtn: {paddingHorizontal: 2},
  dropRow: {flexDirection: 'row', alignItems: 'center', flex: 1, paddingHorizontal: 4},
  dropName: {color: theme.text, fontSize: 12},
  dropStat: {color: theme.textDim, fontSize: 10, marginTop: 2},
  rootSourceAmountStepper: {
    position: 'absolute',
    right: -40,
  },
  rootSourceActionButtons: {
    position: 'absolute',
    left: 6,
    right: 6,
    bottom: 6,
  },
  attachedRootActions: {
    position: 'absolute',
    zIndex: 12,
  },
  attachedRootStepper: {
    position: 'absolute',
  },
  attachedRootButtons: {
    position: 'absolute',
  },
  rootNodeAmountRail: {
    width: 34,
    alignItems: 'center',
    gap: 4,
  },
  rootNodeStepButton: {
    width: 34,
    height: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 6,
  },
  rootNodeStepText: {color: theme.radialRoot, fontSize: 17, fontWeight: '700'},
  rootNodeIncreaseButton: {
    backgroundColor: theme.radialRoot,
    borderColor: theme.radialRoot,
  },
  rootNodeIncreaseText: {color: '#0b1610'},
  rootNodeAmountInput: {
    width: 34,
    height: 26,
    backgroundColor: '#0f141b',
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 7,
    color: theme.text,
    fontSize: 11,
    fontWeight: '700',
    paddingHorizontal: 0,
    paddingVertical: 0,
    textAlign: 'center',
  },
  rootNodeActionButtons: {flex: 1, flexDirection: 'row', gap: 6},
  rootNodeSecondaryAction: {
    flex: 1,
    minHeight: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderColor: theme.borderLight,
    borderWidth: 1,
    borderRadius: 6,
  },
  rootNodeSecondaryActionText: {color: theme.text, fontSize: 9, fontWeight: '800'},
  rootNodePrimaryAction: {
    flex: 1,
    minHeight: 34,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.radialRoot,
    borderRadius: 6,
  },
  rootNodePrimaryActionText: {color: '#0b1610', fontSize: 9, fontWeight: '800'},
  controls: {
    position: 'absolute',
    top: 10,
    right: 10,
    flexDirection: 'row',
    gap: 6,
  },
  layoutFallbackNotice: {
    position: 'absolute',
    left: 62,
    bottom: 10,
    maxWidth: 420,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderWidth: 1,
    borderColor: theme.warn,
    borderRadius: 8,
    backgroundColor: 'rgba(23,29,38,0.96)',
  },
  layoutFallbackText: {color: theme.warn, fontSize: 10, lineHeight: 14},
  recipeLookupBackdrop: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    zIndex: 50,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(5,8,12,0.72)',
    padding: 20,
  },
  recipeLookupCard: {
    width: '100%',
    maxWidth: 360,
    alignItems: 'center',
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 12,
    padding: 22,
  },
  recipeLookupTitle: {
    color: theme.text,
    fontSize: 16,
    fontWeight: '700',
    textAlign: 'center',
    marginTop: 14,
  },
  recipeLookupHint: {color: theme.textDim, fontSize: 12, marginTop: 5},
  recipeLookupCancel: {
    minWidth: 120,
    minHeight: 42,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.panelAlt,
    borderColor: theme.borderLight,
    borderWidth: 1,
    borderRadius: 8,
    marginTop: 18,
    paddingHorizontal: 18,
  },
  recipeLookupCancelText: {color: theme.text, fontSize: 13, fontWeight: '700'},
  controlOptions: {
    flexDirection: 'row',
    gap: 6,
  },
  totalsPanel: {
    position: 'absolute',
    top: 54,
    right: 10,
    width: 360,
    maxWidth: '92%',
    maxHeight: '62%',
    backgroundColor: 'rgba(23,29,38,0.97)',
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 10,
    overflow: 'hidden',
  },
  totalsTitle: {
    color: theme.text,
    fontSize: 14,
    fontWeight: '700',
    flex: 1,
  },
  totalsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    paddingTop: 10,
  },
  totalsOption: {
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 5,
  },
  totalsOptionActive: {borderColor: theme.accent, backgroundColor: '#173724'},
  totalsOptionText: {color: theme.textDim, fontSize: 10, fontWeight: '700'},
  totalsOptionTextActive: {color: theme.accent},
  exportActions: {
    flexDirection: 'row',
    gap: 6,
    paddingHorizontal: 12,
    paddingTop: 8,
  },
  exportBtn: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 30,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 6,
    backgroundColor: theme.panelAlt,
    paddingHorizontal: 6,
  },
  exportBtnDisabled: {opacity: 0.55},
  exportBtnText: {color: theme.text, fontSize: 10, fontWeight: '700'},
  exportMessage: {color: theme.textDim, fontSize: 9, paddingHorizontal: 12, paddingTop: 6},
  totalsScroll: {flexShrink: 1},
  totalsContent: {paddingHorizontal: 12, paddingBottom: 10},
  totalsSection: {marginTop: 10},
  totalsSectionTitle: {
    color: theme.textDim,
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  totalsEmpty: {color: theme.textDim, fontSize: 11, fontStyle: 'italic'},
  totalRow: {flexDirection: 'row', alignItems: 'center', gap: 7, minHeight: 30},
  totalName: {color: theme.text, fontSize: 11, flex: 1},
  totalAmount: {color: theme.accent, fontSize: 11, fontWeight: '700'},
  ctrlBtn: {
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 8,
    height: 36,
    paddingHorizontal: 10,
    minWidth: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ctrlBtnActive: {borderColor: theme.accent, backgroundColor: '#173724'},
  ctrlBtnText: {color: theme.text, fontSize: 13},
  ctrlBtnTextActive: {color: theme.accent, fontWeight: '700'},
  controlMenuBtn: {
    width: 38,
    paddingHorizontal: 0,
  },
  controlMenuBtnText: {
    fontSize: 18,
    lineHeight: 18,
    fontWeight: '800',
  },
  fitControl: {
    position: 'absolute',
    left: 10,
    bottom: 10,
    width: 40,
    paddingHorizontal: 0,
  },
  fitControlIcon: {
    fontSize: 21,
    lineHeight: 21,
  },
  emptyWrap: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: 30},
  emptyTitle: {color: theme.text, fontSize: 17, fontWeight: '700'},
  emptyText: {
    color: theme.textDim,
    textAlign: 'center',
    maxWidth: 440,
    marginTop: 8,
    lineHeight: 20,
  },
  emptyBtn: {
    marginTop: 16,
    backgroundColor: theme.accent,
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 9,
  },
  emptyBtnText: {color: '#0b2613', fontWeight: '700'},
});
