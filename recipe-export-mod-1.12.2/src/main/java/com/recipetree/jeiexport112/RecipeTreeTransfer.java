package com.recipetree.jeiexport112;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded reader for portable Recipe Tree files created by the in-game share action. */
final class RecipeTreeTransfer {
    static final long MAX_TREE_BYTES = 1024L * 1024L;
    static final int MAX_LISTED_FILES = 128;

    private RecipeTreeTransfer() {}

    static RecipeTreeProgress.RecipeHistoryEntry fromFile(
            Path file,
            RecipeTreeViewerBridge bridge) throws IOException {
        return fromJson(readBoundedUtf8(file), bridge);
    }

    static RecipeTreeProgress.RecipeHistoryEntry fromJson(
            String json,
            RecipeTreeViewerBridge bridge) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            throw new IOException("The recipe tree JSON is empty");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_TREE_BYTES) {
            throw new IOException("The recipe tree exceeds the " + MAX_TREE_BYTES + "-byte limit");
        }
        final JsonObject root;
        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) throw new IOException("Recipe tree root must be an object");
            root = parsed.getAsJsonObject();
        } catch (JsonParseException invalid) {
            throw new IOException("Recipe tree is not valid JSON", invalid);
        }

        requireString(root, "format", "minecraft-recipe-tree");
        requireInteger(root, "version", 1);
        String direction = string(root, "direction");
        if (!"inputs".equals(direction)) {
            throw new IOException("Only input recipe trees can be imported");
        }
        JsonObject pack = object(root, "pack");
        requireString(pack, "minecraftVersion", "1.12.2");
        String rootKey = nonEmptyString(root, "rootKey");
        JsonObject productionPlan = object(root, "productionPlan");
        long amount = positiveLong(productionPlan, "amount");
        JsonArray serializedSelections = array(root, "selections");
        if (serializedSelections.size() > RecipeTreeModel.MAX_NODES) {
            throw new IOException("Recipe tree has more than " + RecipeTreeModel.MAX_NODES
                    + " selections");
        }

        List<RecipeTreeProgress.RecipeHistorySelection> selections =
                new ArrayList<RecipeTreeProgress.RecipeHistorySelection>();
        Set<String> paths = new HashSet<String>();
        int treeDepth = 1;
        String rootRecipe = null;
        for (int index = 0; index < serializedSelections.size(); index++) {
            JsonElement serialized = serializedSelections.get(index);
            if (!serialized.isJsonObject()) {
                throw new IOException("selections[" + index + "] must be an object");
            }
            JsonObject selection = serialized.getAsJsonObject();
            List<Integer> path = path(selection, index);
            String pathKey = path.toString();
            if (!paths.add(pathKey)) {
                throw new IOException("Recipe tree contains duplicate selection path " + pathKey);
            }
            String itemKey = nonEmptyString(selection, "itemKey");
            JsonObject source = object(selection, "source");
            requireString(source, "kind", "recipe");
            String recipeKey = nonEmptyString(source, "recipeKey");
            if (path.isEmpty()) rootRecipe = recipeKey;
            treeDepth = Math.max(treeDepth, path.size() + 1);
            RecipeTreeViewerBridge.Ingredient ingredient = bridge.findIngredient(itemKey);
            selections.add(new RecipeTreeProgress.RecipeHistorySelection(
                    0,
                    path,
                    itemKey,
                    ingredient == null ? itemKey : ingredient.getDisplayName(),
                    recipeKey,
                    null,
                    false));
        }

        RecipeTreeViewerBridge.Ingredient rootIngredient = bridge.findIngredient(rootKey);
        String rootName = rootIngredient == null ? rootKey : rootIngredient.getDisplayName();
        return new RecipeTreeProgress.RecipeHistoryEntry(
                rootKey,
                rootRecipe,
                amount,
                false,
                treeDepth,
                Collections.singletonList(new RecipeTreeProgress.RecipeHistoryRoot(
                        rootKey, rootName, rootRecipe, amount)),
                selections,
                false);
    }

    static List<Path> listShareFiles(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return Collections.emptyList();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Recipe tree share location is not a directory: " + directory);
        }
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.mrtree.json")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) files.add(file);
            }
        }
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                try {
                    return Files.getLastModifiedTime(right, LinkOption.NOFOLLOW_LINKS)
                            .compareTo(Files.getLastModifiedTime(left, LinkOption.NOFOLLOW_LINKS));
                } catch (IOException error) {
                    JeiExportMod.LOGGER.warn(
                            "[jeiexport] Could not compare recipe-tree share timestamps for {} "
                                    + "and {}; keeping their filename order",
                            left, right, error);
                    return left.getFileName().toString().compareToIgnoreCase(
                            right.getFileName().toString());
                }
            }
        });
        if (files.size() > MAX_LISTED_FILES) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Recipe-tree share folder contains {} files; showing only the "
                            + "newest {}",
                    files.size(), MAX_LISTED_FILES);
            return new ArrayList<Path>(files.subList(0, MAX_LISTED_FILES));
        }
        return files;
    }

    private static String readBoundedUtf8(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Recipe tree must be a regular, non-symbolic-link file: " + file);
        }
        if (attributes.size() > MAX_TREE_BYTES) {
            throw new IOException("Recipe tree exceeds the " + MAX_TREE_BYTES + "-byte limit");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) attributes.size());
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_TREE_BYTES) {
                    throw new IOException("Recipe tree grew beyond the " + MAX_TREE_BYTES
                            + "-byte limit while being read");
                }
                bytes.write(buffer, 0, read);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Recipe tree is not valid UTF-8", invalid);
        }
    }

    private static List<Integer> path(JsonObject selection, int selectionIndex)
            throws IOException {
        JsonArray serialized = array(selection, "path");
        if (serialized.size() > RecipeTreeModel.MAX_DEPTH) {
            throw new IOException("selections[" + selectionIndex + "].path exceeds depth "
                    + RecipeTreeModel.MAX_DEPTH);
        }
        List<Integer> path = new ArrayList<Integer>();
        for (int index = 0; index < serialized.size(); index++) {
            JsonElement part = serialized.get(index);
            if (!part.isJsonPrimitive() || !part.getAsJsonPrimitive().isNumber()) {
                throw new IOException("selections[" + selectionIndex + "].path[" + index
                        + "] must be a non-negative integer");
            }
            int value;
            try {
                value = part.getAsInt();
            } catch (NumberFormatException invalid) {
                throw new IOException("selections[" + selectionIndex + "].path[" + index
                        + "] must be a non-negative integer", invalid);
            }
            if (value < 0 || value >= RecipeTreeModel.MAX_CHILDREN) {
                throw new IOException("selections[" + selectionIndex + "].path[" + index
                        + "] is outside the supported child range");
            }
            path.add(value);
        }
        return path;
    }

    private static JsonObject object(JsonObject parent, String name) throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IOException(name + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IOException(name + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject parent, String name) throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IOException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static String nonEmptyString(JsonObject parent, String name) throws IOException {
        String value = string(parent, name);
        if (value.trim().isEmpty()) throw new IOException(name + " must not be empty");
        return value;
    }

    private static void requireString(JsonObject parent, String name, String expected)
            throws IOException {
        String actual = string(parent, name);
        if (!expected.equals(actual)) {
            throw new IOException(name + " must be " + expected + "; got " + actual);
        }
    }

    private static void requireInteger(JsonObject parent, String name, int expected)
            throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException(name + " must be the integer " + expected);
        }
        try {
            BigDecimal parsed = new BigDecimal(value.getAsString());
            if (parsed.stripTrailingZeros().scale() > 0
                    || parsed.compareTo(BigDecimal.valueOf(expected)) != 0) {
                throw new IOException(name + " must be " + expected);
            }
        } catch (NumberFormatException invalid) {
            throw new IOException(name + " must be the integer " + expected, invalid);
        }
    }

    private static long positiveLong(JsonObject parent, String name) throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException(name + " must be a positive integer");
        }
        try {
            BigDecimal decimal = new BigDecimal(value.getAsString());
            if (decimal.stripTrailingZeros().scale() > 0) {
                throw new IOException(name + " must be a positive integer");
            }
            long parsed = decimal.longValueExact();
            if (parsed < 1L || parsed > 999L) {
                throw new IOException(name + " must be between 1 and 999");
            }
            return parsed;
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IOException(name + " must be a positive integer", invalid);
        }
    }
}
