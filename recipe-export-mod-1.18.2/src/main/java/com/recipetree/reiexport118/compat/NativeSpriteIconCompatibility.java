package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Pins and arms the audited MM2 runtime before REI constructs any entry renderers. */
public final class NativeSpriteIconCompatibility {
    private static final String INGREDIENT_RENDERER_INTERFACE =
            "mezz.jei.api.ingredients.IIngredientRenderer";
    private static volatile boolean armed;

    private NativeSpriteIconCompatibility() {
    }

    public static void validateBeforeReiRegistration() {
        armed = false;
        String minecraftVersion = modVersion("minecraft");
        String forgeVersion = modVersion("forge");
        String reiVersion = modVersion("roughlyenoughitems");
        String compatVersion = modVersion("rei_plugin_compatibilities");
        String architecturyVersion = modVersion("architectury");
        String mekanismVersion = modVersion("mekanism");

        if (!NativeSpriteIconContract.isApplicable(
                minecraftVersion,
                forgeVersion,
                reiVersion,
                compatVersion,
                architecturyVersion,
                mekanismVersion
        )) {
            if (mekanismVersion != null || compatVersion != null) {
                ReiExportMod.LOGGER.info(
                        "[reiexport] Native fluid/gas icon correction not armed; required minecraft={}, forge={}, rei={}, rei-jei-compat={}, architectury={}, mekanism={}; actual minecraft={}, forge={}, rei={}, rei-jei-compat={}, architectury={}, mekanism={}",
                        NativeSpriteIconContract.MINECRAFT_VERSION,
                        NativeSpriteIconContract.FORGE_VERSION,
                        NativeSpriteIconContract.REI_VERSION,
                        NativeSpriteIconContract.REI_JEI_COMPAT_VERSION,
                        NativeSpriteIconContract.ARCHITECTURY_VERSION,
                        NativeSpriteIconContract.MEKANISM_VERSION,
                        minecraftVersion,
                        forgeVersion,
                        reiVersion,
                        compatVersion,
                        architecturyVersion,
                        mekanismVersion
                );
            }
            return;
        }

        List<String> failures = new ArrayList<>();
        validateClassResource(
                NativeSpriteIconContract.REI_FLUID_RENDERER_RESOURCE,
                NativeSpriteIconContract.REI_FLUID_RENDERER_SHA256,
                failures
        );
        validateClassResource(
                NativeSpriteIconContract.JEI_RENDERER_WRAPPER_RESOURCE,
                NativeSpriteIconContract.JEI_RENDERER_WRAPPER_SHA256,
                failures
        );
        validateClassResource(
                NativeSpriteIconContract.JEI_FLUID_RENDERER_RESOURCE,
                NativeSpriteIconContract.JEI_FLUID_RENDERER_SHA256,
                failures
        );
        validateClassResource(
                NativeSpriteIconContract.ARCHITECTURY_FLUID_STACK_RESOURCE,
                NativeSpriteIconContract.ARCHITECTURY_FLUID_STACK_SHA256,
                failures
        );
        validateClassResource(
                NativeSpriteIconContract.ARCHITECTURY_FLUID_HOOKS_RESOURCE,
                NativeSpriteIconContract.ARCHITECTURY_FLUID_HOOKS_SHA256,
                failures
        );
        validateClassResource(
                NativeSpriteIconContract.MEKANISM_GAS_STACK_RESOURCE,
                NativeSpriteIconContract.MEKANISM_GAS_STACK_SHA256,
                failures
        );
        validateClassResource(
                NativeSpriteIconContract.MEKANISM_CHEMICAL_RESOURCE,
                NativeSpriteIconContract.MEKANISM_CHEMICAL_SHA256,
                failures
        );
        validateClassResource(
                NativeSpriteIconContract.MEKANISM_CHEMICAL_RENDERER_RESOURCE,
                NativeSpriteIconContract.MEKANISM_CHEMICAL_RENDERER_SHA256,
                failures
        );
        validateWrapperField(failures);
        if (!failures.isEmpty()) {
            failClosed(failures);
        }

        armed = true;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact MM2 deterministic native fluid/gas icon repair for REI {}, REI JEI compatibility {}, Architectury {}, and Mekanism {}; native EntryStack.render remains primary and replacement is permitted only when the exact runtime sprite restores lost alpha coverage or canonicalizes a differing animated capture to its first declared frame; correction evidence is measured on the logical 16x16 grid and corrected pixels are rasterized at the requested iconScale",
                reiVersion,
                compatVersion,
                architecturyVersion,
                mekanismVersion
        );
    }

    public static boolean isArmed() {
        return armed;
    }

    private static void validateWrapperField(List<String> failures) {
        try {
            Class<?> wrapperClass = Class.forName(
                    NativeSpriteIconContract.JEI_RENDERER_WRAPPER_CLASS,
                    false,
                    NativeSpriteIconCompatibility.class.getClassLoader()
            );
            Field field = wrapperClass.getDeclaredField("ingredientRenderer");
            if (!INGREDIENT_RENDERER_INTERFACE.equals(field.getType().getName())) {
                failures.add("JEI renderer wrapper ingredientRenderer type drift: expected="
                        + INGREDIENT_RENDERER_INTERFACE + ", actual=" + field.getType().getName());
            }
            int modifiers = field.getModifiers();
            if (!Modifier.isPrivate(modifiers) || !Modifier.isFinal(modifiers)) {
                failures.add("JEI renderer wrapper ingredientRenderer modifiers drift: actual="
                        + Modifier.toString(modifiers));
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            failures.add("JEI renderer wrapper field validation failed: "
                    + exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private static void validateClassResource(
            String resourcePath,
            String expectedSha256,
            List<String> failures
    ) {
        try (InputStream input = NativeSpriteIconCompatibility.class.getClassLoader()
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
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (!expectedSha256.equals(actualSha256)) {
                failures.add("class bytecode drift resource=" + resourcePath
                        + ", expectedSha256=" + expectedSha256
                        + ", actualSha256=" + actualSha256);
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

    private static void failClosed(List<String> failures) {
        for (String failure : failures) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Native fluid/gas icon compatibility preflight failure: {}",
                    failure
            );
        }
        throw new IllegalStateException(
                "Native fluid/gas icon compatibility rejected " + failures.size()
                        + " exact runtime contract(s) during bytecode preflight"
        );
    }
}
