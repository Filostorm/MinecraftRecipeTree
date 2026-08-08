package com.recipetree.jeiexport112;

import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
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
    static final String PREVIEW_CATEGORY = "modularmachinery.preview";
    private static final String WRAPPER_CLASS =
            "hellfirepvp.modularmachinery.common.integration.preview.StructurePreviewWrapper";
    private static final ResourceLocation CONTROLLER_ID =
            new ResourceLocation("modularmachinery", "blockcontroller");

    private ModularMachineryStructure() {}

    static boolean isPreviewCategory(String categoryUid) {
        return PREVIEW_CATEGORY.equals(categoryUid);
    }

    static Data extract(String categoryUid, IRecipeWrapper wrapper, ItemCatalog catalog)
            throws Exception {
        if (!isPreviewCategory(categoryUid)) {
            return null;
        }

        Field machineField = findMachineField(wrapper.getClass());
        machineField.setAccessible(true);
        Object machine = machineField.get(wrapper);
        if (machine == null) {
            throw new IOException("wrapper " + wrapper.getClass().getName() +
                    " has a null machine field");
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

        DescriptiveStackCall descriptiveStack = null;
        for (Map.Entry<?, ?> entry : entries) {
            BlockPos position = requirePosition(entry.getKey());
            Object information = entry.getValue();
            if (information == null) {
                throw new IOException("Modular Machinery structure has null block information at " +
                        position);
            }
            if (descriptiveStack == null ||
                    descriptiveStack.informationClass != information.getClass()) {
                descriptiveStack = resolveDescriptiveStackCall(information.getClass());
            }
            data.include(position.getX(), position.getY(), position.getZ());
            ItemStack representative = resolveRepresentativeStack(information, descriptiveStack);
            if (representative == null) {
                // MM/MMCE structure patterns may explicitly require air. JEI does not render or
                // count those cells, but their coordinates still define the preview envelope.
                continue;
            }
            if (representative.isEmpty()) {
                throw new IOException("Modular Machinery structure block at " + position +
                        " has no descriptive item stack");
            }
            ItemStack stack = representative.copy();
            stack.setCount(1);
            String key = catalog.ensure(VanillaTypes.ITEM, stack);
            data.add(new Cell(position.getX(), position.getY(), position.getZ(), key));
        }
        data.finish();
        return data;
    }

    /**
     * MMCE chooses a preview sample from the current time. Selector zero is deterministic, but a
     * rule may place an itemless state (usually air) at that index even when later alternatives
     * are visible blocks. Prefer the deterministic sample, then use MMCE's own ingredient list to
     * find the first displayable alternative. A genuinely air-only rule is omitted from cells.
     */
    static ItemStack resolveRepresentativeStack(Object information, DescriptiveStackCall call)
            throws Exception {
        Object rawStack = call.method.invoke(information, call.argument);
        if (rawStack instanceof ItemStack && !((ItemStack) rawStack).isEmpty()) {
            return (ItemStack) rawStack;
        }

        try {
            Object rawIngredients = information.getClass().getMethod("getIngredientList")
                    .invoke(information);
            if (rawIngredients instanceof Iterable) {
                for (Object candidate : (Iterable<?>) rawIngredients) {
                    if (candidate instanceof ItemStack && !((ItemStack) candidate).isEmpty()) {
                        return (ItemStack) candidate;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
            // Original Modular Machinery variants may expose only getDescriptiveStack(Optional).
        }

        IBlockState sampleState = resolveSampleState(information);
        if (sampleState != null && sampleState.getBlock() == Blocks.AIR) {
            return null;
        }
        return ItemStack.EMPTY;
    }

    private static IBlockState resolveSampleState(Object information) throws Exception {
        Class<?>[] parameterTypes = {long.class, Long.class, Optional.class};
        for (Class<?> parameterType : parameterTypes) {
            try {
                Method method = information.getClass().getMethod("getSampleState", parameterType);
                Object argument = parameterType == Optional.class
                        ? Optional.of(Long.valueOf(0L))
                        : Long.valueOf(0L);
                Object state = method.invoke(information, argument);
                return state instanceof IBlockState ? (IBlockState) state : null;
            } catch (NoSuchMethodException ignored) {
                // Try the next supported upstream/fork signature.
            }
        }
        return null;
    }

    /**
     * MMCE 2.x changed the preview selector from {@code Optional<Long>} to primitive
     * {@code long}. Support both audited contracts and the boxed bridge form used by some forks.
     */
    static DescriptiveStackCall resolveDescriptiveStackCall(Class<?> informationClass)
            throws IOException {
        Class<?>[] parameterTypes = {long.class, Long.class, Optional.class};
        for (Class<?> parameterType : parameterTypes) {
            try {
                Method method = informationClass.getMethod("getDescriptiveStack", parameterType);
                Object argument = parameterType == Optional.class
                        ? Optional.of(Long.valueOf(0L))
                        : Long.valueOf(0L);
                return new DescriptiveStackCall(informationClass, method, argument);
            } catch (NoSuchMethodException ignored) {
                // Try the next supported upstream/fork signature.
            }
        }
        throw new IOException("Modular Machinery structure block information " +
                informationClass.getName() + " exposes none of the supported " +
                "getDescriptiveStack(long), getDescriptiveStack(Long), or " +
                "getDescriptiveStack(Optional) signatures");
    }

    /**
     * Original Modular Machinery and MMCE currently use the same wrapper name. Searching the
     * hierarchy as well keeps subclasses and compatibility shims working, while the category gate
     * above prevents an unrelated recipe wrapper with a coincidental field from being inspected.
     */
    static Field findMachineField(Class<?> wrapperClass) throws IOException {
        Class<?> current = wrapperClass;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField("machine");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IOException("unsupported Modular Machinery Structure Preview wrapper " +
                wrapperClass.getName() + "; expected " + WRAPPER_CLASS +
                " or a compatible wrapper exposing a machine field");
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
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private void add(Cell cell) {
            include(cell.x, cell.y, cell.z);
            cells.add(cell);
            BigDecimal previous = blockCounts.get(cell.key);
            blockCounts.put(cell.key,
                    previous == null ? BigDecimal.ONE : previous.add(BigDecimal.ONE));
        }

        private void include(int x, int y, int z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        private void finish() throws IOException {
            if (cells.isEmpty()) {
                throw new IOException("Modular Machinery structure exported no cells");
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

    static final class DescriptiveStackCall {
        final Class<?> informationClass;
        final Method method;
        final Object argument;

        DescriptiveStackCall(Class<?> informationClass, Method method, Object argument) {
            this.informationClass = informationClass;
            this.method = method;
            this.argument = argument;
        }
    }
}
