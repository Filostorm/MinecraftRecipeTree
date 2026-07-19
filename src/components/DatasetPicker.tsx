import React, {useEffect, useMemo, useState} from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import type {DatasetDescriptor} from '../data/datasetCatalog';
import {theme} from '../theme';

export function DatasetPicker({
  visible,
  datasets,
  selectedSlug,
  onSelect,
  onClose,
}: {
  visible: boolean;
  datasets: readonly DatasetDescriptor[];
  selectedSlug: string | null;
  onSelect(slug: string): void;
  onClose(): void;
}) {
  const [query, setQuery] = useState('');

  useEffect(() => {
    if (!visible) setQuery('');
  }, [visible]);

  const filteredDatasets = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    const matches = normalizedQuery.length === 0
      ? [...datasets]
      : datasets.filter(dataset =>
          [
            dataset.displayName,
            dataset.slug,
            dataset.minecraftVersion,
            dataset.packVersion,
          ].some(value => value.toLocaleLowerCase().includes(normalizedQuery)),
        );
    return matches.sort((left, right) => {
      if (left.slug === selectedSlug) return -1;
      if (right.slug === selectedSlug) return 1;
      return left.displayName.localeCompare(right.displayName, undefined, {sensitivity: 'base'});
    });
  }, [datasets, query, selectedSlug]);

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}
      accessibilityViewIsModal>
      <Pressable
        style={styles.backdrop}
        onPress={onClose}
        accessible={false}>
        <Pressable
          style={styles.card}
          onPress={() => {}}
          accessible={false}>
          <View style={styles.header}>
            <View style={styles.headerCopy}>
              <Text style={styles.title} accessibilityRole="header">
                Choose a recipe dataset
              </Text>
              <Text style={styles.subtitle}>
                Each pack is an immutable export with its own items, recipes, and layout previews.
              </Text>
            </View>
            <TouchableOpacity
              onPress={onClose}
              style={styles.closeButton}
              accessibilityRole="button"
              accessibilityLabel="Close modpack picker"
              focusable>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.searchRegion}>
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="Search name, Minecraft version, or pack version"
              placeholderTextColor={theme.textDim}
              style={styles.searchInput}
              autoCapitalize="none"
              autoCorrect={false}
              returnKeyType="search"
              accessibilityLabel="Search published modpacks"
              accessibilityHint="Filters by pack name, identifier, Minecraft version, or pack version"
              onSubmitEditing={() => {
                if (filteredDatasets.length === 1) onSelect(filteredDatasets[0].slug);
              }}
            />
            {query.length > 0 && (
              <TouchableOpacity
                style={styles.clearButton}
                onPress={() => setQuery('')}
                accessibilityRole="button"
                accessibilityLabel="Clear modpack search"
                focusable>
                <Text style={styles.clearButtonText}>Clear</Text>
              </TouchableOpacity>
            )}
            <Text style={styles.resultCount} accessibilityLiveRegion="polite">
              {filteredDatasets.length} of {datasets.length}{' '}
              {datasets.length === 1 ? 'pack' : 'packs'}
            </Text>
          </View>

          <ScrollView
            style={styles.list}
            contentContainerStyle={styles.listContent}
            keyboardShouldPersistTaps="handled"
            accessibilityRole="list"
            accessibilityLabel="Published modpacks">
            {filteredDatasets.length === 0 ? (
              <View style={styles.emptyState}>
                <Text style={styles.emptyTitle}>No matching modpacks</Text>
                <Text style={styles.emptyText}>
                  Try a pack name, Minecraft version, or pack release number.
                </Text>
              </View>
            ) : filteredDatasets.map(dataset => {
              const selected = dataset.slug === selectedSlug;
              return (
                <TouchableOpacity
                  key={dataset.slug}
                  style={[styles.option, selected && styles.optionSelected]}
                  onPress={() => onSelect(dataset.slug)}
                  accessibilityRole="button"
                  accessibilityState={{selected}}
                  accessibilityLabel={`${dataset.displayName}, Minecraft ${dataset.minecraftVersion}, pack version ${dataset.packVersion}${selected ? ', selected' : ''}`}
                  accessibilityHint="Loads this modpack's recipe dataset"
                  focusable>
                  <View style={styles.optionCopy}>
                    <Text style={[styles.optionName, selected && styles.optionNameSelected]}>
                      {dataset.displayName}
                    </Text>
                    <Text style={styles.optionMeta}>
                      Minecraft {dataset.minecraftVersion} · pack {dataset.packVersion}
                    </Text>
                  </View>
                  <Text style={[styles.selection, selected && styles.selectionActive]}>
                    {selected ? 'Active' : 'Open'}
                  </Text>
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
    backgroundColor: 'rgba(0,0,0,0.76)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 18,
  },
  card: {
    width: '100%',
    maxWidth: 620,
    maxHeight: '82%',
    minHeight: 240,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panel,
    overflow: 'hidden',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    padding: 18,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
  },
  headerCopy: {flex: 1},
  title: {color: theme.text, fontSize: 19, fontWeight: '800'},
  subtitle: {color: theme.textDim, fontSize: 12, lineHeight: 18, marginTop: 5},
  closeButton: {
    width: 44,
    height: 44,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
  },
  closeText: {color: theme.textDim, fontSize: 16},
  searchRegion: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
  },
  searchInput: {
    flexGrow: 1,
    flexShrink: 1,
    flexBasis: 260,
    minWidth: 0,
    minHeight: 44,
    color: theme.text,
    backgroundColor: theme.bg,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 9,
    paddingHorizontal: 12,
    paddingVertical: 9,
    outlineStyle: 'none',
  } as object,
  clearButton: {
    minHeight: 44,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 9,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
  },
  clearButtonText: {color: theme.accent, fontSize: 12, fontWeight: '700'},
  resultCount: {color: theme.textDim, fontSize: 10, paddingHorizontal: 2},
  list: {minHeight: 0},
  listContent: {padding: 12, gap: 10},
  option: {
    minHeight: 68,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  optionSelected: {
    borderColor: theme.accent,
    backgroundColor: 'rgba(74, 222, 128, 0.08)',
  },
  optionCopy: {flex: 1, minWidth: 0},
  optionName: {color: theme.text, fontSize: 15, fontWeight: '700'},
  optionNameSelected: {color: theme.accent},
  optionMeta: {color: theme.textDim, fontSize: 12, lineHeight: 17, marginTop: 3},
  selection: {color: theme.textDim, fontSize: 12, fontWeight: '700'},
  selectionActive: {color: theme.accent},
  emptyState: {alignItems: 'center', paddingHorizontal: 18, paddingVertical: 38},
  emptyTitle: {color: theme.text, fontSize: 15, fontWeight: '700'},
  emptyText: {color: theme.textDim, fontSize: 12, lineHeight: 18, marginTop: 5, textAlign: 'center'},
});
