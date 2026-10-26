import React from 'react';
import {View, type StyleProp, type ViewStyle} from 'react-native';

export interface ImportPackLinkProps {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
  metricsId?: string;
  /** Native uses the in-app import flow; web navigation belongs to the anchor. */
  onPress(): void;
}

/** A real link also works with keyboard/accessibility activation and embedded browsers. */
export function ImportPackLink({
  children,
  style,
  accessibilityLabel = 'Import a local modpack exporter ZIP',
  metricsId = 'header.import-pack',
}: ImportPackLinkProps) {
  return (
    <a
      href="/publish#upload"
      aria-label={accessibilityLabel}
      data-metrics-id={metricsId}
      style={{display: 'block', color: 'inherit', textDecoration: 'none'}}>
      <View style={style}>{children}</View>
    </a>
  );
}
