package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DeterministicCropReplayContractTest {
    private static final int[][] RATIOS = new int[][] {
            { 1, 3, 5, 1, 2 },
            { 2, 1, 4, 2, 3 },
            { 3, 2, 1, 3, 4 },
            { 2, 2, 2, 1, 1 },
            { 1, 1, 1, 2, 1 }
    };

    private static final class Crop {
        final String id;
        final int coordinate;
        final int adversarialHash;

        Crop(String id, int coordinate, int adversarialHash) {
            this.id = id;
            this.coordinate = coordinate;
            this.adversarialHash = adversarialHash;
        }

        @Override
        public int hashCode() {
            return adversarialHash;
        }
    }

    @Test
    public void replaysExactBucketLocalClosureAndExposesCanonicalParents()
            throws Exception {
        List<Crop> crops = crops(91, -7, 91, Integer.MIN_VALUE, 0);
        DeterministicCropReplayContract.Snapshot<Crop> snapshot = replay(crops);

        assertEquals(5, snapshot.cropCount);
        assertEquals(10, snapshot.pairCount);
        assertEquals(30L, snapshot.simulatorCandidateCount);
        assertEquals(17, snapshot.canonicalGlobalMatchKeyCount);
        assertEquals(28, snapshot.bucketLocalClosureCount);
        assertEquals(17, snapshot.craftWinnerCount);
        assertEquals(28, snapshot.usageWinnerIdentityCount);
        assertEquals(46, snapshot.usageOccurrenceCount);
        assertEquals(17, snapshot.bothViewWinnerCount);
        assertEquals(0, snapshot.craftOnlyWinnerCount);
        assertEquals(11, snapshot.usageOnlyWinnerCount);
        assertEquals(3, snapshot.minimumCandidatesPerPair);
        assertEquals(3, snapshot.maximumCandidatesPerPair);
        assertEquals(18, snapshot.minimumTotalPoints);
        assertEquals(24, snapshot.maximumTotalPoints);

        DeterministicCropReplayContract.Winner<Crop> abToD =
                find(snapshot, "owner:a", "owner:b", "owner:d");
        assertNotNull(abToD);
        assertEquals(1, abToD.selectionIndex);
        assertEquals(1L, abToD.simulatorCandidateIndex);
        assertSame(crops.get(0), abToD.leftInput);
        assertSame(crops.get(1), abToD.rightInput);
        assertSame(crops.get(3), abToD.output);
        assertEquals(4, abToD.points);
        assertEquals(18, abToD.totalPoints);
        assertEquals(2, abToD.inputArity);
        assertTrue(abToD.leftUsageMember);
        assertTrue(abToD.rightUsageMember);
        assertTrue(abToD.craftMember);

        try {
            snapshot.winners.clear();
            fail("winner corpus must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void bucketLocalFirstWinnerRetainsDifferentTotalAndGraphParents()
            throws Exception {
        DeterministicCropReplayContract.Snapshot<Crop> snapshot = replay(
                crops(5, 4, 3, 2, 1));

        DeterministicCropReplayContract.Winner<Crop> first =
                find(snapshot, "owner:a", "owner:b", "owner:d");
        DeterministicCropReplayContract.Winner<Crop> second =
                find(snapshot, "owner:a", "owner:c", "owner:d");
        DeterministicCropReplayContract.Winner<Crop> suppressed =
                find(snapshot, "owner:b", "owner:c", "owner:d");

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.outputId, second.outputId);
        assertEquals(4, first.points);
        assertEquals(4, second.points);
        assertEquals(18, first.totalPoints);
        assertEquals(22, second.totalPoints);
        assertTrue(first.leftUsageMember);
        assertTrue(first.rightUsageMember);
        assertTrue(first.craftMember);
        assertFalse(second.leftUsageMember);
        assertTrue(second.rightUsageMember);
        assertFalse(second.craftMember);
        assertNull("the third graph loses independently in B usage, C usage, and D craft",
                suppressed);
    }

    @Test
    public void fingerprintAndWinnerOrderIgnoreSourceOrderAndObjectHashes()
            throws Exception {
        List<Crop> firstCrops = crops(1, 2, 3, 4, 5);
        List<Crop> secondCrops = crops(
                Integer.MAX_VALUE, 0, -1, 0x55555555, 0xaaaaaaaa);
        List<Crop> shuffled = Arrays.asList(
                secondCrops.get(4), secondCrops.get(2), secondCrops.get(0),
                secondCrops.get(3), secondCrops.get(1));

        DeterministicCropReplayContract.Snapshot<Crop> first = replay(firstCrops);
        DeterministicCropReplayContract.Snapshot<Crop> second = replay(shuffled);

        assertEquals(first.fingerprint, second.fingerprint);
        assertEquals(winnerCanonicals(first), winnerCanonicals(second));
        assertEquals(first.simulatorCandidateCount, second.simulatorCandidateCount);
        assertEquals(first.canonicalGlobalMatchKeyCount,
                second.canonicalGlobalMatchKeyCount);
        assertEquals(first.bucketLocalClosureCount, second.bucketLocalClosureCount);
        for (DeterministicCropReplayContract.Winner<Crop> winner : second.winners) {
            assertTrue(winner.leftInputId.compareTo(winner.rightInputId) < 0);
        }
    }

    @Test
    public void fingerprintBindsCompensatedNonWinnerRatioMatrixDrift()
            throws Exception {
        List<Crop> crops = crops(1, 2, 3, 4, 5);
        int[][] baseline = new int[][] {
                { 5, 0, 0, 0, 0 },
                { 0, 5, 0, 0, 0 },
                { 0, 0, 5, 0, 0 },
                { 2, 2, 2, 5, 2 },
                { 0, 0, 0, 0, 5 }
        };
        int[][] changed = copyMatrix(baseline);
        // These diagonal result=input ratios can only contribute while that result is a
        // parent, so they never form candidates. The +1/-1 column-total compensation keeps
        // every selected A-star winner's denominator unchanged while suppressed leaf-pair
        // denominators drift.
        changed[0][0] += 1;
        changed[1][1] -= 1;
        changed[2][2] -= 1;
        changed[4][4] -= 1;

        DeterministicCropReplayContract.Snapshot<Crop> first =
                replay(crops, baseline);
        DeterministicCropReplayContract.Snapshot<Crop> second =
                replay(crops, changed);

        assertEquals(6L, first.simulatorCandidateCount);
        assertEquals(1, first.canonicalGlobalMatchKeyCount);
        assertEquals(3, first.bucketLocalClosureCount);
        assertEquals(4, first.usageOccurrenceCount);
        assertEquals(winnerCanonicals(first), winnerCanonicals(second));
        assertEquals(first.simulatorCandidateCount, second.simulatorCandidateCount);
        assertEquals(first.canonicalGlobalMatchKeyCount,
                second.canonicalGlobalMatchKeyCount);
        assertEquals(first.bucketLocalClosureCount, second.bucketLocalClosureCount);
        assertNotEquals("the full canonical ratio matrix is provenance, even when the repaired "
                        + "query-bucket closure is unchanged",
                first.fingerprint, second.fingerprint);
    }

    @Test
    public void failsClosedOnNegativeRatio() throws Exception {
        final List<Crop> crops = crops(1, 2, 3, 4, 5);
        try {
            DeterministicCropReplayContract.replay(
                    crops,
                    canonicalIds(),
                    new DeterministicCropMatrixContract.Ratio<Crop>() {
                        @Override
                        public int calculate(Crop result, Crop input) {
                            return result.coordinate == 2 && input.coordinate == 4
                                    ? -1 : RATIOS[result.coordinate][input.coordinate];
                        }
                    });
            fail("negative public crop ratios must fail closed");
        } catch (ExportFailure expected) {
            assertEquals("RECIPE_SEMANTICS", expected.code);
        }
    }

    @Test
    public void failsClosedOnDuplicateCanonicalCropId() throws Exception {
        final List<Crop> crops = new ArrayList<Crop>(crops(1, 2, 3, 4, 5));
        crops.set(4, new Crop("owner:d", 4, 99));
        try {
            replay(crops);
            fail("duplicate canonical CropCard IDs must fail closed");
        } catch (ExportFailure expected) {
            assertEquals("HANDLER_DUPLICATE", expected.code);
        }
    }

    @Test
    public void failsClosedBeforeCubicReplayAboveResourceCap() throws Exception {
        final List<Crop> oversized = new ArrayList<Crop>(
                DeterministicCropReplayContract.MAX_REPLAY_CROPS + 1);
        for (int index = 0;
             index <= DeterministicCropReplayContract.MAX_REPLAY_CROPS;
             index++) {
            oversized.add(new Crop("owner:oversized-" + index, index, index));
        }
        try {
            DeterministicCropReplayContract.replay(
                    oversized,
                    canonicalIds(),
                    new DeterministicCropMatrixContract.Ratio<Crop>() {
                        @Override
                        public int calculate(Crop result, Crop input) {
                            fail("resource cap must reject before evaluating ratios");
                            return 0;
                        }
                    });
            fail("oversized crop universes must fail before O(n^3) replay");
        } catch (ExportFailure expected) {
            assertEquals("RECIPE_SEMANTICS", expected.code);
        }
    }

    private static List<Crop> crops(int... hashes) {
        assertEquals(5, hashes.length);
        return Collections.unmodifiableList(Arrays.asList(
                new Crop("owner:a", 0, hashes[0]),
                new Crop("owner:b", 1, hashes[1]),
                new Crop("owner:c", 2, hashes[2]),
                new Crop("owner:d", 3, hashes[3]),
                new Crop("owner:e", 4, hashes[4])));
    }

    private static DeterministicCropReplayContract.Snapshot<Crop> replay(
            List<Crop> crops) throws ExportFailure {
        return replay(crops, RATIOS);
    }

    private static DeterministicCropReplayContract.Snapshot<Crop> replay(
            List<Crop> crops, final int[][] ratios) throws ExportFailure {
        return DeterministicCropReplayContract.replay(
                crops,
                canonicalIds(),
                new DeterministicCropMatrixContract.Ratio<Crop>() {
                    @Override
                    public int calculate(Crop result, Crop input) {
                        return ratios[result.coordinate][input.coordinate];
                    }
                });
    }

    private static DeterministicCropMatrixContract.CanonicalId<Crop> canonicalIds() {
        return new DeterministicCropMatrixContract.CanonicalId<Crop>() {
            @Override
            public String canonicalId(Crop crop) {
                return crop.id;
            }
        };
    }

    private static DeterministicCropReplayContract.Winner<Crop> find(
            DeterministicCropReplayContract.Snapshot<Crop> snapshot,
            String left, String right, String output) {
        DeterministicCropReplayContract.Winner<Crop> match = null;
        for (DeterministicCropReplayContract.Winner<Crop> winner : snapshot.winners) {
            if (left.equals(winner.leftInputId)
                    && right.equals(winner.rightInputId)
                    && output.equals(winner.outputId)) {
                if (match != null) {
                    fail("duplicate replay graph winner for " + left + "/" + right
                            + " -> " + output);
                }
                match = winner;
            }
        }
        return match;
    }

    private static List<String> winnerCanonicals(
            DeterministicCropReplayContract.Snapshot<Crop> snapshot) {
        List<String> values = new ArrayList<String>(snapshot.winners.size());
        for (DeterministicCropReplayContract.Winner<Crop> winner : snapshot.winners) {
            values.add(winner.graphCanonical
                    + "|" + winner.leftUsageMember
                    + "|" + winner.rightUsageMember
                    + "|" + winner.craftMember);
        }
        return values;
    }

    private static int[][] copyMatrix(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index].clone();
        }
        return copy;
    }
}
