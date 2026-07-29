import assert from 'node:assert/strict';
import test from 'node:test';
import {strToU8, zipSync} from 'fflate';
import {
  EXPORTER_BUILD_ALGORITHM,
  EXPORTER_BUILD_FORMAT,
  EXPORTER_BUILD_RESOURCE_PATH,
  canonicalExporterBuildIdentityBytes,
  canonicalExporterPayloadSha256,
  inspectExporterJarBuild,
  requireMatchingExportedBuildIdentity,
} from './exporter-artifact-provenance.mjs';

function provenanceJar(overrides = {}) {
  const payload = overrides.payload ?? [
    ['META-INF/MANIFEST.MF', strToU8('Manifest-Version: 1.0\r\n\r\n')],
    ['com/example/Exporter.class', Uint8Array.from([0xca, 0xfe, 0xba, 0xbe, 0x34])],
  ];
  const identity = {
    format: EXPORTER_BUILD_FORMAT,
    exporterId: 'forge-rei-1.18.2',
    minecraftVersion: '1.18.2',
    algorithm: EXPORTER_BUILD_ALGORITHM,
    payloadSha256: canonicalExporterPayloadSha256(payload),
    ...overrides.identity,
  };
  const resourceBytes =
    overrides.resourceBytes ?? canonicalExporterBuildIdentityBytes(identity);
  const files = Object.fromEntries(payload);
  files[EXPORTER_BUILD_RESOURCE_PATH] = resourceBytes;
  return {
    bytes: Buffer.from(zipSync(files, {level: 6})),
    identity,
    resourceBytes: Buffer.from(resourceBytes),
  };
}

test('recomputes the canonical JAR payload and matches byte-exact exporter self-attestation', () => {
  const fixture = provenanceJar();
  const inspected = inspectExporterJarBuild(fixture.bytes);
  assert.deepEqual(inspected.identity, fixture.identity);
  assert.deepEqual(inspected.resourceBytes, fixture.resourceBytes);
  assert.equal(inspected.payloadEntries, 2);
  assert.deepEqual(
    requireMatchingExportedBuildIdentity(fixture.resourceBytes, inspected),
    fixture.identity,
  );
});

test('rejects payload mutation, caller-shaped identity, and noncanonical exported bytes', () => {
  const accepted = provenanceJar();
  const changedPayload = provenanceJar({
    payload: [
      ['META-INF/MANIFEST.MF', strToU8('Manifest-Version: 1.0\r\n\r\n')],
      ['com/example/Exporter.class', Uint8Array.from([0xca, 0xfe, 0xba, 0xbe, 0x35])],
    ],
    resourceBytes: accepted.resourceBytes,
  });
  assert.throws(
    () => inspectExporterJarBuild(changedPayload.bytes),
    /does not match its embedded build identity/,
  );

  const inspected = inspectExporterJarBuild(accepted.bytes);
  assert.throws(
    () =>
      requireMatchingExportedBuildIdentity(
        Buffer.from(`${JSON.stringify(accepted.identity, null, 2)}\n`),
        inspected,
      ),
    /canonical byte representation/,
  );
  const otherIdentity = {
    ...accepted.identity,
    payloadSha256: '0'.repeat(64),
  };
  assert.throws(
    () =>
      requireMatchingExportedBuildIdentity(
        canonicalExporterBuildIdentityBytes(otherIdentity),
        inspected,
      ),
    /not byte-identical/,
  );
});

test('rejects missing provenance and unsafe ZIP entry paths before acceptance', () => {
  const missing = Buffer.from(
    zipSync({'META-INF/MANIFEST.MF': strToU8('Manifest-Version: 1.0\r\n\r\n')}),
  );
  assert.throws(() => inspectExporterJarBuild(missing), /is missing/);

  const unsafe = Buffer.from(
    zipSync({
      '../escape.class': Uint8Array.from([1]),
      [EXPORTER_BUILD_RESOURCE_PATH]: canonicalExporterBuildIdentityBytes({
        format: EXPORTER_BUILD_FORMAT,
        exporterId: 'forge-rei-1.18.2',
        minecraftVersion: '1.18.2',
        algorithm: EXPORTER_BUILD_ALGORITHM,
        payloadSha256: '0'.repeat(64),
      }),
    }),
  );
  assert.throws(() => inspectExporterJarBuild(unsafe), /unsafe ZIP entry path/);

  for (const name of ['.', './', './entry.class', 'a//b.class', 'a/../b.class', '\\entry.class']) {
    assert.throws(
      () => canonicalExporterPayloadSha256([[name, Uint8Array.from([1])]]),
      /unsafe ZIP entry path/,
      `expected ${JSON.stringify(name)} to be rejected`,
    );
  }
});
