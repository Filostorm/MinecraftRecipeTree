package com.recipetree.neiexport1710;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DeterministicCropMatrixContractTest {
    private static final class Crop {
        final String id;
        final int coordinate;

        Crop(String id, int coordinate) {
            this.id = id;
            this.coordinate = coordinate;
        }
    }

    private final Crop a = new Crop("owner:a", 0);
    private final Crop b = new Crop("owner:b", 1);
    private final Crop c = new Crop("owner:c", 2);
    private final Crop d = new Crop("owner:d", 4);

    @Test
    public void canonicalMatrixIsInvariantToSourceOrderAndObjectHashCodes()
            throws Exception {
        DeterministicCropMatrixContract.Snapshot first = audit(
                Arrays.asList(a, b, c, d));
        DeterministicCropMatrixContract.Snapshot second = audit(
                Arrays.asList(d, b, a, c));

        assertEquals(4, first.cropCount);
        assertEquals(6, first.pairCount);
        assertEquals(12L, first.outcomeCount);
        assertEquals(2, first.minimumOutcomesPerPair);
        assertEquals(2, first.maximumOutcomesPerPair);
        assertEquals(24, first.minimumTotalPoints);
        assertEquals(30, first.maximumTotalPoints);
        assertEquals(first.fingerprint, second.fingerprint);
    }

    @Test
    public void fingerprintChangesWhenPublicRatioSemanticsChange() throws Exception {
        final List<Crop> crops = Arrays.asList(a, b, c, d);
        DeterministicCropMatrixContract.Snapshot baseline = audit(crops);
        DeterministicCropMatrixContract.Snapshot changed =
                DeterministicCropMatrixContract.audit(
                        crops,
                        new DeterministicCropMatrixContract.CanonicalId<Crop>() {
                            @Override
                            public String canonicalId(Crop crop) {
                                return crop.id;
                            }
                        },
                        new DeterministicCropMatrixContract.Ratio<Crop>() {
                            @Override
                            public int calculate(Crop result, Crop input) {
                                int value = Math.max(0,
                                        5 - Math.abs(result.coordinate - input.coordinate));
                                return result == a && input == b ? value + 1 : value;
                            }
                        });

        org.junit.Assert.assertNotEquals(baseline.fingerprint, changed.fingerprint);
    }

    private DeterministicCropMatrixContract.Snapshot audit(List<Crop> crops)
            throws ExportFailure {
        return DeterministicCropMatrixContract.audit(
                crops,
                new DeterministicCropMatrixContract.CanonicalId<Crop>() {
                    @Override
                    public String canonicalId(Crop crop) {
                        return crop.id;
                    }
                },
                new DeterministicCropMatrixContract.Ratio<Crop>() {
                    @Override
                    public int calculate(Crop result, Crop input) {
                        return Math.max(0,
                                5 - Math.abs(result.coordinate - input.coordinate));
                    }
                });
    }
}
