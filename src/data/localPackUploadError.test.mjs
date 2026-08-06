import assert from 'node:assert/strict';
import test from 'node:test';

import {localPackUploadErrorMessage} from './localPackUploadError.ts';

test('shows the exact unsafe ZIP entry and rejection reason', () => {
  const message =
    'The ZIP entry "../manifest.json" cannot be opened safely: it tries to leave the export folder.';

  assert.equal(localPackUploadErrorMessage(new Error(message)), message);
});

test('keeps actionable update ZIP recovery instructions', () => {
  const message = 'Install the full Update Pack export before adding this update ZIP.';
  assert.equal(localPackUploadErrorMessage(new Error(message)), message);
});

test('names the exact manifest validation failure', () => {
  assert.equal(
    localPackUploadErrorMessage(new Error('manifest.json counts.recipes must be a non-negative safe integer.')),
    'The exporter manifest is invalid: manifest.json counts.recipes must be a non-negative safe integer.',
  );
});

test('distinguishes a missing manifest from invalid manifest data', () => {
  assert.equal(
    localPackUploadErrorMessage(new Error(
      'No exporter manifest.json was found at the ZIP root or inside one top-level folder.',
    )),
    'This ZIP does not contain exporter manifest.json at its root or inside one top-level folder.',
  );
});

test('includes the underlying reason for unreadable archives', () => {
  assert.equal(
    localPackUploadErrorMessage(new Error(
      'The selected file is not a readable ZIP archive: invalid zip data',
    )),
    'This file is not a readable ZIP: The selected file is not a readable ZIP archive: invalid zip data',
  );
});

test('keeps the concrete reason for otherwise unknown import failures', () => {
  assert.equal(
    localPackUploadErrorMessage(new Error('IndexedDB transaction was aborted while saving exports/items.json.')),
    'The pack could not be added: IndexedDB transaction was aborted while saving exports/items.json.',
  );
});
