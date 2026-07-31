package com.recipetree.reiexport118.compat;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure fail-closed state machine for the pinned JEI pigment-recipe registration seam.
 *
 * <p>This observer never mutates a recipe collection or REI display registry. It first validates
 * the exact 177-recipe Mekanism collection, makes an immutable defensive copy ordered by
 * authoritative Recipe ID, and proves that this canonical collection is queued once and executed
 * once through {@code JEIRecipeRegistration.addRecipes0}. REI 8.0.89 ordinarily sends that list
 * through an asynchronous 50-recipe partitioner which has historically exposed only zero or one
 * completed partition in MM2. The exporter therefore requires one exact decision override from
 * the pinned multithreaded path to the direct serial path; reaching the optimized method is
 * contract drift rather than a reason to deduplicate or retry.</p>
 */
final class Mm2PigmentRecipeRegistrationSequence {
    static final int EXPECTED_RECIPES =
            Mm2DeterminismContract.MEKANISM_PIGMENT_EXTRACTING_RECIPES;
    static final String EXPECTED_SORTED_RECIPE_IDS_SHA256 =
            Mm2DeterminismContract.MEKANISM_PIGMENT_EXTRACTING_IDS_SHA256;

    enum State {
        INACTIVE,
        END,
        COMPLETE,
        FAILED
    }

    enum ExecutionPath {
        DIRECT_SERIAL_OVERRIDE,
        OPTIMIZED
    }

    record RecipeOrigin(Object identity, ResourceLocation recipeId) {
    }

    record Census(
            Object collectionIdentity,
            int collectionSize,
            List<RecipeOrigin> origins
    ) {
    }

    record Summary(
            int queuedCalls,
            int executionStarts,
            int executionFinishes,
            int serialOverrideCalls,
            boolean upstreamMultithreadEligible,
            int optimizedStarts,
            int optimizedFinishes,
            int collectionIdentityHash,
            int collectionSize,
            int distinctRecipeIds,
            int distinctRecipeIdentities,
            String orderedRecipeIdsSha256,
            String sortedRecipeIdsSha256,
            ExecutionPath executionPath
    ) {
    }

    private State state = State.INACTIVE;
    private Thread ownerThread;
    private Object registrationIdentity;
    private Object collectionIdentity;
    private Map<Object, Boolean> recipeIdentities;
    private Map<ResourceLocation, Boolean> recipeIds;
    private int queuedCalls;
    private int executionStarts;
    private int executionFinishes;
    private int serialOverrideCalls;
    private boolean upstreamMultithreadEligible;
    private int optimizedStarts;
    private int optimizedFinishes;
    private boolean executionActive;
    private boolean optimizedActive;
    private String orderedRecipeIdsSha256;
    private String sortedRecipeIdsSha256;

    synchronized void beginEnd(Thread thread) {
        requireState(State.INACTIVE, "begin owned END");
        ownerThread = requireThread(thread);
        state = State.END;
    }

    synchronized boolean isObservingEnd() {
        return state == State.END;
    }

    synchronized Census canonicalizeQueued(
            Object registration,
            Census census,
            Thread thread
    ) {
        requireState(State.END, "observe queued pigment recipes");
        requireOwner(thread);
        if (registration == null) {
            reject("JEI pigment recipe-registration identity must not be null");
        }
        if (queuedCalls != 0) {
            reject("Mekanism pigment recipe collection was queued more than once: priorCalls="
                    + queuedCalls);
        }
        ValidatedCensus source = validateCensus(census, "queue source");
        if (!EXPECTED_SORTED_RECIPE_IDS_SHA256.equals(source.sortedRecipeIdsSha256())) {
            reject("Mekanism pigment authoritative Recipe ID membership drift at queue source: "
                    + "expectedSortedIdsSha256=" + EXPECTED_SORTED_RECIPE_IDS_SHA256
                    + ", actualSortedIdsSha256=" + source.sortedRecipeIdsSha256());
        }

        List<RecipeOrigin> mutableOrigins = new ArrayList<>(census.origins());
        mutableOrigins.sort(Comparator.comparing(origin -> origin.recipeId().toString()));
        List<RecipeOrigin> canonicalOrigins = List.copyOf(mutableOrigins);
        List<Object> mutableRecipes = new ArrayList<>(canonicalOrigins.size());
        for (RecipeOrigin origin : canonicalOrigins) {
            mutableRecipes.add(origin.identity());
        }
        List<Object> canonicalRecipes = List.copyOf(mutableRecipes);
        Census canonical = new Census(
                canonicalRecipes,
                canonicalRecipes.size(),
                canonicalOrigins);
        ValidatedCensus validated = validateCensus(canonical, "canonical queue copy");
        if (!EXPECTED_SORTED_RECIPE_IDS_SHA256.equals(validated.orderedRecipeIdsSha256())
                || !EXPECTED_SORTED_RECIPE_IDS_SHA256.equals(
                        validated.sortedRecipeIdsSha256())) {
            reject("Mekanism pigment canonical Recipe ID order drift at queue: "
                    + "expectedOrderedIdsSha256=" + EXPECTED_SORTED_RECIPE_IDS_SHA256
                    + ", actualOrderedIdsSha256=" + validated.orderedRecipeIdsSha256()
                    + ", actualSortedIdsSha256=" + validated.sortedRecipeIdsSha256());
        }
        registrationIdentity = registration;
        collectionIdentity = canonical.collectionIdentity();
        recipeIdentities = validated.identities();
        recipeIds = validated.ids();
        orderedRecipeIdsSha256 = validated.orderedRecipeIdsSha256();
        sortedRecipeIdsSha256 = validated.sortedRecipeIdsSha256();
        queuedCalls = 1;
        return canonical;
    }

    synchronized void beginExecution(Census census, Thread thread) {
        requireState(State.END, "begin pigment addRecipes0 execution");
        requireOwner(thread);
        requireQueued();
        if (executionStarts != 0 || executionActive || executionFinishes != 0) {
            reject("Mekanism pigment addRecipes0 replay detected: starts=" + executionStarts
                    + ", finishes=" + executionFinishes
                    + ", active=" + executionActive);
        }
        requireSameCensus(census, "addRecipes0 HEAD");
        executionStarts = 1;
        executionActive = true;
    }

    synchronized boolean forceSerialExecution(
            Census census,
            boolean originalDecision,
            Thread thread
    ) {
        requireState(State.END, "override pigment multithread decision");
        requireOwner(thread);
        requireQueued();
        if (executionStarts != 1 || !executionActive || executionFinishes != 0) {
            reject("Mekanism pigment serial override occurred outside addRecipes0 execution: "
                    + "starts=" + executionStarts
                    + ", finishes=" + executionFinishes
                    + ", active=" + executionActive);
        }
        if (serialOverrideCalls != 0) {
            reject("Mekanism pigment multithread decision was overridden more than once: calls="
                    + serialOverrideCalls);
        }
        requireSameCensus(census, "canRecipesBeMultithreaded RETURN");
        if (!originalDecision) {
            reject("pinned REI multithread predicate no longer selects the authoritative "
                    + EXPECTED_RECIPES + "-recipe pigment list");
        }
        serialOverrideCalls = 1;
        upstreamMultithreadEligible = true;
        return false;
    }

    synchronized void finishExecution(Census census, Thread thread) {
        requireState(State.END, "finish pigment addRecipes0 execution");
        requireOwner(thread);
        if (executionStarts != 1 || !executionActive || executionFinishes != 0) {
            reject("Mekanism pigment addRecipes0 completion drift: starts=" + executionStarts
                    + ", finishes=" + executionFinishes
                    + ", active=" + executionActive);
        }
        if (serialOverrideCalls != 1 || !upstreamMultithreadEligible) {
            reject("Mekanism pigment direct execution did not pass through the exact serial "
                    + "override: overrideCalls=" + serialOverrideCalls
                    + ", upstreamMultithreadEligible=" + upstreamMultithreadEligible);
        }
        if (optimizedActive || optimizedStarts != optimizedFinishes) {
            reject("Mekanism pigment optimized execution remained incomplete: starts="
                    + optimizedStarts + ", finishes=" + optimizedFinishes
                    + ", active=" + optimizedActive);
        }
        requireSameCensus(census, "addRecipes0 RETURN");
        executionActive = false;
        executionFinishes = 1;
    }

    synchronized void beginOptimized(Census census, Thread thread) {
        requireState(State.END, "begin pigment addRecipesOptimized execution");
        requireOwner(thread);
        requireSameCensus(census, "addRecipesOptimized HEAD");
        optimizedStarts = Math.addExact(optimizedStarts, 1);
        optimizedActive = true;
        reject("Mekanism pigment recipes unexpectedly reached addRecipesOptimized: "
                + "collectionSize=" + census.collectionSize()
                + ", requiredSerialOverrideCardinality=" + EXPECTED_RECIPES);
    }

    synchronized void finishOptimized(Census census, Thread thread) {
        requireState(State.END, "finish pigment addRecipesOptimized execution");
        requireOwner(thread);
        requireSameCensus(census, "addRecipesOptimized RETURN");
        if (optimizedStarts != 1 || !optimizedActive || optimizedFinishes != 0) {
            reject("Mekanism pigment optimized completion drift: starts=" + optimizedStarts
                    + ", finishes=" + optimizedFinishes
                    + ", active=" + optimizedActive);
        }
        optimizedActive = false;
        optimizedFinishes = 1;
    }

    synchronized Summary finishEnd(Thread thread) {
        requireState(State.END, "finish owned END");
        requireOwner(thread);
        requireQueued();
        if (queuedCalls != 1
                || executionStarts != 1
                || executionFinishes != 1
                || executionActive
                || serialOverrideCalls != 1
                || !upstreamMultithreadEligible
                || optimizedStarts != 0
                || optimizedFinishes != 0
                || optimizedActive) {
            reject("Mekanism pigment JEI registration cardinality drift: queued=" + queuedCalls
                    + ", executionStarts=" + executionStarts
                    + ", executionFinishes=" + executionFinishes
                    + ", serialOverrideCalls=" + serialOverrideCalls
                    + ", upstreamMultithreadEligible=" + upstreamMultithreadEligible
                    + ", optimizedStarts=" + optimizedStarts
                    + ", optimizedFinishes=" + optimizedFinishes
                    + ", executionActive=" + executionActive
                    + ", optimizedActive=" + optimizedActive);
        }
        Summary summary = new Summary(
                queuedCalls,
                executionStarts,
                executionFinishes,
                serialOverrideCalls,
                upstreamMultithreadEligible,
                optimizedStarts,
                optimizedFinishes,
                System.identityHashCode(collectionIdentity),
                EXPECTED_RECIPES,
                recipeIds.size(),
                recipeIdentities.size(),
                orderedRecipeIdsSha256,
                sortedRecipeIdsSha256,
                ExecutionPath.DIRECT_SERIAL_OVERRIDE);
        state = State.COMPLETE;
        return summary;
    }

    synchronized void requireComplete() {
        requireState(State.COMPLETE, "authorize export");
    }

    synchronized State state() {
        return state;
    }

    synchronized void fail(Throwable failure) {
        if (failure == null) {
            throw new IllegalArgumentException(
                    "pigment recipe-registration failure must not be null");
        }
        state = State.FAILED;
    }

    private void requireQueued() {
        if (queuedCalls != 1
                || registrationIdentity == null
                || collectionIdentity == null
                || recipeIdentities == null
                || recipeIds == null
                || orderedRecipeIdsSha256 == null
                || sortedRecipeIdsSha256 == null) {
            reject("Mekanism pigment collection execution occurred without one authoritative "
                    + "queue observation");
        }
    }

    private void requireSameCensus(Census census, String seam) {
        ValidatedCensus validated = validateCensus(census, seam);
        if (census.collectionIdentity() != collectionIdentity) {
            reject("Mekanism pigment collection identity drift at " + seam
                    + ": expectedIdentity=" + System.identityHashCode(collectionIdentity)
                    + ", actualIdentity="
                    + System.identityHashCode(census.collectionIdentity()));
        }
        if (!sameIdentitySet(recipeIdentities, validated.identities())
                || !recipeIds.keySet().equals(validated.ids().keySet())
                || !orderedRecipeIdsSha256.equals(validated.orderedRecipeIdsSha256())
                || !sortedRecipeIdsSha256.equals(validated.sortedRecipeIdsSha256())) {
            reject("Mekanism pigment recipe membership drift at " + seam
                    + ": expectedDistinctIdentities=" + recipeIdentities.size()
                    + ", actualDistinctIdentities=" + validated.identities().size()
                    + ", expectedDistinctIds=" + recipeIds.size()
                    + ", actualDistinctIds=" + validated.ids().size()
                    + ", expectedOrderedIdsSha256=" + orderedRecipeIdsSha256
                    + ", actualOrderedIdsSha256=" + validated.orderedRecipeIdsSha256()
                    + ", expectedSortedIdsSha256=" + sortedRecipeIdsSha256
                    + ", actualSortedIdsSha256=" + validated.sortedRecipeIdsSha256());
        }
    }

    private ValidatedCensus validateCensus(Census census, String seam) {
        if (census == null || census.collectionIdentity() == null || census.origins() == null) {
            reject("Mekanism pigment recipe census is incomplete at " + seam);
        }
        if (census.collectionSize() != EXPECTED_RECIPES
                || census.origins().size() != census.collectionSize()) {
            reject("Mekanism pigment recipe cardinality drift at " + seam
                    + ": expected=" + EXPECTED_RECIPES
                    + ", collectionSize=" + census.collectionSize()
                    + ", observedOrigins=" + census.origins().size());
        }

        Map<Object, Boolean> identities = new IdentityHashMap<>();
        Map<ResourceLocation, Boolean> ids = new HashMap<>();
        for (int index = 0; index < census.origins().size(); index++) {
            RecipeOrigin origin = census.origins().get(index);
            if (origin == null || origin.identity() == null || origin.recipeId() == null) {
                reject("Mekanism pigment recipe census contains a null origin at " + seam
                        + ", index=" + index);
            }
            if (identities.put(origin.identity(), Boolean.TRUE) != null) {
                reject("Mekanism pigment recipe object identity replay at " + seam
                        + ", index=" + index + ", recipeId=" + origin.recipeId());
            }
            if (ids.put(origin.recipeId(), Boolean.TRUE) != null) {
                reject("Mekanism pigment Recipe ID replay at " + seam
                        + ", index=" + index + ", recipeId=" + origin.recipeId());
            }
        }
        if (identities.size() != EXPECTED_RECIPES || ids.size() != EXPECTED_RECIPES) {
            reject("Mekanism pigment distinct-origin cardinality drift at " + seam
                    + ": identities=" + identities.size() + ", ids=" + ids.size());
        }
        return new ValidatedCensus(
                identities,
                ids,
                orderedRecipeIdsSha256(census.origins()),
                sortedRecipeIdsSha256(census.origins()));
    }

    private String orderedRecipeIdsSha256(List<RecipeOrigin> origins) {
        return recipeIdsSha256(origins.stream()
                .map(origin -> origin.recipeId().toString())
                .toList());
    }

    private String sortedRecipeIdsSha256(List<RecipeOrigin> origins) {
        return recipeIdsSha256(origins.stream()
                .map(origin -> origin.recipeId().toString())
                .sorted(Comparator.naturalOrder())
                .toList());
    }

    private String recipeIdsSha256(List<String> ids) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        for (String recipeId : ids) {
            byte[] id = recipeId.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (id.length >>> 24));
            digest.update((byte) (id.length >>> 16));
            digest.update((byte) (id.length >>> 8));
            digest.update((byte) id.length);
            digest.update(id);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private boolean sameIdentitySet(Map<Object, Boolean> expected, Map<Object, Boolean> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (Object identity : expected.keySet()) {
            if (!actual.containsKey(identity)) {
                return false;
            }
        }
        return true;
    }

    private void requireState(State expected, String operation) {
        if (state != expected) {
            reject("cannot " + operation + " from pigment-registration state " + state
                    + "; expected=" + expected);
        }
    }

    private Thread requireThread(Thread thread) {
        if (thread == null) {
            reject("owned pigment recipe-registration thread must not be null");
        }
        return thread;
    }

    private void requireOwner(Thread thread) {
        requireThread(thread);
        if (thread != ownerThread) {
            reject("pigment recipe-registration thread identity drift: expected="
                    + ownerThread.getName() + ", actual=" + thread.getName());
        }
    }

    private void reject(String message) {
        state = State.FAILED;
        throw new IllegalStateException(message);
    }

    private record ValidatedCensus(
            Map<Object, Boolean> identities,
            Map<ResourceLocation, Boolean> ids,
            String orderedRecipeIdsSha256,
            String sortedRecipeIdsSha256
    ) {
    }
}
