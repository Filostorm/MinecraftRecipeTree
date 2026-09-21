import React from 'react';
import Svg, {Path} from 'react-native-svg';
import {appIconPaths, type AppIconName} from './appIconPaths';
import {theme} from '../theme';
export function AppIcon({name, size = 22, color = theme.textDim}: {name: AppIconName; size?: number; color?: string}) {
  return <Svg width={size} height={size} viewBox="0 0 24 24" fill="none"><Path d={appIconPaths[name]} stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" /></Svg>;
}
