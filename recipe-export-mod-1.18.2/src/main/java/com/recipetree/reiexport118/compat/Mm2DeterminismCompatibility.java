package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import com.recipetree.reiexport118.mixin.ReiExportMixinConfigPlugin;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Validates and arms only byte-for-byte audited MM2 repairs. */
public final class Mm2DeterminismCompatibility {
    private static final Set<String> ARMED_MODS = ConcurrentHashMap.newKeySet();
    private static volatile boolean lifecycleArmed;

    private Mm2DeterminismCompatibility() {
    }

    public static boolean validateBeforeReiRegistration() {
        ARMED_MODS.clear();
        lifecycleArmed = false;
        Mm2ExportRequestScope.Inspection requestScope =
                Mm2ExportRequestScope.inspect(FMLLoader.getGamePath());
        if (!requestScope.isExactMm2()) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] Exact MM2 compatibility repairs DISABLED: no exporter request "
                            + "exists at {}; no target JAR or class was validated and no repair "
                            + "module was armed",
                    requestScope.requestPath());
            return false;
        }
        ReiExportMixinConfigPlugin.requireExactMm2RequestSelection(
                requestScope.requestPath().getParent());
        List<String> failures = new ArrayList<>();
        for (Mm2DeterminismContract.ModPin pin : Mm2DeterminismContract.LIFECYCLE_SIGNATURE) {
            int failuresBeforePin = failures.size();
            String actualVersion = modVersion(pin.modId());
            if (actualVersion == null) {
                failures.add("required MM2 mod is absent mod=" + pin.modId());
                continue;
            }
            if (!pin.version().equals(actualVersion)) {
                failures.add("mod version drift mod=" + pin.modId()
                        + ", expected=" + pin.version() + ", actual=" + actualVersion);
                continue;
            }
            validateModJar(pin, failures);
            for (Mm2DeterminismContract.ClassPin classPin : Mm2DeterminismContract.CLASS_PINS) {
                if (pin.modId().equals(classPin.modId())) {
                    validateClass(classPin, failures);
                }
            }
            if (failures.size() == failuresBeforePin) {
                ARMED_MODS.add(pin.modId());
            }
        }
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                ReiExportMod.LOGGER.error(
                        "[reiexport] MM2 deterministic-export preflight failure: {}", failure);
            }
            throw new IllegalStateException("MM2 deterministic-export preflight rejected "
                    + failures.size() + " exact contract(s)");
        }

        lifecycleArmed = Mm2DeterminismContract.LIFECYCLE_SIGNATURE.stream()
                .allMatch(pin -> ARMED_MODS.contains(pin.modId()));
        if (lifecycleArmed) {
            Mm2ReiLifecycleGate.arm();
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] MM2 deterministic-export preflight armed modules={} lifecycleGate={}",
                ARMED_MODS, lifecycleArmed);
        return true;
    }

    public static void requireArmed(String modId) {
        if (!ARMED_MODS.contains(modId)) {
            throw new IllegalStateException(
                    "MM2 deterministic repair executed before exact preflight arm: " + modId);
        }
    }

    public static boolean isArmed(String modId) {
        return ARMED_MODS.contains(modId);
    }

    public static boolean isLifecycleArmed() {
        return lifecycleArmed;
    }

    private static void validateModJar(
            Mm2DeterminismContract.ModPin pin,
            List<String> failures
    ) {
        IModFileInfo fileInfo = ModList.get().getModFileById(pin.modId());
        Path path = fileInfo == null || fileInfo.getFile() == null
                ? null : fileInfo.getFile().getFilePath();
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("mod JAR is not a plain file mod=" + pin.modId() + ", path=" + path);
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            String actual = sha256(input);
            if (!pin.jarSha256().equals(actual)) {
                failures.add("mod JAR drift mod=" + pin.modId() + ", path=" + path
                        + ", expectedSha256=" + pin.jarSha256() + ", actualSha256=" + actual);
            }
        } catch (IOException exception) {
            failures.add("mod JAR validation failed mod=" + pin.modId() + ", path=" + path
                    + ", cause=" + exception);
        }
    }

    private static void validateClass(
            Mm2DeterminismContract.ClassPin pin,
            List<String> failures
    ) {
        try (InputStream input = Mm2DeterminismCompatibility.class.getClassLoader()
                .getResourceAsStream(pin.resource())) {
            if (input == null) {
                failures.add("missing class resource mod=" + pin.modId()
                        + ", resource=" + pin.resource());
                return;
            }
            String actual = sha256(input);
            if (!pin.sha256().equals(actual)) {
                failures.add("class drift mod=" + pin.modId() + ", resource=" + pin.resource()
                        + ", expectedSha256=" + pin.sha256() + ", actualSha256=" + actual);
            }
        } catch (IOException exception) {
            failures.add("class validation failed mod=" + pin.modId()
                    + ", resource=" + pin.resource() + ", cause=" + exception);
        }
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
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
