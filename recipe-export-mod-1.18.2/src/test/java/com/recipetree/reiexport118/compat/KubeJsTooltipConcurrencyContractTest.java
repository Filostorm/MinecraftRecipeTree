package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class KubeJsTooltipConcurrencyContractTest {
    private static final String TARGET_INTERNAL =
            "dev/latvian/mods/kubejs/client/KubeJSClientEventHandler";
    private static final String EVENT_INTERNAL =
            "dev/latvian/mods/kubejs/item/ItemTooltipEventJS";

    @Test
    void appliesToExactMinecraftAndKubeJsAcrossSupportedForgeAndReiContexts() {
        assertTrue(KubeJsTooltipConcurrencyContract.isApplicable(
                "1.18.2", "1802.5.5-build.569"));
        assertFalse(KubeJsTooltipConcurrencyContract.isApplicable(
                "1.18.1", "1802.5.5-build.569"));
        assertFalse(KubeJsTooltipConcurrencyContract.isApplicable(
                "1.18.2", "1802.5.5-build.570"));
        assertFalse(KubeJsTooltipConcurrencyContract.isApplicable(
                "1.18.2", null));
    }

    @Test
    void pinsJarTargetClassAndExactMixinSelector() {
        assertTrue(KubeJsTooltipConcurrencyContract.KUBEJS_JAR_SHA256
                .matches("[0-9a-f]{64}"));
        assertTrue(KubeJsTooltipConcurrencyContract.TARGET_SHA256
                .matches("[0-9a-f]{64}"));
        assertTrue(KubeJsTooltipConcurrencyContract.RELOAD_TARGET_SHA256
                .matches("[0-9a-f]{64}"));
        assertEquals(
                "itemTooltip(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;"
                        + "Lnet/minecraft/world/item/TooltipFlag;)V",
                KubeJsTooltipConcurrencyContract.TARGET_METHOD_SELECTOR
        );
        assertEquals(
                "reloadClientScripts()V",
                KubeJsTooltipConcurrencyContract.RELOAD_METHOD_SELECTOR
        );
    }

    @Test
    void targetContainsTheExactUnsynchronizedLazyInitializationSeam() throws Exception {
        byte[] bytecode;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                KubeJsTooltipConcurrencyContract.TARGET_RESOURCE)) {
            assertNotNull(input, "missing pinned KubeJS target class");
            bytecode = input.readAllBytes();
        }
        assertEquals(
                KubeJsTooltipConcurrencyContract.TARGET_SHA256,
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(bytecode)),
                "KubeJS target bytecode pin drift"
        );

        ClassNode target = new ClassNode();
        new ClassReader(bytecode).accept(
                target,
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
        );
        FieldNode field = target.fields.stream()
                .filter(candidate -> KubeJsTooltipConcurrencyContract.TARGET_FIELD
                        .equals(candidate.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing staticItemTooltips field"));
        assertEquals("Ljava/util/Map;", field.desc);
        assertTrue((field.access & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((field.access & Opcodes.ACC_STATIC) != 0);
        assertFalse((field.access & Opcodes.ACC_VOLATILE) != 0);
        assertFalse((field.access & Opcodes.ACC_FINAL) != 0);

        MethodNode method = target.methods.stream()
                .filter(candidate -> KubeJsTooltipConcurrencyContract.TARGET_METHOD
                        .equals(candidate.name)
                        && KubeJsTooltipConcurrencyContract.TARGET_METHOD_DESCRIPTOR
                        .equals(candidate.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing itemTooltip method seam"));
        assertTrue((method.access & Opcodes.ACC_PRIVATE) != 0);
        assertFalse((method.access & Opcodes.ACC_STATIC) != 0);
        assertFalse((method.access & Opcodes.ACC_SYNCHRONIZED) != 0);

        FieldInsnNode nullCheck = fieldInstruction(
                method, Opcodes.GETSTATIC, TARGET_INTERNAL,
                KubeJsTooltipConcurrencyContract.TARGET_FIELD, "Ljava/util/Map;");
        AbstractInsnNode branch = nextOpcode(nullCheck);
        assertTrue(branch instanceof JumpInsnNode);
        assertEquals(Opcodes.IFNONNULL, branch.getOpcode());

        TypeInsnNode allocation = typeInstructionAfter(
                method, branch, Opcodes.NEW, "java/util/HashMap");
        FieldInsnNode partialPublication = fieldInstructionAfter(
                method, allocation, Opcodes.PUTSTATIC, TARGET_INTERNAL,
                KubeJsTooltipConcurrencyContract.TARGET_FIELD, "Ljava/util/Map;");
        TypeInsnNode eventAllocation = typeInstructionAfter(
                method, partialPublication, Opcodes.NEW, EVENT_INTERNAL);
        MethodInsnNode eventConstructor = invocationAfter(
                method,
                eventAllocation,
                Opcodes.INVOKESPECIAL,
                EVENT_INTERNAL,
                "<init>",
                KubeJsTooltipConcurrencyContract.TOOLTIP_EVENT_CONSTRUCTOR_DESCRIPTOR
        );
        MethodInsnNode post = invocationAfter(
                method,
                eventConstructor,
                Opcodes.INVOKEVIRTUAL,
                EVENT_INTERNAL,
                "post",
                "(Ldev/latvian/mods/kubejs/script/ScriptType;Ljava/lang/String;)Z"
        );
        assertTrue(method.instructions.indexOf(partialPublication)
                        < method.instructions.indexOf(post),
                "pinned KubeJS must publish the mutable map before posting item.tooltip");
        assertFalse(hasOpcode(method, Opcodes.MONITORENTER));
        assertFalse(hasOpcode(method, Opcodes.MONITOREXIT));
        assertEquals(Type.VOID_TYPE, Type.getReturnType(method.desc));
    }

    @Test
    void handlerInitTooltipRegistrationIsExactlyAuditedOrdinalThree() throws Exception {
        ClassNode target = readPinnedClass(
                KubeJsTooltipConcurrencyContract.TARGET_RESOURCE,
                KubeJsTooltipConcurrencyContract.TARGET_SHA256
        );
        MethodNode init = target.methods.stream()
                .filter(candidate -> "init".equals(candidate.name) && "()V".equals(candidate.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing KubeJS handler init()V"));
        List<MethodInsnNode> registrations = new ArrayList<>();
        for (AbstractInsnNode instruction : init.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == Opcodes.INVOKEINTERFACE
                    && "dev/architectury/event/Event".equals(invocation.owner)
                    && "register".equals(invocation.name)
                    && "(Ljava/lang/Object;)V".equals(invocation.desc)) {
                registrations.add(invocation);
            }
        }
        assertEquals(12, registrations.size(), "KubeJS init Event.register call count drift");

        MethodInsnNode tooltipRegistration = registrations.get(
                KubeJsTooltipConcurrencyContract.TOOLTIP_REGISTER_ORDINAL);
        AbstractInsnNode listenerFactoryNode = previousOpcode(tooltipRegistration);
        assertTrue(listenerFactoryNode instanceof InvokeDynamicInsnNode);
        InvokeDynamicInsnNode listenerFactory = (InvokeDynamicInsnNode) listenerFactoryNode;
        assertEquals(
                "(Ldev/latvian/mods/kubejs/client/KubeJSClientEventHandler;)"
                        + "Ldev/architectury/event/events/client/ClientTooltipEvent$Item;",
                listenerFactory.desc
        );
        AbstractInsnNode handlerLoad = previousOpcode(listenerFactory);
        assertEquals(Opcodes.ALOAD, handlerLoad.getOpcode());
        AbstractInsnNode eventNode = previousOpcode(handlerLoad);
        assertTrue(eventNode instanceof FieldInsnNode);
        FieldInsnNode eventField = (FieldInsnNode) eventNode;
        assertEquals(Opcodes.GETSTATIC, eventField.getOpcode());
        assertEquals("dev/architectury/event/events/client/ClientTooltipEvent", eventField.owner);
        assertEquals("ITEM", eventField.name);
        assertEquals("Ldev/architectury/event/Event;", eventField.desc);
    }

    @Test
    void reloadTargetContainsTheExactResetUnloadLoadBody() throws Exception {
        ClassNode target = readPinnedClass(
                KubeJsTooltipConcurrencyContract.RELOAD_TARGET_RESOURCE,
                KubeJsTooltipConcurrencyContract.RELOAD_TARGET_SHA256
        );
        MethodNode reload = target.methods.stream()
                .filter(candidate -> KubeJsTooltipConcurrencyContract.RELOAD_METHOD
                        .equals(candidate.name)
                        && KubeJsTooltipConcurrencyContract.RELOAD_METHOD_DESCRIPTOR
                        .equals(candidate.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing reloadClientScripts()V"));
        assertTrue((reload.access & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((reload.access & Opcodes.ACC_STATIC) != 0);
        assertFalse((reload.access & Opcodes.ACC_SYNCHRONIZED) != 0);

        List<AbstractInsnNode> opcodes = new ArrayList<>();
        for (AbstractInsnNode instruction : reload.instructions) {
            if (instruction.getOpcode() >= 0) {
                opcodes.add(instruction);
            }
        }
        assertEquals(9, opcodes.size(), "reloadClientScripts instruction count drift");
        assertEquals(Opcodes.ACONST_NULL, opcodes.get(0).getOpcode());
        assertField(opcodes.get(1), Opcodes.PUTSTATIC,
                TARGET_INTERNAL, KubeJsTooltipConcurrencyContract.TARGET_FIELD,
                "Ljava/util/Map;");
        assertField(opcodes.get(2), Opcodes.GETSTATIC,
                "dev/latvian/mods/kubejs/KubeJS", "clientScriptManager",
                "Ldev/latvian/mods/kubejs/script/ScriptManager;");
        assertInvocation(opcodes.get(3), "unload");
        assertField(opcodes.get(4), Opcodes.GETSTATIC,
                "dev/latvian/mods/kubejs/KubeJS", "clientScriptManager",
                "Ldev/latvian/mods/kubejs/script/ScriptManager;");
        assertInvocation(opcodes.get(5), "loadFromDirectory");
        assertField(opcodes.get(6), Opcodes.GETSTATIC,
                "dev/latvian/mods/kubejs/KubeJS", "clientScriptManager",
                "Ldev/latvian/mods/kubejs/script/ScriptManager;");
        assertInvocation(opcodes.get(7), "load");
        assertEquals(Opcodes.RETURN, opcodes.get(8).getOpcode());
    }

    private ClassNode readPinnedClass(String resource, String expectedSha256) throws Exception {
        byte[] bytecode;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "missing pinned KubeJS class " + resource);
            bytecode = input.readAllBytes();
        }
        assertEquals(
                expectedSha256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytecode)),
                "KubeJS bytecode pin drift for " + resource
        );
        ClassNode target = new ClassNode();
        new ClassReader(bytecode).accept(
                target,
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
        );
        return target;
    }

    private static void assertField(
            AbstractInsnNode instruction,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        assertTrue(instruction instanceof FieldInsnNode);
        FieldInsnNode field = (FieldInsnNode) instruction;
        assertEquals(opcode, field.getOpcode());
        assertEquals(owner, field.owner);
        assertEquals(name, field.name);
        assertEquals(descriptor, field.desc);
    }

    private static void assertInvocation(AbstractInsnNode instruction, String name) {
        assertTrue(instruction instanceof MethodInsnNode);
        MethodInsnNode invocation = (MethodInsnNode) instruction;
        assertEquals(Opcodes.INVOKEVIRTUAL, invocation.getOpcode());
        assertEquals("dev/latvian/mods/kubejs/script/ScriptManager", invocation.owner);
        assertEquals(name, invocation.name);
        assertEquals("()V", invocation.desc);
    }

    private static FieldInsnNode fieldInstruction(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        return fieldInstructionAfter(method, method.instructions.getFirst(), opcode,
                owner, name, descriptor);
    }

    private static FieldInsnNode fieldInstructionAfter(
            MethodNode method,
            AbstractInsnNode start,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        for (AbstractInsnNode instruction = start; instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == opcode
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
                return field;
            }
        }
        throw new AssertionError("missing field instruction " + owner + "." + name
                + " in " + method.name + method.desc);
    }

    private static TypeInsnNode typeInstructionAfter(
            MethodNode method,
            AbstractInsnNode start,
            int opcode,
            String descriptor
    ) {
        for (AbstractInsnNode instruction = start; instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == opcode
                    && descriptor.equals(type.desc)) {
                return type;
            }
        }
        throw new AssertionError("missing type instruction " + descriptor
                + " in " + method.name + method.desc);
    }

    private static MethodInsnNode invocationAfter(
            MethodNode method,
            AbstractInsnNode start,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        for (AbstractInsnNode instruction = start; instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == opcode
                    && owner.equals(invocation.owner)
                    && name.equals(invocation.name)
                    && descriptor.equals(invocation.desc)) {
                return invocation;
            }
        }
        throw new AssertionError("missing invocation " + owner + "." + name
                + descriptor + " in " + method.name + method.desc);
    }

    private static boolean hasOpcode(MethodNode method, int opcode) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) {
                return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        assertNotNull(cursor, "missing following opcode");
        return cursor;
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        assertNotNull(cursor, "missing preceding opcode");
        return cursor;
    }
}
