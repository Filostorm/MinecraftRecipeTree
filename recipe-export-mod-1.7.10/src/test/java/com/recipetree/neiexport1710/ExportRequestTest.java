package com.recipetree.neiexport1710;

import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExportRequestTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void requiresAndPreservesExactSnapshotScales() throws Exception {
        JsonObject request = baseRequest();
        request.addProperty("output", "snapshot");
        ExportRequest parsed = ExportRequest.fromJson(request, temporary.getRoot().toPath());

        assertEquals(temporary.getRoot().toPath().resolve("snapshot").toAbsolutePath().normalize(),
                parsed.output);
    }

    @Test(expected = IOException.class)
    public void rejectsImplicitPackIdentity() throws Exception {
        ExportRequest.fromJson(new JsonObject(), temporary.getRoot().toPath());
    }

    @Test(expected = IOException.class)
    public void rejectsScaleDrift() throws Exception {
        JsonObject request = baseRequest();
        request.addProperty("iconScale", 2);
        ExportRequest.fromJson(request, temporary.getRoot().toPath());
    }

    @Test(expected = IOException.class)
    public void rejectsMobCanvasDrift() throws Exception {
        JsonObject request = baseRequest();
        request.addProperty("mobCanvas", 128);
        ExportRequest.fromJson(request, temporary.getRoot().toPath());
    }

    @Test
    public void requiresExplicitIntegratedWorldAuthorization() throws Exception {
        JsonObject request = baseRequest();
        request.remove("bootstrapIntegratedWorld");
        assertRejectedWithMessage(request, "bootstrapIntegratedWorld=true");

        request.addProperty("bootstrapIntegratedWorld", false);
        assertRejectedWithMessage(request, "bootstrapIntegratedWorld=true");
    }

    @Test
    public void preservesExactPinnedPackIdentity() throws Exception {
        JsonObject wrongName = baseRequest();
        wrongName.getAsJsonObject("pack").addProperty("name", "GT New Horizons ");
        assertRejectedWithMessage(wrongName, "only exports");

        JsonObject wrongVersion = baseRequest();
        wrongVersion.getAsJsonObject("pack").addProperty("version", "2.8.5");
        assertRejectedWithMessage(wrongVersion, "only exports");
    }

    @Test
    public void packIdentityBoundsCountUnicodeCodePoints() throws Exception {
        String maximumName = repeatCodePoint(0x1f9f1, ExportRequest.MAX_PACK_NAME_CODE_POINTS);
        String maximumVersion = repeatCodePoint(0x1f680, ExportRequest.MAX_PACK_VERSION_CODE_POINTS);

        assertEquals(maximumName, ExportRequest.validatedPackIdentityText(
                maximumName, "pack.name", ExportRequest.MAX_PACK_NAME_CODE_POINTS));
        assertEquals(maximumVersion, ExportRequest.validatedPackIdentityText(
                maximumVersion, "pack.version", ExportRequest.MAX_PACK_VERSION_CODE_POINTS));
        assertTextRejected(maximumName + new String(Character.toChars(0x1f9f1)),
                "pack.name", ExportRequest.MAX_PACK_NAME_CODE_POINTS, "120 Unicode code points");
        assertTextRejected(maximumVersion + new String(Character.toChars(0x1f680)),
                "pack.version", ExportRequest.MAX_PACK_VERSION_CODE_POINTS, "80 Unicode code points");
    }

    @Test
    public void rejectsCanonicalControlBidiAndZeroWidthRanges() throws Exception {
        JsonObject unsafeNameRequest = baseRequest();
        unsafeNameRequest.getAsJsonObject("pack").addProperty("name", "GT New\u200b Horizons");
        assertRejectedWithMessage(unsafeNameRequest, "control, bidirectional, or zero-width");

        JsonObject unsafeVersionRequest = baseRequest();
        unsafeVersionRequest.getAsJsonObject("pack").addProperty("version", "2.8.\ufeff4");
        assertRejectedWithMessage(unsafeVersionRequest, "control, bidirectional, or zero-width");

        assertUnsafeRangeRejected(0x0000, 0x001f);
        assertUnsafeRangeRejected(0x007f, 0x009f);
        assertUnsafeRangeRejected(0x061c, 0x061c);
        assertUnsafeRangeRejected(0x200b, 0x200f);
        assertUnsafeRangeRejected(0x202a, 0x202e);
        assertUnsafeRangeRejected(0x2060, 0x2069);
        assertUnsafeRangeRejected(0xfeff, 0xfeff);
    }

    private void assertRejectedWithMessage(JsonObject request, String expectedMessage) throws Exception {
        try {
            ExportRequest.fromJson(request, temporary.getRoot().toPath());
            fail("Expected request to be rejected");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        }
    }

    private static void assertUnsafeRangeRejected(int first, int last) throws Exception {
        for (int codePoint = first; codePoint <= last; codePoint++) {
            assertTextRejected("Safe" + new String(Character.toChars(codePoint)) + "Pack",
                    "pack.name", ExportRequest.MAX_PACK_NAME_CODE_POINTS,
                    "control, bidirectional, or zero-width");
        }
    }

    private static void assertTextRejected(String value, String field, int maximumCodePoints,
                                           String expectedMessage) throws Exception {
        try {
            ExportRequest.validatedPackIdentityText(value, field, maximumCodePoints);
            fail("Expected " + field + " text to be rejected");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        }
    }

    private static String repeatCodePoint(int codePoint, int count) {
        StringBuilder value = new StringBuilder(count * Character.charCount(codePoint));
        for (int index = 0; index < count; index++) {
            value.appendCodePoint(codePoint);
        }
        return value.toString();
    }

    private static JsonObject baseRequest() {
        JsonObject request = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("name", "GT New Horizons");
        pack.addProperty("version", "2.8.4");
        request.add("pack", pack);
        request.addProperty("iconScale", 3);
        request.addProperty("recipeScale", 2);
        request.addProperty("mobCanvas", 256);
        request.addProperty("bootstrapIntegratedWorld", true);
        return request;
    }
}
