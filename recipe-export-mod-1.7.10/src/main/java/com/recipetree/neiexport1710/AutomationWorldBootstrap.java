package com.recipetree.neiexport1710;

import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Prepares the deterministic integrated world that establishes GTNH's normal
 * client/server lifecycle before NEI is inspected.
 */
final class AutomationWorldBootstrap {
    static final String WORLD_FOLDER = "RecipeTree-GTNH-2.8.4-Exporter";
    static final String WORLD_NAME = "Recipe Tree GTNH 2.8.4 Exporter";
    static final String MARKER_FILE = ".recipe-tree-export-world.json";
    static final String MARKER_CONTENT = "{\n"
            + "  \"schema\": 1,\n"
            + "  \"owner\": \"com.recipetree.neiexport1710\",\n"
            + "  \"pack\": \"GT New Horizons\",\n"
            + "  \"version\": \"2.8.4\"\n"
            + "}\n";
    // GT5U's Ross128b chunk provider uses XSTR, whose xorshift state remains
    // permanently zero when seeded with 0. That forces every probabilistic
    // world-generation branch, including the nominal 1-in-512 ruin path,
    // during Galacticraft dimension registration. A fixed nonzero state keeps
    // startup deterministic without changing any recipe-affecting pack config.
    static final long WORLD_SEED = 1L;
    static final String FLAT_GENERATOR_OPTIONS = "2;7,2x3,2;1;";

    private static final byte[] MARKER_BYTES = MARKER_CONTENT.getBytes(StandardCharsets.UTF_8);

    private AutomationWorldBootstrap() {}

    static PreparedWorld prepare(Path gameDirectory) throws IOException {
        Path normalizedGame = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedGame, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Minecraft game directory is not a real directory: " + normalizedGame);
        }

        Path saves = normalizedGame.resolve("saves");
        if (Files.exists(saves, LinkOption.NOFOLLOW_LINKS)) {
            requireRealDirectory(saves, "Minecraft saves directory");
        } else {
            Files.createDirectory(saves);
        }

        Path world = saves.resolve(WORLD_FOLDER);
        if (Files.exists(world, LinkOption.NOFOLLOW_LINKS)) {
            requireRealDirectory(world, "automation world");
            validateMarker(world.resolve(MARKER_FILE));
            return new PreparedWorld(world, false);
        }

        Files.createDirectory(world);
        Path marker = world.resolve(MARKER_FILE);
        Files.write(marker, MARKER_BYTES, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        validateMarker(marker);
        return new PreparedWorld(world, true);
    }

    static Path validateExisting(Path gameDirectory) throws IOException {
        Path normalizedGame = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedGame, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Minecraft game directory is not a real directory: " + normalizedGame);
        }
        Path saves = normalizedGame.resolve("saves");
        requireRealDirectory(saves, "Minecraft saves directory");
        Path world = saves.resolve(WORLD_FOLDER);
        requireRealDirectory(world, "automation world");
        validateMarker(world.resolve(MARKER_FILE));
        return world;
    }

    static Path requireActiveOwnedWorld(Minecraft minecraft) throws IOException {
        requireActiveSessionIdentity(minecraft);
        Path world = validateExisting(minecraft.mcDataDir.toPath());
        validatePersistedSettings(minecraft);
        return world;
    }

    /**
     * Audits the authoritative server-side overworld after the client/player/NEI
     * handshake. Do not replace this with WorldClient#getSeed: 1.7.10's client
     * world does not carry the authoritative integrated-server seed. Likewise,
     * reading worldServers[0] is intentional; worldServerForDimension may create
     * or initialize dimensions as a side effect in this modpack.
     */
    static void requireLiveServerWorld(Minecraft minecraft) throws IOException {
        IntegratedServer server = minecraft.getIntegratedServer();
        if (server == null) {
            throw new IOException("live world audit found no integrated-server instance");
        }

        WorldServer overworld = requireDirectOverworldEntry(server.worldServers);
        if (overworld.provider == null) {
            throw new IOException("integrated-server worldServers[0] has no world provider");
        }
        WorldInfo info = overworld.getWorldInfo();
        if (info == null) {
            throw new IOException("integrated-server worldServers[0] has no WorldInfo");
        }
        validateLiveServerWorld(overworld.provider.dimensionId, overworld.getSeed(), info);
    }

    static WorldServer requireDirectOverworldEntry(WorldServer[] worldServers) throws IOException {
        if (worldServers == null) {
            throw new IOException("integrated-server worldServers array is null");
        }
        if (worldServers.length == 0) {
            throw new IOException("integrated-server worldServers array is empty");
        }
        if (worldServers[0] == null) {
            throw new IOException("integrated-server worldServers[0] is null");
        }
        return worldServers[0];
    }

    static void validateLiveServerWorld(int dimensionId, long liveSeed, WorldInfo info)
            throws IOException {
        if (dimensionId != 0) {
            throw new IOException("integrated-server worldServers[0] is not dimension 0; actual="
                    + dimensionId);
        }
        if (liveSeed != WORLD_SEED) {
            throw new IOException("integrated-server worldServers[0] seed drifted; expected="
                    + WORLD_SEED + ", actual=" + liveSeed);
        }
        if (info == null) {
            throw new IOException("integrated-server worldServers[0] has no WorldInfo");
        }
        validateWorldInfo(info);
    }

    static void requireActiveSessionIdentity(Minecraft minecraft) throws IOException {
        IntegratedServer server = minecraft.getIntegratedServer();
        if (server == null) {
            throw new IOException("active client session has no integrated-server instance");
        }
        String folder = server.getFolderName();
        if (!WORLD_FOLDER.equals(folder)) {
            throw new IOException("active integrated world is not the owned automation save; folder="
                    + folder);
        }
        if (server.isServerStopped()) {
            throw new IOException("owned integrated server stopped before the client world/player handshake");
        }
        if (!minecraft.isSingleplayer()) {
            throw new IOException("owned integrated-server running flag cleared before the client handshake");
        }
    }

    static void validatePersistedSettings(Minecraft minecraft) throws IOException {
        WorldInfo info = minecraft.getSaveLoader().getWorldInfo(WORLD_FOLDER);
        if (info == null) {
            throw new IOException("owned automation world has no readable persisted WorldInfo");
        }
        validateWorldInfo(info);
    }

    static void validateWorldInfo(WorldInfo info) throws IOException {
        if (info.getSeed() != WORLD_SEED
                || info.getGameType() != WorldSettings.GameType.CREATIVE
                || info.isMapFeaturesEnabled()
                || info.isHardcoreModeEnabled()
                || info.getTerrainType() != WorldType.FLAT
                || !FLAT_GENERATOR_OPTIONS.equals(info.getGeneratorOptions())
                || !info.areCommandsAllowed()) {
            throw new IOException("owned automation world settings drifted; expected seed="
                    + WORLD_SEED + ", gameType=CREATIVE, mapFeatures=false, hardcore=false, "
                    + "terrain=FLAT, generatorOptions=" + FLAT_GENERATOR_OPTIONS
                    + ", commands=true; actual seed=" + info.getSeed()
                    + ", gameType=" + info.getGameType()
                    + ", mapFeatures=" + info.isMapFeaturesEnabled()
                    + ", hardcore=" + info.isHardcoreModeEnabled()
                    + ", terrain=" + info.getTerrainType()
                    + ", generatorOptions=" + info.getGeneratorOptions()
                    + ", commands=" + info.areCommandsAllowed());
        }
    }

    static void launch(Minecraft minecraft) throws IOException {
        WorldSettings settings = new WorldSettings(
                WORLD_SEED,
                WorldSettings.GameType.CREATIVE,
                false,
                false,
                WorldType.FLAT).func_82750_a(FLAT_GENERATOR_OPTIONS).enableCommands();
        minecraft.launchIntegratedServer(WORLD_FOLDER, WORLD_NAME, settings);
    }

    private static void requireRealDirectory(Path directory, String label) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a non-symlink directory: " + directory);
        }
    }

    private static void validateMarker(Path marker) throws IOException {
        if (Files.isSymbolicLink(marker)
                || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to reuse an unowned automation world; exact marker is absent: "
                    + marker);
        }
        byte[] actual = Files.readAllBytes(marker);
        if (!Arrays.equals(MARKER_BYTES, actual)) {
            throw new IOException("Refusing to reuse automation world with a modified ownership marker: "
                    + marker);
        }
    }

    static final class PreparedWorld {
        final Path directory;
        final boolean created;

        PreparedWorld(Path directory, boolean created) {
            this.directory = directory;
            this.created = created;
        }
    }
}
