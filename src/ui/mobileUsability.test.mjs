import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
const source = path => readFileSync(new URL(path, import.meta.url), 'utf8');

test('UI previews cannot intercept the parent settings scroll', () => {
  const scale = source('../components/ScaleSettings.tsx');
  assert.doesNotMatch(scale, /ScrollView/);
  assert.match(scale, /pointerEvents="none"/);
  assert.match(source('../account/AccountModal.native.tsx'), /\{showUi && <ScaleSettings/);
});

test('graph toggles retain accessible state without redundant on/off prose', () => {
  const sheet = source('../graph/GraphSettingsSheet.tsx');
  assert.match(sheet, /accessibilityRole=\{option.kind === 'toggle' \? 'switch' : 'button'\}/);
  assert.match(sheet, /checked: option.active === true/);
  assert.doesNotMatch(sheet, /\{option.description\}|\? 'On' : 'Off'/);
});

test('graph settings use a native draggable sheet with coordinated scrolling', () => {
  const surface = source('../graph/GraphSheetSurface.native.tsx');
  assert.match(surface, /@expo\/ui\/community\/bottom-sheet/);
  assert.match(surface, /index=\{visible \? 0 : -1\}/);
  assert.match(surface, /enablePanDownToClose/);
  assert.match(surface, /if \(!visibleRef.current\) return/);
  assert.match(surface, /visibleRef.current = false;\s*onClose\(\)/);
  const sheet = source('../graph/GraphSettingsSheet.tsx');
  assert.match(sheet, /<ScrollView nestedScrollEnabled/);
  assert.match(sheet, /<NativeUiScale>/);
  assert.doesNotMatch(sheet, /animationType="fade"|<Modal/);
});

test('navigation uses platform icons and graph settings are distinct from the main gear', () => {
  const icons = source('../components/AppSymbol.tsx');
  assert.match(icons, /from 'expo-symbols'/);
  assert.match(icons, /graphSettings: \{ios: 'slider.horizontal.3', android: 'tune'/);
  assert.match(icons, /settings: \{ios: 'gearshape', android: 'settings'/);
  assert.match(source('../graph/GraphScreen.tsx'), /<AppIcon name="graphSettings"/);
  assert.match(source('../components/AppIcon.tsx'), /materialIconPaths/);
});

test('mobile signup uses the existing password auth instead of a second account system', () => {
  const form = source('../account/EmailAccountForm.tsx');
  assert.match(form, /account.signUpWithPassword\(email, password\)/);
  assert.match(form, /account.signInWithPassword\(email, password\)/);
  assert.match(form, /password !== confirmation/);
  assert.match(form, /confirmation_required/);
  assert.match(form, /secureTextEntry/);
});
