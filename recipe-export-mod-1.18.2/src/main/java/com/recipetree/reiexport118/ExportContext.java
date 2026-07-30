package com.recipetree.reiexport118;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.blaze3d.platform.NativeImage;
import com.recipetree.reiexport118.compat.NativeSpriteIconContract;
import com.recipetree.reiexport118.compat.NativeSpriteIconCorrectionAudit;
import com.recipetree.reiexport118.compat.UpstreamNativeIconContract;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class ExportContext implements AutoCloseable {
    static final int MOB_CANVAS = 256;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    static final class IndexEntry {
        final List<int[]> produced = new ArrayList<>();
        final List<int[]> used = new ArrayList<>();
    }

    final ExportRequest request;
    final ExporterBuildIdentity exporterBuildIdentity;
    final Path gameDirectory;
    final Path finalRoot;
    final Path root;
    final OffscreenRenderer renderer = new OffscreenRenderer();
    final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    final Map<String, IndexEntry> recipeIndex = new HashMap<>();
    final Set<String> usedPaths = new HashSet<>();
    final JsonArray categories = new JsonArray();
    final NativeSpriteIconCorrectionAudit nativeIconCorrectionAudit =
            new NativeSpriteIconCorrectionAudit();

    private final PngWriter pngWriter;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean finalizationStarted = new AtomicBoolean();
    private ItemCatalog catalog;

    int recipeCount;
    int categoryCount;
    int skippedEmptyEntries;
    int identityFallbacks;
    int transparentIcons;

    ExportContext(Path gameDirectory, ExportRequest request, long claimTimestamp) throws IOException {
        this.request = request;
        this.exporterBuildIdentity = ExporterBuildIdentity.loadRuntime();
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.finalRoot = this.gameDirectory.resolve(request.output).normalize();
        if (!finalRoot.startsWith(this.gameDirectory)) {
            throw new IOException("Resolved output escapes the game directory: " + finalRoot);
        }
        if (Files.exists(finalRoot)) {
            throw new IOException("Refusing to replace an existing export; choose a new output path: " + finalRoot);
        }
        Path parent = finalRoot.getParent();
        if (parent == null) {
            throw new IOException("Export output has no parent: " + finalRoot);
        }
        Files.createDirectories(parent);
        this.root = parent.resolve("." + finalRoot.getFileName() + ".staging-" + claimTimestamp);
        if (Files.exists(root)) {
            throw new IOException("Transactional staging path already exists: " + root);
        }
        Files.createDirectory(root);
        this.pngWriter = new PngWriter(this, request.pngThreads, request.pngQueueCapacity);
    }

    ItemCatalog catalog() throws IOException {
        if (catalog == null) {
            catalog = new ItemCatalog(this);
        }
        return catalog;
    }

    int catalogCount() {
        return catalog == null ? 0 : catalog.count();
    }

    String uniquePath(String directory, String baseName, String extension) {
        String base = directory + "/" + Naming.sanitize(baseName);
        String candidate = base + extension;
        int suffix = 2;
        while (!usedPaths.add(candidate)) {
            candidate = base + "_" + suffix++ + extension;
        }
        return candidate;
    }

    void saveImage(NativeImage image, String relativePath, boolean requireVisible) {
        if (requireVisible && !hasVisiblePixel(image)) {
            transparentIcons++;
            failure("Rendered icon is fully transparent: " + relativePath);
        }
        pngWriter.submit(image, root.resolve(relativePath));
    }

    static boolean hasVisiblePixel(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (NativeImage.getA(image.getPixelRGBA(x, y)) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    static int visiblePixelCount(NativeImage image) {
        int visiblePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (NativeImage.getA(image.getPixelRGBA(x, y)) != 0) {
                    visiblePixels++;
                }
            }
        }
        return visiblePixels;
    }

    void recordNativeIconCorrection(NativeSpriteIconContract.CorrectionEvidence evidence) {
        nativeIconCorrectionAudit.record(evidence, this::warning);
    }

    void recordUpstreamNativeIconUnavailable(UpstreamNativeIconContract.Omission omission) {
        UpstreamNativeIconContract.Identity identity = omission.identity();
        warning("UPSTREAM_NATIVE_ICON_UNAVAILABLE id=" + identity.identifier()
                + " type=" + identity.typeId()
                + " valueClass=" + identity.valueClass()
                + " itemClass=" + diagnosticClassName(identity.itemClass())
                + " blockClass=" + diagnosticClassName(identity.blockClass())
                + " visiblePixels=0"
                + " contract=" + omission.reason()
                + "; exact native 16x16 render has zero visible pixels; omitted PNG/icon field; "
                + "named UI fallback used");
    }

    private static String diagnosticClassName(String className) {
        return className == null ? "<none>" : className;
    }

    int registerCategory(JsonObject category) {
        categories.add(category);
        categoryCount = categories.size();
        return categories.size() - 1;
    }

    void indexRecipe(String key, boolean output, int categoryIndex, int recipeIndexInCategory) {
        IndexEntry entry = this.recipeIndex.computeIfAbsent(key, ignored -> new IndexEntry());
        (output ? entry.produced : entry.used).add(new int[]{categoryIndex, recipeIndexInCategory});
    }

    void failure(String message) {
        failures.add(message);
        ReiExportMod.LOGGER.error("[reiexport] {}", message);
    }

    void warning(String message) {
        warnings.add(message);
        ReiExportMod.LOGGER.warn("[reiexport] {}", message);
    }

    String relative(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(root.toAbsolutePath().normalize())
                ? root.toAbsolutePath().normalize().relativize(normalized).toString()
                : normalized.toString();
    }

    int pendingPngWrites() {
        return pngWriter.pending();
    }

    void finish(boolean aborted, long durationMs, ExportPlan plan) throws IOException, InterruptedException {
        if (!finalizationStarted.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Export finalization was already attempted; refusing a second pass over closed metadata writers.");
        }
        if (catalog == null) {
            catalog();
        }
        catalog.close();
        catalog = null;
        pngWriter.awaitCompletion();
        renderer.close();

        JsonObject categoryRoot = new JsonObject();
        categoryRoot.add("categories", categories);
        try (Writer writer = Files.newBufferedWriter(root.resolve("categories.json"))) {
            GSON.toJson(categoryRoot, writer);
        }

        JsonObject mobRoot = new JsonObject();
        mobRoot.add("mobs", new JsonArray());
        writeJsonObject(root.resolve("mobs.json"), mobRoot);

        JsonObject blockDropRoot = new JsonObject();
        blockDropRoot.add("blocks", new JsonObject());
        writeJsonObject(root.resolve("blockdrops.json"), blockDropRoot);

        writeIndex();
        writeStringArray(root.resolve("failures.json"), failures);
        writeStringArray(root.resolve("warnings.json"), warnings);
        exporterBuildIdentity.writeTo(root);
        writeManifest(aborted, durationMs, plan);
    }

    private void writeIndex() throws IOException {
        List<Map.Entry<String, IndexEntry>> entries = new ArrayList<>(recipeIndex.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(root.resolve("index.json")))) {
            writer.beginObject();
            for (Map.Entry<String, IndexEntry> mapEntry : entries) {
                writer.name(mapEntry.getKey()).beginObject();
                writeRefs(writer, "p", mapEntry.getValue().produced);
                writeRefs(writer, "u", mapEntry.getValue().used);
                writer.endObject();
            }
            writer.endObject();
        }
    }

    private static void writeRefs(JsonWriter writer, String name, List<int[]> refs) throws IOException {
        refs.sort(Comparator.<int[]>comparingInt(ref -> ref[0]).thenComparingInt(ref -> ref[1]));
        writer.name(name).beginArray();
        for (int[] ref : refs) {
            writer.beginArray().value(ref[0]).value(ref[1]).endArray();
        }
        writer.endArray();
    }

    private static void writeStringArray(Path path, List<String> values) throws IOException {
        List<String> snapshot;
        synchronized (values) {
            snapshot = List.copyOf(values);
        }
        try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(path))) {
            writer.beginArray();
            for (String value : snapshot) {
                writer.value(value);
            }
            writer.endArray();
        }
    }

    private static void writeJsonObject(Path path, JsonObject value) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(value, writer);
        }
    }

    private void writeManifest(boolean aborted, long durationMs, ExportPlan plan) throws IOException {
        try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(root.resolve("manifest.json")))) {
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("format").value(1);
            writer.name("generatedAt").value(Instant.now().toString());
            writer.name("durationMs").value(durationMs);
            writer.name("aborted").value(aborted);
            writer.name("minecraft").value(SharedConstants.getCurrentVersion().getName());
            writer.name("pack").beginObject()
                    .name("name").value(request.packName)
                    .name("version").value(request.packVersion)
                    .name("identitySource").value("explicit-request")
                    .endObject();
            writer.name("settings").beginObject()
                    .name("iconScale").value(request.iconScale)
                    .name("recipeScale").value(request.recipeScale)
                    .name("mobCanvas").value(MOB_CANVAS)
                    .endObject();
            writer.name("counts").beginObject()
                    .name("items").value(plan.itemCountAtFinish())
                    .name("recipes").value(recipeCount)
                    .name("categories").value(categoryCount)
                    .name("mobs").value(0)
                    .name("blockDrops").value(0)
                    .name("nativeIconCorrections").value(
                            nativeIconCorrectionAudit.correctionCount())
                    .name("failures").value(failures.size())
                    .endObject();
            writer.name("diagnostics").beginObject()
                    .name("failureEvents").value(failures.size())
                    .name("failureEventsOmitted").value(0)
                    .name("nativeIconCorrections").value(
                            nativeIconCorrectionAudit.correctionCount())
                    .name("transparentIcons").value(transparentIcons)
                    .endObject();
            if (request.isQualitySample()) {
                writer.name("qualitySample").beginObject();
                writer.name("selectorCounts").beginObject()
                        .name("recipeId").value(0)
                        .name("sourceIndex").value(request.qualitySample.size())
                        .name("item").value(request.qualityItemSample.size())
                        .endObject();
                writer.name("requested").beginArray();
                for (ExportRequest.Sample sample : request.qualitySample) {
                    writer.beginObject()
                            .name("categoryId").value(sample.categoryId())
                            .name("sourceIndex").value(sample.sourceIndex())
                            .endObject();
                }
                writer.endArray();
                writer.name("requestedItems").beginArray();
                for (ExportRequest.ItemSample sample : request.qualityItemSample) {
                    writer.beginObject()
                            .name("typeId").value(sample.typeId())
                            .name("identifier").value(sample.identifier())
                            .endObject();
                }
                writer.endArray().endObject();
            }
            writer.name("mods").beginObject();
            for (var mod : ModList.get().getMods()) {
                writer.name(mod.getModId()).value(mod.getDisplayName());
            }
            writer.endObject();
            writer.endObject();
        }
    }

    void publish() throws IOException {
        try {
            Files.move(root, finalRoot, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic export publication is unavailable; no non-atomic fallback was attempted.", exception);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (catalog != null) {
                try {
                    catalog.close();
                } catch (IOException exception) {
                    ReiExportMod.LOGGER.error("[reiexport] Failed to close the item catalog after abort", exception);
                }
                catalog = null;
            }
            pngWriter.close();
            renderer.close();
        }
    }
}
