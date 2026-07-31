package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExporterBuildIdentityTest {
    private static final String ZERO_SHA256 =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @TempDir
    Path temporary;

    @Test
    void canonicalPayloadIgnoresZipOrderMetadataAndIdentityEntry() throws Exception {
        List<EntryData> payload = payload();
        Path first = temporary.resolve("first.jar");
        writeJar(first, payload, null, 10L);

        List<EntryData> reversed = new ArrayList<>(payload);
        Collections.reverse(reversed);
        Path second = temporary.resolve("second.jar");
        writeJar(second, reversed, ExporterBuildIdentity.canonicalBytes(ZERO_SHA256), 99L);

        assertEquals(
                ExporterBuildIdentity.payloadSha256(first),
                ExporterBuildIdentity.payloadSha256(second));
        assertTrue(
                ExporterBuildIdentity.compareUtf8("\ue000.txt", "\ud800\udc00.txt") < 0 &&
                        "\ue000.txt".compareTo("\ud800\udc00.txt") > 0,
                "unsigned UTF-8 ordering must differ from UTF-16 ordering for this pair");
    }

    @Test
    void verifiesAndPublishesByteIdenticalCanonicalIdentity() throws Exception {
        List<EntryData> payload = payload();
        Path unsigned = temporary.resolve("unsigned.jar");
        writeJar(unsigned, payload, null, 1L);
        String digest = ExporterBuildIdentity.payloadSha256(unsigned);
        byte[] canonical = ExporterBuildIdentity.canonicalBytes(digest);
        assertEquals(
                "{\"format\":\"mrt-exporter-build-v1\",\"exporterId\":\"forge-rei-1.18.2\"," +
                        "\"minecraftVersion\":\"1.18.2\",\"algorithm\":\"sha256\"," +
                        "\"payloadSha256\":\"" + digest + "\"}\n",
                new String(canonical, StandardCharsets.UTF_8));

        Path signed = temporary.resolve("verified.jar");
        writeJar(signed, payload, canonical, 2L);
        ExporterBuildIdentity identity = ExporterBuildIdentity.readAndVerify(signed);
        assertEquals(digest, identity.payloadSha256());
        assertArrayEquals(canonical, identity.canonicalBytes());

        Path output = Files.createDirectory(temporary.resolve("export"));
        identity.writeTo(output);
        assertArrayEquals(canonical, Files.readAllBytes(output.resolve("exporter-build.json")));
        identity.writeTo(output);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(output)) {
            int count = 0;
            for (Path ignored : entries) {
                count++;
            }
            assertEquals(1, count, "atomic identity publication must not retain a temporary file");
        }

        Files.writeString(
                output.resolve("exporter-build.json"),
                "tampered\n",
                StandardOpenOption.TRUNCATE_EXISTING);
        assertThrows(IOException.class, () -> identity.writeTo(output));
    }

    @Test
    void rejectsPayloadDriftNoncanonicalRecordsAndDevelopmentDirectories() throws Exception {
        List<EntryData> payload = payload();
        Path original = temporary.resolve("original.jar");
        writeJar(original, payload, null, 1L);
        String digest = ExporterBuildIdentity.payloadSha256(original);

        List<EntryData> changed = new ArrayList<>(payload);
        changed.set(0, new EntryData(changed.get(0).name, "changed"));
        Path drifted = temporary.resolve("drifted.jar");
        writeJar(drifted, changed, ExporterBuildIdentity.canonicalBytes(digest), 1L);
        assertThrows(IOException.class, () -> ExporterBuildIdentity.readAndVerify(drifted));

        Path noncanonical = temporary.resolve("noncanonical.jar");
        byte[] pretty = ("{ \"format\": \"mrt-exporter-build-v1\", " +
                "\"exporterId\": \"forge-rei-1.18.2\", \"minecraftVersion\": \"1.18.2\", " +
                "\"algorithm\": \"sha256\", \"payloadSha256\": \"" + digest + "\" }\n")
                .getBytes(StandardCharsets.UTF_8);
        writeJar(noncanonical, payload, pretty, 1L);
        assertThrows(IOException.class, () -> ExporterBuildIdentity.readAndVerify(noncanonical));

        Path wrongVersion = temporary.resolve("wrong-version.jar");
        byte[] wrongVersionIdentity = ("{\"format\":\"mrt-exporter-build-v1\"," +
                "\"exporterId\":\"forge-rei-1.18.2\",\"minecraftVersion\":\"1.12.2\"," +
                "\"algorithm\":\"sha256\",\"payloadSha256\":\"" + digest + "\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        writeJar(wrongVersion, payload, wrongVersionIdentity, 1L);
        assertThrows(IOException.class, () -> ExporterBuildIdentity.readAndVerify(wrongVersion));
        assertThrows(
                IOException.class,
                () -> ExporterBuildIdentity.readAndVerify(Files.createDirectory(temporary.resolve("classes"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExporterBuildIdentity.canonicalBytes("A" + digest.substring(1)));
    }

    @Test
    void rejectsNoncanonicalZipEntryPathsBeforeHashing() throws Exception {
        List<String> invalidNames = Arrays.asList(
                ".", "./", "./entry.class", "a//b.class", "a/../b.class", "\\entry.class");
        for (int index = 0; index < invalidNames.size(); index++) {
            Path jar = temporary.resolve("unsafe-" + index + ".jar");
            writeJar(
                    jar,
                    Collections.singletonList(new EntryData(invalidNames.get(index), "unsafe")),
                    null,
                    1L);
            assertThrows(IOException.class, () -> ExporterBuildIdentity.payloadSha256(jar));
        }
    }

    private static List<EntryData> payload() {
        return Arrays.asList(
                new EntryData("z.txt", "last"),
                new EntryData("\ud800\udc00.txt", "supplementary"),
                new EntryData("META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\r\nImplementation-Title: Fixture\r\n\r\n"),
                new EntryData("\ue000.txt", "private-use"),
                new EntryData("a.txt", "first"));
    }

    private static void writeJar(
            Path path, List<EntryData> payload, byte[] identity, long timestamp) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (EntryData value : payload) {
                ZipEntry entry = new ZipEntry(value.name);
                entry.setTime(timestamp);
                output.putNextEntry(entry);
                output.write(value.value.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            if (identity != null) {
                ZipEntry entry = new ZipEntry(ExporterBuildIdentity.RESOURCE_PATH);
                entry.setTime(timestamp);
                output.putNextEntry(entry);
                output.write(identity);
                output.closeEntry();
            }
        }
    }

    private static final class EntryData {
        final String name;
        final String value;

        EntryData(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
