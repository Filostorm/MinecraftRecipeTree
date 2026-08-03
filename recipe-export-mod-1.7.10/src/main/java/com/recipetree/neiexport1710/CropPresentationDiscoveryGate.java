package com.recipetree.neiexport1710;

/**
 * Fail-closed promotion gate for the exact GTNH 2.8.4 IC2 crop NEI presentation corpus.
 *
 * <p>A discovery release deliberately leaves both promotion constants at
 * {@link #UNPROMOTED}. It reports the complete deterministic observation and aborts before
 * category metadata or rendering. A promoted release must replace both constants with the
 * reviewed count vector and SHA-256; promoting only one is an invalid contract configuration.
 */
final class CropPresentationDiscoveryGate {
    static final String CONTRACT =
            "gtnh-2.8.4-ic2-crop-nei-presentation-promotion-v1";
    static final String UNPROMOTED = "<unpromoted>";

    static final String EXPECTED_COUNT_VECTOR =
            "pages=290789;renderedAlternatives=290789;renderedGraphCropAlternatives=288727;"
                    + "cropPreservingPages=288727;lossyPermutationPages=2062;"
                    + "directPages=288727;wildcardItemListPages=2062;"
                    + "wildcardEmptyFallbackPages=0;wildcardFireFallbackPages=0;"
                    + "minimumAlternativesPerPage=1;maximumAlternativesPerPage=1;"
                    + "renderedInputAlternatives=581578;"
                    + "renderedGraphCropInputAlternatives=579493;"
                    + "cropPreservingInputSlots=579493;lossyInputSlots=2085;"
                    + "directInputSlots=579493;wildcardItemListInputSlots=2085;"
                    + "wildcardEmptyFallbackInputSlots=0;wildcardFireFallbackInputSlots=0;"
                    + "minimumInputAlternativesPerSlot=1;maximumInputAlternativesPerSlot=1";
    static final String EXPECTED_SHA256 =
            "2bc4ba240ab68b0fd67c490521af6c17a0ac2540ae0485548999463b6ae937ea";

    static final class Observation {
        final int pages;
        final long renderedAlternatives;
        final long renderedGraphCropAlternatives;
        final int cropPreservingPages;
        final int lossyPermutationPages;
        final int directPages;
        final int wildcardItemListPages;
        final int wildcardEmptyFallbackPages;
        final int wildcardFireFallbackPages;
        final int minimumAlternativesPerPage;
        final int maximumAlternativesPerPage;
        final long renderedInputAlternatives;
        final long renderedGraphCropInputAlternatives;
        final int cropPreservingInputSlots;
        final int lossyInputSlots;
        final int directInputSlots;
        final int wildcardItemListInputSlots;
        final int wildcardEmptyFallbackInputSlots;
        final int wildcardFireFallbackInputSlots;
        final int minimumInputAlternativesPerSlot;
        final int maximumInputAlternativesPerSlot;
        final String fingerprint;

        Observation(
                int pages,
                long renderedAlternatives,
                long renderedGraphCropAlternatives,
                int cropPreservingPages,
                int lossyPermutationPages,
                int directPages,
                int wildcardItemListPages,
                int wildcardEmptyFallbackPages,
                int wildcardFireFallbackPages,
                int minimumAlternativesPerPage,
                int maximumAlternativesPerPage,
                long renderedInputAlternatives,
                long renderedGraphCropInputAlternatives,
                int cropPreservingInputSlots,
                int lossyInputSlots,
                int directInputSlots,
                int wildcardItemListInputSlots,
                int wildcardEmptyFallbackInputSlots,
                int wildcardFireFallbackInputSlots,
                int minimumInputAlternativesPerSlot,
                int maximumInputAlternativesPerSlot,
                String fingerprint) {
            this.pages = pages;
            this.renderedAlternatives = renderedAlternatives;
            this.renderedGraphCropAlternatives = renderedGraphCropAlternatives;
            this.cropPreservingPages = cropPreservingPages;
            this.lossyPermutationPages = lossyPermutationPages;
            this.directPages = directPages;
            this.wildcardItemListPages = wildcardItemListPages;
            this.wildcardEmptyFallbackPages = wildcardEmptyFallbackPages;
            this.wildcardFireFallbackPages = wildcardFireFallbackPages;
            this.minimumAlternativesPerPage = minimumAlternativesPerPage;
            this.maximumAlternativesPerPage = maximumAlternativesPerPage;
            this.renderedInputAlternatives = renderedInputAlternatives;
            this.renderedGraphCropInputAlternatives =
                    renderedGraphCropInputAlternatives;
            this.cropPreservingInputSlots = cropPreservingInputSlots;
            this.lossyInputSlots = lossyInputSlots;
            this.directInputSlots = directInputSlots;
            this.wildcardItemListInputSlots = wildcardItemListInputSlots;
            this.wildcardEmptyFallbackInputSlots =
                    wildcardEmptyFallbackInputSlots;
            this.wildcardFireFallbackInputSlots =
                    wildcardFireFallbackInputSlots;
            this.minimumInputAlternativesPerSlot =
                    minimumInputAlternativesPerSlot;
            this.maximumInputAlternativesPerSlot =
                    maximumInputAlternativesPerSlot;
            this.fingerprint = fingerprint;
        }

        String countVector() {
            return "pages=" + pages
                    + ";renderedAlternatives=" + renderedAlternatives
                    + ";renderedGraphCropAlternatives="
                    + renderedGraphCropAlternatives
                    + ";cropPreservingPages=" + cropPreservingPages
                    + ";lossyPermutationPages=" + lossyPermutationPages
                    + ";directPages=" + directPages
                    + ";wildcardItemListPages=" + wildcardItemListPages
                    + ";wildcardEmptyFallbackPages=" + wildcardEmptyFallbackPages
                    + ";wildcardFireFallbackPages=" + wildcardFireFallbackPages
                    + ";minimumAlternativesPerPage=" + minimumAlternativesPerPage
                    + ";maximumAlternativesPerPage=" + maximumAlternativesPerPage
                    + ";renderedInputAlternatives=" + renderedInputAlternatives
                    + ";renderedGraphCropInputAlternatives="
                    + renderedGraphCropInputAlternatives
                    + ";cropPreservingInputSlots=" + cropPreservingInputSlots
                    + ";lossyInputSlots=" + lossyInputSlots
                    + ";directInputSlots=" + directInputSlots
                    + ";wildcardItemListInputSlots=" + wildcardItemListInputSlots
                    + ";wildcardEmptyFallbackInputSlots="
                    + wildcardEmptyFallbackInputSlots
                    + ";wildcardFireFallbackInputSlots="
                    + wildcardFireFallbackInputSlots
                    + ";minimumInputAlternativesPerSlot="
                    + minimumInputAlternativesPerSlot
                    + ";maximumInputAlternativesPerSlot="
                    + maximumInputAlternativesPerSlot;
        }
    }

    private CropPresentationDiscoveryGate() {
    }

    static boolean requiresDiscovery(CompleteCategoryAdapters.Adapter adapter) {
        return adapter == CompleteCategoryAdapters.Adapter.IC2_CROP_BREEDING
                && !hasCompletePromotionConstants(
                EXPECTED_COUNT_VECTOR, EXPECTED_SHA256);
    }

    static void requirePromoted(Observation observed) throws ExportFailure {
        requirePromotion(observed, EXPECTED_COUNT_VECTOR, EXPECTED_SHA256);
    }

    /** Pure package-private seam for promotion-gate tests. */
    static void requirePromotionForTest(
            Observation observed, String expectedCountVector, String expectedSha256)
            throws ExportFailure {
        requirePromotion(observed, expectedCountVector, expectedSha256);
    }

    private static void requirePromotion(
            Observation observed, String expectedCountVector, String expectedSha256)
            throws ExportFailure {
        validateObservation(observed);
        boolean countsUnpromoted = UNPROMOTED.equals(expectedCountVector);
        boolean fingerprintUnpromoted = UNPROMOTED.equals(expectedSha256);
        if (countsUnpromoted != fingerprintUnpromoted) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CONTRACT + " has a partially promoted contract configuration; both the "
                            + "exact count vector and SHA-256 must be promoted together");
        }
        String observedCounts = observed.countVector();
        if (countsUnpromoted) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    CONTRACT + " is intentionally unpromoted; observedCountVector="
                            + observedCounts + "; observedSha256=" + observed.fingerprint
                            + "; presentationDigestDomain="
                            + CropGraphSemanticContract.PRESENTATION_CORPUS_DOMAIN
                            + "; export was intentionally aborted before category metadata or "
                            + "rendering. Promote only the reviewed exact count vector and "
                            + "presentation fingerprint together.");
        }
        if (expectedCountVector == null || expectedCountVector.trim().isEmpty()
                || !isLowerHexSha256(expectedSha256)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CONTRACT + " has malformed promoted constants; expectedCountVector="
                            + String.valueOf(expectedCountVector) + "; expectedSha256="
                            + String.valueOf(expectedSha256));
        }
        if (!expectedCountVector.equals(observedCounts)
                || !expectedSha256.equals(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    CONTRACT + " drifted from its promoted corpus; expectedCountVector="
                            + expectedCountVector + "; observedCountVector=" + observedCounts
                            + "; expectedSha256=" + expectedSha256
                            + "; observedSha256=" + observed.fingerprint
                            + "; presentationDigestDomain="
                            + CropGraphSemanticContract.PRESENTATION_CORPUS_DOMAIN);
        }
    }

    private static void validateObservation(Observation observed) throws ExportFailure {
        if (observed == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CONTRACT + " received a null presentation observation");
        }
        long branchPages = (long) observed.directPages
                + observed.wildcardItemListPages
                + observed.wildcardEmptyFallbackPages
                + observed.wildcardFireFallbackPages;
        long dispositionPages = (long) observed.cropPreservingPages
                + observed.lossyPermutationPages;
        long expectedInputSlots = (long) observed.pages * 2L;
        long inputBranchSlots = (long) observed.directInputSlots
                + observed.wildcardItemListInputSlots
                + observed.wildcardEmptyFallbackInputSlots
                + observed.wildcardFireFallbackInputSlots;
        long inputDispositionSlots = (long) observed.cropPreservingInputSlots
                + observed.lossyInputSlots;
        if (observed.pages <= 0
                || observed.cropPreservingPages < 0
                || observed.lossyPermutationPages < 0
                || observed.directPages < 0
                || observed.wildcardItemListPages < 0
                || observed.wildcardEmptyFallbackPages < 0
                || observed.wildcardFireFallbackPages < 0
                || observed.cropPreservingInputSlots < 0
                || observed.lossyInputSlots < 0
                || observed.directInputSlots < 0
                || observed.wildcardItemListInputSlots < 0
                || observed.wildcardEmptyFallbackInputSlots < 0
                || observed.wildcardFireFallbackInputSlots < 0
                || observed.renderedAlternatives < observed.pages
                || observed.renderedGraphCropAlternatives < 0L
                || observed.renderedGraphCropAlternatives
                > observed.renderedAlternatives
                || dispositionPages != observed.pages
                || branchPages != observed.pages
                || observed.renderedInputAlternatives < expectedInputSlots
                || observed.renderedGraphCropInputAlternatives < 0L
                || observed.renderedGraphCropInputAlternatives
                > observed.renderedInputAlternatives
                || inputDispositionSlots != expectedInputSlots
                || inputBranchSlots != expectedInputSlots
                || observed.minimumAlternativesPerPage <= 0
                || observed.maximumAlternativesPerPage
                < observed.minimumAlternativesPerPage
                || observed.renderedAlternatives
                < (long) observed.pages * observed.minimumAlternativesPerPage
                || observed.renderedAlternatives
                > (long) observed.pages * observed.maximumAlternativesPerPage
                || observed.minimumInputAlternativesPerSlot <= 0
                || observed.maximumInputAlternativesPerSlot
                < observed.minimumInputAlternativesPerSlot
                || observed.renderedInputAlternatives
                < expectedInputSlots * observed.minimumInputAlternativesPerSlot
                || observed.renderedInputAlternatives
                > expectedInputSlots * observed.maximumInputAlternativesPerSlot
                || !isLowerHexSha256(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CONTRACT + " received an internally inconsistent observation; "
                            + "countVector=" + observed.countVector()
                            + "; fingerprint=" + String.valueOf(observed.fingerprint));
        }
    }

    private static boolean hasCompletePromotionConstants(
            String expectedCountVector, String expectedSha256) {
        return !UNPROMOTED.equals(expectedCountVector)
                && expectedCountVector != null
                && !expectedCountVector.trim().isEmpty()
                && isLowerHexSha256(expectedSha256);
    }

    private static boolean isLowerHexSha256(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
