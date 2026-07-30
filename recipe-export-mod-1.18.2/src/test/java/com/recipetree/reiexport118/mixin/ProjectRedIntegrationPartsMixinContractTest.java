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
import org.objectweb.asm.tree.FieldInsnNode;
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

final class ProjectRedIntegrationPartsMixinContractTest {
    private static final String TARGET =
            "mrtjp/projectred/integration/init/IntegrationParts";
    private static final String GATE_TYPE =
            "mrtjp/projectred/integration/GateType";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/ProjectRedIntegrationPartsMixin";

    @TempDir
    Path temporaryDirectory;

    @Test
    void pinnedCrossModuleRegistrationHasTheSingleRaceSeam() throws IOException {
        ClassNode integration = readPinned(
                Mm2DeterminismContract.PROJECT_RED_INTEGRATION_PARTS);
        assertEquals(TARGET, integration.name);
        MethodNode register = method(integration, "register", "()V");
        assertEquals(1, callCount(
                register, Opcodes.INVOKESTATIC, GATE_TYPE, "values",
                "()[Lmrtjp/projectred/integration/GateType;"));
        assertEquals(1, callCount(
                register, Opcodes.INVOKEVIRTUAL, GATE_TYPE, "isEnabled", "()Z"));
        assertEquals(1, callCount(
                register, Opcodes.INVOKEVIRTUAL, GATE_TYPE, "registerParts",
                "(Lnet/minecraftforge/registries/DeferredRegister;"
                        + "Lnet/minecraftforge/registries/DeferredRegister;)V"));

        ClassNode fabrication = readPinned(
                Mm2DeterminismContract.PROJECT_RED_FABRICATION_PARTS);
        MethodNode fabricationRegister = method(fabrication, "register", "()V");
        assertEquals(1, callCount(
                fabricationRegister,
                Opcodes.INVOKEVIRTUAL,
                GATE_TYPE,
                "inject",
                "(Ljava/lang/String;Ljava/util/function/Function;"
                        + "Lnet/minecraftforge/registries/RegistryObject;"
                        + "Lnet/minecraftforge/registries/RegistryObject;)V"));
    }

    @Test
    void compiledMixinRedirectsOnlyTheEnabledDecisionAndRecordsTheBranch()
            throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));

        MethodNode handler = method(
                mixin,
                "reiexport$skipDuplicateFabricatedGate",
                "(Lmrtjp/projectred/integration/GateType;)Z");
        assertTrue((handler.access & Opcodes.ACC_STATIC) != 0);
        AnnotationNode redirect = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(List.of("register()V"), value(redirect, "method"));
        assertEquals(1, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode point = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(point, "value"));
        assertEquals(
                "Lmrtjp/projectred/integration/GateType;isEnabled()Z",
                value(point, "target"));

        assertEquals(1, callCount(
                handler, Opcodes.INVOKEVIRTUAL, GATE_TYPE, "isEnabled", "()Z"));
        assertEquals(1, fieldCount(
                handler, Opcodes.GETSTATIC, GATE_TYPE, "FABRICATED_GATE",
                "Lmrtjp/projectred/integration/GateType;"));
        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/mixin/ReiExportMixinConfigPlugin",
                "requireExactProjectRedRegistrationSelection",
                "(Ljava/nio/file/Path;)V"));
        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2ProjectRedRegistrationGate",
                "filterRegistration",
                "(ZZ)Z"));
    }

    @Test
    void pinAndMixinAreBoundToTheExactMm2Request() throws Exception {
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.PROJECT_RED_INTEGRATION));
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.PROJECT_RED_FABRICATION));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.PROJECT_RED_INTEGRATION_PARTS));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.PROJECT_RED_GATE_TYPE));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.PROJECT_RED_FABRICATION_PARTS));

        Path absentDirectory = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absentDirectory);
        assertFalse(disabled.shouldApplyMixin(
                Mm2DeterminismContract.PROJECT_RED_INTEGRATION_PARTS.className(),
                MIXIN.replace('/', '.')));

        Path exactDirectory = Files.createDirectory(temporaryDirectory.resolve("exact"));
        Files.writeString(
                exactDirectory.resolve(Mm2ExportRequestScope.REQUEST_NAME),
                "{\"profile\":\"" + Mm2ExportRequestScope.PROFILE
                        + "\",\"packName\":\"" + Mm2ExportRequestScope.PACK_NAME + "\"}");
        ReiExportMixinConfigPlugin enabled = new ReiExportMixinConfigPlugin(exactDirectory);
        assertTrue(enabled.shouldApplyMixin(
                Mm2DeterminismContract.PROJECT_RED_INTEGRATION_PARTS.className(),
                MIXIN.replace('/', '.')));
        assertThrows(IllegalStateException.class, () -> enabled.shouldApplyMixin(
                "drifted.Target", MIXIN.replace('/', '.')));

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "compiled mixin configuration");
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"ProjectRedIntegrationPartsMixin\""));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                ProjectRedIntegrationPartsMixinContractTest.class
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
        try (InputStream input = ProjectRedIntegrationPartsMixinContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return read(input.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytecode) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
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
                    && opcode == call.getOpcode()
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int fieldCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && opcode == field.getOpcode()
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
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

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (name.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        throw new AssertionError("missing annotation value " + name + " on " + annotation.desc);
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String sha256(byte[] bytecode) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytecode));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
