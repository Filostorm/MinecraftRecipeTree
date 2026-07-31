package com.recipetree.reiexport118.mixin;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class NativeImagePixelsAccessorTest {
    @Test
    void pinsTheProductionSrgFieldWhenRefmapsAreDisabled() throws Exception {
        Method getter = NativeImagePixelsAccessor.class.getDeclaredMethod("reiexport$getPixels");
        Accessor accessor = getter.getAnnotation(Accessor.class);
        assertEquals("f_84964_", accessor.value());
        assertFalse(accessor.remap());
    }
}
