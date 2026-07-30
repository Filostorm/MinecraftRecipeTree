package com.recipetree.reiexport118;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Integrity-bound identity for the exact reobfuscated exporter payload.
 *
 * <p>The full JAR cannot contain its own SHA-256 without a circular dependency. Instead, the
 * build hashes a canonical stream of every non-directory entry except the identity record itself.
 * ZIP ordering, timestamps, and compression are deliberately excluded; every payload path and
 * uncompressed byte remains covered.</p>
 */
final class ExporterBuildIdentity {
    static final String RESOURCE_PATH = "META-INF/mrt-exporter-build.json";
    static final String OUTPUT_FILE_NAME = "exporter-build.json";
    static final String FORMAT = "mrt-exporter-build-v1";
    static final String EXPORTER_ID = "forge-rei-1.18.2";
    static final String MINECRAFT_VERSION = "1.18.2";
    static final String ALGORITHM = "sha256";
    static final String PAYLOAD_FORMAT = "mrt-exporter-jar-payload-v1";

    private static final byte[] PAYLOAD_PREFIX =
            (PAYLOAD_FORMAT + '\0').getBytes(StandardCharsets.UTF_8);
    private static final int MAX_ENTRY_COUNT = 4096;
    private static final long MAX_PAYLOAD_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_IDENTITY_BYTES = 512;

    private final String payloadSha256;
    private final byte[] canonicalBytes;

    private ExporterBuildIdentity(String payloadSha256, byte[] canonicalBytes) {
        this.payloadSha256 = payloadSha256;
        this.canonicalBytes = canonicalBytes;
    }

    static ExporterBuildIdentity loadRuntime() throws IOException {
        IModFileInfo modFile = ModList.get().getModFileById(ReiExportMod.MOD_ID);
        if (modFile == null || modFile.getFile() == null) {
            throw new IOException("Exporter mod file is unavailable; build provenance cannot be resolved");
        }
        return readAndVerify(modFile.getFile().getFilePath());
    }

    static ExporterBuildIdentity readAndVerify(Path jarPath) throws IOException {
        Path normalized = requirePlainJar(jarPath);
        BasicFileAttributes before = attributes(normalized);
        byte[] declaredIdentity = null;
        String computedPayloadSha256;

        try (JarFile jar = new JarFile(normalized.toFile(), false)) {
            List<JarEntry> payloadEntries = new ArrayList<>();
            Set<String> names = new HashSet<>();
            Enumeration<JarEntry> enumeration = jar.entries();
            int entryCount = 0;
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("Exporter JAR exceeds the " + MAX_ENTRY_COUNT + " entry provenance bound");
                }
                requireCanonicalEntryName(entry.getName());
                if (!names.add(entry.getName())) {
                    throw new IOException("Exporter JAR contains duplicate entry " + entry.getName());
                }
                if (RESOURCE_PATH.equals(entry.getName())) {
                    if (entry.isDirectory()) {
                        throw new IOException("Exporter build identity entry is a directory");
                    }
                    declaredIdentity = readEntry(jar, entry, MAX_IDENTITY_BYTES);
                } else if (!entry.isDirectory()) {
                    payloadEntries.add(entry);
                }
            }
            if (declaredIdentity == null) {
                throw new IOException(
                        "Exporter JAR lacks " + RESOURCE_PATH + "; development outputs are not publishable");
            }
            computedPayloadSha256 = digestPayload(jar, payloadEntries);
        }

        BasicFileAttributes after = attributes(normalized);
        if (!sameFileSnapshot(before, after)) {
            throw new IOException("Exporter JAR changed while build provenance was verified");
        }

        byte[] expectedIdentity = canonicalBytes(computedPayloadSha256);
        if (!Arrays.equals(declaredIdentity, expectedIdentity)) {
            throw new IOException(
                    "Exporter build identity is not canonical or does not match the JAR payload SHA-256");
        }
        return new ExporterBuildIdentity(computedPayloadSha256, expectedIdentity);
    }

    static String payloadSha256(Path jarPath) throws IOException {
        Path normalized = requirePlainJar(jarPath);
        try (JarFile jar = new JarFile(normalized.toFile(), false)) {
            List<JarEntry> payloadEntries = new ArrayList<>();
            Set<String> names = new HashSet<>();
            Enumeration<JarEntry> enumeration = jar.entries();
            int entryCount = 0;
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("Exporter JAR exceeds the provenance entry bound");
                }
                requireCanonicalEntryName(entry.getName());
                if (!names.add(entry.getName())) {
                    throw new IOException("Exporter JAR contains duplicate entry " + entry.getName());
                }
                if (!entry.isDirectory() && !RESOURCE_PATH.equals(entry.getName())) {
                    payloadEntries.add(entry);
                }
            }
            return digestPayload(jar, payloadEntries);
        }
    }

    String payloadSha256() {
        return payloadSha256;
    }

    byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    void writeTo(Path exportRoot) throws IOException {
        if (Files.isSymbolicLink(exportRoot) ||
                !Files.isDirectory(exportRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Export root is not a plain directory: " + exportRoot);
        }
        Path destination = exportRoot.resolve(OUTPUT_FILE_NAME);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            requireExistingIdentity(destination);
            return;
        }
        Path temporary = exportRoot.resolve(
                "." + OUTPUT_FILE_NAME + "." + UUID.randomUUID().toString() + ".tmp");
        IOException primaryFailure = null;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(canonicalBytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException alreadyExists) {
                requireExistingIdentity(destination);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException(
                        "Atomic exporter-build.json publication is unavailable; no fallback was attempted",
                        unsupported);
            }
        } catch (IOException failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private void requireExistingIdentity(Path destination) throws IOException {
        if (Files.isSymbolicLink(destination) ||
                !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) ||
                Files.size(destination) != canonicalBytes.length ||
                !Arrays.equals(Files.readAllBytes(destination), canonicalBytes)) {
            throw new IOException(
                    "Existing exporter-build.json does not match the verified exporter JAR");
        }
    }

    static byte[] canonicalBytes(String payloadSha256) {
        if (payloadSha256 == null || !payloadSha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("payloadSha256 must be lowercase SHA-256");
        }
        String value = "{\"format\":\"" + FORMAT + "\",\"exporterId\":\"" + EXPORTER_ID +
                "\",\"minecraftVersion\":\"" + MINECRAFT_VERSION +
                "\",\"algorithm\":\"" + ALGORITHM +
                "\",\"payloadSha256\":\"" + payloadSha256 + "\"}\n";
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Path requirePlainJar(Path jarPath) throws IOException {
        if (jarPath == null) {
            throw new IOException("Exporter source JAR path is unavailable");
        }
        Path normalized = jarPath.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized) ||
                !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "Exporter source must be a plain JAR file; no development-directory fallback was attempted: " +
                            normalized);
        }
        return normalized;
    }

    private static void requireCanonicalEntryName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\\') >= 0 ||
                name.indexOf('\0') >= 0 || name.charAt(0) == '/') {
            throw new IOException("Exporter JAR contains an unsafe ZIP entry path: " + name);
        }
        String[] segments = name.split("/", -1);
        int contentSegments = name.endsWith("/") ? segments.length - 1 : segments.length;
        if (contentSegments <= 0) {
            throw new IOException("Exporter JAR contains an unsafe ZIP entry path: " + name);
        }
        for (int index = 0; index < contentSegments; index++) {
            String segment = segments[index];
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Exporter JAR contains an unsafe ZIP entry path: " + name);
            }
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean sameFileSnapshot(BasicFileAttributes left, BasicFileAttributes right) {
        return left.isRegularFile() && right.isRegularFile() &&
                left.size() == right.size() &&
                left.lastModifiedTime().equals(right.lastModifiedTime()) &&
                Objects.equals(left.fileKey(), right.fileKey());
    }

    private static String digestPayload(JarFile jar, List<JarEntry> entries) throws IOException {
        Collections.sort(entries, (left, right) -> compareUtf8(left.getName(), right.getName()));
        MessageDigest digest = sha256();
        digest.update(PAYLOAD_PREFIX);
        long totalBytes = 0L;
        byte[] buffer = new byte[8192];
        for (JarEntry entry : entries) {
            byte[] path = entry.getName().getBytes(StandardCharsets.UTF_8);
            long size = entry.getSize();
            if (size < 0L) {
                throw new IOException("Exporter JAR entry has no uncompressed size: " + entry.getName());
            }
            try {
                totalBytes = Math.addExact(totalBytes, size);
            } catch (ArithmeticException overflow) {
                throw new IOException("Exporter JAR payload size overflowed", overflow);
            }
            if (totalBytes > MAX_PAYLOAD_BYTES) {
                throw new IOException("Exporter JAR exceeds the provenance payload byte bound");
            }
            updateUInt32(digest, path.length);
            digest.update(path);
            updateUInt64(digest, size);
            long readBytes = 0L;
            try (InputStream input = jar.getInputStream(entry)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    digest.update(buffer, 0, read);
                    readBytes += read;
                }
            }
            if (readBytes != size) {
                throw new IOException("Exporter JAR entry size changed while hashing: " + entry.getName());
            }
        }
        return hex(digest.digest());
    }

    private static byte[] readEntry(JarFile jar, JarEntry entry, int maximumBytes) throws IOException {
        long declaredSize = entry.getSize();
        if (declaredSize < 1L || declaredSize > maximumBytes) {
            throw new IOException("Exporter build identity resource has an invalid byte length");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) declaredSize);
        byte[] buffer = new byte[512];
        try (InputStream input = jar.getInputStream(entry)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                if (output.size() + read > maximumBytes) {
                    throw new IOException("Exporter build identity resource exceeds its byte bound");
                }
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", impossible);
        }
    }

    static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int shared = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < shared; index++) {
            int comparison = Integer.compare(leftBytes[index] & 0xff, rightBytes[index] & 0xff);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }

    private static void updateUInt32(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateUInt64(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }
}
