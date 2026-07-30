package com.recipetree.reiexport118.compat;

import capsule.items.CapsuleItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.recipetree.reiexport118.ReiExportMod;
import me.shedaniel.rei.api.client.config.ConfigObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Hydrates Capsule's idempotent client cache before Architectury's HIGH-priority recipe event
 * bridge enters REI's synchronous reload. Capsule's own NORMAL-priority subscriber still runs.
 */
public final class CapsuleRecipeSyncCompatibility {
    private static volatile boolean armed;

    private CapsuleRecipeSyncCompatibility() {
    }

    public static void validateBeforeReiRegistration() {
        armed = false;
        String minecraftVersion = modVersion("minecraft");
        String forgeVersion = modVersion("forge");
        String capsuleVersion = modVersion("capsule");
        String reiVersion = modVersion("roughlyenoughitems");
        String compatVersion = modVersion("rei_plugin_compatibilities");
        String architecturyVersion = modVersion("architectury");

        if (!CapsuleRecipeSyncContract.isApplicable(
                minecraftVersion,
                forgeVersion,
                capsuleVersion,
                reiVersion,
                compatVersion,
                architecturyVersion
        )) {
            failClosed(List.of(
                    "runtime tuple drift: required minecraft="
                            + CapsuleRecipeSyncContract.MINECRAFT_VERSION
                            + ", forge=" + CapsuleRecipeSyncContract.FORGE_VERSION
                            + ", capsule=" + CapsuleRecipeSyncContract.CAPSULE_VERSION
                            + ", rei=" + CapsuleRecipeSyncContract.REI_VERSION
                            + ", rei-jei-compat="
                            + CapsuleRecipeSyncContract.REI_JEI_COMPAT_VERSION
                            + ", architectury="
                            + CapsuleRecipeSyncContract.ARCHITECTURY_VERSION
                            + "; actual minecraft=" + minecraftVersion
                            + ", forge=" + forgeVersion
                            + ", capsule=" + capsuleVersion
                            + ", rei=" + reiVersion
                            + ", rei-jei-compat=" + compatVersion
                            + ", architectury=" + architecturyVersion
            ), "runtime version preflight");
        }

        List<String> failures = new ArrayList<>();
        validateCapsuleJar(failures);
        validateClassResource(
                CapsuleRecipeSyncContract.CAPSULE_ITEMS_RESOURCE,
                CapsuleRecipeSyncContract.CAPSULE_ITEMS_SHA256,
                failures
        );
        validateClassResource(
                CapsuleRecipeSyncContract.CAPSULE_PLUGIN_RESOURCE,
                CapsuleRecipeSyncContract.CAPSULE_PLUGIN_SHA256,
                failures
        );
        validateClassResource(
                CapsuleRecipeSyncContract.CAPSULE_FORGE_SUBSCRIBER_RESOURCE,
                CapsuleRecipeSyncContract.CAPSULE_FORGE_SUBSCRIBER_SHA256,
                failures
        );
        validateClassResource(
                CapsuleRecipeSyncContract.REI_CORE_CLIENT_RESOURCE,
                CapsuleRecipeSyncContract.REI_CORE_CLIENT_SHA256,
                failures
        );
        validateClassResource(
                CapsuleRecipeSyncContract.ARCHITECTURY_EVENT_HANDLER_RESOURCE,
                CapsuleRecipeSyncContract.ARCHITECTURY_EVENT_HANDLER_SHA256,
                failures
        );
        if (!failures.isEmpty()) {
            failClosed(failures, "bytecode/JAR preflight");
        }

        armed = true;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact Capsule {} synchronous recipe-cache ordering repair: exporter HIGHEST hydration precedes Architectury HIGH/REI and preserves Capsule's NORMAL idempotent rebuild; Capsule JAR and all lifecycle participants are byte/version pinned",
                capsuleVersion
        );
    }

    public static void hydrateBeforeSynchronousRei(RecipeManager recipeManager) {
        if (!armed) {
            failClosed(List.of(
                    "compatibility is not armed; FML client-setup preflight did not complete "
                            + "successfully before RecipesUpdatedEvent"
            ), "RecipesUpdatedEvent");
        }
        if (ConfigObject.getInstance().doesRegisterRecipesInAnotherThread()) {
            failClosed(
                    List.of("REI registerRecipesInAnotherThread=true; the exact ordering repair "
                            + "requires serialized registration and refuses concurrent Capsule cache mutation"),
                    "RecipesUpdatedEvent"
            );
        }
        if (recipeManager == null) {
            failClosed(List.of("RecipesUpdatedEvent supplied a null RecipeManager"),
                    "RecipesUpdatedEvent");
        }
        if (!RenderSystem.isOnRenderThread()) {
            failClosed(List.of(
                    "RecipesUpdatedEvent is not executing on Minecraft's render thread: actual="
                            + Thread.currentThread().getName()
            ), "RecipesUpdatedEvent");
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            failClosed(List.of("active Minecraft client connection is null"),
                    "RecipesUpdatedEvent");
        }
        RecipeManager activeRecipeManager = connection.getRecipeManager();
        if (activeRecipeManager != recipeManager) {
            failClosed(List.of(
                    "RecipesUpdatedEvent RecipeManager identity differs from the active client "
                            + "connection manager: eventIdentity="
                            + System.identityHashCode(recipeManager)
                            + ", activeIdentity=" + System.identityHashCode(activeRecipeManager)
            ), "RecipesUpdatedEvent");
        }

        RecipeCorpus before = recipeCorpus(recipeManager);
        try {
            CapsuleItems.registerRecipesClient(recipeManager);
        } catch (RuntimeException | LinkageError exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Exact Capsule client recipe-cache hydration failed explicitly",
                    exception
            );
            throw exception;
        }

        List<String> failures = new ArrayList<>();
        RecipeCorpus after = recipeCorpus(recipeManager);
        if (!before.equals(after)) {
            failures.add("Capsule hydration mutated the authoritative RecipeManager corpus: before="
                    + before + ", after=" + after);
        }
        if (CapsuleItems.unlabelledCapsule == null) {
            failures.add("unlabelledCapsule is null");
        }
        if (CapsuleItems.deployedCapsule == null) {
            failures.add("deployedCapsule is null");
        }
        if (CapsuleItems.recoveryCapsule == null) {
            failures.add("recoveryCapsule is null");
        }
        if (CapsuleItems.blueprintChangedCapsule == null) {
            failures.add("blueprintChangedCapsule is null");
        }
        if (CapsuleItems.upgradedCapsule == null) {
            failures.add("upgradedCapsule is null");
        }
        if (!failures.isEmpty()) {
            failClosed(failures, "post-hydration required-field validation");
        }

        CapsuleRecipeSyncContract.HydratedSnapshot snapshot =
                new CapsuleRecipeSyncContract.HydratedSnapshot(
                        recipeId(CapsuleItems.upgradedCapsule.getValue()),
                        recipeId(CapsuleItems.recoveryCapsule.getValue()),
                        recipeId(CapsuleItems.blueprintChangedCapsule.getValue()),
                        CapsuleItems.capsuleList.size(),
                        CapsuleItems.opCapsuleList.size(),
                        CapsuleItems.blueprintCapsules.size(),
                        CapsuleItems.blueprintPrefabs.size()
                );
        try {
            CapsuleRecipeSyncContract.requireHydratedSnapshot(snapshot);
        } catch (IllegalStateException exception) {
            failClosed(List.of(exception.getMessage()), "post-hydration corpus validation");
        }

        ReiExportMod.LOGGER.warn(
                "[reiexport] Capsule synchronous recipe cache hydrated before REI: managerCapsuleRecipes={}, managerCorpusSha256={}, regularCapsules={}, overpoweredCapsules={}, blueprintCapsules={}, blueprintPrefabs={}; authoritative RecipeManager corpus remained unchanged and Capsule's recipe provider was not suppressed",
                before.count(),
                before.sha256(),
                snapshot.regularCapsules(),
                snapshot.overpoweredCapsules(),
                snapshot.blueprintCapsules(),
                snapshot.blueprintPrefabs()
        );
    }

    private static RecipeCorpus recipeCorpus(RecipeManager recipeManager) {
        List<Recipe<?>> recipes = recipeManager.getRecipes().stream()
                .filter(recipe -> "capsule".equals(recipe.getId().getNamespace()))
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
                .toList();
        MessageDigest digest = sha256();
        for (Recipe<?> recipe : recipes) {
            updateUtf8(digest, recipe.getId().toString());
            updateUtf8(digest, recipe.getClass().getName());
        }
        return new RecipeCorpus(recipes.size(), HexFormat.of().formatHex(digest.digest()));
    }

    private static String recipeId(Recipe<?> recipe) {
        ResourceLocation id = recipe == null ? null : recipe.getId();
        return id == null ? null : id.toString();
    }

    private static void updateUtf8(MessageDigest digest, String value) {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static void validateCapsuleJar(List<String> failures) {
        IModFileInfo modFile = ModList.get().getModFileById("capsule");
        if (modFile == null || modFile.getFile() == null) {
            failures.add("Capsule mod file is unavailable");
            return;
        }
        Path path = modFile.getFile().getFilePath();
        try (InputStream input = Files.newInputStream(path)) {
            String actual = sha256(input);
            if (!CapsuleRecipeSyncContract.CAPSULE_JAR_SHA256.equals(actual)) {
                failures.add("Capsule JAR drift path=" + path
                        + ", expectedSha256=" + CapsuleRecipeSyncContract.CAPSULE_JAR_SHA256
                        + ", actualSha256=" + actual);
            }
        } catch (IOException exception) {
            failures.add("Capsule JAR validation failed path=" + path + ", exception="
                    + exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private static void validateClassResource(
            String resourcePath,
            String expectedSha256,
            List<String> failures
    ) {
        try (InputStream input = CapsuleRecipeSyncCompatibility.class.getClassLoader()
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
        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static void failClosed(List<String> failures, String phase) {
        for (String failure : failures) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Capsule synchronous recipe-cache compatibility failure ({}): {}",
                    phase,
                    failure
            );
        }
        throw new IllegalStateException(
                "Capsule synchronous recipe-cache compatibility rejected " + failures.size()
                        + " contract(s) during " + phase
        );
    }

    private record RecipeCorpus(int count, String sha256) {
    }
}
