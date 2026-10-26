import React from 'react';
import {TouchableOpacity} from 'react-native';
import {signalTarget} from '../analytics/signal';
import type {ImportPackLinkProps} from './ImportPackLink';

export function ImportPackLink({
  children,
  style,
  onPress,
  accessibilityLabel = 'Import a local modpack exporter ZIP',
  metricsId = 'header.import-pack',
}: ImportPackLinkProps) {
  return (
    <TouchableOpacity
      {...signalTarget(metricsId)}
      style={style}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}>
      {children}
    </TouchableOpacity>
  );
}
