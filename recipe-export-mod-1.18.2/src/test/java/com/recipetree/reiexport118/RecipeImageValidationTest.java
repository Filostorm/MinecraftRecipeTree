package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RecipeImageValidationTest {
    @Test
    void convertsArgbToNativeImageAbgrWithoutLosingAnyChannel() {
        assertEquals(0x7f332211, RecipeImageValidation.argbToNativeRgba(0x7f112233));
        assertEquals(0xffc6c6c6, RecipeImageValidation.argbToNativeRgba(0xffc6c6c6));
        assertEquals(0x00000000, RecipeImageValidation.argbToNativeRgba(0x00000000));
    }

    @Test
    void requiresAtLeastOneLogicalPixelAtTheRequestedScale() {
        assertEquals(1L, RecipeImageValidation.minimumNativePixels(1));
        assertEquals(4L, RecipeImageValidation.minimumNativePixels(2));
        assertEquals(9L, RecipeImageValidation.minimumNativePixels(3));
        assertThrows(IllegalArgumentException.class,
                () -> RecipeImageValidation.minimumNativePixels(0));
    }

    @Test
    void rejectsANonPositiveEarlyExitThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> RecipeImageValidation.countPixelsDifferentFrom(null, 0, 0));
    }
}
