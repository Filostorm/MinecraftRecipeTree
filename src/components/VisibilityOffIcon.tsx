import React from 'react';
import {theme} from '../theme';

export function VisibilityOffIcon({size = 16}: {size?: number}) {
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
    React.createElement('path', {
      d: 'M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z',
      stroke: theme.textDim,
      strokeLinecap: 'round',
      strokeLinejoin: 'round',
      strokeWidth: 1.8,
    }),
    React.createElement('circle', {
      cx: 12,
      cy: 12,
      r: 2.7,
      stroke: theme.textDim,
      strokeWidth: 1.8,
    }),
    React.createElement('line', {
      x1: 4,
      x2: 20,
      y1: 4,
      y2: 20,
      stroke: theme.textDim,
      strokeLinecap: 'round',
      strokeWidth: 2.2,
    }),
  );
}
