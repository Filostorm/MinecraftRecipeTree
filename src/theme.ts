import {Platform} from 'react-native';

export const darkTheme = {
  bg: '#0e1116',
  panel: '#161b23',
  panelAlt: '#1d242f',
  border: '#2a3342',
  borderLight: '#3a4659',
  text: '#e6ebf2',
  textDim: '#8b97a8',
  accent: '#58c47b',
  accentAlt: '#5aa7fa',
  transfer: '#4ecdc4',
  radialRoot: '#c69cff',
  radialRootPanel: '#352448',
  warn: '#e0b341',
  danger: '#e06363',
  slot: '#c6c6c6',
} as const;

export type ThemeColor = keyof typeof darkTheme;

function themeColor(name: ThemeColor): string {
  return Platform.OS === 'web' ? `var(--mrt-${name})` : darkTheme[name];
}

export const theme = Object.fromEntries(
  (Object.keys(darkTheme) as ThemeColor[]).map(name => [name, themeColor(name)]),
) as Record<ThemeColor, string>;

export function resolvedThemeColor(name: ThemeColor): string {
  if (Platform.OS !== 'web' || typeof document === 'undefined') return darkTheme[name];
  const value = getComputedStyle(document.documentElement).getPropertyValue(`--mrt-${name}`).trim();
  if (value) return value;
  console.error(`Theme color --mrt-${name} was unavailable; using the dark theme value.`);
  return darkTheme[name];
}

export const fontMono = {fontFamily: 'monospace' as const};
