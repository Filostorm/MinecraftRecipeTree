package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageVisibilityTest {
    @Test
    void classifiesVisibleHiddenAndEmptyFramebuffers() {
        assertEquals(
                ImageVisibility.Result.VISIBLE,
                ImageVisibility.resultFor(true, true));
        assertEquals(
                ImageVisibility.Result.REPAIRED_HIDDEN_RGB,
                ImageVisibility.resultFor(false, true));
        assertEquals(
                ImageVisibility.Result.EMPTY,
                ImageVisibility.resultFor(false, false));
    }

    @Test
    void makesHiddenRgbOpaqueWithoutChangingItsColorChannels() {
        assertEquals(0xFF563412, ImageVisibility.withOpaqueAlpha(0x00563412));
        assertEquals(0xFF563412, ImageVisibility.withOpaqueAlpha(0x7F563412));
    }
}
