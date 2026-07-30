package com.recipetree.reiexport118;

import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class ItemPhase implements ExportJob.PhaseRunner {
    private final ExportContext context;
    private final ItemCatalog catalog;
    private final ArrayDeque<EntryStack<?>> queue = new ArrayDeque<>();
    private final int total;
    private int done;

    ItemPhase(ExportContext context) throws IOException {
        this.context = context;
        this.catalog = context.catalog();
        if (context.request.qualityItemSample.isEmpty()) {
            List<EntryStack<?>> available = new ArrayList<>();
            EntryRegistry.getInstance().getEntryStacks().forEach(stack -> {
                if (stack == null || stack.isEmpty()) {
                    context.skippedEmptyEntries++;
                } else {
                    available.add(stack.copy());
                }
            });
            queue.addAll(available);
        } else {
            queue.addAll(QualityItemCandidateCollector.collect(context));
        }
        this.total = queue.size();
    }

    @Override
    public boolean step() {
        EntryStack<?> stack = queue.poll();
        if (stack == null) {
            return true;
        }
        try {
            catalog.ensure(stack);
        } catch (Throwable throwable) {
            context.failure("Item catalog entry " + done + ": " + throwable);
        }
        done++;
        return queue.isEmpty();
    }

    @Override
    public String label() {
        return context.request.qualityItemSample.isEmpty() ? "items" : "quality items";
    }

    @Override
    public int done() {
        return done;
    }

    @Override
    public int total() {
        return total;
    }
}
