package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Fail-closed runtime preflight for the Minecraft block-atlas animation seam. */
public final class Mm2BlockAtlasCanonicalizationCompatibility {
    private static volatile boolean armed;

    private Mm2BlockAtlasCanonicalizationCompatibility() {
    }

    public static void validateBeforeExport() {
        armed = false;
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            return;
        }

        List<String> failures = new ArrayList<>();
        requireVersion("minecraft", Mm2BlockAtlasCanonicalizationContract.MINECRAFT_VERSION,
                failures);
        requireVersion("forge", Mm2BlockAtlasCanonicalizationContract.FORGE_VERSION, failures);
        if (!NativeSpriteIconCompatibility.isArmed()) {
            failures.add("native sprite compatibility did not arm before block-atlas preflight");
        }
        for (Mm2BlockAtlasCanonicalizationContract.CoreClassPin pin
                : Mm2BlockAtlasCanonicalizationContract.CORE_CLASS_PINS) {
            validateClassResource(pin, failures);
        }
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                ReiExportMod.LOGGER.error(
                        "[reiexport] MM2 block-atlas canonicalization preflight failure: {}",
                        failure);
            }
            throw new IllegalStateException(
                    "MM2 block-atlas canonicalization rejected " + failures.size()
                            + " exact runtime contract(s)");
        }

        armed = true;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact MM2 request-scoped block-atlas canonicalization for "
                        + "Minecraft {} / Forge {}; animated native sprites will be uploaded once "
                        + "at their first declared frame and block-atlas animation ticks will be "
                        + "suppressed only while an exporter job is active; resourceStage={}; "
                        + "resourcePins={}",
                Mm2BlockAtlasCanonicalizationContract.MINECRAFT_VERSION,
                Mm2BlockAtlasCanonicalizationContract.FORGE_VERSION,
                Mm2BlockAtlasCanonicalizationContract.PRODUCTION_RESOURCE_STAGE,
                describePins());
    }

    public static boolean isArmed() {
        return armed;
    }

    private static void requireVersion(String modId, String expected, List<String> failures) {
        String actual = ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
        if (!expected.equals(actual)) {
            failures.add("version drift mod=" + modId + ", expected=" + expected
                    + ", actual=" + actual);
        }
    }

    private static void validateClassResource(
            Mm2BlockAtlasCanonicalizationContract.CoreClassPin pin,
            List<String> failures
    ) {
        try (InputStream input = Mm2BlockAtlasCanonicalizationCompatibility.class
                .getClassLoader().getResourceAsStream(pin.resource())) {
            if (input == null) {
                failures.add("missing production class resource stage="
                        + Mm2BlockAtlasCanonicalizationContract.PRODUCTION_RESOURCE_STAGE
                        + ", resource=" + pin.resource());
                return;
            }
            String actual = sha256(input);
            if (!pin.sha256().equals(actual)) {
                failures.add("production class resource drift stage="
                        + Mm2BlockAtlasCanonicalizationContract.PRODUCTION_RESOURCE_STAGE
                        + ", resource=" + pin.resource() + ", expectedSha256=" + pin.sha256()
                        + ", actualSha256=" + actual);
            }
        } catch (IOException exception) {
            failures.add("production class resource validation failed stage="
                    + Mm2BlockAtlasCanonicalizationContract.PRODUCTION_RESOURCE_STAGE
                    + ", resource=" + pin.resource() + ", cause=" + exception);
        }
    }

    private static String describePins() {
        return Mm2BlockAtlasCanonicalizationContract.CORE_CLASS_PINS.stream()
                .map(pin -> pin.resource() + "=" + pin.sha256())
                .toList()
                .toString();
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
