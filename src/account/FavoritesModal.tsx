import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useData} from '../data/DataContext';
import {
  type CommunityRecipeFavorite,
  type PersonalRecipeFavorite,
  loadPersonalRecipeFavorites,
  loadRecipeFavoriteLeaderboard,
} from '../data/recipeFavorites';
import {theme} from '../theme';
import {useUser} from './UserContext';

export function FavoritesModal({
  visible,
  interfaceZoom = 1,
  onClose,
}: {
  visible: boolean;
  interfaceZoom?: number;
  onClose(): void;
}) {
  const data = useData();
  const account = useUser();
  const [leaderboard, setLeaderboard] = useState<CommunityRecipeFavorite[]>([]);
  const [personal, setPersonal] = useState<PersonalRecipeFavorite[]>([]);
  const [selectedTab, setSelectedTab] = useState<'mine' | 'leaderboard'>(
    account.user ? 'mine' : 'leaderboard',
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;
    let alive = true;
    setLoading(true);
    setError(null);
    Promise.all([
      loadRecipeFavoriteLeaderboard(data.descriptor),
      account.user ? loadPersonalRecipeFavorites(data.descriptor) : Promise.resolve([]),
    ])
      .then(([entries, personal]) => {
        if (!alive) return;
        setLeaderboard(entries);
        setPersonal(personal);
      })
      .catch(cause => {
        if (!alive) return;
        console.error('Favorite leaderboard could not be loaded.', cause);
        setError('Favorites could not be loaded.');
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [account.revision, account.user, data.descriptor, visible]);

  useEffect(() => {
    if (!account.user && selectedTab === 'mine') setSelectedTab('leaderboard');
  }, [account.user, selectedTab]);

  const scaledCardStyle = Platform.OS === 'web'
    ? ({zoom: interfaceZoom, width: `${100 / interfaceZoom}%`, maxWidth: 640 / interfaceZoom} as object)
    : null;

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable accessibilityViewIsModal style={[styles.card, scaledCardStyle]} onPress={() => {}}>
          <View style={styles.header}>
            <View style={styles.headerCopy}>
              <Text style={styles.title}>Recipe favorites</Text>
              <Text style={styles.subtitle}>
                {account.user
                  ? `${account.user.displayName} · ${personal.length} synced favorite${personal.length === 1 ? '' : 's'}`
                  : 'Sign in to sync favorites across devices'}
              </Text>
            </View>
            <TouchableOpacity accessibilityRole="button" accessibilityLabel="Close favorites" style={styles.closeButton} onPress={onClose}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.accountRow}>
            {account.user ? (
              <TouchableOpacity
                accessibilityRole="button"
                style={styles.secondaryButton}
                onPress={() => {
                  void account.signOut().catch(cause => {
                    console.error('Account sign-out failed.', cause);
                    setError('Sign out failed. Please try again.');
                  });
                }}>
                <Text style={styles.secondaryButtonText}>Sign out</Text>
              </TouchableOpacity>
            ) : (
              <TouchableOpacity accessibilityRole="button" style={styles.primaryButton} onPress={account.signIn}>
                <Text style={styles.primaryButtonText}>Sign in with Discord</Text>
              </TouchableOpacity>
            )}
            <Text style={styles.privacyText}>Leaderboard totals are public. User identities are not.</Text>
          </View>

          <View style={styles.tabs} accessibilityRole="tablist">
            {account.user && (
              <TouchableOpacity
                accessibilityRole="tab"
                accessibilityState={{selected: selectedTab === 'mine'}}
                style={[styles.tab, selectedTab === 'mine' && styles.tabActive]}
                onPress={() => setSelectedTab('mine')}>
                <Text style={[styles.tabText, selectedTab === 'mine' && styles.tabTextActive]}>
                  My favorites ({personal.length})
                </Text>
              </TouchableOpacity>
            )}
            <TouchableOpacity
              accessibilityRole="tab"
              accessibilityState={{selected: selectedTab === 'leaderboard'}}
              style={[styles.tab, selectedTab === 'leaderboard' && styles.tabActive]}
              onPress={() => setSelectedTab('leaderboard')}>
              <Text style={[styles.tabText, selectedTab === 'leaderboard' && styles.tabTextActive]}>
                Leaderboard
              </Text>
            </TouchableOpacity>
          </View>
          <Text style={styles.sectionTitle}>
            {selectedTab === 'mine' ? 'Synced recipes' : 'Most favorited recipes'} · {data.descriptor.displayName}
          </Text>
          <ScrollView style={styles.scroll} contentContainerStyle={styles.list}>
            {loading ? (
              <View style={styles.centerState}>
                <ActivityIndicator color={theme.accent} />
                <Text style={styles.stateText}>Loading favorites…</Text>
              </View>
            ) : error ? (
              <Text accessibilityRole="alert" style={styles.errorText}>{error}</Text>
            ) : selectedTab === 'mine' && personal.length === 0 ? (
              <Text style={styles.stateText}>Choose “Use automatically in future trees” on a recipe to add your first favorite.</Text>
            ) : selectedTab === 'leaderboard' && leaderboard.length === 0 ? (
              <Text style={styles.stateText}>No signed-in users have favorited a recipe in this pack version yet.</Text>
            ) : (
              (selectedTab === 'mine' ? personal : leaderboard).map((entry, index) => {
                const itemName = data.itemsByKey.get(entry.itemKey)?.n ?? entry.itemKey;
                const categoryName = data.categories[entry.recipeRef[0]]?.title ?? `Recipe ${entry.recipeRef.join(':')}`;
                return (
                  <View key={`${entry.itemKey}|${entry.recipeRef.join(':')}`} style={styles.row}>
                    <Text style={styles.rank}>{selectedTab === 'mine' ? '★' : index + 1}</Text>
                    <View style={styles.rowCopy}>
                      <Text style={styles.itemName} numberOfLines={1}>{itemName}</Text>
                      <Text style={styles.recipeName} numberOfLines={1}>{categoryName}</Text>
                    </View>
                    {selectedTab === 'leaderboard' && 'count' in entry && (
                      <Text accessibilityLabel={`${entry.count} favorites`} style={styles.count}>★ {entry.count}</Text>
                    )}
                  </View>
                );
              })
            )}
          </ScrollView>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16, backgroundColor: 'rgba(0,0,0,0.72)'},
  card: {width: '100%', maxWidth: 640, maxHeight: '86%', padding: 16, borderRadius: 14, borderWidth: 1, borderColor: theme.borderLight, backgroundColor: theme.panel},
  header: {flexDirection: 'row', alignItems: 'flex-start', gap: 10},
  headerCopy: {flex: 1},
  title: {color: theme.text, fontSize: 18, fontWeight: '800'},
  subtitle: {color: theme.accent, fontSize: 11, marginTop: 3},
  closeButton: {width: 32, height: 32, alignItems: 'center', justifyContent: 'center'},
  closeText: {color: theme.textDim, fontSize: 16},
  accountRow: {flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginTop: 14, padding: 10, borderRadius: 9, backgroundColor: theme.panelAlt},
  primaryButton: {minHeight: 38, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 7, backgroundColor: theme.accent},
  primaryButtonText: {color: '#07120a', fontSize: 12, fontWeight: '800'},
  secondaryButton: {minHeight: 38, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 7, borderWidth: 1, borderColor: theme.borderLight},
  secondaryButtonText: {color: theme.text, fontSize: 12, fontWeight: '700'},
  privacyText: {flex: 1, minWidth: 180, color: theme.textDim, fontSize: 10, lineHeight: 14},
  tabs: {flexDirection: 'row', gap: 6, marginTop: 14},
  tab: {minHeight: 34, justifyContent: 'center', paddingHorizontal: 11, borderRadius: 7, borderWidth: 1, borderColor: theme.border},
  tabActive: {borderColor: theme.accent, backgroundColor: theme.panelAlt},
  tabText: {color: theme.textDim, fontSize: 11, fontWeight: '700'},
  tabTextActive: {color: theme.accent},
  sectionTitle: {marginTop: 16, color: theme.textDim, fontSize: 10, fontWeight: '800', letterSpacing: 0.7, textTransform: 'uppercase'},
  scroll: {marginTop: 6},
  list: {paddingBottom: 4},
  row: {minHeight: 54, flexDirection: 'row', alignItems: 'center', gap: 10, borderBottomWidth: 1, borderBottomColor: theme.border},
  rank: {width: 24, color: theme.textDim, fontSize: 11, fontWeight: '800', textAlign: 'center'},
  rowCopy: {flex: 1, minWidth: 0},
  itemName: {color: theme.text, fontSize: 12, fontWeight: '700'},
  recipeName: {color: theme.textDim, fontSize: 10, marginTop: 2},
  count: {color: theme.accent, fontSize: 12, fontWeight: '800'},
  centerState: {alignItems: 'center', paddingVertical: 28, gap: 8},
  stateText: {color: theme.textDim, fontSize: 11, lineHeight: 16, paddingVertical: 20, textAlign: 'center'},
  errorText: {color: theme.danger, fontSize: 11, paddingVertical: 20, textAlign: 'center'},
});
