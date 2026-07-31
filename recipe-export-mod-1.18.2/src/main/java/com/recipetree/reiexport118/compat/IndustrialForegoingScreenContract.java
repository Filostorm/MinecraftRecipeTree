package com.recipetree.reiexport118.compat;

import java.util.Map;
import java.util.Set;

/** Exact MM2 contract for JEI categories that dereference Minecraft's current Screen. */
public final class IndustrialForegoingScreenContract {
    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String INDUSTRIAL_FOREGOING_VERSION = "3.3.1.7";
    public static final String TITANIUM_VERSION = "3.5.11";

    public static final Map<String, String> RENDERER_CLASS_SHA256 = Map.of(
            "com/buuz135/industrial/plugin/jei/category/DissolutionChamberCategory.class",
            "b1fcea629fd1a0ffbe6371fca99403dc26bea3ff3d47361a95d8f6c4c790e96b",
            "com/buuz135/industrial/plugin/jei/category/OreWasherCategory.class",
            "83b9d3ce02b4075a31186477cf8aefeab22c12776e1130d00145e021b125388d",
            "com/buuz135/industrial/plugin/jei/category/FluidSieveCategory.class",
            "bc0a1873789786f651001449c7d98d5cbdb12fbcbbacdc429f73eb226e7d60b5",
            "com/buuz135/industrial/plugin/jei/category/LaserDrillFluidCategory.class",
            "a18ce79b5bb333892c984aebb3a55326368e0636e5bf4dc52f35ccbf516c0b77",
            "com/buuz135/industrial/plugin/jei/category/LaserDrillOreCategory.class",
            "a6028d4393cbafc112af6b1e754e6627787f90cd9af095d05f591cbf97be0caa",
            "com/buuz135/industrial/plugin/jei/category/StoneWorkGeneratorCategory.class",
            "06e5b6ba489192b9662af3ac4d762ea45f1281d7c5fa9a40f5342de02cd9a1af",
            "com/buuz135/industrial/plugin/jei/generator/MycelialGeneratorCategory.class",
            "05a86167defeb5e0064881513e50eed1ac87ea8f8f17b013843a76253c8a3ae6",
            "com/buuz135/industrial/plugin/jei/machineproduce/MachineProduceCategory.class",
            "0fdc9747cdd5170e7db5223e69ede6cb6338a4e96cd2dbf00c1496f39873757c"
    );

    public static final Set<String> SCREEN_DEPENDENT_CATEGORY_IDS = Set.of(
            "industrialforegoing:dissolution",
            "industrialforegoing:ore_washer",
            "industrialforegoing:ore_sieve",
            "industrialforegoing:laser_ore",
            "industrialforegoing:laser_fluid",
            "industrialforegoing:stone_work_generator",
            "industrialforegoing:machine_produce",
            "industrialforegoing:mycelial_furnace",
            "industrialforegoing:mycelial_disenchantment",
            "industrialforegoing:mycelial_potion",
            "industrialforegoing:mycelial_culinary",
            "industrialforegoing:mycelial_pink",
            "industrialforegoing:mycelial_crimed",
            "industrialforegoing:mycelial_frosty",
            "industrialforegoing:mycelial_death",
            "industrialforegoing:mycelial_rocket",
            "industrialforegoing:mycelial_magma",
            "industrialforegoing:mycelial_explosive",
            "industrialforegoing:mycelial_ender",
            "industrialforegoing:mycelial_slimey",
            "industrialforegoing:mycelial_netherstar",
            "industrialforegoing:mycelial_meatallurgic",
            "industrialforegoing:mycelial_halitosis"
    );

    private IndustrialForegoingScreenContract() {
    }

    public static boolean isApplicable(
            String minecraftVersion,
            String forgeVersion,
            String industrialForegoingVersion,
            String titaniumVersion
    ) {
        return MINECRAFT_VERSION.equals(minecraftVersion)
                && FORGE_VERSION.equals(forgeVersion)
                && INDUSTRIAL_FOREGOING_VERSION.equals(industrialForegoingVersion)
                && TITANIUM_VERSION.equals(titaniumVersion);
    }

    public static boolean requiresScreen(String categoryId) {
        return SCREEN_DEPENDENT_CATEGORY_IDS.contains(categoryId);
    }

    public static void requireLogicalDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException(
                    "Invalid native recipe Screen dimensions: " + width + "x" + height);
        }
    }
}
