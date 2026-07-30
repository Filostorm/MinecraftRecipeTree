package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the two one-shot settlement seams from degrading into a per-tick registry scan. */
final class Mm2SettledEntryReadinessContractTest {
    private static final String COORDINATOR =
            "com/recipetree/reiexport118/ExportCoordinator";
    private static final String REPAIRS =
            "com/recipetree/reiexport118/compat/Mm2RegistryRepairs";
    private static final String SEAM = REPAIRS + "$SettlementSeam";
    private static final String RESULT = REPAIRS + "$SettlementResult";
    private static final String CANONICALIZE_DESCRIPTOR =
            "(L" + SEAM + ";)L" + RESULT + ";";

    @Test
    void readinessCanonicalizesOnlyBeforeDeepCandidateAndAtomicClaim() throws Exception {
        MethodNode tick = method(readClass(COORDINATOR + ".class"), "tick", "()V");
        List<MethodInsnNode> calls = calls(tick);

        List<Integer> canonicalizations = callIndexes(
                calls, REPAIRS, "canonicalizeSettledEntries", CANONICALIZE_DESCRIPTOR);
        List<Integer> deepCensuses = callIndexes(
                calls,
                "com/recipetree/reiexport118/RegistryCensus",
                "captureDeepWithDiagnostics",
                "()Lcom/recipetree/reiexport118/RegistryCensus$Capture;");
        int claim = onlyCallIndex(
                calls,
                COORDINATOR,
                "claim",
                "(Ljava/nio/file/Path;)Lcom/recipetree/reiexport118/ExportCoordinator$Claim;");

        assertEquals(2, canonicalizations.size(),
                "settlement must remain a two-seam operation, never a per-tick scan");
        assertEquals(2, deepCensuses.size(), "candidate and claimed deep censuses");
        assertTrue(canonicalizations.get(0) < deepCensuses.get(0),
                "candidate settlement must precede the deep candidate census");
        assertTrue(deepCensuses.get(0) < canonicalizations.get(1),
                "pre-claim settlement must follow the completed stability candidate");
        assertTrue(canonicalizations.get(1) < claim && claim < deepCensuses.get(1),
                "pre-claim settlement must run before atomic claim and claimed deep census");

        assertEquals(
                List.of("READINESS_CANDIDATE", "PRE_CLAIM"),
                settlementSeamConstants(tick),
                "the two calls must use explicit, non-stringly-typed readiness seams");
    }

    @Test
    void aPreClaimMutationRestartsAndCanonicalizationFailuresTerminalize() throws Exception {
        ClassNode coordinator = readClass(COORDINATOR + ".class");
        List<MethodInsnNode> tickCalls = calls(method(coordinator, "tick", "()V"));
        List<Integer> changedCalls = callIndexes(
                tickCalls, RESULT, "changed", "()Z");
        int claim = onlyCallIndex(
                tickCalls,
                COORDINATOR,
                "claim",
                "(Ljava/nio/file/Path;)Lcom/recipetree/reiexport118/ExportCoordinator$Claim;");

        assertEquals(2, changedCalls.size(), "candidate log and pre-claim restart decisions");
        assertTrue(changedCalls.get(1) < claim,
                "the pre-claim mutation decision must precede atomic claim");
        assertTrue(hasCallBetween(
                        tickCalls,
                        changedCalls.get(1),
                        claim,
                        COORDINATOR,
                        "resetReadiness",
                        "(Z)V"),
                "the pre-claim changed branch must contain an explicit readiness reset");
        assertEquals(
                2,
                callIndexes(
                        tickCalls,
                        COORDINATOR,
                        "failSettledCanonicalization",
                        "(Ljava/nio/file/Path;L" + SEAM + ";Ljava/lang/Throwable;)V")
                        .size(),
                "both settlement seams must route failures through the terminal path");

        List<MethodInsnNode> failureCalls = calls(method(
                coordinator,
                "failSettledCanonicalization",
                "(Ljava/nio/file/Path;L" + SEAM + ";Ljava/lang/Throwable;)V"));
        assertEquals(
                1,
                callIndexes(
                        failureCalls,
                        COORDINATOR,
                        "failActiveOrUnclaimed",
                        "(Ljava/nio/file/Path;Ljava/lang/Throwable;)V").size(),
                "settlement drift must terminalize the active request without fallback");
    }

    private ClassNode readClass(String resource) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "missing compiled class " + resource);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG);
        }
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

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                result.add(call);
            }
        }
        return result;
    }

    private static List<Integer> callIndexes(
            List<MethodInsnNode> calls,
            String owner,
            String name,
            String descriptor
    ) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < calls.size(); index++) {
            MethodInsnNode call = calls.get(index);
            if (owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                result.add(index);
            }
        }
        return List.copyOf(result);
    }

    private static int onlyCallIndex(
            List<MethodInsnNode> calls,
            String owner,
            String name,
            String descriptor
    ) {
        List<Integer> indexes = callIndexes(calls, owner, name, descriptor);
        assertEquals(1, indexes.size(), "exact invocation cardinality for " + owner + "." + name);
        return indexes.get(0);
    }

    private static boolean hasCallBetween(
            List<MethodInsnNode> calls,
            int exclusiveStart,
            int exclusiveEnd,
            String owner,
            String name,
            String descriptor
    ) {
        for (int index = exclusiveStart + 1; index < exclusiveEnd; index++) {
            MethodInsnNode call = calls.get(index);
            if (owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> settlementSeamConstants(MethodNode method) {
        List<String> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)
                    || !REPAIRS.equals(call.owner)
                    || !"canonicalizeSettledEntries".equals(call.name)
                    || !CANONICALIZE_DESCRIPTOR.equals(call.desc)) {
                continue;
            }
            AbstractInsnNode previous = instruction.getPrevious();
            while (previous != null && previous.getOpcode() < 0) {
                previous = previous.getPrevious();
            }
            assertTrue(previous instanceof FieldInsnNode,
                    "canonicalization must receive a direct enum constant");
            FieldInsnNode field = (FieldInsnNode) previous;
            assertEquals(SEAM, field.owner, "settlement enum owner");
            result.add(field.name);
        }
        return List.copyOf(result);
    }
}
