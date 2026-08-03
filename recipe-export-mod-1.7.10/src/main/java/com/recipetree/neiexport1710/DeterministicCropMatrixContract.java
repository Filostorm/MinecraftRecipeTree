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
 * Canonical two-parent IC2 crop-breeding matrix independent of JVM object hashes.
 *
 * <p>The pinned IC2 Crop Plugin 1.3.1 cache is not a reproducible semantic source:
 * {@code BreedTask} sorts and de-duplicates with runtime object hash codes, while
 * {@code BreedResult.matches} conflates graph-distinct input pairs. This contract
 * evaluates the plugin's public ratio function over a canonical crop order without
 * constructing or retaining the potentially large outcome corpus.</p>
 */
final class DeterministicCropMatrixContract {
    interface CanonicalId<C> {
        String canonicalId(C crop) throws ExportFailure;
    }

    interface Ratio<C> {
        int calculate(C result, C input) throws ExportFailure;
    }

    static final class Snapshot {
        final int cropCount;
        final int pairCount;
        final long outcomeCount;
        final int minimumOutcomesPerPair;
        final int maximumOutcomesPerPair;
        final int minimumTotalPoints;
        final int maximumTotalPoints;
        final String fingerprint;

        Snapshot(int cropCount,
                 int pairCount,
                 long outcomeCount,
                 int minimumOutcomesPerPair,
                 int maximumOutcomesPerPair,
                 int minimumTotalPoints,
                 int maximumTotalPoints,
                 String fingerprint) {
            this.cropCount = cropCount;
            this.pairCount = pairCount;
            this.outcomeCount = outcomeCount;
            this.minimumOutcomesPerPair = minimumOutcomesPerPair;
            this.maximumOutcomesPerPair = maximumOutcomesPerPair;
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

    private DeterministicCropMatrixContract() {
    }

    static <C> Snapshot audit(List<C> crops,
                              CanonicalId<C> canonicalIds,
                              Ratio<C> ratios) throws ExportFailure {
        if (crops == null || canonicalIds == null || ratios == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "deterministic IC2 crop matrix received a null dependency");
        }
        if (crops.size() < 2) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "deterministic IC2 crop matrix requires at least two CropCards");
        }

        List<CropEntry<C>> ordered = new ArrayList<CropEntry<C>>(crops.size());
        IdentityHashMap<C, Boolean> identities = new IdentityHashMap<C, Boolean>();
        Map<String, C> byId = new HashMap<String, C>();
        for (C crop : crops) {
            if (crop == null || identities.put(crop, Boolean.TRUE) != null) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "deterministic IC2 crop matrix contains a null/repeated object identity");
            }
            String id = canonicalIds.canonicalId(crop);
            if (id == null || id.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "deterministic IC2 crop matrix contains an empty canonical CropCard ID");
            }
            C duplicate = byId.put(id, crop);
            if (duplicate != null && duplicate != crop) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "deterministic IC2 crop matrix repeats canonical CropCard ID " + id);
            }
            ordered.add(new CropEntry<C>(crop, id));
        }
        Collections.sort(ordered, new Comparator<CropEntry<C>>() {
            @Override
            public int compare(CropEntry<C> left, CropEntry<C> right) {
                return left.id.compareTo(right.id);
            }
        });

        final int count = ordered.size();
        int[][] ratioMatrix = new int[count][count];
        for (int resultIndex = 0; resultIndex < count; resultIndex++) {
            for (int inputIndex = 0; inputIndex < count; inputIndex++) {
                int ratio = ratios.calculate(
                        ordered.get(resultIndex).crop,
                        ordered.get(inputIndex).crop);
                if (ratio < 0) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "IC2 crop ratio is negative for result="
                                    + ordered.get(resultIndex).id + ", input="
                                    + ordered.get(inputIndex).id + ": " + ratio);
                }
                ratioMatrix[resultIndex][inputIndex] = ratio;
            }
        }

        MessageDigest digest = sha256();
        digestText(digest, "mrt-ic2-crop-deterministic-two-parent-matrix-v1\n");
        digestText(digest, "cropCount\t" + count + "\n");
        for (int index = 0; index < count; index++) {
            digestText(digest, "crop\t");
            digestField(digest, ordered.get(index).id);
            digestText(digest, "\n");
        }
        digestText(digest, "ratioMatrix\t" + count + "\t" + count + "\n");
        for (int resultIndex = 0; resultIndex < count; resultIndex++) {
            for (int inputIndex = 0; inputIndex < count; inputIndex++) {
                digestText(digest, "ratio\t");
                digestField(digest, ordered.get(resultIndex).id);
                digestText(digest, "\t");
                digestField(digest, ordered.get(inputIndex).id);
                digestText(digest, "\t" + ratioMatrix[resultIndex][inputIndex] + "\n");
            }
        }

        int pairCount = 0;
        long outcomeCount = 0L;
        int minimumOutcomes = Integer.MAX_VALUE;
        int maximumOutcomes = Integer.MIN_VALUE;
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
                            "IC2 crop pair has an invalid total-points denominator: left="
                                    + ordered.get(left).id + ", right="
                                    + ordered.get(right).id + ", total=" + totalLong);
                }
                int total = (int) totalLong;
                int pairOutcomes = 0;
                digestText(digest, "pair\t");
                digestField(digest, ordered.get(left).id);
                digestText(digest, "\t");
                digestField(digest, ordered.get(right).id);
                digestText(digest, "\ttotal\t" + total + "\n");
                for (int result = 0; result < count; result++) {
                    int points = ratioMatrix[result][left] + ratioMatrix[result][right];
                    if (result == left || result == right || points <= 0) {
                        continue;
                    }
                    pairOutcomes++;
                    digestText(digest, "outcome\t");
                    digestField(digest, ordered.get(result).id);
                    digestText(digest, "\tpoints\t" + points + "\n");
                }
                digestText(digest, "pairOutcomes\t" + pairOutcomes + "\n");
                pairCount++;
                outcomeCount += pairOutcomes;
                minimumOutcomes = Math.min(minimumOutcomes, pairOutcomes);
                maximumOutcomes = Math.max(maximumOutcomes, pairOutcomes);
                minimumTotal = Math.min(minimumTotal, total);
                maximumTotal = Math.max(maximumTotal, total);
            }
        }
        int expectedPairs = count * (count - 1) / 2;
        if (pairCount != expectedPairs || pairCount <= 0 || outcomeCount <= 0L) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "deterministic IC2 crop matrix is incomplete; pairs=" + pairCount
                            + "/" + expectedPairs + ", outcomes=" + outcomeCount);
        }
        return new Snapshot(
                count,
                pairCount,
                outcomeCount,
                minimumOutcomes,
                maximumOutcomes,
                minimumTotal,
                maximumTotal,
                hex(digest.digest()));
    }

    private static MessageDigest sha256() throws ExportFailure {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "JVM does not provide required SHA-256 digest", error);
        }
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
