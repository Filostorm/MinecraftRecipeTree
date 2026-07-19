import assert from 'node:assert/strict';
import test from 'node:test';
import {
  EXPORTER_RELEASE_MANIFEST_FORMAT,
  requireExporterReleaseManifest,
} from './exporterReleases.ts';

const SHA_A = 'a'.repeat(64);
const SHA_B = 'b'.repeat(64);

function release(overrides = {}) {
  return {
    id: 'forge-jei-1.20.1',
    minecraftVersion: '1.20.1',
    recipeViewer: 'JEI 15',
    loader: 'Forge 47',
    version: '1.0.0',
    filename: 'recipe-tree-exporter-forge-1.20.1-1.0.0.jar',
    downloadUrl: '/exporters/recipe-tree-exporter-forge-1.20.1-1.0.0.jar',
    sha256: SHA_A,
    bytes: 123456,
    qualityProfiles: ['generic-jei-1.20.1'],
    compatibility: 'Forge 47 with JEI 15.x',
    ...overrides,
  };
}

function manifest(releases = [release()], overrides = {}) {
  return {
    format: EXPORTER_RELEASE_MANIFEST_FORMAT,
    generatedAt: '2026-07-19T12:34:56.789Z',
    releases,
    ...overrides,
  };
}

test('accepts and freezes the exact public exporter release contract', () => {
  const result = requireExporterReleaseManifest(manifest());
  assert.equal(result.releases[0].minecraftVersion, '1.20.1');
  assert.equal(result.releases[0].downloadUrl, `/exporters/${result.releases[0].filename}`);
  assert.equal(Object.isFrozen(result), true);
  assert.equal(Object.isFrozen(result.releases), true);
  assert.equal(Object.isFrozen(result.releases[0].qualityProfiles), true);
});

test('rejects top-level or release contract drift and noncanonical timestamps', () => {
  assert.throws(
    () => requireExporterReleaseManifest({...manifest(), unexpected: true}),
    /exact top-level contract/,
  );
  assert.throws(
    () => requireExporterReleaseManifest(manifest([{...release(), source: 'local.jar'}])),
    /exact release contract/,
  );
  assert.throws(
    () => requireExporterReleaseManifest(manifest(undefined, {generatedAt: '2026-07-19'})),
    /canonical ISO timestamp/,
  );
});

test('allows only the exact same-origin exporter path for its safe release filename', () => {
  for (const downloadUrl of [
    'https://example.test/exporters/recipe-tree-exporter-forge-1.20.1-1.0.0.jar',
    '/other/recipe-tree-exporter-forge-1.20.1-1.0.0.jar',
    '/exporters/../private.jar',
    '/exporters/recipe-tree-exporter-forge-1.20.1-1.0.0.jar?download=1',
  ]) {
    assert.throws(
      () => requireExporterReleaseManifest(manifest([release({downloadUrl})])),
      /same-origin path/,
    );
  }
  assert.throws(
    () => requireExporterReleaseManifest(manifest([release({filename: 'exporter-sources.jar'})])),
    /unsafe release JAR filename/,
  );
});

test('requires lowercase SHA-256, positive byte counts, and nonempty canonical profiles', () => {
  assert.throws(
    () => requireExporterReleaseManifest(manifest([release({sha256: SHA_A.toUpperCase()})])),
    /lowercase 64-hex/,
  );
  assert.throws(
    () => requireExporterReleaseManifest(manifest([release({bytes: 0})])),
    /positive safe integer/,
  );
  assert.throws(
    () => requireExporterReleaseManifest(manifest([release({qualityProfiles: []})])),
    /between 1 and 16 quality profiles/,
  );
  assert.throws(
    () =>
      requireExporterReleaseManifest(
        manifest([release({qualityProfiles: ['generic-jei-1.20.1', 'generic-jei-1.20.1']})]),
      ),
    /repeats a quality profile/,
  );
});

test('counts Unicode code points and rejects control, bidi, and zero-width text', () => {
  assert.equal(
    requireExporterReleaseManifest(
      manifest([release({compatibility: '🧱'.repeat(320)})]),
    ).releases[0].compatibility,
    '🧱'.repeat(320),
  );
  assert.throws(
    () =>
      requireExporterReleaseManifest(
        manifest([release({compatibility: '🧱'.repeat(321)})]),
      ),
    /invalid or unbounded identity text/,
  );
  assert.throws(
    () =>
      requireExporterReleaseManifest(
        manifest([release({compatibility: 'Forge\u202e deceptive text'})]),
      ),
    /invalid or unbounded identity text/,
  );
});

test('enforces the release-count bound and globally unique identities and paths', () => {
  assert.throws(
    () => requireExporterReleaseManifest(manifest([])),
    /between 1 and 16 releases/,
  );
  assert.throws(
    () => requireExporterReleaseManifest(manifest(Array.from({length: 17}, () => release()))),
    /between 1 and 16 releases/,
  );
  const second = release({
    minecraftVersion: '1.18.2',
    sha256: SHA_B,
  });
  assert.throws(
    () => requireExporterReleaseManifest(manifest([release(), second])),
    /repeats id/,
  );
  assert.throws(
    () =>
      requireExporterReleaseManifest(
        manifest([
          release(),
          release({
            id: 'another-release',
            sha256: SHA_B,
          }),
        ]),
      ),
    /repeats filename/,
  );
});
