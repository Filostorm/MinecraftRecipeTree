import React from 'react';
import {SymbolView, type SymbolViewProps} from 'expo-symbols';
import {theme} from '../theme';

export const appSymbols = {
  items: {ios: 'square.grid.2x2', android: 'grid_view', web: 'grid_view'},
  graph: {ios: 'point.3.connected.trianglepath.dotted', android: 'account_tree', web: 'account_tree'},
  resources: {ios: 'checklist', android: 'checklist', web: 'checklist'},
  mobs: {ios: 'pawprint', android: 'pets', web: 'pets'},
  settings: {ios: 'gearshape', android: 'settings', web: 'settings'},
  graphSettings: {ios: 'slider.horizontal.3', android: 'tune', web: 'tune'},
  info: {ios: 'info.circle', android: 'info', web: 'info'},
  close: {ios: 'xmark', android: 'close', web: 'close'},
  check: {ios: 'checkmark', android: 'check', web: 'check'},
  minus: {ios: 'minus', android: 'remove', web: 'remove'},
} as const satisfies Record<string, SymbolViewProps['name']>;

export function AppIcon({name, size = 22, color = theme.textDim}: {
  name: keyof typeof appSymbols;
  size?: number;
  color?: string;
}) {
  return <SymbolView name={appSymbols[name]} size={size} tintColor={color} style={{width: size, height: size}} />;
}
