import {useEffect, useRef} from 'react';
import {AppState, Platform} from 'react-native';
import type {DatasetDescriptor} from './datasetCatalog';
import {sendCommunityViews} from './communityViews';

export function useCommunityViewTracking(pack: DatasetDescriptor, itemKey: string | null) {
  const record = useRef<(key: string) => void>(() => {});
  useEffect(() => {
    if (pack.slug.startsWith('local-')) return;
    const storageKey = `communityViews:v1:${pack.slug}`;
    let today = new Date().toISOString().slice(0, 10);
    let seen = new Set<string>();
    try {
      const raw = globalThis.localStorage?.getItem(storageKey);
      if (raw) {
        const saved = JSON.parse(raw);
        if (saved.day === today && Array.isArray(saved.keys) && saved.keys.length <= 2000 && saved.keys.every((key: unknown) => typeof key === 'string')) seen = new Set(saved.keys);
      }
    } catch (error) {console.error('Daily view deduplication could not be restored.', error);}
    const pending = new Set<string>();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let sending = false;
    let active = true;
    const flush = async () => {
      if (sending || !pending.size) return;
      sending = true;
      const batch = [...pending].slice(0, 20);
      try {
        await sendCommunityViews(pack, batch);
        batch.forEach(key => pending.delete(key));
      } catch (error) {
        // Views are best-effort analytics, not user data. Do not loop retries and
        // burn D1 or accidentally double-count a response lost after a commit.
        console.error('Community views could not be reported; dropping this analytics batch.', error);
        batch.forEach(key => pending.delete(key));
      } finally {
        sending = false;
        if (active && pending.size && !timer) timer = setTimeout(() => {timer = undefined; void flush();}, 10_000);
      }
    };
    record.current = key => {
      const day = new Date().toISOString().slice(0, 10);
      if (day !== today) {today = day; seen.clear();}
      if (seen.has(key) || seen.size >= 2000) return;
      seen.add(key); pending.add(key);
      try {globalThis.localStorage?.setItem(storageKey, JSON.stringify({day: today, keys: [...seen]}));}
      catch (error) {console.error('Daily item views could not be saved locally.', error);}
      if (!timer) timer = setTimeout(() => {timer = undefined; void flush();}, 10_000);
    };
    const subscription = AppState.addEventListener('change', state => {if (state !== 'active') void flush();});
    const onHide = () => {void flush();};
    if (Platform.OS === 'web') globalThis.addEventListener?.('pagehide', onHide);
    return () => {
      active = false;
      record.current = () => {};
      if (timer) clearTimeout(timer);
      subscription.remove();
      if (Platform.OS === 'web') globalThis.removeEventListener?.('pagehide', onHide);
      void flush();
    };
  }, [pack.slug, pack.publicationId]);
  useEffect(() => {if (itemKey) record.current(itemKey);}, [itemKey, pack.slug, pack.publicationId]);
}
