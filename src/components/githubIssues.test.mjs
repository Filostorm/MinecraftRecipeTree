import assert from 'node:assert/strict';
import test from 'node:test';

import {buildIssueReportPayload} from './githubIssues.ts';

const context = {
  packSlug: 'meatballcraft',
  packName: 'MeatballCraft',
  packVersion: '0.18.6',
  minecraftVersion: '1.12.2',
  publicationId: 'a'.repeat(64),
  previewAssetSetId: 'b'.repeat(64),
  exportGeneratedAt: '2026-08-06T20:00:00Z',
  exportFormat: 2,
  itemCount: 196160,
  recipeCount: 200000,
  categoryCount: 180,
  modCount: 320,
  activeTab: 'graph',
  openItemKey: '',
  graphRootKey: 'item|thaumcraft:cluster',
  graphDirection: 'inputs',
  interfaceZoomPercent: 125,
};

const runtime = {
  page: '/?pack=meatballcraft',
  platform: 'web',
  userAgent: 'Test Browser/1.0',
  viewport: '1280×720 @2x',
  language: 'en-US',
  online: 'yes',
};

test('bug reports include bounded product and runtime diagnostics', () => {
  const payload = buildIssueReportPayload(
    'bug',
    'Crucible alternatives are wrong',
    'The recipe requires every ore at once.',
    context,
    runtime,
  );

  assert.equal(payload.kind, 'bug');
  assert.equal(payload.title, 'Crucible alternatives are wrong');
  assert.equal(payload.packSlug, 'meatballcraft');
  assert.equal(payload.page, '/?pack=meatballcraft');
  assert.equal(payload.diagnostics.packVersion, '0.18.6');
  assert.equal(payload.diagnostics.publicationId, 'a'.repeat(64));
  assert.equal(payload.diagnostics.graphRootKey, 'item|thaumcraft:cluster');
  assert.equal(payload.diagnostics.interfaceZoom, '125%');
  assert.equal(payload.diagnostics.viewport, '1280×720 @2x');
  assert.equal(payload.contact, '');
  assert.equal(payload.website, '');
});

test('feedback uses the same diagnostic contract', () => {
  const payload = buildIssueReportPayload(
    'feedback',
    'Machine totals could be clearer',
    'Please keep totals visible while panning.',
    context,
    runtime,
  );
  assert.equal(payload.kind, 'feedback');
  assert.equal(payload.diagnostics.activeTab, 'graph');
  assert.equal(payload.diagnostics.userAgent, 'Test Browser/1.0');
});
