package com.recipetree.neiexport1710;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CropCacheViewContractTest {
    private static final class FixtureCrop {
        final String id;

        FixtureCrop(String id) {
            this.id = id;
        }
    }

    private static final class FixtureResult {
        final FixtureCrop output;
        final int points;
        final List<FixtureCrop> inputs;
        final String canonical;

        FixtureResult(FixtureCrop output, int points,
                      String canonical, FixtureCrop first, FixtureCrop second) {
            this.output = output;
            this.points = points;
            this.inputs = Collections.unmodifiableList(
                    Arrays.asList(first, second));
            this.canonical = canonical;
        }
    }

    private static final class FixtureRecord implements CropCacheViewContract.Record {
        final FixtureResult result;

        FixtureRecord(FixtureResult result) {
            this.result = result;
        }

        @Override
        public Object rawResult() {
            return result;
        }

        @Override
        public String semanticCanonical() {
            return result.canonical;
        }

        @Override
        public String diagnosticId() {
            return "fixture:" + result.canonical;
        }

        @Override
        public Object resultCrop() {
            return result.output;
        }

        @Override
        public String resultCropId() {
            return result.output.id;
        }

        @Override
        public List<Object> inputCrops() {
            return new ArrayList<Object>(result.inputs);
        }

        @Override
        public List<String> inputCropIds() {
            List<String> ids = new ArrayList<String>(2);
            for (FixtureCrop crop : result.inputs) {
                ids.add(crop.id);
            }
            return ids;
        }
    }

    private static final CropCacheViewContract.Capture<FixtureRecord> CAPTURE =
            new CropCacheViewContract.Capture<FixtureRecord>() {
                @Override
                public FixtureRecord capture(Object rawResult) {
                    return new FixtureRecord((FixtureResult) rawResult);
                }
            };

    /** Mirrors the shipped broken matches(): graph inputs and total are ignored. */
    private static final CropCacheViewContract.Matcher MATCHER =
            new CropCacheViewContract.Matcher() {
                @Override
                public boolean matches(Object left, Object right) {
                    FixtureResult first = (FixtureResult) left;
                    FixtureResult second = (FixtureResult) right;
                    return first.output == second.output
                            && first.points == second.points
                            && first.inputs.size() == second.inputs.size();
                }
            };

    private FixtureCrop a;
    private FixtureCrop b;
    private FixtureCrop c;
    private FixtureCrop d;
    private FixtureCrop e;
    private FixtureCrop f;
    private FixtureCrop output;
    private List<FixtureCrop> allCrops;
    private IdentityHashMap<Object, String> cropIds;

    @Before
    public void setUp() {
        a = new FixtureCrop("A");
        b = new FixtureCrop("B");
        c = new FixtureCrop("C");
        d = new FixtureCrop("D");
        e = new FixtureCrop("E");
        f = new FixtureCrop("F");
        output = new FixtureCrop("Output");
        allCrops = Arrays.asList(a, b, c, d, e, f, output);
        cropIds = new IdentityHashMap<Object, String>();
        for (FixtureCrop crop : allCrops) {
            cropIds.put(crop, crop.id);
        }
    }

    @Test
    public void authoritativeUnionRetainsSingleAndAllInputUsageOnlyResults()
            throws Exception {
        FixtureResult craftTen = result(10, "graph-craft-10", a, b);
        FixtureResult usageOnlySingle = result(10, "graph-usage-only-single", a, c);
        FixtureResult craftTwenty = result(20, "graph-craft-20", d, e);
        FixtureResult usageOnlyAll = result(20, "graph-usage-only-all", c, f);
        HashMap<FixtureCrop, List<FixtureResult>> craft = emptyView(false);
        HashMap<FixtureCrop, List<FixtureResult>> usage = emptyView(false);

        add(craft, output, craftTen, craftTwenty);
        add(usage, a, craftTen);
        add(usage, b, craftTen);
        // The earlier matching representative suppresses this object under A,
        // but the distinct C bucket retains it.
        add(usage, c, usageOnlySingle, usageOnlyAll);
        add(usage, d, craftTwenty);
        add(usage, e, craftTwenty);
        // Neither C nor F contains the points=20 craft representative, so this
        // graph-distinct object survives under both of its usage inputs.
        add(usage, f, usageOnlyAll);

        CropCacheViewContract.Snapshot<FixtureRecord> snapshot = audit(craft, usage);

        assertEquals(4, snapshot.records.size());
        assertEquals(2, snapshot.craftResultIdentities);
        assertEquals(4, snapshot.usageResultIdentities);
        assertEquals(2, snapshot.bothViewResultIdentities);
        assertEquals(0, snapshot.craftOnlyResultIdentities);
        assertEquals(2, snapshot.usageOnlyResultIdentities);
        assertEquals(2, snapshot.craftOccurrences);
        assertEquals(7, snapshot.usageOccurrences);
        assertEquals(1, snapshot.usageOnlySingleInputIdentities);
        assertEquals(1, snapshot.usageOnlyAllInputIdentities);
        assertEquals(2, snapshot.conflatedCraftRepresentatives);
        assertEquals(Arrays.asList(
                        "graph-craft-10", "graph-craft-20",
                        "graph-usage-only-all", "graph-usage-only-single"),
                canonicals(snapshot.records));
        assertTrue(snapshot.membershipCanonical.contains("craftKeys\t7\n"));
        assertTrue(snapshot.membershipCanonical.contains("usageKeys\t7\n"));
        assertTrue(snapshot.membershipCanonical.contains(
                "usageOnlyResultIdentities\t2\n"));
        assertTrue(snapshot.membershipCanonical.contains(
                "representativeLinks\t4\n"));
    }

    @Test
    public void fingerprintIsInvariantToHashMapAndNonConflatedListPermutation()
            throws Exception {
        FixtureResult craftTen = result(10, "graph-craft-10", a, b);
        FixtureResult usageOnlySingle = result(10, "graph-usage-only-single", a, c);
        FixtureResult craftTwenty = result(20, "graph-craft-20", d, e);
        FixtureResult usageOnlyAll = result(20, "graph-usage-only-all", c, f);

        HashMap<FixtureCrop, List<FixtureResult>> firstCraft = emptyView(false);
        HashMap<FixtureCrop, List<FixtureResult>> firstUsage = emptyView(false);
        populateValidViews(firstCraft, firstUsage,
                craftTen, usageOnlySingle, craftTwenty, usageOnlyAll, false);

        HashMap<FixtureCrop, List<FixtureResult>> secondCraft = emptyView(true);
        HashMap<FixtureCrop, List<FixtureResult>> secondUsage = emptyView(true);
        populateValidViews(secondCraft, secondUsage,
                craftTen, usageOnlySingle, craftTwenty, usageOnlyAll, true);

        assertEquals(audit(firstCraft, firstUsage).membershipCanonical,
                audit(secondCraft, secondUsage).membershipCanonical);
    }

    @Test
    public void rejectsCraftRepresentativeMissingOneUsageInput() throws Exception {
        final FixtureResult craftResult = result(10, "craft", a, b);
        final HashMap<FixtureCrop, List<FixtureResult>> craft = emptyView(false);
        final HashMap<FixtureCrop, List<FixtureResult>> usage = emptyView(false);
        add(craft, output, craftResult);
        add(usage, a, craftResult);

        assertAuditFailure("RECIPE_SEMANTICS", craft, usage);
    }

    @Test
    public void rejectsUsageOnlyResultWithoutExactlyOneCraftRepresentative()
            throws Exception {
        final FixtureResult orphan = result(99, "orphan", a, b);
        final HashMap<FixtureCrop, List<FixtureResult>> craft = emptyView(false);
        final HashMap<FixtureCrop, List<FixtureResult>> usage = emptyView(false);
        add(usage, a, orphan);

        assertAuditFailure("RECIPE_SEMANTICS", craft, usage);
    }

    @Test
    public void rejectsUsageMembershipUnderNonInputCrop() throws Exception {
        final FixtureResult craftResult = result(10, "craft", a, b);
        final HashMap<FixtureCrop, List<FixtureResult>> craft = emptyView(false);
        final HashMap<FixtureCrop, List<FixtureResult>> usage = emptyView(false);
        add(craft, output, craftResult);
        add(usage, a, craftResult);
        add(usage, b, craftResult);
        add(usage, c, craftResult);

        assertAuditFailure("RECIPE_SEMANTICS", craft, usage);
    }

    @Test
    public void rejectsTwoLocallyConflatedResultsInOneBucket() throws Exception {
        final FixtureResult first = result(10, "first", a, b);
        final FixtureResult second = result(10, "second", c, d);
        final HashMap<FixtureCrop, List<FixtureResult>> craft = emptyView(false);
        final HashMap<FixtureCrop, List<FixtureResult>> usage = emptyView(false);
        add(craft, output, first, second);
        add(usage, a, first);
        add(usage, b, first);
        add(usage, c, second);
        add(usage, d, second);

        assertAuditFailure("HANDLER_DUPLICATE", craft, usage);
    }

    @Test
    public void rejectsDistinctObjectIdentitiesWithDuplicateFullGraphCanonical()
            throws Exception {
        final FixtureResult first = result(10, "same-graph", a, b);
        final FixtureResult second = result(20, "same-graph", c, d);
        final HashMap<FixtureCrop, List<FixtureResult>> craft = emptyView(false);
        final HashMap<FixtureCrop, List<FixtureResult>> usage = emptyView(false);
        add(craft, output, first, second);
        add(usage, a, first);
        add(usage, b, first);
        add(usage, c, second);
        add(usage, d, second);

        assertAuditFailure("HANDLER_DUPLICATE", craft, usage);
    }

    private void populateValidViews(
            HashMap<FixtureCrop, List<FixtureResult>> craft,
            HashMap<FixtureCrop, List<FixtureResult>> usage,
            FixtureResult craftTen,
            FixtureResult usageOnlySingle,
            FixtureResult craftTwenty,
            FixtureResult usageOnlyAll,
            boolean reverse) {
        if (reverse) {
            add(craft, output, craftTwenty, craftTen);
            add(usage, c, usageOnlyAll, usageOnlySingle);
        } else {
            add(craft, output, craftTen, craftTwenty);
            add(usage, c, usageOnlySingle, usageOnlyAll);
        }
        add(usage, a, craftTen);
        add(usage, b, craftTen);
        add(usage, d, craftTwenty);
        add(usage, e, craftTwenty);
        add(usage, f, usageOnlyAll);
    }

    private FixtureResult result(
            int points, String canonical, FixtureCrop first, FixtureCrop second) {
        return new FixtureResult(output, points, canonical, first, second);
    }

    private HashMap<FixtureCrop, List<FixtureResult>> emptyView(boolean reverse) {
        HashMap<FixtureCrop, List<FixtureResult>> view =
                new HashMap<FixtureCrop, List<FixtureResult>>();
        List<FixtureCrop> keys = new ArrayList<FixtureCrop>(allCrops);
        if (reverse) {
            Collections.reverse(keys);
        }
        for (FixtureCrop crop : keys) {
            view.put(crop, new ArrayList<FixtureResult>());
        }
        return view;
    }

    private static void add(
            HashMap<FixtureCrop, List<FixtureResult>> view,
            FixtureCrop crop,
            FixtureResult... results) {
        view.get(crop).addAll(Arrays.asList(results));
    }

    private CropCacheViewContract.Snapshot<FixtureRecord> audit(
            HashMap<FixtureCrop, List<FixtureResult>> craft,
            HashMap<FixtureCrop, List<FixtureResult>> usage) throws ExportFailure {
        return CropCacheViewContract.audit(
                craft, usage, FixtureResult.class, cropIds, CAPTURE, MATCHER);
    }

    private void assertAuditFailure(
            String code,
            HashMap<FixtureCrop, List<FixtureResult>> craft,
            HashMap<FixtureCrop, List<FixtureResult>> usage) throws Exception {
        try {
            audit(craft, usage);
        } catch (ExportFailure failure) {
            assertEquals(code, failure.code);
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private static List<String> canonicals(List<FixtureRecord> records) {
        List<String> values = new ArrayList<String>(records.size());
        for (FixtureRecord record : records) {
            values.add(record.semanticCanonical());
        }
        return values;
    }
}
