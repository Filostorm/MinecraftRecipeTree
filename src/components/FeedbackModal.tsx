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

export type FeedbackKind = 'bug' | 'feature';

const MINIMUM_TITLE_LENGTH = 3;
const MINIMUM_MESSAGE_LENGTH = 10;

export function FeedbackModal({
  kind,
  onClose,
  packSlug,
  packName,
}: {
  kind: FeedbackKind;
  onClose: () => void;
  packSlug: string;
  packName: string;
}) {
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [contact, setContact] = useState('');
  const [website, setWebsite] = useState('');
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const [submissionState, setSubmissionState] = useState<
    'idle' | 'submitting' | 'submitted' | 'error'
  >('idle');
  const [submissionError, setSubmissionError] = useState('');

  const normalizedTitle = title.trim();
  const normalizedMessage = message.trim();
  const titleIsValid = normalizedTitle.length >= MINIMUM_TITLE_LENGTH;
  const messageIsValid = normalizedMessage.length >= MINIMUM_MESSAGE_LENGTH;

  const submitFeedback = async () => {
    if (submissionState === 'submitting') return;
    setAttemptedSubmit(true);
    if (!titleIsValid || !messageIsValid) return;

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
          kind,
          title: normalizedTitle,
          message: normalizedMessage,
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
    } catch (error) {
      console.error('Feedback submission failed.', error);
      setSubmissionError(error instanceof Error ? error.message : 'Feedback could not be sent.');
      setSubmissionState('error');
    }
  };

  const formTitle = kind === 'bug' ? 'Report a bug' : 'Request a feature';

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.card} onPress={() => {}}>
          <View style={styles.header}>
            <View style={styles.headerCopy}>
              <Text style={styles.title}>{formTitle}</Text>
              <Text style={styles.subtitle}>
                Include a short title and enough detail to reproduce or evaluate the request.
              </Text>
            </View>
            <TouchableOpacity
              accessibilityRole="button"
              accessibilityLabel={`Close ${formTitle.toLowerCase()} form`}
              onPress={onClose}
              style={styles.closeButton}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          {submissionState === 'submitted' ? (
            <View style={styles.success} accessibilityRole="alert">
              <Text style={styles.successTitle}>Thanks — feedback submitted.</Text>
              <Text style={styles.helpText}>Your report was saved for review.</Text>
              <TouchableOpacity
                style={styles.submitButton}
                onPress={onClose}
                accessibilityRole="button">
                <Text style={styles.submitButtonText}>Done</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <ScrollView
              style={styles.scroll}
              contentContainerStyle={styles.form}
              keyboardShouldPersistTaps="handled">
              <Text style={styles.fieldLabel}>Title</Text>
              <TextInput
                value={title}
                onChangeText={value => {
                  setTitle(value.slice(0, 120));
                  if (submissionState === 'error') setSubmissionState('idle');
                }}
                style={[styles.input, attemptedSubmit && !titleIsValid && styles.inputInvalid]}
                maxLength={120}
                placeholder={
                  kind === 'bug' ? 'Summarize the problem' : 'Name the requested improvement'
                }
                placeholderTextColor={theme.textDim}
                accessibilityLabel="Feedback title"
              />
              {attemptedSubmit && !titleIsValid ? (
                <Text style={styles.validationError} accessibilityRole="alert">
                  Enter a title with at least {MINIMUM_TITLE_LENGTH} characters.
                </Text>
              ) : null}

              <Text style={styles.fieldLabel}>
                {kind === 'bug' ? 'What went wrong?' : 'What would you like to improve?'}
              </Text>
              <TextInput
                value={message}
                onChangeText={value => {
                  setMessage(value.slice(0, 2000));
                  if (submissionState === 'error') setSubmissionState('idle');
                }}
                style={[
                  styles.input,
                  styles.messageInput,
                  attemptedSubmit && !messageIsValid && styles.inputInvalid,
                ]}
                multiline
                maxLength={2000}
                placeholder={
                  kind === 'bug'
                    ? 'Describe what happened and what you expected.'
                    : 'Describe the feature and how it would help.'
                }
                placeholderTextColor={theme.textDim}
                accessibilityLabel="Feedback details"
              />
              {attemptedSubmit && !messageIsValid ? (
                <Text style={styles.validationError} accessibilityRole="alert">
                  Add at least {MINIMUM_MESSAGE_LENGTH} characters of detail.
                </Text>
              ) : (
                <Text style={styles.helpText}>At least {MINIMUM_MESSAGE_LENGTH} characters.</Text>
              )}

              <Text style={styles.fieldLabel}>Contact email · optional</Text>
              <TextInput
                value={contact}
                onChangeText={value => {
                  setContact(value.slice(0, 254));
                  if (submissionState === 'error') setSubmissionState('idle');
                }}
                style={styles.input}
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
              <Text style={styles.context}>
                The current modpack, page, and browser details are included automatically.
              </Text>
              {submissionState === 'error' ? (
                <Text style={styles.submissionError} accessibilityRole="alert">
                  {submissionError}
                </Text>
              ) : null}
              <TouchableOpacity
                style={[
                  styles.submitButton,
                  submissionState === 'submitting' && styles.submitButtonDisabled,
                ]}
                disabled={submissionState === 'submitting'}
                onPress={() => void submitFeedback()}
                accessibilityRole="button"
                accessibilityLabel={
                  kind === 'bug' ? 'Submit bug report' : 'Submit feature request'
                }>
                <Text style={styles.submitButtonText}>
                  {submissionState === 'submitting'
                    ? 'Sending…'
                    : kind === 'bug'
                      ? 'Submit bug report'
                      : 'Submit feature request'}
                </Text>
              </TouchableOpacity>
            </ScrollView>
          )}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.72)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
  },
  card: {
    width: '100%',
    maxWidth: 560,
    maxHeight: '88%' as never,
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
  },
  header: {flexDirection: 'row', alignItems: 'flex-start', gap: 12},
  headerCopy: {flex: 1},
  title: {color: theme.text, fontSize: 19, fontWeight: '800'},
  subtitle: {color: theme.textDim, fontSize: 11, lineHeight: 16, marginTop: 4},
  closeButton: {padding: 6},
  closeText: {color: theme.textDim, fontSize: 15},
  scroll: {marginTop: 16},
  form: {paddingBottom: 2},
  fieldLabel: {color: theme.text, fontSize: 12, fontWeight: '700', marginBottom: 6},
  input: {
    minHeight: 44,
    marginBottom: 14,
    paddingHorizontal: 11,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.bg,
    color: theme.text,
    fontSize: 16,
    outlineStyle: 'none',
  } as object,
  inputInvalid: {borderColor: theme.danger},
  messageInput: {minHeight: 120, textAlignVertical: 'top'},
  validationError: {
    color: theme.danger,
    fontSize: 11,
    lineHeight: 16,
    marginTop: -8,
    marginBottom: 12,
  },
  helpText: {color: theme.textDim, fontSize: 11, lineHeight: 16},
  context: {color: theme.textDim, fontSize: 11, lineHeight: 16, marginBottom: 12},
  honeypot: {position: 'absolute', width: 1, height: 1, opacity: 0},
  submissionError: {color: theme.danger, fontSize: 11, lineHeight: 16, marginBottom: 10},
  submitButton: {
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    backgroundColor: theme.accent,
    paddingHorizontal: 14,
    marginTop: 2,
  },
  submitButtonDisabled: {opacity: 0.45},
  submitButtonText: {color: theme.bg, fontSize: 12, fontWeight: '800'},
  success: {
    marginTop: 18,
    padding: 14,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.accent,
    backgroundColor: 'rgba(74, 222, 128, 0.08)',
  },
  successTitle: {color: theme.accent, fontSize: 14, fontWeight: '800', marginBottom: 3},
});
