import React from 'react';
import {ScrollView, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {GraphSheetSurface} from './GraphSheetSurface';
import {NativeUiScale} from '../ui/nativeUiScale';
import {AppIcon} from '../components/AppIcon';
import {signalTarget} from '../analytics/signal';
import {theme} from '../theme';

export interface GraphSettingOption {
  key: string;
  label: string;
  description?: string;
  /** Toggles show their state; actions run and close the sheet. */
  kind: 'toggle' | 'action';
  active?: boolean;
  destructive?: boolean;
  metricsId: string;
  onPress(): void;
}

/** Native presentation owns the slide animation, drag handle, and scroll-to-dismiss gesture. */
export function GraphSettingsSheet({
  visible,
  options,
  onClose,
}: {
  visible: boolean;
  options: readonly GraphSettingOption[];
  onClose: () => void;
}) {
  return (
    <GraphSheetSurface visible={visible} onClose={onClose}>
      <ScrollView nestedScrollEnabled style={styles.scroll} contentContainerStyle={styles.content}>
        <NativeUiScale>
        <View
          accessibilityViewIsModal
          accessibilityLabel="Graph settings"
          style={styles.panel}>
          <View style={styles.header}>
            <Text style={styles.title}>Graph settings</Text>
            <TouchableOpacity
              {...signalTarget('graph.settings.close')}
              accessibilityRole="button"
              accessibilityLabel="Close graph settings"
              style={styles.closeButton}
              onPress={onClose}>
              <AppIcon name="close" size={20} />
            </TouchableOpacity>
          </View>
          <View style={styles.options}>
            {options.map(option => (
              <TouchableOpacity
                key={option.key}
                {...signalTarget(option.metricsId)}
                accessibilityRole={option.kind === 'toggle' ? 'switch' : 'button'}
                accessibilityState={
                  option.kind === 'toggle' ? {checked: option.active === true} : undefined
                }
                style={[styles.row, option.active && styles.rowActive]}
                onPress={option.onPress}>
                <View style={styles.rowCopy}>
                  <Text
                    style={[
                      styles.rowLabel,
                      option.active && styles.rowLabelActive,
                      option.destructive && styles.rowLabelDestructive,
                    ]}>
                    {option.label}
                  </Text>
                </View>
                {option.kind === 'toggle' && (
                  <View style={[styles.pill, option.active && styles.pillActive]}>
                    <AppIcon name={option.active ? 'check' : 'minus'} size={16} color={option.active ? theme.accent : theme.textDim} />
                  </View>
                )}
              </TouchableOpacity>
            ))}
          </View>
        </View>
        </NativeUiScale>
      </ScrollView>
    </GraphSheetSurface>
  );
}

const styles = StyleSheet.create({
  panel: {
    paddingHorizontal: 14,
    paddingTop: 12,
    paddingBottom: 22,
  },
  header: {flexDirection: 'row', alignItems: 'center', gap: 12},
  title: {flex: 1, color: theme.text, fontSize: 16, fontWeight: '700'},
  closeButton: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scroll: {flex: 1},
  content: {paddingBottom: 16},
  options: {marginTop: 6, gap: 8},
  row: {
    minHeight: 56,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
  },
  rowActive: {borderColor: theme.accent, backgroundColor: '#173724'},
  rowCopy: {flex: 1, minWidth: 0},
  rowLabel: {color: theme.text, fontSize: 14, fontWeight: '700'},
  rowLabelActive: {color: theme.accent},
  rowLabelDestructive: {color: theme.warn},
  pill: {
    minWidth: 44,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: theme.border,
    alignItems: 'center',
  },
  pillActive: {borderColor: theme.accent},
});
