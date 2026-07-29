import React, {createContext, useCallback, useContext, useMemo, useState} from 'react';
import {
  loadHiddenRecipeStages,
  persistHiddenRecipeStages,
  toggleHiddenRecipeStage,
} from './recipeStagePreferences';
import {
  isValidRecipeStage,
  recipeStageCatalogForDataset,
  type RecipeStageCatalog,
} from './recipeStages';
import {useData} from './DataContext';

interface RecipeStageControls {
  catalog: RecipeStageCatalog;
  hiddenStages: ReadonlySet<string>;
  selectedStage: string | null;
  toggleStage(stage: string): void;
  showAllStages(): void;
  hideAllStages(): void;
  selectStage(stage: string | null): void;
}

const RecipeStageContext = createContext<RecipeStageControls | null>(null);

export function RecipeStageProvider({children}: {children: React.ReactNode}) {
  const data = useData();
  const catalog = useMemo(
    () => recipeStageCatalogForDataset(data.descriptor),
    [data.descriptor],
  );
  const knownStages = useMemo(
    () => new Set(catalog.stages.map(summary => summary.stage)),
    [catalog.stages],
  );
  const [hiddenStages, setHiddenStages] = useState<Set<string>>(() =>
    loadHiddenRecipeStages(data.descriptor.slug),
  );
  const [selectedStage, setSelectedStage] = useState<string | null>(null);

  const updateHiddenStages = useCallback(
    (update: (current: ReadonlySet<string>) => Set<string>) => {
      setHiddenStages(current => {
        const next = update(current);
        persistHiddenRecipeStages(data.descriptor.slug, next);
        return next;
      });
    },
    [data.descriptor.slug],
  );

  const toggleStage = useCallback(
    (stage: string) => {
      if (!isValidRecipeStage(stage)) {
        throw new Error(`Cannot toggle invalid recipe stage ${JSON.stringify(stage)}.`);
      }
      updateHiddenStages(current => toggleHiddenRecipeStage(current, stage));
    },
    [updateHiddenStages],
  );

  const showAllStages = useCallback(() => {
    updateHiddenStages(current => {
      const next = new Set(current);
      for (const stage of knownStages) next.delete(stage);
      return next;
    });
  }, [knownStages, updateHiddenStages]);

  const hideAllStages = useCallback(() => {
    updateHiddenStages(current => {
      const next = new Set(current);
      for (const stage of knownStages) next.add(stage);
      return next;
    });
  }, [knownStages, updateHiddenStages]);

  const selectStage = useCallback(
    (stage: string | null) => {
      if (stage !== null && !knownStages.has(stage)) {
        throw new Error(`Cannot browse unknown recipe stage ${JSON.stringify(stage)}.`);
      }
      setSelectedStage(stage);
    },
    [knownStages],
  );

  const value = useMemo<RecipeStageControls>(
    () => ({
      catalog,
      hiddenStages,
      selectedStage,
      toggleStage,
      showAllStages,
      hideAllStages,
      selectStage,
    }),
    [
      catalog,
      hiddenStages,
      selectedStage,
      toggleStage,
      showAllStages,
      hideAllStages,
      selectStage,
    ],
  );
  return (
    <RecipeStageContext.Provider value={value}>
      {children}
    </RecipeStageContext.Provider>
  );
}

export function useRecipeStages(): RecipeStageControls {
  const context = useContext(RecipeStageContext);
  if (!context) throw new Error('RecipeStageProvider missing');
  return context;
}
