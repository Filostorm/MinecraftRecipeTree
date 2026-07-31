package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.IndustrialForegoingRecipeListOrderContract;
import org.junit.jupiter.api.Test;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IndustrialForegoingRecipeListOrderMixinContractTest {
    private static final String TARGET =
            "com/buuz135/industrial/plugin/jei/JEICustomPlugin";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/IndustrialForegoingOreTagOrderMixin";
    private static final String RECIPE_UTIL = "com/hrznstudio/titanium/util/RecipeUtil";
    private static final String GET_RECIPES_DESCRIPTOR =
            "(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;";

    @Test
    void pinnedTargetOwnsSixTitaniumReadsInTheExactRecipeTypeOrder() throws IOException {
        ClassNode target = readPinnedTarget();
        MethodNode register = method(target, "registerRecipes",
                "(Lmezz/jei/api/registration/IRecipeRegistration;)V");
        List<String> serializerOwners = new ArrayList<>();
        String pendingSerializerOwner = null;
        int calls = 0;
        for (AbstractInsnNode instruction : register.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && "SERIALIZER".equals(field.name)
                    && field.owner.startsWith("com/buuz135/industrial/recipe/")) {
                pendingSerializerOwner = field.owner;
            }
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESTATIC
                    && RECIPE_UTIL.equals(call.owner)
                    && "getRecipes".equals(call.name)
                    && GET_RECIPES_DESCRIPTOR.equals(call.desc)) {
                calls++;
                assertNotNull(pendingSerializerOwner, "serializer owner before call=" + calls);
                serializerOwners.add(pendingSerializerOwner);
                pendingSerializerOwner = null;
            }
        }
        assertEquals(IndustrialForegoingRecipeListOrderContract.EXPECTED_GET_RECIPES_CALLS,
                calls);
        assertEquals(List.of(
                "com/buuz135/industrial/recipe/FluidExtractorRecipe",
                "com/buuz135/industrial/recipe/DissolutionChamberRecipe",
                "com/buuz135/industrial/recipe/LaserDrillOreRecipe",
                "com/buuz135/industrial/recipe/LaserDrillFluidRecipe",
                "com/buuz135/industrial/recipe/StoneWorkGenerateRecipe",
                "com/buuz135/industrial/recipe/StoneWorkGenerateRecipe"), serializerOwners);
    }

    @Test
    void compiledRedirectCallsTheOriginalExactlyOnceThenCanonicalizes() throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        MethodNode handler = method(mixin,
                "reiexport$canonicalRecipeOrder",
                GET_RECIPES_DESCRIPTOR);
        AnnotationNode redirect = annotation(annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(List.of(IndustrialForegoingRecipeListOrderContract.REGISTER_RECIPES),
                value(redirect, "method"));
        assertEquals(6, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode point = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(point, "value"));
        assertEquals(IndustrialForegoingRecipeListOrderContract.GET_RECIPES_TARGET,
                value(point, "target"));

        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                RECIPE_UTIL, "getRecipes", GET_RECIPES_DESCRIPTOR));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                "com/recipetree/reiexport118/compat/"
                        + "IndustrialForegoingRecipeListOrderCompatibility",
                "canonicalRecipes",
                "(Lnet/minecraft/world/item/crafting/RecipeType;Ljava/util/List;)"
                        + "Ljava/util/List;"));
    }

    private static ClassNode readPinnedTarget() throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                IndustrialForegoingRecipeListOrderMixinContractTest.class
                        .getClassLoader().getResources(
                                IndustrialForegoingRecipeListOrderContract.TARGET_RESOURCE))) {
            try (InputStream input = url.openStream()) {
                byte[] bytecode = input.readAllBytes();
                String hash = sha256(bytecode);
                observed.add(url + " sha256=" + hash);
                if (IndustrialForegoingRecipeListOrderContract.TARGET_CLASS_SHA256.equals(hash)) {
                    exact.add(bytecode);
                }
            }
        }
        assertTrue(!observed.isEmpty(), "missing pinned target resource");
        assertEquals(observed.size(), exact.size(),
                "every visible target copy must match the exact pin; observed=" + observed);
        return read(exact.get(0));
    }

    private static ClassNode readResource(String resource) throws IOException {
        try (InputStream input = IndustrialForegoingRecipeListOrderMixinContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return read(input.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .filter(candidate -> descriptor.equals(candidate.desc))
                .toList();
        assertEquals(1, matches.size(), owner.name + "." + name + descriptor);
        return matches.get(0);
    }

    private static int callCount(
            MethodNode method, int opcode, String owner, String name, String descriptor) {
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

    private static AnnotationNode annotation(List<AnnotationNode> values, String descriptor) {
        for (AnnotationNode annotation : values) {
            if (descriptor.equals(annotation.desc)) return annotation;
        }
        throw new AssertionError("missing annotation " + descriptor);
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int index = 0; annotation.values != null
                && index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
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
