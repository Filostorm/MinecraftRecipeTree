import * as Haptics from 'expo-haptics';

export function selectionFeedback() {
  void Haptics.selectionAsync().catch(() => {
    // Haptics are an enhancement and can be unavailable on some devices/settings.
  });
}

export function lightImpactFeedback() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {
    // Haptics are an enhancement and can be unavailable on some devices/settings.
  });
}
