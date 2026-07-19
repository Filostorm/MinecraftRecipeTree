import assert from 'node:assert/strict';
import test from 'node:test';
import {
  requirePackIdentity,
  requirePublishablePackIdentity,
  slugForPackName,
} from './pack-identity.mjs';

test('accepts exact explicit and launcher-backed pack identities', () => {
  assert.deepEqual(requirePackIdentity({
    name: 'Multiblock Madness 2',
    version: '1.0.0',
    identitySource: 'explicit-request',
  }), {
    name: 'Multiblock Madness 2',
    version: '1.0.0',
    identitySource: 'explicit-request',
  });
  assert.equal(requirePackIdentity({
    name: 'Pack',
    version: '4',
    identitySource: 'curseforge',
    instanceName: 'Pack - local test',
    provider: 'curseforge',
    projectId: '123',
    versionId: '456',
  }).provider, 'curseforge');
});

test('rejects contract drift and misleading text', () => {
  for (const identity of [
    {name: ' Pack', version: '1', identitySource: 'explicit-request'},
    {name: 'Pack\u202eexe', version: '1', identitySource: 'explicit-request'},
    {name: 'Pack', version: '1', identitySource: 'guessed'},
    {name: 'Pack', version: '1', identitySource: 'explicit-request', unknown: true},
    {name: 'Pack', version: '1', identitySource: 'curseforge', projectId: '123'},
  ]) {
    assert.throws(() => requirePackIdentity(identity));
  }
});

test('bounds identity text by Unicode code points rather than UTF-16 code units', () => {
  const name = '🧱'.repeat(120);
  const version = '🚀'.repeat(80);
  assert.equal(
    requirePackIdentity({name, version, identitySource: 'explicit-request'}).name,
    name,
  );
  assert.throws(
    () => requirePackIdentity({
      name: `${name}🧱`,
      version: '1',
      identitySource: 'explicit-request',
    }),
    /at most 120 characters/,
  );
  assert.throws(
    () => requirePackIdentity({
      name: 'Pack',
      version: `${version}🚀`,
      identitySource: 'explicit-request',
    }),
    /at most 80 characters/,
  );
  assert.equal(
    requirePackIdentity({
      name: 'Pack',
      version: '1',
      identitySource: 'curseforge',
      instanceName: '🌍'.repeat(120),
      provider: 'curseforge',
      projectId: '📦'.repeat(120),
    }).projectId,
    '📦'.repeat(120),
  );
});

test('rejects every canonical control, bidi, and zero-width code-point range', () => {
  const unsafeCodePoints = [
    0x0000,
    0x001f,
    0x007f,
    0x009f,
    0x061c,
    0x200b,
    0x200c,
    0x200d,
    0x200e,
    0x200f,
    0x202a,
    0x202e,
    0x2060,
    0x2065,
    0x2069,
    0xfeff,
  ];
  for (const codePoint of unsafeCodePoints) {
    const unsafe = String.fromCodePoint(codePoint);
    assert.throws(
      () => requirePackIdentity({
        name: `Safe${unsafe}Pack`,
        version: '1',
        identitySource: 'explicit-request',
      }),
      /control or directional-formatting/,
      `expected U+${codePoint.toString(16).toUpperCase()} to be rejected`,
    );
  }
});

test('hosted publication requires an explicit or launcher-backed versioned identity', () => {
  assert.throws(
    () => requirePublishablePackIdentity({name: 'Pack', identitySource: 'curseforge'}),
    /version is required/,
  );
  assert.throws(
    () => requirePublishablePackIdentity({
      name: 'Pack',
      version: '1',
      identitySource: 'game-directory',
    }),
    /inferred from the game-directory/,
  );
});

test('derives stable readable slug candidates without accepting empty Unicode results', () => {
  assert.equal(slugForPackName('Crème & Create 2'), 'creme-create-2');
  assert.throws(() => slugForPackName('世界'), /cannot produce a URL slug/);
});
