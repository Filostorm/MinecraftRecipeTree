package com.recipetree.neiexport1710;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Exact catalog-icon correction for ModernMarkings' six four-corner crossing sprites.
 *
 * <p>The owner floor renderer intentionally draws a horizontal world-space quad through the
 * legacy 3-D block-item projection. Each crossing texture contains only four opaque corner
 * texels, all of which disappear during the 16x16 projected inventory draw. This adapter keeps
 * the exact resolved owner atlas sprite and draws it face-on at one source texel per output pixel.
 * It neither synthesizes artwork nor broadens the policy to any other ModernMarkings block.</p>
 */
final class ModernMarkingsCrossingIconRenderer {
    static final String CONTRACT =
            "modernmarkings-four-corner-crossing-owner-atlas-face-on-catalog-v1";
    static final int EXPECTED_ITEM_ICONS = 6;
    static final String FLOOR_BLOCK_CLASS = "modernmarkings.blocks.MarkingFloor";
    static final String FLOOR_RENDERER_CLASS =
            "modernmarkings.renderer.MarkingFloorRenderer";
    private static final String MOD_ID = "modernmarkings";
    private static final int OWNER_WIDTH = 16;
    private static final int OWNER_HEIGHT = 16;
    private static final int OWNER_RESOURCE_BYTES = 216;

    static final class AssetPin {
        final String registryId;
        final String iconName;
        final String resourcePath;
        final String sha256;
        final int opaqueArgb;

        AssetPin(String color, String sha256, int opaqueArgb) {
            this.registryId = MOD_ID + ":tile.floor_marking_" + color + "_crossing";
            this.iconName = MOD_ID + ":marking_" + color + "_crossing";
            this.resourcePath = "textures/blocks/marking_" + color + "_crossing.png";
            this.sha256 = sha256;
            this.opaqueArgb = opaqueArgb;
        }
    }

    private static final Map<String, AssetPin> ASSETS_BY_REGISTRY_ID = assetPins();

    private final Minecraft minecraft;
    private final TextureMap blockAtlas;
    private final RenderItem ownerRenderItem;
    private final Thread renderThread;
    private final Map<String, BufferedImage> verifiedOwnerImages =
            new LinkedHashMap<String, BufferedImage>();

    private ModernMarkingsCrossingIconRenderer(
            Minecraft minecraft, TextureMap blockAtlas, RenderItem ownerRenderItem) {
        this.minecraft = minecraft;
        this.blockAtlas = blockAtlas;
        this.ownerRenderItem = ownerRenderItem;
        this.renderThread = Thread.currentThread();
    }

    static ModernMarkingsCrossingIconRenderer create() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null
                || minecraft.getResourceManager() == null
                || minecraft.getTextureMapBlocks() == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ModernMarkings owner texture runtime is unavailable");
        }
        RenderItem renderItem = GuiContainerManager.drawItems;
        if (renderItem == null || renderItem.getClass() != RenderItem.class) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ModernMarkings requires the exact NEI owner RenderItem; got "
                            + (renderItem == null ? "<null>" : renderItem.getClass().getName()));
        }
        return new ModernMarkingsCrossingIconRenderer(
                minecraft, minecraft.getTextureMapBlocks(), renderItem);
    }

    void draw(ItemStack stack) throws Exception {
        if (Thread.currentThread() != renderThread
                || !minecraft.func_152345_ab()) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ModernMarkings adapter left Minecraft's pinned client thread");
        }
        AssetPin pin = requireTarget(stack);
        Block block = Block.getBlockFromItem(stack.getItem());
        int renderType = block.getRenderType();
        ISimpleBlockRenderingHandler renderer =
                StackIdentity.pinnedBlockRenderer(renderType, FLOOR_RENDERER_CLASS);
        if (renderer.getRenderId() != renderType
                || !renderer.shouldRender3DInInventory(renderType)
                || !RenderingRegistry.instance().renderItemAsFull3DBlock(renderType)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: " + pin.registryId
                            + " owner 3-D inventory renderer topology drifted");
        }
        if (MinecraftForgeClient.getItemRenderer(
                stack, IItemRenderer.ItemRenderType.INVENTORY) != null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: " + pin.registryId
                            + " unexpectedly acquired a Forge item renderer");
        }

        IIcon icon = block.getIcon(0, 0);
        if (!(icon instanceof TextureAtlasSprite)
                || !pin.iconName.equals(icon.getIconName())
                || stack.getIconIndex() != icon
                || icon.getIconWidth() != OWNER_WIDTH
                || icon.getIconHeight() != OWNER_HEIGHT
                || blockAtlas.getAtlasSprite(pin.iconName) != icon) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: " + pin.registryId
                            + " exact stitched owner-sprite binding drifted; got "
                            + describeIcon(icon));
        }
        verifyOwnerResource(pin);
        drawFaceOn(icon);
    }

    private AssetPin requireTarget(ItemStack stack) {
        if (!StackIdentity.isPinnedModernMarkingsCrossingIconTarget(stack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ModernMarkings adapter received a non-target stack: "
                            + StackIdentity.describe(stack));
        }
        GameRegistry.UniqueIdentifier identifier =
                GameRegistry.findUniqueIdentifierFor(stack.getItem());
        String registryId = identifier == null ? "<unregistered>"
                : identifier.modId + ":" + identifier.name;
        AssetPin pin = ASSETS_BY_REGISTRY_ID.get(registryId);
        if (pin == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: unpinned ModernMarkings crossing registry ID "
                            + registryId);
        }
        return pin;
    }

    private void verifyOwnerResource(AssetPin pin) throws Exception {
        if (verifiedOwnerImages.containsKey(pin.registryId)) {
            return;
        }
        IResource resource = minecraft.getResourceManager().getResource(
                new ResourceLocation(MOD_ID, pin.resourcePath));
        if (resource == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: missing resolved owner resource " + pin.resourcePath);
        }
        byte[] bytes;
        try (InputStream input = resource.getInputStream()) {
            bytes = readBounded(input, OWNER_RESOURCE_BYTES + 1);
        }
        BufferedImage image = decodePinnedOwnerPng(
                bytes, OWNER_RESOURCE_BYTES, pin.sha256, pin.opaqueArgb, pin.registryId);
        verifiedOwnerImages.put(pin.registryId, image);
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
            if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                    != OpenGlHelper.defaultTexUnit) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: ModernMarkings could not select the default "
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
                                "ITEM_ICON_RENDER: ModernMarkings default texture binding "
                                        + "restore was not exact"));
                    }
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            try {
                GL11.glMatrixMode(oldMatrixMode);
                if (GL11.glGetInteger(GL11.GL_MATRIX_MODE) != oldMatrixMode) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: ModernMarkings matrix-mode restore was not exact"));
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            try {
                OpenGlHelper.setActiveTexture(oldActiveTexture);
                if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != oldActiveTexture) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: ModernMarkings active texture restore was not exact"));
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
        }
        rethrow(failure);
    }

    static BufferedImage decodePinnedOwnerPng(
            byte[] bytes, int expectedBytes, String expectedSha256,
            int expectedOpaqueArgb, String label) throws Exception {
        if (bytes == null || bytes.length != expectedBytes) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: " + label + " owner PNG byte size drifted; expected "
                            + expectedBytes + ", got " + (bytes == null ? -1 : bytes.length));
        }
        String actualSha256 = sha256(bytes);
        if (!expectedSha256.equals(actualSha256)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: " + label + " owner PNG SHA-256 drifted; expected "
                            + expectedSha256 + ", got " + actualSha256);
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() != OWNER_WIDTH
                || image.getHeight() != OWNER_HEIGHT) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: " + label + " owner PNG dimensions drifted");
        }
        int opaque = 0;
        for (int y = 0; y < OWNER_HEIGHT; y++) {
            for (int x = 0; x < OWNER_WIDTH; x++) {
                int argb = image.getRGB(x, y);
                boolean corner = (x == 0 || x == OWNER_WIDTH - 1)
                        && (y == 0 || y == OWNER_HEIGHT - 1);
                if ((argb >>> 24) != 0) {
                    opaque++;
                }
                if (corner ? argb != expectedOpaqueArgb : (argb >>> 24) != 0) {
                    throw new IllegalArgumentException(
                            "ITEM_ICON_RENDER: " + label
                                    + " four-corner owner pixel topology drifted at "
                                    + x + "," + y);
                }
            }
        }
        if (opaque != 4) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: " + label
                            + " owner PNG expected four opaque pixels, got " + opaque);
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
                        "ITEM_ICON_RENDER: ModernMarkings owner resource exceeded its byte bound");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] value = digest.digest(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte part : value) {
            hex.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        }
        return hex.toString();
    }

    static Map<String, AssetPin> assetPinsForTest() {
        return ASSETS_BY_REGISTRY_ID;
    }

    private static Map<String, AssetPin> assetPins() {
        LinkedHashMap<String, AssetPin> pins = new LinkedHashMap<String, AssetPin>();
        add(pins, new AssetPin("blue",
                "7d365900c2c3a0c9eac463c0783faa72478e12ac0b28346f30bc80aa3a64eb5b",
                0xff031e88));
        add(pins, new AssetPin("green",
                "62da7a16181e4fb736dd72235ee2df7ca1f79721bd2f3bfb973451a0cf59c0e8",
                0xff038803));
        add(pins, new AssetPin("orange",
                "43530736d736528dda12d3bff105007a836864548c72a782f2f8ffed311fbedc",
                0xffff5f00));
        add(pins, new AssetPin("red",
                "cdf8266c0375ef0fd774d89d63833e81fa8863b145cb8802bc01541280454c5e",
                0xffde0000));
        add(pins, new AssetPin("white",
                "e3a6b9a404c035d20607b883d09a3a60b13700b67649bcae6b3f720456d3101e",
                0xffffffff));
        add(pins, new AssetPin("yellow",
                "a672e92a42dd52b77b30cd34bd2951fce06ac6b2182075893068524ac9a2acbe",
                0xffffb600));
        if (pins.size() != EXPECTED_ITEM_ICONS) {
            throw new IllegalStateException(
                    "ModernMarkings crossing asset pin cardinality drifted");
        }
        return Collections.unmodifiableMap(pins);
    }

    private static void add(Map<String, AssetPin> pins, AssetPin pin) {
        if (pins.put(pin.registryId, pin) != null) {
            throw new IllegalStateException(
                    "Duplicate ModernMarkings crossing asset pin " + pin.registryId);
        }
    }

    private static String describeIcon(IIcon icon) {
        return icon == null ? "<null>" : icon.getClass().getName() + ":" + icon.getIconName()
                + ":" + icon.getIconWidth() + "x" + icon.getIconHeight();
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        FatalErrors.rethrowIfFatal(failure);
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }

    private static Throwable merge(Throwable primary, Throwable secondary) {
        if (primary == null) {
            return secondary;
        }
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
        return primary;
    }
}
