package com.recipetree.jeiexport112;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OreDictionarySlotIdentityTest {
    private static Set<String> names(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }

    @Test
    public void resolvesTheIdentitySharedByEveryAlternative() {
        OreDictionarySlotIdentity.Resolution resolution =
                OreDictionarySlotIdentity.resolveNames(Arrays.asList(
                        names("ingotCopper", "materialCopper"),
                        names("ingotCopper"),
                        names("ingotCopper", "metalCopper")));

        assertTrue(resolution.isPresent());
        assertEquals("ore:ingotCopper", resolution.identity);
        assertFalse(resolution.isAmbiguous());
    }

    @Test
    public void refusesToMergeAlternativesWithoutACommonDictionaryName() {
        OreDictionarySlotIdentity.Resolution resolution =
                OreDictionarySlotIdentity.resolveNames(Arrays.asList(
                        names("ingotCopper"), names("ingotTin")));

        assertFalse(resolution.isPresent());
    }

    @Test
    public void multipleSharedNamesUseStableOrderingAndRemainObservable() {
        OreDictionarySlotIdentity.Resolution resolution =
                OreDictionarySlotIdentity.resolveNames(Arrays.asList(
                        names("metalCopper", "ingotCopper"),
                        names("ingotCopper", "metalCopper")));

        assertEquals("ore:ingotCopper", resolution.identity);
        assertTrue(resolution.isAmbiguous());
        assertEquals(Arrays.asList("ingotCopper", "metalCopper"), resolution.sharedNames);
    }

    @Test
    public void choosesTheNarrowestDictionaryIdentityInsteadOfAGenericOreAlias() {
        Map<String, Integer> cardinalities = new LinkedHashMap<String, Integer>();
        cardinalities.put("ore", 900);
        cardinalities.put("oreIron", 5);

        OreDictionarySlotIdentity.Resolution resolution =
                OreDictionarySlotIdentity.resolveNames(Arrays.asList(
                        names("ore", "oreIron"),
                        names("ore", "oreIron")), cardinalities);

        assertEquals("ore:oreIron", resolution.identity);
        assertEquals(Arrays.asList("ore", "oreIron"), resolution.sharedNames);
    }

    @Test
    public void emptyAlternativeListHasNoIdentity() {
        assertFalse(OreDictionarySlotIdentity.resolveNames(
                Collections.<Set<String>>emptyList()).isPresent());
    }
}
