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

test('mobile signup uses the existing password auth instead of a second account system', () => {
  const form = source('../account/EmailAccountForm.tsx');
  assert.match(form, /account.signUpWithPassword\(email, password\)/);
  assert.match(form, /account.signInWithPassword\(email, password\)/);
  assert.match(form, /password !== confirmation/);
  assert.match(form, /confirmation_required/);
  assert.match(form, /secureTextEntry/);
});
