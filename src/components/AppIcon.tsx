import React from 'react';
import {appIconPaths, type AppIconName} from './appIconPaths';
import {theme} from '../theme';
export function AppIcon({name, size = 22, color = theme.textDim}: {name: AppIconName; size?: number; color?: string}) {
  return React.createElement('svg', {width: size, height: size, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true},
    React.createElement('path', {d: appIconPaths[name], stroke: color, strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round'}));
}
