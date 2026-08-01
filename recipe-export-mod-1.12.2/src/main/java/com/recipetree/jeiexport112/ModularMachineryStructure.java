package com.recipetree.jeiexport112;

import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts the same deterministic representative blocks that Modular Machinery 1.12 displays in
 * its Structure Preview contents tooltip. Keeping this adapter reflective avoids making the
 * exporter depend on one optional mod while still auditing the exact upstream wrapper contract.
 */
final class ModularMachineryStructure {
    private static final String WRAPPER_CLASS =
            "hellfirepvp.modularmachinery.common.integration.preview.StructurePreviewWrapper";
    private static final ResourceLocation CONTROLLER_ID =
            new ResourceLocation("modularmachinery", "blockcontroller");

    private ModularMachineryStructure() {}

    static Data extract(IRecipeWrapper wrapper, ItemCatalog catalog) throws Exception {
        if (!WRAPPER_CLASS.equals(wrapper.getClass().getName())) {
            return null;
        }

        Field machineField = wrapper.getClass().getDeclaredField("machine");
        machineField.setAccessible(true);
        Object machine = machineField.get(wrapper);
        if (machine == null) {
            throw new IOException("Modular Machinery Structure Preview wrapper has no machine");
        }

        Object pattern = invokeNoArgs(machine, "getPattern");
        Object rawPattern = invokeNoArgs(pattern, "getPattern");
        if (!(rawPattern instanceof Map)) {
            throw new IOException("Modular Machinery structure pattern is not a map");
        }

        List<Map.Entry<?, ?>> entries = new ArrayList<Map.Entry<?, ?>>(
                ((Map<?, ?>) rawPattern).entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<?, ?>>() {
            @Override
            public int compare(Map.Entry<?, ?> left, Map.Entry<?, ?> right) {
                BlockPos a = requirePosition(left.getKey());
                BlockPos b = requirePosition(right.getKey());
                int y = Integer.compare(a.getY(), b.getY());
                if (y != 0) return y;
                int z = Integer.compare(a.getZ(), b.getZ());
                return z != 0 ? z : Integer.compare(a.getX(), b.getX());
            }
        });

        Data data = new Data();
        Block controller = resolveController(machine);
        if (controller == null) {
            throw new IOException("Modular Machinery controller block is absent from Forge registry");
        }
        String controllerKey = catalog.ensure(
                VanillaTypes.ITEM, new ItemStack(controller, 1));
        data.controllerKey = controllerKey;
        data.add(new Cell(0, 0, 0, controllerKey));

        Method descriptiveStack = null;
        for (Map.Entry<?, ?> entry : entries) {
            BlockPos position = requirePosition(entry.getKey());
            Object information = entry.getValue();
            if (information == null) {
                throw new IOException("Modular Machinery structure has null block information at " +
                        position);
            }
            if (descriptiveStack == null ||
                    descriptiveStack.getDeclaringClass() != information.getClass()) {
                descriptiveStack = information.getClass().getMethod(
                        "getDescriptiveStack", Optional.class);
            }
            Object rawStack = descriptiveStack.invoke(information, Optional.of(Long.valueOf(0L)));
            if (!(rawStack instanceof ItemStack) || ((ItemStack) rawStack).isEmpty()) {
                throw new IOException("Modular Machinery structure block at " + position +
                        " has no descriptive item stack");
            }
            ItemStack stack = ((ItemStack) rawStack).copy();
            stack.setCount(1);
            String key = catalog.ensure(VanillaTypes.ITEM, stack);
            data.add(new Cell(position.getX(), position.getY(), position.getZ(), key));
        }
        data.finish();
        return data;
    }

    /**
     * MMCE registers one controller block per machine and its JEI preview calls
     * {@code BlockController.getControllerWithMachine(machine)}. Prefer that exact stack source;
     * the original Modular Machinery release has no such factory and keeps using its generic
     * {@code blockcontroller}, which remains the compatibility fallback below.
     */
    private static Block resolveController(Object machine) throws Exception {
        String[] ownerNames = {
                "hellfirepvp.modularmachinery.common.block.BlockController",
                "hellfirepvp.modularmachinery.common.block.BlockFactoryController"
        };
        String[] methodNames = {"getControllerWithMachine", "getMocControllerWithMachine"};
        ClassLoader loader = machine.getClass().getClassLoader();
        for (String ownerName : ownerNames) {
            Class<?> owner;
            try {
                owner = Class.forName(ownerName, false, loader);
            } catch (ClassNotFoundException ignored) {
                continue;
            }
            for (Method method : owner.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 1) {
                    continue;
                }
                boolean named = false;
                for (String methodName : methodNames) {
                    if (methodName.equals(method.getName())) {
                        named = true;
                        break;
                    }
                }
                if (!named || !method.getParameterTypes()[0].isAssignableFrom(machine.getClass())) {
                    continue;
                }
                Object resolved = method.invoke(null, machine);
                if (resolved instanceof Block) {
                    return (Block) resolved;
                }
                throw new IOException(ownerName + "." + method.getName() +
                        " did not return a controller block");
            }
        }
        return ForgeRegistries.BLOCKS.getValue(CONTROLLER_ID);
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        if (target == null) {
            throw new IOException("Cannot invoke " + name + " on a null Modular Machinery value");
        }
        return target.getClass().getMethod(name).invoke(target);
    }

    private static BlockPos requirePosition(Object value) {
        if (!(value instanceof BlockPos)) {
            throw new IllegalStateException("Modular Machinery structure key is not a BlockPos: " +
                    (value == null ? "null" : value.getClass().getName()));
        }
        return (BlockPos) value;
    }

    static final class Data {
        String controllerKey;
        int sizeX;
        int sizeY;
        int sizeZ;
        final List<Cell> cells = new ArrayList<Cell>();
        final LinkedHashMap<String, BigDecimal> blockCounts =
                new LinkedHashMap<String, BigDecimal>();

        private void add(Cell cell) {
            cells.add(cell);
            BigDecimal previous = blockCounts.get(cell.key);
            blockCounts.put(cell.key,
                    previous == null ? BigDecimal.ONE : previous.add(BigDecimal.ONE));
        }

        private void finish() throws IOException {
            if (cells.isEmpty()) {
                throw new IOException("Modular Machinery structure exported no cells");
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Cell cell : cells) {
                minX = Math.min(minX, cell.x);
                minY = Math.min(minY, cell.y);
                minZ = Math.min(minZ, cell.z);
                maxX = Math.max(maxX, cell.x);
                maxY = Math.max(maxY, cell.y);
                maxZ = Math.max(maxZ, cell.z);
            }
            sizeX = Math.addExact(Math.subtractExact(maxX, minX), 1);
            sizeY = Math.addExact(Math.subtractExact(maxY, minY), 1);
            sizeZ = Math.addExact(Math.subtractExact(maxZ, minZ), 1);
        }
    }

    static final class Cell {
        final int x;
        final int y;
        final int z;
        final String key;

        Cell(int x, int y, int z, String key) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.key = key;
        }
    }
}
