package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Applies LaserIO's intended hide operation without fabricating pack-removed reset recipes. */
public final class LaserIoJeiRuntimeCompatibility {
    private static volatile boolean armed;

    private LaserIoJeiRuntimeCompatibility() {
    }

    public static void validateBeforeReiRegistration() {
        armed = false;
        String minecraftVersion = modVersion("minecraft");
        String forgeVersion = modVersion("forge");
        String laserIoVersion = modVersion("laserio");
        String reiVersion = modVersion("roughlyenoughitems");
        String compatVersion = modVersion("rei_plugin_compatibilities");
        String jeiApiVersion = modVersion("jei");
        if (!LaserIoJeiRuntimeContract.isApplicable(
                minecraftVersion,
                forgeVersion,
                laserIoVersion,
                reiVersion,
                compatVersion,
                jeiApiVersion
        )) {
            if (laserIoVersion != null || compatVersion != null) {
                ReiExportMod.LOGGER.info(
                        "[reiexport] LaserIO/REI JEI-runtime compatibility not applied; required minecraft={}, forge={}, laserio={}, rei={}, rei-jei-compat={}, jei-api={}; actual minecraft={}, forge={}, laserio={}, rei={}, rei-jei-compat={}, jei-api={}",
                        LaserIoJeiRuntimeContract.MINECRAFT_VERSION,
                        LaserIoJeiRuntimeContract.FORGE_VERSION,
                        LaserIoJeiRuntimeContract.LASER_IO_VERSION,
                        LaserIoJeiRuntimeContract.REI_VERSION,
                        LaserIoJeiRuntimeContract.REI_JEI_COMPAT_VERSION,
                        LaserIoJeiRuntimeContract.JEI_API_VERSION,
                        minecraftVersion,
                        forgeVersion,
                        laserIoVersion,
                        reiVersion,
                        compatVersion,
                        jeiApiVersion
                );
            }
            return;
        }

        List<String> failures = new ArrayList<>();
        validateClassResource(
                LaserIoJeiRuntimeContract.PLUGIN_CLASS_RESOURCE,
                LaserIoJeiRuntimeContract.PLUGIN_CLASS_SHA256,
                failures
        );
        validateClassResource(
                LaserIoJeiRuntimeContract.RUNTIME_CLASS_RESOURCE,
                LaserIoJeiRuntimeContract.RUNTIME_CLASS_SHA256,
                failures
        );
        validateClassResource(
                LaserIoJeiRuntimeContract.RECIPE_MANAGER_CLASS_RESOURCE,
                LaserIoJeiRuntimeContract.RECIPE_MANAGER_CLASS_SHA256,
                failures
        );
        if (!failures.isEmpty()) {
            failClosed(failures, "bytecode preflight");
        }

        armed = true;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact LaserIO {}/REI JEI compatibility for pluginClass={} and recipeManagerClass={}; runtime corpus must contain 4 pack-removed card resets and 4 extant filter resets",
                laserIoVersion,
                LaserIoJeiRuntimeContract.PLUGIN_CLASS,
                LaserIoJeiRuntimeContract.RECIPE_MANAGER_CLASS
        );
    }

    /**
     * @return true when the exact replacement ran and the original broken hook must be cancelled
     */
    public static boolean hideExactResetRecipeCorpus(IJeiRuntime jeiRuntime) {
        if (!armed) {
            return false;
        }

        List<String> failures = new ArrayList<>();
        if (jeiRuntime == null
                || !LaserIoJeiRuntimeContract.RUNTIME_CLASS.equals(jeiRuntime.getClass().getName())) {
            failures.add("runtime class drift: actual=" + className(jeiRuntime)
                    + ", expected=" + LaserIoJeiRuntimeContract.RUNTIME_CLASS);
        }

        IRecipeManager jeiRecipeManager = jeiRuntime == null ? null : jeiRuntime.getRecipeManager();
        if (jeiRecipeManager == null
                || !LaserIoJeiRuntimeContract.RECIPE_MANAGER_CLASS.equals(
                jeiRecipeManager.getClass().getName()
        )) {
            failures.add("recipe manager class drift: actual=" + className(jeiRecipeManager)
                    + ", expected=" + LaserIoJeiRuntimeContract.RECIPE_MANAGER_CLASS);
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            failures.add("Minecraft client level is null during LaserIO onRuntimeAvailable");
        }
        if (!failures.isEmpty()) {
            failClosed(failures, "runtime object validation");
        }

        RecipeManager minecraftRecipeManager = level.getRecipeManager();
        Set<String> observedResetRecipeIds = new LinkedHashSet<>();
        Set<String> observedReplacementRecipeIds = new LinkedHashSet<>();
        Map<String, Recipe<?>> observedRecipes = new java.util.LinkedHashMap<>();

        for (String recipeId : LaserIoJeiRuntimeContract.allResetRecipeIds()) {
            recipeById(minecraftRecipeManager, recipeId).ifPresent(recipe -> {
                observedResetRecipeIds.add(recipeId);
                observedRecipes.put(recipeId, recipe);
            });
        }
        for (String replacementId : LaserIoJeiRuntimeContract.packRemovedCardResets().values()) {
            recipeById(minecraftRecipeManager, replacementId).ifPresent(
                    recipe -> observedReplacementRecipeIds.add(replacementId)
            );
        }

        try {
            LaserIoJeiRuntimeContract.requireExactCorpus(
                    observedResetRecipeIds,
                    observedReplacementRecipeIds
            );
        } catch (IllegalStateException exception) {
            failures.add(exception.getMessage());
        }

        List<CraftingRecipe> recipesToHide = new ArrayList<>();
        for (Map.Entry<String, String> entry
                : LaserIoJeiRuntimeContract.presentFilterResets().entrySet()) {
            Recipe<?> recipe = observedRecipes.get(entry.getKey());
            validateFilterResetRecipe(entry.getKey(), entry.getValue(), recipe, recipesToHide, failures);
        }
        if (!failures.isEmpty()) {
            failClosed(failures, "recipe corpus validation before mutation");
        }

        try {
            jeiRecipeManager.hideRecipes(RecipeTypes.CRAFTING, recipesToHide);
        } catch (RuntimeException exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] LaserIO/REI JEI compatibility hide operation failed explicitly",
                    exception
            );
            throw exception;
        }

        for (String recipeId : LaserIoJeiRuntimeContract.packRemovedCardResets().keySet()) {
            ReiExportMod.LOGGER.warn(
                    "[reiexport] Corrected LaserIO stale hide lookup: recipe={} is intentionally absent from the MM2 corpus; no recipe was fabricated",
                    recipeId
            );
        }
        for (String recipeId : LaserIoJeiRuntimeContract.presentFilterResets().keySet()) {
            ReiExportMod.LOGGER.warn(
                    "[reiexport] Preserved LaserIO's intended JEI visibility rule: hid extant reset recipe={} from the crafting display corpus",
                    recipeId
            );
        }
        ReiExportMod.LOGGER.warn(
                "[reiexport] LaserIO {}/REI JEI-runtime compatibility passed: accepted 4 exact pack-removed card reset recipes, hid 4 exact extant filter reset recipes, and cancelled only the bytecode-verified failing hook; no recipes or providers were fabricated",
                LaserIoJeiRuntimeContract.LASER_IO_VERSION
        );
        return true;
    }

    private static void validateFilterResetRecipe(
            String recipeId,
            String expectedResultItemId,
            Recipe<?> recipe,
            List<CraftingRecipe> recipesToHide,
            List<String> failures
    ) {
        if (!(recipe instanceof CraftingRecipe)) {
            failures.add("reset recipe class drift id=" + recipeId
                    + ", actual=" + className(recipe)
                    + ", expected CraftingRecipe");
            return;
        }
        if (!ShapelessRecipe.class.equals(recipe.getClass())) {
            failures.add("reset recipe implementation drift id=" + recipeId
                    + ", actual=" + recipe.getClass().getName()
                    + ", expected=" + ShapelessRecipe.class.getName());
            return;
        }
        ResourceLocation serializerId = ForgeRegistries.RECIPE_SERIALIZERS.getKey(
                recipe.getSerializer()
        );
        ItemStack result = recipe.getResultItem();
        ResourceLocation resultItemId = result.isEmpty()
                ? null
                : ForgeRegistries.ITEMS.getKey(result.getItem());
        if (serializerId == null
                || !"minecraft:crafting_shapeless".equals(serializerId.toString())
                || resultItemId == null
                || !expectedResultItemId.equals(resultItemId.toString())
                || recipe.getIngredients().size() != 1) {
            failures.add("reset recipe tuple drift id=" + recipeId
                    + ", serializer=" + serializerId
                    + ", result=" + resultItemId
                    + ", ingredientCount=" + recipe.getIngredients().size());
            return;
        }
        recipesToHide.add((CraftingRecipe) recipe);
    }

    private static Optional<? extends Recipe<?>> recipeById(
            RecipeManager recipeManager,
            String recipeId
    ) {
        return recipeManager.byKey(new ResourceLocation(recipeId));
    }

    private static void validateClassResource(
            String resourcePath,
            String expectedSha256,
            List<String> failures
    ) {
        try (InputStream input = LaserIoJeiRuntimeCompatibility.class.getClassLoader()
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
            String actualSha256 = java.util.HexFormat.of().formatHex(digest.digest());
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

    private static String className(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    private static void failClosed(List<String> failures, String phase) {
        for (String failure : failures) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] LaserIO/REI JEI-runtime compatibility failure ({}): {}",
                    phase,
                    failure
            );
        }
        throw new IllegalStateException(
                "LaserIO/REI JEI-runtime compatibility rejected " + failures.size()
                        + " contract(s) during " + phase
        );
    }
}
