package com.recipetree.neiexport1710;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned deterministic repair of the IC2 Crop Plugin's NEI query-bucket closure.
 *
 * <p>The plugin evaluates every canonical two-parent/result candidate, but
 * {@code BreedResult.addToMaps} de-duplicates independently in each parent usage bucket and
 * result craft bucket. Its match relation is exactly result identity, points, and input arity;
 * total probability points and graph inputs do not participate. This contract preserves those
 * bucket-local first winners without retaining the full simulator candidate corpus or depending
 * on JVM object hashes. It intentionally does not reproduce the preceding broken global
 * {@code LinkedHashSet} stage byte-for-byte; canonical candidate order replaces that stage so the
 * repaired closure is reproducible.</p>
 */
final class DeterministicCropReplayContract {
    static final int INPUT_ARITY = 2;
    static final int MAX_REPLAY_CROPS = 512;
    private static final int MAX_PACKED_CROPS = 0xffff;

    static final class Winner<C> {
        final int selectionIndex;
        final long simulatorCandidateIndex;
        final C leftInput;
        final C rightInput;
        final C output;
        final String leftInputId;
        final String rightInputId;
        final String outputId;
        final int points;
        final int totalPoints;
        final int inputArity;
        final boolean leftUsageMember;
        final boolean rightUsageMember;
        final boolean craftMember;
        final String graphCanonical;

        Winner(int selectionIndex,
               long simulatorCandidateIndex,
               CropEntry<C> left,
               CropEntry<C> right,
               CropEntry<C> output,
               int points,
               int totalPoints,
               boolean leftUsageMember,
               boolean rightUsageMember,
               boolean craftMember) {
            this.selectionIndex = selectionIndex;
            this.simulatorCandidateIndex = simulatorCandidateIndex;
            this.leftInput = left.crop;
            this.rightInput = right.crop;
            this.output = output.crop;
            this.leftInputId = left.id;
            this.rightInputId = right.id;
            this.outputId = output.id;
            this.points = points;
            this.totalPoints = totalPoints;
            this.inputArity = INPUT_ARITY;
            this.leftUsageMember = leftUsageMember;
            this.rightUsageMember = rightUsageMember;
            this.craftMember = craftMember;

            StringBuilder canonical = new StringBuilder(160);
            canonical.append("crop-replay-graph-v1;");
            canonical.append('A').append(INPUT_ARITY).append(';');
            canonical.append('L');
            appendField(canonical, left.id);
            canonical.append('R');
            appendField(canonical, right.id);
            canonical.append('O');
            appendField(canonical, output.id);
            canonical.append('P').append(points).append(';');
            canonical.append('T').append(totalPoints).append(';');
            this.graphCanonical = canonical.toString();
        }

        boolean usageMember() {
            return leftUsageMember || rightUsageMember;
        }
    }

    static final class Snapshot<C> {
        final List<Winner<C>> winners;
        final int cropCount;
        final int pairCount;
        final long simulatorCandidateCount;
        final int canonicalGlobalMatchKeyCount;
        final int bucketLocalClosureCount;
        final int craftWinnerCount;
        final int usageWinnerIdentityCount;
        final int usageOccurrenceCount;
        final int bothViewWinnerCount;
        final int craftOnlyWinnerCount;
        final int usageOnlyWinnerCount;
        final int minimumCandidatesPerPair;
        final int maximumCandidatesPerPair;
        final int minimumTotalPoints;
        final int maximumTotalPoints;
        final String fingerprint;

        Snapshot(List<Winner<C>> winners,
                 int cropCount,
                 int pairCount,
                 long simulatorCandidateCount,
                 int canonicalGlobalMatchKeyCount,
                 int bucketLocalClosureCount,
                 int craftWinnerCount,
                 int usageWinnerIdentityCount,
                 int usageOccurrenceCount,
                 int bothViewWinnerCount,
                 int craftOnlyWinnerCount,
                 int usageOnlyWinnerCount,
                 int minimumCandidatesPerPair,
                 int maximumCandidatesPerPair,
                 int minimumTotalPoints,
                 int maximumTotalPoints,
                 String fingerprint) {
            this.winners = winners;
            this.cropCount = cropCount;
            this.pairCount = pairCount;
            this.simulatorCandidateCount = simulatorCandidateCount;
            this.canonicalGlobalMatchKeyCount = canonicalGlobalMatchKeyCount;
            this.bucketLocalClosureCount = bucketLocalClosureCount;
            this.craftWinnerCount = craftWinnerCount;
            this.usageWinnerIdentityCount = usageWinnerIdentityCount;
            this.usageOccurrenceCount = usageOccurrenceCount;
            this.bothViewWinnerCount = bothViewWinnerCount;
            this.craftOnlyWinnerCount = craftOnlyWinnerCount;
            this.usageOnlyWinnerCount = usageOnlyWinnerCount;
            this.minimumCandidatesPerPair = minimumCandidatesPerPair;
            this.maximumCandidatesPerPair = maximumCandidatesPerPair;
            this.minimumTotalPoints = minimumTotalPoints;
            this.maximumTotalPoints = maximumTotalPoints;
            this.fingerprint = fingerprint;
        }
    }

    private static final class CropEntry<C> {
        final C crop;
        final String id;

        CropEntry(C crop, String id) {
            this.crop = crop;
            this.id = id;
        }
    }

    /** Allocation-free membership set for the hot 1.26-million-candidate replay loop. */
    private static final class LongSet {
        private long[] table = new long[8];
        private int size;
        private int resizeAt = 5;

        boolean add(long value) throws ExportFailure {
            if (value == 0L) {
                throw new ExportFailure("INTERNAL_ERROR",
                        "IC2 crop replay attempted to store the reserved zero match key");
            }
            if (size + 1 > resizeAt) {
                grow();
            }
            int mask = table.length - 1;
            int slot = mix(value) & mask;
            while (true) {
                long present = table[slot];
                if (present == 0L) {
                    table[slot] = value;
                    size++;
                    return true;
                }
                if (present == value) {
                    return false;
                }
                slot = (slot + 1) & mask;
            }
        }

        int size() {
            return size;
        }

        private void grow() throws ExportFailure {
            if (table.length >= (1 << 29)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 crop replay match-key set exceeds its bounded representation");
            }
            long[] old = table;
            table = new long[old.length << 1];
            resizeAt = (table.length * 2) / 3;
            int oldSize = size;
            size = 0;
            for (long value : old) {
                if (value != 0L) {
                    insertKnownAbsent(value);
                }
            }
            if (size != oldSize) {
                throw new ExportFailure("INTERNAL_ERROR",
                        "IC2 crop replay lost a match key while growing its primitive set");
            }
        }

        private void insertKnownAbsent(long value) {
            int mask = table.length - 1;
            int slot = mix(value) & mask;
            while (table[slot] != 0L) {
                slot = (slot + 1) & mask;
            }
            table[slot] = value;
            size++;
        }

        private static int mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdl;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53l;
            value ^= value >>> 33;
            return (int) value;
        }
    }

    private DeterministicCropReplayContract() {
    }

    static <C> Snapshot<C> replay(
            List<C> crops,
            DeterministicCropMatrixContract.CanonicalId<C> canonicalIds,
            DeterministicCropMatrixContract.Ratio<C> ratios) throws ExportFailure {
        List<CropEntry<C>> ordered = canonicalCrops(crops, canonicalIds);
        final int count = ordered.size();
        int[][] ratioMatrix = ratioMatrix(ordered, ratios);

        LongSet[] usageBuckets = new LongSet[count];
        LongSet[] craftBuckets = new LongSet[count];
        for (int index = 0; index < count; index++) {
            usageBuckets[index] = new LongSet();
            craftBuckets[index] = new LongSet();
        }
        LongSet globalMatchKeys = new LongSet();
        List<Winner<C>> winners = new ArrayList<Winner<C>>();

        long simulatorCandidates = 0L;
        int pairCount = 0;
        int craftWinners = 0;
        int usageWinnerIdentities = 0;
        int usageOccurrences = 0;
        int bothViewWinners = 0;
        int craftOnlyWinners = 0;
        int usageOnlyWinners = 0;
        int minimumCandidates = Integer.MAX_VALUE;
        int maximumCandidates = Integer.MIN_VALUE;
        int minimumTotal = Integer.MAX_VALUE;
        int maximumTotal = Integer.MIN_VALUE;

        for (int left = 0; left < count; left++) {
            for (int right = left + 1; right < count; right++) {
                long totalLong = 0L;
                for (int result = 0; result < count; result++) {
                    totalLong += (long) ratioMatrix[result][left]
                            + (long) ratioMatrix[result][right];
                }
                if (totalLong <= 0L || totalLong > Integer.MAX_VALUE) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "IC2 crop replay pair has an invalid total-points denominator: left="
                                    + ordered.get(left).id + ", right="
                                    + ordered.get(right).id + ", total=" + totalLong);
                }
                int total = (int) totalLong;
                int pairCandidates = 0;
                for (int result = 0; result < count; result++) {
                    long pointsLong = (long) ratioMatrix[result][left]
                            + (long) ratioMatrix[result][right];
                    if (result == left || result == right || pointsLong <= 0L) {
                        continue;
                    }
                    if (pointsLong > Integer.MAX_VALUE) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                "IC2 crop replay candidate points overflow an int: output="
                                        + ordered.get(result).id + ", points=" + pointsLong);
                    }
                    int points = (int) pointsLong;
                    long candidateIndex = simulatorCandidates;
                    simulatorCandidates++;
                    pairCandidates++;

                    long matchKey = matchKey(result, points, INPUT_ARITY);
                    boolean globalWinner = globalMatchKeys.add(matchKey);
                    // This order is the pinned BreedResult.addToMaps contract.
                    boolean leftUsage = usageBuckets[left].add(matchKey);
                    boolean rightUsage = usageBuckets[right].add(matchKey);
                    boolean craft = craftBuckets[result].add(matchKey);
                    if (globalWinner != craft) {
                        throw new ExportFailure("INTERNAL_ERROR",
                                "canonical global and output-bucket IC2 crop de-duplication differ");
                    }
                    if (craft && (!leftUsage || !rightUsage)) {
                        throw new ExportFailure("INTERNAL_ERROR",
                                "first global IC2 crop candidate did not win both usage buckets");
                    }
                    if (!leftUsage && !rightUsage && !craft) {
                        continue;
                    }

                    Winner<C> winner = new Winner<C>(
                            winners.size(), candidateIndex,
                            ordered.get(left), ordered.get(right), ordered.get(result),
                            points, total, leftUsage, rightUsage, craft);
                    winners.add(winner);
                    if (craft) {
                        craftWinners++;
                    }
                    if (winner.usageMember()) {
                        usageWinnerIdentities++;
                    }
                    usageOccurrences += (leftUsage ? 1 : 0) + (rightUsage ? 1 : 0);
                    if (craft && winner.usageMember()) {
                        bothViewWinners++;
                    } else if (craft) {
                        craftOnlyWinners++;
                    } else {
                        usageOnlyWinners++;
                    }
                }
                pairCount++;
                minimumCandidates = Math.min(minimumCandidates, pairCandidates);
                maximumCandidates = Math.max(maximumCandidates, pairCandidates);
                minimumTotal = Math.min(minimumTotal, total);
                maximumTotal = Math.max(maximumTotal, total);
            }
        }

        validateClosure(
                count, pairCount, simulatorCandidates, winners,
                usageBuckets, craftBuckets, globalMatchKeys,
                craftWinners, usageWinnerIdentities, usageOccurrences,
                bothViewWinners, craftOnlyWinners, usageOnlyWinners);
        String fingerprint = fingerprint(
                ordered, ratioMatrix, winners, pairCount, simulatorCandidates,
                globalMatchKeys.size(), craftWinners,
                usageWinnerIdentities, usageOccurrences,
                bothViewWinners, craftOnlyWinners, usageOnlyWinners);
        return new Snapshot<C>(
                Collections.unmodifiableList(new ArrayList<Winner<C>>(winners)),
                count,
                pairCount,
                simulatorCandidates,
                globalMatchKeys.size(),
                winners.size(),
                craftWinners,
                usageWinnerIdentities,
                usageOccurrences,
                bothViewWinners,
                craftOnlyWinners,
                usageOnlyWinners,
                minimumCandidates,
                maximumCandidates,
                minimumTotal,
                maximumTotal,
                fingerprint);
    }

    private static <C> List<CropEntry<C>> canonicalCrops(
            List<C> crops,
            DeterministicCropMatrixContract.CanonicalId<C> canonicalIds)
            throws ExportFailure {
        if (crops == null || canonicalIds == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "deterministic IC2 crop replay received a null dependency");
        }
        if (crops.size() < 2) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "deterministic IC2 crop replay requires at least two CropCards");
        }
        if (crops.size() > MAX_REPLAY_CROPS) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "deterministic IC2 crop replay refuses an unbounded O(n^3) universe of "
                            + crops.size() + " CropCards; maximum=" + MAX_REPLAY_CROPS);
        }

        List<CropEntry<C>> ordered = new ArrayList<CropEntry<C>>(crops.size());
        IdentityHashMap<C, Boolean> identities = new IdentityHashMap<C, Boolean>();
        Map<String, C> byId = new HashMap<String, C>();
        for (C crop : crops) {
            if (crop == null || identities.put(crop, Boolean.TRUE) != null) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "deterministic IC2 crop replay contains a null/repeated object identity");
            }
            String id = canonicalIds.canonicalId(crop);
            if (id == null || id.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "deterministic IC2 crop replay contains an empty CropCard ID");
            }
            C duplicate = byId.put(id, crop);
            if (duplicate != null && duplicate != crop) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "deterministic IC2 crop replay repeats CropCard ID " + id);
            }
            ordered.add(new CropEntry<C>(crop, id));
        }
        Collections.sort(ordered, new Comparator<CropEntry<C>>() {
            @Override
            public int compare(CropEntry<C> left, CropEntry<C> right) {
                return left.id.compareTo(right.id);
            }
        });
        return ordered;
    }

    private static <C> int[][] ratioMatrix(
            List<CropEntry<C>> ordered,
            DeterministicCropMatrixContract.Ratio<C> ratios) throws ExportFailure {
        if (ratios == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "deterministic IC2 crop replay received a null ratio function");
        }
        int count = ordered.size();
        int[][] matrix = new int[count][count];
        for (int result = 0; result < count; result++) {
            for (int input = 0; input < count; input++) {
                int ratio = ratios.calculate(
                        ordered.get(result).crop, ordered.get(input).crop);
                if (ratio < 0) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "IC2 crop replay ratio is negative for result="
                                    + ordered.get(result).id + ", input="
                                    + ordered.get(input).id + ": " + ratio);
                }
                matrix[result][input] = ratio;
            }
        }
        return matrix;
    }

    private static <C> void validateClosure(
            int cropCount,
            int pairCount,
            long simulatorCandidates,
            List<Winner<C>> winners,
            LongSet[] usageBuckets,
            LongSet[] craftBuckets,
            LongSet globalMatchKeys,
            int craftWinners,
            int usageWinnerIdentities,
            int usageOccurrences,
            int bothViewWinners,
            int craftOnlyWinners,
            int usageOnlyWinners) throws ExportFailure {
        long expectedPairsLong = ((long) cropCount * (cropCount - 1L)) / 2L;
        if (expectedPairsLong > Integer.MAX_VALUE
                || pairCount != (int) expectedPairsLong
                || pairCount <= 0
                || simulatorCandidates <= 0L
                || winners.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "deterministic IC2 crop replay is incomplete; pairs=" + pairCount
                            + "/" + expectedPairsLong + ", candidates="
                            + simulatorCandidates + ", closure=" + winners.size());
        }

        int countedUsageOccurrences = 0;
        int countedCraftWinners = 0;
        for (LongSet bucket : usageBuckets) {
            countedUsageOccurrences += bucket.size();
        }
        for (LongSet bucket : craftBuckets) {
            countedCraftWinners += bucket.size();
        }
        if (countedUsageOccurrences != usageOccurrences
                || countedCraftWinners != craftWinners
                || globalMatchKeys.size() != craftWinners
                || usageWinnerIdentities != winners.size()
                || bothViewWinners != craftWinners
                || craftOnlyWinners != 0
                || usageOnlyWinners != winners.size() - craftWinners) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "deterministic IC2 crop replay closure counters disagree; closure="
                            + winners.size() + ", craft=" + craftWinners
                            + ", global=" + globalMatchKeys.size()
                            + ", usageIdentities=" + usageWinnerIdentities
                            + ", usageOccurrences=" + usageOccurrences
                            + ", countedUsageOccurrences=" + countedUsageOccurrences);
        }

        for (int index = 0; index < winners.size(); index++) {
            Winner<C> winner = winners.get(index);
            if (winner.selectionIndex != index
                    || winner.simulatorCandidateIndex < 0L
                    || winner.points <= 0
                    || winner.totalPoints <= 0
                    || winner.inputArity != INPUT_ARITY
                    || !winner.usageMember()
                    || winner.leftInputId.compareTo(winner.rightInputId) >= 0
                    || winner.outputId.equals(winner.leftInputId)
                    || winner.outputId.equals(winner.rightInputId)) {
                throw new ExportFailure("INTERNAL_ERROR",
                        "deterministic IC2 crop replay produced an invalid winner at " + index);
            }
        }
    }

    private static <C> String fingerprint(
            List<CropEntry<C>> ordered,
            int[][] ratioMatrix,
            List<Winner<C>> winners,
            int pairCount,
            long simulatorCandidates,
            int globalMatchKeys,
            int craftWinners,
            int usageWinnerIdentities,
            int usageOccurrences,
            int bothViewWinners,
            int craftOnlyWinners,
            int usageOnlyWinners) throws ExportFailure {
        MessageDigest digest = sha256();
        digestText(digest, "mrt-ic2-crop-canonical-bucket-replay-v1\n");
        digestText(digest, "inputArity\t" + INPUT_ARITY + "\n");
        digestText(digest, "cropCount\t" + ordered.size() + "\n");
        for (CropEntry<C> crop : ordered) {
            digestText(digest, "crop\t");
            digestField(digest, crop.id);
            digestText(digest, "\n");
        }
        digestText(digest, "ratioMatrix\t" + ordered.size()
                + "\t" + ordered.size() + "\n");
        for (int result = 0; result < ordered.size(); result++) {
            for (int input = 0; input < ordered.size(); input++) {
                digestText(digest, "ratio\t");
                digestField(digest, ordered.get(result).id);
                digestText(digest, "\t");
                digestField(digest, ordered.get(input).id);
                digestText(digest, "\t" + ratioMatrix[result][input] + "\n");
            }
        }
        digestText(digest, "pairCount\t" + pairCount + "\n");
        digestText(digest, "simulatorCandidateCount\t" + simulatorCandidates + "\n");
        for (Winner<C> winner : winners) {
            digestText(digest, "winner\t");
            digestField(digest, winner.graphCanonical);
            digestText(digest, "\tleftUsage\t" + flag(winner.leftUsageMember));
            digestText(digest, "\trightUsage\t" + flag(winner.rightUsageMember));
            digestText(digest, "\tcraft\t" + flag(winner.craftMember) + "\n");
        }
        digestText(digest, "canonicalGlobalMatchKeyCount\t" + globalMatchKeys + "\n");
        digestText(digest, "bucketLocalClosureCount\t" + winners.size() + "\n");
        digestText(digest, "craftWinnerCount\t" + craftWinners + "\n");
        digestText(digest, "usageWinnerIdentityCount\t" + usageWinnerIdentities + "\n");
        digestText(digest, "usageOccurrenceCount\t" + usageOccurrences + "\n");
        digestText(digest, "bothViewWinnerCount\t" + bothViewWinners + "\n");
        digestText(digest, "craftOnlyWinnerCount\t" + craftOnlyWinners + "\n");
        digestText(digest, "usageOnlyWinnerCount\t" + usageOnlyWinners + "\n");
        return hex(digest.digest());
    }

    private static long matchKey(int outputIndex, int points, int arity)
            throws ExportFailure {
        if (outputIndex < 0 || outputIndex > MAX_PACKED_CROPS
                || points <= 0 || arity <= 0 || arity > 0xffff) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 crop replay cannot pack match key output=" + outputIndex
                            + ", points=" + points + ", arity=" + arity);
        }
        return ((long) arity << 48)
                | ((long) outputIndex << 32)
                | (points & 0xffffffffL);
    }

    private static MessageDigest sha256() throws ExportFailure {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "JVM does not provide required SHA-256 digest", error);
        }
    }

    private static int flag(boolean value) {
        return value ? 1 : 0;
    }

    private static void appendField(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append(';');
    }

    private static void digestField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digestText(digest, Integer.toString(bytes.length));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static void digestText(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }
}
