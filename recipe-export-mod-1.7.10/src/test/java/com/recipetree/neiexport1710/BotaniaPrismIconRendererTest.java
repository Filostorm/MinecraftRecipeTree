package com.recipetree.neiexport1710;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BotaniaPrismIconRendererTest {
    @Test
    public void legacyTransparentMipCutoffZerosPinnedPrismMaximum() {
        assertEquals(91, BotaniaPrismIconRenderer.legacyTransparentMipmapAlphaBeforeCutoff(
                112, 112, 64, 64));
        assertEquals(0, BotaniaPrismIconRenderer.legacyTransparentMipmapAlpha(
                112, 112, 64, 64));
    }

    @Test
    public void postStitchNonAnimatedSpriteRequiresClearedCpuFrames() {
        TestSprite sprite = new TestSprite("botania:prism0");
        BotaniaPrismIconRenderer.verifyPostStitchFrameState(sprite, "botania:prism0");

        sprite.setFramesTextureData(Collections.singletonList(new int[1][]));
        try {
            BotaniaPrismIconRenderer.verifyPostStitchFrameState(sprite, "botania:prism0");
            fail("retained post-stitch CPU frames must fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("retained 1 CPU frame"));
        }
    }

    @Test
    public void boundedPinnedResourceRequiresExactLengthAndDigest() throws Exception {
        byte[] bytes = {1, 2, 3, 4, 5};
        assertArrayEquals(bytes, BotaniaPrismIconRenderer.readExactPinnedResource(
                new ByteArrayInputStream(bytes), bytes.length,
                BotaniaPrismIconRenderer.sha256Hex(bytes)));

        try {
            BotaniaPrismIconRenderer.readExactPinnedResource(
                    new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6}),
                    bytes.length, BotaniaPrismIconRenderer.sha256Hex(bytes));
            fail("oversize resolved resources must fail closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("byte length drifted"));
        }

        try {
            BotaniaPrismIconRenderer.readExactPinnedResource(
                    new ByteArrayInputStream(bytes), bytes.length,
                    BotaniaPrismIconRenderer.sha256Hex(new byte[] {9}));
            fail("digest drift must fail closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256 drifted"));
        }
    }

    @Test
    public void baseLevelLeaseRestoresTextureStateAfterOwnerDraw() throws Exception {
        FakeTextureState textures = new FakeTextureState();
        textures.activeTexture(GL13.GL_TEXTURE1);
        textures.bindTexture2d(17);
        textures.activeTexture(OpenGlHelper.defaultTexUnit);
        textures.bindTexture2d(23);
        textures.maximumMipByTexture.put(29, 4);
        textures.activeTexture(GL13.GL_TEXTURE1);

        BotaniaPrismIconRenderer.AtlasBaseLevelLease lease =
                new BotaniaPrismIconRenderer.AtlasBaseLevelLease(
                        textures, 29, Thread.currentThread(),
                        verifierFor(textures, 29, 4));
        lease.draw(new OffscreenRenderer.DrawCall() {
            @Override
            public void draw() {
                assertEquals(OpenGlHelper.defaultTexUnit, textures.activeTexture());
                assertEquals(29, textures.boundTexture2d());
                assertEquals(0, textures.maximumMipLevel());
                textures.bindTexture2d(31);
            }
        });

        assertEquals(GL13.GL_TEXTURE1, textures.activeTexture());
        assertEquals(17, textures.boundTexture2d());
        textures.activeTexture(OpenGlHelper.defaultTexUnit);
        assertEquals(23, textures.boundTexture2d());
        textures.bindTexture2d(29);
        assertEquals(4, textures.maximumMipLevel());
    }

    @Test
    public void baseLevelLeaseRestoresTextureStateAfterOwnerFailure() throws Exception {
        final RuntimeException ownerFailure = new RuntimeException("owner failed");
        FakeTextureState textures = new FakeTextureState();
        textures.activeTexture(OpenGlHelper.defaultTexUnit);
        textures.bindTexture2d(41);
        textures.maximumMipByTexture.put(43, 3);

        BotaniaPrismIconRenderer.AtlasBaseLevelLease lease =
                new BotaniaPrismIconRenderer.AtlasBaseLevelLease(
                        textures, 43, Thread.currentThread(),
                        verifierFor(textures, 43, 3));
        try {
            lease.draw(new OffscreenRenderer.DrawCall() {
                @Override
                public void draw() {
                    throw ownerFailure;
                }
            });
        } catch (RuntimeException observed) {
            assertSame(ownerFailure, observed);
        }

        assertEquals(OpenGlHelper.defaultTexUnit, textures.activeTexture());
        assertEquals(41, textures.boundTexture2d());
        textures.bindTexture2d(43);
        assertEquals(3, textures.maximumMipLevel());
    }

    @Test
    public void liveAtlasReadbackRestoresFramebufferReadBufferAndPackState() throws Exception {
        LiveFixture fixture = new LiveFixture();
        BotaniaPrismIconRenderer.verifyLiveAtlas(
                fixture.readback, fixture.atlasTextureId,
                fixture.owner, fixture.side);

        fixture.assertStateRestored();
        assertTrue(fixture.readback.deletedFramebuffer);
    }

    @Test
    public void liveAtlasReadFailureStillRestoresFramebufferReadBufferAndPackState() {
        LiveFixture fixture = new LiveFixture();
        fixture.readback.failReadCall = 2;
        try {
            BotaniaPrismIconRenderer.verifyLiveAtlas(
                    fixture.readback, fixture.atlasTextureId,
                    fixture.owner, fixture.side);
            fail("readback failure must fail closed");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("injected read failure"));
        }

        fixture.assertStateRestored();
        assertTrue(fixture.readback.deletedFramebuffer);
    }

    private static BotaniaPrismIconRenderer.PreClampVerifier verifierFor(
            final FakeTextureState textures,
            final int atlasTextureId,
            final int expectedMaximumMip) {
        return new BotaniaPrismIconRenderer.PreClampVerifier() {
            @Override
            public void verify() {
                assertEquals(OpenGlHelper.defaultTexUnit, textures.activeTexture());
                assertEquals(atlasTextureId, textures.boundTexture2d());
                assertEquals(expectedMaximumMip, textures.maximumMipLevel());
            }
        };
    }

    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(String name) {
            super(name);
        }
    }

    private static final class FakeTextureState
            implements BotaniaPrismIconRenderer.TextureStateAccess {
        private int activeTexture = OpenGlHelper.defaultTexUnit;
        private final Map<Integer, Integer> bindingByUnit = new HashMap<Integer, Integer>();
        final Map<Integer, Integer> maximumMipByTexture = new HashMap<Integer, Integer>();

        @Override
        public int activeTexture() {
            return activeTexture;
        }

        @Override
        public void activeTexture(int textureUnit) {
            activeTexture = textureUnit;
        }

        @Override
        public int boundTexture2d() {
            Integer binding = bindingByUnit.get(activeTexture);
            return binding == null ? 0 : binding;
        }

        @Override
        public void bindTexture2d(int textureId) {
            bindingByUnit.put(activeTexture, textureId);
        }

        @Override
        public int maximumMipLevel() {
            Integer level = maximumMipByTexture.get(boundTexture2d());
            return level == null ? 0 : level;
        }

        @Override
        public void maximumMipLevel(int level) {
            maximumMipByTexture.put(boundTexture2d(), level);
        }
    }

    private static final class LiveFixture {
        final int atlasTextureId = 29;
        final int oldFramebuffer = 77;
        final int oldReadBuffer = GL11.GL_BACK;
        final int[] ownerBase = filled(32 * 32, 0x40112233);
        final int[] sideBase = filled(32 * 32, 0x70445566);
        final int[] ownerMip = filled(16 * 16, 0x00123456);
        final int[] sideMip = filled(16 * 16, 0x00654321);
        final BotaniaPrismIconRenderer.SpriteRegion owner =
                new BotaniaPrismIconRenderer.SpriteRegion(
                        "botania:prism0", 0, 0, 32, 32, ownerBase, ownerMip);
        final BotaniaPrismIconRenderer.SpriteRegion side =
                new BotaniaPrismIconRenderer.SpriteRegion(
                        "botania:prism1", 32, 0, 32, 32, sideBase, sideMip);
        final FakeAtlasReadback readback = new FakeAtlasReadback(
                atlasTextureId, oldFramebuffer, oldReadBuffer,
                ownerBase, sideBase, ownerMip, sideMip);
        final Map<Integer, Integer> originalPack =
                new HashMap<Integer, Integer>(readback.packState);

        void assertStateRestored() {
            assertEquals(oldFramebuffer, readback.framebufferBinding());
            assertEquals(oldReadBuffer, readback.readBuffer());
            assertEquals(originalPack, readback.packState);
        }

        private static int[] filled(int length, int value) {
            int[] result = new int[length];
            for (int index = 0; index < length; index++) {
                result[index] = value;
            }
            return result;
        }
    }

    private static final class FakeAtlasReadback
            implements BotaniaPrismIconRenderer.AtlasReadbackAccess {
        private final int atlasTextureId;
        private final int[] ownerBase;
        private final int[] sideBase;
        private final int[] ownerMip;
        private final int[] sideMip;
        private int framebuffer;
        private int readBuffer;
        private int attachedLevel;
        private int readCalls;
        int failReadCall;
        boolean deletedFramebuffer;
        final Map<Integer, Integer> packState = new HashMap<Integer, Integer>();

        FakeAtlasReadback(
                int atlasTextureId,
                int framebuffer,
                int readBuffer,
                int[] ownerBase,
                int[] sideBase,
                int[] ownerMip,
                int[] sideMip) {
            this.atlasTextureId = atlasTextureId;
            this.framebuffer = framebuffer;
            this.readBuffer = readBuffer;
            this.ownerBase = ownerBase;
            this.sideBase = sideBase;
            this.ownerMip = ownerMip;
            this.sideMip = sideMip;
            packState.put(GL11.GL_PACK_ALIGNMENT, 8);
            packState.put(GL11.GL_PACK_ROW_LENGTH, 19);
            packState.put(GL11.GL_PACK_SKIP_ROWS, 3);
            packState.put(GL11.GL_PACK_SKIP_PIXELS, 5);
            packState.put(GL11.GL_PACK_SWAP_BYTES, 1);
            packState.put(GL11.GL_PACK_LSB_FIRST, 1);
        }

        @Override
        public boolean framebufferSupported() {
            return true;
        }

        @Override
        public boolean textureExists(int textureId) {
            return textureId == atlasTextureId;
        }

        @Override
        public int textureParameter(int parameter) {
            if (parameter == org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL) {
                return 0;
            }
            if (parameter == GL11.GL_TEXTURE_MIN_FILTER) {
                return GL11.GL_NEAREST_MIPMAP_LINEAR;
            }
            throw new AssertionError("unexpected texture parameter " + parameter);
        }

        @Override
        public int textureLevelParameter(int level, int parameter) {
            if (parameter == GL11.GL_TEXTURE_WIDTH) {
                return level == 0 ? 64 : 32;
            }
            if (parameter == GL11.GL_TEXTURE_HEIGHT) {
                return level == 0 ? 32 : 16;
            }
            throw new AssertionError("unexpected texture-level parameter " + parameter);
        }

        @Override
        public int framebufferBinding() {
            return framebuffer;
        }

        @Override
        public int readBuffer() {
            return readBuffer;
        }

        @Override
        public int generateFramebuffer() {
            return 91;
        }

        @Override
        public void bindFramebuffer(int framebuffer) {
            this.framebuffer = framebuffer;
        }

        @Override
        public void attachTexture(int textureId, int level) {
            assertEquals(atlasTextureId, textureId);
            attachedLevel = level;
        }

        @Override
        public int framebufferStatus() {
            return OpenGlHelper.field_153202_i;
        }

        @Override
        public void readBuffer(int buffer) {
            readBuffer = buffer;
        }

        @Override
        public int pixelStore(int parameter) {
            Integer value = packState.get(parameter);
            return value == null ? 0 : value;
        }

        @Override
        public void pixelStore(int parameter, int value) {
            packState.put(parameter, value);
        }

        @Override
        public int[] readPixels(int x, int y, int width, int height) {
            readCalls++;
            if (readCalls == failReadCall) {
                throw new IllegalStateException("injected read failure");
            }
            int[] source;
            if (attachedLevel == 0) {
                source = x == 0 ? ownerBase : sideBase;
            } else {
                source = x == 0 ? ownerMip : sideMip;
            }
            return source.clone();
        }

        @Override
        public void deleteFramebuffer(int framebuffer) {
            assertEquals(91, framebuffer);
            deletedFramebuffer = true;
        }
    }
}
