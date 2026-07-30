package com.recipetree.reiexport118.compat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exact Multiblock Madness 2 contract for LaserIO's JEI runtime recipe-hiding hook.
 *
 * <p>The pack removes the four card reset recipes while replacing the cards' normal crafting
 * recipes. LaserIO 1.4.5 calls {@code Optional.get()} for all eight reset recipe IDs, so the first
 * absent card recipe aborts the hook before its four extant filter reset recipes can be hidden.</p>
 */
public final class LaserIoJeiRuntimeContract {
    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String LASER_IO_VERSION = "1.4.5";
    public static final String REI_VERSION = "8.4.778";
    public static final String REI_JEI_COMPAT_VERSION = "8.0.89";
    public static final String JEI_API_VERSION = "9.9999";

    public static final String PLUGIN_CLASS = "com.direwolf20.laserio.client.jei.JEIIntegration";
    public static final String PLUGIN_CLASS_RESOURCE =
            "com/direwolf20/laserio/client/jei/JEIIntegration.class";
    public static final String PLUGIN_CLASS_SHA256 =
            "7935e1cead25b5050a946626ca98eeb0775c113ea27f5ed27caa5ef250801462";
    public static final String RUNTIME_CLASS =
            "me.shedaniel.rei.jeicompat.wrap.JEIJeiRuntime";
    public static final String RUNTIME_CLASS_RESOURCE =
            "me/shedaniel/rei/jeicompat/wrap/JEIJeiRuntime.class";
    public static final String RUNTIME_CLASS_SHA256 =
            "74c8d1306d751342d0984c16f28e30164c20303748434f5ef31635671e808b06";
    public static final String RECIPE_MANAGER_CLASS =
            "me.shedaniel.rei.jeicompat.wrap.JEIRecipeManager";
    public static final String RECIPE_MANAGER_CLASS_RESOURCE =
            "me/shedaniel/rei/jeicompat/wrap/JEIRecipeManager.class";
    public static final String RECIPE_MANAGER_CLASS_SHA256 =
            "844533b464c4fcb15695898fd30bdd875018b839d5f6219af590eb888ff8a4cc";

    private static final Map<String, String> PACK_REMOVED_CARD_RESETS;
    private static final Map<String, String> PRESENT_FILTER_RESETS;

    static {
        Map<String, String> removed = new LinkedHashMap<>();
        removed.put("laserio:card_item_nbtclear", "mbm2:laserio_card_item");
        removed.put("laserio:card_fluid_nbtclear", "mbm2:laserio_card_fluid");
        removed.put("laserio:card_energy_nbtclear", "mbm2:laserio_card_energy");
        removed.put("laserio:card_redstone_nbtclear", "mbm2:laserio_card_redstone");
        PACK_REMOVED_CARD_RESETS = Collections.unmodifiableMap(removed);

        Map<String, String> present = new LinkedHashMap<>();
        present.put("laserio:filter_basic_nbtclear", "laserio:filter_basic");
        present.put("laserio:filter_count_nbtclear", "laserio:filter_count");
        present.put("laserio:filter_tag_nbtclear", "laserio:filter_tag");
        present.put("laserio:filter_mod_nbtclear", "laserio:filter_mod");
        PRESENT_FILTER_RESETS = Collections.unmodifiableMap(present);
    }

    private LaserIoJeiRuntimeContract() {
    }

    public static boolean isApplicable(
            String minecraftVersion,
            String forgeVersion,
            String laserIoVersion,
            String reiVersion,
            String reiJeiCompatVersion,
            String jeiApiVersion
    ) {
        return MINECRAFT_VERSION.equals(minecraftVersion)
                && FORGE_VERSION.equals(forgeVersion)
                && LASER_IO_VERSION.equals(laserIoVersion)
                && REI_VERSION.equals(reiVersion)
                && REI_JEI_COMPAT_VERSION.equals(reiJeiCompatVersion)
                && JEI_API_VERSION.equals(jeiApiVersion);
    }

    public static Map<String, String> packRemovedCardResets() {
        return PACK_REMOVED_CARD_RESETS;
    }

    public static Map<String, String> presentFilterResets() {
        return PRESENT_FILTER_RESETS;
    }

    public static Set<String> allResetRecipeIds() {
        Set<String> ids = new LinkedHashSet<>(PACK_REMOVED_CARD_RESETS.keySet());
        ids.addAll(PRESENT_FILTER_RESETS.keySet());
        return Collections.unmodifiableSet(ids);
    }

    public static void requireExactCorpus(
            Set<String> observedResetRecipeIds,
            Set<String> observedReplacementRecipeIds
    ) {
        Set<String> expectedResetRecipeIds = PRESENT_FILTER_RESETS.keySet();
        Set<String> expectedReplacementRecipeIds = new LinkedHashSet<>(
                PACK_REMOVED_CARD_RESETS.values()
        );
        if (!expectedResetRecipeIds.equals(observedResetRecipeIds)
                || !expectedReplacementRecipeIds.equals(observedReplacementRecipeIds)) {
            throw new IllegalStateException(
                    "drifted LaserIO/MM2 recipe-hide corpus; expectedResetRecipes="
                            + expectedResetRecipeIds
                            + ", actualResetRecipes=" + observedResetRecipeIds
                            + ", expectedReplacementRecipes=" + expectedReplacementRecipeIds
                            + ", actualReplacementRecipes=" + observedReplacementRecipeIds
            );
        }
    }
}
