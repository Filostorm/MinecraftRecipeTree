package com.recipetree.neiexport1710;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

public class CropIdentityContractTest {
    /** Mirrors the relevant public/abstract method shape of IC2 2.2.828 CropCard. */
    public abstract static class ApiCropCard {
        public String owner() {
            return "fixture";
        }

        public abstract String name();
    }

    private static final class HiddenCrop extends ApiCropCard {
        private final String owner;
        private final String name;

        private HiddenCrop(String owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        @Override
        public String owner() {
            return owner;
        }

        @Override
        public String name() {
            return name;
        }
    }

    @Test
    public void publicApiMethodsDispatchToPackageInaccessibleConcreteCrop()
            throws Exception {
        assertFalse(Modifier.isPublic(HiddenCrop.class.getModifiers()));
        Method concreteName = HiddenCrop.class.getMethod("name");
        assertSame("the old runtime-class lookup binds the inaccessible declaration",
                HiddenCrop.class, concreteName.getDeclaringClass());

        CropIdentityContract contract =
                CropIdentityContract.bindForTesting(ApiCropCard.class);
        Map<String, Object> crops = new HashMap<String, Object>();
        HiddenCrop crop = new HiddenCrop("berriespp", "Oak Bonsai");

        String id = contract.requireCanonicalId(crop, crops);

        assertEquals("O9:berriesppN10:Oak Bonsai", id);
        assertSame(crop, crops.get(id));
    }

    @Test
    public void ownerAndNameBoundaryIsInjectiveEvenWhenValuesContainColons()
            throws Exception {
        CropIdentityContract contract =
                CropIdentityContract.bindForTesting(ApiCropCard.class);
        Map<String, Object> crops = new HashMap<String, Object>();

        String first = contract.requireCanonicalId(
                new HiddenCrop("a", "b:c"), crops);
        String second = contract.requireCanonicalId(
                new HiddenCrop("a:b", "c"), crops);

        assertEquals("O1:aN3:b:c", first);
        assertEquals("O3:a:bN1:c", second);
        assertNotEquals(first, second);
        assertEquals(2, crops.size());
    }

    @Test
    public void componentLengthsAreUtf8ByteLengths() throws Exception {
        CropIdentityContract contract =
                CropIdentityContract.bindForTesting(ApiCropCard.class);

        String id = contract.requireCanonicalId(
                new HiddenCrop("M\u00f3d", "\u7a32"),
                new HashMap<String, Object>());

        assertEquals("O4:M\u00f3dN3:\u7a32", id);
    }

    @Test
    public void identityPreservesRawValuesAndRejectsBoundaryWhitespace()
            throws Exception {
        CropIdentityContract contract =
                CropIdentityContract.bindForTesting(ApiCropCard.class);

        assertFailure("RECIPE_SEMANTICS", contract,
                new HiddenCrop(" berriespp", "Oak Bonsai"));
        assertFailure("RECIPE_SEMANTICS", contract,
                new HiddenCrop("berriespp", "Oak Bonsai "));
    }

    @Test
    public void rejectsNullEmptyControlAndMalformedUnicodeComponents()
            throws Exception {
        CropIdentityContract contract =
                CropIdentityContract.bindForTesting(ApiCropCard.class);

        assertFailure("RECIPE_SEMANTICS", contract,
                new HiddenCrop(null, "Oak Bonsai"));
        assertFailure("RECIPE_SEMANTICS", contract,
                new HiddenCrop("berriespp", ""));
        assertFailure("RECIPE_SEMANTICS", contract,
                new HiddenCrop("berriespp", "Oak\nBonsai"));
        assertFailure("RECIPE_SEMANTICS", contract,
                new HiddenCrop("berriespp", "Oak\ud800Bonsai"));
    }

    @Test
    public void rejectsDistinctCropObjectsWithTheSameCanonicalIdentity()
            throws Exception {
        CropIdentityContract contract =
                CropIdentityContract.bindForTesting(ApiCropCard.class);
        Map<String, Object> crops = new HashMap<String, Object>();
        HiddenCrop first = new HiddenCrop("berriespp", "Oak Bonsai");
        contract.requireCanonicalId(first, crops);

        assertFailure("HANDLER_DUPLICATE", contract,
                new HiddenCrop("berriespp", "Oak Bonsai"), crops);
        assertEquals(1, crops.size());
        assertSame(first, crops.values().iterator().next());
    }

    private static void assertFailure(String code,
                                      CropIdentityContract contract,
                                      Object crop) throws Exception {
        assertFailure(code, contract, crop, new HashMap<String, Object>());
    }

    private static void assertFailure(String code,
                                      CropIdentityContract contract,
                                      Object crop,
                                      Map<String, Object> crops) throws Exception {
        try {
            contract.requireCanonicalId(crop, crops);
        } catch (ExportFailure failure) {
            assertEquals(code, failure.code);
            return;
        }
        throw new AssertionError("Expected " + code);
    }
}
