package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ElementalCraftPureOreBytecodeContractTest {
    @Test
    void pinnedLoaderCoalescesOverlappingForgeTagsIntoExistingEntries() throws Exception {
        ClassNode loader = readPinned(Mm2DeterminismContract.ELEMENTAL_PURE_ORE_LOADER);
        MethodNode findOrCreate = method(
                loader,
                "findOrCreateEntry",
                "(Ljava/util/Map;Lnet/minecraft/world/item/Item;)"
                        + "Lsirttas/elementalcraft/pureore/PureOre;");

        assertEquals(1, invocationCount(
                findOrCreate, Opcodes.INVOKEINTERFACE, "java/util/Map", "values",
                "()Ljava/util/Collection;"));
        assertEquals(1, invocationCount(
                findOrCreate, Opcodes.INVOKEVIRTUAL,
                "sirttas/elementalcraft/pureore/PureOre", "contains",
                "(Lnet/minecraft/world/item/Item;)Z"));
        assertEquals(1, invocationCount(
                findOrCreate, Opcodes.INVOKESTATIC,
                "sirttas/elementalcraft/tag/ECTags$Items", "getTag",
                "(Ljava/util/function/Predicate;)Lnet/minecraft/core/HolderSet$Named;"));
        assertEquals(1, invocationCount(
                findOrCreate, Opcodes.INVOKEVIRTUAL,
                "sirttas/elementalcraft/pureore/PureOre", "addTag",
                "(Lnet/minecraft/core/HolderSet$Named;)V"));
    }

    @Test
    void pinnedManagerRemovesEveryNonProcessableCoalescedEntry() throws Exception {
        ClassNode manager = readPinned(Mm2DeterminismContract.ELEMENTAL_PURE_ORE_MANAGER);
        MethodNode reload = method(
                manager,
                "reload",
                "(Lsirttas/dpanvil/api/event/DataPackReloadCompleteEvent;)V");
        assertEquals(2, invocationCount(
                reload, Opcodes.INVOKEVIRTUAL,
                "sirttas/elementalcraft/pureore/PureOreLoader", "generate",
                "(Ljava/util/Collection;)Ljava/util/List;"));
        assertEquals(1, invocationCount(
                reload, Opcodes.INVOKEINTERFACE, "java/util/Collection", "removeIf",
                "(Ljava/util/function/Predicate;)Z"));

        MethodNode predicate = method(
                manager,
                "lambda$reload$11",
                "(Lsirttas/elementalcraft/pureore/PureOreManager$Entry;)Z");
        assertEquals(1, invocationCount(
                predicate, Opcodes.INVOKEVIRTUAL,
                "sirttas/elementalcraft/pureore/PureOreManager$Entry", "isProcessable", "()Z"));
        assertEquals(1, opcodeCount(predicate, Opcodes.IFNE),
                "the removal predicate must negate Entry.isProcessable()");
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin) throws Exception {
        byte[] bytes;
        try (InputStream input = ElementalCraftPureOreBytecodeContractTest.class
                .getClassLoader().getResourceAsStream(pin.resource())) {
            assertNotNull(input, "missing pinned target " + pin.resource());
            bytes = input.readAllBytes();
        }
        assertEquals(
                pin.sha256(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                "target bytecode drift for " + pin.resource());
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name)
                        && descriptor.equals(candidate.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing method " + owner.name + "." + name + descriptor));
    }

    private static int invocationCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == opcode
                    && owner.equals(invocation.owner)
                    && name.equals(invocation.name)
                    && descriptor.equals(invocation.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int opcodeCount(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) {
                count++;
            }
        }
        return count;
    }
}
