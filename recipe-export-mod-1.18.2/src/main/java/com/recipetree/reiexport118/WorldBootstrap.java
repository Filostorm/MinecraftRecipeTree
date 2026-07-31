package com.recipetree.reiexport118;

import com.recipetree.reiexport118.compat.Mm2UnattendedUiScope;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class WorldBootstrap {
    private static final int TITLE_DELAY_TICKS = 100;
    private static final WorldBootstrapTransition TRANSITION =
            new WorldBootstrapTransition();
    private static int titleTicks;

    private WorldBootstrap() {
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            observeActiveLevel();
            return;
        }
        Path requestPath = minecraft.gameDirectory.toPath().resolve(ExportRequest.ACTIVE_NAME);
        if (!Files.isRegularFile(requestPath)) {
            titleTicks = 0;
            if (TRANSITION.isActive()) {
                ReiExportMod.LOGGER.warn(
                        "[reiexport] Clearing MM2 world bootstrap ownership because the request "
                                + "disappeared before a client level became active: {}",
                        TRANSITION.description());
                TRANSITION.clear();
            }
            ExportCoordinator.releaseUnclaimedScopes(
                    "request absent while no client level is active");
            return;
        }
        if (TRANSITION.isActive()) {
            if (TRANSITION.tickWithoutLevelTimedOut()) {
                String transition = TRANSITION.description();
                TRANSITION.clear();
                IllegalStateException failure = new IllegalStateException(
                        "MM2 world bootstrap returned without producing a client level within "
                                + WorldBootstrapTransition.MAX_POST_CALL_NO_LEVEL_TICKS
                                + " ticks: " + transition);
                ReiExportMod.LOGGER.error(
                        "[reiexport] MM2 world bootstrap timed out; the request is being failed "
                                + "and no alternate world fallback was attempted",
                        failure);
                ExportCoordinator.failUnclaimedRequest(failure);
            }
            return;
        }
        titleTicks++;
        if (titleTicks < TITLE_DELAY_TICKS) {
            return;
        }
        try {
            ExportRequest request = ExportRequest.read(requestPath);
            String requestSha256 = sha256(requestPath);
            Mm2UnattendedUiScope.armForExactRequest(minecraft.gameDirectory.toPath());
            if (minecraft.getLevelSource().levelExists(request.worldName)) {
                ReiExportMod.LOGGER.info("[reiexport] Loading requested export world {}", request.worldName);
                TRANSITION.begin(
                        WorldBootstrapTransition.Kind.LOAD,
                        request.worldName,
                        requestSha256);
                minecraft.loadLevel(request.worldName);
                TRANSITION.markCallReturned();
                return;
            }
            RegistryAccess.Writable registries = RegistryAccess.builtinCopy();
            LevelSettings settings = new LevelSettings(
                    "REI Export",
                    GameType.CREATIVE,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    new GameRules(),
                    DataPackConfig.DEFAULT);
            WorldGenSettings worldGen = WorldGenSettings.makeDefault(registries);
            ReiExportMod.LOGGER.info("[reiexport] Creating disposable requested export world {}", request.worldName);
            TRANSITION.begin(
                    WorldBootstrapTransition.Kind.CREATE,
                    request.worldName,
                    requestSha256);
            minecraft.createLevel(request.worldName, settings, registries, worldGen);
            TRANSITION.markCallReturned();
        } catch (Throwable throwable) {
            String transition = TRANSITION.description();
            TRANSITION.clear();
            ReiExportMod.LOGGER.error("[reiexport] Export-world bootstrap failed; no alternate world fallback was attempted", throwable);
            ReiExportMod.LOGGER.error("[reiexport] Failed MM2 world bootstrap ownership state: {}", transition);
            ExportCoordinator.failUnclaimedRequest(throwable);
        }
    }

    static boolean consumeExpectedBootstrapLogout() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || minecraft.level != null || !TRANSITION.isActive()) {
            return false;
        }
        Path requestPath = minecraft.gameDirectory.toPath().resolve(ExportRequest.ACTIVE_NAME);
        if (!Files.isRegularFile(requestPath)) {
            return false;
        }
        try {
            return TRANSITION.consumeExpectedLogout(sha256(requestPath));
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Could not verify the active request while classifying a world "
                            + "bootstrap logout; it will be treated as an ordinary terminal logout",
                    throwable);
            return false;
        }
    }

    static void observeActiveLevel() {
        titleTicks = 0;
        if (!TRANSITION.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Path requestPath = minecraft.gameDirectory.toPath().resolve(ExportRequest.ACTIVE_NAME);
        try {
            if (!Files.isRegularFile(requestPath)) {
                throw new IllegalStateException(
                        "Active export request disappeared during world bootstrap");
            }
            TRANSITION.requireReadyForActiveLevel(sha256(requestPath));
            if (minecraft.getSingleplayerServer() == null) {
                throw new IllegalStateException(
                        "MM2 exporter world bootstrap produced a client level without an "
                                + "integrated server");
            }
            Path activeWorld = minecraft.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath()
                    .normalize();
            String requestedWorld = TRANSITION.worldName();
            Path fileName = activeWorld.getFileName();
            if (fileName == null || !requestedWorld.equals(fileName.toString())) {
                throw new IllegalStateException(
                        "MM2 world bootstrap entered the wrong integrated world: expected="
                                + requestedWorld + ", actualRoot=" + activeWorld);
            }
            ReiExportMod.LOGGER.info(
                    "[reiexport] Completed owned MM2 world bootstrap transition: {}",
                    TRANSITION.description());
            TRANSITION.clear();
        } catch (Throwable throwable) {
            String transition = TRANSITION.description();
            TRANSITION.clear();
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 active-level bootstrap verification failed; the request "
                            + "is being failed and no alternate world fallback was attempted; state={}",
                    transition,
                    throwable);
            ExportCoordinator.failUnclaimedRequest(throwable);
        }
    }

    private static String sha256(Path path) throws java.io.IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", impossible);
        }
    }
}
