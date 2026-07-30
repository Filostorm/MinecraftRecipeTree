package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.mixin.ReiExportMixinConfigPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2CreativeExemplarBytecodeContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exactMixinsAreConfiguredAndMappedThroughTheBytecodeGate() throws Exception {
        String ae2Mixin = "com.recipetree.reiexport118.mixin.Ae2ColorApplicatorCreativeMixin";
        String tombstoneMixin =
                "com.recipetree.reiexport118.mixin.TombstoneFamiliarCreativeMixin";
        String ifMixin =
                "com.recipetree.reiexport118.mixin.IndustrialForegoingBackpackNbtMixin";
        String relicsMixin =
                "com.recipetree.reiexport118.mixin.RelicsStatRandomMixin";
        Files.writeString(
                temporaryDirectory.resolve(Mm2ExportRequestScope.REQUEST_NAME),
                "{\"profile\":\"" + Mm2ExportRequestScope.PROFILE
                        + "\",\"packName\":\"" + Mm2ExportRequestScope.PACK_NAME + "\"}");
        ReiExportMixinConfigPlugin plugin =
                new ReiExportMixinConfigPlugin(temporaryDirectory);
        assertTrue(plugin.shouldApplyMixin(
                Mm2DeterminismContract.AE2_COLOR_APPLICATOR.className(), ae2Mixin));
        assertTrue(plugin.shouldApplyMixin(
                Mm2DeterminismContract.TOMBSTONE_RECEPTACLE.className(), tombstoneMixin));
        assertTrue(plugin.shouldApplyMixin(
                Mm2DeterminismContract.INFINITY_BACKPACK.className(), ifMixin));
        assertTrue(plugin.shouldApplyMixin(
                Mm2DeterminismContract.RELIC_ITEM.className(), relicsMixin));
        assertThrows(IllegalStateException.class, () -> plugin.shouldApplyMixin(
                "drifted.Target", ae2Mixin));
        assertThrows(IllegalStateException.class, () -> plugin.shouldApplyMixin(
                "drifted.Target", relicsMixin));

        String mixinConfig;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "missing reiexport.mixins.json");
            mixinConfig = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(mixinConfig.contains("\"Ae2ColorApplicatorCreativeMixin\""));
        assertTrue(mixinConfig.contains("\"TombstoneFamiliarCreativeMixin\""));
        assertTrue(mixinConfig.contains("\"IndustrialForegoingBackpackNbtMixin\""));
        assertTrue(mixinConfig.contains("\"RelicsStatRandomMixin\""));
        assertFalse(mixinConfig.contains("\"RelicsCreativeExemplarMixin\""));
    }

    @Test
    void relicsPinsOneEntropySourceAndPreservesItsNativeInitializationPath() throws Exception {
        ClassNode relicInterface = readPinned(Mm2DeterminismContract.RELIC_INTERFACE);
        ClassNode relicItem = readPinned(Mm2DeterminismContract.RELIC_ITEM);
        assertTrue((relicItem.access & Opcodes.ACC_ABSTRACT) != 0);
        assertEquals("net/minecraft/world/item/Item", relicItem.superName);
        assertTrue(relicItem.interfaces.contains(
                Mm2DeterminismContract.RELIC_INTERFACE.className().replace('.', '/')));
        assertFalse(hasMethod(
                relicItem,
                "randomizeStat",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;"
                        + "Ljava/lang/String;)V"),
                "RelicItem must continue inheriting the audited interface default before merge");
        MethodNode randomizeStat = method(
                relicInterface,
                "randomizeStat",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Ljava/lang/String;)V");
        assertEquals(1, typeInstructionCount(
                randomizeStat, Opcodes.NEW, "java/util/Random"));
        assertEquals(1, invocationCount(
                randomizeStat,
                Opcodes.INVOKESPECIAL,
                "java/util/Random",
                "<init>",
                "()V"));
        assertEquals(1, invocationCount(
                randomizeStat,
                Opcodes.INVOKESTATIC,
                "it/hurts/sskirillss/relics/utils/MathUtils",
                "randomBetween",
                "(Ljava/util/Random;DD)D"));
        assertEquals(1, invocationCount(
                randomizeStat,
                Opcodes.INVOKESTATIC,
                "it/hurts/sskirillss/relics/utils/MathUtils",
                "round",
                "(DI)D"));
        assertEquals(1, invocationCount(
                randomizeStat,
                Opcodes.INVOKEINTERFACE,
                Mm2DeterminismContract.RELIC_INTERFACE.className().replace('.', '/'),
                "setAbilityValue",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Ljava/lang/String;D)V"));

        ClassNode nativeConstructorMixin = readClass(
                "it/hurts/sskirillss/relics/mixin/ItemStackMixin.class");
        MethodNode initialize = method(
                nativeConstructorMixin,
                "init",
                "(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/nbt/CompoundTag;"
                        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        String relicOwner = Mm2DeterminismContract.RELIC_INTERFACE.className().replace('.', '/');
        assertEquals(1, invocationCount(
                initialize,
                Opcodes.INVOKEINTERFACE,
                relicOwner,
                "randomizeStats",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)V"));
        assertEquals(1, invocationCount(
                initialize,
                Opcodes.INVOKEINTERFACE,
                relicOwner,
                "setAbilityPoints",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;I)V"));
        assertEquals(1, invocationCount(
                initialize,
                Opcodes.INVOKEINTERFACE,
                relicOwner,
                "setAbilityTicking",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Z)V"));

        ClassNode exporterMixin = readClass(
                "com/recipetree/reiexport118/mixin/RelicsStatRandomMixin.class");
        AnnotationNode mixin = annotation(
                exporterMixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(
                List.of(Mm2DeterminismContract.RELIC_ITEM.className()),
                annotationValue(mixin, "targets"));
        assertEquals(false, annotationValue(mixin, "remap"));
        annotation(
                exporterMixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Pseudo;");
        assertFalse((exporterMixin.access & Opcodes.ACC_INTERFACE) != 0,
                "Mixin 0.8.5 must receive a concrete-class mixin, never an interface mixin");

        MethodNode concreteOverride = method(
                exporterMixin,
                "randomizeStat",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Ljava/lang/String;)"
                        + "V");
        assertTrue((concreteOverride.access & Opcodes.ACC_PUBLIC) != 0);
        assertFalse((concreteOverride.access & Opcodes.ACC_PRIVATE) != 0);
        assertFalse((concreteOverride.access & Opcodes.ACC_ABSTRACT) != 0,
                "the RelicItem override must be a mergeable concrete method");
        assertTrue(concreteOverride.visibleAnnotations == null
                        || concreteOverride.visibleAnnotations.isEmpty(),
                "the inherited default has no legal injector target on RelicItem");
        assertEquals(0, typeInstructionCount(
                concreteOverride, Opcodes.NEW, "java/util/Random"));
        assertEquals(1, invocationCount(
                concreteOverride,
                Opcodes.INVOKEINTERFACE,
                relicOwner,
                "getStatData",
                "(Ljava/lang/String;Ljava/lang/String;)"
                        + "Lit/hurts/sskirillss/relics/items/relics/base/data/leveling/"
                        + "StatData;"));
        assertEquals(1, invocationCount(
                concreteOverride,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/RelicsStatRandomDeterminism",
                "randomFor",
                "(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;"
                        + "Ljava/lang/String;Ljava/lang/String;)Ljava/util/Random;"));
        assertEquals(1, invocationCount(
                concreteOverride,
                Opcodes.INVOKESTATIC,
                "it/hurts/sskirillss/relics/utils/MathUtils",
                "randomBetween",
                "(Ljava/util/Random;DD)D"));
        assertEquals(1, invocationCount(
                concreteOverride,
                Opcodes.INVOKESTATIC,
                "it/hurts/sskirillss/relics/utils/MathUtils",
                "round",
                "(DI)D"));
        assertEquals(1, invocationCount(
                concreteOverride,
                Opcodes.INVOKEINTERFACE,
                relicOwner,
                "setAbilityValue",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Ljava/lang/String;D)V"));

        ClassNode plugin = readClass(
                "com/recipetree/reiexport118/mixin/ReiExportMixinConfigPlugin.class");
        MethodNode shouldApply = method(
                plugin,
                "shouldApplyMixin",
                "(Ljava/lang/String;Ljava/lang/String;)Z");
        MethodInsnNode requestInspection = invocation(
                shouldApply,
                "com/recipetree/reiexport118/mixin/ReiExportMixinConfigPlugin",
                "requestScope");
        MethodInsnNode classHash = invocation(
                shouldApply,
                "com/recipetree/reiexport118/mixin/ReiExportMixinConfigPlugin",
                "sha256");
        MethodInsnNode earlyPublication = invocation(
                shouldApply,
                "com/recipetree/reiexport118/mixin/ReiExportMixinConfigPlugin",
                "publishAuditedRuntimeTargetSelection");
        assertTrue(shouldApply.instructions.indexOf(requestInspection)
                < shouldApply.instructions.indexOf(classHash));
        assertTrue(shouldApply.instructions.indexOf(classHash)
                < shouldApply.instructions.indexOf(earlyPublication));

        MethodNode publish = method(
                plugin,
                "publishAuditedRuntimeTargetSelection",
                "(Ljava/lang/String;)V");
        assertTrue(stringConstants(publish).contains(
                "com.recipetree.reiexport118.mixin.RelicsStatRandomMixin"));
        assertTrue(stringConstants(publish).contains(
                "com.recipetree.reiexport118.mixin.ProjectRedIntegrationPartsMixin"));
        assertEquals(2, invocationCount(
                publish,
                Opcodes.INVOKEVIRTUAL,
                "com/recipetree/reiexport118/mixin/ExactRuntimeSelection",
                "publish",
                "(Ljava/nio/file/Path;)Z"));

        ClassNode determinism = readClass(
                "com/recipetree/reiexport118/compat/RelicsStatRandomDeterminism.class");
        MethodNode randomFor = method(
                determinism,
                "randomFor",
                "(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;"
                        + "Ljava/lang/String;Ljava/lang/String;)Ljava/util/Random;");
        assertEquals(1, invocationCount(
                randomFor,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/mixin/ReiExportMixinConfigPlugin",
                "requireExactRelicsStatRandomSelection",
                "(Ljava/nio/file/Path;)V"));
        assertEquals(0, invocationCount(
                randomFor,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2DeterminismCompatibility",
                "requireArmed",
                "(Ljava/lang/String;)V"));
        assertEquals(1, invocationCount(
                randomFor,
                Opcodes.INVOKEINTERFACE,
                "net/minecraftforge/registries/IForgeRegistry",
                "getKey",
                "(Lnet/minecraftforge/registries/IForgeRegistryEntry;)"
                        + "Lnet/minecraft/resources/ResourceLocation;"));
        assertEquals(1, invocationCount(
                randomFor,
                Opcodes.INVOKEINTERFACE,
                "net/minecraftforge/registries/IForgeRegistry",
                "getValue",
                "(Lnet/minecraft/resources/ResourceLocation;)"
                        + "Lnet/minecraftforge/registries/IForgeRegistryEntry;"));
        assertEquals(0, invocationCount(
                randomFor,
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/core/Registry",
                "getKey",
                "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"));
        assertTrue(stringConstants(randomFor).contains("relics"));
        assertEquals(1, invocationCount(
                randomFor,
                Opcodes.INVOKEVIRTUAL,
                "java/util/concurrent/atomic/AtomicLong",
                "incrementAndGet",
                "()J"));

        ClassNode coordinator = readClass(
                "com/recipetree/reiexport118/ExportCoordinator.class");
        MethodNode tick = method(coordinator, "tick", "()V");
        assertEquals(1, invocationCount(
                tick,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/RelicsStatRandomDeterminism",
                "requireObservedRuntimeApplication",
                "()V"));
    }

    @Test
    void ae2PinsUnorderedParallelPersistenceAndExactReturnHook() throws Exception {
        ClassNode applicator = readPinned(Mm2DeterminismContract.AE2_COLOR_APPLICATOR);
        MethodNode create = method(
                applicator,
                "createFullColorApplicator",
                "()Lnet/minecraft/world/item/ItemStack;");
        assertTrue((create.access & Opcodes.ACC_STATIC) != 0);
        assertEquals(1, invocationCount(
                create,
                Opcodes.INVOKEINTERFACE,
                "com/google/common/collect/BiMap",
                "values",
                "()Ljava/util/Set;"));
        assertEquals(2, invocationCount(
                create,
                Opcodes.INVOKEVIRTUAL,
                "appeng/me/cells/BasicCellInventory",
                "insert",
                "(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;"
                        + "Lappeng/api/networking/security/IActionSource;)J"));

        MethodNode fill = method(
                applicator,
                "m_6787_",
                "(Lnet/minecraft/world/item/CreativeModeTab;Lnet/minecraft/core/NonNullList;)V");
        assertEquals(1, invocationCount(
                fill,
                Opcodes.INVOKESTATIC,
                "appeng/items/tools/powered/ColorApplicatorItem",
                "createFullColorApplicator",
                "()Lnet/minecraft/world/item/ItemStack;"));

        ClassNode inventory = readPinned(Mm2DeterminismContract.AE2_BASIC_CELL);
        MethodNode persist = method(inventory, "persist", "()V");
        assertEquals(1, invocationCount(
                persist,
                Opcodes.INVOKEINTERFACE,
                "it/unimi/dsi/fastutil/objects/Object2LongMap",
                "object2LongEntrySet",
                "()Lit/unimi/dsi/fastutil/objects/ObjectSet;"));
        assertEquals(1, invocationCount(
                persist,
                Opcodes.INVOKEVIRTUAL,
                "appeng/api/stacks/AEKey",
                "toTagGeneric",
                "()Lnet/minecraft/nbt/CompoundTag;"));
        assertTrue(stringConstants(persist).containsAll(List.of("keys", "amts")));

        ClassNode key = readClass("appeng/api/stacks/AEKey.class");
        MethodNode genericTag = method(
                key, "toTagGeneric", "()Lnet/minecraft/nbt/CompoundTag;");
        assertTrue(stringConstants(genericTag).contains("#c"));
    }

    @Test
    void tombstonePinsRandomCallToTheSecondCreativeStackOnly() throws Exception {
        ClassNode target = readPinned(Mm2DeterminismContract.TOMBSTONE_RECEPTACLE);
        MethodNode fill = method(
                target,
                "m_6787_",
                "(Lnet/minecraft/world/item/CreativeModeTab;Lnet/minecraft/core/NonNullList;)V");
        assertEquals(1, invocationCount(
                fill,
                Opcodes.INVOKEVIRTUAL,
                "ovh/corail/tombstone/item/ItemReceptacleOfFamiliar",
                "setRandomFamiliar",
                "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"));

        MethodNode randomize = method(
                target,
                "setRandomFamiliar",
                "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;");
        assertEquals(1, invocationCount(
                randomize,
                Opcodes.INVOKESTATIC,
                "ovh/corail/tombstone/helper/TamableType",
                "getRandomTamableEntityTypeString",
                "()Ljava/lang/String;"));
        assertTrue(stringConstants(randomize).containsAll(
                List.of("dead_pet", "id", "is_spellcaster")));
    }

    @Test
    void industrialForegoingPinsAddNbtBeforeTheLazyTanksFieldExists() throws Exception {
        ClassNode target = readPinned(Mm2DeterminismContract.INFINITY_BACKPACK);
        MethodNode addNbt = method(
                target,
                "addNbt",
                "(Lnet/minecraft/world/item/ItemStack;JIZ)V");
        assertEquals(1, invocationCount(
                addNbt,
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack",
                "m_41751_",
                "(Lnet/minecraft/nbt/CompoundTag;)V"));
        assertFalse(stringConstants(addNbt).contains("Tanks"));

        MethodNode fuelRead = method(
                target,
                "getFuelFromStack",
                "(Lnet/minecraft/world/item/ItemStack;)I");
        assertTrue(stringConstants(fuelRead).contains("Tanks"));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin) throws Exception {
        byte[] bytes;
        try (InputStream input = Mm2CreativeExemplarBytecodeContractTest.class
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

    private static ClassNode readClass(String resource) throws Exception {
        byte[] bytes;
        try (InputStream input = Mm2CreativeExemplarBytecodeContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "missing audited dependency " + resource);
            bytes = input.readAllBytes();
        }
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

    private static boolean hasMethod(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().anyMatch(candidate -> name.equals(candidate.name)
                && descriptor.equals(candidate.desc));
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

    private static MethodInsnNode invocation(
            MethodNode method,
            String owner,
            String name
    ) {
        MethodInsnNode matching = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)) {
                if (matching != null) {
                    throw new AssertionError("duplicate invocation " + owner + "." + name);
                }
                matching = call;
            }
        }
        if (matching == null) {
            throw new AssertionError("missing invocation " + owner + "." + name);
        }
        return matching;
    }

    private static int typeInstructionCount(
            MethodNode method,
            int opcode,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode typeInstruction
                    && typeInstruction.getOpcode() == opcode
                    && descriptor.equals(typeInstruction.desc)) {
                count++;
            }
        }
        return count;
    }

    private static AnnotationNode annotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (descriptor.equals(annotation.desc)) {
                    return annotation;
                }
            }
        }
        throw new AssertionError("missing annotation " + descriptor);
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (name.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        throw new AssertionError("missing annotation value " + name + " on " + annotation.desc);
    }

    private static List<String> stringConstants(MethodNode method) {
        List<String> constants = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof String value) {
                constants.add(value);
            }
        }
        return List.copyOf(constants);
    }
}
