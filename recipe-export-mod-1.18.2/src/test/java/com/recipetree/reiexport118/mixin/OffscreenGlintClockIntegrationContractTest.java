package com.recipetree.reiexport118.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OffscreenGlintClockIntegrationContractTest {
    private static final String CLOCK =
            "com/recipetree/reiexport118/compat/Mm2OffscreenGlintClock";
    private static final String SCOPE = CLOCK + "$CaptureScope";
    private static final String CONTRACT =
            "com/recipetree/reiexport118/compat/Mm2OffscreenGlintClockContract";
    private static final String PIN = CONTRACT + "$CoreClassPin";
    private static final String MIXIN_FQCN =
            "com.recipetree.reiexport118.mixin.RenderStateShardGlintClockMixin";

    @Test
    void requiredMixinConfigContainsTheExactGlintRedirectOnce() throws IOException {
        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"RenderStateShardGlintClockMixin\""));
        assertTrue(config.contains("\"required\": true"));
        assertTrue(config.contains("\"defaultRequire\": 1"));
    }

    @Test
    void earlyMixinPluginRoutesTheExactNameToTheFullProductionResourcePin()
            throws IOException {
        ClassNode plugin = readResource(
                "com/recipetree/reiexport118/mixin/ReiExportMixinConfigPlugin.class");
        FieldNode name = field(plugin, "RENDER_STATE_SHARD_GLINT_CLOCK_MIXIN");
        assertEquals(MIXIN_FQCN, name.value);

        MethodNode auditedTarget = methodByName(plugin, "auditedTarget");
        assertEquals(1, fieldCount(
                auditedTarget,
                Opcodes.GETSTATIC,
                CONTRACT,
                "RENDER_STATE_SHARD",
                "L" + PIN + ";"));
        assertEquals(1, callCount(auditedTarget, Opcodes.INVOKEVIRTUAL,
                PIN, "className", "()Ljava/lang/String;"));
        assertEquals(1, callCount(auditedTarget, Opcodes.INVOKEVIRTUAL,
                PIN, "resource", "()Ljava/lang/String;"));
        assertEquals(1, callCount(auditedTarget, Opcodes.INVOKEVIRTUAL,
                PIN, "sha256", "()Ljava/lang/String;"));
        assertEquals(1, callCount(auditedTarget, Opcodes.INVOKEVIRTUAL,
                PIN, "resourceStage", "()Ljava/lang/String;"));
        assertTrue(callCount(auditedTarget, Opcodes.INVOKESPECIAL,
                plugin.name + "$AuditedTarget", "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z"
                        + "Ljava/lang/String;)V") >= 1,
                "the routed glint pin retains its resource-stage constructor");
    }

    @Test
    void offscreenScopeEnclosesOwnedDrawFlushReadbackAndRestore() throws IOException {
        ClassNode renderer = readResource("com/recipetree/reiexport118/OffscreenRenderer.class");
        String captureDescriptor =
                "(IIILcom/recipetree/reiexport118/compat/"
                        + "LowDragFboViewportCompatibility$CaptureMode;Ljava/lang/String;ZI"
                        + "Ljava/util/function/Supplier;"
                        + "Ljava/util/function/Consumer;)Lcom/mojang/blaze3d/platform/NativeImage;";
        String ownedDescriptor =
                "(IIILcom/recipetree/reiexport118/compat/"
                        + "LowDragFboViewportCompatibility$CaptureMode;Ljava/lang/String;ZI"
                        + "Ljava/util/function/Consumer;)Lcom/mojang/blaze3d/platform/NativeImage;";
        MethodNode capture = method(renderer, "capture", captureDescriptor);
        MethodNode owned = method(renderer, "captureOwnedTarget", ownedDescriptor);

        MethodInsnNode begin = soleCall(capture, Opcodes.INVOKESTATIC, CLOCK,
                "beginOffscreenCapture", "(Ljava/lang/String;)L" + SCOPE + ";");
        MethodInsnNode invokeOwned = soleCall(capture, Opcodes.INVOKEVIRTUAL,
                renderer.name, "captureOwnedTarget", ownedDescriptor);
        List<MethodInsnNode> closes = calls(capture, Opcodes.INVOKEVIRTUAL,
                SCOPE, "close", "()V");
        assertEquals(2, closes.size(),
                "try-with-resources emits normal and exceptional balanced closes");
        assertTrue(instructionIndex(capture, begin) < instructionIndex(capture, invokeOwned));
        assertTrue(instructionIndex(capture, invokeOwned) < instructionIndex(capture, closes.get(0)));

        assertEquals(1, callCount(owned, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/renderer/MultiBufferSource$BufferSource",
                "endBatch", "()V"));
        assertEquals(1, callCount(owned, Opcodes.INVOKEVIRTUAL,
                "com/mojang/blaze3d/platform/NativeImage",
                "downloadTexture", "(IZ)V"));
        assertTrue(callCount(owned, Opcodes.INVOKEVIRTUAL,
                renderer.name, "restore", "()V") >= 1);
    }

    @Test
    void catalogDeclaresOnlyAuditedVanillaFoilPotionsAsPositiveControls()
            throws IOException {
        ClassNode catalog = readResource("com/recipetree/reiexport118/ItemCatalog.class");
        MethodNode draw = catalog.methods.stream()
                .filter(method -> method.name.startsWith("lambda$renderIcon$"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing catalog render lambda"));
        MethodInsnNode active = soleCall(draw, Opcodes.INVOKESTATIC,
                CLOCK, "isCaptureActive", "()Z");
        MethodInsnNode foil = soleCall(draw, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack", "hasFoil", "()Z");
        MethodInsnNode potion = soleCall(draw, Opcodes.INVOKESTATIC,
                catalog.name, "isVanillaPotion",
                "(Lnet/minecraft/resources/ResourceLocation;)Z");
        MethodInsnNode require = soleCall(draw, Opcodes.INVOKESTATIC,
                CLOCK, "requireKnownSampleInterception", "(Ljava/lang/String;)V");
        MethodInsnNode render = soleCall(draw, Opcodes.INVOKEINTERFACE,
                "me/shedaniel/rei/api/common/entry/EntryStack", "render",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                        + "Lme/shedaniel/math/Rectangle;IIF)V");
        assertTrue(instructionIndex(draw, active) < instructionIndex(draw, foil));
        assertTrue(instructionIndex(draw, foil) < instructionIndex(draw, potion));
        assertTrue(instructionIndex(draw, potion) < instructionIndex(draw, require));
        assertTrue(instructionIndex(draw, require) < instructionIndex(draw, render),
                "positive control is declared before native rendering and deferred flush");

        MethodNode classifier = method(
                catalog,
                "isVanillaPotion",
                "(Lnet/minecraft/resources/ResourceLocation;)Z");
        assertEquals(1, ldcCount(classifier, "minecraft"));
        assertEquals(1, ldcCount(classifier, "potion"));
        assertEquals(1, ldcCount(classifier, "splash_potion"));
        assertEquals(1, ldcCount(classifier, "lingering_potion"));
    }

    @Test
    void exportBoundaryAuditsOneRunDeltaBeforePublication() throws IOException {
        ClassNode job = readResource("com/recipetree/reiexport118/ExportJob.class");
        MethodNode constructor = methodByName(job, "<init>");
        MethodNode finish = method(job, "finish", "()V");
        assertEquals(1, callCount(constructor, Opcodes.INVOKESTATIC,
                CLOCK, "auditSnapshot", "()L" + CLOCK + "$AuditSnapshot;"));
        assertEquals(1, fieldCount(finish, Opcodes.GETFIELD,
                job.name, "glintAuditBaseline", "L" + CLOCK + "$AuditSnapshot;"));
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/Mm2DeterminismCompatibility",
                "isLifecycleArmed", "()Z"));
        MethodInsnNode audit = soleCall(finish, Opcodes.INVOKESTATIC,
                CLOCK, "requireKnownSampleInterceptionSince",
                "(L" + CLOCK + "$AuditSnapshot;Ljava/lang/String;)V");
        MethodInsnNode contextFinish = soleCall(finish, Opcodes.INVOKEVIRTUAL,
                "com/recipetree/reiexport118/ExportContext", "finish",
                "(ZJLcom/recipetree/reiexport118/ExportPlan;)V");
        assertTrue(instructionIndex(finish, audit) < instructionIndex(finish, contextFinish));
    }

    private static ClassNode readResource(String path) throws IOException {
        try (InputStream input = OffscreenGlintClockIntegrationContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static FieldNode field(ClassNode owner, String name) {
        List<FieldNode> fields = owner.fields.stream()
                .filter(field -> name.equals(field.name))
                .toList();
        assertEquals(1, fields.size(), owner.name + "." + name);
        return fields.get(0);
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> name.equals(method.name))
                .filter(method -> descriptor.equals(method.desc))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name + descriptor);
        return methods.get(0);
    }

    private static MethodNode methodByName(ClassNode owner, String name) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> name.equals(method.name))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name);
        return methods.get(0);
    }

    private static MethodInsnNode soleCall(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        List<MethodInsnNode> calls = calls(method, opcode, owner, name, descriptor);
        assertEquals(1, calls.size(), owner + "." + name + descriptor);
        return calls.get(0);
    }

    private static List<MethodInsnNode> calls(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        List<MethodInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == opcode
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                matches.add(call);
            }
        }
        return matches;
    }

    private static int callCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        return calls(method, opcode, owner, name, descriptor).size();
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
                    && field.getOpcode() == opcode
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int ldcCount(MethodNode method, Object constant) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode ldc && constant.equals(ldc.cst)) {
                count++;
            }
        }
        return count;
    }

    private static int instructionIndex(MethodNode method, AbstractInsnNode target) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction == target) {
                return index;
            }
            index++;
        }
        throw new AssertionError("instruction is not owned by " + method.name);
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
