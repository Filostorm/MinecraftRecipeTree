package com.recipetree.reiexport118.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.recipetree.reiexport118.ReiExportMod;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.util.EntryStacks;
import mrtjp.projectred.integration.GateType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Exact post-reload registry repairs and assertions for Multiblock Madness 2. */
public final class Mm2RegistryRepairs {
    enum State {
        NEW,
        RUNNING,
        DONE,
        FAILED
    }

    enum ProjectRedAction {
        KEEP_ZERO,
        REMOVE_EXACT_BLANK
    }

    enum NatureAuraAction {
        ADD_EXACT_BLANK,
        KEEP_ONE_PLAIN
    }

    public enum SettlementSeam {
        READINESS_CANDIDATE("readiness-candidate"),
        PRE_CLAIM("pre-claim");

        private final String logName;

        SettlementSeam(String logName) {
            this.logName = logName;
        }

        String logName() {
            return logName;
        }
    }

    public record SettlementResult(
            boolean removedFabricationGate,
            boolean removedIntegrationGate,
            boolean addedNaturesAuraRebottling
    ) {
        public boolean changed() {
            return removedFabricationGate
                    || removedIntegrationGate
                    || addedNaturesAuraRebottling;
        }
    }

    record EntryShape(boolean itemStack, boolean tagged) {
    }

    record RecipeOrigin(Object identity, ResourceLocation recipeId) {
    }

    private record DisplayCount(String categoryId, int expected) {
    }

    private record EntryTargets(
            List<EntryStack<?>> fabricationGates,
            List<EntryStack<?>> integrationGates,
            List<EntryStack<?>> naturesAuraRebottling
    ) {
    }

    static final ResourceLocation PROJECT_RED_FABRICATION_FABRICATED_GATE =
            new ResourceLocation("projectred_fabrication", "fabricated_gate");
    static final ResourceLocation PROJECT_RED_INTEGRATION_FABRICATED_GATE =
            new ResourceLocation("projectred_integration", "fabricated_gate");
    static final ResourceLocation NATURES_AURA_REBOTTLING =
            new ResourceLocation("naturesaura", "bottle_two_the_rebottling");
    private static final ResourceLocation ITEM_ENTRY_TYPE =
            new ResourceLocation("minecraft", "item");
    private static final String MEKANISM_PIGMENT_EXTRACTOR =
            "mekanism:pigment_extractor";
    private static final List<DisplayCount> REQUIRED_DISPLAY_COUNTS = List.of(
            new DisplayCount(
                    MEKANISM_PIGMENT_EXTRACTOR,
                    Mm2DeterminismContract.MEKANISM_PIGMENT_EXTRACTING_RECIPES),
            new DisplayCount("createaddition:rolling", 133),
            new DisplayCount("ae2:throwing_in_water", 3),
            new DisplayCount("apotheosis:smithing", 2));
    private static final AtomicReference<State> STATE =
            new AtomicReference<>(State.NEW);

    private Mm2RegistryRepairs() {
    }

    /**
     * Runs exactly once after the owned synchronous REI reload and before census/export.
     */
    public static void repairAndVerifyAfterOwnedReload() {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] MM2 registry repairs not applicable: exact MM2 lifecycle is not armed");
            return;
        }
        if (!STATE.compareAndSet(State.NEW, State.RUNNING)) {
            throw new IllegalStateException(
                    "MM2 registry repairs must run exactly once; currentState=" + STATE.get());
        }

        try {
            requireOwnedReloadHasFinished();
            Mm2RegistryRepairContract.validateAndArm();

            EntryRegistry registry = EntryRegistry.getInstance();
            SettlementResult settlement = canonicalizeEntryTargets(registry, "owned-reload");
            assertDisplayCounts();

            STATE.set(State.DONE);
            ReiExportMod.LOGGER.info(
                    "[reiexport] Completed exact MM2 registry repairs: "
                            + "projectred_fabrication:fabricated_gate=0, "
                            + "projectred_integration:fabricated_gate=0 "
                            + "(duplicate registry id absent), "
                            + "naturesaura:bottle_two_the_rebottling=1, displayCounts={}",
                    REQUIRED_DISPLAY_COUNTS);
            ReiExportMod.LOGGER.info(
                    "[reiexport] Owned-reload MM2 entry canonicalization mutation={}",
                    settlement);
        } catch (RuntimeException | Error failure) {
            STATE.set(State.FAILED);
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 post-reload registry repair failed; "
                            + "no retry or broad fallback was attempted",
                    failure);
            throw failure;
        }
    }

    /**
     * Reasserts the exact MM2 entry contract once at a readiness boundary. This is deliberately
     * not a tick hook: each call performs one registry scan before mutation and one postcondition
     * scan, so callers must invoke it only when establishing a deep candidate or immediately
     * before an atomic export claim.
     */
    public static SettlementResult canonicalizeSettledEntries(SettlementSeam seam) {
        if (seam == null) {
            throw new IllegalArgumentException("MM2 settlement seam must not be null");
        }
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            throw new IllegalStateException(
                    "MM2 settled entry canonicalization ran before the exact lifecycle arm");
        }
        if (STATE.get() != State.DONE) {
            throw new IllegalStateException(
                    "MM2 settled entry canonicalization requires completed owned-reload repairs; "
                            + "currentState=" + STATE.get());
        }

        try {
            requireOwnedReloadHasFinished();
            Mm2RegistryRepairContract.requireArmed();
            SettlementResult result = canonicalizeEntryTargets(
                    EntryRegistry.getInstance(), seam.logName());
            ReiExportMod.LOGGER.info(
                    "[reiexport] Verified exact settled MM2 entry contract seam={} changed={} "
                            + "projectredFabrication=0 projectredIntegration=0 naturesAura=1 "
                            + "mutation={}",
                    seam.logName(), result.changed(), result);
            return result;
        } catch (RuntimeException | Error failure) {
            STATE.compareAndSet(State.DONE, State.FAILED);
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 settled entry canonicalization failed at seam={}; "
                            + "no retry or broad fallback was attempted",
                    seam.logName(), failure);
            throw failure;
        }
    }

    static ProjectRedAction projectRedAction(List<EntryShape> shapes) {
        requireShapes(shapes, "ProjectRed fabricated gate");
        if (shapes.isEmpty()) {
            return ProjectRedAction.KEEP_ZERO;
        }
        if (shapes.size() != 1) {
            throw new IllegalStateException(
                    "ProjectRed fabricated gate cardinality drift: " + shapes.size());
        }
        EntryShape shape = shapes.get(0);
        if (!shape.itemStack() || shape.tagged()) {
            throw new IllegalStateException(
                    "ProjectRed fabricated gate is not the exact blank ItemStack: " + shape);
        }
        return ProjectRedAction.REMOVE_EXACT_BLANK;
    }

    static NatureAuraAction natureAuraAction(List<EntryShape> shapes) {
        requireShapes(shapes, "Nature's Aura rebottling");
        if (shapes.isEmpty()) {
            return NatureAuraAction.ADD_EXACT_BLANK;
        }
        if (shapes.size() != 1) {
            throw new IllegalStateException(
                    "Nature's Aura rebottling cardinality drift: " + shapes.size());
        }
        EntryShape shape = shapes.get(0);
        if (!shape.itemStack() || shape.tagged()) {
            throw new IllegalStateException(
                    "Nature's Aura rebottling is not one exact plain ItemStack: " + shape);
        }
        return NatureAuraAction.KEEP_ONE_PLAIN;
    }

    private static SettlementResult canonicalizeEntryTargets(
            EntryRegistry registry,
            String seam
    ) {
        if (registry == null) {
            throw new IllegalArgumentException("MM2 entry registry must not be null");
        }
        if (seam == null || seam.isBlank()) {
            throw new IllegalArgumentException("MM2 canonicalization seam must not be blank");
        }

        EntryTargets before = exactEntryTargets(registry);
        ProjectRedAction fabricationAction = exactProjectRedAction(
                PROJECT_RED_FABRICATION_FABRICATED_GATE,
                before.fabricationGates());
        ProjectRedAction integrationAction = exactProjectRedAction(
                PROJECT_RED_INTEGRATION_FABRICATED_GATE,
                before.integrationGates());
        NatureAuraAction natureAuraAction = natureAuraAction(
                shapes(before.naturesAuraRebottling()));

        boolean upstreamIntegrationDuplicateEnabled =
                Mm2ProjectRedRegistrationGate.requireObservedUpstreamState();

        // Validate every target before mutating any REI state. This turns a namespace typo or
        // pack drift into a terminal contract failure instead of a partial canonicalization.
        Item fabricationItem = exactRegisteredItem(
                PROJECT_RED_FABRICATION_FABRICATED_GATE,
                "ProjectRed Fabrication fabricated gate");
        requireFabricationGateOwner(fabricationItem);
        requireUnregisteredItem(
                PROJECT_RED_INTEGRATION_FABRICATED_GATE,
                "ProjectRed Integration duplicate fabricated gate");
        if (integrationAction != ProjectRedAction.KEEP_ZERO) {
            throw new IllegalStateException(
                    "ProjectRed Integration duplicate fabricated gate reached REI despite "
                            + "the registration guard id="
                            + PROJECT_RED_INTEGRATION_FABRICATED_GATE);
        }
        Item naturesAuraItem = exactRegisteredItem(
                NATURES_AURA_REBOTTLING,
                "Nature's Aura rebottling");

        boolean removedFabrication = removeExactBlankIfRequired(
                registry,
                before.fabricationGates(),
                fabricationAction,
                PROJECT_RED_FABRICATION_FABRICATED_GATE,
                seam);
        boolean removedIntegration = false;
        boolean addedNaturesAura = addNaturesAuraIfRequired(
                registry, naturesAuraItem, natureAuraAction, seam);

        EntryTargets after = exactEntryTargets(registry);
        if (exactProjectRedAction(
                PROJECT_RED_FABRICATION_FABRICATED_GATE,
                after.fabricationGates())
                != ProjectRedAction.KEEP_ZERO) {
            throw new IllegalStateException(
                    "ProjectRed Fabrication fabricated gate remained after exact removal");
        }
        if (exactProjectRedAction(
                PROJECT_RED_INTEGRATION_FABRICATED_GATE,
                after.integrationGates())
                != ProjectRedAction.KEEP_ZERO) {
            throw new IllegalStateException(
                    "ProjectRed Integration fabricated gate remained after exact removal");
        }
        if (natureAuraAction(shapes(after.naturesAuraRebottling()))
                != NatureAuraAction.KEEP_ONE_PLAIN) {
            throw new IllegalStateException(
                    "Nature's Aura rebottling was not present exactly once after repair");
        }

        ReiExportMod.LOGGER.info(
                "[reiexport] Verified ProjectRed fabricated-gate registry ownership seam={} "
                        + "fabricationRegistered=true integrationDuplicateRegistered=false "
                        + "upstreamDuplicateEnabled={} integrationEntryCount=0",
                seam, upstreamIntegrationDuplicateEnabled);

        return new SettlementResult(
                removedFabrication, removedIntegration, addedNaturesAura);
    }

    private static boolean removeExactBlankIfRequired(
            EntryRegistry registry,
            List<EntryStack<?>> entries,
            ProjectRedAction action,
            ResourceLocation identifier,
            String seam
    ) {
        if (action == ProjectRedAction.KEEP_ZERO) {
            return false;
        }
        EntryStack<?> exactBlank = entries.get(0);
        if (!registry.removeEntry(exactBlank)) {
            throw new IllegalStateException(
                    "REI refused exact ProjectRed fabricated-gate removal id=" + identifier);
        }
        ReiExportMod.LOGGER.warn(
                "[reiexport] Removed one exact untagged ProjectRed fabricated gate during "
                        + "MM2 registry canonicalization seam={} id={}",
                seam, identifier);
        return true;
    }

    private static ProjectRedAction exactProjectRedAction(
            ResourceLocation identifier,
            List<EntryStack<?>> entries
    ) {
        try {
            return projectRedAction(shapes(entries));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new IllegalStateException(
                    "ProjectRed fabricated-gate entry contract drift id=" + identifier
                            + ", cause=" + failure.getMessage(),
                    failure);
        }
    }

    private static boolean addNaturesAuraIfRequired(
            EntryRegistry registry,
            Item item,
            NatureAuraAction action,
            String seam
    ) {
        if (action == NatureAuraAction.KEEP_ONE_PLAIN) {
            return false;
        }
        EntryStack<ItemStack> exactBlank = EntryStacks.of(new ItemStack(item));
        ItemStack stack = exactBlank.getValue();
        if (exactBlank.isEmpty() || stack == null || stack.hasTag()) {
            throw new IllegalStateException(
                    "Nature's Aura rebottling factory did not produce an exact blank ItemStack");
        }
        if (registry.alreadyContain(exactBlank)) {
            throw new IllegalStateException(
                    "Nature's Aura rebottling was absent from the entry stream but present "
                            + "in REI's exact-containment index");
        }
        registry.addEntry(exactBlank);
        ReiExportMod.LOGGER.warn(
                "[reiexport] Added one exact untagged Nature's Aura rebottling entry at "
                        + "seam={} id={}",
                seam, NATURES_AURA_REBOTTLING);
        return true;
    }

    private static Item exactRegisteredItem(ResourceLocation identifier, String label) {
        if (!Registry.ITEM.containsKey(identifier)) {
            throw new IllegalStateException(label + " is absent from Registry.ITEM id=" + identifier);
        }
        Item item = Registry.ITEM.get(identifier);
        if (item == Items.AIR || !identifier.equals(Registry.ITEM.getKey(item))) {
            throw new IllegalStateException(
                    label + " registry lookup did not resolve the exact item id=" + identifier);
        }
        return item;
    }

    private static void requireUnregisteredItem(ResourceLocation identifier, String label) {
        if (!Registry.ITEM.containsKey(identifier)) {
            return;
        }
        Item item = Registry.ITEM.get(identifier);
        throw new IllegalStateException(
                label + " was registered even though the exact MM2 registration guard must "
                        + "suppress it id=" + identifier + ", itemClass="
                        + (item == null ? "null" : item.getClass().getName()));
    }

    private static void requireFabricationGateOwner(Item fabricationItem) {
        GateType fabricatedGate = GateType.FABRICATED_GATE;
        if (!fabricatedGate.isEnabled()) {
            throw new IllegalStateException(
                    "ProjectRed FABRICATED_GATE was not enabled by Fabrication injection");
        }
        Item selected = fabricatedGate.getItem();
        if (selected != fabricationItem) {
            throw new IllegalStateException(
                    "ProjectRed FABRICATED_GATE supplier does not retain the exact Fabrication "
                            + "registry item: expectedId="
                            + PROJECT_RED_FABRICATION_FABRICATED_GATE
                            + ", actualId=" + Registry.ITEM.getKey(selected));
        }
    }

    private static EntryTargets exactEntryTargets(EntryRegistry registry) {
        List<EntryStack<?>> fabricationGates = new ArrayList<>();
        List<EntryStack<?>> integrationGates = new ArrayList<>();
        List<EntryStack<?>> naturesAuraRebottling = new ArrayList<>();
        try (var stacks = registry.getEntryStacks()) {
            var iterator = stacks.iterator();
            while (iterator.hasNext()) {
                EntryStack<?> stack = iterator.next();
                if (stack == null) {
                    throw new IllegalStateException(
                            "REI entry stream contains null while locating exact MM2 targets");
                }
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation identifier = stack.getIdentifier();
                if (PROJECT_RED_FABRICATION_FABRICATED_GATE.equals(identifier)) {
                    fabricationGates.add(stack);
                } else if (PROJECT_RED_INTEGRATION_FABRICATED_GATE.equals(identifier)) {
                    integrationGates.add(stack);
                } else if (NATURES_AURA_REBOTTLING.equals(identifier)) {
                    naturesAuraRebottling.add(stack);
                }
            }
        }
        return new EntryTargets(
                List.copyOf(fabricationGates),
                List.copyOf(integrationGates),
                List.copyOf(naturesAuraRebottling));
    }

    private static List<EntryShape> shapes(List<EntryStack<?>> entries) {
        return entries.stream().map(Mm2RegistryRepairs::shape).toList();
    }

    private static EntryShape shape(EntryStack<?> entry) {
        boolean itemEntry = ITEM_ENTRY_TYPE.equals(entry.getType().getId())
                && entry.getValue() instanceof ItemStack;
        boolean tagged = entry.getValue() instanceof ItemStack stack && stack.hasTag();
        return new EntryShape(itemEntry, tagged);
    }

    private static void requireShapes(List<EntryShape> shapes, String label) {
        if (shapes == null || shapes.stream().anyMatch(shape -> shape == null)) {
            throw new IllegalArgumentException(label + " entry-shape census must not be null");
        }
    }

    private static void assertDisplayCounts() {
        DisplayRegistry registry = DisplayRegistry.getInstance();
        Map<CategoryIdentifier<?>, List<Display>> all = registry.getAll();
        for (DisplayCount requirement : REQUIRED_DISPLAY_COUNTS) {
            CategoryIdentifier<Display> category = CategoryIdentifier.of(requirement.categoryId());
            List<Display> displays = all.getOrDefault(category, List.of());
            int actual = displays.size();
            if (MEKANISM_PIGMENT_EXTRACTOR.equals(requirement.categoryId())) {
                assertPigmentOrigins(registry, displays, requirement.expected());
            }
            if (actual != requirement.expected()) {
                throw new IllegalStateException(
                        "MM2 post-reload display count drift category=" + requirement.categoryId()
                                + ", expected=" + requirement.expected() + ", actual=" + actual);
            }
        }
    }

    private static void assertPigmentOrigins(
            DisplayRegistry registry,
            List<Display> displays,
            int expected
    ) {
        List<RecipeOrigin> origins = new ArrayList<>(displays.size());
        for (int index = 0; index < displays.size(); index++) {
            Display display = displays.get(index);
            if (display == null) {
                throw new IllegalStateException(
                        "Mekanism pigment display list contains null at index=" + index);
            }
            Object origin = registry.getDisplayOrigin(display);
            if (!(origin instanceof Recipe<?> recipe)) {
                throw new IllegalStateException(
                        "Mekanism pigment display origin is not an authoritative Minecraft "
                                + "Recipe index=" + index + ", originType="
                                + (origin == null ? "null" : origin.getClass().getName()));
            }
            origins.add(new RecipeOrigin(recipe, recipe.getId()));
        }
        assertUniqueRecipeOrigins(MEKANISM_PIGMENT_EXTRACTOR, expected, origins);
    }

    static void assertUniqueRecipeOrigins(
            String categoryId,
            int expected,
            List<RecipeOrigin> origins
    ) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("recipe-origin category must not be blank");
        }
        if (expected <= 0) {
            throw new IllegalArgumentException("expected recipe-origin count must be positive");
        }
        if (origins == null) {
            throw new IllegalArgumentException(
                    "recipe-origin census must not be null category=" + categoryId);
        }

        Map<Object, Integer> identityMultiplicity = new IdentityHashMap<>();
        Map<ResourceLocation, Integer> idMultiplicity = new HashMap<>();
        for (int index = 0; index < origins.size(); index++) {
            RecipeOrigin origin = origins.get(index);
            if (origin == null || origin.identity() == null || origin.recipeId() == null) {
                throw new IllegalStateException(
                        "recipe-origin census contains null identity or Recipe ID category="
                                + categoryId + ", index=" + index + ", origin=" + origin);
            }
            identityMultiplicity.merge(origin.identity(), 1, Math::addExact);
            idMultiplicity.merge(origin.recipeId(), 1, Math::addExact);
        }

        int maximumIdentityMultiplicity = identityMultiplicity.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        int maximumIdMultiplicity = idMultiplicity.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        if (origins.size() != expected
                || identityMultiplicity.size() != expected
                || idMultiplicity.size() != expected
                || maximumIdentityMultiplicity != 1
                || maximumIdMultiplicity != 1) {
            throw new IllegalStateException(
                    "MM2 authoritative recipe-origin drift category=" + categoryId
                            + ", expectedTotal=" + expected
                            + ", actualTotal=" + origins.size()
                            + ", distinctObjectIdentities=" + identityMultiplicity.size()
                            + ", maximumObjectMultiplicity=" + maximumIdentityMultiplicity
                            + ", distinctRecipeIds=" + idMultiplicity.size()
                            + ", maximumRecipeIdMultiplicity=" + maximumIdMultiplicity);
        }
    }

    private static void requireOwnedReloadHasFinished() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || !RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException(
                    "MM2 registry repairs are not running on the Minecraft client/render thread: "
                            + Thread.currentThread().getName());
        }
        if (PluginManager.areAnyReloading()) {
            throw new IllegalStateException(
                    "MM2 registry repairs started while a REI plugin manager was still reloading");
        }
    }
}
