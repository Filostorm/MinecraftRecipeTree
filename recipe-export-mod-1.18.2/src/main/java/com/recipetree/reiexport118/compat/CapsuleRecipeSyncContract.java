package com.recipetree.reiexport118.compat;

import java.util.Objects;

/** Exact byte/version contract for Capsule's client recipe-cache lifecycle in MM2. */
public final class CapsuleRecipeSyncContract {
    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String CAPSULE_VERSION = "1.18.2-6.0.99";
    public static final String REI_VERSION = "8.4.778";
    public static final String REI_JEI_COMPAT_VERSION = "8.0.89";
    public static final String ARCHITECTURY_VERSION = "4.12.94";

    public static final String CAPSULE_JAR_SHA256 =
            "508099f9dd92e919d04ba0bc94855e23da60d1270b5b1a25c0ce4f0476418e6f";
    public static final String CAPSULE_ITEMS_RESOURCE = "capsule/items/CapsuleItems.class";
    public static final String CAPSULE_ITEMS_SHA256 =
            "4c86a7e813e026039986a8221d1f131cf4f380806e02133d8a70f28f9d6dfb7b";
    public static final String CAPSULE_PLUGIN_RESOURCE =
            "capsule/plugins/jei/CapsulePlugin.class";
    public static final String CAPSULE_PLUGIN_SHA256 =
            "d13ab0f33c3a9c33d92d499608414b205172b30025fdb9435cd048ee8155bece";
    public static final String CAPSULE_FORGE_SUBSCRIBER_RESOURCE =
            "capsule/CapsuleForgeSubscriber.class";
    public static final String CAPSULE_FORGE_SUBSCRIBER_SHA256 =
            "c168b23406ab5191e2b0a24ea494754e5f13b14f640a35547558415a0d7b97a4";
    public static final String REI_CORE_CLIENT_RESOURCE =
            "me/shedaniel/rei/RoughlyEnoughItemsCoreClient.class";
    public static final String REI_CORE_CLIENT_SHA256 =
            "848b06b48665e917500d920f40cfb03fb2870b20a2f0ab6cbbb44eee828ea1ac";
    public static final String ARCHITECTURY_EVENT_HANDLER_RESOURCE =
            "dev/architectury/event/forge/EventHandlerImplClient.class";
    public static final String ARCHITECTURY_EVENT_HANDLER_SHA256 =
            "807dcd7f5b4fbeb25be4654ccf8511b7090229c3e9e9b15079f9df1dc828c658";

    public static final String UPGRADE_RECIPE_ID = "capsule:upgrade";
    public static final String RECOVERY_RECIPE_ID = "capsule:recovery";
    public static final String BLUEPRINT_CHANGE_RECIPE_ID = "capsule:blueprint_change";

    public record HydratedSnapshot(
            String upgradeRecipeId,
            String recoveryRecipeId,
            String blueprintChangeRecipeId,
            int regularCapsules,
            int overpoweredCapsules,
            int blueprintCapsules,
            int blueprintPrefabs
    ) {
    }

    private CapsuleRecipeSyncContract() {
    }

    public static boolean isApplicable(
            String minecraftVersion,
            String forgeVersion,
            String capsuleVersion,
            String reiVersion,
            String reiJeiCompatVersion,
            String architecturyVersion
    ) {
        return MINECRAFT_VERSION.equals(minecraftVersion)
                && FORGE_VERSION.equals(forgeVersion)
                && CAPSULE_VERSION.equals(capsuleVersion)
                && REI_VERSION.equals(reiVersion)
                && REI_JEI_COMPAT_VERSION.equals(reiJeiCompatVersion)
                && ARCHITECTURY_VERSION.equals(architecturyVersion);
    }

    public static void requireHydratedSnapshot(HydratedSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireRecipeId("upgrade", UPGRADE_RECIPE_ID, snapshot.upgradeRecipeId());
        requireRecipeId("recovery", RECOVERY_RECIPE_ID, snapshot.recoveryRecipeId());
        requireRecipeId(
                "blueprint change",
                BLUEPRINT_CHANGE_RECIPE_ID,
                snapshot.blueprintChangeRecipeId()
        );
        requirePositive("regular capsule recipes", snapshot.regularCapsules());
        requirePositive("overpowered capsule recipes", snapshot.overpoweredCapsules());
        requirePositive("blueprint capsule recipes", snapshot.blueprintCapsules());
        requirePositive("blueprint prefab recipes", snapshot.blueprintPrefabs());
    }

    private static void requireRecipeId(String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Capsule " + name + " recipe drift: expected="
                    + expected + ", actual=" + actual);
        }
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalStateException("Capsule cache is missing " + name
                    + ": observed=" + value);
        }
    }
}
