package com.recipetree.neiexport1710;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CropPresentationDiscoveryGateTest {
    private static final String FINGERPRINT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void planningPredicateSkipsThePromotedCropAdapter() {
        assertFalse(CompleteCategoryAdapters.requiresCropPresentationDiscovery(
                CompleteCategoryAdapters.Adapter.IC2_CROP_BREEDING));
        assertFalse(CompleteCategoryAdapters.requiresCropPresentationDiscovery(
                CompleteCategoryAdapters.Adapter.STANDARD));
        assertFalse(CompleteCategoryAdapters.requiresCropPresentationDiscovery(null));
    }

    @Test
    public void promotedReleasePinsTheReviewedRuntimeCorpus() throws Exception {
        CropPresentationDiscoveryGate.Observation observed =
                new CropPresentationDiscoveryGate.Observation(
                        290789, 290789L, 288727L, 288727, 2062,
                        288727, 2062, 0, 0, 1, 1,
                        581578L, 579493L, 579493, 2085,
                        579493, 2085, 0, 0, 1, 1,
                        "2bc4ba240ab68b0fd67c490521af6c17a0ac2540ae0485548999463b6ae937ea");

        assertEquals(observed.countVector(),
                CropPresentationDiscoveryGate.EXPECTED_COUNT_VECTOR);
        assertEquals(observed.fingerprint,
                CropPresentationDiscoveryGate.EXPECTED_SHA256);
        CropPresentationDiscoveryGate.requirePromoted(observed);
    }

    @Test
    public void unpromotedGateReportsDeterministicPromotionInventoryAndAborts()
            throws Exception {
        CropPresentationDiscoveryGate.Observation observed = observation();
        try {
            CropPresentationDiscoveryGate.requirePromotionForTest(
                    observed, CropPresentationDiscoveryGate.UNPROMOTED,
                    CropPresentationDiscoveryGate.UNPROMOTED);
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_UNLOADED", failure.code);
            assertTrue(failure.getMessage().contains(observed.countVector()));
            assertTrue(failure.getMessage().contains(FINGERPRINT));
            assertTrue(failure.getMessage().contains(
                    "presentationDigestDomain=ic2-crop-nei-presentation-corpus-v4"));
            assertTrue(failure.getMessage().contains(
                    "aborted before category metadata or rendering"));
            return;
        }
        throw new AssertionError("Expected unpromoted crop-presentation failure");
    }

    @Test
    public void exactPromotedCountsAndFingerprintAreBothRequired() throws Exception {
        CropPresentationDiscoveryGate.Observation observed = observation();
        CropPresentationDiscoveryGate.requirePromotionForTest(
                observed, observed.countVector(), FINGERPRINT);

        assertFailure("HANDLER_UNLOADED", new CheckedCall() {
            @Override
            public void run() throws Exception {
                CropPresentationDiscoveryGate.requirePromotionForTest(
                        observation(), observation().countVector(),
                        "1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            }
        });
        assertFailure("HANDLER_AMBIGUOUS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                CropPresentationDiscoveryGate.requirePromotionForTest(
                        observation(), CropPresentationDiscoveryGate.UNPROMOTED,
                        FINGERPRINT);
            }
        });
    }

    @Test
    public void inconsistentCountVectorFailsBeforePromotionComparison()
            throws Exception {
        final CropPresentationDiscoveryGate.Observation inconsistent =
                new CropPresentationDiscoveryGate.Observation(
                        4, 4L, 4L, 4, 0,
                        3, 0, 0, 0, 1, 1,
                        8L, 8L, 8, 0,
                        8, 0, 0, 0, 1, 1,
                        FINGERPRINT);
        assertFailure("HANDLER_AMBIGUOUS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                CropPresentationDiscoveryGate.requirePromotionForTest(
                        inconsistent, CropPresentationDiscoveryGate.UNPROMOTED,
                        CropPresentationDiscoveryGate.UNPROMOTED);
            }
        });
    }

    private static CropPresentationDiscoveryGate.Observation observation() {
        return new CropPresentationDiscoveryGate.Observation(
                4, 7L, 3L, 2, 2,
                1, 2, 1, 0, 1, 3,
                10L, 7L, 5, 3,
                4, 2, 2, 0, 1, 2,
                FINGERPRINT);
    }

    private static void assertFailure(String code, CheckedCall call) throws Exception {
        try {
            call.run();
        } catch (ExportFailure failure) {
            assertEquals(code, failure.code);
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private interface CheckedCall {
        void run() throws Exception;
    }
}
