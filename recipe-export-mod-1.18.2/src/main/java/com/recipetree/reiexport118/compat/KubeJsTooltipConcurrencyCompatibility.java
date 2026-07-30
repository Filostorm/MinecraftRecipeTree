package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Validates and arms the optional exact KubeJS tooltip-publication repair. */
public final class KubeJsTooltipConcurrencyCompatibility {
    private KubeJsTooltipConcurrencyCompatibility() {
    }

    public static void validateBeforeReiRegistration() {
        String minecraftVersion = modVersion("minecraft");
        String forgeVersion = modVersion("forge");
        String reiVersion = modVersion("roughlyenoughitems");
        String kubeJsVersion = modVersion("kubejs");
        if (kubeJsVersion == null) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] KubeJS item.tooltip publication repair is explicitly not "
                            + "applicable: mod kubejs is absent"
            );
            return;
        }
        if (!KubeJsTooltipConcurrencyContract.isApplicable(minecraftVersion, kubeJsVersion)) {
            failClosed(List.of(
                    "runtime compatibility drift: required minecraft="
                            + KubeJsTooltipConcurrencyContract.MINECRAFT_VERSION
                            + ", kubejs=" + KubeJsTooltipConcurrencyContract.KUBEJS_VERSION
                            + "; actual minecraft=" + minecraftVersion
                            + ", kubejs=" + kubeJsVersion
                            + "; contextual forge=" + forgeVersion
                            + ", rei=" + reiVersion
            ), "runtime version preflight");
        }

        List<String> failures = new ArrayList<>();
        validateKubeJsJar(failures);
        validateClassResource(
                KubeJsTooltipConcurrencyContract.TARGET_RESOURCE,
                KubeJsTooltipConcurrencyContract.TARGET_SHA256,
                failures
        );
        validateClassResource(
                KubeJsTooltipConcurrencyContract.RELOAD_TARGET_RESOURCE,
                KubeJsTooltipConcurrencyContract.RELOAD_TARGET_SHA256,
                failures
        );
        validateReflectionSeam(failures);
        if (!failures.isEmpty()) {
            failClosed(failures, "bytecode/JAR preflight");
        }

        KubeJsTooltipPublicationRepair.arm();
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact KubeJS {} item.tooltip publication repair "
                        + "(minecraft={}, forge context={}, REI context={}): "
                        + "the pinned unsynchronized lazy-init seam is replaced by complete local-map "
                        + "construction plus lock/volatile publication; REI's two-thread search cache remains available",
                kubeJsVersion,
                minecraftVersion,
                forgeVersion,
                reiVersion
        );
    }

    /** Optional-safe request boundary used to reject exports after a terminal lifecycle failure. */
    public static void requireHealthyIfApplicable() {
        if (modVersion("kubejs") == null) {
            return;
        }
        KubeJsTooltipPublicationRepair.requireHealthy();
    }

    private static void validateReflectionSeam(List<String> failures) {
        try {
            Class<?> target = Class.forName(
                    KubeJsTooltipConcurrencyContract.TARGET_CLASS,
                    false,
                    KubeJsTooltipConcurrencyCompatibility.class.getClassLoader()
            );
            Field field = target.getDeclaredField(KubeJsTooltipConcurrencyContract.TARGET_FIELD);
            int fieldModifiers = field.getModifiers();
            if (field.getType() != Map.class
                    || !Modifier.isPublic(fieldModifiers)
                    || !Modifier.isStatic(fieldModifiers)
                    || Modifier.isFinal(fieldModifiers)
                    || Modifier.isVolatile(fieldModifiers)) {
                failures.add("KubeJS staticItemTooltips field seam drift: type="
                        + field.getType().getName() + ", modifiers="
                        + Modifier.toString(fieldModifiers));
            }

            Method method = target.getDeclaredMethod(
                    KubeJsTooltipConcurrencyContract.TARGET_METHOD,
                    ItemStack.class,
                    List.class,
                    TooltipFlag.class
            );
            int methodModifiers = method.getModifiers();
            if (method.getReturnType() != void.class
                    || !Modifier.isPrivate(methodModifiers)
                    || Modifier.isStatic(methodModifiers)) {
                failures.add("KubeJS itemTooltip method seam drift: returnType="
                        + method.getReturnType().getName() + ", modifiers="
                        + Modifier.toString(methodModifiers));
            }

            Class<?> eventClass = Class.forName(
                    KubeJsTooltipConcurrencyContract.TOOLTIP_EVENT_CLASS,
                    false,
                    KubeJsTooltipConcurrencyCompatibility.class.getClassLoader()
            );
            Constructor<?> constructor = eventClass.getConstructor(Map.class);
            if (!Modifier.isPublic(constructor.getModifiers())) {
                failures.add("KubeJS ItemTooltipEventJS(Map) constructor is no longer public");
            }

            Class<?> reloadTarget = Class.forName(
                    KubeJsTooltipConcurrencyContract.RELOAD_TARGET_CLASS,
                    false,
                    KubeJsTooltipConcurrencyCompatibility.class.getClassLoader()
            );
            Method reloadMethod = reloadTarget.getDeclaredMethod(
                    KubeJsTooltipConcurrencyContract.RELOAD_METHOD
            );
            int reloadModifiers = reloadMethod.getModifiers();
            if (reloadMethod.getReturnType() != void.class
                    || !Modifier.isPublic(reloadModifiers)
                    || !Modifier.isStatic(reloadModifiers)
                    || Modifier.isSynchronized(reloadModifiers)) {
                failures.add("KubeJS reloadClientScripts method seam drift: returnType="
                        + reloadMethod.getReturnType().getName() + ", modifiers="
                        + Modifier.toString(reloadModifiers));
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            failures.add("KubeJS tooltip reflection seam validation failed: "
                    + exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private static void validateKubeJsJar(List<String> failures) {
        IModFileInfo modFile = ModList.get().getModFileById("kubejs");
        if (modFile == null || modFile.getFile() == null) {
            failures.add("KubeJS mod file is unavailable");
            return;
        }
        validatePlainFile(
                modFile.getFile().getFilePath(),
                KubeJsTooltipConcurrencyContract.KUBEJS_JAR_SHA256,
                "KubeJS JAR",
                failures
        );
    }

    private static void validatePlainFile(
            Path path,
            String expectedSha256,
            String label,
            List<String> failures
    ) {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            failures.add(label + " is not a plain regular file: " + path);
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            String actual = sha256(input);
            if (!expectedSha256.equals(actual)) {
                failures.add(label + " drift path=" + path
                        + ", expectedSha256=" + expectedSha256
                        + ", actualSha256=" + actual);
            }
        } catch (IOException exception) {
            failures.add(label + " validation failed path=" + path + ", exception="
                    + exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private static void validateClassResource(
            String resourcePath,
            String expectedSha256,
            List<String> failures
    ) {
        try (InputStream input = KubeJsTooltipConcurrencyCompatibility.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                failures.add("missing class resource=" + resourcePath);
                return;
            }
            String actual = sha256(input);
            if (!expectedSha256.equals(actual)) {
                failures.add("class bytecode drift resource=" + resourcePath
                        + ", expectedSha256=" + expectedSha256
                        + ", actualSha256=" + actual);
            }
        } catch (IOException exception) {
            failures.add("class bytecode validation failed resource=" + resourcePath
                    + ", exception=" + exception.getClass().getName()
                    + ": " + exception.getMessage());
        }
    }

    private static String sha256(InputStream input) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static void failClosed(List<String> failures, String phase) {
        for (String failure : failures) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] KubeJS tooltip publication compatibility failure ({}): {}",
                    phase,
                    failure
            );
        }
        throw new IllegalStateException(
                "KubeJS tooltip publication compatibility rejected " + failures.size()
                        + " contract(s) during " + phase
        );
    }
}
