package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2ExportRequestScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LowDragCycleItemStackHandlerMixinContractTest {
    private static final String TARGET =
            "com/lowdragmc/lowdraglib/utils/CycleItemStackHandler";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/LowDragCycleItemStackHandlerMixin";
    private static final String GET_STACK_DESCRIPTOR =
            "(I)Lnet/minecraft/world/item/ItemStack;";

    @TempDir
    Path temporaryDirectory;

    @Test
    void pinnedTargetHasOneClockDrivenCandidateSelectionSeam() throws IOException {
        ClassNode target = readPinned(Mm2DeterminismContract.LOW_DRAG_CYCLE_ITEM_STACK_HANDLER);
        assertEquals(TARGET, target.name);

        List<FieldNode> stackFields = target.fields.stream()
                .filter(field -> "stacks".equals(field.name))
                .filter(field -> "Ljava/util/List;".equals(field.desc))
                .toList();
        assertEquals(1, stackFields.size());
        assertEquals(
                Opcodes.ACC_PRIVATE,
                stackFields.get(0).access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                        | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL));

        MethodNode method = method(target, "getStackInSlot", GET_STACK_DESCRIPTOR);
        assertEquals(0, method.access & Opcodes.ACC_STATIC);
        assertEquals(1, callCount(
                method,
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "currentTimeMillis",
                "()J"));
        assertEquals(1, callCount(
                method,
                Opcodes.INVOKESTATIC,
                "java/lang/Math",
                "abs",
                "(I)I"));
        assertEquals(2, callCount(
                method,
                Opcodes.INVOKEINTERFACE,
                "java/util/List",
                "get",
                "(I)Ljava/lang/Object;"));
        assertEquals(1, longConstantCount(method, 1_000L));
        assertEquals(1, opcodeCount(method, Opcodes.LDIV));
        assertEquals(1, opcodeCount(method, Opcodes.L2I));
        assertEquals(1, opcodeCount(method, Opcodes.IREM));

        int classWideClockReads = target.methods.stream()
                .mapToInt(candidate -> callCount(
                        candidate,
                        Opcodes.INVOKESTATIC,
                        "java/lang/System",
                        "currentTimeMillis",
                        "()J"))
                .sum();
        assertEquals(1, classWideClockReads,
                "a new LowDrag wall-clock selection seam requires a separate audit");
    }

    @Test
    void compiledMixinRedirectsOnlyThePinnedClockRead() throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));

        MethodNode handler = methodByName(mixin, "reiexport$selectFirstCandidate");
        assertEquals(0, handler.access & Opcodes.ACC_STATIC,
                "redirect handler staticness must match the instance target method");
        AnnotationNode redirect = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(
                List.of("getStackInSlot" + GET_STACK_DESCRIPTOR),
                value(redirect, "method"));
        assertEquals(1, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode point = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(point, "value"));
        assertEquals("Ljava/lang/System;currentTimeMillis()J", value(point, "target"));

        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2DeterminismCompatibility",
                "requireArmed",
                "(Ljava/lang/String;)V"));
        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2LowDragCycleSelectionRepair",
                "firstCandidateEpochMillis",
                "()J"));
        assertEquals(0, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "currentTimeMillis",
                "()J"));
    }

    @Test
    void pinAndMixinAreBoundToTheExactMm2Request() throws Exception {
        assertEquals("ldlib", Mm2DeterminismContract.LOW_DRAG_LIB.modId());
        assertEquals("1.18.2-1.0.8", Mm2DeterminismContract.LOW_DRAG_LIB.version());
        assertEquals(
                "dbf3032612be9e0c7448673bac8f6c14b1bab3e6927aff4e27182309de900b50",
                Mm2DeterminismContract.LOW_DRAG_LIB.jarSha256());
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.LOW_DRAG_LIB));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.LOW_DRAG_CYCLE_ITEM_STACK_HANDLER));

        Path absentDirectory = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absentDirectory);
        assertFalse(disabled.shouldApplyMixin(
                Mm2DeterminismContract.LOW_DRAG_CYCLE_ITEM_STACK_HANDLER.className(),
                MIXIN.replace('/', '.')));

        Path exactDirectory = Files.createDirectory(temporaryDirectory.resolve("exact"));
        Files.writeString(
                exactDirectory.resolve(Mm2ExportRequestScope.REQUEST_NAME),
                "{\"profile\":\"" + Mm2ExportRequestScope.PROFILE
                        + "\",\"packName\":\"" + Mm2ExportRequestScope.PACK_NAME + "\"}");
        ReiExportMixinConfigPlugin enabled = new ReiExportMixinConfigPlugin(exactDirectory);
        assertTrue(enabled.shouldApplyMixin(
                Mm2DeterminismContract.LOW_DRAG_CYCLE_ITEM_STACK_HANDLER.className(),
                MIXIN.replace('/', '.')));
        assertThrows(IllegalStateException.class, () -> enabled.shouldApplyMixin(
                "drifted.Target", MIXIN.replace('/', '.')));

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "compiled mixin configuration");
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"LowDragCycleItemStackHandlerMixin\""));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                LowDragCycleItemStackHandlerMixinContractTest.class
                        .getClassLoader().getResources(pin.resource()))) {
            try (InputStream input = url.openStream()) {
                byte[] bytecode = input.readAllBytes();
                String hash = sha256(bytecode);
                observed.add(url + " sha256=" + hash);
                if (pin.sha256().equals(hash)) {
                    exact.add(bytecode);
                }
            }
        }
        assertTrue(!observed.isEmpty(), "missing pinned resource " + pin.resource());
        assertEquals(observed.size(), exact.size(),
                "every visible copy must match the exact pin; observed=" + observed);
        return read(exact.get(0));
    }

    private static ClassNode readResource(String resource) throws IOException {
        try (InputStream input = LowDragCycleItemStackHandlerMixinContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return read(input.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytecode) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(
                node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .filter(candidate -> descriptor.equals(candidate.desc))
                .toList();
        assertEquals(1, matches.size(), name + descriptor);
        return matches.get(0);
    }

    private static MethodNode methodByName(ClassNode owner, String name) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .toList();
        assertEquals(1, matches.size(), name);
        return matches.get(0);
    }

    private static int callCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == opcode
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int longConstantCount(MethodNode method, long expected) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && Long.valueOf(expected).equals(constant.cst)) {
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

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        return annotations;
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

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (name.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        throw new AssertionError("missing annotation value " + name);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
