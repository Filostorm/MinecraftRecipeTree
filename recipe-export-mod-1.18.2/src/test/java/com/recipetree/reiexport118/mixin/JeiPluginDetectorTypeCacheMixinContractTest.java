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

final class JeiPluginDetectorTypeCacheMixinContractTest {
    private static final String TARGET =
            "me/shedaniel/rei/jeicompat/JEIPluginDetector";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/JeiPluginDetectorTypeCacheMixin";
    private static final String TYPE_MAP_DESCRIPTOR = "Ljava/util/Map;";
    private static final String UNWRAP_TYPE_DESCRIPTOR =
            "(Lmezz/jei/api/ingredients/IIngredientType;)"
                    + "Lme/shedaniel/rei/api/common/entry/type/EntryType;";

    @Test
    void pinnedTargetOwnsOnePrivateHashMapUsedOnlyByUnwrapType() throws IOException {
        ClassNode target = readPinned(Mm2DeterminismContract.JEI_PLUGIN_DETECTOR);
        assertEquals(TARGET, target.name);

        FieldNode typeMap = target.fields.stream()
                .filter(field -> "TYPE_MAP".equals(field.name))
                .filter(field -> TYPE_MAP_DESCRIPTOR.equals(field.desc))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                typeMap.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                        | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL));

        MethodNode initializer = method(target, "<clinit>", "()V");
        assertEquals(4, typeInstructionCount(initializer, Opcodes.NEW, "java/util/HashMap"));
        assertEquals(1, fieldInstructionCount(
                initializer, Opcodes.PUTSTATIC, TARGET, "TYPE_MAP", TYPE_MAP_DESCRIPTOR));

        MethodNode unwrapType = method(target, "unwrapType", UNWRAP_TYPE_DESCRIPTOR);
        assertEquals(1, fieldInstructionCount(
                unwrapType, Opcodes.GETSTATIC, TARGET, "TYPE_MAP", TYPE_MAP_DESCRIPTOR));
        assertEquals(1, callCount(
                unwrapType,
                Opcodes.INVOKEINTERFACE,
                "java/util/Map",
                "computeIfAbsent",
                "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"));

        int classWideTypeMapReferences = target.methods.stream()
                .mapToInt(candidate -> fieldInstructionCount(
                        candidate, -1, TARGET, "TYPE_MAP", TYPE_MAP_DESCRIPTOR))
                .sum();
        assertEquals(2, classWideTypeMapReferences,
                "TYPE_MAP ownership changed outside <clinit> and unwrapType");
    }

    @Test
    void compiledMixinReplacesTheCacheExactlyOnceAtClassInitializationReturn()
            throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));

        FieldNode typeMap = mixin.fields.stream()
                .filter(field -> "TYPE_MAP".equals(field.name))
                .filter(field -> TYPE_MAP_DESCRIPTOR.equals(field.desc))
                .findFirst()
                .orElseThrow();
        assertNotNull(annotation(
                annotations(typeMap),
                "Lorg/spongepowered/asm/mixin/Shadow;"));
        assertNotNull(annotation(
                annotations(typeMap),
                "Lorg/spongepowered/asm/mixin/Final;"));
        assertNotNull(annotation(
                annotations(typeMap),
                "Lorg/spongepowered/asm/mixin/Mutable;"));

        MethodNode handler = methodByName(
                mixin, "reiexport$installConcurrentIngredientTypeCache");
        AnnotationNode inject = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("<clinit>"), value(inject, "method"));
        assertEquals(1, value(inject, "require"));
        assertEquals(false, value(inject, "remap"));
        List<?> points = (List<?>) value(inject, "at");
        assertEquals(1, points.size());
        assertEquals("RETURN", value((AnnotationNode) points.get(0), "value"));

        assertEquals(1, fieldInstructionCount(
                handler, Opcodes.GETSTATIC, MIXIN, "TYPE_MAP", TYPE_MAP_DESCRIPTOR));
        assertEquals(1, fieldInstructionCount(
                handler, Opcodes.PUTSTATIC, MIXIN, "TYPE_MAP", TYPE_MAP_DESCRIPTOR));
        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2JeiIngredientTypeCacheRepair",
                "install",
                "(Ljava/util/Map;)Ljava/util/Map;"));
    }

    @Test
    void mixinConfigurationIncludesExactCacheRepairOnce() throws IOException {
        String config;
        try (InputStream input = JeiPluginDetectorTypeCacheMixinContractTest.class
                .getClassLoader().getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "compiled mixin configuration");
            config = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"JeiPluginDetectorTypeCacheMixin\""));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                JeiPluginDetectorTypeCacheMixinContractTest.class
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
        try (InputStream input = JeiPluginDetectorTypeCacheMixinContractTest.class
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

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        return annotations;
    }

    private static List<AnnotationNode> annotations(FieldNode field) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (field.visibleAnnotations != null) annotations.addAll(field.visibleAnnotations);
        if (field.invisibleAnnotations != null) annotations.addAll(field.invisibleAnnotations);
        return annotations;
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
