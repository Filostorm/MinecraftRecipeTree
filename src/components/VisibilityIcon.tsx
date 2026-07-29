import React from 'react';
import {theme} from '../theme';

export function VisibilityIcon({
  visible,
  size = 16,
}: {
  visible: boolean;
  size?: number;
}) {
  const color = visible ? theme.accentAlt : theme.textDim;
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
      stroke: color,
      strokeLinecap: 'round',
      strokeLinejoin: 'round',
      strokeWidth: 1.8,
    }),
    React.createElement('circle', {
      cx: 12,
      cy: 12,
      r: 2.7,
      stroke: color,
      strokeWidth: 1.8,
    }),
    !visible
      ? React.createElement('line', {
          x1: 4,
          x2: 20,
          y1: 4,
          y2: 20,
          stroke: color,
          strokeLinecap: 'round',
          strokeWidth: 2.2,
        })
      : null,
  );
}
