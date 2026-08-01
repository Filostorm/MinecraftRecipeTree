import React from 'react';
import {
  Modal,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {theme} from '../theme';
import {SafeAreaProvider, SafeAreaView, initialWindowMetrics} from '../ui/safeArea';

const DESKTOP_UPLOAD_URL = 'minecraftrecipetree.craftsmannsoftware.com/publish';

export function MobileUploadGuide({
  visible,
  onClose,
}: {
  visible: boolean;
  onClose(): void;
}) {
  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle={Platform.OS === 'ios' ? 'fullScreen' : undefined}
      onRequestClose={onClose}
      accessibilityViewIsModal>
      <SafeAreaProvider initialMetrics={initialWindowMetrics}>
        <SafeAreaView edges={['top', 'right', 'bottom', 'left']} style={styles.page}>
          <View style={styles.header}>
            <TouchableOpacity
              style={styles.backButton}
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="Close desktop upload instructions">
              <Text style={styles.backIcon}>‹</Text>
            </TouchableOpacity>
            <Text style={styles.headerTitle} accessibilityRole="header">
              Upload
            </Text>
            <View style={styles.headerSpacer} />
          </View>

          <ScrollView
            style={styles.scroll}
            contentContainerStyle={styles.content}
            showsVerticalScrollIndicator={false}>
            <View style={styles.desktopBadge}>
              <Text style={styles.desktopBadgeText}>DESKTOP REQUIRED</Text>
            </View>
            <Text style={styles.title}>Continue on your computer</Text>
            <Text style={styles.body}>
              Modpack exports can be large, so creating and uploading an exporter ZIP is handled by
              the desktop website. You can keep using the mobile app after the pack is published.
            </Text>

            <View style={styles.urlCard}>
              <Text style={styles.urlLabel}>Open this address on your desktop</Text>
              <Text
                style={styles.url}
                selectable
                numberOfLines={1}
                adjustsFontSizeToFit
                minimumFontScale={0.72}
                accessibilityLabel={`Desktop upload address ${DESKTOP_UPLOAD_URL}`}>
                {DESKTOP_UPLOAD_URL}
              </Text>
            </View>

            <View style={styles.steps}>
              <UploadStep
                number="1"
                title="Run the exporter in Minecraft"
                body="Use the exporter version that matches your Minecraft and recipe-viewer version, then let it finish creating the ZIP."
              />
              <UploadStep
                number="2"
                title="Open Recipe Tree on desktop"
                body={`Visit ${DESKTOP_UPLOAD_URL} in a desktop browser.`}
              />
              <UploadStep
                number="3"
                title="Drop in the exporter ZIP"
                body="Drag the completed ZIP onto the upload page and follow the validation prompts to publish the pack."
              />
              <UploadStep
                number="4"
                title="Return to mobile"
                body="Open the modpack picker again. The published pack will be available after the catalog refreshes."
                last
              />
            </View>
          </ScrollView>

          <View style={styles.footer}>
            <TouchableOpacity
              style={styles.doneButton}
              onPress={onClose}
              accessibilityRole="button">
              <Text style={styles.doneButtonText}>Got it</Text>
            </TouchableOpacity>
          </View>
        </SafeAreaView>
      </SafeAreaProvider>
    </Modal>
  );
}

function UploadStep({
  number,
  title,
  body,
  last = false,
}: {
  number: string;
  title: string;
  body: string;
  last?: boolean;
}) {
  return (
    <View style={styles.step}>
      <View style={styles.stepRail}>
        <View style={styles.stepNumber}>
          <Text style={styles.stepNumberText}>{number}</Text>
        </View>
        {!last && <View style={styles.stepLine} />}
      </View>
      <View style={styles.stepCopy}>
        <Text style={styles.stepTitle}>{title}</Text>
        <Text style={styles.stepBody}>{body}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  page: {flex: 1, backgroundColor: theme.bg},
  header: {
    minHeight: 58,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    backgroundColor: theme.panel,
  },
  backButton: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
  },
  backIcon: {color: theme.accent, fontSize: 32, lineHeight: 34},
  headerTitle: {
    flex: 1,
    color: theme.text,
    textAlign: 'left',
    fontSize: 17,
    fontWeight: '800',
  },
  headerSpacer: {width: 44},
  scroll: {flex: 1},
  content: {padding: 22, paddingBottom: 30},
  desktopBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: theme.accent,
    backgroundColor: theme.panelAlt,
  },
  desktopBadgeText: {color: theme.accent, fontSize: 9, fontWeight: '900', letterSpacing: 0.8},
  title: {color: theme.text, fontSize: 28, lineHeight: 34, fontWeight: '900', marginTop: 16},
  body: {color: theme.textDim, fontSize: 14, lineHeight: 21, marginTop: 10},
  urlCard: {
    marginTop: 22,
    padding: 16,
    gap: 7,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panel,
  },
  urlLabel: {color: theme.textDim, fontSize: 11, fontWeight: '700'},
  url: {color: theme.accent, fontSize: 14, lineHeight: 20, fontWeight: '800'},
  steps: {marginTop: 26},
  step: {flexDirection: 'row', minHeight: 94},
  stepRail: {width: 38, alignItems: 'center'},
  stepNumber: {
    width: 30,
    height: 30,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 15,
    backgroundColor: theme.accent,
  },
  stepNumberText: {color: theme.bg, fontSize: 12, fontWeight: '900'},
  stepLine: {width: 1, flex: 1, backgroundColor: theme.borderLight},
  stepCopy: {flex: 1, paddingLeft: 10, paddingBottom: 22},
  stepTitle: {color: theme.text, fontSize: 14, lineHeight: 19, fontWeight: '800'},
  stepBody: {color: theme.textDim, fontSize: 12, lineHeight: 18, marginTop: 5},
  footer: {
    padding: 12,
    borderTopWidth: 1,
    borderTopColor: theme.border,
    backgroundColor: theme.panel,
  },
  doneButton: {
    minHeight: 50,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 13,
    backgroundColor: theme.accent,
  },
  doneButtonText: {color: theme.bg, fontSize: 15, fontWeight: '900'},
});
