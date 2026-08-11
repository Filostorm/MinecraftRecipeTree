package com.recipetree.jeiexport112;

import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class PackIdentityResolverTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void explicitRequestIdentityWinsOverLauncherMetadata() throws Exception {
        Path gameDirectory = temporary.newFolder("instance").toPath();
        write(gameDirectory.resolve("minecraftinstance.json"),
                "{\"name\":\"Launcher Name\",\"installedModpackVersion\":\"1\"}");

        PackIdentity identity = PackIdentityResolver.resolve(
                gameDirectory, "Explicit Name", "2.0.0");

        assertEquals("Explicit Name", identity.name);
        assertEquals("2.0.0", identity.version);
        assertEquals("explicit-request", identity.source);
    }

    @Test
    public void resolvesCurseForgeMetadataWithBoundedKnownFields() throws Exception {
        Path gameDirectory = temporary.newFolder("curse-instance").toPath();
        write(gameDirectory.resolve("minecraftinstance.json"),
                "{\"name\":\"MeatballCraft\",\"installedModpackVersion\":\"0.16.5\"}");

        PackIdentity identity = PackIdentityResolver.resolve(gameDirectory, null, null);

        assertEquals("MeatballCraft", identity.name);
        assertEquals("0.16.5", identity.version);
        assertEquals("curseforge", identity.source);
    }

    @Test
    public void resolvesCurrentCurseForgeManifestVersionBeforeLegacyFields() throws Exception {
        Path gameDirectory = temporary.newFolder("current-curse-instance").toPath();
        write(gameDirectory.resolve("minecraftinstance.json"),
                "{\"name\":\"MeatballCraft Dimensional Ascension\","
                        + "\"manifest\":{\"name\":\"Meatballcraft\","
                        + "\"version\":\"prerelease-0.18.5-hotfix2\"},"
                        + "\"installedModpackVersion\":\"stale-version\"}");

        PackIdentity identity = PackIdentityResolver.resolve(gameDirectory, null, null);

        assertEquals("MeatballCraft Dimensional Ascension", identity.name);
        assertEquals("prerelease-0.18.5-hotfix2", identity.version);
        assertEquals("curseforge", identity.source);
    }

    @Test
    public void resolvesPrismParentInstanceConfiguration() throws Exception {
        Path instance = temporary.newFolder("prism-instance").toPath();
        Path gameDirectory = Files.createDirectory(instance.resolve(".minecraft"));
        write(instance.resolve("instance.cfg"),
                "InstanceType=OneSix\nManagedPackName=GregTech Community Pack\n"
                        + "ManagedPackVersionName=1.2.3\nname=Local Alias\n");

        PackIdentity identity = PackIdentityResolver.resolve(gameDirectory, null, null);

        assertEquals("GregTech Community Pack", identity.name);
        assertEquals("1.2.3", identity.version);
        assertEquals("prism", identity.source);
    }

    @Test
    public void resolvesModrinthIndexAndFallsBackToInstanceDirectoryExplicitly() throws Exception {
        Path modrinth = temporary.newFolder("modrinth-instance").toPath();
        write(modrinth.resolve("modrinth.index.json"),
                "{\"formatVersion\":1,\"name\":\"Create Pack\",\"versionId\":\"4.2\"}");
        PackIdentity indexed = PackIdentityResolver.resolve(modrinth, null, null);
        assertEquals("Create Pack", indexed.name);
        assertEquals("4.2", indexed.version);
        assertEquals("modrinth-index", indexed.source);

        Path fallbackRoot = temporary.newFolder("Manual Pack").toPath();
        Path gameDirectory = Files.createDirectory(fallbackRoot.resolve(".minecraft"));
        PackIdentity fallback = PackIdentityResolver.resolve(gameDirectory, null, null);
        assertEquals("Manual Pack", fallback.name);
        assertNull(fallback.version);
        assertEquals("game-directory", fallback.source);
    }

    @Test
    public void rejectsAmbiguousUnsafeAndMalformedIdentityInputs() throws Exception {
        Path gameDirectory = temporary.newFolder("unsafe-instance").toPath();
        expectFailure(() -> PackIdentityResolver.resolve(gameDirectory, null, "1.0"),
                "packVersion requires packName");
        expectFailure(() -> PackIdentityResolver.resolve(gameDirectory, "Unsafe\nName", null),
                "control, bidirectional, or zero-width");
        expectFailure(() -> PackIdentityResolver.resolve(gameDirectory, "Visual\u202eSpoof", null),
                "control, bidirectional, or zero-width");
        expectFailure(() -> PackIdentityResolver.resolve(
                        gameDirectory, repeat('x', PackIdentity.MAX_NAME_CODE_POINTS + 1), null),
                "at most " + PackIdentity.MAX_NAME_CODE_POINTS);

        write(gameDirectory.resolve("minecraftinstance.json"), "[]");
        expectFailure(() -> PackIdentityResolver.resolve(gameDirectory, null, null),
                "root must be a JSON object");
    }

    @Test
    public void countsPackIdentityLimitsInUnicodeCodePoints() throws Exception {
        String name = repeat("🧱", PackIdentity.MAX_NAME_CODE_POINTS);
        String version = repeat("🚀", PackIdentity.MAX_VERSION_CODE_POINTS);

        PackIdentity identity = new PackIdentity(name, version, "explicit-request");

        assertEquals(PackIdentity.MAX_NAME_CODE_POINTS,
                identity.name.codePointCount(0, identity.name.length()));
        assertEquals(PackIdentity.MAX_VERSION_CODE_POINTS,
                identity.version.codePointCount(0, identity.version.length()));
        expectFailure(() -> new PackIdentity(name + "🧱", version, "explicit-request"),
                "at most " + PackIdentity.MAX_NAME_CODE_POINTS);
        expectFailure(() -> new PackIdentity(name, version + "🚀", "explicit-request"),
                "at most " + PackIdentity.MAX_VERSION_CODE_POINTS);
    }

    @Test
    public void rejectsCanonicalControlBidiAndZeroWidthRanges() throws Exception {
        int[] unsafeCodePoints = {
                0x0000, 0x001f, 0x007f, 0x009f, 0x061c,
                0x200b, 0x200c, 0x200d, 0x200e, 0x200f,
                0x202a, 0x202e, 0x2060, 0x2065, 0x2069, 0xfeff
        };
        for (int codePoint : unsafeCodePoints) {
            String unsafe = new String(Character.toChars(codePoint));
            expectFailure(() -> new PackIdentity(
                            "Safe" + unsafe + "Pack", "1", "explicit-request"),
                    "control, bidirectional, or zero-width");
        }
    }

    @Test
    public void requestSchemaRejectsUnknownTopLevelFields() throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("packName", "Known Pack");
        object.addProperty("output", "jei-exports");
        ExportRequest.rejectUnknownKeys(object);

        object.addProperty("silentFallback", true);
        expectFailure(() -> ExportRequest.rejectUnknownKeys(object),
                "Unsupported exporter request field: silentFallback");
    }

    private static void write(Path path, String source) throws IOException {
        Files.write(path, source.getBytes(StandardCharsets.UTF_8));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static void expectFailure(ThrowingRunnable runnable, String messageFragment)
            throws Exception {
        try {
            runnable.run();
            fail("Expected IOException containing: " + messageFragment);
        } catch (IOException exception) {
            if (!exception.getMessage().contains(messageFragment)) {
                throw new AssertionError("Expected message containing '" + messageFragment
                        + "' but received '" + exception.getMessage() + "'", exception);
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
