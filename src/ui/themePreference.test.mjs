import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const preferenceSource = await readFile(new URL('./themePreference.tsx', import.meta.url), 'utf8');
const accountSource = await readFile(new URL('../account/AccountModal.tsx', import.meta.url), 'utf8');
const globalStyles = await readFile(new URL('../../app/globals.css', import.meta.url), 'utf8');

test('color theme and Minecraft font preferences are independent and persisted', () => {
  assert.match(preferenceSource, /ThemePreference = 'dark' \| 'light'/u);
  assert.doesNotMatch(preferenceSource, /ThemePreference = [^\n]*minecraft/u);
  assert.match(preferenceSource, /window\.localStorage\.setItem\(THEME_STORAGE_KEY, next\)/u);
  assert.match(preferenceSource, /window\.localStorage\.setItem\(FONT_STORAGE_KEY, next \? 'minecraft' : 'default'\)/u);
  assert.match(accountSource, /Dark[\s\S]*?Light/u);
  assert.match(accountSource, /themePreference\.setPreference\(choice\.value\)/u);
  assert.match(accountSource, /themePreference\.setMinecraftFont\(!themePreference\.minecraftFont\)/u);
  assert.match(preferenceSource, /stored === 'minecraft'[\s\S]*?preference: 'dark', minecraftFont: true/u);
});

test('Minecraft font mode only applies the bundled Monocraft typeface', () => {
  assert.match(globalStyles, /@font-face[\s\S]*?Monocraft\.ttf/u);
  assert.match(globalStyles, /data-mrt-font='minecraft'[\s\S]*?font-family:\s*'Monocraft'/u);
  assert.doesNotMatch(globalStyles, /data-mrt-theme='minecraft'/u);
  assert.doesNotMatch(globalStyles, /data-mrt-font='minecraft'\][^\n]*\[role='button'\]/u);
  assert.doesNotMatch(globalStyles, /text-shadow/u);
});

test('account identity fields stay behind a compact edit disclosure', () => {
  assert.match(accountSource, /Account details/u);
  assert.match(accountSource, /accessibilityState=\{\{expanded: editingDetails\}\}/u);
  assert.match(accountSource, /editingDetails \? 'Done' : 'Edit'/u);
  assert.match(accountSource, /\{editingDetails && \(/u);
  assert.match(accountSource, /styles\.compactInput/u);
  assert.match(accountSource, /styles\.compactButton/u);
});
