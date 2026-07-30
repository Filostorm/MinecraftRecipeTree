package com.recipetree.jeiexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.blaze3d.platform.NativeImage;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared state for one export run: output paths, the offscreen renderer, async
 * image writing, the ingredient catalog and the item->recipe index.
 */
final class ExportContext {
    static final int MAX_PENDING_IMAGE_WRITES = 32;
    final Path root;
    final Path finalRoot;
    final int iconScale;
    final PackIdentity packIdentity;
    final int recipeScale = ExportManifestContract.RECIPE_SCALE;
    final int mobCanvas = ExportManifestContract.MOB_CANVAS;

    final OffscreenRenderer renderer = new OffscreenRenderer();
    final AtomicInteger pendingWrites = new AtomicInteger();
    private final Semaphore imageWritePermits = new Semaphore(MAX_PENDING_IMAGE_WRITES);
    final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    /** All relative paths handed out so far, to keep file/dir names collision-free. */
    final Set<String> usedPaths = new HashSet<>();

    /** key -> recipes that produce / use it. Filled by recipe-ish phases, written as index.json. */
    final Map<String, IndexEntry> recipeIndex = new TreeMap<>();

    /** All exported categories (JEI ones + synthetic ones like trading), written as categories.json. */
    private final JsonArray categoriesJson = new JsonArray();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    int recipeCount;
    int categoryCount;
    int mobCount;
    int blockDropsCount;

    @Nullable
    private ItemCatalog catalog;

    static final class IndexEntry {
        final List<int[]> produced = new ArrayList<>();
        final List<int[]> used = new ArrayList<>();
    }

    ExportContext(Path finalRoot, int iconScale, PackIdentity packIdentity) throws IOException {
        this.finalRoot = finalRoot.toAbsolutePath().normalize();
        Path parent = this.finalRoot.getParent();
        if (parent == null) {
            throw new IOException("Export output must have a parent directory: " + this.finalRoot);
        }
        this.root = parent.resolve("." + this.finalRoot.getFileName()
                + ".staging-" + UUID.randomUUID()).normalize();
        this.iconScale = iconScale;
        this.packIdentity = packIdentity;
        if (!root.getParent().equals(parent)) {
            throw new IOException("Staging output escaped the export directory: " + root);
        }
        Files.createDirectories(root);
        JeiExportMod.LOGGER.info("[jeiexport] Writing transactional snapshot to {}", root);
    }

    ItemCatalog catalog(IIngredientManager manager) throws IOException {
        if (catalog == null) {
            catalog = new ItemCatalog(this, manager);
        }
        return catalog;
    }

    /** Queue a PNG write on the IO pool; the image is closed after writing. */
    void saveImage(NativeImage image, Path file) {
        try {
            imageWritePermits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            image.close();
            failure("write scheduling " + root.relativize(file)
                    + ": interrupted while applying image-write backpressure");
            return;
        }
        pendingWrites.incrementAndGet();
        try {
            Util.ioPool().execute(() -> {
                try {
                    Files.createDirectories(file.getParent());
                    image.writeToFile(file);
                    try {
                        PngIntegrity.verify(file);
                    } catch (IOException nativeWriteFailure) {
                        JeiExportMod.LOGGER.warn(
                                "[jeiexport] Native PNG verification failed for {}; retrying with Java ImageIO",
                                root.relativize(file), nativeWriteFailure);
                        writePngWithImageIo(image, file);
                        PngIntegrity.verify(file);
                    }
                } catch (Throwable t) {
                    failure("write " + root.relativize(file) + ": " + t);
                } finally {
                    image.close();
                    pendingWrites.decrementAndGet();
                    imageWritePermits.release();
                }
            });
        } catch (RuntimeException e) {
            image.close();
            pendingWrites.decrementAndGet();
            imageWritePermits.release();
            failure("write scheduling " + root.relativize(file) + ": " + e);
        }
    }

    private static void writePngWithImageIo(NativeImage image, Path file) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = image.getPixelRGBA(x, y);
                row[x] = (abgr & 0xFF000000)
                        | ((abgr & 0x000000FF) << 16)
                        | (abgr & 0x0000FF00)
                        | ((abgr & 0x00FF0000) >>> 16);
            }
            buffered.setRGB(0, y, width, 1, row, 0, width);
        }
        if (!ImageIO.write(buffered, "PNG", file.toFile())) {
            throw new IOException("No Java ImageIO PNG writer is available");
        }
    }

    void failure(String message) {
        failures.add(message);
        JeiExportMod.LOGGER.warn("[jeiexport] {}", message);
    }

    /** Reserve a unique sanitized relative file path like "icons/item/minecraft/stone.png". */
    String uniquePath(String dir, String baseName, String extension) {
        String base = dir + "/" + Naming.sanitize(baseName);
        String candidate = base + extension;
        int n = 2;
        while (!usedPaths.add(candidate)) {
            candidate = base + "_" + n++ + extension;
        }
        return candidate;
    }

    /** Appends a category entry and returns its index (used in index.json refs). */
    int registerCategory(JsonObject category) {
        categoriesJson.add(category);
        categoryCount = categoriesJson.size();
        return categoriesJson.size() - 1;
    }

    void indexRecipe(String key, boolean isOutput, int categoryIndex, int recipeIndexInCategory) {
        IndexEntry entry = recipeIndex.computeIfAbsent(key, k -> new IndexEntry());
        (isOutput ? entry.produced : entry.used).add(new int[]{categoryIndex, recipeIndexInCategory});
    }

    int catalogCount() {
        return catalog == null ? 0 : catalog.count();
    }

    /** Closes writers and writes index.json, failures.json and manifest.json. */
    void finishAndClose(boolean aborted, long durationMs) throws IOException {
        int itemCount = catalogCount();
        if (catalog != null) {
            catalog.close();
            catalog = null;
        }
        renderer.close();

        if (categoriesJson.size() > 0) {
            JsonObject categoriesRoot = new JsonObject();
            categoriesRoot.add("categories", categoriesJson);
            try (var writer = Files.newBufferedWriter(root.resolve("categories.json"))) {
                GSON.toJson(categoriesRoot, writer);
            }
        }

        if (!recipeIndex.isEmpty()) {
            try (JsonWriter w = new JsonWriter(Files.newBufferedWriter(root.resolve("index.json")))) {
                w.beginObject();
                for (Map.Entry<String, IndexEntry> e : recipeIndex.entrySet()) {
                    w.name(e.getKey());
                    w.beginObject();
                    writeRefs(w, "p", e.getValue().produced);
                    writeRefs(w, "u", e.getValue().used);
                    w.endObject();
                }
                w.endObject();
            }
        }

        List<String> failuresCopy;
        synchronized (failures) {
            failuresCopy = new ArrayList<>(failures);
        }
        try (JsonWriter w = new JsonWriter(Files.newBufferedWriter(root.resolve("failures.json")))) {
            w.beginArray();
            for (String f : failuresCopy) {
                w.value(f);
            }
            w.endArray();
        }

        try (JsonWriter w = new JsonWriter(Files.newBufferedWriter(root.resolve("manifest.json")))) {
            w.setIndent("  ");
            w.beginObject();
            w.name("format").value(1);
            w.name("generatedAt").value(Instant.now().toString());
            w.name("durationMs").value(durationMs);
            w.name("aborted").value(aborted);
            w.name("pack").beginObject()
                    .name("name").value(packIdentity.name());
            if (packIdentity.version() != null) {
                w.name("version").value(packIdentity.version());
            }
            w.name("identitySource").value(packIdentity.identitySource())
                    .endObject();
            w.name("minecraft").value(SharedConstants.getCurrentVersion().getName());
            w.name("settings").beginObject()
                    .name("iconScale").value(iconScale)
                    .name("recipeScale").value(recipeScale)
                    .name("mobCanvas").value(mobCanvas)
                    .endObject();
            w.name("counts").beginObject()
                    .name("items").value(itemCount)
                    .name("recipes").value(recipeCount)
                    .name("categories").value(categoryCount)
                    .name("mobs").value(mobCount)
                    .name("blockDrops").value(blockDropsCount)
                    .name("failures").value(failuresCopy.size())
                    .endObject();
            ExportManifestContract.writeDiagnostics(w, failuresCopy.size());
            w.name("mods").beginObject();
            for (var mod : ModList.get().getMods().stream()
                    .sorted(Comparator.comparing(info -> info.getModId()))
                    .toList()) {
                w.name(mod.getModId()).value(mod.getDisplayName());
            }
            w.endObject();
            w.endObject();
        }
    }

    /**
     * Replaces the completed snapshot without exposing a half-written directory.
     * Both moves stay on the same filesystem; a prior snapshot is restored if the
     * staging promotion fails.
     */
    void publishCompletedSnapshot() throws IOException {
        if (pendingWrites.get() != 0) {
            throw new IOException("Cannot publish with " + pendingWrites.get() + " pending image writes");
        }
        Path parent = finalRoot.getParent();
        Path backup = parent.resolve("." + finalRoot.getFileName()
                + ".previous-" + UUID.randomUUID()).normalize();
        boolean previousMoved = false;
        try {
            if (Files.exists(finalRoot)) {
                moveWithLoggedAtomicFallback(finalRoot, backup, "preserve previous completed snapshot");
                previousMoved = true;
            }
            moveWithLoggedAtomicFallback(root, finalRoot, "publish completed snapshot");
        } catch (IOException promotionFailure) {
            if (previousMoved && Files.exists(backup) && !Files.exists(finalRoot)) {
                try {
                    moveWithLoggedAtomicFallback(backup, finalRoot, "restore previous completed snapshot");
                } catch (IOException restoreFailure) {
                    promotionFailure.addSuppressed(restoreFailure);
                    JeiExportMod.LOGGER.error(
                            "[jeiexport] Snapshot promotion and previous-snapshot restoration both failed; "
                                    + "the preserved snapshot remains at {}",
                            backup, restoreFailure);
                }
            }
            throw promotionFailure;
        }
        if (previousMoved) {
            try {
                deleteTree(backup);
            } catch (IOException cleanupFailure) {
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] Published the new snapshot but could not remove previous snapshot backup {}",
                        backup, cleanupFailure);
            }
        }
        JeiExportMod.LOGGER.info("[jeiexport] Published completed snapshot to {}", finalRoot);
    }

    private static void moveWithLoggedAtomicFallback(Path source, Path destination, String operation)
            throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Atomic move unavailable while {}; using a same-filesystem non-atomic move: {}",
                    operation, unsupported.toString());
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    private static void writeRefs(JsonWriter w, String name, List<int[]> refs) throws IOException {
        w.name(name);
        w.beginArray();
        for (int[] ref : refs) {
            w.beginArray();
            w.value(ref[0]);
            w.value(ref[1]);
            w.endArray();
        }
        w.endArray();
    }
}
