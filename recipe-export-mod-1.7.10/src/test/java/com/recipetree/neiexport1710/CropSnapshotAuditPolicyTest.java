package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CropSnapshotAuditPolicyTest {
    @Test
    public void firstPostWorkerObservationRequiresFullCapture() {
        assertTrue(CompleteCategoryAdapters.requiresFreshCropSnapshot(false, false));
    }

    @Test
    public void normalReadinessPollingReusesCompletedImmutableSnapshot() {
        assertFalse(CompleteCategoryAdapters.requiresFreshCropSnapshot(true, false));
    }

    @Test
    public void explicitIntegrityAuditReusesSnapshotAndChecksCompactBasis() {
        assertFalse(CompleteCategoryAdapters.requiresFreshCropSnapshot(true, true));
    }

    @Test
    public void repairedBreedResultArrayUsesCanonicalWinnerOrder() throws Exception {
        FixtureCrop left = new FixtureCrop();
        FixtureCrop right = new FixtureCrop();
        FixtureCrop[] retained = new FixtureCrop[]{right, left};

        CompleteCategoryAdapters.restoreCanonicalCropInputOrder(
                retained, FixtureCrop.class, left, right);

        assertSame(left, retained[0]);
        assertSame(right, retained[1]);
    }

    @Test
    public void compactAuditRequiresExactCropObjectIdentityAndOrder() throws Exception {
        Object first = new Object();
        Object second = new Object();
        CompleteCategoryAdapters.requireExactCropUniverseIdentities(
                Arrays.asList(first, second), Arrays.asList(first, second));

        try {
            CompleteCategoryAdapters.requireExactCropUniverseIdentities(
                    Arrays.asList(first, second), Arrays.asList(second, first));
            fail("reordered ALL_CROPS identities must fail closed");
        } catch (ExportFailure expected) {
            assertEquals("HANDLER_UNLOADED", expected.code);
        }

        try {
            CompleteCategoryAdapters.requireExactCropUniverseIdentities(
                    Arrays.asList(first, second), Arrays.asList(first));
            fail("resized ALL_CROPS must fail closed");
        } catch (ExportFailure expected) {
            assertEquals("HANDLER_UNLOADED", expected.code);
        }
    }

    @Test
    public void compactAuditRequiresCompleteMatrixFingerprintAndCounters() throws Exception {
        DeterministicCropMatrixContract.Snapshot baseline = matrix("matrix-a", 12L);
        CompleteCategoryAdapters.requireSameCropMatrix(
                baseline, matrix("matrix-a", 12L));

        try {
            CompleteCategoryAdapters.requireSameCropMatrix(
                    baseline, matrix("matrix-b", 12L));
            fail("matrix fingerprint drift must fail closed");
        } catch (ExportFailure expected) {
            assertEquals("HANDLER_UNLOADED", expected.code);
        }

        try {
            CompleteCategoryAdapters.requireSameCropMatrix(
                    baseline, matrix("matrix-a", 13L));
            fail("matrix candidate-count drift must fail closed");
        } catch (ExportFailure expected) {
            assertEquals("HANDLER_UNLOADED", expected.code);
        }
    }

    private static DeterministicCropMatrixContract.Snapshot matrix(
            String fingerprint, long outcomes) {
        return new DeterministicCropMatrixContract.Snapshot(
                5, 10, outcomes, 1, 3, 18, 24, fingerprint);
    }

    private static final class FixtureCrop {
    }
}
