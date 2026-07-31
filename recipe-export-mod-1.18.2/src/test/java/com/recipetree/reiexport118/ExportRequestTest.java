package com.recipetree.reiexport118;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExportRequestTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesStrictMiniRequestAndNativeIconScale() throws Exception {
        Path requestPath = temporaryDirectory.resolve("request.json");
        Files.writeString(requestPath, """
                {
                  "profile":"multiblock-madness-2-1.18.2",
                  "packName":"Multiblock Madness 2",
                  "packVersion":"1.0.0",
                  "output":"reiexport-mini-v1",
                  "qualitySample":[
                    {"categoryId":"minecraft:plugins/crafting","sourceIndex":0},
                    {"categoryId":"multiblocked:test","sourceIndex":4}
                  ],
                  "qualityItemSample":[
                    {"typeId":"minecraft:item","identifier":"mekanism:bounding_block"}
                  ]
                }
                """);

        ExportRequest request = ExportRequest.read(requestPath);
        assertEquals(1, request.iconScale);
        assertEquals(2, request.recipeScale);
        assertEquals("reiexport-mini-v1", request.output);
        assertEquals(2, request.qualitySample.size());
        assertEquals(1, request.qualityItemSample.size());
        assertTrue(request.isQualitySample());
    }

    @Test
    void parsesAndEnforcesAnExactRegistryCensusContract() throws Exception {
        String categoryDigest = "1".repeat(64);
        String entryDigest = "2".repeat(64);
        Path requestPath = temporaryDirectory.resolve("census.json");
        Files.writeString(requestPath, """
                {
                  "profile":"multiblock-madness-2-1.18.2",
                  "packName":"Multiblock Madness 2",
                  "packVersion":"1.0.0",
                  "output":"reiexport-full-v1",
                  "expectedRegistryCensus":{
                    "entries":27637,
                    "displays":99230,
                    "categories":348,
                    "categoryCountsSha256":"%s",
                    "entryIdentitiesSha256":"%s"
                  }
                }
                """.formatted(categoryDigest, entryDigest));

        ExportRequest request = ExportRequest.read(requestPath);
        RegistryCensus.Counts counts = new RegistryCensus.Counts(
                27637, 99230, 348, categoryDigest, java.util.Map.of());
        request.requireExpectedRegistryCensus(new RegistryCensus.Deep(counts, 1, entryDigest));

        assertThrows(
                IllegalStateException.class,
                () -> request.requireExpectedRegistryCensus(new RegistryCensus.Deep(
                        new RegistryCensus.Counts(
                                27637, 99229, 348, categoryDigest, java.util.Map.of()),
                        1,
                        entryDigest)));
    }

    @Test
    void rejectsMalformedOrPartialRegistryCensusContracts() throws Exception {
        for (String census : new String[]{
                "{\"entries\":1,\"displays\":2,\"categories\":3,"
                        + "\"categoryCountsSha256\":\"short\","
                        + "\"entryIdentitiesSha256\":\"" + "2".repeat(64) + "\"}",
                "{\"entries\":1,\"displays\":2,\"categories\":3,"
                        + "\"categoryCountsSha256\":\"" + "1".repeat(64) + "\"}",
                "{\"entries\":1,\"displays\":2,\"categories\":3,"
                        + "\"categoryCountsSha256\":\"" + "1".repeat(64) + "\","
                        + "\"entryIdentitiesSha256\":\"" + "2".repeat(64) + "\","
                        + "\"fallback\":true}"
        }) {
            Path requestPath = requestWithTail(",\"expectedRegistryCensus\":" + census);
            assertThrows(java.io.IOException.class, () -> ExportRequest.read(requestPath));
        }
    }

    @Test
    void rejectsUnknownFieldsInsteadOfIgnoringThem() throws Exception {
        Path requestPath = requestWithTail(",\"fallback\":true");
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(requestPath));
    }

    @Test
    void rejectsWrongProfileAndEscapingOutput() throws Exception {
        Path wrongProfile = temporaryDirectory.resolve("wrong-profile.json");
        Files.writeString(wrongProfile, baseRequest("other-profile", "output"));
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(wrongProfile));

        Path escaping = temporaryDirectory.resolve("escaping.json");
        Files.writeString(escaping, baseRequest("multiblock-madness-2-1.18.2", "../outside"));
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(escaping));
    }

    @Test
    void rejectsDuplicateSampleSelectors() throws Exception {
        Path requestPath = temporaryDirectory.resolve("duplicate.json");
        Files.writeString(requestPath, """
                {
                  "profile":"multiblock-madness-2-1.18.2",
                  "packName":"Multiblock Madness 2",
                  "packVersion":"1.0.0",
                  "output":"sample",
                  "qualitySample":[
                    {"categoryId":"minecraft:crafting","sourceIndex":0},
                    {"categoryId":"minecraft:crafting","sourceIndex":0}
                  ]
                }
                """);
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(requestPath));
    }

    @Test
    void rejectsDuplicateAndMalformedItemSampleSelectors() throws Exception {
        Path duplicate = temporaryDirectory.resolve("duplicate-item.json");
        Files.writeString(duplicate, """
                {
                  "profile":"multiblock-madness-2-1.18.2",
                  "packName":"Multiblock Madness 2",
                  "packVersion":"1.0.0",
                  "output":"sample",
                  "qualityItemSample":[
                    {"typeId":"minecraft:item","identifier":"mekanism:bounding_block"},
                    {"typeId":"minecraft:item","identifier":"mekanism:bounding_block"}
                  ]
                }
                """);
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(duplicate));

        Path malformed = temporaryDirectory.resolve("malformed-item.json");
        Files.writeString(malformed, """
                {
                  "profile":"multiblock-madness-2-1.18.2",
                  "packName":"Multiblock Madness 2",
                  "packVersion":"1.0.0",
                  "output":"sample",
                  "qualityItemSample":[
                    {"typeId":"not a resource location","identifier":"mekanism:bounding_block"}
                  ]
                }
                """);
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(malformed));
    }

    @Test
    void rejectsFailureTolerantPublicationMode() throws Exception {
        Path requestPath = requestWithTail(",\"failOnError\":false");
        java.io.IOException exception = assertThrows(
                java.io.IOException.class,
                () -> ExportRequest.read(requestPath));
        assertTrue(exception.getMessage().contains("requires failOnError=true"));
    }

    @Test
    void rejectsManualWorldEntryWithoutAnOwnedLogoutProtocol() throws Exception {
        Path requestPath = requestWithTail(",\"createWorld\":false");
        java.io.IOException exception = assertThrows(
                java.io.IOException.class,
                () -> ExportRequest.read(requestPath));
        assertTrue(exception.getMessage().contains("requires createWorld=true"));
    }

    @Test
    void rejectsOversizedControlAndBidirectionalPackIdentityText() throws Exception {
        String oversized = "x".repeat(ExportRequest.MAX_PACK_NAME_CODE_POINTS + 1);
        for (String packName : new String[]{oversized, "Unsafe\nName", "Visual\u202eSpoof"}) {
            Path requestPath = temporaryDirectory.resolve("unsafe-" + Math.abs(packName.hashCode()) + ".json");
            Files.writeString(requestPath, baseRequest(
                    "multiblock-madness-2-1.18.2", "output", packName, "1.0.0"));
            assertThrows(java.io.IOException.class, () -> ExportRequest.read(requestPath));
        }

        String oversizedVersion = "v".repeat(ExportRequest.MAX_PACK_VERSION_CODE_POINTS + 1);
        Path requestPath = temporaryDirectory.resolve("unsafe-version.json");
        Files.writeString(requestPath, baseRequest(
                "multiblock-madness-2-1.18.2", "output", "Safe pack", oversizedVersion));
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(requestPath));
    }

    @Test
    void countsPackIdentityLimitsInUnicodeCodePoints() throws Exception {
        String name = "🧱".repeat(ExportRequest.MAX_PACK_NAME_CODE_POINTS);
        String version = "🚀".repeat(ExportRequest.MAX_PACK_VERSION_CODE_POINTS);
        Path boundary = temporaryDirectory.resolve("unicode-boundary.json");
        Files.writeString(boundary, baseRequest(
                "multiblock-madness-2-1.18.2", "output", name, version));

        ExportRequest request = ExportRequest.read(boundary);

        assertEquals(ExportRequest.MAX_PACK_NAME_CODE_POINTS,
                request.packName.codePointCount(0, request.packName.length()));
        assertEquals(ExportRequest.MAX_PACK_VERSION_CODE_POINTS,
                request.packVersion.codePointCount(0, request.packVersion.length()));

        Path oversizedName = temporaryDirectory.resolve("unicode-name-oversized.json");
        Files.writeString(oversizedName, baseRequest(
                "multiblock-madness-2-1.18.2", "output", name + "🧱", version));
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(oversizedName));

        Path oversizedVersion = temporaryDirectory.resolve("unicode-version-oversized.json");
        Files.writeString(oversizedVersion, baseRequest(
                "multiblock-madness-2-1.18.2", "output", name, version + "🚀"));
        assertThrows(java.io.IOException.class, () -> ExportRequest.read(oversizedVersion));
    }

    @Test
    void rejectsCanonicalControlBidiAndZeroWidthRanges() throws Exception {
        int[] unsafeCodePoints = {
                0x0000, 0x001f, 0x007f, 0x009f, 0x061c,
                0x200b, 0x200c, 0x200d, 0x200e, 0x200f,
                0x202a, 0x202e, 0x2060, 0x2065, 0x2069, 0xfeff
        };
        for (int codePoint : unsafeCodePoints) {
            String unsafe = new String(Character.toChars(codePoint));
            Path requestPath = temporaryDirectory.resolve(
                    "unsafe-code-point-" + Integer.toHexString(codePoint) + ".json");
            Files.writeString(requestPath, baseRequest(
                    "multiblock-madness-2-1.18.2", "output", "Safe" + unsafe + "Pack", "1"));
            java.io.IOException error = assertThrows(
                    java.io.IOException.class,
                    () -> ExportRequest.read(requestPath),
                    () -> "Expected U+" + Integer.toHexString(codePoint).toUpperCase()
                            + " to be rejected");
            assertTrue(error.getMessage().contains("control, bidirectional, or zero-width"));
        }
    }

    private Path requestWithTail(String tail) throws Exception {
        Path path = temporaryDirectory.resolve("tail.json");
        String base = baseRequest("multiblock-madness-2-1.18.2", "output");
        Files.writeString(path, base.substring(0, base.length() - 1) + tail + "}");
        return path;
    }

    private static String baseRequest(String profile, String output) {
        return baseRequest(profile, output, "Multiblock Madness 2", "1.0.0");
    }

    private static String baseRequest(
            String profile, String output, String packName, String packVersion) {
        JsonObject object = new JsonObject();
        object.addProperty("profile", profile);
        object.addProperty("packName", packName);
        object.addProperty("packVersion", packVersion);
        object.addProperty("output", output);
        return object.toString();
    }
}
