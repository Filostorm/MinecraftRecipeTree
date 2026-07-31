package com.recipetree.reiexport118;

import com.google.gson.stream.JsonWriter;
import com.mojang.blaze3d.platform.NativeImage;
import com.recipetree.reiexport118.compat.Mm2EntryCanonicalization;
import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClock;
import com.recipetree.reiexport118.compat.UpstreamNativeIconContract;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

final class ItemCatalog implements AutoCloseable {
    record CanonicalIdentity(
            ResourceLocation typeId,
            ResourceLocation identifier,
            String serialized,
            String key) {
    }

    private final ExportContext context;
    private final JsonWriter writer;
    private final NativeSpriteIconCorrector nativeSpriteIconCorrector =
            new NativeSpriteIconCorrector();
    private final Map<String, String> identityByKey = new HashMap<>();
    private int count;
    private boolean closed;

    ItemCatalog(ExportContext context) throws IOException {
        this.context = context;
        this.writer = new JsonWriter(Files.newBufferedWriter(context.root.resolve("items.json")));
        writer.beginObject();
        writer.name("items").beginArray();
    }

    int count() {
        return count;
    }

    String ensure(EntryStack<?> original) {
        if (original == null || original.isEmpty()) {
            throw new IllegalArgumentException("Cannot catalog an empty REI entry stack.");
        }
        EntryStack<?> renderStack = copyForCatalogIconRender(original);
        CanonicalIdentity canonical = canonicalIdentity(original, context);
        ResourceLocation typeId = canonical.typeId();
        ResourceLocation identifier = canonical.identifier();
        String serialized = canonical.serialized();
        String key = canonical.key();
        String previousIdentity = identityByKey.putIfAbsent(key, serialized);
        if (previousIdentity != null && !previousIdentity.equals(serialized)) {
            throw new IllegalStateException("Item catalog SHA-256 prefix collision for " + key
                    + "; refusing to merge distinct REI identities");
        }
        if (previousIdentity != null) {
            return key;
        }

        String name;
        try {
            name = renderStack.asFormattedText().getString();
            if (name == null || name.isBlank()) {
                context.warning("Display-name lookup returned blank text for " + key
                        + "; publishing the explicit registry identifier " + identifier + " instead");
                name = identifier.toString();
            }
        } catch (Throwable throwable) {
            name = identifier.toString();
            context.warning("Display-name lookup failed for " + key + ": " + throwable);
        }
        String mod;
        try {
            mod = renderStack.getContainingNamespace();
        } catch (Throwable throwable) {
            mod = identifier.getNamespace();
            context.warning("Containing-namespace lookup failed for " + key + ": " + throwable);
        }

        String icon = null;
        try {
            icon = renderIcon(renderStack, typeId, identifier, serialized);
        } catch (Throwable throwable) {
            context.failure("Icon render " + key + ": " + throwable);
        }

        try {
            writer.beginObject();
            writer.name("k").value(key);
            writer.name("id").value(identifier.toString());
            writer.name("n").value(name);
            writer.name("m").value(mod);
            String prefix = typePrefix(typeId);
            if (!"item".equals(prefix)) {
                writer.name("t").value(prefix);
            }
            if (icon != null) {
                writer.name("icon").value(icon);
            }
            writer.endObject();
        } catch (IOException exception) {
            throw new UncheckedIOException("Writing items.json failed", exception);
        }
        count++;
        return key;
    }

    /**
     * Produces the amount-neutral stack used only by the standalone item catalog icon.
     *
     * <p>{@link EntryStack#normalize()} cannot be used for rendering here: REI 8.4.778
     * intentionally drops the source stack's settings while normalizing, including a
     * custom renderer supplied through {@code EntryStack.Settings}. {@link EntryStack#copy()}
     * retains those settings and deep-copies the built-in {@link ItemStack} value. The
     * copied item count is then set to one so a recipe quantity is not rasterized into
     * the reusable 16x16 catalog asset. Recipe serialization and native REI layout
     * rendering continue to receive their original stacks and amounts.</p>
     */
    static EntryStack<?> copyForCatalogIconRender(EntryStack<?> original) {
        if (original == null || original.isEmpty()) {
            throw new IllegalArgumentException("Cannot render an empty REI catalog entry stack.");
        }
        EntryStack<?> renderStack = original.copy();
        if (renderStack == null || renderStack.isEmpty()) {
            throw new IllegalStateException("REI EntryStack.copy() returned an empty catalog render stack.");
        }
        Object originalValue = original.getValue();
        Object renderValue = renderStack.getValue();
        if (renderValue instanceof ItemStack renderItem) {
            if (renderValue == originalValue) {
                throw new IllegalStateException(
                        "REI EntryStack.copy() aliased the mutable ItemStack used for catalog rendering.");
            }
            int originalCount = originalValue instanceof ItemStack originalItem
                    ? originalItem.getCount()
                    : Integer.MIN_VALUE;
            renderItem.setCount(1);
            if (renderItem.getCount() != 1) {
                throw new IllegalStateException("Catalog ItemStack amount normalization did not produce count=1.");
            }
            if (originalValue instanceof ItemStack originalItem && originalItem.getCount() != originalCount) {
                throw new IllegalStateException("Catalog icon normalization mutated the source ItemStack amount.");
            }
        }
        Mm2EntryCanonicalization.normalizeExporterOwnedEntry(renderStack);
        return renderStack;
    }

    static CanonicalIdentity canonicalIdentity(EntryStack<?> original, ExportContext context) {
        if (original == null || original.isEmpty()) {
            throw new IllegalArgumentException("Cannot identify an empty REI entry stack.");
        }
        EntryStack<?> identityStack = Mm2EntryCanonicalization.canonicalIdentityCopy(original);
        ResourceLocation typeId = identityStack.getType().getId();
        ResourceLocation identifier = identityStack.getIdentifier();
        String serialized = stableSerializedIdentity(
                identityStack, typeId, identifier, context);
        String key = typePrefix(typeId) + "|" + identifier + "|" + Naming.hash128(serialized);
        return new CanonicalIdentity(typeId, identifier, serialized, key);
    }

    private static String stableSerializedIdentity(
            EntryStack<?> stack,
            ResourceLocation typeId,
            ResourceLocation identifier,
            ExportContext context) {
        try {
            if (!stack.supportSaving()) {
                throw new IllegalStateException("entry serializer reports supportSaving=false");
            }
            CompoundTag tag = stack.saveStack();
            return tag.toString();
        } catch (Throwable throwable) {
            context.identityFallbacks++;
            String fallback = typeId + "|" + identifier + "|" + stack.getValue().getClass().getName() + "|" + stack;
            context.failure("Canonical REI serialization failed for " + typeId + " " + identifier
                    + "; using a diagnostic-only textual identity and rejecting publication: " + throwable);
            return fallback;
        }
    }

    private String renderIcon(
            EntryStack<?> stack,
            ResourceLocation typeId,
            ResourceLocation identifier,
            String serialized) {
        int logicalSize = 16;
        int scale = context.request.iconScale;
        NativeImage image = context.renderer.capture(logicalSize * scale, logicalSize * scale, pose -> {
            if (Mm2OffscreenGlintClock.isCaptureActive()
                    && stack.getValue() instanceof ItemStack itemStack
                    && itemStack.hasFoil()
                    && isVanillaPotion(identifier)) {
                Mm2OffscreenGlintClock.requireKnownSampleInterception(
                        "native ItemStack.hasFoil=true; id=" + identifier
                                + "; identityHash=" + Naming.hash128(serialized));
            }
            pose.pushPose();
            try {
                pose.scale(scale, scale, 1f);
                stack.render(pose, new Rectangle(0, 0, logicalSize, logicalSize), -10_000, -10_000, 0f);
            } finally {
                pose.popPose();
            }
        });

        if (nativeSpriteIconCorrector.mayCompare(typeId)) {
            try {
                int nativeVisiblePixels = ExportContext.visiblePixelCount(image);
                NativeSpriteIconCorrector.Correction correction =
                        nativeSpriteIconCorrector.correctAfterNativeCapture(
                                stack,
                                typeId,
                                identifier,
                                image,
                                scale,
                                nativeVisiblePixels
                        );
                if (correction != null) {
                    image.close();
                    image = correction.image();
                    context.recordNativeIconCorrection(correction.evidence());
                }
            } catch (RuntimeException | Error throwable) {
                image.close();
                throw throwable;
            }
        }

        int visiblePixels = ExportContext.visiblePixelCount(image);
        UpstreamNativeIconContract.Identity nativeIdentity = nativeIconIdentity(
                stack,
                typeId,
                identifier
        );
        UpstreamNativeIconContract.Omission omission =
                UpstreamNativeIconContract.omission(nativeIdentity, visiblePixels);
        if (omission != null) {
            image.close();
            context.recordUpstreamNativeIconUnavailable(omission);
            return null;
        }

        String prefix = typePrefix(typeId);
        String directory = "icons/" + Naming.sanitize(prefix) + "/" + Naming.sanitize(identifier.getNamespace());
        String base = Naming.sanitize(identifier.getPath()) + "__" + Naming.hash128(serialized);
        String relative = context.uniquePath(directory, base, ".png");
        context.saveImage(image, relative, true);
        return relative;
    }

    private static boolean isVanillaPotion(ResourceLocation identifier) {
        if (!"minecraft".equals(identifier.getNamespace())) {
            return false;
        }
        return switch (identifier.getPath()) {
            case "potion", "splash_potion", "lingering_potion" -> true;
            default -> false;
        };
    }

    private static UpstreamNativeIconContract.Identity nativeIconIdentity(
            EntryStack<?> stack,
            ResourceLocation typeId,
            ResourceLocation identifier
    ) {
        Object value = stack.getValue();
        if (value == null) {
            throw new IllegalStateException("Native REI icon entry has a null runtime value for "
                    + typeId + " " + identifier);
        }
        String itemClass = null;
        String blockClass = null;
        if (value instanceof ItemStack itemStack) {
            itemClass = itemStack.getItem().getClass().getName();
            if (itemStack.getItem() instanceof BlockItem blockItem) {
                blockClass = blockItem.getBlock().getClass().getName();
            }
        }
        return new UpstreamNativeIconContract.Identity(
                typeId,
                identifier,
                value.getClass().getName(),
                itemClass,
                blockClass
        );
    }

    private static String typePrefix(ResourceLocation typeId) {
        if ("minecraft".equals(typeId.getNamespace())) {
            return Naming.sanitize(typeId.getPath());
        }
        return Naming.sanitize(typeId.getNamespace() + "/" + typeId.getPath());
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                writer.endArray();
                writer.endObject();
                writer.close();
            } finally {
                nativeSpriteIconCorrector.close();
            }
        }
    }
}
