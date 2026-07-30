package com.recipetree.jeiexport;

import com.google.gson.stream.JsonWriter;
import com.mojang.blaze3d.platform.NativeImage;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * The ingredient catalog: every unique ingredient (items, fluids, and any custom JEI
 * ingredient type like Mekanism gases) gets one entry in items.json plus a rendered icon.
 * Both the items phase and the recipe phase feed it, so every key referenced by a recipe
 * is guaranteed to exist in the catalog.
 */
final class ItemCatalog {
    private final ExportContext ctx;
    final IIngredientManager manager;
    private final JsonWriter writer;
    private final Set<String> known = new HashSet<>();
    private int count;

    ItemCatalog(ExportContext ctx, IIngredientManager manager) throws IOException {
        this.ctx = ctx;
        this.manager = manager;
        this.writer = new JsonWriter(Files.newBufferedWriter(ctx.root.resolve("items.json")));
        writer.beginObject();
        writer.name("items");
        writer.beginArray();
    }

    int count() {
        return count;
    }

    /** Returns the stable key for this ingredient, writing catalog entry + icon on first sight. */
    String ensure(ITypedIngredient<?> typed) {
        return ensureTyped(typed);
    }

    private <V> String ensureTyped(ITypedIngredient<V> typed) {
        IIngredientType<V> type = typed.getType();
        V ingredient = typed.getIngredient();
        IIngredientHelper<V> helper = manager.getIngredientHelper(type);
        String prefix = IngredientKeys.typePrefix(type);

        String uid;
        try {
            uid = helper.getUniqueId(ingredient, UidContext.Ingredient);
        } catch (Throwable t) {
            throw new IllegalStateException("ITEM_IDENTITY: JEI helper failed to identify "
                    + ingredient.getClass().getName(), t);
        }
        if (uid == null || uid.isBlank()) {
            throw new IllegalStateException("ITEM_IDENTITY: JEI helper returned a null/blank id for "
                    + ingredient.getClass().getName());
        }
        String key = prefix + "|" + uid;
        if (!known.add(key)) {
            return key;
        }

        String name;
        try {
            name = helper.getDisplayName(ingredient);
        } catch (Throwable t) {
            ctx.failure("ingredient display name " + key + ": " + t + "; using unique id");
            name = uid;
        }
        if (name == null || name.isBlank()) {
            ctx.failure("ingredient display name " + key + " was null/blank; using unique id");
            name = uid;
        }
        ResourceLocation rl = null;
        try {
            rl = helper.getResourceLocation(ingredient);
        } catch (Throwable t) {
            ctx.failure("ingredient resource id " + key + ": " + t + "; using unique id");
        }
        String mod;
        try {
            mod = helper.getDisplayModId(ingredient);
        } catch (Throwable t) {
            ctx.failure("ingredient mod id " + key + ": " + t + "; deriving namespace");
            mod = rl != null ? rl.getNamespace() : "unknown";
        }
        if (mod == null || mod.isBlank()) {
            ctx.failure("ingredient mod id " + key
                    + " was null/blank; deriving namespace");
            mod = rl != null ? rl.getNamespace() : "unknown";
        }

        String icon = null;
        try {
            icon = renderIcon(type, ingredient, prefix, rl, uid);
        } catch (Throwable t) {
            ctx.failure("icon " + key + ": " + t);
        }

        try {
            writer.beginObject();
            writer.name("k").value(key);
            writer.name("id").value(rl != null ? rl.toString() : uid);
            writer.name("n").value(name);
            writer.name("m").value(mod);
            if (!"item".equals(prefix)) {
                writer.name("t").value(prefix);
            }
            if (icon != null) {
                writer.name("icon").value(icon);
            }
            writer.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed writing items.json", e);
        }
        count++;
        return key;
    }

    @Nullable
    private <V> String renderIcon(IIngredientType<V> type, V ingredient, String prefix,
                                  @Nullable ResourceLocation rl, String uid) {
        IIngredientRenderer<V> renderer = manager.getIngredientRenderer(type);
        int w = Math.max(16, renderer.getWidth());
        int h = Math.max(16, renderer.getHeight());
        int scale = ctx.iconScale;

        String dir = "icons/" + prefix + "/" + (rl != null ? Naming.sanitize(rl.getNamespace()) : "unknown");
        String base = rl != null ? rl.getPath() : Naming.hash8(uid);
        if (rl == null || !uid.equals(rl.toString())) {
            // Subtyped ingredient (potion, enchanted book, NBT variants...): disambiguate.
            base = base + "__" + Naming.hash8(uid);
        }
        String rel = ctx.uniquePath(dir, base, ".png");

        NativeImage image = ctx.renderer.capture(w * scale, h * scale, g -> {
            g.pose().pushPose();
            try {
                g.pose().scale(scale, scale, 1f);
                renderer.render(g, ingredient);
            } finally {
                g.pose().popPose();
            }
        });
        ctx.saveImage(image, ctx.root.resolve(rel));
        return rel;
    }

    void close() throws IOException {
        writer.endArray();
        writer.endObject();
        writer.close();
    }
}
