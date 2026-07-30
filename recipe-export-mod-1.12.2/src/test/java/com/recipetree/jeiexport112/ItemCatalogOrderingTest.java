package com.recipetree.jeiexport112;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ItemCatalogOrderingTest {
    @Test
    public void resolvedIngredientsSortByExactExportedKeyWithoutInspectingIngredientToString() {
        List<CanonicalKeyOrdering.Entry> resolved = Arrays.<CanonicalKeyOrdering.Entry>asList(
                entry("item|zeta:machine"),
                entry("fluid|alpha:steam"),
                entry("item|alpha:machine"));

        CanonicalKeyOrdering.sortAndValidate(resolved);

        assertEquals(Arrays.asList(
                        "fluid|alpha:steam",
                        "item|alpha:machine",
                        "item|zeta:machine"),
                Arrays.asList(
                        resolved.get(0).canonicalKey(),
                        resolved.get(1).canonicalKey(),
                        resolved.get(2).canonicalKey()));
    }

    @Test
    public void distinctCanonicalKeysDoNotRequestExactPayloads() {
        List<CanonicalKeyOrdering.Entry> resolved = Arrays.<CanonicalKeyOrdering.Entry>asList(
                entryWithUnavailablePayload("item|zeta:machine"),
                entryWithUnavailablePayload("fluid|alpha:steam"),
                entryWithUnavailablePayload("item|alpha:machine"));

        CanonicalKeyOrdering.sortAndValidate(resolved);

        assertEquals(Arrays.asList(
                        "fluid|alpha:steam",
                        "item|alpha:machine",
                        "item|zeta:machine"),
                Arrays.asList(
                        resolved.get(0).canonicalKey(),
                        resolved.get(1).canonicalKey(),
                        resolved.get(2).canonicalKey()));
    }

    @Test
    public void resolvedIngredientBuildsExactPayloadLazilyAndMemoizesIt() {
        ItemCatalog.ResolvedIngredient<Object> resolved =
                new ItemCatalog.ResolvedIngredient<Object>(
                        null,
                        new Object(),
                        "item",
                        "alpha:machine",
                        "alpha:machine",
                        "Machine",
                        "alpha");

        assertNull(resolved.canonicalPayload);
        String first = resolved.canonicalPayload();

        assertEquals(
                "4:item13:alpha:machine13:alpha:machine7:Machine5:alpha16:java.lang.Object",
                first);
        assertSame(first, resolved.canonicalPayload());
    }

    @Test(expected = IllegalStateException.class)
    public void equalCanonicalKeysWithDifferentExactPayloadsAreRejectedBeforeEmission() {
        List<CanonicalKeyOrdering.Entry> resolved = Arrays.<CanonicalKeyOrdering.Entry>asList(
                entry("item|alpha:machine", "resource=alpha:machine;name=A"),
                entry("item|alpha:machine", "resource=alpha:machine;name=B"));

        CanonicalKeyOrdering.sortAndValidate(resolved);
    }

    @Test
    public void exactDuplicateCanonicalKeysAndPayloadsAreEquivalent() {
        List<CanonicalKeyOrdering.Entry> resolved = Arrays.<CanonicalKeyOrdering.Entry>asList(
                entry("item|alpha:machine", "resource=alpha:machine;name=A"),
                entry("item|alpha:machine", "resource=alpha:machine;name=A"));

        CanonicalKeyOrdering.sortAndValidate(resolved);

        assertEquals(resolved.get(0).canonicalPayload(), resolved.get(1).canonicalPayload());
    }

    private static CanonicalKeyOrdering.Entry entry(final String key) {
        return entry(key, "payload:" + key);
    }

    private static CanonicalKeyOrdering.Entry entry(final String key, final String payload) {
        return new CanonicalKeyOrdering.Entry() {
            @Override
            public String canonicalKey() {
                return key;
            }

            @Override
            public String canonicalPayload() {
                return payload;
            }

            @Override
            public String toString() {
                throw new AssertionError("ordering must not inspect lossy ingredient text");
            }
        };
    }

    private static CanonicalKeyOrdering.Entry entryWithUnavailablePayload(final String key) {
        return new CanonicalKeyOrdering.Entry() {
            @Override
            public String canonicalKey() {
                return key;
            }

            @Override
            public String canonicalPayload() {
                throw new AssertionError("distinct canonical keys must not materialize exact payloads");
            }
        };
    }
}
