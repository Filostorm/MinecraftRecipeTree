import React from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {pixelArtImageStyle} from '../data/pixelArtSizing';
import type {SlotSummary} from '../data/slotSummary';
import {theme} from '../theme';
import {pixelated} from './ItemIcon';
import {ItemChip} from './RecipeCard';
import {RecipePreviewImage} from './RecipePreviewImage';

export interface PickerOption {
  label: string;
  sublabel?: string;
  imageUri?: string;
  imageW?: number;
  imageH?: number;
  inputs?: SlotSummary[];
  prerequisites?: SlotSummary[];
}

/** Generic chooser used to pick between recipes and drop sources for an item. */
export function PickerModal({
  visible,
  title,
  options,
  rememberSource,
  onRememberSourceChange,
  filterLabel,
  filterHint,
  filterValue,
  onFilterValueChange,
  onSelect,
  onClose,
}: {
  visible: boolean;
  title: string;
  options: PickerOption[];
  rememberSource?: boolean;
  onRememberSourceChange?: (remember: boolean) => void;
  filterLabel?: string;
  filterHint?: string;
  filterValue?: boolean;
  onFilterValueChange?: (show: boolean) => void;
  onSelect: (index: number) => void;
  onClose: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.card} onPress={() => {}}>
          <Text style={styles.title}>{title}</Text>
          {onRememberSourceChange && (
            <View style={styles.rememberRow}>
              <View style={styles.rememberCopy}>
                <Text style={styles.rememberTitle}>★ Use automatically in future trees</Text>
              </View>
              <Switch
                accessibilityLabel="Use automatically in future trees"
                value={rememberSource ?? true}
                onValueChange={onRememberSourceChange}
                trackColor={{false: theme.border, true: theme.accent}}
                thumbColor={theme.text}
              />
            </View>
          )}
          {filterLabel && onFilterValueChange && (
            <View style={styles.rememberRow}>
              <View style={styles.rememberCopy}>
                <Text style={styles.filterTitle}>{filterLabel}</Text>
                {filterHint ? <Text style={styles.rememberHint}>{filterHint}</Text> : null}
              </View>
              <Switch
                accessibilityLabel={filterLabel}
                value={filterValue ?? false}
                onValueChange={onFilterValueChange}
                trackColor={{false: theme.border, true: theme.accent}}
                thumbColor={theme.text}
              />
            </View>
          )}
          <ScrollView
            style={styles.optionsScroll}
            contentContainerStyle={styles.optionGrid}>
            {options.length === 0 ? (
              <Text style={styles.emptyText}>No standard sources are available.</Text>
            ) : null}
            {options.map((opt, i) => {
              const imageSize = opt.imageUri
                ? pixelArtImageStyle(opt.imageW ?? 160, opt.imageH ?? 60, 250, 128)
                : null;
              return (
                <TouchableOpacity key={i} style={styles.option} onPress={() => onSelect(i)}>
                  <Text style={styles.optionLabel}>{opt.label}</Text>
                  {opt.sublabel ? <Text style={styles.optionSub}>{opt.sublabel}</Text> : null}
                  {opt.imageUri && imageSize ? (
                    <RecipePreviewImage
                      uri={opt.imageUri}
                      context={opt.label}
                      style={[imageSize, styles.optionImage, pixelated as object]}
                      resizeMode="contain"
                    />
                  ) : null}
                  {opt.inputs && opt.inputs.length > 0 ? (
                    <View style={styles.ingredientGroup}>
                      <Text style={styles.ingredientLabel}>Inputs</Text>
                      <View style={styles.ingredientChips}>
                        {opt.inputs.map(input => (
                          <ItemChip
                            key={`input-${input.tag ?? input.key}`}
                            itemKey={input.key}
                            amount={input.amount}
                            variableAmount={input.variableAmount}
                            variants={input.variants}
                            tag={input.tag}
                            probability={input.probability}
                            probabilityRole="consume"
                            interactive={false}
                          />
                        ))}
                      </View>
                    </View>
                  ) : null}
                  {opt.prerequisites && opt.prerequisites.length > 0 ? (
                    <View style={styles.ingredientGroup}>
                      <Text style={styles.ingredientLabel}>Required · not consumed</Text>
                      <View style={styles.ingredientChips}>
                        {opt.prerequisites.map(input => (
                          <ItemChip
                            key={`prerequisite-${input.tag ?? input.key}`}
                            itemKey={input.key}
                            amount={input.amount}
                            variableAmount={input.variableAmount}
                            variants={input.variants}
                            tag={input.tag}
                            interactive={false}
                          />
                        ))}
                      </View>
                    </View>
                  ) : null}
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.65)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
  },
  card: {
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 12,
    width: '100%',
    maxWidth: 920,
    maxHeight: '92%' as never,
    padding: 14,
  },
  title: {color: theme.text, fontSize: 15, fontWeight: '700', marginBottom: 10},
  rememberRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
  },
  rememberCopy: {flex: 1},
  rememberTitle: {color: theme.accent, fontSize: 12, fontWeight: '700'},
  filterTitle: {color: theme.text, fontSize: 12, fontWeight: '700'},
  rememberHint: {color: theme.textDim, fontSize: 10, marginTop: 2, lineHeight: 14},
  optionsScroll: {maxHeight: 560, flexShrink: 1},
  optionGrid: {flexDirection: 'row', flexWrap: 'wrap', alignItems: 'stretch', gap: 8},
  emptyText: {
    color: theme.textDim,
    fontSize: 12,
    paddingVertical: 14,
    textAlign: 'center',
    width: '100%',
  },
  option: {
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 8,
    backgroundColor: theme.panelAlt,
    padding: 10,
    flexBasis: 280,
    flexGrow: 1,
    maxWidth: 420,
  },
  optionLabel: {color: theme.text, fontSize: 13, fontWeight: '600'},
  optionSub: {color: theme.textDim, fontSize: 11, marginTop: 2},
  optionImage: {marginTop: 8, borderRadius: 4},
  ingredientGroup: {marginTop: 9, gap: 5},
  ingredientLabel: {
    color: theme.textDim,
    fontSize: 10,
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  ingredientChips: {flexDirection: 'row', flexWrap: 'wrap', gap: 5},
});
