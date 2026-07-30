package com.recipetree.jeiexport112;

import com.google.gson.stream.JsonWriter;
import com.recipetree.jeiexport112.compat.ExportWorldStartupDimensions;
import mezz.jei.api.ingredients.IIngredientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class ExportContext {
    static final int MOB_CANVAS = 256;
    private static final int MAX_FAILURE_SAMPLES = 5000;
    private static final int MAX_WARNING_SAMPLES = 5000;

    final Path root;
    final ExportRequest request;
    final ExporterBuildIdentity exporterBuildIdentity;
    final OffscreenRenderer renderer = new OffscreenRenderer();
    final PngWriter pngWriter;
    final List<String> failures = Collections.synchronizedList(new ArrayList<String>());
    final List<String> warnings = Collections.synchronizedList(new ArrayList<String>());
    private final AtomicInteger totalFailures = new AtomicInteger();
    private final AtomicInteger omittedFailures = new AtomicInteger();
    private final AtomicInteger totalWarnings = new AtomicInteger();
    private final AtomicInteger omittedWarnings = new AtomicInteger();
    final List<CategoryMeta> categories = new ArrayList<CategoryMeta>();
    final Map<String, PrimitiveRefs> reverseIndex = new LinkedHashMap<String, PrimitiveRefs>();
    private final Set<String> usedPaths = new HashSet<String>();
    private final Set<String> amountFallbackTypes = new HashSet<String>();
    private final Set<Path> requiredSampleRecipeImages = new LinkedHashSet<Path>();
    private final Set<String> recipeLayoutCompatibilityTargets = new HashSet<String>();
    private final AtomicInteger advancedRocketryEmptyWildcardInputLayouts = new AtomicInteger();
    private final AtomicInteger buildCraftHeatableAbsentOutputLayouts = new AtomicInteger();
    private final AtomicInteger buildCraftCoolableAbsentOutputLayouts = new AtomicInteger();
    private final AtomicInteger multiblocked08ScreenCenteredParentLayouts = new AtomicInteger();
    private long multiblocked08CorrectedScissorCalls;
    private String firstMultiblocked08Placement;

    private ItemCatalog catalog;
    private boolean catalogClosed;
    int recipeCount;

    ExportContext(Path root, ExportRequest request) throws IOException {
        this.root = root;
        this.request = request;
        this.exporterBuildIdentity = ExporterBuildIdentity.loadRuntime();
        Files.createDirectories(root);
        pngWriter = new PngWriter(request.pngThreads, request.pngQueueCapacity, this::failure);
    }

    ItemCatalog catalog(IIngredientRegistry registry) throws IOException {
        if (catalog == null) {
            catalog = new ItemCatalog(this, registry);
        }
        return catalog;
    }

    int itemCount() {
        return catalog == null ? 0 : catalog.count();
    }

    void submitImage(BufferedImage image, Path file) throws IOException {
        pngWriter.submit(image, file);
    }

    void submitRecipeImage(BufferedImage image, Path file) throws IOException {
        if (request.qualitySample != null) {
            if (image == null) {
                throw new IOException("Quality sample recipe renderer returned a null image for " + file);
            }
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedFile = file.toAbsolutePath().normalize();
            if (!normalizedFile.startsWith(normalizedRoot)) {
                throw new IOException("Quality sample recipe image escapes the staging root: " + file);
            }
            if (!requiredSampleRecipeImages.add(normalizedFile)) {
                throw new IOException("Quality sample recipe image path was submitted twice: " + file);
            }
        }
        submitImage(image, file);
    }

    void failure(String message) {
        String safe = message == null ? "unknown failure" : message;
        if (safe.length() > 4000) {
            safe = safe.substring(0, 4000) + "…";
        }
        totalFailures.incrementAndGet();
        synchronized (failures) {
            if (failures.size() < MAX_FAILURE_SAMPLES) {
                failures.add(safe);
            } else {
                int omitted = omittedFailures.incrementAndGet();
                if (omitted == 1 || omitted % 1000 == 0) {
                    JeiExportMod.LOGGER.warn(
                            "[jeiexport] failures.json sample limit {} reached; {} additional failures counted " +
                                    "but not retained in heap", MAX_FAILURE_SAMPLES, omitted);
                }
            }
        }
        JeiExportMod.LOGGER.warn("[jeiexport] {}", safe);
    }

    int failureCount() {
        return totalFailures.get();
    }

    void warning(String message) {
        String safe = message == null ? "unknown warning" : message;
        if (safe.length() > 4000) {
            safe = safe.substring(0, 4000) + "…";
        }
        totalWarnings.incrementAndGet();
        synchronized (warnings) {
            if (warnings.size() < MAX_WARNING_SAMPLES) {
                warnings.add(safe);
            } else {
                int omitted = omittedWarnings.incrementAndGet();
                if (omitted == 1 || omitted % 1000 == 0) {
                    JeiExportMod.LOGGER.warn(
                            "[jeiexport] warnings.json sample limit {} reached; {} additional warnings " +
                                    "counted but not retained in heap", MAX_WARNING_SAMPLES, omitted);
                }
            }
        }
        JeiExportMod.LOGGER.warn("[jeiexport] {}", safe);
    }

    int warningCount() {
        return totalWarnings.get();
    }

    synchronized void warnAmountFallback(Class<?> ingredientClass) {
        String className = ingredientClass.getName();
        if (amountFallbackTypes.add(className)) {
            failure("ingredient amount type " + className +
                    " has no recognized numeric amount/count accessor; using quantity 1 for this type");
        }
    }

    void recordZeroQuantityDecision(ZeroQuantityPolicy.Decision decision, String role,
                                    String categoryUid, int recipeIndex, Class<?> ingredientClass) {
        String amount = decision.publishedAmount == null
                ? "none"
                : decision.publishedAmount.toPlainString();
        warning(decision.diagnosticCode + " recipe " + role + " " + categoryUid + " #" + recipeIndex +
                " type " + ingredientClass.getName() + " publishedAmount=" + amount + "; " +
                decision.explanation);
    }

    synchronized void recordRecipeLayoutCompatibility(
            RecipeLayoutCompatibilityPolicy.Kind kind, String categoryUid,
            String categoryClass, String wrapperClass, int sourceIndex, int exportedIndex)
            throws RecipeLayoutCompatibility.DriftException {
        String identity = kind.name() + '\u0000' + categoryUid + '\u0000' + sourceIndex;
        if (!recipeLayoutCompatibilityTargets.add(identity)) {
            throw new RecipeLayoutCompatibility.DriftException(
                    "RECIPE_LAYOUT_COMPAT_DRIFT: duplicate intervention " + kind + " for " +
                            categoryUid + " sourceIndex=" + sourceIndex);
        }
        switch (kind) {
            case ADVANCED_ROCKETRY_EMPTY_WILDCARD_INPUT:
                advancedRocketryEmptyWildcardInputLayouts.incrementAndGet();
                break;
            case BUILDCRAFT_HEATABLE_ABSENT_OUTPUT:
                buildCraftHeatableAbsentOutputLayouts.incrementAndGet();
                break;
            case BUILDCRAFT_COOLABLE_ABSENT_OUTPUT:
                buildCraftCoolableAbsentOutputLayouts.incrementAndGet();
                break;
            default:
                recipeLayoutCompatibilityTargets.remove(identity);
                throw new RecipeLayoutCompatibility.DriftException(
                        "RECIPE_LAYOUT_COMPAT_DRIFT: attempted to record unsupported intervention " +
                                kind + " for " + categoryUid + " sourceIndex=" + sourceIndex);
        }
        JeiExportMod.LOGGER.info(
                "[jeiexport] RECIPE_LAYOUT_COMPAT_APPLIED kind={} category={} sourceIndex={} " +
                        "exportedIndex={} categoryClass={} wrapperClass={} scope=layout-only",
                kind.diagnosticName, categoryUid, sourceIndex, exportedIndex,
                categoryClass, wrapperClass);
    }

    synchronized void recordRecipeLayoutPlacementCompatibility(
            RecipeLayoutPlacementPolicy.Kind kind, String categoryUid,
            String categoryClass, String wrapperClass, int sourceIndex, int exportedIndex,
            int layoutX, int layoutY, int translateX, int translateY,
            int correctedScissorCalls)
            throws RecipeLayoutCompatibility.DriftException {
        if (kind != RecipeLayoutPlacementPolicy.Kind.
                MULTIBLOCKED_0_8_SCREEN_CENTERED_PARENT) {
            throw new RecipeLayoutCompatibility.DriftException(
                    "RECIPE_LAYOUT_PLACEMENT_DRIFT: attempted to record unsupported intervention " +
                            kind + " for " + categoryUid + " sourceIndex=" + sourceIndex);
        }
        if (correctedScissorCalls <= 0) {
            throw new RecipeLayoutCompatibility.DriftException(
                    "RECIPE_LAYOUT_PLACEMENT_DRIFT: Multiblocked 0.8.0 layout corrected " +
                            correctedScissorCalls + " scissor calls for " + categoryUid +
                            " sourceIndex=" + sourceIndex);
        }
        try {
            multiblocked08CorrectedScissorCalls = Math.addExact(
                    multiblocked08CorrectedScissorCalls, correctedScissorCalls);
        } catch (ArithmeticException overflow) {
            throw new RecipeLayoutCompatibility.DriftException(
                    "RECIPE_LAYOUT_PLACEMENT_DRIFT: corrected scissor diagnostic counter " +
                            "overflowed for " + categoryUid + " sourceIndex=" + sourceIndex,
                    overflow);
        }
        int count = multiblocked08ScreenCenteredParentLayouts.incrementAndGet();
        if (count == 1) {
            firstMultiblocked08Placement = "category=" + categoryUid +
                    ", sourceIndex=" + sourceIndex + ", exportedIndex=" + exportedIndex +
                    ", categoryClass=" + categoryClass + ", wrapperClass=" + wrapperClass +
                    ", layoutOrigin=(" + layoutX + ',' + layoutY + ')' +
                    ", externalTranslation=(" + translateX + ',' + translateY + ')' +
                    ", correctedScissorCalls=" + correctedScissorCalls;
        }
    }

    synchronized String uniquePath(String directory, String baseName, String extension) {
        String base = directory + "/" + Naming.sanitize(baseName);
        String candidate = base + extension;
        int suffix = 2;
        while (!usedPaths.add(candidate)) {
            candidate = base + "_" + suffix++ + extension;
        }
        return candidate;
    }

    synchronized String uniqueCategoryDirectory(String categoryUid) {
        String base = "recipes/" + Naming.sanitize(categoryUid);
        String candidate = base;
        int suffix = 2;
        while (!usedPaths.add(candidate + "/")) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    int addCategory(CategoryMeta category) {
        categories.add(category);
        return categories.size() - 1;
    }

    void index(String key, boolean output, int category, int recipe) {
        PrimitiveRefs refs = reverseIndex.get(key);
        if (refs == null) {
            refs = new PrimitiveRefs();
            reverseIndex.put(key, refs);
        }
        refs.add(output, category, recipe);
    }

    void finishWritersAndImages() throws IOException {
        IOException failure = null;
        try {
            closeCatalog();
        } catch (IOException e) {
            failure = e;
        }
        renderer.close();
        try {
            pngWriter.finish();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            verifyRequiredSampleRecipeImages();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void verifyRequiredSampleRecipeImages() throws IOException {
        if (request.qualitySample == null) {
            return;
        }
        int targetCount = request.qualitySample.recipeCount();
        if (requiredSampleRecipeImages.size() != targetCount) {
            throw new IOException("Quality sample produced " + requiredSampleRecipeImages.size() +
                    " recipe images for " + targetCount + " selected recipes");
        }
        for (Path image : requiredSampleRecipeImages) {
            if (!Files.isRegularFile(image)) {
                throw new IOException("Quality sample recipe image is missing after PNG completion: " + image);
            }
            if (Files.size(image) <= 0L) {
                throw new IOException("Quality sample recipe image is empty after PNG completion: " + image);
            }
        }
    }

    private void closeCatalog() throws IOException {
        if (catalog != null && !catalogClosed) {
            catalog.close();
            catalogClosed = true;
        }
    }

    void writeFinalMetadata(boolean aborted, long durationMillis) throws IOException {
        exporterBuildIdentity.writeTo(root);
        writeCategories();
        writeReverseIndex();
        writeEmptyUnsupportedDatasets();
        writeFailures();
        writeWarnings();
        writeExporterDiagnostics();
        writeManifest(aborted, durationMillis);
    }

    private void writeCategories() throws IOException {
        try (JsonWriter writer = jsonWriter(root.resolve("categories.json"))) {
            writer.beginObject().name("categories").beginArray();
            for (CategoryMeta category : categories) {
                category.write(writer);
            }
            writer.endArray().endObject();
        }
    }

    private void writeReverseIndex() throws IOException {
        try (JsonWriter writer = jsonWriter(root.resolve("index.json"))) {
            writer.beginObject();
            for (Map.Entry<String, PrimitiveRefs> entry : reverseIndex.entrySet()) {
                writer.name(entry.getKey()).beginObject();
                entry.getValue().write(writer);
                writer.endObject();
            }
            writer.endObject();
        }
    }

    private void writeEmptyUnsupportedDatasets() throws IOException {
        JeiExportMod.LOGGER.info(
                "[jeiexport] Minecraft 1.12.2 port intentionally exports mobs.json and blockdrops.json as empty; " +
                        "this module is scoped to HEI items and recipes.");
        try (JsonWriter writer = jsonWriter(root.resolve("mobs.json"))) {
            writer.beginObject().name("mobs").beginArray().endArray().endObject();
        }
        try (JsonWriter writer = jsonWriter(root.resolve("blockdrops.json"))) {
            writer.beginObject().name("blocks").beginObject().endObject().endObject();
        }
    }

    private void writeFailures() throws IOException {
        List<String> copy;
        synchronized (failures) {
            copy = new ArrayList<String>(failures);
        }
        try (JsonWriter writer = jsonWriter(root.resolve("failures.json"))) {
            writer.beginArray();
            for (String entry : copy) {
                writer.value(entry);
            }
            int omitted = omittedFailures.get();
            if (omitted > 0) {
                writer.value("[jeiexport] " + omitted + " additional failures omitted from this bounded " +
                        "sample; manifest.diagnostics.failureEvents contains the full count and the game " +
                        "log contains each failure");
            }
            writer.endArray();
        }
    }

    private void writeWarnings() throws IOException {
        List<String> copy;
        synchronized (warnings) {
            copy = new ArrayList<String>(warnings);
        }
        try (JsonWriter writer = jsonWriter(root.resolve("warnings.json"))) {
            writer.beginArray();
            for (String entry : copy) {
                writer.value(entry);
            }
            int omitted = omittedWarnings.get();
            if (omitted > 0) {
                writer.value("[jeiexport] " + omitted + " additional warnings omitted from this bounded " +
                        "sample; manifest.diagnostics.warningEvents contains the full count and the game " +
                        "log contains each warning");
            }
            writer.endArray();
        }
    }

    private void writeExporterDiagnostics() throws IOException {
        try (JsonWriter writer = jsonWriter(root.resolve("exporter-diagnostics.json"))) {
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("recipeLayoutCompatibility").beginObject();
            writer.name("total").value(recipeLayoutCompatibilityTargets.size());
            writer.name("advancedRocketryChemicalReactorEmptyWildcardInput")
                    .value(advancedRocketryEmptyWildcardInputLayouts.get());
            writer.name("buildCraftHeatableAbsentOutput")
                    .value(buildCraftHeatableAbsentOutputLayouts.get());
            writer.name("buildCraftCoolableAbsentOutput")
                    .value(buildCraftCoolableAbsentOutputLayouts.get());
            writer.endObject();
            writer.name("recipeLayoutPlacementCompatibility").beginObject();
            writer.name("total").value(multiblocked08ScreenCenteredParentLayouts.get());
            writer.name("multiblocked08ScreenCenteredParent")
                    .value(multiblocked08ScreenCenteredParentLayouts.get());
            writer.name("multiblocked08CorrectedScissorCalls")
                    .value(multiblocked08CorrectedScissorCalls);
            writer.endObject();
            writer.endObject();
        }
        int multiblockedPlacements = multiblocked08ScreenCenteredParentLayouts.get();
        if (multiblockedPlacements > 0) {
            JeiExportMod.LOGGER.info(
                    "[jeiexport] RECIPE_LAYOUT_PLACEMENT_COMPAT_APPLIED kind={} count={} " +
                            "correctedScissorCalls={} scope=layout-coordinate-and-framebuffer-scissor; " +
                            "HEI native pixels were captured without " +
                            "substitution or fallback; first={}",
                    RecipeLayoutPlacementPolicy.Kind.
                            MULTIBLOCKED_0_8_SCREEN_CENTERED_PARENT.diagnosticName,
                    multiblockedPlacements, multiblocked08CorrectedScissorCalls,
                    firstMultiblocked08Placement);
        }
    }

    private void writeManifest(boolean aborted, long durationMillis) throws IOException {
        int omitted = omittedFailures.get();
        int serializedFailures;
        synchronized (failures) {
            serializedFailures = failures.size() + (omitted > 0 ? 1 : 0);
        }
        try (JsonWriter writer = jsonWriter(root.resolve("manifest.json"))) {
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("format").value(1);
            writer.name("generatedAt").value(Instant.now().toString());
            writer.name("durationMs").value(durationMillis);
            writer.name("aborted").value(aborted);
            writer.name("minecraft").value(Loader.MC_VERSION);
            writer.name("pack").beginObject();
            writer.name("name").value(request.pack.name);
            if (request.pack.version != null) {
                writer.name("version").value(request.pack.version);
            }
            writer.name("identitySource").value(request.pack.source);
            writer.endObject();
            writer.name("settings").beginObject();
            writer.name("iconScale").value(request.iconScale);
            writer.name("recipeScale").value(request.recipeScale);
            writer.name("mobCanvas").value(MOB_CANVAS);
            writeWorldStartupOptimization(writer);
            writer.endObject();
            if (request.qualitySample != null) {
                writer.name("qualitySample").beginObject();
                writer.name("enabled").value(true);
                writer.name("recipeTargets").value(request.qualitySample.recipeCount());
                writer.name("selectorCounts").beginObject();
                writer.name("recipeId").value(request.qualitySample.recipeIdSelectorCount());
                writer.name("sourceIndex").value(request.qualitySample.sourceIndexSelectorCount());
                writer.endObject();
                writer.endObject();
            }
            writer.name("counts").beginObject();
            writer.name("items").value(itemCount());
            writer.name("recipes").value(recipeCount);
            writer.name("categories").value(categories.size());
            writer.name("mobs").value(0);
            writer.name("blockDrops").value(0);
            writer.name("failures").value(serializedFailures);
            writer.endObject();
            writer.name("diagnostics").beginObject();
            writer.name("failureEvents").value(failureCount());
            writer.name("failureEventsOmitted").value(omitted);
            writer.name("warningEvents").value(warningCount());
            writer.name("warningEventsOmitted").value(omittedWarnings.get());
            writer.endObject();
            writer.name("mods").beginObject();
            for (ModContainer mod : Loader.instance().getActiveModList()) {
                writer.name(mod.getModId()).value(mod.getName());
            }
            writer.endObject();
            writer.endObject();
        }
    }

    private static void writeWorldStartupOptimization(JsonWriter writer) throws IOException {
        boolean enabled = ExportWorldStartupDimensions.isEnabled();
        ExportWorldStartupDimensions.SelectionSnapshot selection =
                ExportWorldStartupDimensions.lastSelection();
        writer.name("worldStartupOptimization").beginObject();
        writer.name("enabled").value(enabled);
        writer.name("policy").value(ExportWorldStartupDimensions.policyName());
        writer.name("applied").value(selection != null);
        if (selection != null) {
            writer.name("originalDimensions").value(selection.originalCount());
            writer.name("selectedDimensions").value(selection.selectedCount());
            writer.name("skippedDimensions").value(selection.skippedCount());
        }
        writer.endObject();
    }

    static JsonWriter jsonWriter(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return new JsonWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    static final class CategoryMeta {
        final String id;
        final String title;
        final String directory;
        int count;
        String icon;
        final List<String> catalysts = new ArrayList<String>();

        CategoryMeta(String id, String title, String directory, int count) {
            this.id = id;
            this.title = title;
            this.directory = directory;
            this.count = count;
        }

        void write(JsonWriter writer) throws IOException {
            writer.beginObject();
            writer.name("id").value(id);
            writer.name("title").value(title);
            writer.name("dir").value(directory);
            writer.name("count").value(count);
            if (icon != null) {
                writer.name("icon").value(icon);
            }
            writer.name("catalysts").beginArray();
            for (String catalyst : catalysts) {
                writer.value(catalyst);
            }
            writer.endArray();
            writer.endObject();
        }
    }
}
