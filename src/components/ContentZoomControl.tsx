import React from 'react';
import {
  StyleProp,
  StyleSheet,
  Text,
  TextStyle,
  View,
  ViewStyle,
} from 'react-native';
import {
  CONTENT_ZOOM_STEP,
  MAXIMUM_CONTENT_ZOOM,
  MINIMUM_CONTENT_ZOOM,
} from '../ui/contentZoom';
import {theme} from '../theme';
import {InterfaceZoomSlider} from './InterfaceZoomSlider';

export function ContentZoomControl({
  value,
  onValueChange,
  onSlidingComplete,
  testID = 'content-zoom-slider',
  appearance = 'modal',
  style,
  valueStyle,
}: {
  value: number;
  onValueChange: (value: number) => void;
  onSlidingComplete?: (value: number) => void;
  testID?: string;
  appearance?: 'modal' | 'toolbar';
  style?: StyleProp<ViewStyle>;
  valueStyle?: StyleProp<TextStyle>;
}) {
  return (
    <View
      style={[
        styles.control,
        appearance === 'toolbar' ? styles.toolbarControl : styles.modalControl,
        style,
      ]}
      accessibilityLabel="Recipe and item size controls">
      <Text
        style={[
          styles.value,
          appearance === 'toolbar' && styles.toolbarValue,
          valueStyle,
        ]}
        accessibilityLabel={`Recipe and item size ${Math.round(value * 100)} percent`}>
        Recipe/items {Math.round(value * 100)}%
      </Text>
      <InterfaceZoomSlider
        accessibilityLabel="Recipe and item size"
        testID={testID}
        minimumValue={MINIMUM_CONTENT_ZOOM}
        maximumValue={MAXIMUM_CONTENT_ZOOM}
        step={CONTENT_ZOOM_STEP}
        value={value}
        onValueChange={onValueChange}
        onSlidingComplete={onSlidingComplete ?? onValueChange}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  control: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 8,
    backgroundColor: theme.panelAlt,
  },
  modalControl: {
    minHeight: 38,
    alignSelf: 'flex-start',
    gap: 10,
    borderColor: theme.border,
    paddingLeft: 10,
    paddingRight: 6,
    marginBottom: 10,
  },
  toolbarControl: {
    minHeight: 34,
    borderColor: theme.borderLight,
    paddingHorizontal: 9,
  },
  value: {
    minWidth: 112,
    color: theme.text,
    fontSize: 10,
    fontWeight: '700',
  },
  toolbarValue: {
    marginRight: 5,
    fontSize: 11,
    textAlign: 'center',
  },
});
