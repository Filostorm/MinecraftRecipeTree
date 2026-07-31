package com.recipetree.jeiexport112;

import com.google.gson.stream.JsonWriter;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
        BufferedImage image = renderIngredient(renderer, catalogIngredient, scale);
        String unusableReason = RenderedIconValidation.unusableReason(image);
        if (RenderedIconValidation.FULLY_TRANSPARENT.equals(unusableReason)) {
            BufferedImage overscan = renderIngredientWithOverscan(renderer, catalogIngredient, scale);
            if (RenderedIconValidation.unusableReason(overscan) == null) {
                image = overscan;
                unusableReason = null;
                context.warning("NATIVE_ICON_OVERSCAN_RECOVERY_APPLIED ingredient " + key +
                        ": the exact 16x16 HEI draw was fully transparent; a second native HEI draw " +
                        "on a 32x32 logical framebuffer produced visible pixels in the exact 16x16 " +
                        "center crop; no interpolation or synthetic pixels were used");
            }
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
        if (type != VanillaTypes.ITEM) {
            return ingredient;
        }
        if (!(ingredient instanceof ItemStack)) {
            throw new IllegalArgumentException("HEI ITEM ingredient was not an ItemStack: " +
                    (ingredient == null ? "null" : ingredient.getClass().getName()));
        }
        ItemStack renderStack = ((ItemStack) ingredient).copy();
        renderStack.setCount(1);
        return (T) renderStack;
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
