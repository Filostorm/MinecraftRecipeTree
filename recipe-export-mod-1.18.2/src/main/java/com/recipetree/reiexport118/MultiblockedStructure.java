package com.recipetree.reiexport118;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports the first design shown by Multiblocked's own PatternWidget as exact placed blocks.
 *
 * <p>The adapter is deliberately reflective because Multiblocked is optional outside MM2, while
 * the audited contract is exact: {@code MultiblockInfoDisplay.definition.getDesigns().get(0)} and
 * {@code MultiblockShapeInfo.getBlocks()}. This is the same design selected by PatternWidget's
 * initial {@code reset(0)} call.</p>
 */
final class MultiblockedStructure {
    private static final String DISPLAY_CLASS =
            "com.lowdragmc.multiblocked.rei.multipage.MultiblockInfoDisplay";

    private MultiblockedStructure() {
    }

    static JsonObject extract(Display display, ItemCatalog catalog) throws Exception {
        if (!DISPLAY_CLASS.equals(display.getClass().getName())) {
            return null;
        }

        Field definitionField = display.getClass().getField("definition");
        Object definition = definitionField.get(display);
        if (definition == null) {
            throw new IOException("Multiblocked info display has no controller definition");
        }
        Object rawDesigns = invokeNoArgs(definition, "getDesigns");
        if (!(rawDesigns instanceof List<?> designs) || designs.isEmpty()) {
            throw new IOException("Multiblocked controller definition has no preview designs");
        }
        Object rawBlocks = invokeNoArgs(designs.get(0), "getBlocks");
        requireArray(rawBlocks, "x");

        Object rawControllerStack = invokeNoArgs(definition, "getStackForm");
        if (!(rawControllerStack instanceof ItemStack controllerStack) || controllerStack.isEmpty()) {
            throw new IOException("Multiblocked controller definition has no item stack form");
        }
        ItemStack normalizedController = controllerStack.copy();
        normalizedController.setCount(1);
        String controllerKey = catalog.ensure(EntryStacks.of(normalizedController));

        List<Cell> cells = new ArrayList<>();
        Method itemStackForm = null;
        int sizeX = Array.getLength(rawBlocks);
        for (int x = 0; x < sizeX; x++) {
            Object ys = Array.get(rawBlocks, x);
            requireArray(ys, "y at x=" + x);
            for (int y = 0; y < Array.getLength(ys); y++) {
                Object zs = Array.get(ys, y);
                requireArray(zs, "z at x=" + x + ", y=" + y);
                for (int z = 0; z < Array.getLength(zs); z++) {
                    Object blockInfo = Array.get(zs, z);
                    if (blockInfo == null) {
                        continue;
                    }
                    if (itemStackForm == null ||
                            itemStackForm.getDeclaringClass() != blockInfo.getClass()) {
                        itemStackForm = blockInfo.getClass().getMethod("getItemStackForm");
                    }
                    Object rawStack = itemStackForm.invoke(blockInfo);
                    if (!(rawStack instanceof ItemStack stack) || stack.isEmpty()) {
                        continue;
                    }
                    ItemStack normalized = stack.copy();
                    normalized.setCount(1);
                    cells.add(new Cell(x, y, z, catalog.ensure(EntryStacks.of(normalized))));
                }
            }
        }
        if (cells.isEmpty()) {
            throw new IOException("Multiblocked preview design contains no renderable blocks");
        }
        cells.sort(Comparator.comparingInt(Cell::y)
                .thenComparingInt(Cell::z)
                .thenComparingInt(Cell::x)
                .thenComparing(Cell::key));
        if (cells.stream().noneMatch(cell -> controllerKey.equals(cell.key()))) {
            throw new IOException("Multiblocked preview design does not contain its controller stack");
        }
        return serialize(controllerKey, cells);
    }

    private static JsonObject serialize(String controllerKey, List<Cell> cells) throws IOException {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        LinkedHashMap<String, BigDecimal> counts = new LinkedHashMap<>();
        for (Cell cell : cells) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
            counts.merge(cell.key(), BigDecimal.ONE, BigDecimal::add);
        }

        JsonObject structure = new JsonObject();
        JsonArray size = new JsonArray();
        size.add(Math.addExact(Math.subtractExact(maxX, minX), 1));
        size.add(Math.addExact(Math.subtractExact(maxY, minY), 1));
        size.add(Math.addExact(Math.subtractExact(maxZ, minZ), 1));
        structure.add("size", size);
        structure.addProperty("total", cells.size());
        structure.addProperty("controller", controllerKey);

        JsonArray blocks = new JsonArray();
        for (Map.Entry<String, BigDecimal> entry : counts.entrySet()) {
            JsonArray block = new JsonArray();
            block.add(entry.getKey());
            block.add(entry.getValue());
            blocks.add(block);
        }
        structure.add("blocks", blocks);

        JsonArray positions = new JsonArray();
        for (Cell cell : cells) {
            JsonArray position = new JsonArray();
            position.add(cell.x());
            position.add(cell.y());
            position.add(cell.z());
            position.add(cell.key());
            positions.add(position);
        }
        structure.add("cells", positions);
        return structure;
    }

    private static Object invokeNoArgs(Object target, String methodName) throws Exception {
        if (target == null) {
            throw new IOException("Cannot call " + methodName + " on a null Multiblocked value");
        }
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static void requireArray(Object value, String label) throws IOException {
        if (value == null || !value.getClass().isArray()) {
            throw new IOException("Multiblocked preview blocks are not an array at " + label);
        }
    }

    private record Cell(int x, int y, int z, String key) {
    }
}
