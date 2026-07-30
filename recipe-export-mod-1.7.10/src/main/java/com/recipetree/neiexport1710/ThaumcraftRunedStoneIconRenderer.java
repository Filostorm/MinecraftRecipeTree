package com.recipetree.neiexport1710;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Exact catalog-icon projection for Thaumcraft's Runed Stone recipe identity.
 *
 * <p>Thaumcraft 4.2.3.5a's {@code BlockEldritchRenderer.renderInventoryBlock}
 * draws only metadata 4, 5, and 6. Metadata 10 is nevertheless a valid Runed Stone world block,
 * is referenced by the live infusion corpus, and exposes the owner's canonical
 * {@code thaumcraft:es_5} inventory icon through {@code Block.getIcon(side, metadata)}. The
 * ordinary inventory path therefore produces a fully transparent image. This adapter draws that
 * exact resolved owner atlas sprite face-on. It does not invent pixels, mutate the owner renderer,
 * or broaden the policy to another metadata value.</p>
 */
final class ThaumcraftRunedStoneIconRenderer {
    static final String CONTRACT =
            "thaumcraft-runed-stone-meta10-owner-atlas-face-on-catalog-v1";
    static final int EXPECTED_ITEM_ICONS = 1;
    static final String REGISTRY_ID = "Thaumcraft:blockEldritch";
    static final String ITEM_CLASS = "thaumcraft.common.blocks.BlockEldritchItem";
    static final String BLOCK_CLASS = "thaumcraft.common.blocks.BlockEldritch";
    static final String BLOCK_RENDERER_CLASS =
            "thaumcraft.client.renderers.block.BlockEldritchRenderer";
    static final int METADATA = 10;
    static final String ICON_NAME = "thaumcraft:es_5";
    static final String DEFAULT_ITEM_ICON_NAME = "thaumcraft:obsidiantile";
    static final String RESOURCE_PATH = "textures/blocks/es_5.png";
    static final int RESOURCE_BYTES = 781;
    static final String RESOURCE_SHA256 =
            "5269a9d75f4d5e95d44efbb1537e9f66866061436cbf2e49b3a4b2844ec3298e";
    static final String OWNER_ARGB_SHA256 =
            "4e6a681e782660126af3f84a7d3eaedb56dc0d3877a542c835c5956b49326385";
    static final int OWNER_WIDTH = 16;
    static final int OWNER_HEIGHT = 16;
    static final int OWNER_VISIBLE_PIXELS = 256;

    private final Minecraft minecraft;
    private final TextureMap blockAtlas;
    private final RenderItem ownerRenderItem;
    private final Thread renderThread;
    private boolean ownerResourceVerified;

    private ThaumcraftRunedStoneIconRenderer(
            Minecraft minecraft, TextureMap blockAtlas, RenderItem ownerRenderItem) {
        this.minecraft = minecraft;
        this.blockAtlas = blockAtlas;
        this.ownerRenderItem = ownerRenderItem;
        this.renderThread = Thread.currentThread();
    }

    static ThaumcraftRunedStoneIconRenderer create() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null
                || minecraft.getResourceManager() == null
                || minecraft.getTextureMapBlocks() == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner texture runtime is unavailable");
        }
        RenderItem renderItem = GuiContainerManager.drawItems;
        if (renderItem == null || renderItem.getClass() != RenderItem.class) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone requires the exact NEI owner "
                            + "RenderItem; got "
                            + (renderItem == null ? "<null>" : renderItem.getClass().getName()));
        }
        return new ThaumcraftRunedStoneIconRenderer(
                minecraft, minecraft.getTextureMapBlocks(), renderItem);
    }

    void draw(ItemStack stack) throws Exception {
        if (Thread.currentThread() != renderThread || !minecraft.func_152345_ab()) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone adapter left Minecraft's pinned "
                            + "client thread");
        }
        requireTarget(stack);
        Block block = Block.getBlockFromItem(stack.getItem());
        int renderType = block.getRenderType();
        ISimpleBlockRenderingHandler renderer =
                StackIdentity.pinnedBlockRenderer(renderType, BLOCK_RENDERER_CLASS);
        if (renderer.getRenderId() != renderType
                || !renderer.shouldRender3DInInventory(renderType)
                || !RenderingRegistry.instance().renderItemAsFull3DBlock(renderType)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner 3-D inventory renderer "
                            + "topology drifted");
        }
        if (MinecraftForgeClient.getItemRenderer(
                stack, IItemRenderer.ItemRenderType.INVENTORY) != null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone unexpectedly acquired a Forge "
                            + "item renderer");
        }

        IIcon icon = block.getIcon(0, METADATA);
        for (int side = 1; side < 6; side++) {
            if (block.getIcon(side, METADATA) != icon) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone canonical metadata icon "
                                + "varies by inventory side");
            }
        }
        if (!(icon instanceof TextureAtlasSprite)
                || !ICON_NAME.equals(icon.getIconName())
                || icon.getIconWidth() != OWNER_WIDTH
                || icon.getIconHeight() != OWNER_HEIGHT) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone exact metadata-10 owner icon "
                            + "drifted; got " + describeIcon(icon));
        }
        IIcon stitchedOwnerIcon = blockAtlas.getAtlasSprite(ICON_NAME);
        if (stitchedOwnerIcon != icon) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone exact metadata-10 stitched "
                            + "owner-sprite identity drifted; block=" + describeIcon(icon)
                            + ", atlas=" + describeIcon(stitchedOwnerIcon));
        }

        // BlockEldritchItem inherits ItemBlock#getIconFromDamage, which intentionally ignores
        // stack damage and resolves Block#getBlockTextureFromSide(1), i.e. side 1, metadata 0.
        // Pin that independent owner behavior so the face-on metadata-10 projection cannot hide
        // a future item-class or texture-routing change.
        IIcon metadataZeroIcon = block.getIcon(1, 0);
        IIcon inheritedItemBlockIcon = block.getBlockTextureFromSide(1);
        IIcon stackIcon = stack.getIconIndex();
        if (!(metadataZeroIcon instanceof TextureAtlasSprite)
                || !DEFAULT_ITEM_ICON_NAME.equals(metadataZeroIcon.getIconName())
                || metadataZeroIcon.getIconWidth() != OWNER_WIDTH
                || metadataZeroIcon.getIconHeight() != OWNER_HEIGHT
                || blockAtlas.getAtlasSprite(DEFAULT_ITEM_ICON_NAME) != metadataZeroIcon) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone exact inherited metadata-zero "
                            + "ItemBlock owner-sprite binding drifted; got "
                            + describeIcon(metadataZeroIcon));
        }
        if (inheritedItemBlockIcon != metadataZeroIcon
                || stackIcon != inheritedItemBlockIcon
                || stackIcon == icon) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone ItemStack icon no longer resolves "
                            + "only the exact inherited metadata-zero ItemBlock owner sprite; "
                            + "metadata-zero=" + describeIcon(metadataZeroIcon)
                            + ", inherited=" + describeIcon(inheritedItemBlockIcon)
                            + ", stack=" + describeIcon(stackIcon));
        }
        verifyOwnerResource();
        drawFaceOn(icon);
    }

    private static void requireTarget(ItemStack stack) {
        if (!StackIdentity.isPinnedThaumcraftRunedStoneIconTarget(stack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone adapter received a non-target "
                            + "stack: " + StackIdentity.describe(stack));
        }
    }

    private void verifyOwnerResource() throws Exception {
        if (ownerResourceVerified) {
            return;
        }
        IResource resource = minecraft.getResourceManager().getResource(
                new ResourceLocation("thaumcraft", RESOURCE_PATH));
        if (resource == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: missing resolved Thaumcraft owner resource "
                            + RESOURCE_PATH);
        }
        byte[] bytes;
        try (InputStream input = resource.getInputStream()) {
            bytes = readBounded(input, RESOURCE_BYTES + 1);
        }
        decodePinnedOwnerPng(
                bytes, RESOURCE_BYTES, RESOURCE_SHA256, OWNER_ARGB_SHA256);
        ownerResourceVerified = true;
    }

    private void drawFaceOn(IIcon icon) throws Exception {
        int oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int oldDefaultTexture = 0;
        boolean capturedDefaultTexture = false;
        boolean attributesPushed = false;
        boolean matrixPushed = false;
        Throwable failure = null;
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != OpenGlHelper.defaultTexUnit) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone could not select the default "
                                + "texture unit");
            }
            oldDefaultTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            capturedDefaultTexture = true;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attributesPushed = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            matrixPushed = true;
            minecraft.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.0F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            ownerRenderItem.renderIcon(0, 0, icon, OWNER_WIDTH, OWNER_HEIGHT);
        } catch (Throwable error) {
            failure = error;
        } finally {
            if (matrixPushed) {
                try {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            if (attributesPushed) {
                try {
                    GL11.glPopAttrib();
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            if (capturedDefaultTexture) {
                try {
                    OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldDefaultTexture);
                    if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                            != oldDefaultTexture) {
                        failure = merge(failure, new IllegalStateException(
                                "ITEM_ICON_RENDER: Thaumcraft Runed Stone default texture "
                                        + "binding restore was not exact"));
                    }
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            try {
                GL11.glMatrixMode(oldMatrixMode);
                if (GL11.glGetInteger(GL11.GL_MATRIX_MODE) != oldMatrixMode) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: Thaumcraft Runed Stone matrix-mode restore was "
                                    + "not exact"));
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            try {
                OpenGlHelper.setActiveTexture(oldActiveTexture);
                if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != oldActiveTexture) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: Thaumcraft Runed Stone active texture restore was "
                                    + "not exact"));
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
        }
        rethrow(failure);
    }

    static BufferedImage decodePinnedOwnerPng(
            byte[] bytes, int expectedBytes, String expectedSha256,
            String expectedArgbSha256) throws Exception {
        if (bytes == null || bytes.length != expectedBytes) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner PNG byte size drifted; "
                            + "expected " + expectedBytes + ", got "
                            + (bytes == null ? -1 : bytes.length));
        }
        String actualSha256 = sha256(bytes);
        if (!expectedSha256.equals(actualSha256)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner PNG SHA-256 drifted; "
                            + "expected " + expectedSha256 + ", got " + actualSha256);
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() != OWNER_WIDTH
                || image.getHeight() != OWNER_HEIGHT) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner PNG dimensions drifted");
        }
        MessageDigest pixels = MessageDigest.getInstance("SHA-256");
        int visible = 0;
        for (int y = 0; y < OWNER_HEIGHT; y++) {
            for (int x = 0; x < OWNER_WIDTH; x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) != 0xff) {
                    throw new IllegalArgumentException(
                            "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner pixel alpha drifted "
                                    + "at " + x + "," + y);
                }
                visible++;
                pixels.update((byte) (argb >>> 24));
                pixels.update((byte) (argb >>> 16));
                pixels.update((byte) (argb >>> 8));
                pixels.update((byte) argb);
            }
        }
        if (visible != OWNER_VISIBLE_PIXELS) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone visible owner pixel cardinality "
                            + "drifted; expected " + OWNER_VISIBLE_PIXELS + ", got " + visible);
        }
        String actualArgbSha256 = hex(pixels.digest());
        if (!expectedArgbSha256.equals(actualArgbSha256)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner ARGB SHA-256 drifted; "
                            + "expected " + expectedArgbSha256 + ", got " + actualArgbSha256);
        }
        return image;
    }

    private static byte[] readBounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) {
                continue;
            }
            if (output.size() + count > limit) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner resource exceeded its "
                                + "byte bound");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static String sha256(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte part : bytes) {
            value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        }
        return value.toString();
    }

    private static String describeIcon(IIcon icon) {
        if (icon == null) {
            return "<null>";
        }
        return icon.getClass().getName() + "(" + icon.getIconName() + ","
                + icon.getIconWidth() + "x" + icon.getIconHeight() + ")";
    }

    private static Throwable merge(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        throw new IllegalStateException(
                "ITEM_ICON_RENDER: Thaumcraft Runed Stone adapter failed", failure);
    }
}
