package com.recipetree.reiexport118.compat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Mm2PigmentRecipeRegistrationSequenceTest {
    private static final List<String> COLORS = List.of(
            "black", "blue", "brown", "cyan", "gray", "green", "light_blue",
            "light_gray", "lime", "magenta", "orange", "pink", "purple", "red",
            "white", "yellow");
    private static final List<String> STANDARD_GROUPS = List.of(
            "banner", "candle", "carpet", "concrete", "concrete_powder", "dye",
            "stained_glass", "stained_glass_pane", "terracotta", "wool");
    private static final List<String> FLOWERS = List.of(
            "black", "blue", "brown", "green", "large_magenta", "large_pink",
            "large_red", "large_yellow", "light_blue", "light_gray", "lime", "orange",
            "small_magenta", "small_pink", "small_red", "small_yellow", "white");

    @Test
    void provesOneExactAuthoritativeCollectionThroughForcedSerialExecution() {
        Mm2PigmentRecipeRegistrationSequence sequence =
                new Mm2PigmentRecipeRegistrationSequence();
        Thread owner = Thread.currentThread();
        Object collection = new Object();
        var census = productionCensus(collection);
        List<String> sourceOrder = census.origins().stream()
                .map(Mm2PigmentRecipeRegistrationSequence.RecipeOrigin::recipeId)
                .map(ResourceLocation::toString)
                .toList();
        List<String> sortedOrder = sourceOrder.stream().sorted().toList();
        assertNotEquals(sortedOrder, sourceOrder);

        sequence.beginEnd(owner);
        var canonical = sequence.canonicalizeQueued(new Object(), census, owner);
        assertNotSame(collection, canonical.collectionIdentity());
        assertEquals(sourceOrder, census.origins().stream()
                .map(Mm2PigmentRecipeRegistrationSequence.RecipeOrigin::recipeId)
                .map(ResourceLocation::toString)
                .toList());
        assertEquals(
                sortedOrder,
                canonical.origins().stream()
                        .map(Mm2PigmentRecipeRegistrationSequence.RecipeOrigin::recipeId)
                        .map(ResourceLocation::toString)
                        .toList());
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((List<?>) canonical.collectionIdentity()).clear());
        sequence.beginExecution(canonical, owner);
        assertEquals(false, sequence.forceSerialExecution(canonical, true, owner));
        sequence.finishExecution(canonical, owner);
        Mm2PigmentRecipeRegistrationSequence.Summary summary = sequence.finishEnd(owner);

        assertEquals(1, summary.queuedCalls());
        assertEquals(1, summary.executionStarts());
        assertEquals(1, summary.executionFinishes());
        assertEquals(1, summary.serialOverrideCalls());
        assertEquals(true, summary.upstreamMultithreadEligible());
        assertEquals(0, summary.optimizedStarts());
        assertEquals(0, summary.optimizedFinishes());
        assertEquals(177, summary.collectionSize());
        assertEquals(177, summary.distinctRecipeIds());
        assertEquals(177, summary.distinctRecipeIdentities());
        assertEquals(
                Mm2PigmentRecipeRegistrationSequence.EXPECTED_SORTED_RECIPE_IDS_SHA256,
                summary.orderedRecipeIdsSha256());
        assertEquals(
                Mm2PigmentRecipeRegistrationSequence.EXPECTED_SORTED_RECIPE_IDS_SHA256,
                summary.sortedRecipeIdsSha256());
        assertEquals(
                Mm2PigmentRecipeRegistrationSequence.ExecutionPath.DIRECT_SERIAL_OVERRIDE,
                summary.executionPath());
        sequence.requireComplete();
        assertEquals(
                Mm2PigmentRecipeRegistrationSequence.State.COMPLETE,
                sequence.state());
    }

    @Test
    void rejectsWrongUpstreamCardinalityBeforeExecution() {
        Mm2PigmentRecipeRegistrationSequence sequence =
                new Mm2PigmentRecipeRegistrationSequence();
        Thread owner = Thread.currentThread();
        sequence.beginEnd(owner);

        assertThrows(
                IllegalStateException.class,
                () -> sequence.canonicalizeQueued(
                        new Object(), census(new Object(), 100, "inflated"), owner));
        assertEquals(
                Mm2PigmentRecipeRegistrationSequence.State.FAILED,
                sequence.state());
    }

    @Test
    void rejectsNullDuplicateIdsAndDuplicateObjectIdentitiesBeforeCopying() {
        Thread owner = Thread.currentThread();
        var source = productionCensus(new Object());

        List<Mm2PigmentRecipeRegistrationSequence.RecipeOrigin> nullId =
                new ArrayList<>(source.origins());
        nullId.set(0, new Mm2PigmentRecipeRegistrationSequence.RecipeOrigin(
                nullId.get(0).identity(), null));
        assertRejectedAtQueue(owner, new Mm2PigmentRecipeRegistrationSequence.Census(
                new Object(), nullId.size(), List.copyOf(nullId)));

        List<Mm2PigmentRecipeRegistrationSequence.RecipeOrigin> nullIdentity =
                new ArrayList<>(source.origins());
        nullIdentity.set(0, new Mm2PigmentRecipeRegistrationSequence.RecipeOrigin(
                null, nullIdentity.get(0).recipeId()));
        assertRejectedAtQueue(owner, new Mm2PigmentRecipeRegistrationSequence.Census(
                new Object(), nullIdentity.size(), List.copyOf(nullIdentity)));

        List<Mm2PigmentRecipeRegistrationSequence.RecipeOrigin> duplicateId =
                new ArrayList<>(source.origins());
        duplicateId.set(1, new Mm2PigmentRecipeRegistrationSequence.RecipeOrigin(
                duplicateId.get(1).identity(), duplicateId.get(0).recipeId()));
        assertRejectedAtQueue(owner, new Mm2PigmentRecipeRegistrationSequence.Census(
                new Object(), duplicateId.size(), List.copyOf(duplicateId)));

        List<Mm2PigmentRecipeRegistrationSequence.RecipeOrigin> duplicateIdentity =
                new ArrayList<>(source.origins());
        duplicateIdentity.set(1, new Mm2PigmentRecipeRegistrationSequence.RecipeOrigin(
                duplicateIdentity.get(0).identity(), duplicateIdentity.get(1).recipeId()));
        assertRejectedAtQueue(owner, new Mm2PigmentRecipeRegistrationSequence.Census(
                new Object(), duplicateIdentity.size(), List.copyOf(duplicateIdentity)));
    }

    @Test
    void rejectsDuplicateQueueAndExecutionReplay() {
        Thread owner = Thread.currentThread();
        Object collection = new Object();
        var census = productionCensus(collection);

        Mm2PigmentRecipeRegistrationSequence duplicateQueue =
                new Mm2PigmentRecipeRegistrationSequence();
        duplicateQueue.beginEnd(owner);
        duplicateQueue.canonicalizeQueued(new Object(), census, owner);
        assertThrows(
                IllegalStateException.class,
                () -> duplicateQueue.canonicalizeQueued(new Object(), census, owner));

        Mm2PigmentRecipeRegistrationSequence replay =
                new Mm2PigmentRecipeRegistrationSequence();
        replay.beginEnd(owner);
        var canonical = replay.canonicalizeQueued(new Object(), census, owner);
        replay.beginExecution(canonical, owner);
        assertThrows(
                IllegalStateException.class,
                () -> replay.beginExecution(canonical, owner));
    }

    @Test
    void rejectsCollectionIdentityAndMembershipDrift() {
        Thread owner = Thread.currentThread();
        Object collection = new Object();
        var census = productionCensus(collection);

        Mm2PigmentRecipeRegistrationSequence identityDrift =
                new Mm2PigmentRecipeRegistrationSequence();
        identityDrift.beginEnd(owner);
        identityDrift.canonicalizeQueued(new Object(), census, owner);
        assertThrows(
                IllegalStateException.class,
                () -> identityDrift.beginExecution(
                        productionCensus(new Object()), owner));

        Mm2PigmentRecipeRegistrationSequence membershipDrift =
                new Mm2PigmentRecipeRegistrationSequence();
        membershipDrift.beginEnd(owner);
        membershipDrift.canonicalizeQueued(new Object(), census, owner);
        assertThrows(
                IllegalStateException.class,
                () -> membershipDrift.beginExecution(
                        census(collection, 177, "different"), owner));
    }

    @Test
    void rejectsBypassedSerialOverrideOptimizedPathAndOwnerThreadDrift() {
        Thread owner = Thread.currentThread();
        Object collection = new Object();
        var census = productionCensus(collection);

        Mm2PigmentRecipeRegistrationSequence bypassed =
                new Mm2PigmentRecipeRegistrationSequence();
        bypassed.beginEnd(owner);
        var bypassedCanonical = bypassed.canonicalizeQueued(new Object(), census, owner);
        bypassed.beginExecution(bypassedCanonical, owner);
        assertThrows(
                IllegalStateException.class,
                () -> bypassed.forceSerialExecution(bypassedCanonical, false, owner));

        Mm2PigmentRecipeRegistrationSequence optimized =
                new Mm2PigmentRecipeRegistrationSequence();
        optimized.beginEnd(owner);
        var optimizedCanonical = optimized.canonicalizeQueued(new Object(), census, owner);
        optimized.beginExecution(optimizedCanonical, owner);
        assertThrows(
                IllegalStateException.class,
                () -> optimized.beginOptimized(optimizedCanonical, owner));

        Mm2PigmentRecipeRegistrationSequence threadDrift =
                new Mm2PigmentRecipeRegistrationSequence();
        threadDrift.beginEnd(owner);
        assertThrows(
                IllegalStateException.class,
                () -> threadDrift.canonicalizeQueued(
                        new Object(), census, new Thread("not-owner")));
    }

    private static Mm2PigmentRecipeRegistrationSequence.Census productionCensus(
            Object collection
    ) {
        List<Mm2PigmentRecipeRegistrationSequence.RecipeOrigin> origins =
                new ArrayList<>(177);
        for (String group : STANDARD_GROUPS) {
            for (String color : COLORS) {
                origins.add(origin("pigment_extracting/" + group + "/" + color));
            }
        }
        for (String flower : FLOWERS) {
            origins.add(origin("pigment_extracting/flower/" + flower));
        }
        assertEquals(177, origins.size());
        return new Mm2PigmentRecipeRegistrationSequence.Census(
                collection, origins.size(), List.copyOf(origins));
    }

    private static Mm2PigmentRecipeRegistrationSequence.RecipeOrigin origin(String path) {
        return new Mm2PigmentRecipeRegistrationSequence.RecipeOrigin(
                new Object(), new ResourceLocation("mekanism", path));
    }

    private static Mm2PigmentRecipeRegistrationSequence.Census census(
            Object collection,
            int count,
            String prefix
    ) {
        List<Mm2PigmentRecipeRegistrationSequence.RecipeOrigin> origins =
                new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            origins.add(new Mm2PigmentRecipeRegistrationSequence.RecipeOrigin(
                    new Object(),
                    new ResourceLocation("mekanism", prefix + "_" + index)));
        }
        return new Mm2PigmentRecipeRegistrationSequence.Census(
                collection, count, List.copyOf(origins));
    }

    private static void assertRejectedAtQueue(
            Thread owner,
            Mm2PigmentRecipeRegistrationSequence.Census census
    ) {
        Mm2PigmentRecipeRegistrationSequence sequence =
                new Mm2PigmentRecipeRegistrationSequence();
        sequence.beginEnd(owner);
        assertThrows(
                IllegalStateException.class,
                () -> sequence.canonicalizeQueued(new Object(), census, owner));
        assertEquals(
                Mm2PigmentRecipeRegistrationSequence.State.FAILED,
                sequence.state());
    }
}
