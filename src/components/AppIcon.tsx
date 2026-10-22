import React from 'react';
import {materialIconPaths} from './materialIconPaths';
import {theme} from '../theme';

export function AppIcon({name, size = 22, color = theme.textDim}: {
  name: keyof typeof materialIconPaths;
  size?: number;
  color?: string;
}) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill={color} aria-hidden="true">
    {materialIconPaths[name].map((d, index) => <path key={index} d={d} />)}
    {name === 'mobs' && [[4.5, 9.5], [9, 5.5], [15, 5.5], [19.5, 9.5]].map(([cx, cy]) => <circle key={cx} cx={cx} cy={cy} r={2.5} />)}
  </svg>;
}
