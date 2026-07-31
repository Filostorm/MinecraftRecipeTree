package com.recipetree.reiexport118;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExportPlan {
    record PlannedDisplay(Display display, int sourceIndex) {
    }

    record CategoryPlan(
            CategoryRegistry.CategoryConfiguration<?> configuration,
            List<PlannedDisplay> displays) {
        CategoryPlan {
            displays = List.copyOf(displays);
        }
    }

    private final List<CategoryPlan> categories;
    private final int stableEntryCount;
    private final int stableDisplayCount;
    private final int stableCategoryCount;
    private int itemCountAtFinish;

    private ExportPlan(
            List<CategoryPlan> categories,
            int stableEntryCount,
            int stableDisplayCount,
            int stableCategoryCount) {
        this.categories = List.copyOf(categories);
        this.stableEntryCount = stableEntryCount;
        this.stableDisplayCount = stableDisplayCount;
        this.stableCategoryCount = stableCategoryCount;
    }

    List<CategoryPlan> categories() {
        return categories;
    }

    int stableEntryCount() {
        return stableEntryCount;
    }

    int stableDisplayCount() {
        return stableDisplayCount;
    }

    int stableCategoryCount() {
        return stableCategoryCount;
    }

    int itemCountAtFinish() {
        return itemCountAtFinish;
    }

    void setItemCountAtFinish(int count) {
        itemCountAtFinish = count;
    }

    static ExportPlan build(
            ExportRequest request,
            int stableEntryCount,
            int stableDisplayCount,
            int stableCategoryCount) {
        CategoryRegistry categoryRegistry = CategoryRegistry.getInstance();
        DisplayRegistry displayRegistry = DisplayRegistry.getInstance();
        Map<CategoryIdentifier<?>, List<Display>> allDisplays = displayRegistry.getAll();

        LinkedHashMap<String, CategoryRegistry.CategoryConfiguration<?>> configurations = new LinkedHashMap<>();
        for (CategoryRegistry.CategoryConfiguration<?> configuration : categoryRegistry) {
            String id = configuration.getCategoryIdentifier().getIdentifier().toString();
            if (configurations.put(id, configuration) != null) {
                throw new IllegalStateException("REI registered duplicate category identifier " + id);
            }
        }
        if (configurations.size() != stableCategoryCount) {
            throw new IllegalStateException("REI category count changed while creating the plan: stable="
                    + stableCategoryCount + " current=" + configurations.size());
        }

        List<CategoryPlan> plans = request.isQualitySample()
                ? samplePlans(request, configurations, allDisplays)
                : completePlans(configurations, allDisplays);
        int plannedDisplays = plans.stream().mapToInt(plan -> plan.displays().size()).sum();
        if (!request.isQualitySample() && plannedDisplays != stableDisplayCount) {
            throw new IllegalStateException("REI display inventory changed or contains an unregistered category: stable="
                    + stableDisplayCount + " planned=" + plannedDisplays);
        }
        return new ExportPlan(plans, stableEntryCount, stableDisplayCount, stableCategoryCount);
    }

    private static List<CategoryPlan> completePlans(
            LinkedHashMap<String, CategoryRegistry.CategoryConfiguration<?>> configurations,
            Map<CategoryIdentifier<?>, List<Display>> allDisplays) {
        List<CategoryPlan> plans = new ArrayList<>();
        Set<CategoryIdentifier<?>> consumed = new LinkedHashSet<>();
        for (CategoryRegistry.CategoryConfiguration<?> configuration : configurations.values()) {
            CategoryIdentifier<?> identifier = configuration.getCategoryIdentifier();
            List<Display> displays = allDisplays.getOrDefault(identifier, List.of());
            consumed.add(identifier);
            if (!displays.isEmpty()) {
                List<PlannedDisplay> planned = new ArrayList<>(displays.size());
                for (int index = 0; index < displays.size(); index++) {
                    planned.add(new PlannedDisplay(displays.get(index), index));
                }
                plans.add(new CategoryPlan(configuration, planned));
            }
        }
        for (Map.Entry<CategoryIdentifier<?>, List<Display>> entry : allDisplays.entrySet()) {
            if (!entry.getValue().isEmpty() && !consumed.contains(entry.getKey())) {
                throw new IllegalStateException("REI exposes displays for an unregistered category " + entry.getKey());
            }
        }
        return plans;
    }

    private static List<CategoryPlan> samplePlans(
            ExportRequest request,
            LinkedHashMap<String, CategoryRegistry.CategoryConfiguration<?>> configurations,
            Map<CategoryIdentifier<?>, List<Display>> allDisplays) {
        LinkedHashMap<String, List<PlannedDisplay>> selected = new LinkedHashMap<>();
        for (ExportRequest.Sample selector : request.qualitySample) {
            CategoryRegistry.CategoryConfiguration<?> configuration = configurations.get(selector.categoryId());
            if (configuration == null) {
                throw new IllegalStateException("Quality sample category is not registered: " + selector.categoryId());
            }
            List<Display> displays = allDisplays.getOrDefault(configuration.getCategoryIdentifier(), List.of());
            if (selector.sourceIndex() >= displays.size()) {
                throw new IllegalStateException("Quality sample index is outside " + selector.categoryId()
                        + ": requested=" + selector.sourceIndex() + " available=" + displays.size());
            }
            selected.computeIfAbsent(selector.categoryId(), ignored -> new ArrayList<>())
                    .add(new PlannedDisplay(displays.get(selector.sourceIndex()), selector.sourceIndex()));
        }
        List<CategoryPlan> plans = new ArrayList<>(selected.size());
        for (Map.Entry<String, List<PlannedDisplay>> entry : selected.entrySet()) {
            plans.add(new CategoryPlan(configurations.get(entry.getKey()), entry.getValue()));
        }
        return plans;
    }
}
