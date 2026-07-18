import React, {createContext, useCallback, useContext, useMemo, useState} from 'react';
import {RecipeRef} from '../types';

export type Tab = 'items' | 'graph' | 'mobs';

function loadAnimateMobs(): boolean {
  try {
    return globalThis.localStorage?.getItem('animateMobs') !== '0';
  } catch {
    return true;
  }
}

interface Ui {
  tab: Tab;
  setTab(t: Tab): void;
  /** Item-detail modal stack (navigating between items keeps history). */
  itemStack: string[];
  openItem(key: string): void;
  popItem(): void;
  closeItems(): void;
  /** Current flowchart root item. */
  graphRootKey: string | null;
  /** Increments for every flowchart request, including repeated requests for the same item. */
  graphRequestId: number;
  /** Exact recipe requested from an item-detail recipe card. */
  graphRecipeRef: RecipeRef | null;
  openRecipeInGraph(key: string, ref: RecipeRef): void;
  /** Mob sprite animation on/off (persisted). */
  animateMobs: boolean;
  toggleAnimateMobs(): void;
}

const UiContext = createContext<Ui | null>(null);

export function UiProvider({children}: {children: React.ReactNode}) {
  const [tab, setTab] = useState<Tab>('items');
  const [itemStack, setItemStack] = useState<string[]>([]);
  const [graphRootKey, setGraphRootKey] = useState<string | null>(null);
  const [graphRequestId, setGraphRequestId] = useState(0);
  const [graphRecipeRef, setGraphRecipeRef] = useState<RecipeRef | null>(null);
  const [animateMobs, setAnimateMobs] = useState<boolean>(loadAnimateMobs);

  const toggleAnimateMobs = useCallback(() => {
    setAnimateMobs(v => {
      try {
        globalThis.localStorage?.setItem('animateMobs', v ? '0' : '1');
      } catch {
        // no persistence available (native) — in-memory only
      }
      return !v;
    });
  }, []);

  const openItem = useCallback((key: string) => {
    setItemStack(s => (s[s.length - 1] === key ? s : [...s, key]));
  }, []);
  const popItem = useCallback(() => setItemStack(s => s.slice(0, -1)), []);
  const closeItems = useCallback(() => setItemStack([]), []);
  const openRecipeInGraph = useCallback((key: string, ref: RecipeRef) => {
    setItemStack([]);
    setGraphRootKey(key);
    setGraphRecipeRef(ref);
    setGraphRequestId(requestId => requestId + 1);
    setTab('graph');
  }, []);

  const value = useMemo<Ui>(
    () => ({
      tab,
      setTab,
      itemStack,
      openItem,
      popItem,
      closeItems,
      graphRootKey,
      graphRequestId,
      graphRecipeRef,
      openRecipeInGraph,
      animateMobs,
      toggleAnimateMobs,
    }),
    [
      tab,
      itemStack,
      graphRootKey,
      graphRequestId,
      graphRecipeRef,
      animateMobs,
      openItem,
      popItem,
      closeItems,
      openRecipeInGraph,
      toggleAnimateMobs,
    ],
  );
  return <UiContext.Provider value={value}>{children}</UiContext.Provider>;
}

export function useUi(): Ui {
  const ui = useContext(UiContext);
  if (!ui) throw new Error('UiProvider missing');
  return ui;
}
