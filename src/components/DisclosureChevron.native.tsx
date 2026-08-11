import React from 'react';
import Svg, {Path} from 'react-native-svg';
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
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <Path
        d={expanded ? 'M6.5 14.5 12 9l5.5 5.5' : 'M6.5 9.5 12 15l5.5-5.5'}
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={strokeWidth}
      />
    </Svg>
  );
}
