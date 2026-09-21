import React, {useEffect, useRef, useState} from 'react';
import {Alert, ActivityIndicator, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {useUser} from '../account/UserContext';
import type {DatasetDescriptor} from '../data/datasetCatalog';
import {listDownloads, saveDownload} from './packLibrary.native';
import {theme} from '../theme';

/** Mounted above the dataset: selecting a different pack does not cancel an accepted download. */
export function PackDownloadPrompt({dataset, onComplete}: {dataset: DatasetDescriptor | null; onComplete(): void}) {
  const account = useUser();
  const [status, setStatus] = useState<string | null>(null);
  const controller = useRef<AbortController | null>(null);
  const complete = useRef(onComplete); complete.current = onComplete;
  const accountId = useRef(account.user?.id); accountId.current = account.user?.id;
  useEffect(() => () => controller.current?.abort(new Error('The account changed or the viewer closed.')), [account.user?.id]);
  useEffect(() => {
    const userId = account.user?.id;
    if (!dataset || !userId || dataset.slug.startsWith('local-') || controller.current) return;
    try {
      if (listDownloads(userId).some(row => row.descriptor.publicationId === dataset.publicationId && row.descriptor.previewAssetSetId === dataset.previewAssetSetId)) return;
    } catch (error) {
      console.error('Could not check whether the selected pack is downloaded.', error);
      Alert.alert('Downloads unavailable', 'Open Settings to check your downloaded library.');
      return;
    }
    Alert.alert(`Download ${dataset.displayName}?`, 'Keep browsing while this pack saves offline. Keep the app open; large packs may use over 1 GB. Wi-Fi recommended.', [
      {text: 'Not now', style: 'cancel'},
      {text: 'Download', onPress: () => {
        if (accountId.current !== userId || controller.current) return;
        const request = new AbortController(); controller.current = request;
        setStatus(`Downloading ${dataset.displayName}…`);
        void saveDownload(userId, dataset, request.signal, progress => setStatus(`${dataset.displayName} · ${Math.round(progress.bytes / 1024 / 1024)} MB`))
          .then(() => {if (accountId.current === userId) complete.current();})
          .catch(error => {
            console.error('Selected pack download did not complete.', error);
            if (!request.signal.aborted) Alert.alert('Download failed', error instanceof Error ? error.message : 'Please retry from Settings.');
          }).finally(() => {if (controller.current === request) {controller.current = null; setStatus(null);}});
      }},
    ]);
  }, [dataset, account.user?.id]);
  if (!status) return null;
  return <View style={s.banner} accessibilityLiveRegion="polite">
    <ActivityIndicator color={theme.accent}/><Text style={s.text} numberOfLines={1}>{status}</Text>
    <TouchableOpacity accessibilityRole="button" accessibilityLabel="Cancel pack download" onPress={() => controller.current?.abort(new Error('Download cancelled.'))} style={s.cancel}><Text style={{color: theme.danger}}>Cancel</Text></TouchableOpacity>
  </View>;
}
const s = StyleSheet.create({
  banner: {flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 12, backgroundColor: theme.panelAlt, borderBottomWidth: 1, borderColor: theme.border},
  text: {color: theme.text, flex: 1, fontSize: 12}, cancel: {minHeight: 44, justifyContent: 'center'},
});
