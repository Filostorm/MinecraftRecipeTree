package com.recipetree.jeiexport112;

import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OffscreenRendererContractTest {
    private static final String GL_STATE_MANAGER =
            "net/minecraft/client/renderer/GlStateManager";
    private static final String OPEN_GL_HELPER =
            "net/minecraft/client/renderer/OpenGlHelper";
    private static final String GL11 = "org/lwjgl/opengl/GL11";

    @Test
    public void integerQueryBufferSatisfiesLwjglTwoBufferChecks() {
        assertEquals(16, OffscreenRenderer.INTEGER_QUERY_BUFFER_CAPACITY);
    }

    @Test
    public void textureBaselineSynchronizesDriverAndCacheBeforeNativeRendererBind()
            throws IOException {
        ClassNode classNode = new ClassNode();
        try (InputStream input = OffscreenRenderer.class.getResourceAsStream(
                "OffscreenRenderer.class")) {
            assertNotNull("Missing compiled OffscreenRenderer.class", input);
            new ClassReader(input).accept(classNode, 0);
        }

        MethodNode baseline = null;
        for (MethodNode method : classNode.methods) {
            if ("establishGuiBaseline".equals(method.name) && "()V".equals(method.desc)) {
                baseline = method;
                break;
            }
        }
        assertNotNull("Missing establishGuiBaseline()V", baseline);

        MethodInsnNode[] prefix = firstMethodCalls(baseline, 9);
        assertCall(prefix[0], GL_STATE_MANAGER, "setActiveTexture", "(I)V");
        assertCall(prefix[1], OPEN_GL_HELPER, "setActiveTexture", "(I)V");
        assertCall(prefix[2], GL_STATE_MANAGER, "disableTexture2D", "()V");
        assertCall(prefix[3], GL11, "glDisable", "(I)V");
        assertCall(prefix[4], GL_STATE_MANAGER, "setActiveTexture", "(I)V");
        assertCall(prefix[5], GL_STATE_MANAGER, "enableTexture2D", "()V");
        assertCall(prefix[6], GL11, "glEnable", "(I)V");
        assertCall(prefix[7], GL_STATE_MANAGER, "bindTexture", "(I)V");
        assertCall(prefix[8], GL11, "glBindTexture", "(II)V");

        assertStaticIntField(previousExecutable(prefix[0]), OPEN_GL_HELPER,
                "lightmapTexUnit");
        assertStaticIntField(previousExecutable(prefix[1]), OPEN_GL_HELPER,
                "lightmapTexUnit");
        assertEquals(3553, intConstant(previousExecutable(prefix[3])));
        assertStaticIntField(previousExecutable(prefix[4]), OPEN_GL_HELPER,
                "defaultTexUnit");
        assertEquals(3553, intConstant(previousExecutable(prefix[6])));
        assertEquals(0, intConstant(previousExecutable(prefix[7])));
        assertEquals(0, intConstant(previousExecutable(prefix[8])));
        assertEquals(3553, intConstant(previousExecutable(
                previousExecutable(prefix[8]))));
    }

    private static MethodInsnNode[] firstMethodCalls(MethodNode method, int count) {
        MethodInsnNode[] calls = new MethodInsnNode[count];
        int index = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null && index < count;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                calls[index++] = (MethodInsnNode) instruction;
            }
        }
        assertEquals("Unexpected method-call count in texture baseline prefix", count, index);
        return calls;
    }

    private static void assertCall(MethodInsnNode call, String owner, String name,
                                   String descriptor) {
        assertNotNull(call);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(owner, call.owner);
        assertEquals(name, call.name);
        assertEquals(descriptor, call.desc);
        assertEquals(false, call.itf);
    }

    private static void assertStaticIntField(AbstractInsnNode instruction, String owner,
                                             String name) {
        assertTrue(instruction instanceof FieldInsnNode);
        FieldInsnNode field = (FieldInsnNode) instruction;
        assertEquals(Opcodes.GETSTATIC, field.getOpcode());
        assertEquals(owner, field.owner);
        assertEquals(name, field.name);
        assertEquals("I", field.desc);
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        assertNotNull(previous);
        return previous;
    }

    private static int intConstant(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
            return opcode - Opcodes.ICONST_0;
        }
        assertTrue("Expected integer constant opcode, got " + opcode,
                instruction instanceof IntInsnNode);
        return ((IntInsnNode) instruction).operand;
    }
}
