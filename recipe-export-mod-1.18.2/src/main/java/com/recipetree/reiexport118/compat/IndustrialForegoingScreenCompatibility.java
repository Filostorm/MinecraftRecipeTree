package com.recipetree.reiexport118.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Supplies the inert Screen context that Industrial Foregoing's native JEI renderers receive in
 * an actual recipe-viewing UI. The exporter installs it only for byte-pinned MM2 categories and
 * restores the exact prior null state after every serialized off-screen capture. The export-wide
 * unattended UI scope rejects every ambient Screen before native rendering begins.
 */
public final class IndustrialForegoingScreenCompatibility {
    public static final class Scope implements AutoCloseable {
        private final Minecraft minecraft;
        private final CaptureScreen installed;
        private final boolean active;
        private boolean closed;

        private Scope(Minecraft minecraft, CaptureScreen installed, boolean active) {
            this.minecraft = minecraft;
            this.installed = installed;
            this.active = active;
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException(
                        "Industrial Foregoing native Screen scope was closed more than once");
            }
            closed = true;
            if (!active) {
                return;
            }

            IllegalStateException drift = null;
            if (ACTIVE_SCOPE.get() != this) {
                drift = new IllegalStateException(
                        "Industrial Foregoing native Screen scope ownership drifted before release");
            } else if (minecraft.screen != installed) {
                drift = new IllegalStateException(
                        "Industrial Foregoing native render replaced the exporter-owned Screen: actual="
                                + className(minecraft.screen));
            }
            try {
                minecraft.screen = null;
                if (minecraft.screen != null) {
                    IllegalStateException restoreFailure = new IllegalStateException(
                            "Exporter-owned native recipe Screen did not restore to null");
                    if (drift == null) {
                        drift = restoreFailure;
                    } else {
                        drift.addSuppressed(restoreFailure);
                    }
                }
            } finally {
                ACTIVE_SCOPE.remove();
                releases++;
            }
            if (drift != null) {
                throw drift;
            }
        }
    }

    private static final class CaptureScreen extends Screen {
        private CaptureScreen() {
            super(new TextComponent("Minecraft Recipe Tree native recipe capture"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static final ThreadLocal<Scope> ACTIVE_SCOPE = new ThreadLocal<>();
    private static volatile boolean armed;
    private static CaptureScreen captureScreen;
    private static long activations;
    private static long releases;

    private IndustrialForegoingScreenCompatibility() {
    }

    public static void validateBeforeReiRegistration() {
        armed = false;
        captureScreen = null;
        activations = 0;
        releases = 0;
        if (ACTIVE_SCOPE.get() != null) {
            throw new IllegalStateException(
                    "Industrial Foregoing native Screen preflight found a leaked prior scope");
        }

        Minecraft minecraft = Minecraft.getInstance();
        Mm2ExportRequestScope.Inspection request = Mm2ExportRequestScope.inspect(
                minecraft.gameDirectory.toPath());
        if (!request.isExactMm2()) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] Industrial Foregoing native Screen compatibility not armed because the exact MM2 request is absent");
            return;
        }

        String minecraftVersion = modVersion("minecraft");
        String forgeVersion = modVersion("forge");
        String industrialVersion = modVersion("industrialforegoing");
        String titaniumVersion = modVersion("titanium");
        if (!IndustrialForegoingScreenContract.isApplicable(
                minecraftVersion, forgeVersion, industrialVersion, titaniumVersion)) {
            throw new IllegalStateException(
                    "Industrial Foregoing native Screen compatibility runtime drift: required "
                            + "minecraft=" + IndustrialForegoingScreenContract.MINECRAFT_VERSION
                            + ", forge=" + IndustrialForegoingScreenContract.FORGE_VERSION
                            + ", industrialforegoing="
                            + IndustrialForegoingScreenContract.INDUSTRIAL_FOREGOING_VERSION
                            + ", titanium=" + IndustrialForegoingScreenContract.TITANIUM_VERSION
                            + "; actual minecraft=" + minecraftVersion
                            + ", forge=" + forgeVersion
                            + ", industrialforegoing=" + industrialVersion
                            + ", titanium=" + titaniumVersion);
        }

        List<String> failures = new ArrayList<>();
        for (var pin : IndustrialForegoingScreenContract.RENDERER_CLASS_SHA256.entrySet()) {
            validateClassResource(pin.getKey(), pin.getValue(), failures);
        }
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                ReiExportMod.LOGGER.error(
                        "[reiexport] Industrial Foregoing native Screen preflight failure: {}",
                        failure);
            }
            throw new IllegalStateException(
                    "Industrial Foregoing native Screen compatibility rejected "
                            + failures.size() + " bytecode contract(s)");
        }

        armed = true;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact MM2 Industrial Foregoing {} / Titanium {} native Screen context for {} byte-pinned category renderers; direct Screen assignment is capture-scoped and Minecraft.setScreen lifecycle side effects are not invoked",
                industrialVersion,
                titaniumVersion,
                IndustrialForegoingScreenContract.RENDERER_CLASS_SHA256.size());
    }

    public static Scope beginIfRequired(String categoryId, int logicalWidth, int logicalHeight) {
        if (!IndustrialForegoingScreenContract.requiresScreen(categoryId)) {
            return new Scope(null, null, false);
        }
        if (!armed) {
            throw new IllegalStateException(
                    "A byte-pinned Industrial Foregoing category requested a native Screen without an armed compatibility contract: "
                            + categoryId);
        }
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException(
                    "Industrial Foregoing native Screen scope must run on Minecraft's render thread");
        }
        if (ACTIVE_SCOPE.get() != null) {
            throw new IllegalStateException(
                    "Nested Industrial Foregoing native Screen scopes are not supported");
        }
        IndustrialForegoingScreenContract.requireLogicalDimensions(logicalWidth, logicalHeight);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            throw new IllegalStateException(
                    "Industrial Foregoing export expected no active Minecraft Screen before capture; actual="
                            + className(minecraft.screen));
        }
        CaptureScreen screen = captureScreen;
        if (screen == null) {
            screen = new CaptureScreen();
            captureScreen = screen;
        }
        screen.init(minecraft, logicalWidth, logicalHeight);
        Scope scope = new Scope(minecraft, screen, true);
        ACTIVE_SCOPE.set(scope);
        minecraft.screen = screen;
        if (minecraft.screen != screen) {
            ACTIVE_SCOPE.remove();
            minecraft.screen = null;
            throw new IllegalStateException(
                    "Exporter-owned Industrial Foregoing native Screen was not installed");
        }
        activations++;
        if (activations == 1) {
            ReiExportMod.LOGGER.warn(
                    "[reiexport] Installed exporter-owned inert Screen for first byte-pinned Industrial Foregoing native recipe capture: category={}, logical={}x{}",
                    categoryId, logicalWidth, logicalHeight);
        }
        return scope;
    }

    public static void requireReleasedAndLog() {
        if (!armed) {
            return;
        }
        if (ACTIVE_SCOPE.get() != null
                || (captureScreen != null && Minecraft.getInstance().screen == captureScreen)
                || activations != releases) {
            throw new IllegalStateException(
                    "Industrial Foregoing native Screen lifecycle imbalance: activations="
                            + activations + ", releases=" + releases
                            + ", activeScope=" + (ACTIVE_SCOPE.get() != null)
                            + ", installed="
                            + (captureScreen != null
                            && Minecraft.getInstance().screen == captureScreen));
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] Released all exporter-owned Industrial Foregoing native Screen scopes: activations={}, releases={}",
                activations, releases);
    }

    public static boolean isArmed() {
        return armed;
    }

    private static void validateClassResource(
            String resourcePath,
            String expectedSha256,
            List<String> failures
    ) {
        try (InputStream input = IndustrialForegoingScreenCompatibility.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                failures.add("missing class resource=" + resourcePath);
                return;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!expectedSha256.equals(actual)) {
                failures.add("class bytecode drift resource=" + resourcePath
                        + ", expectedSha256=" + expectedSha256
                        + ", actualSha256=" + actual);
            }
        } catch (IOException | NoSuchAlgorithmException exception) {
            failures.add("class bytecode validation failed resource=" + resourcePath
                    + ", exception=" + exception.getClass().getName()
                    + ": " + exception.getMessage());
        }
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
