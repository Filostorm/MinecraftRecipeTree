import React from 'react';
import Svg, {Path} from 'react-native-svg';
import {theme} from '../theme';

export function BugIcon({active = false, size = 18}: {active?: boolean; size?: number}) {
  const color = active ? theme.accent : theme.text;
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <Path
        d="M9 9h6v8a3 3 0 0 1-6 0V9Z"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
      />
      <Path
        d="M10 9V7a2 2 0 0 1 4 0v2"
        stroke={color}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
      />
      <Path d="M12 10v9" stroke={color} strokeLinecap="round" strokeWidth={1.8} />
      <Path
        d="M7 12H4m3 4H4m13-4h3m-3 4h3"
        stroke={color}
        strokeLinecap="round"
        strokeWidth={1.8}
      />
      <Path
        d="M8.5 9 6 6.5M15.5 9 18 6.5"
        stroke={color}
        strokeLinecap="round"
        strokeWidth={1.8}
      />
    </Svg>
  );
}
