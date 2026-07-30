package com.recipetree.neiexport1710;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proves that the GTNH Thaumcraft NEI replacement handlers expose locked
 * recipes before a dataset is allowed to leave the client.
 *
 * <p>The class-name contract is intentionally exact and pack-version-specific.
 * A future TCNEIAdditions release must be audited and this policy updated; it
 * is not safe to guess that an unfamiliar handler preserves recipe visibility.</p>
 */
final class ThaumcraftRecipeVisibilityPolicy {
    static final String FAILURE_CODE = "RECIPE_VISIBILITY_GATED";

    private static final String CONFIG_CLASS =
            "ru.timeconqueror.tcneiadditions.util.TCNAConfig";
    private static final String CONFIG_FIELD = "showLockedRecipes";
    private static final String REPLACEMENT_PREFIX =
            "ru.timeconqueror.tcneiadditions.nei.";
    private static final String LEGACY_PREFIX =
            "com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.";

    private static final Set<String> CRAFTING_REPLACEMENTS = immutableSet(
            "ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler",
            "ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler",
            "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapedHandler",
            "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapelessHandler",
            "ru.timeconqueror.tcneiadditions.nei.TCNACrucibleRecipeHandler",
            "ru.timeconqueror.tcneiadditions.nei.TCNAInfusionRecipeHandler");

    private static final Set<String> USAGE_REPLACEMENTS = immutableSet(
            "ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler",
            "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapedHandler",
            "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapelessHandler",
            "ru.timeconqueror.tcneiadditions.nei.TCNACrucibleRecipeHandler",
            "ru.timeconqueror.tcneiadditions.nei.TCNAInfusionRecipeHandler");

    private static final Set<String> LEGACY_HANDLERS = immutableSet(
            "com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.AspectRecipeHandler",
            "com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.ArcaneShapedRecipeHandler",
            "com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.ArcaneShapelessRecipeHandler",
            "com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.CrucibleRecipeHandler",
            "com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.InfusionRecipeHandler");

    private ThaumcraftRecipeVisibilityPolicy() {
    }

    static Snapshot capture() throws ExportFailure {
        boolean showLockedRecipes = readShowLockedRecipes();
        return evaluate(
                snapshotClassNames(GuiCraftingRecipe.craftinghandlers,
                        "regular crafting"),
                snapshotClassNames(GuiCraftingRecipe.serialCraftingHandlers,
                        "serial crafting"),
                snapshotClassNames(GuiUsageRecipe.usagehandlers,
                        "regular usage"),
                snapshotClassNames(GuiUsageRecipe.serialUsageHandlers,
                        "serial usage"),
                showLockedRecipes);
    }

    static Snapshot evaluate(List<String> regularCrafting,
                             List<String> serialCrafting,
                             List<String> regularUsage,
                             List<String> serialUsage,
                             boolean showLockedRecipes) throws ExportFailure {
        List<String> safeRegularCrafting = requiredClassNames(
                regularCrafting, "regular crafting");
        List<String> safeSerialCrafting = requiredClassNames(
                serialCrafting, "serial crafting");
        List<String> safeRegularUsage = requiredClassNames(
                regularUsage, "regular usage");
        List<String> safeSerialUsage = requiredClassNames(
                serialUsage, "serial usage");

        if (!showLockedRecipes) {
            throw gated(CONFIG_CLASS + "." + CONFIG_FIELD
                    + " must be true; exporting with it false omits locked Thaumcraft recipes");
        }

        List<String> allCrafting = concat(safeRegularCrafting, safeSerialCrafting);
        List<String> allUsage = concat(safeRegularUsage, safeSerialUsage);
        verifyExactReplacementSet("crafting", allCrafting, CRAFTING_REPLACEMENTS);
        verifyExactReplacementSet("usage", allUsage, USAGE_REPLACEMENTS);
        verifyLegacyAbsent("crafting", allCrafting);
        verifyLegacyAbsent("usage", allUsage);

        List<String> fingerprintParts = new ArrayList<String>();
        addFingerprintParts(fingerprintParts, "crafting.regular", safeRegularCrafting);
        addFingerprintParts(fingerprintParts, "crafting.serial", safeSerialCrafting);
        addFingerprintParts(fingerprintParts, "usage.regular", safeRegularUsage);
        addFingerprintParts(fingerprintParts, "usage.serial", safeSerialUsage);
        fingerprintParts.add("config." + CONFIG_FIELD + "=true");
        Collections.sort(fingerprintParts);
        StringBuilder canonical = new StringBuilder();
        for (String part : fingerprintParts) {
            canonical.append(part.length()).append(':').append(part).append('|');
        }
        return new Snapshot(
                Naming.sha256(canonical.toString()),
                safeRegularCrafting.size(), safeSerialCrafting.size(),
                safeRegularUsage.size(), safeSerialUsage.size());
    }

    private static boolean readShowLockedRecipes() throws ExportFailure {
        try {
            Class<?> configClass = Class.forName(
                    CONFIG_CLASS, false,
                    ThaumcraftRecipeVisibilityPolicy.class.getClassLoader());
            Field field = configClass.getField(CONFIG_FIELD);
            int modifiers = field.getModifiers();
            if (field.getType() != Boolean.TYPE
                    || !Modifier.isPublic(modifiers)
                    || !Modifier.isStatic(modifiers)) {
                throw gated(CONFIG_CLASS + "." + CONFIG_FIELD
                        + " must remain a public static boolean");
            }
            return field.getBoolean(null);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure(FAILURE_CODE,
                    "could not reflect the exact TCNEIAdditions visibility configuration contract",
                    error);
        }
    }

    private static List<String> snapshotClassNames(List<?> registry, String label)
            throws ExportFailure {
        if (registry == null) {
            throw gated("NEI " + label + " registry is null");
        }
        List<String> classNames = new ArrayList<String>();
        synchronized (registry) {
            for (int index = 0; index < registry.size(); index++) {
                Object handler = registry.get(index);
                if (handler == null) {
                    throw gated("NEI " + label + " registry contains null at index " + index);
                }
                classNames.add(handler.getClass().getName());
            }
        }
        return classNames;
    }

    private static List<String> requiredClassNames(List<String> classNames, String label)
            throws ExportFailure {
        if (classNames == null) {
            throw gated("NEI " + label + " registry snapshot is null");
        }
        List<String> copy = new ArrayList<String>(classNames.size());
        for (int index = 0; index < classNames.size(); index++) {
            String className = classNames.get(index);
            if (className == null || className.trim().isEmpty()) {
                throw gated("NEI " + label
                        + " registry snapshot contains a blank class at index " + index);
            }
            copy.add(className);
        }
        return copy;
    }

    private static void verifyExactReplacementSet(String registryKind,
                                                   List<String> classNames,
                                                   Set<String> expected)
            throws ExportFailure {
        Map<String, Integer> observedCounts = countsWithPrefix(
                classNames, REPLACEMENT_PREFIX);
        Set<String> observed = new HashSet<String>(observedCounts.keySet());
        if (!observed.equals(expected)) {
            Set<String> missing = new HashSet<String>(expected);
            missing.removeAll(observed);
            Set<String> unexpected = new HashSet<String>(observed);
            unexpected.removeAll(expected);
            throw gated("TCNEIAdditions " + registryKind
                    + " replacement class set mismatch; missing=" + sorted(missing)
                    + ", unexpected=" + sorted(unexpected)
                    + ", observed=" + sorted(observed));
        }
        for (String className : expected) {
            int count = observedCounts.get(className);
            if (count != 1) {
                throw gated("TCNEIAdditions " + registryKind + " replacement "
                        + className + " occurs " + count + " times; expected exactly once");
            }
        }
    }

    private static void verifyLegacyAbsent(String registryKind, List<String> classNames)
            throws ExportFailure {
        Map<String, Integer> observedLegacy = countsWithPrefix(classNames, LEGACY_PREFIX);
        if (!observedLegacy.isEmpty()) {
            Set<String> unknownLegacy = new HashSet<String>(observedLegacy.keySet());
            unknownLegacy.removeAll(LEGACY_HANDLERS);
            throw gated("legacy Thaumcraft NEI " + registryKind
                    + " handlers remain registered; expected zero of "
                    + sorted(LEGACY_HANDLERS) + ", observed=" + sorted(observedLegacy.keySet())
                    + (unknownLegacy.isEmpty()
                    ? "" : ", unrecognizedLegacyClasses=" + sorted(unknownLegacy)));
        }
    }

    private static Map<String, Integer> countsWithPrefix(List<String> classNames,
                                                         String prefix) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (String className : classNames) {
            if (!className.startsWith(prefix)) {
                continue;
            }
            Integer previous = counts.get(className);
            counts.put(className, previous == null ? 1 : previous + 1);
        }
        return counts;
    }

    private static void addFingerprintParts(List<String> destination,
                                            String registry,
                                            List<String> classNames) {
        for (String className : classNames) {
            destination.add(registry + "=" + className);
        }
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> combined = new ArrayList<String>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }

    private static List<String> sorted(Set<String> values) {
        List<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        return sorted;
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    private static ExportFailure gated(String message) {
        return new ExportFailure(FAILURE_CODE, message);
    }

    static final class Snapshot {
        final String fingerprint;
        final int regularCraftingCount;
        final int serialCraftingCount;
        final int regularUsageCount;
        final int serialUsageCount;

        Snapshot(String fingerprint,
                 int regularCraftingCount,
                 int serialCraftingCount,
                 int regularUsageCount,
                 int serialUsageCount) {
            this.fingerprint = fingerprint;
            this.regularCraftingCount = regularCraftingCount;
            this.serialCraftingCount = serialCraftingCount;
            this.regularUsageCount = regularUsageCount;
            this.serialUsageCount = serialUsageCount;
        }

        String registrySummary() {
            return "crafting=" + regularCraftingCount + "+" + serialCraftingCount
                    + ", usage=" + regularUsageCount + "+" + serialUsageCount;
        }
    }
}
