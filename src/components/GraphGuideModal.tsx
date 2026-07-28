import React, {useState} from 'react';
import {
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
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
type FeedbackKind = 'bug' | 'feature';

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
  packSlug,
  packName,
}: {
  visible: boolean;
  onClose: () => void;
  packSlug: string;
  packName: string;
}) {
  const [feedbackKind, setFeedbackKind] = useState<FeedbackKind | null>(null);
  const [message, setMessage] = useState('');
  const [contact, setContact] = useState('');
  const [website, setWebsite] = useState('');
  const [submissionState, setSubmissionState] = useState<
    'idle' | 'submitting' | 'submitted' | 'error'
  >('idle');
  const [submissionError, setSubmissionError] = useState('');

  const submitFeedback = async () => {
    if (!feedbackKind || message.trim().length < 10 || submissionState === 'submitting') return;
    setSubmissionState('submitting');
    setSubmissionError('');
    const page =
      Platform.OS === 'web' && typeof window !== 'undefined'
        ? `${window.location.pathname}${window.location.search}`
        : '';
    try {
      const response = await fetch('/api/feedback', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          kind: feedbackKind,
          message,
          contact,
          packSlug,
          packName,
          page,
          website,
        }),
      });
      const result = (await response.json()) as {error?: string; submitted?: boolean};
      if (!response.ok || !result.submitted) {
        throw new Error(result.error || `Feedback request failed with status ${response.status}.`);
      }
      setSubmissionState('submitted');
      setMessage('');
      setContact('');
    } catch (error) {
      console.error('Feedback submission failed.', error);
      setSubmissionError(error instanceof Error ? error.message : 'Feedback could not be sent.');
      setSubmissionState('error');
    }
  };

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

            <Text style={[styles.sectionTitle, styles.feedbackTitle]}>Feedback</Text>
            <Text style={styles.feedbackIntro}>
              Report a problem or suggest an improvement. Submissions include the current modpack,
              page, and browser details.
            </Text>
            <View style={styles.feedbackChoiceRow}>
              <TouchableOpacity
                style={[
                  styles.feedbackChoice,
                  feedbackKind === 'bug' && styles.feedbackChoiceActive,
                ]}
                onPress={() => {
                  setFeedbackKind('bug');
                  setSubmissionState('idle');
                }}
                accessibilityRole="button"
                accessibilityState={{selected: feedbackKind === 'bug'}}>
                <Text
                  style={[
                    styles.feedbackChoiceText,
                    feedbackKind === 'bug' && styles.feedbackChoiceTextActive,
                  ]}>
                  Report a bug
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.feedbackChoice,
                  feedbackKind === 'feature' && styles.feedbackChoiceActive,
                ]}
                onPress={() => {
                  setFeedbackKind('feature');
                  setSubmissionState('idle');
                }}
                accessibilityRole="button"
                accessibilityState={{selected: feedbackKind === 'feature'}}>
                <Text
                  style={[
                    styles.feedbackChoiceText,
                    feedbackKind === 'feature' && styles.feedbackChoiceTextActive,
                  ]}>
                  Request a feature
                </Text>
              </TouchableOpacity>
            </View>
            {feedbackKind && submissionState !== 'submitted' ? (
              <View style={styles.feedbackForm}>
                <Text style={styles.fieldLabel}>
                  {feedbackKind === 'bug' ? 'What went wrong?' : 'What would you like to improve?'}
                </Text>
                <TextInput
                  value={message}
                  onChangeText={value => {
                    setMessage(value.slice(0, 2000));
                    if (submissionState === 'error') setSubmissionState('idle');
                  }}
                  style={[styles.feedbackInput, styles.feedbackMessage]}
                  multiline
                  maxLength={2000}
                  placeholder={
                    feedbackKind === 'bug'
                      ? 'Describe what happened and what you expected.'
                      : 'Describe the feature and how it would help.'
                  }
                  placeholderTextColor={theme.textDim}
                  accessibilityLabel="Feedback details"
                />
                <Text style={styles.fieldLabel}>Contact email · optional</Text>
                <TextInput
                  value={contact}
                  onChangeText={value => {
                    setContact(value.slice(0, 254));
                    if (submissionState === 'error') setSubmissionState('idle');
                  }}
                  style={styles.feedbackInput}
                  maxLength={254}
                  inputMode="email"
                  autoCapitalize="none"
                  autoCorrect={false}
                  placeholder="you@example.com"
                  placeholderTextColor={theme.textDim}
                  accessibilityLabel="Contact email, optional"
                />
                <TextInput
                  value={website}
                  onChangeText={setWebsite}
                  style={styles.honeypot}
                  tabIndex={-1}
                  accessibilityElementsHidden
                  importantForAccessibility="no-hide-descendants"
                />
                {submissionState === 'error' ? (
                  <Text style={styles.feedbackError} accessibilityRole="alert">
                    {submissionError}
                  </Text>
                ) : null}
                <TouchableOpacity
                  style={[
                    styles.submitButton,
                    (message.trim().length < 10 || submissionState === 'submitting') &&
                      styles.submitButtonDisabled,
                  ]}
                  disabled={message.trim().length < 10 || submissionState === 'submitting'}
                  onPress={() => void submitFeedback()}
                  accessibilityRole="button"
                  accessibilityLabel={
                    feedbackKind === 'bug' ? 'Submit bug report' : 'Submit feature request'
                  }>
                  <Text style={styles.submitButtonText}>
                    {submissionState === 'submitting'
                      ? 'Sending…'
                      : feedbackKind === 'bug'
                        ? 'Submit bug report'
                        : 'Submit feature request'}
                  </Text>
                </TouchableOpacity>
              </View>
            ) : null}
            {submissionState === 'submitted' ? (
              <View style={styles.feedbackSuccess} accessibilityRole="alert">
                <Text style={styles.feedbackSuccessTitle}>Thanks — feedback submitted.</Text>
                <Text style={styles.description}>Your report was saved for review.</Text>
              </View>
            ) : null}
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
  feedbackTitle: {marginTop: 18},
  feedbackIntro: {color: theme.textDim, fontSize: 11, lineHeight: 16, marginTop: 6},
  feedbackChoiceRow: {flexDirection: 'row', gap: 8, marginTop: 10},
  feedbackChoice: {
    flex: 1,
    minHeight: 42,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panelAlt,
  },
  feedbackChoiceActive: {
    borderColor: theme.accent,
    backgroundColor: 'rgba(74, 222, 128, 0.08)',
  },
  feedbackChoiceText: {color: theme.textDim, fontSize: 12, fontWeight: '700'},
  feedbackChoiceTextActive: {color: theme.accent},
  feedbackForm: {marginTop: 12},
  fieldLabel: {color: theme.text, fontSize: 11, fontWeight: '700', marginBottom: 5},
  feedbackInput: {
    minHeight: 42,
    marginBottom: 11,
    paddingHorizontal: 10,
    paddingVertical: 9,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.bg,
    color: theme.text,
    fontSize: 16,
    outlineStyle: 'none',
  } as object,
  feedbackMessage: {minHeight: 96, textAlignVertical: 'top'},
  honeypot: {position: 'absolute', width: 1, height: 1, opacity: 0},
  feedbackError: {color: theme.danger, fontSize: 11, lineHeight: 16, marginBottom: 9},
  submitButton: {
    minHeight: 42,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    backgroundColor: theme.accent,
    paddingHorizontal: 14,
  },
  submitButtonDisabled: {opacity: 0.45},
  submitButtonText: {color: theme.bg, fontSize: 12, fontWeight: '800'},
  feedbackSuccess: {
    marginTop: 12,
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.accent,
    backgroundColor: 'rgba(74, 222, 128, 0.08)',
  },
  feedbackSuccessTitle: {color: theme.accent, fontSize: 12, fontWeight: '800'},
});
