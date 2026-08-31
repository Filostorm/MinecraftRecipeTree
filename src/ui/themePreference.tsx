import React, {createContext, useCallback, useContext, useEffect, useMemo, useState} from 'react';
import {Platform} from 'react-native';

export type ThemePreference = 'dark' | 'light';

const THEME_STORAGE_KEY = 'minecraft-recipe-tree-theme';
const FONT_STORAGE_KEY = 'minecraft-recipe-tree-font';
const THEME_OPTIONS: ThemePreference[] = ['dark', 'light'];

interface ThemePreferenceContextValue {
  preference: ThemePreference;
  setPreference(value: ThemePreference): void;
  minecraftFont: boolean;
  setMinecraftFont(value: boolean): void;
}

const ThemePreferenceContext = createContext<ThemePreferenceContextValue | null>(null);

export function isThemePreference(value: unknown): value is ThemePreference {
  return typeof value === 'string' && THEME_OPTIONS.includes(value as ThemePreference);
}

function loadThemePreference(): {preference: ThemePreference; minecraftFont: boolean} {
  if (Platform.OS !== 'web' || typeof window === 'undefined') {
    return {preference: 'dark', minecraftFont: false};
  }
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    const storedFont = window.localStorage.getItem(FONT_STORAGE_KEY);
    if (stored === 'minecraft') {
      return {preference: 'dark', minecraftFont: true};
    }
    if (stored !== null && !isThemePreference(stored)) {
      console.error('Stored theme preference was invalid; using the dark theme.', {stored});
    }
    if (storedFont !== null && storedFont !== 'minecraft' && storedFont !== 'default') {
      console.error('Stored font preference was invalid; using the default font.', {storedFont});
    }
    return {
      preference: isThemePreference(stored) ? stored : 'dark',
      minecraftFont: storedFont === 'minecraft',
    };
  } catch (cause) {
    console.error('Theme preference could not be read; using the dark theme.', cause);
  }
  return {preference: 'dark', minecraftFont: false};
}

function applyThemePreference(preference: ThemePreference, minecraftFont: boolean): void {
  if (Platform.OS !== 'web' || typeof document === 'undefined') return;
  document.documentElement.dataset.mrtTheme = preference;
  if (minecraftFont) document.documentElement.dataset.mrtFont = 'minecraft';
  else delete document.documentElement.dataset.mrtFont;
}

export function ThemePreferenceProvider({children}: {children: React.ReactNode}) {
  const [initial] = useState(loadThemePreference);
  const [preference, setPreferenceState] = useState<ThemePreference>(initial.preference);
  const [minecraftFont, setMinecraftFontState] = useState(initial.minecraftFont);

  useEffect(() => {
    applyThemePreference(preference, minecraftFont);
  }, [minecraftFont, preference]);

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next);
    applyThemePreference(next, minecraftFont);
    if (Platform.OS !== 'web' || typeof window === 'undefined') return;
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch (cause) {
      console.error('Theme preference could not be saved.', cause);
    }
  }, [minecraftFont]);

  const setMinecraftFont = useCallback((next: boolean) => {
    setMinecraftFontState(next);
    applyThemePreference(preference, next);
    if (Platform.OS !== 'web' || typeof window === 'undefined') return;
    try {
      window.localStorage.setItem(FONT_STORAGE_KEY, next ? 'minecraft' : 'default');
      if (window.localStorage.getItem(THEME_STORAGE_KEY) === 'minecraft') {
        window.localStorage.setItem(THEME_STORAGE_KEY, preference);
      }
    } catch (cause) {
      console.error('Font preference could not be saved.', cause);
    }
  }, [preference]);

  const value = useMemo(
    () => ({preference, setPreference, minecraftFont, setMinecraftFont}),
    [minecraftFont, preference, setMinecraftFont, setPreference],
  );
  return (
    <ThemePreferenceContext.Provider value={value}>
      {children}
    </ThemePreferenceContext.Provider>
  );
}

export function useThemePreference(): ThemePreferenceContextValue {
  const value = useContext(ThemePreferenceContext);
  if (!value) throw new Error('useThemePreference must be used inside ThemePreferenceProvider.');
  return value;
}
