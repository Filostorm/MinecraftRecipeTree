package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeiRecipeRegistrationPigmentMixinContractTest {
    private static final String TARGET =
            "me/shedaniel/rei/jeicompat/wrap/JEIRecipeRegistration";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/JeiRecipeRegistrationPigmentMixin";
    private static final String ADD_RECIPES =
            "addRecipes(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V";
    private static final String ADD_RECIPES_ZERO =
            "addRecipes0(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V";
    private static final String CAN_RECIPES_BE_MULTITHREADED =
            "canRecipesBeMultithreaded(Ljava/util/Collection;"
                    + "Lme/shedaniel/rei/api/common/category/CategoryIdentifier;)Z";
    private static final String ADD_RECIPES_OPTIMIZED =
            "addRecipesOptimized(Ljava/util/List;"
                    + "Lme/shedaniel/rei/api/common/category/CategoryIdentifier;"
                    + "Lme/shedaniel/rei/api/client/registry/display/DisplayRegistry;"
                    + "Ljava/util/function/Function;)V";

    @Test
    void pinnedRegistrationHasOneQueueOneExecutionAndOneSwallowedOptimizedFailure()
            throws IOException {
        ClassNode target = readPinned(Mm2DeterminismContract.JEI_RECIPE_REGISTRATION);
        assertEquals(TARGET, target.name);

        MethodNode queue = method(target, "addRecipes",
                "(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V");
        assertEquals(1, callCount(queue, "java/util/List", "add", "(Ljava/lang/Object;)Z"));

        MethodNode execute = method(target, "addRecipes0",
                "(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V");
        assertEquals(1, callCount(
                execute, TARGET, "addRecipesOptimized",
                "(Ljava/util/List;Lme/shedaniel/rei/api/common/category/CategoryIdentifier;"
                        + "Lme/shedaniel/rei/api/client/registry/display/DisplayRegistry;"
                        + "Ljava/util/function/Function;)V"));
        assertEquals(1, callCount(
                execute,
                "me/shedaniel/rei/api/client/registry/display/DisplayRegistry",
                "add",
                "(Lme/shedaniel/rei/api/common/display/Display;Ljava/lang/Object;)V"));

        MethodNode threshold = method(
                target,
                "canRecipesBeMultithreaded",
                "(Ljava/util/Collection;Lme/shedaniel/rei/api/common/category/"
                        + "CategoryIdentifier;)Z");
        assertEquals(1, intOperandCount(threshold, Opcodes.BIPUSH, 100));

        MethodNode optimized = method(
                target,
                "addRecipesOptimized",
                "(Ljava/util/List;Lme/shedaniel/rei/api/common/category/CategoryIdentifier;"
                        + "Lme/shedaniel/rei/api/client/registry/display/DisplayRegistry;"
                        + "Ljava/util/function/Function;)V");
        assertEquals(1, callCount(
                optimized, "java/lang/Exception", "printStackTrace", "()V"));
        assertEquals(1, callCount(
                optimized,
                "me/shedaniel/rei/api/client/registry/display/DisplayRegistry",
                "add",
                "(Lme/shedaniel/rei/api/common/display/Display;Ljava/lang/Object;)V"));
        assertEquals(
                Set.of(
                        "java/lang/InterruptedException",
                        "java/util/concurrent/ExecutionException",
                        "java/util/concurrent/TimeoutException"),
                optimized.tryCatchBlocks.stream()
                        .map(block -> block.type)
                        .collect(Collectors.toSet()));

        int classWidePrintStackTraceCalls = target.methods.stream()
                .mapToInt(candidate -> callCount(
                        candidate, "java/lang/Exception", "printStackTrace", "()V"))
                .sum();
        assertEquals(1, classWidePrintStackTraceCalls,
                "every swallowed Exception.printStackTrace seam must stay covered");
    }

    @Test
    void compiledMixinForcesOnlyThePinnedPigmentPathSerialAndAuditsEverySeam()
            throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));

        MethodNode canonicalQueue = method(
                mixin,
                "reiexport$canonicalizeQueuedPigmentRecipes",
                "(Ljava/util/Collection;Ljava/util/Collection;"
                        + "Lnet/minecraft/resources/ResourceLocation;)"
                        + "Ljava/util/Collection;");
        AnnotationNode canonicalQueueModifier = annotation(
                annotations(canonicalQueue),
                "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;");
        assertEquals(List.of(ADD_RECIPES), value(canonicalQueueModifier, "method"));
        assertEquals(1, value(canonicalQueueModifier, "require"));
        assertEquals(false, value(canonicalQueueModifier, "remap"));
        assertEquals(true, value(canonicalQueueModifier, "argsOnly"));
        assertEquals(0, value(canonicalQueueModifier, "ordinal"));
        AnnotationNode canonicalQueuePoint =
                (AnnotationNode) value(canonicalQueueModifier, "at");
        assertEquals("HEAD", value(canonicalQueuePoint, "value"));
        assertEquals(1, callCount(
                canonicalQueue,
                "com/recipetree/reiexport118/compat/Mm2PigmentRecipeRegistrationGate",
                "canonicalizeQueued",
                "(Ljava/lang/Object;Ljava/util/Collection;"
                        + "Lnet/minecraft/resources/ResourceLocation;)"
                        + "Ljava/util/Collection;"));
        assertInject(
                methodByName(mixin, "reiexport$beginPigmentRecipeExecution"),
                ADD_RECIPES_ZERO,
                "HEAD",
                "beginExecution",
                "(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V");
        assertInject(
                methodByName(mixin, "reiexport$finishPigmentRecipeExecution"),
                ADD_RECIPES_ZERO,
                "RETURN",
                "finishExecution",
                "(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V");

        MethodNode serial = methodByName(
                mixin, "reiexport$forceSerialPigmentRecipeExecution");
        AnnotationNode serialInject = annotation(
                annotations(serial),
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of(CAN_RECIPES_BE_MULTITHREADED),
                value(serialInject, "method"));
        assertEquals(1, value(serialInject, "require"));
        assertEquals(false, value(serialInject, "remap"));
        assertEquals(true, value(serialInject, "cancellable"));
        List<?> serialPoints = (List<?>) value(serialInject, "at");
        assertEquals(1, serialPoints.size());
        assertEquals("RETURN", value((AnnotationNode) serialPoints.get(0), "value"));
        assertEquals(1, callCount(
                serial,
                "com/recipetree/reiexport118/compat/Mm2PigmentRecipeRegistrationGate",
                "forceSerialExecution",
                "(Ljava/util/Collection;"
                        + "Lme/shedaniel/rei/api/common/category/CategoryIdentifier;Z)Z"));
        assertEquals(1, callCount(
                serial,
                "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
                "getReturnValueZ",
                "()Z"));
        assertEquals(1, callCount(
                serial,
                "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
                "setReturnValue",
                "(Ljava/lang/Object;)V"));
        assertInject(
                methodByName(mixin, "reiexport$rejectOptimizedPigmentRecipes"),
                ADD_RECIPES_OPTIMIZED,
                "HEAD",
                "beginOptimized",
                "(Ljava/util/List;Lme/shedaniel/rei/api/common/category/"
                        + "CategoryIdentifier;)V");
        assertInject(
                methodByName(mixin, "reiexport$finishOptimizedPigmentRecipes"),
                ADD_RECIPES_OPTIMIZED,
                "RETURN",
                "finishOptimized",
                "(Ljava/util/List;Lme/shedaniel/rei/api/common/category/"
                        + "CategoryIdentifier;)V");

        MethodNode redirect = methodByName(
                mixin, "reiexport$rejectSwallowedOptimizedFailure");
        AnnotationNode redirectAnnotation = annotation(
                annotations(redirect),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(List.of(ADD_RECIPES_OPTIMIZED), value(redirectAnnotation, "method"));
        assertEquals(1, value(redirectAnnotation, "require"));
        assertEquals(false, value(redirectAnnotation, "remap"));
        AnnotationNode point = (AnnotationNode) value(redirectAnnotation, "at");
        assertEquals("INVOKE", value(point, "value"));
        assertEquals("Ljava/lang/Exception;printStackTrace()V", value(point, "target"));
        assertEquals(1, callCount(
                redirect, "java/lang/Exception", "printStackTrace", "()V"));
        assertEquals(1, callCount(
                redirect,
                "com/recipetree/reiexport118/compat/Mm2ReiLifecycleGate",
                "rejectSwallowedPluginFailure",
                "(Ljava/lang/String;Ljava/lang/Throwable;)V"));
    }

    @Test
    void mixinConfigurationIncludesExactPigmentGuardOnce() throws IOException {
        String config;
        try (InputStream input = JeiRecipeRegistrationPigmentMixinContractTest.class
                .getClassLoader().getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "compiled mixin configuration");
            config = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"JeiRecipeRegistrationPigmentMixin\""));
    }

    private static void assertInject(
            MethodNode handler,
            String selector,
            String position,
            String gateMethod,
            String gateDescriptor
    ) {
        AnnotationNode inject = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of(selector), value(inject, "method"));
        assertEquals(1, value(inject, "require"));
        assertEquals(false, value(inject, "remap"));
        List<?> points = (List<?>) value(inject, "at");
        assertEquals(1, points.size());
        assertEquals(position, value((AnnotationNode) points.get(0), "value"));
        assertEquals(1, callCount(
                handler,
                "com/recipetree/reiexport118/compat/Mm2PigmentRecipeRegistrationGate",
                gateMethod,
                gateDescriptor));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                JeiRecipeRegistrationPigmentMixinContractTest.class
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
        try (InputStream input = JeiRecipeRegistrationPigmentMixinContractTest.class
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
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int intOperandCount(MethodNode method, int opcode, int operand) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof IntInsnNode integer
                    && integer.getOpcode() == opcode
                    && integer.operand == operand) {
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
