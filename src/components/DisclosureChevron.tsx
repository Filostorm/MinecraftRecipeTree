import React from 'react';
import {theme} from '../theme';

export function DisclosureChevron({
  expanded,
  color = theme.textDim,
  size = 16,
  strokeWidth = 2,
}: {
  expanded: boolean;
  color?: string;
  size?: number;
  strokeWidth?: number;
}) {
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
      d: 'M6.5 9.5 12 15l5.5-5.5',
      stroke: color,
      strokeLinecap: 'round',
      strokeLinejoin: 'round',
      strokeWidth,
      transform: expanded ? 'rotate(180 12 12)' : undefined,
    }),
  );
}
