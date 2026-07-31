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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

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

final class MultiblockedCycleBlockStateRendererMixinContractTest {
    private static final String TARGET =
            "com/lowdragmc/multiblocked/client/renderer/impl/CycleBlockStateRenderer";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/MultiblockedCycleBlockStateRendererMixin";
    private static final String BLOCK_INFO =
            "com/lowdragmc/lowdraglib/utils/BlockInfo";
    private static final String BLOCK_INFO_ARRAY_DESCRIPTOR = "[L" + BLOCK_INFO + ";";
    private static final String GET_BLOCK_INFO_DESCRIPTOR = "()L" + BLOCK_INFO + ";";
    private static final String REPAIR =
            "com/recipetree/reiexport118/compat/Mm2MultiblockedCycleStateRepair";

    @TempDir
    Path temporaryDirectory;

    @Test
    void pinnedTargetHasTheSingleSharedClockAndRandomSelectionSeam() throws IOException {
        ClassNode target = readPinned(
                Mm2DeterminismContract.MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER);
        assertEquals(TARGET, target.name);

        FieldNode candidates = field(target, "blockInfos", BLOCK_INFO_ARRAY_DESCRIPTOR);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                candidates.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                        | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL));
        assertEquals(Opcodes.ACC_PUBLIC, visibility(field(target, "index", "I")));
        assertEquals(Opcodes.ACC_PUBLIC, visibility(field(target, "lastTime", "J")));

        MethodNode method = method(target, "getBlockInfo", GET_BLOCK_INFO_DESCRIPTOR);
        assertEquals(Opcodes.ACC_PUBLIC, visibility(method));
        assertEquals(1, callCount(
                method,
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "currentTimeMillis",
                "()J"));
        assertEquals(1, callCount(
                method,
                Opcodes.INVOKEVIRTUAL,
                "java/util/Random",
                "nextInt",
                "()I"));
        assertEquals(1, callCount(
                method,
                Opcodes.INVOKESTATIC,
                "java/lang/Math",
                "abs",
                "(I)I"));
        assertEquals(1, fieldAccessCount(
                method,
                Opcodes.GETSTATIC,
                "com/lowdragmc/lowdraglib/LDLMod",
                "random",
                "Ljava/util/Random;"));
        assertEquals(2, fieldAccessCount(
                method,
                Opcodes.GETFIELD,
                TARGET,
                "blockInfos",
                BLOCK_INFO_ARRAY_DESCRIPTOR));
        assertEquals(1, longConstantCount(method, 1_000L));
        assertEquals(1, opcodeCount(method, Opcodes.IREM));
        assertEquals(1, opcodeCount(method, Opcodes.AALOAD));

        int classWideClockReads = target.methods.stream()
                .mapToInt(candidate -> callCount(
                        candidate,
                        Opcodes.INVOKESTATIC,
                        "java/lang/System",
                        "currentTimeMillis",
                        "()J"))
                .sum();
        assertEquals(1, classWideClockReads,
                "a new Multiblocked cycle clock requires a separate audit");
    }

    @Test
    void compiledMixinReturnsCandidateZeroWithoutClockRandomOrStateMutation()
            throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));
        annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Pseudo;");

        FieldNode candidates = field(mixin, "blockInfos", BLOCK_INFO_ARRAY_DESCRIPTOR);
        annotation(candidates.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;");
        annotation(candidates.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Final;");

        MethodNode handler = methodByName(mixin, "reiexport$selectFirstCandidate");
        assertEquals(0, handler.access & Opcodes.ACC_STATIC);
        AnnotationNode inject = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(
                List.of("getBlockInfo" + GET_BLOCK_INFO_DESCRIPTOR),
                value(inject, "method"));
        assertEquals(true, value(inject, "cancellable"));
        assertEquals(1, value(inject, "require"));
        assertEquals(false, value(inject, "remap"));
        List<?> points = (List<?>) value(inject, "at");
        assertEquals(1, points.size());
        AnnotationNode point = (AnnotationNode) points.get(0);
        assertEquals("HEAD", value(point, "value"));

        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2DeterminismCompatibility",
                "requireArmed",
                "(Ljava/lang/String;)V"));
        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2MultiblockedCycleStateRepair",
                "firstCandidate",
                "([Ljava/lang/Object;)Ljava/lang/Object;"));
        assertEquals(1, fieldAccessCount(
                handler,
                Opcodes.GETFIELD,
                MIXIN,
                "blockInfos",
                BLOCK_INFO_ARRAY_DESCRIPTOR));
        assertEquals(1, typeInstructionCount(handler, Opcodes.CHECKCAST, BLOCK_INFO));
        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKEVIRTUAL,
                "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
                "setReturnValue",
                "(Ljava/lang/Object;)V"));
        assertEquals(0, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "currentTimeMillis",
                "()J"));
        assertEquals(0, fieldWriteCount(handler));
    }

    @Test
    void pinAndMixinAreBoundToTheExactMm2ExporterLifecycle() throws Exception {
        assertEquals("multiblocked", Mm2DeterminismContract.MULTIBLOCKED.modId());
        assertEquals("1.18.2-1.0.10", Mm2DeterminismContract.MULTIBLOCKED.version());
        assertEquals(
                "45661399563a17d6c4c99fa7abe65e17039902874741ddda30cb99df68a7ec93",
                Mm2DeterminismContract.MULTIBLOCKED.jarSha256());
        assertEquals(
                "cd6c1e871dd2e3d5fbb2cf4cdee29a1fd17a7dc9b8a57a4f1365c86a6c181087",
                Mm2DeterminismContract.MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER.sha256());
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.MULTIBLOCKED));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER));

        Path absentDirectory = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absentDirectory);
        assertFalse(disabled.shouldApplyMixin(
                Mm2DeterminismContract.MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER.className(),
                MIXIN.replace('/', '.')));

        Path exactDirectory = Files.createDirectory(temporaryDirectory.resolve("exact"));
        Files.writeString(
                exactDirectory.resolve(Mm2ExportRequestScope.REQUEST_NAME),
                "{\"profile\":\"" + Mm2ExportRequestScope.PROFILE
                        + "\",\"packName\":\"" + Mm2ExportRequestScope.PACK_NAME + "\"}");
        ReiExportMixinConfigPlugin enabled = new ReiExportMixinConfigPlugin(exactDirectory);
        assertTrue(enabled.shouldApplyMixin(
                Mm2DeterminismContract.MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER.className(),
                MIXIN.replace('/', '.')));
        assertThrows(IllegalStateException.class, () -> enabled.shouldApplyMixin(
                "drifted.Target", MIXIN.replace('/', '.')));

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "compiled mixin configuration");
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(
                config, "\"MultiblockedCycleBlockStateRendererMixin\""));
    }

    @Test
    void ownedReloadAndPublicationBoundariesFailClosedOnMissingInterception()
            throws IOException {
        ClassNode lifecycle = readResource(
                "com/recipetree/reiexport118/compat/Mm2ReiLifecycleGate.class");
        MethodNode reload = method(
                lifecycle,
                "reloadAfterRecipeSync",
                "(Lnet/minecraft/client/multiplayer/ClientPacketListener;)V");
        int registryRepair = callIndex(
                reload,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2RegistryRepairs",
                "repairAndVerifyAfterOwnedReload",
                "()V");
        int postReloadAssertion = callIndex(
                reload,
                Opcodes.INVOKESTATIC,
                REPAIR,
                "requireObservedAfterOwnedReiReload",
                "()J");
        int completeReload = callIndex(
                reload,
                Opcodes.INVOKEVIRTUAL,
                "com/recipetree/reiexport118/compat/Mm2ReiLifecycleSequence",
                "completeOwnedReload",
                "(Ljava/lang/Thread;)V");
        assertTrue(registryRepair < postReloadAssertion);
        assertTrue(postReloadAssertion < completeReload);
        assertEquals(1, callCount(
                reload,
                Opcodes.INVOKESTATIC,
                REPAIR,
                "requireObservedAfterOwnedReiReload",
                "()J"));

        ClassNode exportJob = readResource(
                "com/recipetree/reiexport118/ExportJob.class");
        MethodNode finish = method(exportJob, "finish", "()V");
        int closeCaptureScope = callIndex(
                finish,
                Opcodes.INVOKEVIRTUAL,
                "com/recipetree/reiexport118/ExportJob",
                "closeBlockAtlasScope",
                "()V");
        int publicationAssertion = callIndex(
                finish,
                Opcodes.INVOKESTATIC,
                REPAIR,
                "requireObservedBeforePublication",
                "()J");
        int finishDocuments = callIndex(
                finish,
                Opcodes.INVOKEVIRTUAL,
                "com/recipetree/reiexport118/ExportContext",
                "finish",
                "(ZJLcom/recipetree/reiexport118/ExportPlan;)V");
        int publish = callIndex(
                finish,
                Opcodes.INVOKEVIRTUAL,
                "com/recipetree/reiexport118/ExportContext",
                "publish",
                "()V");
        assertTrue(closeCaptureScope < publicationAssertion);
        assertTrue(publicationAssertion < finishDocuments);
        assertTrue(publicationAssertion < publish);
        assertEquals(1, callCount(
                finish,
                Opcodes.INVOKESTATIC,
                REPAIR,
                "requireObservedBeforePublication",
                "()J"));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                MultiblockedCycleBlockStateRendererMixinContractTest.class
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
        try (InputStream input =
                     MultiblockedCycleBlockStateRendererMixinContractTest.class
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

    private static FieldNode field(ClassNode owner, String name, String descriptor) {
        List<FieldNode> matches = owner.fields.stream()
                .filter(candidate -> name.equals(candidate.name))
                .filter(candidate -> descriptor.equals(candidate.desc))
                .toList();
        assertEquals(1, matches.size(), name + " " + descriptor);
        return matches.get(0);
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

    private static int visibility(FieldNode field) {
        return field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
    }

    private static int visibility(MethodNode method) {
        return method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
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

    private static int callIndex(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == opcode
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                return index;
            }
            index++;
        }
        throw new AssertionError("missing call " + owner + "." + name + descriptor);
    }

    private static int fieldAccessCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == opcode
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

    private static int fieldWriteCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.PUTFIELD
                    || instruction.getOpcode() == Opcodes.PUTSTATIC) {
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
