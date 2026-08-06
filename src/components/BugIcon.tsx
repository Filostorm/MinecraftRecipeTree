import React from 'react';
import {theme} from '../theme';

export function BugIcon({active = false, size = 18}: {active?: boolean; size?: number}) {
  const color = active ? theme.accent : theme.text;
  const stroke = {
    fill: 'none',
    stroke: color,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    strokeWidth: 1.8,
  } as const;

  return React.createElement(
    'svg',
    {
      'aria-hidden': true,
      fill: 'none',
      focusable: false,
      height: size,
      viewBox: '0 0 24 24',
      width: size,
    },
    React.createElement('path', {...stroke, d: 'M9 9h6v8a3 3 0 0 1-6 0V9Z'}),
    React.createElement('path', {...stroke, d: 'M10 9V7a2 2 0 0 1 4 0v2'}),
    React.createElement('path', {...stroke, d: 'M12 10v9'}),
    React.createElement('path', {...stroke, d: 'M7 12H4m3 4H4m13-4h3m-3 4h3'}),
    React.createElement('path', {...stroke, d: 'M8.5 9 6 6.5M15.5 9 18 6.5'}),
  );
}
