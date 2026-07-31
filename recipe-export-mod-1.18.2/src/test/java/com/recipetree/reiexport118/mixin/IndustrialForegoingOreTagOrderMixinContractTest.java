package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.IndustrialForegoingOreTagOrderContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
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

final class IndustrialForegoingOreTagOrderMixinContractTest {
    private static final String TARGET =
            "com/buuz135/industrial/plugin/jei/JEICustomPlugin";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/IndustrialForegoingOreTagOrderMixin";
    private static final String REGISTER_RECIPES_DESCRIPTOR =
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V";
    private static final String GET_TAG_NAMES_DESCRIPTOR = "()Ljava/util/stream/Stream;";
    private static final String TAG_MANAGER =
            "net/minecraftforge/registries/tags/ITagManager";

    @Test
    void pinnedTargetOwnsOneUnsortedOreRecipeTagStreamAndThreeAlignedAppends()
            throws IOException {
        ClassNode target = readPinnedTarget();
        assertEquals(TARGET, target.name);
        MethodNode register = method(target, "registerRecipes", REGISTER_RECIPES_DESCRIPTOR);
        assertEquals(1, callCount(
                register,
                Opcodes.INVOKEINTERFACE,
                TAG_MANAGER,
                "getTagNames",
                GET_TAG_NAMES_DESCRIPTOR));

        List<String> downstreamStreamCalls = streamCallsAfterGetTagNames(register);
        assertEquals(List.of(
                "map(Ljava/util/function/Function;)Ljava/util/stream/Stream;",
                "filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;",
                "forEach(Ljava/util/function/Consumer;)V"), downstreamStreamCalls);

        MethodNode filter = method(
                target,
                "lambda$registerRecipes$7",
                "(Lnet/minecraft/resources/ResourceLocation;)Z");
        assertEquals(1, callCount(
                filter,
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "startsWith",
                "(Ljava/lang/String;)Z"));
        assertEquals(1, callCount(
                filter,
                Opcodes.INVOKESTATIC,
                "com/buuz135/industrial/fluid/OreTitaniumFluidAttributes",
                "isValid",
                "(Lnet/minecraft/resources/ResourceLocation;)Z"));

        MethodNode append = method(
                target,
                "lambda$registerRecipes$8",
                "(Ljava/util/List;Ljava/util/List;Ljava/util/List;"
                        + "Lnet/minecraft/resources/ResourceLocation;)V");
        assertEquals(1, newCount(
                append, "com/buuz135/industrial/api/recipe/ore/OreFluidEntryRaw"));
        assertEquals(1, newCount(
                append, "com/buuz135/industrial/api/recipe/ore/OreFluidEntryFermenter"));
        assertEquals(1, newCount(
                append, "com/buuz135/industrial/api/recipe/ore/OreFluidEntrySieve"));
        assertEquals(3, callCount(
                append,
                Opcodes.INVOKEINTERFACE,
                "java/util/List",
                "add",
                "(Ljava/lang/Object;)Z"));
        assertEquals(1, callCount(
                append,
                Opcodes.INVOKESTATIC,
                "com/hrznstudio/titanium/util/TagUtil",
                "getItemWithPreference",
                "(Lnet/minecraft/tags/TagKey;)Lnet/minecraft/world/item/ItemStack;"));
    }

    @Test
    void compiledMixinRedirectsOnlyThePinnedGetTagNamesInvocation() throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode pseudo = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Pseudo;");
        assertNotNull(pseudo);
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));

        MethodNode handler = method(
                mixin,
                "reiexport$canonicalOreRecipeTagOrder",
                "(Lnet/minecraftforge/registries/tags/ITagManager;)"
                        + "Ljava/util/stream/Stream;");
        AnnotationNode redirect = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(
                List.of("registerRecipes" + REGISTER_RECIPES_DESCRIPTOR),
                value(redirect, "method"));
        assertEquals(1, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode point = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(point, "value"));
        assertEquals(
                "L" + TAG_MANAGER + ";getTagNames" + GET_TAG_NAMES_DESCRIPTOR,
                value(point, "target"));

        assertEquals(1, callCount(
                handler,
                Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/"
                        + "IndustrialForegoingOreTagOrderCompatibility",
                "canonicalTagNames",
                "(Lnet/minecraftforge/registries/tags/ITagManager;)"
                        + "Ljava/util/stream/Stream;"));

        ClassNode compatibility = readResource(
                "com/recipetree/reiexport118/compat/"
                        + "IndustrialForegoingOreTagOrderCompatibility.class");
        MethodNode canonicalize = method(
                compatibility,
                "canonicalTagNames",
                "(Lnet/minecraftforge/registries/tags/ITagManager;)"
                        + "Ljava/util/stream/Stream;");
        assertEquals(1, callCount(
                canonicalize,
                Opcodes.INVOKEINTERFACE,
                TAG_MANAGER,
                "getTagNames",
                GET_TAG_NAMES_DESCRIPTOR));
    }

    private static List<String> streamCallsAfterGetTagNames(MethodNode method) {
        List<String> calls = new ArrayList<>();
        boolean afterSeam = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) {
                continue;
            }
            if (!afterSeam
                    && TAG_MANAGER.equals(call.owner)
                    && "getTagNames".equals(call.name)
                    && GET_TAG_NAMES_DESCRIPTOR.equals(call.desc)) {
                afterSeam = true;
                continue;
            }
            if (afterSeam && "java/util/stream/Stream".equals(call.owner)) {
                calls.add(call.name + call.desc);
                if ("forEach".equals(call.name)) {
                    return calls;
                }
            }
        }
        throw new AssertionError("getTagNames stream did not terminate in Stream.forEach");
    }

    private static ClassNode readPinnedTarget() throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                IndustrialForegoingOreTagOrderMixinContractTest.class
                        .getClassLoader()
                        .getResources(IndustrialForegoingOreTagOrderContract.TARGET_RESOURCE))) {
            try (InputStream input = url.openStream()) {
                byte[] bytecode = input.readAllBytes();
                String hash = sha256(bytecode);
                observed.add(url + " sha256=" + hash);
                if (IndustrialForegoingOreTagOrderContract.TARGET_CLASS_SHA256.equals(hash)) {
                    exact.add(bytecode);
                }
            }
        }
        assertTrue(!observed.isEmpty(),
                "missing pinned resource "
                        + IndustrialForegoingOreTagOrderContract.TARGET_RESOURCE);
        assertEquals(observed.size(), exact.size(),
                "every visible target copy must match the exact pin; observed=" + observed);
        return read(exact.get(0));
    }

    private static ClassNode readResource(String resource) throws IOException {
        try (InputStream input = IndustrialForegoingOreTagOrderMixinContractTest.class
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

    private static int newCount(MethodNode method, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.NEW
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
