package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Runtime checks around ElementalCraft's exact pure-ore generation seam. */
public final class ElementalCraftPureOreDeterminism {
    /*
     * Five post-processability domains have both ore and raw-material inputs:
     * elementium, tin, osmium, uranium, and gold. The other 20 domains expose
     * one purifier input each, yielding 25 + 5 = 30 native purifier recipes.
     */
    private static final int EXPECTED_PURIFIER_RECIPE_COUNT = 30;

    /*
     * This is PureOreManager's post-isProcessable domain, not the transitive
     * forge:ores source-tag domain. MM2 aliases forge:ores/yellorium wholly into
     * forge:ores/uranium, while forge:ores/osmium contains the platinum sources.
     * ElementalCraft's pinned loader coalesces those overlaps and its manager then
     * removes entries without a native processing recipe, so neither alias is a
     * distinct manager key.
     */
    private static final Set<String> EXPECTED_ORES = Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of(
            "forge:arcane_crystal", "mythicbotany:elementium", "forge:tin",
            "forbidden_arcanus:runic_darkstone", "forge:apatite", "mythicbotany:dragonstone",
            "forge:certus_quartz", "forge:osmium", "forge:inert_crystal", "forge:lapis",
            "forge:sulfur", "forge:netherite_scrap", "forge:redstone",
            "forge:gold", "forbidden_arcanus:runic_deepslate", "forge:niter", "forge:emerald",
            "forge:cheese", "forge:fluorite", "forbidden_arcanus:runic_stone", "forge:coal",
            "forge:quartz", "forge:diamond", "forge:uranium", "forge:cinnabar")));

    private ElementalCraftPureOreDeterminism() {
    }

    public static Collection<?> sortInjectors(Collection<?> injectors) {
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.ELEMENTAL_CRAFT.modId());
        if (injectors == null || injectors.isEmpty()) {
            throw new IllegalStateException("ElementalCraft returned no pure-ore injectors");
        }
        return injectors.stream()
                .sorted(Comparator.comparing(ElementalCraftPureOreDeterminism::registryName))
                .toList();
    }

    public static void verifyManager(Object manager) {
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.ELEMENTAL_CRAFT.modId());
        try {
            Method getOres = manager.getClass().getMethod("getOres");
            Method getRecipes = manager.getClass().getMethod("getRecipes");
            Object oresResult = getOres.invoke(manager);
            Object recipesResult = getRecipes.invoke(manager);
            if (!(oresResult instanceof Collection<?> ores)
                    || !(recipesResult instanceof Collection<?> recipes)) {
                throw new IllegalStateException("ElementalCraft manager collection seam drift");
            }
            Set<String> actual = new LinkedHashSet<>();
            for (Object ore : ores) {
                if (!(ore instanceof ResourceLocation id)) {
                    throw new IllegalStateException(
                            "ElementalCraft exposed a non-ResourceLocation pure-ore id: " + ore);
                }
                actual.add(id.toString());
            }
            if (!EXPECTED_ORES.equals(actual)) {
                Set<String> missing = new LinkedHashSet<>(EXPECTED_ORES);
                missing.removeAll(actual);
                Set<String> extra = new LinkedHashSet<>(actual);
                extra.removeAll(EXPECTED_ORES);
                throw new IllegalStateException("ElementalCraft pure-ore domain drift: expected="
                        + EXPECTED_ORES.size() + ", actual=" + actual.size()
                        + ", missing=" + missing + ", extra=" + extra);
            }
            if (recipes.size() != EXPECTED_PURIFIER_RECIPE_COUNT) {
                throw new IllegalStateException(
                        "ElementalCraft purifier recipe domain drift: expected="
                                + EXPECTED_PURIFIER_RECIPE_COUNT + ", actual=" + recipes.size());
            }
            ReiExportMod.LOGGER.info(
                    "[reiexport] Verified deterministic ElementalCraft pure-ore domain: ores=25 purifierRecipes=30");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("ElementalCraft manager verification failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ElementalCraft manager reflection seam drift", exception);
        }
    }

    static Set<String> expectedOres() {
        return EXPECTED_ORES;
    }

    static int expectedPurifierRecipeCount() {
        return EXPECTED_PURIFIER_RECIPE_COUNT;
    }

    private static String registryName(Object injector) {
        if (injector == null) {
            throw new IllegalStateException("ElementalCraft returned a null pure-ore injector");
        }
        try {
            Method method = injector.getClass().getMethod("getRegistryName");
            Object value = method.invoke(injector);
            if (!(value instanceof ResourceLocation id)) {
                throw new IllegalStateException(
                        "ElementalCraft injector has no registry id: " + injector.getClass().getName());
            }
            return id.toString();
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "ElementalCraft injector registry-id lookup failed", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "ElementalCraft injector registry-id seam drift", exception);
        }
    }
}
