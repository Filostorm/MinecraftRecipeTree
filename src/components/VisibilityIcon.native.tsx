import React from 'react';
import Svg, {Circle, Line, Path} from 'react-native-svg';
import {theme} from '../theme';

export function VisibilityIcon({
  visible,
  size = 16,
}: {
  visible: boolean;
  size?: number;
}) {
  const color = visible ? theme.accentAlt : theme.textDim;
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <Path
        d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
      />
      <Circle cx={12} cy={12} r={2.7} stroke={color} strokeWidth={1.8} />
      {!visible ? (
        <Line
          x1={4}
          y1={4}
          x2={20}
          y2={20}
          stroke={color}
          strokeLinecap="round"
          strokeWidth={2.2}
        />
      ) : null}
    </Svg>
  );
}
