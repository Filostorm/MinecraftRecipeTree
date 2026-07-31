package com.recipetree.reiexport118;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scans the complete catalog-producing REI domain for mini-export item selectors. */
final class QualityItemCandidateCollector {
    private final ExportContext context;
    private final QualityItemCandidateIndex<EntryStack<?>> index;
    private long registryStacks;
    private long displayStacks;
    private long workstationStacks;

    private QualityItemCandidateCollector(ExportContext context) {
        this.context = context;
        this.index = new QualityItemCandidateIndex<>(context.request.qualityItemSample);
    }

    static List<EntryStack<?>> collect(ExportContext context) {
        QualityItemCandidateCollector collector = new QualityItemCandidateCollector(context);
        collector.scanEntryRegistry();
        collector.scanFullRecipeDomain();
        List<EntryStack<?>> resolved = collector.index.resolveExactlyOnce();
        ReiExportMod.LOGGER.info(
                "[reiexport] Quality item canonical-domain scan resolved selectors={} "
                        + "registryStacks={} displayStacks={} workstationStacks={} "
                        + "matchedOccurrences={} distinctCanonicalIdentities={}",
                collector.index.selectorCount(),
                collector.registryStacks,
                collector.displayStacks,
                collector.workstationStacks,
                collector.index.acceptedOccurrences(),
                collector.index.distinctIdentityCount());
        return resolved;
    }

    private void scanEntryRegistry() {
        try (var entryStacks = EntryRegistry.getInstance().getEntryStacks()) {
            entryStacks.forEach(stack -> {
                registryStacks++;
                if (stack == null || stack.isEmpty()) {
                    // Preserve the accounting performed by the normal full item phase.
                    context.skippedEmptyEntries++;
                    return;
                }
                inspect(stack, "EntryRegistry");
            });
        }
    }

    private void scanFullRecipeDomain() {
        CategoryRegistry categoryRegistry = CategoryRegistry.getInstance();
        Map<CategoryIdentifier<?>, List<Display>> allDisplays =
                DisplayRegistry.getInstance().getAll();
        if (allDisplays == null) {
            throw new IllegalStateException(
                    "DisplayRegistry.getAll() returned null during quality item candidate scan");
        }

        LinkedHashMap<String, CategoryRegistry.CategoryConfiguration<?>> configurations =
                new LinkedHashMap<>();
        for (CategoryRegistry.CategoryConfiguration<?> configuration : categoryRegistry) {
            if (configuration == null) {
                throw new IllegalStateException(
                        "CategoryRegistry contains a null category configuration");
            }
            String categoryId = configuration.getCategoryIdentifier().getIdentifier().toString();
            if (configurations.putIfAbsent(categoryId, configuration) != null) {
                throw new IllegalStateException(
                        "REI registered duplicate category identifier " + categoryId);
            }
        }

        Set<CategoryIdentifier<?>> consumed = new LinkedHashSet<>();
        for (CategoryRegistry.CategoryConfiguration<?> configuration : configurations.values()) {
            CategoryIdentifier<?> categoryIdentifier = configuration.getCategoryIdentifier();
            List<Display> displays = allDisplays.getOrDefault(categoryIdentifier, List.of());
            if (displays == null) {
                throw new IllegalStateException(
                        "DisplayRegistry contains a null display list for " + categoryIdentifier);
            }
            consumed.add(categoryIdentifier);
            if (displays.isEmpty()) {
                // RecipePhase never opens empty categories, so their workstations are
                // intentionally outside the full export's item-catalog domain.
                continue;
            }

            Iterable<EntryIngredient> workstations = configuration.getWorkstations();
            if (workstations == null) {
                throw new IllegalStateException(
                        "REI category contains a null workstation collection: "
                                + categoryIdentifier);
            }
            int workstationIndex = 0;
            for (EntryIngredient workstation : workstations) {
                scanIngredient(
                        workstation,
                        "category workstation " + categoryIdentifier + " #" + workstationIndex,
                        Source.WORKSTATION);
                workstationIndex++;
            }

            for (int displayIndex = 0; displayIndex < displays.size(); displayIndex++) {
                Display display = displays.get(displayIndex);
                if (display == null) {
                    throw new IllegalStateException(
                            "DisplayRegistry contains a null display for " + categoryIdentifier
                                    + " #" + displayIndex);
                }
                scanIngredients(
                        display.getInputEntries(),
                        "display inputs " + categoryIdentifier + " #" + displayIndex);
                scanIngredients(
                        display.getOutputEntries(),
                        "display outputs " + categoryIdentifier + " #" + displayIndex);
            }
        }

        for (Map.Entry<CategoryIdentifier<?>, List<Display>> entry : allDisplays.entrySet()) {
            List<Display> displays = entry.getValue();
            if (displays == null) {
                throw new IllegalStateException(
                        "DisplayRegistry contains a null display list for " + entry.getKey());
            }
            if (!displays.isEmpty() && !consumed.contains(entry.getKey())) {
                throw new IllegalStateException(
                        "REI exposes displays for an unregistered category " + entry.getKey());
            }
        }
    }

    private void scanIngredients(List<EntryIngredient> ingredients, String source) {
        if (ingredients == null) {
            throw new IllegalStateException("REI returned a null ingredient list for " + source);
        }
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            scanIngredient(
                    ingredients.get(ingredientIndex),
                    source + " ingredient #" + ingredientIndex,
                    Source.DISPLAY);
        }
    }

    private void scanIngredient(EntryIngredient ingredient, String source, Source kind) {
        if (ingredient == null) {
            throw new IllegalStateException("REI returned a null ingredient for " + source);
        }
        for (EntryStack<?> stack : ingredient) {
            if (kind == Source.DISPLAY) {
                displayStacks++;
            } else {
                workstationStacks++;
            }
            if (stack == null || stack.isEmpty()) {
                // This is a discovery-only traversal. RecipePhase owns skipped-entry
                // accounting for the sample displays it actually serializes.
                continue;
            }
            inspect(stack, source);
        }
    }

    private void inspect(EntryStack<?> stack, String source) {
        String typeId = stack.getType().getId().toString();
        String identifier = stack.getIdentifier().toString();
        if (!index.requests(typeId, identifier)) {
            return;
        }

        ItemCatalog.CanonicalIdentity canonical = ItemCatalog.canonicalIdentity(stack, context);
        String canonicalTypeId = canonical.typeId().toString();
        String canonicalIdentifier = canonical.identifier().toString();
        if (!typeId.equals(canonicalTypeId) || !identifier.equals(canonicalIdentifier)) {
            throw new IllegalStateException(
                    "Canonicalization changed a quality item selector pair while scanning "
                            + source + ": original=" + typeId + " " + identifier
                            + " canonical=" + canonicalTypeId + " " + canonicalIdentifier);
        }

        EntryStack<?> retained = stack.copy();
        if (retained == null || retained.isEmpty()) {
            throw new IllegalStateException(
                    "REI EntryStack.copy() returned an empty quality item candidate while scanning "
                            + source + ": " + typeId + " " + identifier);
        }
        index.accept(
                canonicalTypeId,
                canonicalIdentifier,
                canonical.serialized(),
                retained);
    }

    private enum Source {
        DISPLAY,
        WORKSTATION
    }
}
