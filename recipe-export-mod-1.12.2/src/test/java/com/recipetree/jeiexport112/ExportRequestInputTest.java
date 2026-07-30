package com.recipetree.jeiexport112;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class ExportRequestInputTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void readsBoundedStrictUtf8() throws Exception {
        Path request = temporary.newFile("request.json").toPath();
        String json = "{\"packName\":\"Brick 🧱\"}";
        Files.write(request, json.getBytes(StandardCharsets.UTF_8));
        assertEquals(json, ExportRequest.readBoundedUtf8(request));
    }

    @Test
    public void rejectsOversizedGrowingAndMalformedInputs() throws Exception {
        Path oversized = temporary.newFile("oversized.json").toPath();
        byte[] bytes = new byte[(int) ExportRequest.MAX_REQUEST_BYTES + 1];
        Files.write(oversized, bytes);
        expectFailure(() -> ExportRequest.readBoundedUtf8(oversized), "byte limit");

        Path malformed = temporary.newFile("malformed.json").toPath();
        Files.write(malformed, new byte[]{(byte) 0xc3, (byte) 0x28});
        expectFailure(() -> ExportRequest.readBoundedUtf8(malformed), "valid UTF-8");
    }

    @Test
    public void rejectsSymbolicLinks() throws Exception {
        Path target = temporary.newFile("target.json").toPath();
        Files.write(target, "{}".getBytes(StandardCharsets.UTF_8));
        Path link = target.resolveSibling("request-link.json");
        Files.createSymbolicLink(link, target);
        expectFailure(() -> ExportRequest.readBoundedUtf8(link), "regular, non-symbolic-link");
    }

    private static void expectFailure(ThrowingRunnable operation, String fragment) throws Exception {
        try {
            operation.run();
            fail("Expected IOException containing " + fragment);
        } catch (IOException expected) {
            if (!expected.getMessage().contains(fragment)) {
                throw new AssertionError("Expected '" + fragment + "' in '" +
                        expected.getMessage() + "'", expected);
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
