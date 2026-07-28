import React from 'react';
import Svg, {Circle, Line, Path} from 'react-native-svg';
import {theme} from '../theme';

export function VisibilityOffIcon({size = 16}: {size?: number}) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <Path
        d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"
        stroke={theme.textDim}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
      />
      <Circle cx={12} cy={12} r={2.7} stroke={theme.textDim} strokeWidth={1.8} />
      <Line
        x1={4}
        y1={4}
        x2={20}
        y2={20}
        stroke={theme.textDim}
        strokeLinecap="round"
        strokeWidth={2.2}
      />
    </Svg>
  );
}
