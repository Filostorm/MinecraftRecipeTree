package com.recipetree.neiexport1710;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AutomationWorldBootstrapTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void createsAndThenReusesOnlyTheOwnedVersionQualifiedWorld() throws Exception {
        Path game = temporary.newFolder("game").toPath();

        AutomationWorldBootstrap.PreparedWorld created = AutomationWorldBootstrap.prepare(game);
        assertTrue(created.created);
        assertEquals(game.resolve("saves").resolve(AutomationWorldBootstrap.WORLD_FOLDER)
                .toAbsolutePath().normalize(), created.directory);
        assertEquals(AutomationWorldBootstrap.MARKER_CONTENT,
                new String(Files.readAllBytes(created.directory.resolve(
                        AutomationWorldBootstrap.MARKER_FILE)), StandardCharsets.UTF_8));

        AutomationWorldBootstrap.PreparedWorld reused = AutomationWorldBootstrap.prepare(game);
        assertFalse(reused.created);
        assertEquals(created.directory, reused.directory);
        assertEquals(created.directory, AutomationWorldBootstrap.validateExisting(game));
    }

    @Test
    public void rejectsAnExistingUnownedWorldInsteadOfWritingIntoIt() throws Exception {
        Path game = temporary.newFolder("unowned-game").toPath();
        Files.createDirectories(game.resolve("saves").resolve(AutomationWorldBootstrap.WORLD_FOLDER));

        assertPrepareRejected(game, "unowned automation world");
    }

    @Test
    public void rejectsAChangedOwnershipMarker() throws Exception {
        Path game = temporary.newFolder("tampered-game").toPath();
        AutomationWorldBootstrap.PreparedWorld prepared = AutomationWorldBootstrap.prepare(game);
        Files.write(prepared.directory.resolve(AutomationWorldBootstrap.MARKER_FILE),
                "not our marker\n".getBytes(StandardCharsets.UTF_8));

        assertPrepareRejected(game, "modified ownership marker");
    }

    @Test
    public void validationNeverCreatesAMissingAutomationWorld() throws Exception {
        Path game = temporary.newFolder("validation-only-game").toPath();
        Files.createDirectory(game.resolve("saves"));
        try {
            AutomationWorldBootstrap.validateExisting(game);
            fail("Expected validation of a missing world to fail closed");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains("automation world"));
        }
        assertFalse(Files.exists(game.resolve("saves").resolve(
                AutomationWorldBootstrap.WORLD_FOLDER)));
    }

    @Test
    public void pinsARecipeNeutralCreativeSuperflatConfiguration() {
        assertEquals(1L, AutomationWorldBootstrap.WORLD_SEED);
        assertEquals("2;7,2x3,2;1;", AutomationWorldBootstrap.FLAT_GENERATOR_OPTIONS);
    }

    @Test
    public void validatesEveryPersistedDeterminismSetting() throws Exception {
        WorldSettings exact = new WorldSettings(
                AutomationWorldBootstrap.WORLD_SEED,
                WorldSettings.GameType.CREATIVE,
                false,
                false,
                WorldType.FLAT)
                .func_82750_a(AutomationWorldBootstrap.FLAT_GENERATOR_OPTIONS)
                .enableCommands();
        AutomationWorldBootstrap.validateWorldInfo(new WorldInfo(
                exact, AutomationWorldBootstrap.WORLD_FOLDER));

        WorldSettings wrongSeed = new WorldSettings(
                0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT)
                .func_82750_a(AutomationWorldBootstrap.FLAT_GENERATOR_OPTIONS)
                .enableCommands();
        try {
            AutomationWorldBootstrap.validateWorldInfo(new WorldInfo(
                    wrongSeed, AutomationWorldBootstrap.WORLD_FOLDER));
            fail("Expected persisted settings drift to fail closed");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains("settings drifted"));
        }
    }

    @Test
    public void rejectsMissingDirectServerOverworldEntries() throws Exception {
        assertServerArrayRejected(null, "array is null");
        assertServerArrayRejected(new WorldServer[0], "array is empty");
        assertServerArrayRejected(new WorldServer[1], "worldServers[0] is null");
    }

    @Test
    public void validatesTheAuthoritativeServerDimensionSeedAndWorldInfo() throws Exception {
        WorldSettings exact = new WorldSettings(
                AutomationWorldBootstrap.WORLD_SEED,
                WorldSettings.GameType.CREATIVE,
                false,
                false,
                WorldType.FLAT)
                .func_82750_a(AutomationWorldBootstrap.FLAT_GENERATOR_OPTIONS)
                .enableCommands();
        WorldInfo info = new WorldInfo(exact, AutomationWorldBootstrap.WORLD_NAME);

        AutomationWorldBootstrap.validateLiveServerWorld(
                0, AutomationWorldBootstrap.WORLD_SEED, info);
        assertLiveServerWorldRejected(1, AutomationWorldBootstrap.WORLD_SEED, info,
                "not dimension 0");
        assertLiveServerWorldRejected(0, 0L, info, "seed drifted");
    }

    private static void assertPrepareRejected(Path game, String expectedMessage) throws Exception {
        try {
            AutomationWorldBootstrap.prepare(game);
            fail("Expected automation world preparation to fail closed");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        }
    }

    private static void assertServerArrayRejected(WorldServer[] worlds, String expectedMessage)
            throws Exception {
        try {
            AutomationWorldBootstrap.requireDirectOverworldEntry(worlds);
            fail("Expected direct integrated-server overworld lookup to fail closed");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        }
    }

    private static void assertLiveServerWorldRejected(int dimensionId, long seed,
                                                      WorldInfo info, String expectedMessage)
            throws Exception {
        try {
            AutomationWorldBootstrap.validateLiveServerWorld(dimensionId, seed, info);
            fail("Expected authoritative integrated-server world audit to fail closed");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        }
    }
}
