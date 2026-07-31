package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ImmersiveEngineeringTagCacheMixinContractTest {
    private static final String TARGET = "blusunrize/immersiveengineering/api/IEApi";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/ImmersiveEngineeringTagCacheMixin";
    private static final String CACHE_DESCRIPTOR = "Ljava/util/HashMap;";
    private static final String GET_PREFERRED_TAG_STACK_DESCRIPTOR =
            "(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/tags/TagKey;)"
                    + "Lnet/minecraft/world/item/ItemStack;";
    private static final String COMPUTE_IF_ABSENT_DESCRIPTOR =
            "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;";

    @Test
    void pinnedTargetOwnsOneConcreteHashMapMutationSeam() throws IOException {
        ClassNode target = readPinned(Mm2DeterminismContract.IMMERSIVE_ENGINEERING_IE_API);
        assertEquals(TARGET, target.name);

        FieldNode cache = target.fields.stream()
                .filter(field -> "oreOutputPreference".equals(field.name))
                .filter(field -> CACHE_DESCRIPTOR.equals(field.desc))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                cache.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                        | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL));

        MethodNode initializer = method(target, "<clinit>", "()V");
        assertEquals(2, typeInstructionCount(
                initializer, Opcodes.NEW, "java/util/HashMap"));
        assertEquals(1, fieldInstructionCount(
                initializer,
                Opcodes.PUTSTATIC,
                TARGET,
                "oreOutputPreference",
                CACHE_DESCRIPTOR));

        MethodNode preferred = method(
                target, "getPreferredTagStack", GET_PREFERRED_TAG_STACK_DESCRIPTOR);
        assertEquals(1, fieldInstructionCount(
                preferred,
                Opcodes.GETSTATIC,
                TARGET,
                "oreOutputPreference",
                CACHE_DESCRIPTOR));
        assertEquals(1, callCount(
                preferred,
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "computeIfAbsent",
                COMPUTE_IF_ABSENT_DESCRIPTOR));

        int classWideCacheReferences = target.methods.stream()
                .mapToInt(candidate -> fieldInstructionCount(
                        candidate,
                        -1,
                        TARGET,
                        "oreOutputPreference",
                        CACHE_DESCRIPTOR))
                .sum();
        assertEquals(2, classWideCacheReferences,
                "oreOutputPreference ownership changed outside <clinit> and getPreferredTagStack");
    }

    @Test
    void compiledMixinRedirectsOnlyThePinnedComputeIfAbsentCall() throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));

        MethodNode handler = methodByName(
                mixin, "reiexport$synchronizePreferredTagCache");
        AnnotationNode redirect = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(
                List.of("getPreferredTagStack" + GET_PREFERRED_TAG_STACK_DESCRIPTOR),
                value(redirect, "method"));
        assertEquals(1, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode point = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(point, "value"));
        assertEquals(
                "Ljava/util/HashMap;computeIfAbsent" + COMPUTE_IF_ABSENT_DESCRIPTOR,
                value(point, "target"));

        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2DeterminismCompatibility",
                "requireArmed",
                "(Ljava/lang/String;)V"));
        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2IePreferredTagCacheRepair",
                "compute",
                "(Ljava/util/HashMap;Ljava/lang/Object;Ljava/util/function/Function;)"
                        + "Ljava/lang/Object;"));
    }

    @Test
    void mixinConfigurationIncludesExactCacheRepairOnce() throws IOException {
        String config;
        try (InputStream input = ImmersiveEngineeringTagCacheMixinContractTest.class
                .getClassLoader().getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "compiled mixin configuration");
            config = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"ImmersiveEngineeringTagCacheMixin\""));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                ImmersiveEngineeringTagCacheMixinContractTest.class
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
        try (InputStream input = ImmersiveEngineeringTagCacheMixinContractTest.class
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

    private static int fieldInstructionCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && (opcode < 0 || field.getOpcode() == opcode)
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int typeInstructionCount(
            MethodNode method,
            int opcode,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == opcode
                    && descriptor.equals(type.desc)) {
                count++;
            }
        }
        return count;
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
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
