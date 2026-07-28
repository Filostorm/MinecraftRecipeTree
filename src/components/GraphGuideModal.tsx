import React from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {theme} from '../theme';

const controls = [
  {
    title: 'Tap an item node',
    description: 'Choose a recipe, drop source, or usage for that item.',
  },
  {
    title: 'Tap an expanded recipe',
    description: 'Collapse that branch back into a compact item node.',
  },
  {
    title: 'Swap recipe  ⇄',
    description: 'Choose a different source for an expanded item.',
  },
] as const;

type KeyVariant = 'root' | 'terminal' | 'recursive' | 'complete' | 'partial';

const visualKey: ReadonlyArray<{
  variant: KeyVariant;
  title: string;
  description: string;
}> = [
  {
    variant: 'root',
    title: 'Purple diamond',
    description: 'The starting item.',
  },
  {
    variant: 'terminal',
    title: 'Silver outline',
    description: 'No further recipe is available in the current tree direction.',
  },
  {
    variant: 'recursive',
    title: 'Amber outline',
    description: 'A recursive input.',
  },
  {
    variant: 'complete',
    title: 'Blue outline',
    description: 'This input is fully supplied by a byproduct.',
  },
  {
    variant: 'partial',
    title: 'Dashed blue outline',
    description: 'A byproduct supplies part of this input; the remainder still needs crafting.',
  },
];

export function GraphGuideModal({
  visible,
  onClose,
}: {
  visible: boolean;
  onClose: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.card} onPress={() => {}}>
          <View style={styles.header}>
            <View style={styles.headerCopy}>
              <Text style={styles.title}>Graph guide</Text>
              <Text style={styles.subtitle}>How to navigate the tree and read node outlines</Text>
            </View>
            <TouchableOpacity
              accessibilityRole="button"
              accessibilityLabel="Close graph guide"
              onPress={onClose}
              style={styles.closeButton}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
            <Text style={styles.sectionTitle}>Controls</Text>
            <View style={styles.controlList}>
              {controls.map(control => (
                <View key={control.title} style={styles.controlRow}>
                  <Text style={styles.controlTitle}>{control.title}</Text>
                  <Text style={styles.description}>{control.description}</Text>
                </View>
              ))}
            </View>

            <Text style={[styles.sectionTitle, styles.keyTitle]}>Visual key</Text>
            <View style={styles.keyList}>
              {visualKey.map(entry => (
                <View key={entry.variant} style={styles.keyRow}>
                  <View style={styles.swatchFrame}>
                    <View
                      style={[
                        styles.swatch,
                        entry.variant === 'root' && styles.swatchRoot,
                        entry.variant === 'terminal' && styles.swatchTerminal,
                        entry.variant === 'recursive' && styles.swatchRecursive,
                        entry.variant === 'complete' && styles.swatchComplete,
                        entry.variant === 'partial' && styles.swatchPartial,
                      ]}
                    />
                  </View>
                  <View style={styles.keyCopy}>
                    <Text style={styles.controlTitle}>{entry.title}</Text>
                    <Text style={styles.description}>{entry.description}</Text>
                  </View>
                </View>
              ))}
            </View>
          </ScrollView>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.68)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
  },
  card: {
    width: '100%',
    maxWidth: 620,
    maxHeight: '86%' as never,
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 12,
    padding: 14,
  },
  header: {flexDirection: 'row', alignItems: 'flex-start', gap: 12},
  headerCopy: {flex: 1},
  title: {color: theme.text, fontSize: 17, fontWeight: '700'},
  subtitle: {color: theme.textDim, fontSize: 11, marginTop: 3},
  closeButton: {padding: 6},
  closeText: {color: theme.textDim, fontSize: 15},
  scroll: {marginTop: 14},
  content: {paddingBottom: 2},
  sectionTitle: {
    color: theme.textDim,
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  controlList: {marginTop: 7},
  controlRow: {
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
  },
  controlTitle: {color: theme.text, fontSize: 12, fontWeight: '700'},
  description: {color: theme.textDim, fontSize: 11, lineHeight: 16, marginTop: 2},
  keyTitle: {marginTop: 18},
  keyList: {gap: 7, marginTop: 8},
  keyRow: {
    minHeight: 54,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 9,
    paddingVertical: 7,
    borderRadius: 8,
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: 1,
  },
  swatchFrame: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
  },
  swatch: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: theme.borderLight,
    backgroundColor: theme.panel,
  },
  swatchRoot: {
    width: 27,
    height: 27,
    borderRadius: 8,
    borderWidth: 3,
    borderColor: theme.radialRoot,
    backgroundColor: theme.radialRootPanel,
    transform: [{rotate: '45deg'}],
  },
  swatchTerminal: {borderColor: theme.textDim},
  swatchRecursive: {borderColor: theme.warn},
  swatchComplete: {borderColor: theme.accentAlt},
  swatchPartial: {borderColor: theme.accentAlt, borderStyle: 'dashed'},
  keyCopy: {flex: 1, minWidth: 0},
});
