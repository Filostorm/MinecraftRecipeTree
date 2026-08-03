package com.recipetree.neiexport1710;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Exact correction for Botania 1.12.28-GTNH's Mana Prism inventory icon.
 *
 * <p>The public prism item uses a 32x32, intentionally translucent owner texture. Minecraft's
 * legacy transparent-texture mip generator gamma-averages each 2x2 texel group and then zeros
 * generated alpha below 96/255. Every alpha value in the prism's 16x16 level-one mip is therefore
 * zero, and the standard 16x16 GUI draw selects that empty mip. This adapter keeps the owner
 * {@link RenderItem}, atlas sprite, tint, blend mode, geometry, and minification filter, but
 * temporarily clamps the atlas maximum mip level to zero for the exact prism draw.</p>
 */
final class BotaniaPrismIconRenderer {
    static final class AdapterCounts {
        final long botaniaPrism;
        final long wrcbeTriangulator;

        AdapterCounts(long botaniaPrism, long wrcbeTriangulator) {
            this.botaniaPrism = botaniaPrism;
            this.wrcbeTriangulator = wrcbeTriangulator;
        }
    }

    static final String CONTRACT =
            "botania-prism-resolved-resource-live-atlas-base-level-lease-v2";
    static final String OWNER_ICON_NAME = "botania:prism0";
    static final String OWNER_SIDE_ICON_NAME = "botania:prism1";
    static final int OWNER_ICON_WIDTH = 32;
    static final int OWNER_ICON_HEIGHT = 32;
    static final int OWNER_BASE_NONZERO_ALPHA_PIXELS = 256;
    static final int OWNER_SIDE_BASE_NONZERO_ALPHA_PIXELS = 512;
    static final int OWNER_BASE_MAX_ALPHA = 112;
    static final int LEGACY_TRANSPARENT_MIP_ALPHA_CUTOFF = 96;
    static final String OWNER_RESOURCE_PATH = "textures/blocks/prism0.png";
    static final String OWNER_SIDE_RESOURCE_PATH = "textures/blocks/prism1.png";
    static final int OWNER_RESOURCE_BYTES = 2917;
    static final int OWNER_SIDE_RESOURCE_BYTES = 2949;
    static final String OWNER_RESOURCE_SHA256 =
            "16cb5ef1c7b81514e1d9410592e893be7b9c5480b558f207566f4c67dddc4791";
    static final String OWNER_SIDE_RESOURCE_SHA256 =
            "3c4b3f913f326502389bcf839e081d50a1005453372f031ea1687b20507d85a9";
    private static final int[] PACK_STATE_NAMES = {
            GL11.GL_PACK_ALIGNMENT,
            GL11.GL_PACK_ROW_LENGTH,
            GL11.GL_PACK_SKIP_ROWS,
            GL11.GL_PACK_SKIP_PIXELS,
            GL11.GL_PACK_SWAP_BYTES,
            GL11.GL_PACK_LSB_FIRST
    };

    private final RenderItem ownerRenderer;
    private final BaseLevelRenderItem adapterRenderer;
    private final Thread renderThread;
    private final boolean requireMinecraftClientThread;
    private boolean leaseActive;

    BotaniaPrismIconRenderer(
            RenderItem ownerRenderer,
            BaseLevelRenderItem adapterRenderer) {
        this(ownerRenderer, adapterRenderer, false);
    }

    private BotaniaPrismIconRenderer(
            RenderItem ownerRenderer,
            BaseLevelRenderItem adapterRenderer,
            boolean requireMinecraftClientThread) {
        this.ownerRenderer = ownerRenderer;
        this.adapterRenderer = adapterRenderer;
        this.renderThread = Thread.currentThread();
        this.requireMinecraftClientThread = requireMinecraftClientThread;
    }

    static BotaniaPrismIconRenderer create(ItemStack prismStack) throws Exception {
        if (!StackIdentity.isPinnedBotaniaPrismIconTarget(prismStack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Botania prism adapter requires its exact pinned stack");
        }
        Block block = Block.getBlockFromItem(prismStack.getItem());
        if (block == null
                || !StackIdentity.BOTANIA_PRISM_BLOCK_CLASS.equals(block.getClass().getName())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism block registration drifted");
        }
        if (block.getRenderType() != 0
                || block.getRenderBlockPass() != 1
                || !RenderBlocks.renderItemIn3d(block.getRenderType())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism no longer uses its translucent "
                            + "standard-block inventory path");
        }
        if (prismStack.getItemSpriteNumber() != 0) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism no longer uses the block atlas");
        }

        IIcon icon = prismStack.getIconIndex();
        IIcon sideIcon = block.getIcon(2, 0);
        if (icon == null || icon != block.getIcon(1, 0)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism item icon binding drifted; got "
                            + describeIcon(icon));
        }
        TextureAtlasSprite ownerSprite = verifyPinnedIcon(icon, OWNER_ICON_NAME);
        TextureAtlasSprite ownerSideSprite = verifyPinnedIcon(sideIcon, OWNER_SIDE_ICON_NAME);

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null
                || minecraft.getTextureManager() == null
                || minecraft.getResourceManager() == null
                || minecraft.getTextureMapBlocks() == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Minecraft texture/resource manager is unavailable");
        }
        TextureMap blockAtlas = minecraft.getTextureMapBlocks();
        ITextureObject atlas = minecraft.getTextureManager().getTexture(
                TextureMap.locationBlocksTexture);
        if (atlas != blockAtlas || atlas.getGlTextureId() <= 0) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: canonical Minecraft block texture atlas ownership drifted");
        }
        if (blockAtlas.getAtlasSprite(OWNER_ICON_NAME) != ownerSprite
                || blockAtlas.getAtlasSprite(OWNER_SIDE_ICON_NAME) != ownerSideSprite) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism sprites are not owned by the "
                            + "canonical stitched block atlas");
        }
        if (minecraft.gameSettings == null || minecraft.gameSettings.anaglyph) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism live-atlas verification requires "
                            + "the exporter-owned non-anaglyph render path");
        }

        PinnedPixels ownerPixels = loadPinnedPixels(
                minecraft.getResourceManager(), OWNER_ICON_NAME, OWNER_RESOURCE_PATH,
                OWNER_RESOURCE_BYTES, OWNER_RESOURCE_SHA256,
                OWNER_BASE_NONZERO_ALPHA_PIXELS);
        PinnedPixels ownerSidePixels = loadPinnedPixels(
                minecraft.getResourceManager(), OWNER_SIDE_ICON_NAME, OWNER_SIDE_RESOURCE_PATH,
                OWNER_SIDE_RESOURCE_BYTES, OWNER_SIDE_RESOURCE_SHA256,
                OWNER_SIDE_BASE_NONZERO_ALPHA_PIXELS);
        RuntimeAtlasVerifier atlasVerifier = new RuntimeAtlasVerifier(
                minecraft, blockAtlas, block, ownerSprite, ownerSideSprite,
                atlas.getGlTextureId(), ownerPixels, ownerSidePixels,
                new GlAtlasReadbackAccess());

        RenderItem ownerRenderer = GuiContainerManager.drawItems;
        if (ownerRenderer == null || ownerRenderer.getClass() != RenderItem.class) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned NEI owner RenderItem mismatch; got "
                            + (ownerRenderer == null
                                    ? "<null>" : ownerRenderer.getClass().getName()));
        }
        AtlasBaseLevelLease atlasLease = new AtlasBaseLevelLease(
                new GlTextureStateAccess(), atlas.getGlTextureId(), Thread.currentThread(),
                atlasVerifier);
        BaseLevelRenderItem adapterRenderer =
                new BaseLevelRenderItem(ownerRenderer, atlasLease);
        return new BotaniaPrismIconRenderer(
                ownerRenderer, adapterRenderer, true);
    }

    static BotaniaPrismIconRenderer createPinnedRuntime() throws Exception {
        net.minecraft.item.Item prism = GameRegistry.findItem("Botania", "prism");
        if (prism == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism registry entry is absent");
        }
        return create(new ItemStack(prism, 1, 0));
    }

    synchronized void attachWrcbeTriangulator(
            WrcbeTriangulatorIconRenderer renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator adapter is required");
        }
        if (Thread.currentThread() != renderThread || leaseActive) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator adapter attachment left the "
                            + "idle pinned render thread");
        }
        adapterRenderer.attachWrcbeTriangulator(renderer);
    }

    void drawExactlyOnce(OffscreenRenderer.DrawCall ownerInventoryDraw) throws Exception {
        long invocations = drawAndCount(ownerInventoryDraw);
        if (invocations != 1L) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism owner inventory renderer invoked the "
                            + "base-level adapter " + invocations
                            + " times instead of exactly once");
        }
    }

    synchronized long drawAndCount(OffscreenRenderer.DrawCall ownerDraw) throws Exception {
        return drawAndCountAll(ownerDraw).botaniaPrism;
    }

    synchronized AdapterCounts drawAndCountAll(
            OffscreenRenderer.DrawCall ownerDraw) throws Exception {
        if (ownerDraw == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Botania prism owner draw is required");
        }
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism RenderItem lease left its pinned client thread");
        }
        if (requireMinecraftClientThread
                && (Minecraft.getMinecraft() == null
                || !Minecraft.getMinecraft().func_152345_ab())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism RenderItem lease is not on Minecraft's client thread");
        }
        if (leaseActive) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: nested Botania prism RenderItem leases are forbidden");
        }
        if (GuiContainerManager.drawItems != ownerRenderer) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: NEI owner RenderItem binding drifted before Botania prism draw");
        }

        adapterRenderer.copyStateFrom(ownerRenderer);
        long attemptsBefore = adapterRenderer.attempts;
        long successesBefore = adapterRenderer.successes;
        long failuresBefore = adapterRenderer.failures;
        long wrcbeAttemptsBefore = adapterRenderer.wrcbeAttempts;
        long wrcbeSuccessesBefore = adapterRenderer.wrcbeSuccesses;
        long wrcbeFailuresBefore = adapterRenderer.wrcbeFailures;
        leaseActive = true;
        GuiContainerManager.drawItems = adapterRenderer;

        Throwable failure = null;
        try {
            ownerDraw.draw();
        } catch (Throwable error) {
            failure = error;
        } finally {
            if (GuiContainerManager.drawItems != adapterRenderer) {
                failure = merge(failure, new IllegalStateException(
                        "ITEM_ICON_RENDER: Botania prism RenderItem lease changed during draw"));
            }
            try {
                adapterRenderer.copyStateTo(ownerRenderer);
                GuiContainerManager.drawItems = ownerRenderer;
                if (GuiContainerManager.drawItems != ownerRenderer) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania prism owner RenderItem restore was not exact"));
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            leaseActive = false;
        }

        long attempts = adapterRenderer.attempts - attemptsBefore;
        long successes = adapterRenderer.successes - successesBefore;
        long failures = adapterRenderer.failures - failuresBefore;
        long wrcbeAttempts = adapterRenderer.wrcbeAttempts - wrcbeAttemptsBefore;
        long wrcbeSuccesses = adapterRenderer.wrcbeSuccesses - wrcbeSuccessesBefore;
        long wrcbeFailures = adapterRenderer.wrcbeFailures - wrcbeFailuresBefore;
        if (attempts < 0L || successes < 0L || failures < 0L
                || attempts != successes + failures) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism adapter telemetry drifted; attempts="
                            + attempts + ", successes=" + successes
                            + ", failures=" + failures));
        }
        if (failures != 0L) {
            FatalErrors.rethrowIfFatal(adapterRenderer.lastFailure);
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism adapter failed " + failures
                            + " time(s) inside an owner render path that may swallow exceptions",
                    adapterRenderer.lastFailure));
        }
        if (wrcbeAttempts < 0L || wrcbeSuccesses < 0L || wrcbeFailures < 0L
                || wrcbeAttempts != wrcbeSuccesses + wrcbeFailures) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator adapter telemetry drifted; attempts="
                            + wrcbeAttempts + ", successes=" + wrcbeSuccesses
                            + ", failures=" + wrcbeFailures));
        }
        if (wrcbeFailures != 0L) {
            FatalErrors.rethrowIfFatal(adapterRenderer.wrcbeLastFailure);
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator adapter failed " + wrcbeFailures
                            + " time(s) inside an owner render path that may swallow exceptions",
                    adapterRenderer.wrcbeLastFailure));
        }
        rethrow(failure);
        return new AdapterCounts(successes, wrcbeSuccesses);
    }

    private static TextureAtlasSprite verifyPinnedIcon(IIcon icon, String expectedName) {
        if (icon == null
                || !expectedName.equals(icon.getIconName())
                || icon.getIconWidth() != OWNER_ICON_WIDTH
                || icon.getIconHeight() != OWNER_ICON_HEIGHT
                || !(icon instanceof TextureAtlasSprite)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism owner icon contract drifted; got "
                            + describeIcon(icon));
        }
        TextureAtlasSprite sprite = (TextureAtlasSprite) icon;
        verifyPostStitchFrameState(sprite, expectedName);
        return sprite;
    }

    static void verifyPostStitchFrameState(TextureAtlasSprite sprite, String expectedName) {
        if (sprite == null || sprite.getFrameCount() != 0) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism non-animated sprite "
                            + expectedName + " retained "
                            + (sprite == null ? "<null>" : sprite.getFrameCount())
                            + " CPU frame(s) after stitching; expected TextureMap to upload and "
                            + "clear them before export");
        }
    }

    private static PinnedPixels loadPinnedPixels(
            IResourceManager resources,
            String iconName,
            String resourcePath,
            int expectedLength,
            String expectedSha256,
            int expectedVisibleBasePixels) throws IOException {
        ResourceLocation location = new ResourceLocation("botania", resourcePath);
        IResource resource = resources.getResource(location);
        if (resource == null || resource.hasMetadata()) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned Botania prism resource metadata drifted for "
                            + location);
        }
        byte[] bytes;
        InputStream input = resource.getInputStream();
        if (input == null) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned Botania prism resource stream is unavailable for "
                            + location);
        }
        try {
            bytes = readExactPinnedResource(input, expectedLength, expectedSha256);
        } finally {
            input.close();
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null
                || image.getWidth() != OWNER_ICON_WIDTH
                || image.getHeight() != OWNER_ICON_HEIGHT) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned Botania prism PNG dimensions drifted for "
                            + location + "; got "
                            + (image == null
                                    ? "<undecodable>"
                                    : image.getWidth() + "x" + image.getHeight()));
        }
        int[] base = image.getRGB(
                0, 0, OWNER_ICON_WIDTH, OWNER_ICON_HEIGHT,
                null, 0, OWNER_ICON_WIDTH);
        fixTransparentPixelsLikeTextureAtlasSprite(base);
        int[] mipOne = generateLegacyTransparentMip(base, OWNER_ICON_WIDTH, OWNER_ICON_HEIGHT);
        verifyPinnedPixelPredicate(
                iconName, base, mipOne, expectedVisibleBasePixels);
        return new PinnedPixels(iconName, base, mipOne);
    }

    static byte[] readExactPinnedResource(
            InputStream input, int expectedLength, String expectedSha256) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("pinned resource input is required");
        }
        if (expectedLength <= 0 || expectedLength > 64 * 1024) {
            throw new IllegalArgumentException(
                    "pinned resource length is outside the bounded PNG range: "
                            + expectedLength);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedLength);
        byte[] buffer = new byte[Math.min(4096, expectedLength + 1)];
        int remaining = expectedLength + 1;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count < 0) {
                break;
            }
            if (count == 0) {
                int single = input.read();
                if (single < 0) {
                    break;
                }
                output.write(single);
                remaining--;
            } else {
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length != expectedLength) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned Botania prism PNG byte length drifted; expected "
                            + expectedLength + ", got " + bytes.length
                            + (bytes.length > expectedLength ? " or more" : ""));
        }
        String actualSha256 = sha256Hex(bytes);
        if (!expectedSha256.equals(actualSha256)) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned Botania prism PNG SHA-256 drifted; expected "
                            + expectedSha256 + ", got " + actualSha256);
        }
        return bytes;
    }

    static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("The JVM does not provide SHA-256", impossible);
        }
    }

    private static void fixTransparentPixelsLikeTextureAtlasSprite(int[] pixels) {
        int visible = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int pixel : pixels) {
            if ((pixel & 0xff000000) != 0) {
                red += pixel >>> 16 & 0xff;
                green += pixel >>> 8 & 0xff;
                blue += pixel & 0xff;
                visible++;
            }
        }
        if (visible == 0) {
            return;
        }
        int transparentRgb = red / visible << 16
                | green / visible << 8
                | blue / visible;
        for (int index = 0; index < pixels.length; index++) {
            if ((pixels[index] & 0xff000000) == 0) {
                pixels[index] = transparentRgb;
            }
        }
    }

    static int[] generateLegacyTransparentMip(int[] base, int width, int height) {
        if (base == null
                || width <= 0 || height <= 0
                || (width & 1) != 0 || (height & 1) != 0
                || base.length != width * height) {
            throw new IllegalArgumentException("invalid transparent mip source");
        }
        int mipWidth = width / 2;
        int mipHeight = height / 2;
        int[] mip = new int[mipWidth * mipHeight];
        for (int y = 0; y < mipHeight; y++) {
            for (int x = 0; x < mipWidth; x++) {
                int source = 2 * (x + y * width);
                mip[x + y * mipWidth] = legacyTransparentMipmapPixel(
                        base[source], base[source + 1],
                        base[source + width], base[source + width + 1]);
            }
        }
        return mip;
    }

    private static int legacyTransparentMipmapPixel(int a, int b, int c, int d) {
        int alpha = gammaAverageVisibleComponent(a, b, c, d, 24);
        int red = gammaAverageVisibleComponent(a, b, c, d, 16);
        int green = gammaAverageVisibleComponent(a, b, c, d, 8);
        int blue = gammaAverageVisibleComponent(a, b, c, d, 0);
        if (alpha < LEGACY_TRANSPARENT_MIP_ALPHA_CUTOFF) {
            alpha = 0;
        }
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int gammaAverageVisibleComponent(
            int a, int b, int c, int d, int shift) {
        int[] pixels = {a, b, c, d};
        float sum = 0.0F;
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                int component = pixel >>> shift & 0xff;
                sum += (float) Math.pow(component / 255.0F, 2.2D);
            }
        }
        return (int) (Math.pow(sum / 4.0F, 1.0D / 2.2D) * 255.0D);
    }

    private static void verifyPinnedPixelPredicate(
            String iconName,
            int[] base,
            int[] mipOne,
            int expectedVisibleBasePixels) {
        int visibleBasePixels = 0;
        int maximumBaseAlpha = 0;
        for (int pixel : base) {
            int alpha = pixel >>> 24;
            if (alpha != 0) {
                visibleBasePixels++;
            }
            maximumBaseAlpha = Math.max(maximumBaseAlpha, alpha);
        }
        int visibleLevelOnePixels = 0;
        for (int pixel : mipOne) {
            if ((pixel >>> 24) != 0) {
                visibleLevelOnePixels++;
            }
        }
        if (base.length != OWNER_ICON_WIDTH * OWNER_ICON_HEIGHT
                || mipOne.length != OWNER_ICON_WIDTH / 2 * (OWNER_ICON_HEIGHT / 2)
                || visibleBasePixels != expectedVisibleBasePixels
                || maximumBaseAlpha != OWNER_BASE_MAX_ALPHA
                || visibleLevelOnePixels != 0) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism resolved-resource mip predicate "
                            + "drifted for " + iconName + "; baseVisible="
                            + visibleBasePixels + ", baseMaxAlpha=" + maximumBaseAlpha
                            + ", levelOneVisible=" + visibleLevelOnePixels);
        }
    }

    static final class PinnedPixels {
        final String iconName;
        final int[] base;
        final int[] mipOne;

        PinnedPixels(String iconName, int[] base, int[] mipOne) {
            this.iconName = iconName;
            this.base = base;
            this.mipOne = mipOne;
        }
    }

    static int legacyTransparentMipmapAlpha(int a, int b, int c, int d) {
        float sum = gammaAlpha(a) + gammaAlpha(b) + gammaAlpha(c) + gammaAlpha(d);
        int generated = (int) (Math.pow(sum / 4.0F, 1.0D / 2.2D) * 255.0D);
        return generated < LEGACY_TRANSPARENT_MIP_ALPHA_CUTOFF ? 0 : generated;
    }

    static int legacyTransparentMipmapAlphaBeforeCutoff(int a, int b, int c, int d) {
        float sum = gammaAlpha(a) + gammaAlpha(b) + gammaAlpha(c) + gammaAlpha(d);
        return (int) (Math.pow(sum / 4.0F, 1.0D / 2.2D) * 255.0D);
    }

    private static float gammaAlpha(int alpha) {
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("alpha must be in [0,255]");
        }
        return alpha == 0
                ? 0.0F
                : (float) Math.pow(alpha / 255.0F, 2.2D);
    }

    private static String describeIcon(IIcon icon) {
        if (icon == null) {
            return "<null>";
        }
        return icon.getClass().getName() + " name=" + icon.getIconName()
                + " size=" + icon.getIconWidth() + "x" + icon.getIconHeight();
    }

    private static Throwable merge(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (additional != primary) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        FatalErrors.rethrowIfFatal(failure);
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        throw new IllegalStateException(
                "ITEM_ICON_RENDER: Botania prism adapter failed", failure);
    }

    interface PreClampVerifier {
        void verify() throws Exception;
    }

    static final class RuntimeAtlasVerifier implements PreClampVerifier {
        private final Minecraft minecraft;
        private final TextureMap blockAtlas;
        private final Block block;
        private final TextureAtlasSprite ownerSprite;
        private final TextureAtlasSprite ownerSideSprite;
        private final int atlasTextureId;
        private final SpriteRegion ownerRegion;
        private final SpriteRegion ownerSideRegion;
        private final AtlasReadbackAccess readback;

        RuntimeAtlasVerifier(
                Minecraft minecraft,
                TextureMap blockAtlas,
                Block block,
                TextureAtlasSprite ownerSprite,
                TextureAtlasSprite ownerSideSprite,
                int atlasTextureId,
                PinnedPixels ownerPixels,
                PinnedPixels ownerSidePixels,
                AtlasReadbackAccess readback) {
            this.minecraft = minecraft;
            this.blockAtlas = blockAtlas;
            this.block = block;
            this.ownerSprite = ownerSprite;
            this.ownerSideSprite = ownerSideSprite;
            this.atlasTextureId = atlasTextureId;
            this.ownerRegion = new SpriteRegion(ownerSprite, ownerPixels);
            this.ownerSideRegion = new SpriteRegion(ownerSideSprite, ownerSidePixels);
            this.readback = readback;
        }

        @Override
        public void verify() throws Exception {
            Minecraft current = Minecraft.getMinecraft();
            if (current != minecraft
                    || current.getTextureMapBlocks() != blockAtlas
                    || current.getTextureManager().getTexture(
                            TextureMap.locationBlocksTexture) != blockAtlas
                    || blockAtlas.getGlTextureId() != atlasTextureId
                    || blockAtlas.getAtlasSprite(OWNER_ICON_NAME) != ownerSprite
                    || blockAtlas.getAtlasSprite(OWNER_SIDE_ICON_NAME) != ownerSideSprite
                    || block.getIcon(1, 0) != ownerSprite
                    || block.getIcon(2, 0) != ownerSideSprite) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: canonical Botania prism live-atlas ownership drifted "
                                + "before its owner draw");
            }
            verifyPostStitchFrameState(ownerSprite, OWNER_ICON_NAME);
            verifyPostStitchFrameState(ownerSideSprite, OWNER_SIDE_ICON_NAME);
            verifyLiveAtlas(readback, atlasTextureId, ownerRegion, ownerSideRegion);
        }
    }

    static final class SpriteRegion {
        final String iconName;
        final int originX;
        final int originY;
        final int width;
        final int height;
        final int[] base;
        final int[] mipOne;

        SpriteRegion(TextureAtlasSprite sprite, PinnedPixels pixels) {
            this(
                    pixels.iconName,
                    sprite.getOriginX(), sprite.getOriginY(),
                    sprite.getIconWidth(), sprite.getIconHeight(),
                    pixels.base, pixels.mipOne);
        }

        SpriteRegion(
                String iconName,
                int originX,
                int originY,
                int width,
                int height,
                int[] base,
                int[] mipOne) {
            this.iconName = iconName;
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
            this.base = base;
            this.mipOne = mipOne;
        }
    }

    interface AtlasReadbackAccess {
        boolean framebufferSupported();
        boolean textureExists(int textureId);
        int textureParameter(int parameter);
        int textureLevelParameter(int level, int parameter);
        int framebufferBinding();
        int readBuffer();
        int generateFramebuffer();
        void bindFramebuffer(int framebuffer);
        void attachTexture(int textureId, int level);
        int framebufferStatus();
        void readBuffer(int buffer);
        int pixelStore(int parameter);
        void pixelStore(int parameter, int value);
        int[] readPixels(int x, int y, int width, int height);
        void deleteFramebuffer(int framebuffer);
    }

    static final class GlAtlasReadbackAccess implements AtlasReadbackAccess {
        @Override
        public boolean framebufferSupported() {
            return OpenGlHelper.framebufferSupported;
        }

        @Override
        public boolean textureExists(int textureId) {
            return GL11.glIsTexture(textureId);
        }

        @Override
        public int textureParameter(int parameter) {
            return GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, parameter);
        }

        @Override
        public int textureLevelParameter(int level, int parameter) {
            return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, parameter);
        }

        @Override
        public int framebufferBinding() {
            return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        }

        @Override
        public int readBuffer() {
            return GL11.glGetInteger(GL11.GL_READ_BUFFER);
        }

        @Override
        public int generateFramebuffer() {
            return OpenGlHelper.func_153165_e();
        }

        @Override
        public void bindFramebuffer(int framebuffer) {
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
        }

        @Override
        public void attachTexture(int textureId, int level) {
            OpenGlHelper.func_153188_a(
                    OpenGlHelper.field_153198_e,
                    OpenGlHelper.field_153200_g,
                    GL11.GL_TEXTURE_2D,
                    textureId,
                    level);
        }

        @Override
        public int framebufferStatus() {
            return OpenGlHelper.func_153167_i(OpenGlHelper.field_153198_e);
        }

        @Override
        public void readBuffer(int buffer) {
            GL11.glReadBuffer(buffer);
        }

        @Override
        public int pixelStore(int parameter) {
            return GL11.glGetInteger(parameter);
        }

        @Override
        public void pixelStore(int parameter, int value) {
            GL11.glPixelStorei(parameter, value);
        }

        @Override
        public int[] readPixels(int x, int y, int width, int height) {
            int count = Math.multiplyExact(width, height);
            IntBuffer buffer = BufferUtils.createIntBuffer(count);
            Buffer state = buffer;
            state.clear();
            state.limit(count);
            GL11.glReadPixels(
                    x, y, width, height,
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            int[] pixels = new int[count];
            for (int index = 0; index < count; index++) {
                pixels[index] = buffer.get(index);
            }
            return pixels;
        }

        @Override
        public void deleteFramebuffer(int framebuffer) {
            OpenGlHelper.func_153174_h(framebuffer);
        }
    }

    static void verifyLiveAtlas(
            AtlasReadbackAccess readback,
            int atlasTextureId,
            SpriteRegion owner,
            SpriteRegion side) throws Exception {
        if (readback == null || owner == null || side == null) {
            throw new IllegalArgumentException("live Botania prism atlas verifier is required");
        }
        if (!readback.framebufferSupported() || !readback.textureExists(atlasTextureId)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: canonical block atlas does not support exact live FBO readback");
        }
        int baseLevel = readback.textureParameter(GL12.GL_TEXTURE_BASE_LEVEL);
        int minificationFilter = readback.textureParameter(GL11.GL_TEXTURE_MIN_FILTER);
        if (baseLevel != 0 || !isMipmapMinificationFilter(minificationFilter)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism atlas sampling predicate drifted; baseLevel="
                            + baseLevel + ", minFilter=" + minificationFilter);
        }
        int levelZeroWidth = readback.textureLevelParameter(0, GL11.GL_TEXTURE_WIDTH);
        int levelZeroHeight = readback.textureLevelParameter(0, GL11.GL_TEXTURE_HEIGHT);
        int levelOneWidth = readback.textureLevelParameter(1, GL11.GL_TEXTURE_WIDTH);
        int levelOneHeight = readback.textureLevelParameter(1, GL11.GL_TEXTURE_HEIGHT);
        verifyRegionBounds(owner, levelZeroWidth, levelZeroHeight, levelOneWidth, levelOneHeight);
        verifyRegionBounds(side, levelZeroWidth, levelZeroHeight, levelOneWidth, levelOneHeight);

        int oldFramebuffer = readback.framebufferBinding();
        int oldReadBuffer = readback.readBuffer();
        int[] oldPackState = new int[PACK_STATE_NAMES.length];
        for (int index = 0; index < PACK_STATE_NAMES.length; index++) {
            oldPackState[index] = readback.pixelStore(PACK_STATE_NAMES[index]);
        }

        int framebuffer = 0;
        int[][] observed = null;
        Throwable failure = null;
        try {
            framebuffer = readback.generateFramebuffer();
            if (framebuffer <= 0) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: live Botania prism atlas readback could not allocate an FBO");
            }
            readback.bindFramebuffer(framebuffer);
            readback.readBuffer(OpenGlHelper.field_153200_g);
            establishReadbackPackState(readback);

            observed = new int[4][];
            readback.attachTexture(atlasTextureId, 0);
            verifyReadbackFramebuffer(readback, "level zero");
            observed[0] = readback.readPixels(
                    owner.originX, owner.originY, owner.width, owner.height);
            observed[1] = readback.readPixels(
                    side.originX, side.originY, side.width, side.height);

            readback.attachTexture(atlasTextureId, 1);
            verifyReadbackFramebuffer(readback, "level one");
            observed[2] = readback.readPixels(
                    owner.originX >> 1, owner.originY >> 1,
                    owner.width >> 1, owner.height >> 1);
            observed[3] = readback.readPixels(
                    side.originX >> 1, side.originY >> 1,
                    side.width >> 1, side.height >> 1);
        } catch (Throwable error) {
            failure = error;
        } finally {
            for (int index = 0; index < PACK_STATE_NAMES.length; index++) {
                try {
                    readback.pixelStore(PACK_STATE_NAMES[index], oldPackState[index]);
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            try {
                readback.bindFramebuffer(oldFramebuffer);
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            try {
                readback.readBuffer(oldReadBuffer);
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            try {
                if (readback.framebufferBinding() != oldFramebuffer
                        || readback.readBuffer() != oldReadBuffer) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania prism live-atlas framebuffer/read-buffer "
                                    + "restore was not exact"));
                }
                for (int index = 0; index < PACK_STATE_NAMES.length; index++) {
                    if (readback.pixelStore(PACK_STATE_NAMES[index]) != oldPackState[index]) {
                        failure = merge(failure, new IllegalStateException(
                                "ITEM_ICON_RENDER: Botania prism live-atlas pixel-pack restore "
                                        + "was not exact for 0x"
                                        + Integer.toHexString(PACK_STATE_NAMES[index])));
                    }
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            if (framebuffer > 0) {
                try {
                    readback.deleteFramebuffer(framebuffer);
                } catch (Throwable cleanup) {
                    failure = merge(failure, cleanup);
                }
            }
        }
        rethrow(failure);
        verifyExactPixels(owner.iconName + " level zero", owner.base, observed[0]);
        verifyExactPixels(side.iconName + " level zero", side.base, observed[1]);
        verifyExactPixels(owner.iconName + " level one", owner.mipOne, observed[2]);
        verifyExactPixels(side.iconName + " level one", side.mipOne, observed[3]);
    }

    private static boolean isMipmapMinificationFilter(int filter) {
        return filter == GL11.GL_NEAREST_MIPMAP_NEAREST
                || filter == GL11.GL_LINEAR_MIPMAP_NEAREST
                || filter == GL11.GL_NEAREST_MIPMAP_LINEAR
                || filter == GL11.GL_LINEAR_MIPMAP_LINEAR;
    }

    private static void verifyRegionBounds(
            SpriteRegion region,
            int levelZeroWidth,
            int levelZeroHeight,
            int levelOneWidth,
            int levelOneHeight) {
        if (region.width != OWNER_ICON_WIDTH
                || region.height != OWNER_ICON_HEIGHT
                || region.originX < 0 || region.originY < 0
                || (region.originX & 1) != 0 || (region.originY & 1) != 0
                || region.originX + region.width > levelZeroWidth
                || region.originY + region.height > levelZeroHeight
                || (region.originX >> 1) + (region.width >> 1) > levelOneWidth
                || (region.originY >> 1) + (region.height >> 1) > levelOneHeight) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania prism live-atlas bounds drifted for "
                            + region.iconName + "; origin=" + region.originX + ","
                            + region.originY + ", level0=" + levelZeroWidth + "x"
                            + levelZeroHeight + ", level1=" + levelOneWidth + "x"
                            + levelOneHeight);
        }
    }

    private static void establishReadbackPackState(AtlasReadbackAccess readback) {
        readback.pixelStore(GL11.GL_PACK_ALIGNMENT, 1);
        readback.pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
        readback.pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
        readback.pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
        readback.pixelStore(GL11.GL_PACK_SWAP_BYTES, 0);
        readback.pixelStore(GL11.GL_PACK_LSB_FIRST, 0);
    }

    private static void verifyReadbackFramebuffer(
            AtlasReadbackAccess readback, String level) {
        int status = readback.framebufferStatus();
        if (status != OpenGlHelper.field_153202_i
                || readback.readBuffer() != OpenGlHelper.field_153200_g) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism live-atlas " + level
                            + " FBO is incomplete or has the wrong read buffer; status=0x"
                            + Integer.toHexString(status) + ", readBuffer=0x"
                            + Integer.toHexString(readback.readBuffer()));
        }
    }

    private static void verifyExactPixels(String description, int[] expected, int[] actual) {
        if (actual == null || expected.length != actual.length) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania prism live-atlas pixel count drifted for "
                            + description);
        }
        for (int index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Botania prism live-atlas pixels drifted for "
                                + description + " at index " + index + "; expected=0x"
                                + Integer.toHexString(expected[index]) + ", got=0x"
                                + Integer.toHexString(actual[index]));
            }
        }
    }

    interface TextureStateAccess {
        int activeTexture();
        void activeTexture(int textureUnit);
        int boundTexture2d();
        void bindTexture2d(int textureId);
        int maximumMipLevel();
        void maximumMipLevel(int level);
    }

    static final class GlTextureStateAccess implements TextureStateAccess {
        @Override
        public int activeTexture() {
            return GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        }

        @Override
        public void activeTexture(int textureUnit) {
            OpenGlHelper.setActiveTexture(textureUnit);
        }

        @Override
        public int boundTexture2d() {
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        @Override
        public void bindTexture2d(int textureId) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }

        @Override
        public int maximumMipLevel() {
            return GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
        }

        @Override
        public void maximumMipLevel(int level) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, level);
        }
    }

    static final class AtlasBaseLevelLease {
        private final TextureStateAccess textures;
        private final int atlasTextureId;
        private final Thread renderThread;
        private final PreClampVerifier preClampVerifier;
        private boolean active;

        AtlasBaseLevelLease(
                TextureStateAccess textures,
                int atlasTextureId,
                Thread renderThread,
                PreClampVerifier preClampVerifier) {
            if (textures == null || renderThread == null || preClampVerifier == null) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: complete Botania prism atlas lease dependencies are required");
            }
            this.textures = textures;
            this.atlasTextureId = atlasTextureId;
            this.renderThread = renderThread;
            this.preClampVerifier = preClampVerifier;
        }

        synchronized void draw(OffscreenRenderer.DrawCall ownerDraw) throws Exception {
            if (ownerDraw == null) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Botania prism atlas draw is required");
            }
            if (Thread.currentThread() != renderThread) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Botania prism atlas lease left its pinned thread");
            }
            if (active) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: nested Botania prism atlas leases are forbidden");
            }

            int oldActiveTexture = 0;
            int oldActiveBinding = 0;
            int oldDefaultBinding = 0;
            int oldMaximumMipLevel = 0;
            boolean capturedActive = false;
            boolean capturedActiveBinding = false;
            boolean capturedDefaultBinding = false;
            boolean capturedMaximumMipLevel = false;
            boolean clampAttempted = false;
            Throwable failure = null;
            try {
                oldActiveTexture = textures.activeTexture();
                capturedActive = true;
                oldActiveBinding = textures.boundTexture2d();
                capturedActiveBinding = true;
                textures.activeTexture(OpenGlHelper.defaultTexUnit);
                oldDefaultBinding = textures.boundTexture2d();
                capturedDefaultBinding = true;
                textures.bindTexture2d(atlasTextureId);
                oldMaximumMipLevel = textures.maximumMipLevel();
                capturedMaximumMipLevel = true;
                active = true;
                if (oldMaximumMipLevel < 1) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania prism failure predicate drifted; block "
                                    + "atlas maximum mip level is " + oldMaximumMipLevel);
                }

                preClampVerifier.verify();
                if (textures.activeTexture() != OpenGlHelper.defaultTexUnit
                        || textures.boundTexture2d() != atlasTextureId
                        || textures.maximumMipLevel() != oldMaximumMipLevel) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania prism live-atlas verifier changed its "
                                    + "leased texture state");
                }
                clampAttempted = true;
                textures.maximumMipLevel(0);
                if (textures.maximumMipLevel() != 0) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania prism base-level mip clamp was not applied");
                }
                ownerDraw.draw();
            } catch (Throwable error) {
                failure = error;
            } finally {
                if (capturedMaximumMipLevel) {
                    try {
                        textures.activeTexture(OpenGlHelper.defaultTexUnit);
                        textures.bindTexture2d(atlasTextureId);
                        if (clampAttempted && textures.maximumMipLevel() != 0) {
                            failure = merge(failure, new IllegalStateException(
                                    "ITEM_ICON_RENDER: Botania prism owner draw changed the "
                                            + "leased atlas maximum mip level"));
                        }
                        textures.maximumMipLevel(oldMaximumMipLevel);
                        if (textures.maximumMipLevel() != oldMaximumMipLevel) {
                            failure = merge(failure, new IllegalStateException(
                                    "ITEM_ICON_RENDER: Botania prism atlas maximum mip restore "
                                            + "was not exact"));
                        }
                    } catch (Throwable restore) {
                        failure = merge(failure, restore);
                    }
                }
                if (capturedDefaultBinding) {
                    try {
                        textures.activeTexture(OpenGlHelper.defaultTexUnit);
                        textures.bindTexture2d(oldDefaultBinding);
                        if (textures.boundTexture2d() != oldDefaultBinding) {
                            failure = merge(failure, new IllegalStateException(
                                    "ITEM_ICON_RENDER: Botania prism default-unit texture "
                                            + "binding restore was not exact"));
                        }
                    } catch (Throwable restore) {
                        failure = merge(failure, restore);
                    }
                }
                if (capturedActive) {
                    try {
                        textures.activeTexture(oldActiveTexture);
                        if (capturedActiveBinding) {
                            textures.bindTexture2d(oldActiveBinding);
                            if (textures.boundTexture2d() != oldActiveBinding) {
                                failure = merge(failure, new IllegalStateException(
                                        "ITEM_ICON_RENDER: Botania prism original-unit texture "
                                                + "binding restore was not exact"));
                            }
                        }
                        if (textures.activeTexture() != oldActiveTexture) {
                            failure = merge(failure, new IllegalStateException(
                                    "ITEM_ICON_RENDER: Botania prism active texture restore "
                                            + "was not exact"));
                        }
                    } catch (Throwable restore) {
                        failure = merge(failure, restore);
                    }
                }
                active = false;
            }
            rethrow(failure);
        }

    }

    static final class BaseLevelRenderItem extends RenderItem {
        private final RenderItem ownerRenderer;
        private final AtlasBaseLevelLease atlasLease;
        private final Thread renderThread;
        long attempts;
        long successes;
        long failures;
        Throwable lastFailure;
        private WrcbeTriangulatorIconRenderer wrcbeTriangulatorRenderer;
        long wrcbeAttempts;
        long wrcbeSuccesses;
        long wrcbeFailures;
        Throwable wrcbeLastFailure;

        BaseLevelRenderItem(RenderItem ownerRenderer, AtlasBaseLevelLease atlasLease) {
            this.ownerRenderer = ownerRenderer;
            this.atlasLease = atlasLease;
            this.renderThread = Thread.currentThread();
            copyStateFrom(ownerRenderer);
        }

        void copyStateFrom(RenderItem source) {
            zLevel = source.zLevel;
            renderWithColor = source.renderWithColor;
        }

        void copyStateTo(RenderItem destination) {
            destination.zLevel = zLevel;
            destination.renderWithColor = renderWithColor;
        }

        void attachWrcbeTriangulator(WrcbeTriangulatorIconRenderer renderer) {
            if (wrcbeTriangulatorRenderer != null) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: WR-CBE triangulator adapter was attached twice");
            }
            wrcbeTriangulatorRenderer = renderer;
        }

        @Override
        public void renderItemAndEffectIntoGUI(
                final FontRenderer fontRenderer,
                final TextureManager textureManager,
                final ItemStack stack,
                final int x,
                final int y) {
            invoke(stack, new Runnable() {
                @Override
                public void run() {
                    ownerRenderer.renderItemAndEffectIntoGUI(
                            fontRenderer, textureManager, stack, x, y);
                }
            });
        }

        @Override
        public void renderItemIntoGUI(
                FontRenderer fontRenderer, TextureManager textureManager,
                ItemStack stack, int x, int y) {
            renderItemIntoGUI(fontRenderer, textureManager, stack, x, y, false);
        }

        @Override
        public void renderItemIntoGUI(
                final FontRenderer fontRenderer,
                final TextureManager textureManager,
                final ItemStack stack,
                final int x,
                final int y,
                final boolean renderEffect) {
            invoke(stack, new Runnable() {
                @Override
                public void run() {
                    ownerRenderer.renderItemIntoGUI(
                            fontRenderer, textureManager, stack, x, y, renderEffect);
                }
            });
        }

        @Override
        public void renderItemOverlayIntoGUI(
                FontRenderer fontRenderer, TextureManager textureManager,
                ItemStack stack, int x, int y) {
            copyStateTo(ownerRenderer);
            try {
                ownerRenderer.renderItemOverlayIntoGUI(
                        fontRenderer, textureManager, stack, x, y);
            } finally {
                copyStateFrom(ownerRenderer);
            }
        }

        @Override
        public void renderItemOverlayIntoGUI(
                FontRenderer fontRenderer, TextureManager textureManager,
                ItemStack stack, int x, int y, String quantity) {
            copyStateTo(ownerRenderer);
            try {
                ownerRenderer.renderItemOverlayIntoGUI(
                        fontRenderer, textureManager, stack, x, y, quantity);
            } finally {
                copyStateFrom(ownerRenderer);
            }
        }

        private void invoke(final ItemStack stack, final Runnable ownerDraw) {
            if (Thread.currentThread() != renderThread) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Botania prism RenderItem adapter left its pinned thread");
            }
            copyStateTo(ownerRenderer);
            try {
                if (StackIdentity.isPinnedWrcbeTriangulatorRenderTarget(stack)) {
                    if (wrcbeTriangulatorRenderer == null) {
                        throw new IllegalStateException(
                                "ITEM_ICON_RENDER: WR-CBE triangulator reached the shared "
                                        + "RenderItem adapter before its owner texture policy "
                                        + "was attached");
                    }
                    wrcbeAttempts++;
                    Throwable failure = null;
                    try {
                        wrcbeTriangulatorRenderer.refreshOwnerTextureAndDraw(ownerDraw);
                    } catch (Throwable error) {
                        failure = error;
                    }
                    if (failure != null) {
                        wrcbeFailures++;
                        wrcbeLastFailure = failure;
                        FatalErrors.rethrowIfFatal(failure);
                        throw new IllegalStateException(
                                "ITEM_ICON_RENDER: WR-CBE triangulator owner texture refresh "
                                        + "or RenderItem draw failed", failure);
                    }
                    wrcbeSuccesses++;
                    return;
                }
                if (!StackIdentity.isPinnedBotaniaPrismIconTarget(stack)) {
                    ownerDraw.run();
                    return;
                }
                attempts++;
                Throwable failure = null;
                try {
                    atlasLease.draw(new OffscreenRenderer.DrawCall() {
                        @Override
                        public void draw() {
                            ownerDraw.run();
                        }
                    });
                } catch (Throwable error) {
                    failure = error;
                }
                if (failure != null) {
                    failures++;
                    lastFailure = failure;
                    FatalErrors.rethrowIfFatal(failure);
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania prism owner RenderItem failed", failure);
                }
                successes++;
            } finally {
                copyStateFrom(ownerRenderer);
            }
        }
    }
}
