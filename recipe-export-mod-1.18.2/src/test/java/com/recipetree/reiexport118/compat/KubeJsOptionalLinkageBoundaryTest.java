package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the public exporter startup path from acquiring an optional KubeJS hard dependency. */
final class KubeJsOptionalLinkageBoundaryTest {
    private static final String PREFLIGHT_CLASS =
            "com.recipetree.reiexport118.compat.KubeJsTooltipConcurrencyCompatibility";
    private static final String PREFLIGHT_RESOURCE = PREFLIGHT_CLASS.replace('.', '/') + ".class";
    private static final String KUBEJS_INTERNAL_PREFIX = "dev/latvian/mods/kubejs/";
    private static final String HEALTH_OWNER =
            "com/recipetree/reiexport118/compat/KubeJsTooltipConcurrencyCompatibility";

    @Test
    void optionalPreflightHasNoJvmTypeReferenceToKubeJs() throws Exception {
        ClassNode preflight = new ClassNode();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(PREFLIGHT_RESOURCE)) {
            assertNotNull(input, "missing compiled optional KubeJS preflight class");
            new ClassReader(input).accept(preflight, ClassReader.SKIP_DEBUG);
        }

        rejectHardLink(preflight.superName);
        preflight.interfaces.forEach(KubeJsOptionalLinkageBoundaryTest::rejectHardLink);
        preflight.fields.forEach(field -> {
            rejectHardLink(field.desc);
            rejectHardLink(field.signature);
        });
        preflight.methods.forEach(method -> {
            rejectHardLink(method.desc);
            rejectHardLink(method.signature);
            method.exceptions.forEach(KubeJsOptionalLinkageBoundaryTest::rejectHardLink);
            method.tryCatchBlocks.forEach(block -> rejectHardLink(block.type));
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type) {
                    rejectHardLink(type.desc);
                } else if (instruction instanceof FieldInsnNode field) {
                    rejectHardLink(field.owner);
                    rejectHardLink(field.desc);
                } else if (instruction instanceof MethodInsnNode invocation) {
                    rejectHardLink(invocation.owner);
                    rejectHardLink(invocation.desc);
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    rejectHardLink(dynamic.desc);
                    rejectHardLink(dynamic.bsm.getOwner());
                    rejectHardLink(dynamic.bsm.getDesc());
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Type type) {
                            rejectHardLink(type.getDescriptor());
                        } else if (argument instanceof Handle handle) {
                            rejectHardLink(handle.getOwner());
                            rejectHardLink(handle.getDesc());
                        }
                    }
                } else if (instruction instanceof MultiANewArrayInsnNode array) {
                    rejectHardLink(array.desc);
                } else if (instruction instanceof LdcInsnNode constant
                        && constant.cst instanceof Type type) {
                    rejectHardLink(type.getDescriptor());
                }
            }
        });
    }

    @Test
    void healthGateProtectsBothTheClaimedRequestAndFinalPublicationBoundaries()
            throws Exception {
        ClassNode coordinator = readClass("com/recipetree/reiexport118/ExportCoordinator.class");
        List<MethodInsnNode> coordinatorCalls = callsIn(coordinator, "tick", "()V");
        int claim = callIndex(
                coordinatorCalls,
                "com/recipetree/reiexport118/ExportCoordinator",
                "claim",
                "(Ljava/nio/file/Path;)Lcom/recipetree/reiexport118/ExportCoordinator$Claim;"
        );
        int claimedHealth = callIndex(
                coordinatorCalls, HEALTH_OWNER, "requireHealthyIfApplicable", "()V");
        int planBuild = callIndex(
                coordinatorCalls,
                "com/recipetree/reiexport118/ExportPlan",
                "build",
                "(Lcom/recipetree/reiexport118/ExportRequest;III)Lcom/recipetree/reiexport118/ExportPlan;"
        );
        assertTrue(claim < claimedHealth && claimedHealth < planBuild,
                "the optional health gate must run after atomic claim and before export planning");
        assertEquals(1, matchingCallCount(
                coordinatorCalls, HEALTH_OWNER, "requireHealthyIfApplicable", "()V"));

        ClassNode exportJob = readClass("com/recipetree/reiexport118/ExportJob.class");
        List<MethodInsnNode> finishCalls = callsIn(exportJob, "finish", "()V");
        int finalHealth = callIndex(
                finishCalls, HEALTH_OWNER, "requireHealthyIfApplicable", "()V");
        int finalizeStaging = callIndex(
                finishCalls,
                "com/recipetree/reiexport118/ExportContext",
                "finish",
                "(ZJLcom/recipetree/reiexport118/ExportPlan;)V"
        );
        int publish = callIndex(
                finishCalls,
                "com/recipetree/reiexport118/ExportContext",
                "publish",
                "()V"
        );
        assertTrue(finalHealth < finalizeStaging && finalizeStaging < publish,
                "the optional health gate must run before one-shot staging finalization and publication");
        assertEquals(1, matchingCallCount(
                finishCalls, HEALTH_OWNER, "requireHealthyIfApplicable", "()V"));
    }

    private ClassNode readClass(String resource) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "missing compiled class " + resource);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG);
        }
        return node;
    }

    private static List<MethodInsnNode> callsIn(
            ClassNode owner,
            String methodName,
            String descriptor
    ) {
        var method = owner.methods.stream()
                .filter(candidate -> methodName.equals(candidate.name)
                        && descriptor.equals(candidate.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing method " + owner.name + "." + methodName + descriptor));
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call);
            }
        }
        return calls;
    }

    private static int callIndex(
            List<MethodInsnNode> calls,
            String owner,
            String name,
            String descriptor
    ) {
        for (int index = 0; index < calls.size(); index++) {
            MethodInsnNode call = calls.get(index);
            if (owner.equals(call.owner) && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                return index;
            }
        }
        throw new AssertionError("missing invocation " + owner + "." + name + descriptor);
    }

    private static long matchingCallCount(
            List<MethodInsnNode> calls,
            String owner,
            String name,
            String descriptor
    ) {
        return calls.stream().filter(call -> owner.equals(call.owner)
                && name.equals(call.name) && descriptor.equals(call.desc)).count();
    }

    private static void rejectHardLink(String classOrDescriptor) {
        if (classOrDescriptor != null) {
            assertFalse(
                    classOrDescriptor.contains(KUBEJS_INTERNAL_PREFIX),
                    () -> "optional startup preflight hard-links KubeJS: " + classOrDescriptor
            );
        }
    }
}
