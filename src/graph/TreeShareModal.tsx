import React, {useEffect, useState} from 'react';
import {
  Modal,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {theme} from '../theme.ts';
import {PortableTreeDropZone} from './PortableTreeDropZone';

export function TreeShareModal({
  visible,
  mode = 'share',
  interfaceZoom = 1,
  onClose,
  onShare,
  onImport,
  onInspectImport,
  onChooseFile,
}: {
  visible: boolean;
  mode?: 'share' | 'import';
  interfaceZoom?: number;
  onClose: () => void;
  onShare: () => Promise<string>;
  onImport: (raw: string) => Promise<void>;
  onInspectImport: (raw: string) => {title: string; detail: string} | null;
  onChooseFile: () => Promise<string | null>;
}) {
  const [raw, setRaw] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [clipboardCandidate, setClipboardCandidate] = useState<{
    raw: string;
    title: string;
    detail: string;
  } | null>(null);
  useEffect(() => {
    if (!visible) {
      setRaw('');
      setMessage('');
      setBusy(false);
      setClipboardCandidate(null);
    }
  }, [visible]);

  useEffect(() => {
    if (!visible || mode !== 'import' || Platform.OS !== 'web') return;
    let canceled = false;
    const clipboard = globalThis.navigator?.clipboard;
    if (!clipboard?.readText) return;
    void clipboard.readText()
      .then(value => {
        if (canceled || !value.trim()) return;
        const preview = onInspectImport(value);
        if (preview) setClipboardCandidate({raw: value, ...preview});
      })
      .catch(error => {
        console.info('Clipboard recipe import suggestion was unavailable.', error);
      });
    return () => {
      canceled = true;
    };
  }, [mode, onInspectImport, visible]);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setMessage('');
    try {
      await action();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Tree transfer failed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View
          style={[
            styles.card,
            Platform.OS === 'web'
              ? ({
                  zoom: interfaceZoom,
                  width: `${100 / interfaceZoom}%`,
                  maxWidth: 520 / interfaceZoom,
                  maxHeight: `${88 / interfaceZoom}%`,
                } as unknown as object)
              : null,
          ]}>
          <View style={styles.header}>
            <View style={{flex: 1}}>
              <Text style={styles.title}>{mode === 'import' ? 'Import recipe tree' : 'Share or open tree history'}</Text>
              <Text style={styles.subtitle}>
                {mode === 'import'
                  ? 'Drop or paste a .mrtree.json recipe tree for this modpack version.'
                  : 'A .mrtree.json history opens against its matching modpack version.'}
              </Text>
            </View>
            <TouchableOpacity accessibilityRole="button" onPress={onClose} style={styles.close}>
              <Text style={styles.closeText}>×</Text>
            </TouchableOpacity>
          </View>

          {mode === 'share' && (
            <TouchableOpacity
              accessibilityRole="button"
              disabled={busy}
              style={styles.primary}
              onPress={() => void run(async () => setMessage(await onShare()))}>
              <Text style={styles.primaryText}>Share current tree history</Text>
            </TouchableOpacity>
          )}

          {clipboardCandidate && (
            <View style={styles.clipboardSuggestion}>
              <View style={styles.clipboardCopy}>
                <Text style={styles.clipboardLabel}>RECIPE TREE FOUND IN CLIPBOARD</Text>
                <Text style={styles.clipboardTitle} numberOfLines={1}>{clipboardCandidate.title}</Text>
                <Text style={styles.clipboardDetail} numberOfLines={1}>{clipboardCandidate.detail}</Text>
              </View>
              <TouchableOpacity
                accessibilityRole="button"
                disabled={busy}
                style={styles.clipboardButton}
                onPress={() => void run(async () => {
                  setRaw(clipboardCandidate.raw);
                  await onImport(clipboardCandidate.raw);
                })}>
                <Text style={styles.clipboardButtonText}>Import</Text>
              </TouchableOpacity>
            </View>
          )}

          <Text style={styles.label}>{mode === 'import' ? 'IMPORT RECIPE TREE' : 'OPEN SHARED TREE HISTORY'}</Text>
          <PortableTreeDropZone
            disabled={busy}
            onDropTree={async dropped => {
              setRaw(dropped);
              await run(() => onImport(dropped));
            }}
          />
          <TextInput
            accessibilityLabel="Shared recipe tree JSON"
            multiline
            value={raw}
            onChangeText={setRaw}
            placeholder={mode === 'import'
              ? 'Paste .mrtree.json recipe tree JSON here…'
              : 'Paste the shared .mrtree.json history here…'}
            placeholderTextColor={theme.textDim}
            autoCapitalize="none"
            autoCorrect={false}
            style={styles.input}
          />
          <View style={styles.actions}>
            <TouchableOpacity
              accessibilityRole="button"
              disabled={busy}
              style={styles.secondary}
              onPress={() =>
                void run(async () => {
                  const selected = await onChooseFile();
                  if (selected !== null) {
                    setRaw(selected);
                    await onImport(selected);
                  }
                })
              }>
              <Text style={styles.secondaryText}>Choose JSON file</Text>
            </TouchableOpacity>
            <TouchableOpacity
              accessibilityRole="button"
              disabled={busy || raw.trim().length === 0}
              style={[styles.primary, styles.importButton, (busy || !raw.trim()) && styles.disabled]}
              onPress={() => void run(() => onImport(raw.trim()))}>
              <Text style={styles.primaryText}>{busy ? 'Working…' : mode === 'import' ? 'Import recipe' : 'Open history'}</Text>
            </TouchableOpacity>
          </View>
          {!!message && <Text style={styles.message}>{message}</Text>}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
    backgroundColor: 'rgba(0,0,0,0.72)',
  },
  card: {
    width: '100%',
    maxWidth: 520,
    maxHeight: '88%',
    padding: 16,
    gap: 12,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panel,
  },
  header: {flexDirection: 'row', alignItems: 'flex-start', gap: 8},
  title: {color: theme.text, fontSize: 18, fontWeight: '800'},
  subtitle: {color: theme.textDim, fontSize: 11, lineHeight: 15, marginTop: 3},
  close: {width: 32, height: 32, alignItems: 'center', justifyContent: 'center'},
  closeText: {color: theme.textDim, fontSize: 24, lineHeight: 26},
  label: {color: theme.textDim, fontSize: 9, fontWeight: '800', letterSpacing: 0.8},
  clipboardSuggestion: {minHeight: 68, flexDirection: 'row', alignItems: 'center', gap: 12, padding: 10, borderRadius: 9, borderWidth: 1, borderColor: theme.accent, backgroundColor: theme.panelAlt},
  clipboardCopy: {flex: 1, minWidth: 0},
  clipboardLabel: {color: theme.accent, fontSize: 9, fontWeight: '900', letterSpacing: 0.6},
  clipboardTitle: {marginTop: 4, color: theme.text, fontSize: 13, fontWeight: '800'},
  clipboardDetail: {marginTop: 1, color: theme.textDim, fontSize: 10},
  clipboardButton: {minHeight: 34, minWidth: 72, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12, borderRadius: 7, backgroundColor: theme.accent},
  clipboardButtonText: {color: '#07120a', fontSize: 11, fontWeight: '900'},
  input: {
    minHeight: 150,
    maxHeight: 260,
    padding: 10,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.bg,
    color: theme.text,
    fontSize: 11,
    textAlignVertical: 'top',
  },
  actions: {flexDirection: 'row', justifyContent: 'flex-end', gap: 8},
  primary: {
    minHeight: 40,
    paddingHorizontal: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    backgroundColor: theme.accent,
  },
  primaryText: {color: '#07120a', fontSize: 12, fontWeight: '800'},
  instanceButton: {
    minHeight: 58,
    paddingHorizontal: 12,
    paddingVertical: 9,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panelAlt,
  },
  instanceTitle: {color: theme.text, fontSize: 12, fontWeight: '800'},
  instanceCopy: {color: theme.textDim, fontSize: 10, lineHeight: 14, marginTop: 2},
  instanceArrow: {color: theme.accent, fontSize: 24, lineHeight: 26},
  secondary: {
    minHeight: 40,
    paddingHorizontal: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.borderLight,
  },
  secondaryText: {color: theme.text, fontSize: 12, fontWeight: '700'},
  importButton: {minWidth: 116},
  disabled: {opacity: 0.45},
  message: {color: theme.accent, fontSize: 11, lineHeight: 15},
});
