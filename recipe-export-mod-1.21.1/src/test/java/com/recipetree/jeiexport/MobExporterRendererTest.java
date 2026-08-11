package com.recipetree.jeiexport;

import net.minecraft.client.renderer.entity.NoopRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MobExporterRendererTest {
    @Test
    void recognizesMinecraftsIntentionalNoopRenderer() {
        assertTrue(MobExporter.isNoopRendererType(NoopRenderer.class));
        assertFalse(MobExporter.isNoopRendererType(Object.class));
    }
}
