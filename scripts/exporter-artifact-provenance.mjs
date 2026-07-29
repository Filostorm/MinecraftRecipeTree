import {createHash} from 'node:crypto';
import {unzipSync} from 'fflate';

export const EXPORTER_BUILD_FORMAT = 'mrt-exporter-build-v1';
export const EXPORTER_BUILD_ALGORITHM = 'sha256';
export const EXPORTER_BUILD_RESOURCE_PATH = 'META-INF/mrt-exporter-build.json';
export const EXPORTER_BUILD_EXPORT_PATH = 'exporter-build.json';
export const EXPORTER_PAYLOAD_DIGEST_DOMAIN = 'mrt-exporter-jar-payload-v1\0';

const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const EXPORTER_ID_PATTERN = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/;
const MINECRAFT_VERSION_PATTERN = /^[0-9]+(?:\.[0-9]+){1,2}$/;
const ZIP_EOCD_SIGNATURE = 0x06054b50;
const ZIP_CENTRAL_SIGNATURE = 0x02014b50;
const MAX_ZIP_ENTRIES = 20_000;
const MAX_ZIP_ENTRY_BYTES = 32 * 1024 * 1024;
const MAX_ZIP_TOTAL_BYTES = 64 * 1024 * 1024;
const MAX_BUILD_RESOURCE_BYTES = 4 * 1024;
const textDecoder = new TextDecoder('utf-8', {fatal: true});

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value, expected) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function findEndOfCentralDirectory(bytes) {
  const minimum = Math.max(0, bytes.length - 65_557);
  for (let offset = bytes.length - 22; offset >= minimum; offset -= 1) {
    if (bytes.readUInt32LE(offset) !== ZIP_EOCD_SIGNATURE) continue;
    const commentLength = bytes.readUInt16LE(offset + 20);
    if (offset + 22 + commentLength === bytes.length) return offset;
  }
  throw new Error('Exporter JAR has no canonical ZIP end-of-central-directory record.');
}

function requireCanonicalZipPath(name) {
  const segments = name.split('/');
  const contentSegments = name.endsWith('/') ? segments.length - 1 : segments.length;
  const hasNoncanonicalSegment =
    contentSegments <= 0 ||
    segments
      .slice(0, contentSegments)
      .some(segment => segment.length === 0 || segment === '.' || segment === '..');
  if (
    name.length === 0 ||
    name.includes('\\') ||
    name.includes('\0') ||
    name.startsWith('/') ||
    hasNoncanonicalSegment
  ) {
    throw new Error(`Exporter JAR contains an unsafe ZIP entry path: ${JSON.stringify(name)}.`);
  }
}

/**
 * Bound decompression before fflate sees the archive and reject duplicate/unsafe ZIP identities.
 */
function inspectCentralDirectory(bytes) {
  const eocd = findEndOfCentralDirectory(bytes);
  const disk = bytes.readUInt16LE(eocd + 4);
  const centralDisk = bytes.readUInt16LE(eocd + 6);
  const diskEntries = bytes.readUInt16LE(eocd + 8);
  const totalEntries = bytes.readUInt16LE(eocd + 10);
  const centralBytes = bytes.readUInt32LE(eocd + 12);
  const centralOffset = bytes.readUInt32LE(eocd + 16);
  if (
    disk !== 0 ||
    centralDisk !== 0 ||
    diskEntries !== totalEntries ||
    totalEntries > MAX_ZIP_ENTRIES ||
    totalEntries === 0 ||
    centralOffset + centralBytes !== eocd
  ) {
    throw new Error('Exporter JAR uses an unsupported split, ZIP64, empty, or noncanonical ZIP layout.');
  }

  const entries = new Map();
  let totalUncompressedBytes = 0;
  let offset = centralOffset;
  for (let index = 0; index < totalEntries; index += 1) {
    if (offset + 46 > eocd || bytes.readUInt32LE(offset) !== ZIP_CENTRAL_SIGNATURE) {
      throw new Error(`Exporter JAR central-directory entry ${index} is malformed.`);
    }
    const flags = bytes.readUInt16LE(offset + 8);
    const method = bytes.readUInt16LE(offset + 10);
    const compressedBytes = bytes.readUInt32LE(offset + 20);
    const uncompressedBytes = bytes.readUInt32LE(offset + 24);
    const nameBytes = bytes.readUInt16LE(offset + 28);
    const extraBytes = bytes.readUInt16LE(offset + 30);
    const commentBytes = bytes.readUInt16LE(offset + 32);
    const localOffset = bytes.readUInt32LE(offset + 42);
    const end = offset + 46 + nameBytes + extraBytes + commentBytes;
    if (
      end > eocd ||
      (flags & 0x1) !== 0 ||
      ![0, 8].includes(method) ||
      compressedBytes === 0xffffffff ||
      uncompressedBytes === 0xffffffff ||
      localOffset === 0xffffffff ||
      localOffset >= centralOffset ||
      uncompressedBytes > MAX_ZIP_ENTRY_BYTES
    ) {
      throw new Error(`Exporter JAR central-directory entry ${index} exceeds the safe ZIP contract.`);
    }
    let name;
    try {
      name = textDecoder.decode(bytes.subarray(offset + 46, offset + 46 + nameBytes));
    } catch (error) {
      throw new Error(`Exporter JAR entry ${index} has a non-UTF-8 name.`, {cause: error});
    }
    requireCanonicalZipPath(name);
    if (entries.has(name)) {
      throw new Error(`Exporter JAR repeats ZIP entry ${JSON.stringify(name)}.`);
    }
    totalUncompressedBytes += uncompressedBytes;
    if (totalUncompressedBytes > MAX_ZIP_TOTAL_BYTES) {
      throw new Error('Exporter JAR exceeds the bounded uncompressed payload size.');
    }
    entries.set(name, Object.freeze({uncompressedBytes, directory: name.endsWith('/')}));
    offset = end;
  }
  if (offset !== eocd) {
    throw new Error('Exporter JAR central-directory byte count is inconsistent.');
  }
  return entries;
}

function uint64be(value) {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error('Invalid payload entry length.');
  const result = Buffer.allocUnsafe(8);
  result.writeBigUInt64BE(BigInt(value));
  return result;
}

export function canonicalExporterPayloadSha256(payloadEntries) {
  if (!Array.isArray(payloadEntries)) {
    throw new Error('Exporter payload entries must be an array.');
  }
  const entries = payloadEntries
    .map(entry => {
      if (!Array.isArray(entry) || entry.length !== 2 || typeof entry[0] !== 'string') {
        throw new Error('Exporter payload entry violates the [path, bytes] contract.');
      }
      requireCanonicalZipPath(entry[0]);
      if (entry[0].endsWith('/') || entry[0] === EXPORTER_BUILD_RESOURCE_PATH) {
        throw new Error(`Exporter payload digest received excluded entry ${entry[0]}.`);
      }
      return [entry[0], Buffer.from(entry[1])];
    })
    .sort(([left], [right]) =>
      Buffer.compare(Buffer.from(left, 'utf8'), Buffer.from(right, 'utf8')),
    );
  if (new Set(entries.map(([name]) => name)).size !== entries.length) {
    throw new Error('Exporter payload digest received duplicate entry names.');
  }
  const hash = createHash('sha256');
  hash.update(EXPORTER_PAYLOAD_DIGEST_DOMAIN, 'utf8');
  for (const [name, content] of entries) {
    const pathBytes = Buffer.from(name, 'utf8');
    const pathLength = Buffer.allocUnsafe(4);
    pathLength.writeUInt32BE(pathBytes.length);
    hash.update(pathLength);
    hash.update(pathBytes);
    hash.update(uint64be(content.length));
    hash.update(content);
  }
  return hash.digest('hex');
}

export function requireExporterBuildIdentity(value) {
  if (
    !hasExactKeys(value, [
      'algorithm',
      'exporterId',
      'format',
      'minecraftVersion',
      'payloadSha256',
    ])
  ) {
    throw new Error('Exporter build identity violates the exact contract.');
  }
  if (value.format !== EXPORTER_BUILD_FORMAT || value.algorithm !== EXPORTER_BUILD_ALGORITHM) {
    throw new Error('Exporter build identity uses an unsupported format or algorithm.');
  }
  if (
    typeof value.exporterId !== 'string' ||
    value.exporterId.length > 80 ||
    !EXPORTER_ID_PATTERN.test(value.exporterId)
  ) {
    throw new Error('Exporter build identity exporterId must be a canonical lowercase ID.');
  }
  if (
    typeof value.minecraftVersion !== 'string' ||
    value.minecraftVersion.length > 40 ||
    !MINECRAFT_VERSION_PATTERN.test(value.minecraftVersion)
  ) {
    throw new Error('Exporter build identity minecraftVersion must be a canonical release version.');
  }
  if (typeof value.payloadSha256 !== 'string' || !SHA256_PATTERN.test(value.payloadSha256)) {
    throw new Error('Exporter build identity payloadSha256 must be lowercase SHA-256.');
  }
  return Object.freeze({
    format: EXPORTER_BUILD_FORMAT,
    exporterId: value.exporterId,
    minecraftVersion: value.minecraftVersion,
    algorithm: EXPORTER_BUILD_ALGORITHM,
    payloadSha256: value.payloadSha256,
  });
}

export function canonicalExporterBuildIdentityBytes(identity) {
  const validated = requireExporterBuildIdentity(identity);
  return Buffer.from(`${JSON.stringify(validated)}\n`, 'utf8');
}

export function parseExporterBuildIdentityBytes(bytes, label = 'Exporter build identity') {
  let parsed;
  try {
    parsed = JSON.parse(Buffer.from(bytes).toString('utf8'));
  } catch (error) {
    throw new Error(`${label} is not valid JSON.`, {cause: error});
  }
  const identity = requireExporterBuildIdentity(parsed);
  if (!Buffer.from(bytes).equals(canonicalExporterBuildIdentityBytes(identity))) {
    throw new Error(`${label} is not in the canonical byte representation.`);
  }
  return identity;
}

export function inspectExporterJarBuild(jarBytes) {
  const bytes = Buffer.from(jarBytes);
  const centralEntries = inspectCentralDirectory(bytes);
  if (!centralEntries.has(EXPORTER_BUILD_RESOURCE_PATH)) {
    throw new Error(`Exporter JAR is missing ${EXPORTER_BUILD_RESOURCE_PATH}.`);
  }
  let unpacked;
  try {
    unpacked = unzipSync(new Uint8Array(bytes));
  } catch (error) {
    throw new Error('Exporter JAR could not be decompressed safely.', {cause: error});
  }
  const unpackedNames = Object.keys(unpacked).sort((left, right) =>
    Buffer.compare(Buffer.from(left, 'utf8'), Buffer.from(right, 'utf8')),
  );
  if (
    unpackedNames.length !== centralEntries.size ||
    unpackedNames.some(name => !centralEntries.has(name))
  ) {
    throw new Error('Exporter JAR decompressed inventory differs from its central directory.');
  }

  const payloadEntries = [];
  for (const name of unpackedNames) {
    const metadata = centralEntries.get(name);
    const content = Buffer.from(unpacked[name]);
    if (content.length !== metadata.uncompressedBytes) {
      throw new Error(`Exporter JAR entry ${JSON.stringify(name)} has an inconsistent size.`);
    }
    if (!metadata.directory && name !== EXPORTER_BUILD_RESOURCE_PATH) {
      payloadEntries.push([name, content]);
    }
  }
  const resourceBytes = Buffer.from(unpacked[EXPORTER_BUILD_RESOURCE_PATH]);
  if (resourceBytes.length < 2 || resourceBytes.length > MAX_BUILD_RESOURCE_BYTES) {
    throw new Error('Exporter build identity resource is outside its byte bound.');
  }
  const identity = parseExporterBuildIdentityBytes(
    resourceBytes,
    'Embedded exporter build identity',
  );
  const payloadSha256 = canonicalExporterPayloadSha256(payloadEntries);
  if (payloadSha256 !== identity.payloadSha256) {
    throw new Error(
      `Exporter JAR payload digest ${payloadSha256} does not match its embedded build identity ${identity.payloadSha256}.`,
    );
  }
  return Object.freeze({identity, resourceBytes, payloadEntries: payloadEntries.length});
}

export function requireMatchingExportedBuildIdentity(exportedBytes, jarBuild) {
  const identity = parseExporterBuildIdentityBytes(
    exportedBytes,
    'Exported exporter-build.json',
  );
  if (!Buffer.from(exportedBytes).equals(jarBuild.resourceBytes)) {
    throw new Error(
      'Exported exporter-build.json is not byte-identical to the exact source JAR build identity.',
    );
  }
  return identity;
}
