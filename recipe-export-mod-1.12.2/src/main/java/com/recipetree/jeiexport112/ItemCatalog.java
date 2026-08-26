package com.recipetree.jeiexport112;

import com.google.gson.stream.JsonWriter;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

final class ItemCatalog {
    private final ExportContext context;
    final IIngredientRegistry registry;
    private final JsonWriter writer;
    private final Set<String> known = new HashSet<String>();
    private int count;
    private boolean closed;

    ItemCatalog(ExportContext context, IIngredientRegistry registry) throws IOException {
        this.context = context;
        this.registry = registry;
        verifyVanillaItemRendererCalibration();
        writer = ExportContext.jsonWriter(context.root.resolve("items.json"));
        writer.beginObject().name("items").beginArray();
    }

    int count() {
        return count;
    }

    <T> String ensure(IIngredientType<T> type, T ingredient) throws IOException {
        return ensureResolved(resolve(type, ingredient));
    }

    /**
     * Resolves the exact identity that will be published without mutating items.json. Callers that
     * need deterministic first-emission order can resolve a bounded batch, sort by
     * {@link ResolvedIngredient#canonicalKey()},
     * and then pass the same objects to {@link #ensureResolved(ResolvedIngredient)}.
     */
    <T> ResolvedIngredient<T> resolve(IIngredientType<T> type, T ingredient) {
        if (type == null) {
            throw new IllegalArgumentException("HEI supplied a null ingredient type for " + ingredient);
        }
        if (ingredient == null) {
            throw new IllegalArgumentException("HEI supplied a null ingredient for " + type.getIngredientClass());
        }
        IIngredientHelper<T> helper = registry.getIngredientHelper(type);
        String prefix = prefix(type);
        String rawResourceId;
        try {
            rawResourceId = helper.getResourceId(ingredient);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("ingredient resource id " + ingredient.getClass().getName() + ": " + throwable +
                    "; deferring to the unique id");
            rawResourceId = null;
        }
        String name;
        try {
            name = helper.getDisplayName(ingredient);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("ingredient display name " + ingredient.getClass().getName() + ": " + throwable +
                    "; deferring to the unique id");
            name = null;
        }

        String uid;
        try {
            uid = helper.getUniqueId(ingredient);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("ingredient unique id " + ingredient.getClass().getName() + ": " + throwable +
                    "; using a deterministic resource/name identity");
            uid = null;
        }
        String modId;
        try {
            modId = helper.getDisplayModId(ingredient);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("ingredient mod id " + ingredient.getClass().getName() + ": " + throwable +
                    "; deriving namespace");
            modId = namespace(rawResourceId == null ? "" : rawResourceId);
        }

        uid = ItemNbtIdentity.refine(uid, rawResourceId, ingredient);

        try {
            LegacyIngredientIdentity.Identity identity = LegacyIngredientIdentity.adapt(
                    ingredient, uid, rawResourceId, name, modId, this::nestedItemIdentity);
            uid = identity.uid;
            rawResourceId = identity.resourceId;
            name = identity.displayName;
            modId = identity.modId;
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("ingredient unique id " + ingredient.getClass().getName() +
                    ": exact legacy semantic identity adapter failed: " + throwable +
                    "; ingredient rejected instead of using the lossy helper identity");
            throw new IllegalStateException("exact legacy semantic identity adapter failed for " +
                    ingredient.getClass().getName(), throwable);
        }
        if (uid == null || uid.trim().isEmpty()) {
            String identitySeed = ingredient.getClass().getName() + "|" + safeIdentityPart(rawResourceId) +
                    "|" + safeIdentityPart(name);
            uid = "jeiexport-fallback:" + Naming.hash8(identitySeed);
            context.failure("ingredient unique id " + ingredient.getClass().getName() +
                    " was null/blank; using logged deterministic fallback " + uid);
        }
        return new ResolvedIngredient<T>(
                type, ingredient, prefix, uid, rawResourceId, name, modId);
    }

    String ensureResolved(ResolvedIngredient<?> resolved) throws IOException {
        if (resolved == null) {
            throw new IllegalArgumentException("cannot emit a null resolved ingredient");
        }
        return ensureResolvedTyped(resolved);
    }

    String ensureSynthetic(String key, String resourceId, String name, String modId, String type)
            throws IOException {
        if (closed) {
            throw new IOException("cannot add synthetic ingredient after items.json was closed");
        }
        if (!known.add(key)) {
            return key;
        }
        writer.beginObject();
        writer.name("k").value(key);
        writer.name("id").value(resourceId);
        writer.name("n").value(name);
        writer.name("m").value(modId);
        if (type != null && !type.trim().isEmpty() && !"item".equals(type)) {
            writer.name("t").value(type);
        }
        writer.endObject();
        count++;
        return key;
    }

    private <T> String ensureResolvedTyped(ResolvedIngredient<T> resolved) throws IOException {
        String key = resolved.key;
        if (!known.add(key)) {
            return key;
        }

        String uid = resolved.uid;
        String rawResourceId = resolved.rawResourceId;
        String name = resolved.displayName;
        String modId = resolved.modId;
        name = Naming.plainText(name);
        if (name == null || name.trim().isEmpty()) {
            context.warning("UPSTREAM_BLANK_DISPLAY_NAME ingredient " + key +
                    " was null/blank after formatting-code removal; using its deterministic HEI unique id");
            name = uid;
        }
        if (rawResourceId == null || rawResourceId.trim().isEmpty()) {
            context.failure("ingredient resource id " + key + " was empty; using unique id");
            rawResourceId = uid;
        }
        String resourceId = rawResourceId.indexOf(':') >= 0
                ? rawResourceId
                : (modId == null || modId.trim().isEmpty() ? "unknown" : modId) + ":" + rawResourceId;

        String icon = null;
        try {
            icon = renderIcon(
                    resolved.type, resolved.ingredient, resolved.prefix, resourceId, uid, key);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("ingredient icon " + key + ": " + throwable);
        }

        writer.beginObject();
        writer.name("k").value(key);
        writer.name("id").value(resourceId);
        writer.name("n").value(name);
        writer.name("m").value(modId == null || modId.trim().isEmpty() ? namespace(resourceId) : modId);
        if (!"item".equals(resolved.prefix)) {
            writer.name("t").value(resolved.prefix);
        }
        if (icon != null) {
            writer.name("icon").value(icon);
        }
        writer.endObject();
        count++;
        return key;
    }

    private String nestedItemIdentity(Object nestedIngredient) {
        if (!(nestedIngredient instanceof ItemStack)) {
            throw new IllegalArgumentException("expected a Meteor catalyst ItemStack, got " +
                    (nestedIngredient == null ? "null" : nestedIngredient.getClass().getName()));
        }
        ItemStack stack = (ItemStack) nestedIngredient;
        IIngredientHelper<ItemStack> itemHelper = registry.getIngredientHelper(VanillaTypes.ITEM);
        String uid = itemHelper.getUniqueId(stack);
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("HEI item helper returned a null/blank Meteor catalyst identity");
        }
        return uid + "|count=" + stack.getCount();
    }

    private static String safeIdentityPart(String value) {
        return value == null || value.trim().isEmpty() ? "<missing>" : value.trim();
    }

    @SuppressWarnings("unchecked")
    ResolvedIngredient<?> resolveUnknown(Object ingredient) {
        IIngredientType<Object> type = (IIngredientType<Object>) registry.getIngredientType(ingredient);
        if (type == null) {
            throw new IllegalArgumentException("HEI has no ingredient type for " +
                    (ingredient == null ? "null" : ingredient.getClass().getName()));
        }
        return resolve(type, ingredient);
    }

    String ensureUnknown(Object ingredient) throws IOException {
        return ensureResolved(resolveUnknown(ingredient));
    }

    private <T> String renderIcon(final IIngredientType<T> type, final T ingredient, String prefix,
                                  String resourceId, String uid, String key) throws Exception {
        final IIngredientRenderer<T> renderer = registry.getIngredientRenderer(type);
        final T catalogIngredient = catalogRenderIngredient(type, ingredient);
        String namespace = namespace(resourceId);
        String path = resourcePath(resourceId);
        if (!uid.equals(resourceId)) {
            path += "__" + Naming.hash8(uid);
        }
        final int scale = context.request.iconScale;
        AvaritiaShaderScope shaderScope = prepareAvaritiaShader(catalogIngredient, key);
        BufferedImage image;
        String unusableReason;
        try {
            boolean fitOversizedItem = requiresOversizedItemFit(catalogIngredient);
            image = fitOversizedItem
                    ? renderIngredientWithVisibleBoundsFit(renderer, catalogIngredient, scale)
                    : renderIngredient(renderer, catalogIngredient, scale);
            if (fitOversizedItem) {
                JeiExportMod.LOGGER.info(
                        "[jeiexport] AVARITIA_ITEM_ICON_FIT_APPLIED ingredient={}; rendered the " +
                                "native oversized item on a bounded canvas and fit its visible pixels " +
                                "into the 16x16 catalog icon",
                        key);
            }
            unusableReason = RenderedIconValidation.unusableReason(image);
            if (RenderedIconValidation.FULLY_TRANSPARENT.equals(unusableReason)) {
                BufferedImage overscan = renderIngredientWithOverscan(
                        renderer, catalogIngredient, scale);
                if (RenderedIconValidation.unusableReason(overscan) == null) {
                    image = overscan;
                    unusableReason = null;
                    context.warning("NATIVE_ICON_OVERSCAN_RECOVERY_APPLIED ingredient " + key +
                            ": the exact 16x16 HEI draw was fully transparent; a second native HEI draw " +
                            "on a 32x32 logical framebuffer produced visible pixels in the exact 16x16 " +
                            "center crop; no interpolation or synthetic pixels were used");
                }
            }
        } finally {
            shaderScope.close();
        }
        if (unusableReason != null) {
            context.warning("UPSTREAM_NATIVE_ICON_UNAVAILABLE ingredient " + key + ": " +
                    unusableReason + "; both the exact 16x16 HEI draw and bounded native overscan " +
                    "draw produced no visible pixel; omitting the PNG and JSON icon reference so " +
                    "the viewer uses its explicitly named fallback");
            return null;
        }
        String relative = context.uniquePath(
                "icons/" + prefix + "/" + Naming.sanitize(namespace), path, ".png");
        context.submitImage(image, context.root.resolve(relative));
        return relative;
    }

    /**
     * Catalog icons encode ingredient identity, not recipe quantity. HEI's ItemStack renderer draws
     * the stack count over the native item model when the count exceeds one. Rendering an identity
     * stack at count one prevents that glyph from making an otherwise transparent item model look
     * usable to {@link RenderedIconValidation}, while a defensive copy keeps recipe quantities and
     * the ingredient-registry object unchanged.
     */
    @SuppressWarnings("unchecked")
    static <T> T catalogRenderIngredient(IIngredientType<T> type, T ingredient) {
        if (type == VanillaTypes.ITEM) {
            if (!(ingredient instanceof ItemStack)) {
                throw new IllegalArgumentException("HEI ITEM ingredient was not an ItemStack: " +
                        (ingredient == null ? "null" : ingredient.getClass().getName()));
            }
            ItemStack renderStack = ((ItemStack) ingredient).copy();
            renderStack.setCount(1);
            return (T) renderStack;
        }
        if (type == VanillaTypes.FLUID) {
            if (!(ingredient instanceof FluidStack)) {
                throw new IllegalArgumentException("HEI FLUID ingredient was not a FluidStack: " +
                        (ingredient == null ? "null" : ingredient.getClass().getName()));
            }
            FluidStack renderStack = ((FluidStack) ingredient).copy();
            renderStack.amount = Fluid.BUCKET_VOLUME;
            return (T) renderStack;
        }
        return ingredient;
    }

    private <T> BufferedImage renderIngredient(final IIngredientRenderer<T> renderer,
                                               final T ingredient, final int scale)
            throws Exception {
        return context.renderer.render(16 * scale, 16 * scale, minecraft -> {
            GlStateManager.pushMatrix();
            try {
                GlStateManager.scale(scale, scale, 1.0F);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                renderer.render(minecraft, 0, 0, ingredient);
            } finally {
                GlStateManager.popMatrix();
            }
        });
    }

    private <T> BufferedImage renderIngredientWithOverscan(final IIngredientRenderer<T> renderer,
                                                           final T ingredient, final int scale)
            throws Exception {
        final int logicalMargin = 8;
        final int physicalMargin = logicalMargin * scale;
        final int iconSize = 16 * scale;
        BufferedImage overscan = context.renderer.render(32 * scale, 32 * scale, minecraft -> {
            GlStateManager.pushMatrix();
            try {
                GlStateManager.scale(scale, scale, 1.0F);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                renderer.render(minecraft, logicalMargin, logicalMargin, ingredient);
            } finally {
                GlStateManager.popMatrix();
            }
        });
        return exactCrop(overscan, physicalMargin, physicalMargin, iconSize, iconSize);
    }

    /**
     * AvaritiaItem's native GUI renderer deliberately draws halos beyond the normal item quad.
     * A 16x16 framebuffer clips that halo before readback, leaving only the center glyph. Render
     * the same native model with the largest supported halo margin and fit its actual alpha bounds
     * into the catalog raster. The adaptation is class-based so scripted AvaritiaItem entries keep
     * their configured texture, mask, color, and opacity without item-id substitutions.
     */
    private <T> BufferedImage renderIngredientWithVisibleBoundsFit(
            final IIngredientRenderer<T> renderer, final T ingredient, final int scale)
            throws Exception {
        final int logicalCanvasSize = 64;
        final int logicalItemOrigin = 24;
        BufferedImage overscan = context.renderer.render(
                logicalCanvasSize * scale, logicalCanvasSize * scale, minecraft -> {
                    GlStateManager.pushMatrix();
                    try {
                        GlStateManager.scale(scale, scale, 1.0F);
                        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                        renderer.render(
                                minecraft, logicalItemOrigin, logicalItemOrigin, ingredient);
                    } finally {
                        GlStateManager.popMatrix();
                    }
                });
        return fitVisiblePixels(overscan, 16 * scale, scale);
    }

    static boolean requiresOversizedItemFit(Object ingredient) throws Exception {
        if (!(ingredient instanceof ItemStack)) {
            return false;
        }
        ItemStack stack = (ItemStack) ingredient;
        Class<?> haloInterface = findNamedInterface(
                stack.getItem().getClass(), "morph.avaritia.api.IHaloRenderItem");
        if (haloInterface == null) {
            return false;
        }
        Method shouldDrawHalo = haloInterface.getMethod("shouldDrawHalo", ItemStack.class);
        return Boolean.TRUE.equals(shouldDrawHalo.invoke(stack.getItem(), stack));
    }

    private AvaritiaShaderScope prepareAvaritiaShader(Object ingredient, String key)
            throws Exception {
        if (!(ingredient instanceof ItemStack)) {
            return AvaritiaShaderScope.NOOP;
        }
        ItemStack stack = (ItemStack) ingredient;
        Class<?> itemClass = stack.getItem().getClass();
        if (!isAvaritiaItemClass(itemClass) || findNamedInterface(
                itemClass, "morph.avaritia.api.ICosmicRenderItem") == null) {
            return AvaritiaShaderScope.NOOP;
        }

        boolean previousShaderSupport = OpenGlHelper.shadersSupported;
        if (!previousShaderSupport) {
            ContextCapabilities capabilities = GLContext.getCapabilities();
            boolean shaderObjectsAvailable = capabilities.OpenGL20 ||
                    (capabilities.GL_ARB_shader_objects &&
                            capabilities.GL_ARB_vertex_shader &&
                            capabilities.GL_ARB_fragment_shader);
            if (!shaderObjectsAvailable) {
                throw new IllegalStateException(
                        "AvaritiaItem cosmic icon " + key + " requires GLSL shader objects, but " +
                                "the active Minecraft OpenGL context does not provide them");
            }
            OpenGlHelper.shadersSupported = true;
            JeiExportMod.LOGGER.info(
                    "[jeiexport] AVARITIA_ITEM_SHADER_SUPPORT_RECOVERED ingredient={}; the active " +
                            "OpenGL context exposes shader objects even though Minecraft's cached " +
                            "shader-support flag was false",
                    key);
        }

        boolean initialized = false;
        try {
            Class<?> shaderHelper = Class.forName(
                    "morph.avaritia.client.render.shader.ShaderHelper");
            Field cosmicShader = shaderHelper.getField("cosmicShader");
            int program = cosmicShader.getInt(null);
            if (program <= 0) {
                shaderHelper.getMethod("initShaders").invoke(null);
                program = cosmicShader.getInt(null);
                initialized = true;
            }
            if (program <= 0) {
                throw new IllegalStateException(
                        "AvaritiaItem cosmic shader initialization returned program " + program);
            }
            if (initialized) {
                JeiExportMod.LOGGER.info(
                        "[jeiexport] AVARITIA_ITEM_COSMIC_SHADER_INITIALIZED ingredient={} program={}",
                        key, program);
            }
            return new AvaritiaShaderScope(previousShaderSupport);
        } catch (Throwable throwable) {
            OpenGlHelper.shadersSupported = previousShaderSupport;
            FatalErrors.rethrowIfFatal(throwable);
            if (throwable instanceof Exception) {
                throw (Exception) throwable;
            }
            throw new IllegalStateException("AvaritiaItem cosmic shader preparation failed", throwable);
        }
    }

    private static boolean isAvaritiaItemClass(Class<?> itemClass) {
        while (itemClass != null) {
            if (isAvaritiaItemClassName(itemClass.getName())) {
                return true;
            }
            itemClass = itemClass.getSuperclass();
        }
        return false;
    }

    private static Class<?> findNamedInterface(Class<?> type, String interfaceName) {
        if (type == null) {
            return null;
        }
        for (Class<?> candidate : type.getInterfaces()) {
            if (interfaceName.equals(candidate.getName())) {
                return candidate;
            }
            Class<?> nested = findNamedInterface(candidate, interfaceName);
            if (nested != null) {
                return nested;
            }
        }
        return findNamedInterface(type.getSuperclass(), interfaceName);
    }

    static boolean isAvaritiaItemClassName(String className) {
        return className != null && className.startsWith("top.suyarong.items.Avaritia");
    }

    private static final class AvaritiaShaderScope {
        static final AvaritiaShaderScope NOOP = new AvaritiaShaderScope(null);
        private final Boolean previousShaderSupport;

        AvaritiaShaderScope(Boolean previousShaderSupport) {
            this.previousShaderSupport = previousShaderSupport;
        }

        void close() {
            if (previousShaderSupport != null) {
                OpenGlHelper.shadersSupported = previousShaderSupport.booleanValue();
            }
        }
    }

    static BufferedImage fitVisiblePixels(BufferedImage source, int targetSize, int padding) {
        if (source == null) {
            throw new IllegalArgumentException("cannot fit a null native render");
        }
        if (targetSize <= 0 || padding < 0) {
            throw new IllegalArgumentException(
                    "invalid visible-pixel fit target=" + targetSize + " padding=" + padding);
        }
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("native oversized item render was fully transparent");
        }

        int visibleWidth = maxX - minX + 1;
        int visibleHeight = maxY - minY + 1;
        int cropSize = Math.max(visibleWidth, visibleHeight) + padding * 2;
        int centerX = (minX + maxX + 1) / 2;
        int centerY = (minY + maxY + 1) / 2;
        int cropX = centerX - cropSize / 2;
        int cropY = centerY - cropSize / 2;
        if (cropX < 0 || cropY < 0 ||
                cropX + cropSize > source.getWidth() ||
                cropY + cropSize > source.getHeight()) {
            throw new IllegalArgumentException(
                    "native oversized item exceeded the bounded " + source.getWidth() + "x" +
                            source.getHeight() + " canvas; visible bounds=" + minX + "," + minY +
                            ".." + maxX + "," + maxY + " padding=" + padding);
        }

        BufferedImage fitted = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        int destinationSize = Math.min(targetSize, cropSize);
        int destinationOffset = (targetSize - destinationSize) / 2;
        Graphics2D graphics = fitted.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.drawImage(
                    source,
                    destinationOffset,
                    destinationOffset,
                    destinationOffset + destinationSize,
                    destinationOffset + destinationSize,
                    cropX,
                    cropY,
                    cropX + cropSize,
                    cropY + cropSize,
                    null);
        } finally {
            graphics.dispose();
        }
        return fitted;
    }

    static BufferedImage exactCrop(BufferedImage source, int x, int y, int width, int height) {
        if (source == null) {
            throw new IllegalArgumentException("cannot crop a null native render");
        }
        if (x < 0 || y < 0 || width <= 0 || height <= 0 ||
                x + width > source.getWidth() || y + height > source.getHeight()) {
            throw new IllegalArgumentException("invalid exact crop " + x + "," + y + " " +
                    width + "x" + height + " from " + source.getWidth() + "x" + source.getHeight());
        }
        BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = source.getRGB(x, y, width, height, null, 0, width);
        cropped.setRGB(0, 0, width, height, pixels, 0, width);
        return cropped;
    }

    private void verifyVanillaItemRendererCalibration() throws IOException {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            TextureMap atlas = minecraft.getTextureMapBlocks();
            if (atlas == null) {
                throw new IllegalStateException(
                        "ITEM_RENDER_CALIBRATION_FAILED: Minecraft block/item atlas is null");
            }
            TextureAtlasSprite paperSprite = atlas.getAtlasSprite("minecraft:items/paper");
            if (paperSprite == null || paperSprite == atlas.getMissingSprite()) {
                throw new IllegalStateException(
                        "ITEM_RENDER_CALIBRATION_FAILED: runtime minecraft:items/paper atlas " +
                                "sprite resolves to the missing sprite");
            }
            if (paperSprite.getFrameCount() != 1) {
                throw new IllegalStateException(
                        "ITEM_RENDER_CALIBRATION_FAILED: runtime minecraft:items/paper atlas " +
                                "sprite has " + paperSprite.getFrameCount() +
                                " frames; the exact static sentinel contract requires one");
            }
            int[][] frame = paperSprite.getFrameTextureData(0);
            if (frame == null || frame.length == 0 || frame[0] == null) {
                throw new IllegalStateException(
                        "ITEM_RENDER_CALIBRATION_FAILED: runtime minecraft:items/paper atlas " +
                                "sprite has no mip-0 frame data");
            }

            int scale = context.request.iconScale;
            IIngredientRenderer<ItemStack> renderer =
                    registry.getIngredientRenderer(VanillaTypes.ITEM);
            if (renderer == null) {
                throw new IllegalStateException(
                        "ITEM_RENDER_CALIBRATION_FAILED: HEI returned a null ItemStack renderer");
            }
            BufferedImage rendered = renderIngredient(
                    renderer, new ItemStack(Items.PAPER), scale);
            RendererCalibrationValidation.Report report =
                    RendererCalibrationValidation.validatePaper(
                            frame[0], paperSprite.getIconWidth(), paperSprite.getIconHeight(),
                            rendered, 16 * scale, 16 * scale);
            JeiExportMod.LOGGER.info(
                    "[jeiexport] ITEM_RENDER_CALIBRATION_PASSED sentinel=minecraft:paper " +
                            "source={}x{} sourceVisible={} sourceTransparent={} sourceColors={} " +
                            "rendered={}x{} renderedVisible={} renderedTransparent={} " +
                            "renderedColors={} matchingSourceColorPixels={}",
                    report.sourceWidth, report.sourceHeight,
                    report.sourceVisible, report.sourceTransparent, report.sourceColors,
                    report.renderedWidth, report.renderedHeight,
                    report.renderedVisible, report.renderedTransparent, report.renderedColors,
                    report.matchingSourceColorPixels);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            String message = throwable.getMessage();
            if (message == null || !message.startsWith("ITEM_RENDER_CALIBRATION_FAILED:")) {
                message = "ITEM_RENDER_CALIBRATION_FAILED: " + throwable;
            }
            IOException failure = new IOException(
                    message + "; refusing to publish item icons or HEI recipe previews from a " +
                            "degraded OpenGL texture state",
                    throwable);
            JeiExportMod.LOGGER.error("[jeiexport] {}", failure.getMessage(), throwable);
            throw failure;
        }
    }

    private static String namespace(String resourceId) {
        int colon = resourceId.indexOf(':');
        return colon > 0 ? resourceId.substring(0, colon) : "unknown";
    }

    private static String resourcePath(String resourceId) {
        int colon = resourceId.indexOf(':');
        return colon >= 0 && colon + 1 < resourceId.length()
                ? resourceId.substring(colon + 1)
                : Naming.hash8(resourceId);
    }

    private static String prefix(IIngredientType<?> type) {
        if (type == VanillaTypes.ITEM) {
            return "item";
        }
        if (type == VanillaTypes.FLUID) {
            return "fluid";
        }
        // ENCHANT was added to VanillaTypes after JEI 4.12. Comparing the stable Minecraft
        // ingredient class preserves the same key prefix without linking against that newer field.
        if (type.getIngredientClass() == EnchantmentData.class) {
            return "enchant";
        }
        String className = type.getIngredientClass().getName().toLowerCase(Locale.ROOT);
        return "custom_" + Naming.sanitize(className) + "_" + Naming.hash8(className);
    }

    static final class ResolvedIngredient<T> implements CanonicalKeyOrdering.Entry {
        final IIngredientType<T> type;
        final T ingredient;
        final String prefix;
        final String uid;
        final String rawResourceId;
        final String displayName;
        final String modId;
        final String key;
        String canonicalPayload;

        ResolvedIngredient(IIngredientType<T> type, T ingredient, String prefix, String uid,
                           String rawResourceId, String displayName, String modId) {
            this.type = type;
            this.ingredient = ingredient;
            this.prefix = prefix;
            this.uid = uid;
            this.rawResourceId = rawResourceId;
            this.displayName = displayName;
            this.modId = modId;
            this.key = prefix + "|" + uid;
        }

        String key() {
            return key;
        }

        @Override
        public String canonicalKey() {
            return key;
        }

        @Override
        public String canonicalPayload() {
            String payload = canonicalPayload;
            if (payload == null) {
                StringBuilder builder = new StringBuilder();
                appendPayload(builder, prefix);
                appendPayload(builder, uid);
                appendPayload(builder, rawResourceId);
                appendPayload(builder, displayName);
                appendPayload(builder, modId);
                appendPayload(builder, ingredient == null ? null : ingredient.getClass().getName());
                payload = builder.toString();
                canonicalPayload = payload;
            }
            return payload;
        }

        private static void appendPayload(StringBuilder target, String value) {
            if (value == null) {
                target.append("-1:");
            } else {
                target.append(value.length()).append(':').append(value);
            }
        }
    }

    void close() throws IOException {
        if (!closed) {
            writer.endArray().endObject();
            writer.close();
            closed = true;
        }
    }
}
