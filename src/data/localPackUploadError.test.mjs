import assert from 'node:assert/strict';
import test from 'node:test';

import {localPackUploadErrorMessage} from './localPackUploadError.ts';

test('shows the exact unsafe ZIP entry and rejection reason', () => {
  const message =
    'The ZIP entry "../manifest.json" cannot be opened safely: it tries to leave the export folder.';

  assert.equal(localPackUploadErrorMessage(new Error(message)), message);
});
