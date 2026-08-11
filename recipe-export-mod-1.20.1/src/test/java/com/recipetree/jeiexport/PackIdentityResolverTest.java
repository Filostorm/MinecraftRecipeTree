package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackIdentityResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void explicitPropertiesWinAndMetadataConflictIsLogged() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Files.writeString(gameDirectory.resolve("minecraftinstance.json"), """
                {"name":"Launcher Pack","version":"2.0"}
                """);
        TestDiagnostics diagnostics = new TestDiagnostics();

        PackIdentity identity = PackIdentityResolver.resolve(gameDirectory,
                properties(Map.of(
                        PackIdentityResolver.PACK_NAME_PROPERTY, "Explicit Pack",
                        PackIdentityResolver.PACK_VERSION_PROPERTY, "3.0"
                )), diagnostics);

        assertEquals("Explicit Pack", identity.name());
        assertEquals("3.0", identity.version());
        assertEquals("explicit-request", identity.identitySource());
        assertTrue(diagnostics.warnings.stream().anyMatch(message -> message.contains("Conflicting modpack identity")));
    }

    @Test
    void curseForgeMetadataIsBoundedAndResolved() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("curseforge-pack"));
        Files.writeString(gameDirectory.resolve("minecraftinstance.json"), """
                {"name":"Example CurseForge Pack","profileVersion":"1.4.2"}
                """);

        PackIdentity identity = resolveWithoutProperties(gameDirectory, new TestDiagnostics());

        assertEquals("Example CurseForge Pack", identity.name());
        assertEquals("1.4.2", identity.version());
        assertEquals("curseforge", identity.identitySource());
    }

    @Test
    void currentCurseForgeManifestVersionWinsOverLegacyFields() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("current-curseforge-pack"));
        Files.writeString(gameDirectory.resolve("minecraftinstance.json"), """
                {
                  "name":"MeatballCraft Dimensional Ascension",
                  "manifest":{"name":"Meatballcraft","version":"prerelease-0.18.5-hotfix2"},
                  "profileVersion":"stale-version"
                }
                """);

        PackIdentity identity = resolveWithoutProperties(gameDirectory, new TestDiagnostics());

        assertEquals("MeatballCraft Dimensional Ascension", identity.name());
        assertEquals("prerelease-0.18.5-hotfix2", identity.version());
        assertEquals("curseforge", identity.identitySource());
    }

    @Test
    void resolvesContemporaryMultiMegabyteCurseForgeMetadata() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("large-curseforge-pack"));
        Files.writeString(gameDirectory.resolve("minecraftinstance.json"),
                "{\"name\":\"Large CurseForge Pack\",\"profileVersion\":\"v60\",\"ignored\":\""
                        + "x".repeat(3 * 1024 * 1024) + "\"}");

        PackIdentity identity = resolveWithoutProperties(gameDirectory, new TestDiagnostics());

        assertEquals("Large CurseForge Pack", identity.name());
        assertEquals("v60", identity.version());
        assertEquals("curseforge", identity.identitySource());
    }

    @Test
    void prismManagedPackFieldsTakePriorityAndConflictIsLogged() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("prism-pack"));
        Path gameDirectory = Files.createDirectory(instanceDirectory.resolve(".minecraft"));
        Files.writeString(instanceDirectory.resolve("instance.cfg"), """
                name=Locally Renamed Instance
                ManagedPackName=Canonical Pack Name
                ManagedPackVersionName=Release 7
                """);
        TestDiagnostics diagnostics = new TestDiagnostics();

        PackIdentity identity = resolveWithoutProperties(gameDirectory, diagnostics);

        assertEquals("Canonical Pack Name", identity.name());
        assertEquals("Release 7", identity.version());
        assertEquals("prism", identity.identitySource());
        assertTrue(diagnostics.warnings.stream().anyMatch(message -> message.contains("Conflicting pack name fields")));
    }

    @Test
    void modrinthIndexIsSupported() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("modrinth-pack"));
        Files.writeString(gameDirectory.resolve("modrinth.index.json"), """
                {"formatVersion":1,"game":"minecraft","name":"A Modrinth Pack","versionId":"1.0.9"}
                """);

        PackIdentity identity = resolveWithoutProperties(gameDirectory, new TestDiagnostics());

        assertEquals("A Modrinth Pack", identity.name());
        assertEquals("1.0.9", identity.version());
        assertEquals("modrinth-index", identity.identitySource());
    }

    @Test
    void numericLauncherVersionIsNormalizedToText() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("numeric-version"));
        Files.writeString(gameDirectory.resolve("minecraftinstance.json"), """
                {"name":"Numeric Version Pack","version":42}
                """);

        PackIdentity identity = resolveWithoutProperties(gameDirectory, new TestDiagnostics());

        assertEquals("42", identity.version());
    }

    @Test
    void malformedOptionalMetadataFieldIsLoggedInsteadOfSilentlyIgnored() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("malformed-version"));
        Files.writeString(gameDirectory.resolve("minecraftinstance.json"), """
                {"name":"Pack With Bad Version","version":false}
                """);
        TestDiagnostics diagnostics = new TestDiagnostics();

        PackIdentity identity = resolveWithoutProperties(gameDirectory, diagnostics);

        assertNull(identity.version());
        assertTrue(diagnostics.warnings.stream().anyMatch(message -> message.contains("expected a string or number")));
    }

    @Test
    void dotMinecraftFallbackUsesInstanceDirectoryAndLogsWarning() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("My Local Pack"));
        Path gameDirectory = Files.createDirectory(instanceDirectory.resolve(".minecraft"));
        TestDiagnostics diagnostics = new TestDiagnostics();

        PackIdentity identity = resolveWithoutProperties(gameDirectory, diagnostics);

        assertEquals("My Local Pack", identity.name());
        assertNull(identity.version());
        assertEquals("game-directory", identity.identitySource());
        assertTrue(diagnostics.warnings.stream().anyMatch(message -> message.contains("game-directory name")));
    }

    @Test
    void invalidExplicitBidiTextIsRejectedInsteadOfFallingBack() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("safe-fallback"));
        Map<String, String> values = Map.of(PackIdentityResolver.PACK_NAME_PROPERTY, "Safe\u202Etxt");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PackIdentityResolver.resolve(gameDirectory, properties(values), new TestDiagnostics()));

        assertTrue(error.getMessage().contains("control, bidirectional, or zero-width"));
    }

    @Test
    void identityBoundsCountUnicodeCodePoints() {
        String name = "🧱".repeat(PackIdentity.MAX_NAME_CODE_POINTS);
        String version = "🚀".repeat(PackIdentity.MAX_VERSION_CODE_POINTS);

        PackIdentity identity = new PackIdentity(name, version, "explicit-request");

        assertEquals(PackIdentity.MAX_NAME_CODE_POINTS,
                identity.name().codePointCount(0, identity.name().length()));
        assertEquals(PackIdentity.MAX_VERSION_CODE_POINTS,
                identity.version().codePointCount(0, identity.version().length()));
        assertThrows(IllegalArgumentException.class,
                () -> new PackIdentity(name + "🧱", version, "explicit-request"));
        assertThrows(IllegalArgumentException.class,
                () -> new PackIdentity(name, version + "🚀", "explicit-request"));
    }

    @Test
    void rejectsCanonicalControlBidiAndZeroWidthRanges() {
        int[] unsafeCodePoints = {
                0x0000, 0x001F, 0x007F, 0x009F, 0x061C,
                0x200B, 0x200C, 0x200D, 0x200E, 0x200F,
                0x202A, 0x202E, 0x2060, 0x2065, 0x2069, 0xFEFF
        };
        for (int codePoint : unsafeCodePoints) {
            String unsafe = new String(Character.toChars(codePoint));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new PackIdentity("Safe" + unsafe + "Pack", "1", "explicit-request"),
                    () -> "Expected U+" + Integer.toHexString(codePoint).toUpperCase()
                            + " to be rejected");
            assertTrue(error.getMessage().contains("control, bidirectional, or zero-width"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PackIdentity(" Pack\n", "1", "explicit-request"));
    }

    @Test
    void versionWithoutExplicitNameIsRejected() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("safe-fallback"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PackIdentityResolver.resolve(gameDirectory,
                        properties(Map.of(PackIdentityResolver.PACK_VERSION_PROPERTY, "1.0")),
                        new TestDiagnostics()));

        assertTrue(error.getMessage().contains("requires"));
    }

    @Test
    void oversizedMetadataIsLoggedAndFallsBack() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("oversized-fallback"));
        byte[] oversized = new byte[(int) PackIdentityResolver.MAX_METADATA_BYTES + 1];
        Files.write(gameDirectory.resolve("minecraftinstance.json"), oversized);
        TestDiagnostics diagnostics = new TestDiagnostics();

        PackIdentity identity = resolveWithoutProperties(gameDirectory, diagnostics);

        assertEquals("oversized-fallback", identity.name());
        assertEquals("game-directory", identity.identitySource());
        assertTrue(diagnostics.warnings.stream().anyMatch(message -> message.contains("exceeds")));
    }

    @Test
    void symbolicLinkMetadataIsLoggedAndNeverFollowed() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("symlink-fallback"));
        Path target = temporaryDirectory.resolve("outside.json");
        Files.writeString(target, "{\"name\":\"Must Not Be Read\"}");
        try {
            Files.createSymbolicLink(gameDirectory.resolve("minecraftinstance.json"), target);
        } catch (UnsupportedOperationException | IOException e) {
            return; // Filesystem cannot exercise this safety assertion.
        }
        TestDiagnostics diagnostics = new TestDiagnostics();

        PackIdentity identity = resolveWithoutProperties(gameDirectory, diagnostics);

        assertEquals("symlink-fallback", identity.name());
        assertEquals("game-directory", identity.identitySource());
        assertTrue(diagnostics.warnings.stream().anyMatch(message -> message.contains("symbolic links")));
    }

    private static PackIdentity resolveWithoutProperties(Path gameDirectory, TestDiagnostics diagnostics)
            throws IOException {
        return PackIdentityResolver.resolve(gameDirectory, name -> null, diagnostics);
    }

    private static PackIdentityResolver.PropertySource properties(Map<String, String> values) {
        Map<String, String> copy = new HashMap<>(values);
        return copy::get;
    }

    private static final class TestDiagnostics implements PackIdentityResolver.Diagnostics {
        final List<String> information = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message) {
            information.add(message);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    }
}
