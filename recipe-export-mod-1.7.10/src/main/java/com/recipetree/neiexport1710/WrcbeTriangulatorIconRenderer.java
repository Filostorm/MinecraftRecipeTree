package com.recipetree.neiexport1710;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/**
 * Exact lifecycle correction for WR-CBE 1.7.1's unconfigured triangulator icon.
 *
 * <p>The owner registers 256 dynamic {@code TextureSpecial} sprites. Its slot-zero pixels are
 * generated during the item-atlas stitch, after the blank sprite frame has already been loaded,
 * and only reach the live atlas on a later animation update. A definitive export can therefore
 * observe the legitimate metadata-zero item before that first upload and capture a transparent
 * frame. This adapter replays the pinned owner's public deterministic {@code loadTextures()},
 * verifies its exact generated pixels, uploads that owner-managed frame to the canonical item
 * atlas, restores the caller's texture binding, and then delegates to Minecraft's owner
 * {@code RenderItem}. It does not synthesize replacement artwork.</p>
 */
final class WrcbeTriangulatorIconRenderer {
    static final String CONTRACT =
            "wrcbe-triangulator-owner-slot-zero-atlas-refresh-v1";
    static final String ITEM_CLASS =
            "codechicken.wirelessredstone.addons.ItemWirelessTriangulator";
    static final String TRIANG_TEX_MANAGER_CLASS =
            "codechicken.wirelessredstone.addons.TriangTexManager";
    static final String MANAGED_TEXTURE_FX_CLASS =
            "codechicken.lib.render.ManagedTextureFX";
    static final String TEXTURE_SPECIAL_CLASS =
            "codechicken.lib.render.TextureSpecial";
    static final String OWNER_ICON_NAME = "wrcbe_addons:triang_0";
    static final String RING_RESOURCE_PATH = "textures/items/triangRing.png";
    static final String GRADIENT_RESOURCE_PATH = "textures/items/triangGrad.png";
    static final int RING_RESOURCE_BYTES = 194;
    static final int GRADIENT_RESOURCE_BYTES = 224;
    static final String RING_RESOURCE_SHA256 =
            "bd71f38f4b6ee4cc86455691cc3ed6d85867acea4f6ca941b1436f993be3a0ac";
    static final String GRADIENT_RESOURCE_SHA256 =
            "a35f5e487b0dfa713cca7860841a88de33bd80d54429097763b20dad32a787fa";
    static final String OWNER_DEFAULT_ARGB_SHA256 =
            "8a1f28ad4d608520b4e8baa13e99ca170012c7cc14aa798b5ad03723862869df";
    static final int OWNER_ICON_SIZE = 16;
    static final int OWNER_DEFAULT_VISIBLE_PIXELS = 140;
    static final int OWNER_SLOT = 0;
    static final int OWNER_ATLAS_INDEX = 1;
    private static final int[] PACK_STATE_NAMES = {
            GL11.GL_PACK_ALIGNMENT,
            GL11.GL_PACK_ROW_LENGTH,
            GL11.GL_PACK_SKIP_ROWS,
            GL11.GL_PACK_SKIP_PIXELS,
            GL11.GL_PACK_SWAP_BYTES,
            GL11.GL_PACK_LSB_FIRST
    };

    private final Thread renderThread;
    private final boolean requireMinecraftClientThread;
    private final Method loadTextures;
    private final Object managedTextureFx;
    private final Field imageDataField;
    private final Field changedField;
    private final TextureAtlasSprite sprite;
    private final int itemAtlasTextureId;
    private final int[] expectedOwnerPixels;

    private WrcbeTriangulatorIconRenderer(
            Method loadTextures,
            Object managedTextureFx,
            Field imageDataField,
            Field changedField,
            TextureAtlasSprite sprite,
            int itemAtlasTextureId,
            int[] expectedOwnerPixels,
            boolean requireMinecraftClientThread) {
        this.renderThread = Thread.currentThread();
        this.requireMinecraftClientThread = requireMinecraftClientThread;
        this.loadTextures = loadTextures;
        this.managedTextureFx = managedTextureFx;
        this.imageDataField = imageDataField;
        this.changedField = changedField;
        this.sprite = sprite;
        this.itemAtlasTextureId = itemAtlasTextureId;
        this.expectedOwnerPixels = expectedOwnerPixels;
    }

    static WrcbeTriangulatorIconRenderer create(ItemStack triangulatorStack) throws Exception {
        if (!StackIdentity.isPinnedWrcbeTriangulatorIconTarget(triangulatorStack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator adapter requires its exact pinned "
                            + "catalog stack");
        }
        Item item = triangulatorStack.getItem();
        if (item.getItemStackLimit() != 1 || triangulatorStack.getItemSpriteNumber() != 1) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned WR-CBE triangulator item semantics drifted; "
                            + "stackLimit=" + item.getItemStackLimit() + ", atlas="
                            + triangulatorStack.getItemSpriteNumber());
        }

        IIcon icon = triangulatorStack.getIconIndex();
        if (!(icon instanceof TextureAtlasSprite)
                || !TEXTURE_SPECIAL_CLASS.equals(icon.getClass().getName())
                || !OWNER_ICON_NAME.equals(icon.getIconName())
                || icon.getIconWidth() != OWNER_ICON_SIZE
                || icon.getIconHeight() != OWNER_ICON_SIZE) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned WR-CBE triangulator owner icon contract drifted; "
                            + "got " + describeIcon(icon));
        }
        TextureAtlasSprite sprite = (TextureAtlasSprite) icon;

        ClassLoader loader = item.getClass().getClassLoader();
        Class<?> managerClass = Class.forName(TRIANG_TEX_MANAGER_CLASS, false, loader);
        Method loadTextures = managerClass.getDeclaredMethod("loadTextures");
        if (!Modifier.isPublic(loadTextures.getModifiers())
                || !Modifier.isStatic(loadTextures.getModifiers())
                || loadTextures.getReturnType() != Void.TYPE) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE loadTextures method shape drifted");
        }

        Field texturesField = managerClass.getDeclaredField("textures");
        if (!Modifier.isPrivate(texturesField.getModifiers())
                || !Modifier.isStatic(texturesField.getModifiers())
                || !texturesField.getType().isArray()
                || !MANAGED_TEXTURE_FX_CLASS.equals(
                        texturesField.getType().getComponentType().getName())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE managed texture array shape drifted");
        }
        texturesField.setAccessible(true);
        Object[] textures = (Object[]) texturesField.get(null);
        if (textures == null || textures.length != 256 || textures[OWNER_SLOT] == null
                || !MANAGED_TEXTURE_FX_CLASS.equals(
                        textures[OWNER_SLOT].getClass().getName())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE managed texture slot topology drifted");
        }
        Object managedTextureFx = textures[OWNER_SLOT];
        Field imageDataField = managedTextureFx.getClass().getField("imageData");
        Field changedField = managedTextureFx.getClass().getField("changed");
        if (imageDataField.getType() != int[].class
                || changedField.getType() != Boolean.TYPE
                || !Modifier.isPublic(imageDataField.getModifiers())
                || !Modifier.isPublic(changedField.getModifiers())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE ManagedTextureFX field contract drifted");
        }
        Field textureField = managedTextureFx.getClass().getSuperclass().getField("texture");
        Object managedSprite = textureField.get(managedTextureFx);
        if (managedSprite != sprite) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner sprite is not bound to managed slot zero");
        }
        Method atlasIndex = managedSprite.getClass().getMethod("atlasIndex");
        Object observedAtlasIndex = atlasIndex.invoke(managedSprite);
        if (!(observedAtlasIndex instanceof Integer)
                || ((Integer) observedAtlasIndex).intValue() != OWNER_ATLAS_INDEX) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner sprite atlas index drifted; got "
                            + observedAtlasIndex);
        }
        Field textureFxField = managedSprite.getClass().getDeclaredField("textureFX");
        textureFxField.setAccessible(true);
        if (textureFxField.get(managedSprite) != managedTextureFx) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE TextureSpecial owner link drifted");
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null
                || minecraft.getResourceManager() == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Minecraft texture/resource manager is unavailable");
        }
        ITextureObject itemAtlasObject = minecraft.getTextureManager().getTexture(
                TextureMap.locationItemsTexture);
        if (!(itemAtlasObject instanceof TextureMap)
                || itemAtlasObject.getGlTextureId() <= 0) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: canonical Minecraft item atlas ownership drifted");
        }
        TextureMap itemAtlas = (TextureMap) itemAtlasObject;
        if (itemAtlas.getAtlasSprite(OWNER_ICON_NAME) != sprite) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner sprite is not owned by the canonical "
                            + "stitched item atlas");
        }

        int[] ring = loadPinnedPixels(
                minecraft.getResourceManager(), RING_RESOURCE_PATH,
                RING_RESOURCE_BYTES, RING_RESOURCE_SHA256);
        int[] gradient = loadPinnedPixels(
                minecraft.getResourceManager(), GRADIENT_RESOURCE_PATH,
                GRADIENT_RESOURCE_BYTES, GRADIENT_RESOURCE_SHA256);
        int[] expectedOwnerPixels = composeOwnerDefaultPixels(ring, gradient);
        verifyOwnerPixels(expectedOwnerPixels);

        return new WrcbeTriangulatorIconRenderer(
                loadTextures, managedTextureFx, imageDataField, changedField,
                sprite, itemAtlasObject.getGlTextureId(), expectedOwnerPixels, true);
    }

    void drawExactlyOnce(
            BotaniaPrismIconRenderer renderItemHost,
            OffscreenRenderer.DrawCall ownerDraw) throws Exception {
        if (renderItemHost == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: shared RenderItem compatibility host is required");
        }
        BotaniaPrismIconRenderer.AdapterCounts counts =
                renderItemHost.drawAndCountAll(ownerDraw);
        if (counts.wrcbeTriangulator != 1L || counts.botaniaPrism != 0L) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator owner inventory renderer invoked "
                            + "the shared adapters an unexpected number of times; triangulator="
                            + counts.wrcbeTriangulator + ", prism=" + counts.botaniaPrism);
        }
    }

    void refreshOwnerTextureAndDraw(Runnable ownerDraw) throws Throwable {
        if (ownerDraw == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator owner draw is required");
        }
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator texture refresh left its pinned "
                            + "render thread");
        }
        if (requireMinecraftClientThread
                && (Minecraft.getMinecraft() == null
                || !Minecraft.getMinecraft().func_152345_ab())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE triangulator texture refresh is not on "
                            + "Minecraft's client thread");
        }

        invokeOwnerLoadTextures();
        int[] generated = (int[]) imageDataField.get(managedTextureFx);
        if (!Arrays.equals(expectedOwnerPixels, generated)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner slot-zero pixels drifted; expected "
                            + OWNER_DEFAULT_ARGB_SHA256 + ", got " + sha256Argb(generated));
        }
        if (!changedField.getBoolean(managedTextureFx)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner did not mark slot zero dirty");
        }

        uploadOwnerFrame();
        ownerDraw.run();
    }

    private void invokeOwnerLoadTextures() throws Throwable {
        try {
            loadTextures.invoke(null);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            throw cause == null ? wrapped : cause;
        }
    }

    private void uploadOwnerFrame() throws Throwable {
        int oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldDefaultBinding = 0;
        boolean capturedDefaultBinding = false;
        Throwable failure = null;
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != OpenGlHelper.defaultTexUnit) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: could not select the default texture unit for "
                                + "WR-CBE owner atlas upload");
            }
            oldDefaultBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            capturedDefaultBinding = true;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTextureId);
            if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != itemAtlasTextureId) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: WR-CBE canonical item atlas bind failed");
            }
            sprite.updateAnimation();
            if (changedField.getBoolean(managedTextureFx)) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: WR-CBE TextureSpecial did not consume the exact "
                                + "owner-managed frame");
            }
            if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != itemAtlasTextureId) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: WR-CBE TextureSpecial changed the live atlas binding");
            }
            verifyLiveAtlasBaseLevel(
                    new BotaniaPrismIconRenderer.GlAtlasReadbackAccess(),
                    itemAtlasTextureId, sprite, expectedOwnerPixels);
        } catch (Throwable error) {
            failure = error;
        } finally {
            if (capturedDefaultBinding) {
                try {
                    OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldDefaultBinding);
                    if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                            != oldDefaultBinding) {
                        failure = merge(failure, new IllegalStateException(
                                "ITEM_ICON_RENDER: WR-CBE default-unit texture binding "
                                        + "restore was not exact"));
                    }
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            try {
                OpenGlHelper.setActiveTexture(oldActiveTexture);
                if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != oldActiveTexture) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: WR-CBE active texture restore was not exact"));
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static void verifyLiveAtlasBaseLevel(
            BotaniaPrismIconRenderer.AtlasReadbackAccess readback,
            int atlasTextureId,
            TextureAtlasSprite sprite,
            int[] expectedPixels) throws Throwable {
        if (readback == null || sprite == null || expectedPixels == null
                || expectedPixels.length != OWNER_ICON_SIZE * OWNER_ICON_SIZE) {
            throw new IllegalArgumentException(
                    "live WR-CBE triangulator atlas verifier is invalid");
        }
        if (!readback.framebufferSupported() || !readback.textureExists(atlasTextureId)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: canonical item atlas does not support exact WR-CBE "
                            + "live FBO readback");
        }
        int atlasWidth = readback.textureLevelParameter(0, GL11.GL_TEXTURE_WIDTH);
        int atlasHeight = readback.textureLevelParameter(0, GL11.GL_TEXTURE_HEIGHT);
        int originX = sprite.getOriginX();
        int originY = sprite.getOriginY();
        if (sprite.getIconWidth() != OWNER_ICON_SIZE
                || sprite.getIconHeight() != OWNER_ICON_SIZE
                || originX < 0 || originY < 0
                || originX + OWNER_ICON_SIZE > atlasWidth
                || originY + OWNER_ICON_SIZE > atlasHeight) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner live-atlas bounds drifted; origin="
                            + originX + "," + originY + ", atlas="
                            + atlasWidth + "x" + atlasHeight);
        }

        int oldFramebuffer = readback.framebufferBinding();
        int oldReadBuffer = readback.readBuffer();
        int[] oldPackState = new int[PACK_STATE_NAMES.length];
        for (int index = 0; index < PACK_STATE_NAMES.length; index++) {
            oldPackState[index] = readback.pixelStore(PACK_STATE_NAMES[index]);
        }

        int framebuffer = 0;
        int[] observed = null;
        Throwable failure = null;
        try {
            framebuffer = readback.generateFramebuffer();
            if (framebuffer <= 0) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: WR-CBE live-atlas readback could not allocate an FBO");
            }
            readback.bindFramebuffer(framebuffer);
            readback.readBuffer(OpenGlHelper.field_153200_g);
            readback.pixelStore(GL11.GL_PACK_ALIGNMENT, 1);
            readback.pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
            readback.pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
            readback.pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
            readback.pixelStore(GL11.GL_PACK_SWAP_BYTES, 0);
            readback.pixelStore(GL11.GL_PACK_LSB_FIRST, 0);
            readback.attachTexture(atlasTextureId, 0);
            int status = readback.framebufferStatus();
            if (status != OpenGlHelper.field_153202_i
                    || readback.readBuffer() != OpenGlHelper.field_153200_g) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: WR-CBE live-atlas FBO is incomplete or has the "
                                + "wrong read buffer; status=0x"
                                + Integer.toHexString(status));
            }
            observed = readback.readPixels(
                    originX, originY, OWNER_ICON_SIZE, OWNER_ICON_SIZE);
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
                            "ITEM_ICON_RENDER: WR-CBE live-atlas framebuffer/read-buffer "
                                    + "restore was not exact"));
                }
                for (int index = 0; index < PACK_STATE_NAMES.length; index++) {
                    if (readback.pixelStore(PACK_STATE_NAMES[index]) != oldPackState[index]) {
                        failure = merge(failure, new IllegalStateException(
                                "ITEM_ICON_RENDER: WR-CBE live-atlas pixel-pack restore was "
                                        + "not exact for 0x"
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
        if (failure != null) {
            throw failure;
        }
        if (observed == null || observed.length != expectedPixels.length) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE live-atlas pixel count drifted");
        }
        for (int index = 0; index < expectedPixels.length; index++) {
            if (expectedPixels[index] != observed[index]) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: WR-CBE live-atlas pixels drifted at index "
                                + index + "; expected=0x"
                                + Integer.toHexString(expectedPixels[index]) + ", got=0x"
                                + Integer.toHexString(observed[index]));
            }
        }
    }

    private static int[] loadPinnedPixels(
            IResourceManager resources,
            String resourcePath,
            int expectedLength,
            String expectedSha256) throws IOException {
        ResourceLocation location = new ResourceLocation("wrcbe_addons", resourcePath);
        IResource resource = resources.getResource(location);
        if (resource == null || resource.hasMetadata()) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned WR-CBE resource metadata drifted for "
                            + location);
        }
        InputStream input = resource.getInputStream();
        if (input == null) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned WR-CBE resource stream is unavailable for "
                            + location);
        }
        byte[] bytes;
        try {
            bytes = readExactPinnedResource(input, expectedLength, expectedSha256);
        } finally {
            input.close();
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() != OWNER_ICON_SIZE
                || image.getHeight() != OWNER_ICON_SIZE) {
            throw new IOException(
                    "ITEM_ICON_RENDER: pinned WR-CBE PNG dimensions drifted for " + location);
        }
        return image.getRGB(
                0, 0, OWNER_ICON_SIZE, OWNER_ICON_SIZE,
                null, 0, OWNER_ICON_SIZE);
    }

    static byte[] readExactPinnedResource(
            InputStream input, int expectedLength, String expectedSha256) throws IOException {
        if (input == null || expectedLength <= 0 || expectedLength > 64 * 1024) {
            throw new IllegalArgumentException(
                    "pinned WR-CBE resource input/length is invalid");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedLength);
        byte[] buffer = new byte[Math.min(4096, expectedLength + 1)];
        int remaining = expectedLength + 1;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count < 0) {
                break;
            }
            if (count > 0) {
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length != expectedLength) {
            throw new IOException(
                    "pinned WR-CBE resource length mismatch; expected " + expectedLength
                            + ", got " + bytes.length);
        }
        String actual = sha256(bytes);
        if (!expectedSha256.equals(actual)) {
            throw new IOException(
                    "pinned WR-CBE resource SHA-256 mismatch; expected " + expectedSha256
                            + ", got " + actual);
        }
        return bytes;
    }

    static int[] composeOwnerDefaultPixels(int[] ring, int[] gradient) {
        if (ring == null || gradient == null
                || ring.length != OWNER_ICON_SIZE * OWNER_ICON_SIZE
                || gradient.length != OWNER_ICON_SIZE * OWNER_ICON_SIZE) {
            throw new IllegalArgumentException(
                    "WR-CBE owner resources must each contain exactly 256 ARGB pixels");
        }
        int[] output = new int[ring.length];
        for (int index = 0; index < output.length; index++) {
            output[index] = (gradient[index] >>> 24) == 0
                    ? ring[index] : gradient[index];
        }
        return output;
    }

    static void verifyOwnerPixels(int[] pixels) {
        if (pixels == null || pixels.length != OWNER_ICON_SIZE * OWNER_ICON_SIZE) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner pixel count drifted");
        }
        int visible = 0;
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                visible++;
            }
        }
        String digest = sha256Argb(pixels);
        if (visible != OWNER_DEFAULT_VISIBLE_PIXELS
                || !OWNER_DEFAULT_ARGB_SHA256.equals(digest)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: WR-CBE owner default pixel fingerprint drifted; "
                            + "visible=" + visible + ", sha256=" + digest);
        }
    }

    static String sha256Argb(int[] pixels) {
        if (pixels == null) {
            return "<null>";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int pixel : pixels) {
                digest.update((byte) (pixel >>> 24));
                digest.update((byte) (pixel >>> 16));
                digest.update((byte) (pixel >>> 8));
                digest.update((byte) pixel);
            }
            return hex(digest.digest());
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(bytes));
        } catch (Exception error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte part : bytes) {
            value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        }
        return value.toString();
    }

    private static String describeIcon(IIcon icon) {
        return icon == null
                ? "<null>"
                : icon.getClass().getName() + "(" + icon.getIconName() + ", "
                        + icon.getIconWidth() + "x" + icon.getIconHeight() + ")";
    }

    private static Throwable merge(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (additional != null && additional != primary) {
            primary.addSuppressed(additional);
        }
        return primary;
    }
}
