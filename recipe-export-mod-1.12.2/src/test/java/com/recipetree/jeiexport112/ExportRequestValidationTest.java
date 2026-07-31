package com.recipetree.jeiexport112;

import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class ExportRequestValidationTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptsAStandardPortableRequest() throws Exception {
        Path game = temporary.newFolder("Portable Pack").toPath();
        JsonObject json = new JsonObject();
        json.addProperty("packName", "Portable Pack");
        json.addProperty("packVersion", "1.2.3");
        json.addProperty("output", "recipe-tree-export");
        json.addProperty("iconScale", 4);
        json.addProperty("requireWorld", true);

        ExportRequest request = ExportRequest.fromJson(json, game);

        assertEquals(game.resolve("recipe-tree-export"), request.output);
        assertEquals(4, request.iconScale);
        assertEquals("Portable Pack", request.pack.name);
    }

    @Test
    public void rejectsFractionalAndStringEncodedNumbers() throws Exception {
        Path game = temporary.newFolder("instance").toPath();
        JsonObject fractional = new JsonObject();
        fractional.addProperty("iconScale", 2.5);
        expectFailure(() -> ExportRequest.fromJson(fractional, game), "must be an integer");

        JsonObject encoded = new JsonObject();
        encoded.addProperty("iconScale", "2");
        expectFailure(() -> ExportRequest.fromJson(encoded, game), "must be an integer");
    }

    @Test
    public void rejectsNonPortableOrUnboundedWorldNames() throws Exception {
        Path game = temporary.newFolder("instance").toPath();
        JsonObject metacharacter = new JsonObject();
        metacharacter.addProperty("worldFolder", "Export:World");
        expectFailure(() -> ExportRequest.fromJson(metacharacter, game), "portable save-folder");

        JsonObject reserved = new JsonObject();
        reserved.addProperty("worldFolder", "CON.txt");
        expectFailure(() -> ExportRequest.fromJson(reserved, game), "portable save-folder");

        JsonObject control = new JsonObject();
        control.addProperty("worldName", "Export\nWorld");
        expectFailure(() -> ExportRequest.fromJson(control, game),
                "control, bidirectional, or zero-width");
    }

    @Test
    public void rejectsUnsafeOrUnboundedOutputTextBeforeFilesystemMutation() throws Exception {
        Path game = temporary.newFolder("instance").toPath();
        JsonObject control = new JsonObject();
        control.addProperty("output", "exports\u0000hidden");
        expectFailure(() -> ExportRequest.fromJson(control, game),
                "control, bidirectional, or zero-width");

        JsonObject unbounded = new JsonObject();
        unbounded.addProperty("output", repeat('x', 4097));
        expectFailure(() -> ExportRequest.fromJson(unbounded, game), "at most 4096");
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
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
