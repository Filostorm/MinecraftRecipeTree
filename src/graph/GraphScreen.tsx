import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  PanResponder,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {formatDropStat} from '../components/DropList';
import {ItemIcon, pixelated} from '../components/ItemIcon';
import {MobSprite} from '../components/MobSprite';
import {
  PickerGroupProgress,
  PickerModal,
  PickerOption,
} from '../components/PickerModal';
import {
  inputSlotSummary,
  prerequisiteSummary,
  slotSummary,
} from '../data/slotSummary';
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
import {isFluidContainerTransferRecipe} from '../data/recipeVisibility';
import {recipeDisplayTitle} from '../data/recipeTitles';
import {theme} from '../theme';
import {DropStat, Mob, Recipe, RecipeRef} from '../types';
import {useUi} from '../ui/UiContext';
import {
  COMPACT_ITEM_SIZE,
  ITEM_H,
  ITEM_W,
  SOURCE_HEADER,
  layoutTree,
  recipeImageDisplay,
} from './layout';
import {
  RADIAL_BRANCH_LABEL_WIDTH,
  RADIAL_ITEM_SIZE,
  RADIAL_ROOT_SIZE,
  layoutRadialTree,
} from './radialLayout';
import {
  PreferredSource,
  PreferredSources,
  loadPreferredSources,
  persistPreferredSources,
} from './preferredSources';
import {automaticGraphFitScale} from './fitScale';
import {
  capturePanGestureOrigin,
  graphPinchZoomFactor,
  graphViewportPointFromClient,
  graphWheelZoomFactor,
  transformForPanGesture,
} from './panGesture';
import type {GraphTransform, PanGestureOrigin} from './panGesture';
import {recordRecipeHistory} from './recipeHistory';
import {planRecipePickerChoices} from './recipePickerPlan';
import {recipeChildrenForDirection} from './direction';
import {makeRoot} from './model';
import type {ItemTreeNode, SourceTreeNode} from './model';
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

/** One way to obtain an item: craft it, kill for it, or mine for it. */
type SourceChoice =
  | {t: 'recipe'; ref: RecipeRef; allowFluidTransfer?: true}
  | {t: 'mob'; mob: Mob; stat: DropStat}
  | {t: 'block'; blockKey: string; stat: DropStat};

interface PickerEntry {
  option: PickerOption;
  choice: SourceChoice;
}

type RecipeSourceChoice = Extract<SourceChoice, {t: 'recipe'}>;

interface PickerState {
  requestId: number;
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
  collapsedGroupKeys: Set<string>;
}

function preferredSourceFromChoice(choice: SourceChoice): PreferredSource {
  if (choice.t === 'recipe') {
    return choice.allowFluidTransfer
      ? {t: 'recipe', ref: choice.ref, allowFluidTransfer: true}
      : {t: 'recipe', ref: choice.ref};
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

/** Dragging the canvas must never start a text selection (web). */
const noSelect = Platform.OS === 'web' ? ({userSelect: 'none'} as unknown as object) : null;
const COMPACT_MODE_KEY = 'graphCompactMode';
const RADIAL_LAYOUT_KEY = 'graphRadialLayout';
const LEGACY_PACKED_LAYOUT_KEY = 'graphPackedLayout';
const USE_BYPRODUCTS_KEY = 'graphUseByproducts';
const MAX_RECIPE_PICKER_CHOICES = 40;
const RECIPE_PICKER_GROUP_PAGE = 40;
const GRAPH_EXPORT_PADDING = 48;
const GRAPH_EXPORT_PIXEL_RATIO = 3;

function recipeRefKey([categoryIndex, recipeIndex]: RecipeRef): string {
  return `${categoryIndex}:${recipeIndex}`;
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

export function GraphScreen({interfaceZoom = 1}: {interfaceZoom?: number}) {
  const data = useData();
  const {
    graphRootKey,
    graphRequestId,
    graphRecipeRef,
    graphDirection,
    openItem,
    setTab,
    animateMobs,
  } = useUi();

  const [root, setRoot] = useState<ItemTreeNode | null>(null);
  const rootRef = useRef<ItemTreeNode | null>(null);
  rootRef.current = root;
  const [version, setVersion] = useState(0);
  const bump = useCallback(() => setVersion(v => v + 1), []);
  const [picker, setPicker] = useState<PickerState | null>(null);
  const pickerRef = useRef<PickerState | null>(null);
  pickerRef.current = picker;
  const pickerGroupLoadsRef = useRef(new Set<string>());
  const pickerRequestIdRef = useRef(0);
  const [compactMode, setCompactMode] = useState(loadCompactMode);
  const [radialLayout, setRadialLayout] = useState(loadRadialLayout);
  const [showTreeTotals, setShowTreeTotals] = useState(true);
  const [useByproducts, setUseByproducts] = useState(loadUseByproducts);
  const [exportingTree, setExportingTree] = useState(false);
  const [exportMessage, setExportMessage] = useState<string | null>(null);
  const [preferredSources, setPreferredSources] =
    useState<PreferredSources>(loadPreferredSources);
  const preferredSourcesRef = useRef(preferredSources);
  preferredSourcesRef.current = preferredSources;

  const [transform, setTransform] = useState<GraphTransform>({x: 60, y: 60, scale: 1});
  const transformRef = useRef(transform);
  transformRef.current = transform;
  const applyTransform = useCallback((next: GraphTransform) => {
    // Gesture events may arrive before React commits the preceding render. Keep
    // the imperative reference synchronized so every event sees the newest transform.
    transformRef.current = next;
    setTransform(next);
  }, []);
  const viewportRef = useRef({w: 0, h: 0});
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
    (key: string): RecipeRef[] =>
      graphDirection === 'outputs' ? usagesFor(key) : recipesFor(key),
    [graphDirection, recipesFor, usagesFor],
  );

  /** Direction-appropriate recipe choices, plus physical sources for ingredient trees. */
  const choicesFor = useCallback(
    (key: string): SourceChoice[] => {
      const recipes = recipeRefsFor(key).map(
        ref => ({t: 'recipe', ref}) as SourceChoice,
      );
      if (graphDirection === 'outputs') return recipes;
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
    async (node: ItemTreeNode, ref: RecipeRef, allowFluidTransfer = false) => {
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
          return;
        }
        if (
          !allowFluidTransfer &&
          isFluidContainerTransferRecipe(recipe, data.itemsByKey)
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
          return;
        }
        if (graphDirection === 'outputs') {
          const anchor = [
            ...inputSlotSummary(recipe.in),
            ...prerequisiteSummary(recipe.cat),
          ].find(
            input =>
              input.key === node.key || input.alternatives.includes(node.key),
          );
          if (!anchor) {
            console.error('An output-directed recipe does not use its graph anchor item.', {
              itemKey: node.key,
              recipeRef: ref,
            });
            return;
          }
          node.amount = anchor.amount;
        }
        const sourceId = `${node.id}.s`;
        const childSpecs = recipeChildrenForDirection(recipe, graphDirection);
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
          recipe,
          dir: cat.dir,
          catTitle: recipeDisplayTitle(cat.title, recipe),
          direction: graphDirection,
          inputs: children,
        };
        if (node.id === 'root') {
          recordRecipeHistory(data.descriptor, {
            itemKey: node.key,
            ref,
            title: recipeDisplayTitle(cat.title, recipe),
            recipeId: recipe.id ?? null,
            openedAt: Date.now(),
            direction: graphDirection,
          });
        }
        for (const child of children) {
          if (child.cyclic) continue;
          const preferred =
            graphDirection === 'inputs' ? preferredSourceFor(child.key) : null;
          if (preferred) applyChoiceRef.current?.(child, preferred);
        }
      } catch (error) {
        console.error('The selected graph recipe could not be expanded.', error);
      } finally {
        node.loading = false;
        bump();
      }
    },
    [data, bump, graphDirection, preferredSourceFor],
  );

  /**
   * Expand every currently collapsed occurrence of an item with its newly
   * preferred source. Existing expanded nodes keep their explicit selection.
   */
  const applyPreferredSourceAcrossTree = useCallback(
    (target: ItemTreeNode, choice: SourceChoice) => {
      const matches = new Set<ItemTreeNode>([target]);
      const traversal = rootRef.current ? [rootRef.current] : [];
      while (traversal.length > 0) {
        const node = traversal.pop()!;
        if (node.key === target.key && !node.source && !node.loading && !node.cyclic) {
          matches.add(node);
        }
        const children = node.source?.inputs ?? [];
        for (let index = children.length - 1; index >= 0; index -= 1) {
          traversal.push(children[index]);
        }
      }
      for (const match of matches) {
        if (!match.cyclic && !match.loading && !match.source) {
          applyChoiceRef.current?.(match, choice);
        }
      }
    },
    [],
  );

  const applyChoice = useCallback(
    (node: ItemTreeNode, choice: SourceChoice) => {
      if (choice.t === 'recipe') {
        void expandRecipe(node, choice.ref, choice.allowFluidTransfer === true);
        return;
      }
      const sourceId = `${node.id}.s`;
      node.source =
        choice.t === 'mob'
          ? {id: sourceId, kind: 'mob', mob: choice.mob, stat: choice.stat, inputs: []}
          : {id: sourceId, kind: 'block', blockKey: choice.blockKey, stat: choice.stat, inputs: []};
      bump();
    },
    [expandRecipe, bump],
  );
  applyChoiceRef.current = applyChoice;

  const pickerEntryFor = useCallback(
    (
      targetKey: string,
      choice: SourceChoice,
      recipe?: Recipe,
    ): PickerEntry => {
      const currentPreferred =
        graphDirection === 'inputs' ? preferredSourcesRef.current[targetKey] : undefined;
      const favoritePrefix =
        currentPreferred && choiceMatchesPreference(choice, currentPreferred) ? '★ ' : '';
      const itemName = (key: string) => data.itemsByKey.get(key)?.n ?? key;
      if (choice.t === 'recipe') {
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
          recipe && category
            ? recipeDisplayTitle(category.title, recipe)
            : category?.title;
        return {
          choice,
          option: {
            label: `${favoritePrefix}${title ?? `category ${categoryIndex}`}`,
            groupKey: category?.id ?? `recipe-category:${categoryIndex}`,
            groupLabel: category?.title ?? `Recipe category ${categoryIndex}`,
            sublabel:
              [
                recipe?.id,
                recipe && !recipe.img ? 'JEI layout preview unavailable' : undefined,
              ]
                .filter((value): value is string => !!value)
                .join(' · ') || undefined,
            imageUri:
              recipe?.img && category
                ? data.imageUrl(recipeImagePath(category.dir, recipe.img))
                : undefined,
            imageW: recipe?.w,
            imageH: recipe?.h,
            inputs:
              recipe && graphDirection === 'inputs'
                ? inputSlotSummary(recipe.in)
                : undefined,
            outputs:
              recipe && graphDirection === 'outputs'
                ? slotSummary(recipe.out)
                : undefined,
            prerequisites:
              recipe && graphDirection === 'inputs'
                ? prerequisiteSummary(recipe.cat)
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
    async (target: ItemTreeNode, byproductCoverage?: NodeByproductCoverage) => {
      const requestId = ++pickerRequestIdRef.current;
      const currentPreferred =
        graphDirection === 'inputs' ? preferredSourcesRef.current[target.key] : undefined;
      const allChoices = choicesFor(target.key);
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

      const itemName = data.itemsByKey.get(target.key)?.n ?? target.key;
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
        title:
          graphDirection === 'outputs'
            ? `Use ${itemName} to produce`
            : `Obtain ${itemName}`,
        standardEntries: [
          ...physicalChoices.map(choice => pickerEntryFor(target.key, choice)),
          ...standardRecipeChoices.map(choice =>
            pickerEntryFor(
              target.key,
              choice,
              recipesByRef.get(recipeRefKey(choice.ref)),
            ),
          ),
        ],
        fluidTransferEntries: fluidTransferChoices.map(choice =>
          pickerEntryFor(
            target.key,
            choice,
            recipesByRef.get(recipeRefKey(choice.ref)),
          ),
        ),
        showFluidTransfers: false,
        identifiedFluidTransferCount,
        remainingRecipeChoices,
        recipeGroupProgress,
        target,
        byproductCoverage,
        rememberSource: graphDirection === 'inputs',
        collapsedGroupKeys: loadCollapsedRecipeCategories(),
      });
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
              pickerEntryFor(snapshot.target.key, explicitChoice, recipe),
            );
          } else {
            standardEntries.push(pickerEntryFor(snapshot.target.key, choice, recipe));
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
    (node: ItemTreeNode, byproductCoverage?: NodeByproductCoverage) => {
      void openPicker(node, byproductCoverage).catch(error => {
        console.error('The recipe-source picker could not be opened.', error);
      });
    },
    [openPicker],
  );

  const applyOnlyChoice = useCallback(
    async (node: ItemTreeNode, choice: SourceChoice) => {
      if (graphDirection === 'outputs') {
        applyChoice(node, choice);
        return;
      }
      if (choice.t === 'recipe') {
        const [recipe] = await data.getRecipes([choice.ref]);
        if (isFluidContainerTransferRecipe(recipe, data.itemsByKey)) {
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
      const removedSourceIds = new Set<string>();
      const removedStack = [removedNode];
      while (removedStack.length > 0) {
        const current = removedStack.pop()!;
        if (!current.source) continue;
        removedSourceIds.add(current.source.id);
        for (const child of current.source.inputs) removedStack.push(child);
      }
      if (removedSourceIds.size === 0 || !rootRef.current) return;

      let releasedAmount = 0;
      const treeStack = [rootRef.current];
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
    },
    [],
  );

  const onItemTap = useCallback(
    (node: ItemTreeNode) => {
      if (node.loading) return;
      if (node.source) {
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
    ],
  );

  // (Re)build and refit for every request. The request id is intentionally
  // included so selecting the same item again still resets an off-screen or
  // previously expanded chart.
  useEffect(() => {
    if (!graphRootKey) return;
    const newRoot = makeRoot(graphRootKey);
    rootRef.current = newRoot;
    setRoot(newRoot);
    needsFitRef.current = true;
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
    setPreferredSource,
    applyOnlyChoiceWithErrorHandling,
  ]);

  const graph = useMemo(
    () =>
      root
        ? radialLayout
          ? layoutRadialTree(root, compactMode)
          : layoutTree(root, compactMode)
        : null,
    [root, version, compactMode, radialLayout],
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
    const totals = calculateTreeTotals(root, useByproducts);
    const byName = (a: TreeTotal, b: TreeTotal) =>
      (data.itemsByKey.get(a.key)?.n ?? a.key).localeCompare(data.itemsByKey.get(b.key)?.n ?? b.key);
    totals.inputs.sort(byName);
    totals.prerequisites.sort(byName);
    totals.byproductCredits.sort(byName);
    totals.byproducts.sort(byName);
    return totals;
  }, [root, version, data.itemsByKey, graphDirection, useByproducts]);
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

  const rootExportName = useMemo(
    () => {
      const rootKey = root?.key ?? 'recipe-tree';
      return safeExportFilename(data.itemsByKey.get(rootKey)?.n ?? rootKey);
    },
    [data.itemsByKey, root?.key],
  );

  const exportTotals = useCallback(() => {
    try {
      const csv = buildTreeTotalsCsv(treeTotals, (key, tag) =>
        displayIngredientName(data.itemsByKey.get(key)?.n ?? key, tag),
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
  }, [data.itemsByKey, rootExportName, treeTotals]);

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
        <TouchableOpacity style={styles.emptyBtn} onPress={() => setTab('items')}>
          <Text style={styles.emptyBtnText}>Browse items</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <View
        ref={setCanvasRef}
        style={[styles.canvas, noSelect]}
        onLayout={e => {
          viewportRef.current = {
            w: e.nativeEvent.layout.width,
            h: e.nativeEvent.layout.height,
          };
          // The graph tab mounts hidden; fit once it actually gets a size.
          if (needsFitRef.current && fitView()) {
            needsFitRef.current = false;
          }
        }}
        {...responder.panHandlers}>
        {/* 0x0 anchor so translate/scale apply around the top-left origin */}
        <View
          ref={anchorRef}
          style={[
            styles.anchor,
            {
              transform: [
                {translateX: transform.x},
                {translateY: transform.y},
                {scale: transform.scale},
              ],
            },
          ]}>
          {graph?.edges.map((e, i) => (
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
          {graph?.nodes.map(n =>
            compactMode || n.radial ? (
              <CompactItemNodeView
                key={n.id}
                x={n.x}
                y={n.y}
                node={n.item}
                requiredAmount={displayedAmountFor(n.item)}
                byproductCoverage={treeTotals.byproductCoverageByNode.get(n.item.id)}
                isRoot={n.item.id === 'root'}
                selectable={
                  treeTotals.byproductCoverageByNode.has(n.item.id) ||
                  choicesFor(n.item.key).length > 0
                }
                terminal={choicesFor(n.item.key).length === 0}
                terminalLabel={graphDirection === 'outputs' ? 'no outputs' : 'no inputs'}
                radial={n.radial === true}
                radialRoot={radialLayout && n.item.id === 'root'}
                branchLabel={n.compactBranch === true}
                onTap={() =>
                  handleCollapsedIngredientTap(n.item, () =>
                    n.radial ? onItemTap(n.item) : openPickerWithErrorHandling(n.item),
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
                expandable={choicesFor(n.item.key).length > 0}
                terminalLabel={graphDirection === 'outputs' ? 'no outputs' : 'no inputs'}
                onTap={() =>
                  handleCollapsedIngredientTap(n.item, () => onItemTap(n.item))
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
                canSwap={choicesFor(n.item.key).length > 1}
                onCollapse={() => onItemTap(n.item)}
                onSwap={() => openPickerWithErrorHandling(n.item)}
                onInfo={() => openItem(n.item.key)}
              />
            ),
          )}
        </View>
      </View>

      <View style={styles.controls}>
        {graphDirection === 'inputs' && (
          <CtrlBtn label="Totals" active={showTreeTotals} onPress={() => setShowTreeTotals(value => !value)} />
        )}
        <CtrlBtn label="Radial" active={radialLayout} onPress={toggleRadialLayout} />
        <CtrlBtn label="Compact" active={compactMode} onPress={toggleCompactMode} />
        <CtrlBtn label="＋" onPress={() => zoomAt(viewportRef.current.w / 2, viewportRef.current.h / 2, 1.25)} />
        <CtrlBtn label="－" onPress={() => zoomAt(viewportRef.current.w / 2, viewportRef.current.h / 2, 0.8)} />
        <CtrlBtn label="fit" onPress={fitView} />
      </View>
      {graphDirection === 'inputs' && showTreeTotals && (
        <TreeTotalsPanel
          totals={treeTotals}
          useByproducts={useByproducts}
          exportingTree={exportingTree}
          exportMessage={exportMessage}
          onUseByproductsChange={updateUseByproducts}
          onExportTotals={exportTotals}
          onExportTree={() => void exportTreeImage()}
          onOpenItem={openItem}
        />
      )}
      <Text style={styles.hint}>
        {graphDirection === 'outputs'
          ? 'output tree · tap item = choose a usage recipe · '
          : radialLayout
            ? 'large ingredient levels stagger across radial rings · '
            : ''}
        silver border = {graphDirection === 'outputs' ? 'no outputs' : 'no inputs'} ·{' '}
        {graphDirection === 'inputs' && useByproducts
          ? 'solid blue = completed byproduct (tap to locate source) · dashed blue = partial byproduct (tap to craft remainder) · '
          : ''}
        {compactMode
          ? graphDirection === 'outputs'
            ? 'tap item = pick usage recipe · drag = pan · scroll = zoom'
            : 'tap item = pick recipe/drop source · drag = pan · scroll = zoom'
          : graphDirection === 'outputs'
            ? 'tap node = expand/collapse · ⇄ = pick usage recipe · drag = pan · scroll = zoom'
            : 'tap node = expand/collapse · ⇄ = pick recipe/drop source · drag = pan · scroll = zoom'}
      </Text>

      {picker && (
        <PickerModal
          visible
          interfaceZoom={interfaceZoom}
          title={picker.title}
          options={(picker.showFluidTransfers
            ? [...picker.standardEntries, ...picker.fluidTransferEntries]
            : picker.standardEntries
          ).map(entry => entry.option)}
          rememberSource={picker.rememberSource}
          onRememberSourceChange={
            graphDirection === 'inputs'
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
          collapsedGroupKeys={picker.collapsedGroupKeys}
          onToggleGroup={togglePickerGroup}
          groupProgress={picker.recipeGroupProgress}
          onLoadGroup={groupKey => void loadPickerRecipeGroup(groupKey)}
          onClose={() => setPicker(null)}
          onSelect={i => {
            const p = picker;
            const entries = p.showFluidTransfers
              ? [...p.standardEntries, ...p.fluidTransferEntries]
              : p.standardEntries;
            const choice = entries[i]?.choice;
            if (!choice) {
              console.error('The selected source index was not present in the picker.', {
                selectedIndex: i,
                optionCount: entries.length,
              });
              return;
            }
            setPicker(null);
            if (graphDirection === 'outputs') {
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
  onOpenItem,
}: {
  totals: TreeTotals;
  useByproducts: boolean;
  exportingTree: boolean;
  exportMessage: string | null;
  onUseByproductsChange: (value: boolean) => void;
  onExportTotals: () => void;
  onExportTree: () => void;
  onOpenItem: (key: string) => void;
}) {
  return (
    <View style={styles.totalsPanel}>
      <View style={styles.totalsHeader}>
        <Text style={[styles.totalsTitle, noSelect]}>Tree totals</Text>
        <TouchableOpacity
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
        <TouchableOpacity style={styles.exportBtn} onPress={onExportTotals}>
          <Text style={styles.exportBtnText}>Export resources CSV</Text>
        </TouchableOpacity>
        <TouchableOpacity
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
          onOpenItem={onOpenItem}
        />
        <TreeTotalsSection
          title="Required · not consumed"
          totals={totals.prerequisites}
          onOpenItem={onOpenItem}
        />
        {useByproducts && (
          <TreeTotalsSection
            title="Byproducts used"
            totals={totals.byproductCredits}
            onOpenItem={onOpenItem}
          />
        )}
        <TreeTotalsSection
          title={useByproducts ? 'Byproducts remaining' : 'Byproducts'}
          totals={totals.byproducts}
          onOpenItem={onOpenItem}
        />
      </ScrollView>
    </View>
  );
}

function TreeTotalsSection({
  title,
  totals,
  onOpenItem,
}: {
  title: string;
  totals: TreeTotal[];
  onOpenItem: (key: string) => void;
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
              key={total.key}
              style={styles.totalRow}
              onPress={() => onOpenItem(total.key)}>
              <ItemIcon item={item} itemKey={total.key} size={16} />
              <Text style={[styles.totalName, noSelect]} numberOfLines={1}>
                {displayIngredientName(item?.n ?? total.key, total.tag)}
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
  onTap: () => void;
}) {
  const data = useData();
  const [showRadialLabel, setShowRadialLabel] = useState(false);
  const item = data.itemsByKey.get(node.key);
  const name = displayIngredientName(item?.n ?? node.key, node.tag);
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
    <Pressable
      accessibilityRole={selectable ? 'button' : undefined}
      accessibilityLabel={`${name}, quantity ${formatIngredientQuantity(node.key, requiredAmount)}${terminal ? `, ${terminalLabel}` : ''}${byproductLabel ? `, ${byproductLabel}` : ''}${node.nonConsumed ? ', not consumed' : ''}${node.consumptionProbability !== undefined ? `, ${node.consumptionProbability == null ? 'unknown' : `${String(Math.round(node.consumptionProbability * 10_000) / 100)} percent`} consume chance` : ''}${node.productionProbability !== undefined ? `, ${node.productionProbability == null ? 'unknown' : `${String(Math.round(node.productionProbability * 10_000) / 100)} percent`} produce chance` : ''}${selectable ? byproductCoverage?.remainingAmount === 0 ? ', navigate to producing recipe' : ', choose source' : ''}`}
      disabled={!selectable || node.loading}
      onPress={onTap}
      onHoverIn={radial ? () => setShowRadialLabel(true) : undefined}
      onHoverOut={radial ? () => setShowRadialLabel(false) : undefined}
      onFocus={radial ? () => setShowRadialLabel(true) : undefined}
      onBlur={radial ? () => setShowRadialLabel(false) : undefined}
      style={[
        radial ? styles.radialItemNode : styles.compactItemNode,
        branchLabel && styles.compactBranchNode,
        radialRoot && styles.radialRootNode,
        {left: x, top: y},
        radial && showRadialLabel && styles.radialItemNodeRaised,
        isRoot && !radialRoot && styles.nodeRoot,
        node.nonConsumed && styles.nodePrerequisite,
        node.cyclic && styles.nodeCyclic,
        terminal && styles.nodeTerminal,
        node.loading && styles.nodeLoading,
        byproductCoverage?.remainingAmount === 0 && styles.nodeByproductComplete,
        byproductCoverage &&
          byproductCoverage.remainingAmount > 0 &&
          styles.nodeByproductPartial,
      ]}>
      {radialRoot && <View pointerEvents="none" style={styles.radialRootDiamond} />}
      <ItemIcon item={item} itemKey={node.key} size={radialRoot ? 40 : 32} />
      <View
        style={[
          styles.compactCountBadge,
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
      {radial && showRadialLabel && (
        <View pointerEvents="none" style={styles.radialItemTooltip}>
          <Text style={[styles.radialItemTooltipText, noSelect]} numberOfLines={1}>
            {name} · {byproductLabel ?? amount}
          </Text>
        </View>
      )}
      {branchLabel && (
        <View
          pointerEvents="none"
          style={[
            styles.compactBranchLabel,
            radialRoot && styles.radialRootBranchLabel,
          ]}>
          <Text style={[styles.compactBranchLabelText, noSelect]} numberOfLines={1}>
            {name}
          </Text>
        </View>
      )}
    </Pressable>
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
  terminalLabel,
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
  terminalLabel: string;
  onTap: () => void;
  onInfo: () => void;
}) {
  const data = useData();
  const item = data.itemsByKey.get(node.key);
  const name = displayIngredientName(item?.n ?? node.key, node.tag);
  const glyph =
    node.loading
      ? '…'
      : byproductCoverage?.remainingAmount === 0
        ? '↗'
        : expandable
          ? '▸'
          : '·';
  const byproductText = byproductCoverage
    ? byproductCoverage.remainingAmount === 0
      ? `  ✓ ${formatIngredientQuantity(node.key, byproductCoverage.creditedAmount)} byproduct`
      : `  ${formatIngredientQuantity(node.key, byproductCoverage.remainingAmount)} needed · ${formatIngredientQuantity(node.key, byproductCoverage.creditedAmount)} byproduct`
    : '';
  return (
    <Pressable
      onPress={onTap}
      accessibilityRole="button"
      accessibilityLabel={`${name}, quantity ${formatIngredientQuantity(node.key, requiredAmount)}${!expandable ? `, ${terminalLabel}` : ''}${node.productionProbability !== undefined ? `, ${node.productionProbability == null ? 'unknown' : `${String(Math.round(node.productionProbability * 10_000) / 100)} percent`} produce chance` : ''}${byproductCoverage ? byproductCoverage.remainingAmount === 0 ? ', completed by byproduct, navigate to producing recipe' : `, partially completed by byproduct, ${formatIngredientQuantity(node.key, byproductCoverage.remainingAmount)} still needed, choose source` : expandable ? ', choose source' : ''}`}
      style={[
        styles.itemNode,
        {left: x, top: y, width: ITEM_W, height: ITEM_H},
        isRoot && styles.nodeRoot,
        node.nonConsumed && styles.nodePrerequisite,
        node.cyclic && styles.nodeCyclic,
        !expandable && styles.nodeTerminal,
        byproductCoverage?.remainingAmount === 0 && styles.nodeByproductComplete,
        byproductCoverage &&
          byproductCoverage.remainingAmount > 0 &&
          styles.nodeByproductPartial,
      ]}>
      <ItemIcon item={item} itemKey={node.key} size={32} />
      <View style={{flex: 1, marginLeft: 7}}>
        <Text style={[styles.itemNodeName, noSelect]} numberOfLines={2}>
          {name}
        </Text>
        <Text style={[styles.itemNodeSub, noSelect]} numberOfLines={1}>
          {glyph}
          {shouldShowIngredientQuantity(node.key, requiredAmount)
            ? `  ${formatIngredientQuantity(node.key, requiredAmount)}`
            : ''}
          {node.cyclic ? '  ↻' : ''}
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
      <TouchableOpacity onPress={onInfo} style={styles.infoBtn} hitSlop={6}>
        <Text style={[styles.smallBtnText, noSelect]}>ⓘ</Text>
      </TouchableOpacity>
    </Pressable>
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
  canSwap: boolean;
  onCollapse: () => void;
  onSwap: () => void;
  onInfo: () => void;
}) {
  const data = useData();
  const catalogItem = data.itemsByKey.get(item.key);
  const concreteName = catalogItem?.n ?? item.key;
  const name = item.tag ? `${displayIngredientName(concreteName, item.tag)} · ${concreteName}` : concreteName;
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

  return (
    <View
      style={[
        styles.sourceNode,
        {left: x, top: y, width: w, height: h},
        isRoot && !radialRoot && styles.nodeRoot,
        radialRoot && styles.radialExpandedRootNode,
        item.nonConsumed && styles.nodePrerequisite,
        item.cyclic && styles.nodeCyclic,
        source.inputs.length === 0 && styles.nodeTerminal,
        byproductCoverage?.remainingAmount === 0 && styles.nodeByproductComplete,
        byproductCoverage &&
          byproductCoverage.remainingAmount > 0 &&
          styles.nodeByproductPartial,
        focused && styles.nodeByproductTarget,
      ]}>
      <Pressable onPress={onCollapse} style={styles.sourceHeader}>
        <ItemIcon item={catalogItem} itemKey={item.key} size={16} />
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
        </Text>
        {canSwap && (
          <TouchableOpacity onPress={onSwap} hitSlop={6} style={styles.headerBtn}>
            <Text style={[styles.smallBtnText, noSelect]}>⇄</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity onPress={onInfo} hitSlop={6} style={styles.headerBtn}>
          <Text style={[styles.smallBtnText, noSelect]}>ⓘ</Text>
        </TouchableOpacity>
        <Text style={[styles.smallBtnText, noSelect]}>▴</Text>
      </Pressable>

      {source.kind === 'recipe' && source.recipe?.img && source.dir && (
        <RecipePreviewImage
          uri={data.imageUrl(recipeImagePath(source.dir, source.recipe.img))!}
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
    </View>
  );
}

function CtrlBtn({
  label,
  active = false,
  onPress,
}: {
  label: string;
  active?: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      accessibilityRole="button"
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
  radialRootNode: {
    width: RADIAL_ROOT_SIZE,
    height: RADIAL_ROOT_SIZE,
    borderWidth: 0,
    borderRadius: 0,
    backgroundColor: 'transparent',
  },
  radialRootDiamond: {
    position: 'absolute',
    width: 56,
    height: 56,
    borderRadius: 13,
    borderColor: theme.radialRoot,
    borderWidth: 3,
    backgroundColor: theme.radialRootPanel,
    transform: [{rotate: '45deg'}],
  },
  radialRootBranchLabel: {
    top: RADIAL_ROOT_SIZE + 4,
    left: -(RADIAL_BRANCH_LABEL_WIDTH - RADIAL_ROOT_SIZE) / 2,
  },
  compactBranchLabel: {
    position: 'absolute',
    top: COMPACT_ITEM_SIZE + 4,
    left: -(RADIAL_BRANCH_LABEL_WIDTH - COMPACT_ITEM_SIZE) / 2,
    width: RADIAL_BRANCH_LABEL_WIDTH,
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
  radialItemNodeRaised: {
    zIndex: 20,
    borderColor: theme.accent,
  },
  radialItemTooltip: {
    position: 'absolute',
    left: RADIAL_ITEM_SIZE / 2,
    bottom: RADIAL_ITEM_SIZE + 5,
    maxWidth: 220,
    minWidth: 96,
    paddingHorizontal: 8,
    paddingVertical: 5,
    borderRadius: 6,
    borderColor: theme.borderLight,
    borderWidth: 1,
    backgroundColor: 'rgba(14,17,22,0.97)',
  },
  radialItemTooltipText: {
    color: theme.text,
    fontSize: 10,
    fontWeight: '600',
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
  compactByproductCountBadge: {
    borderColor: theme.accentAlt,
    borderWidth: 1,
    backgroundColor: 'rgba(24,53,88,0.96)',
  },
  compactByproductCountText: {color: theme.accentAlt},
  nodeLoading: {opacity: 0.55},
  nodeRoot: {borderColor: theme.accent, borderWidth: 2},
  radialExpandedRootNode: {
    backgroundColor: theme.radialRootPanel,
    borderColor: theme.radialRoot,
    borderWidth: 3,
    borderRadius: 22,
  },
  nodePrerequisite: {borderColor: theme.warn, borderStyle: 'dashed'},
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
  headerBtn: {paddingHorizontal: 2},
  dropRow: {flexDirection: 'row', alignItems: 'center', flex: 1, paddingHorizontal: 4},
  dropName: {color: theme.text, fontSize: 12},
  dropStat: {color: theme.textDim, fontSize: 10, marginTop: 2},
  controls: {
    position: 'absolute',
    top: 10,
    right: 10,
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
    paddingHorizontal: 12,
    paddingVertical: 7,
    minWidth: 40,
    alignItems: 'center',
  },
  ctrlBtnActive: {borderColor: theme.accent, backgroundColor: '#173724'},
  ctrlBtnText: {color: theme.text, fontSize: 13},
  ctrlBtnTextActive: {color: theme.accent, fontWeight: '700'},
  hint: {
    position: 'absolute',
    left: 12,
    bottom: 10,
    color: theme.textDim,
    fontSize: 11,
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
