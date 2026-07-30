package com.recipetree.jeiexport112.compat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;

/**
 * Bridges multiblocked-0.8.0's direct GL20 shader-object calls through Minecraft's selected
 * ARB/core shader implementation. The transformer validates the caller bytecode before any call
 * is redirected here.
 */
public final class MultiblockedShaderBridge {
    private static final AtomicBoolean ACTIVATION_LOGGED = new AtomicBoolean();

    private MultiblockedShaderBridge() {
    }

    public static int createShader(int shaderType) {
        logActivation();
        int shaderId = OpenGlHelper.glCreateShader(shaderType);
        if (shaderId == 0) {
            throw failure(
                    "OpenGlHelper.glCreateShader(" + shaderType + ") returned shader id 0"
            );
        }
        return shaderId;
    }

    public static void shaderSource(int shaderId, CharSequence source) {
        logActivation();
        requireShaderId(shaderId, "shaderSource");
        if (source == null) {
            throw failure("shaderSource received a null CharSequence");
        }

        byte[] utf8 = source.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer directSource = BufferUtils.createByteBuffer(utf8.length);
        directSource.put(utf8);
        directSource.flip();
        OpenGlHelper.glShaderSource(shaderId, directSource);
    }

    public static void compileShader(int shaderId) {
        logActivation();
        requireShaderId(shaderId, "compileShader");
        OpenGlHelper.glCompileShader(shaderId);
    }

    public static int getShaderi(int shaderId, int parameter) {
        logActivation();
        requireShaderId(shaderId, "getShaderi");
        return OpenGlHelper.glGetShaderi(shaderId, parameter);
    }

    public static String getShaderInfoLog(int shaderId, int maxLength) {
        logActivation();
        requireShaderId(shaderId, "getShaderInfoLog");
        if (maxLength < 0) {
            throw failure("getShaderInfoLog received negative maxLength " + maxLength);
        }
        return OpenGlHelper.glGetShaderInfoLog(shaderId, maxLength);
    }

    private static void requireShaderId(int shaderId, String operation) {
        if (shaderId == 0) {
            throw failure(operation + " received shader id 0");
        }
    }

    private static void logActivation() {
        if (ACTIVATION_LOGGED.compareAndSet(false, true)) {
            System.out.println(
                    "[jeiexport] Multiblocked shader compatibility bridge active: shader " +
                            "objects use Minecraft OpenGlHelper's selected ARB/core path."
            );
        }
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException(
                "[jeiexport] Multiblocked shader compatibility bridge failed: " + detail +
                        "; refusing to continue with an invalid shader object."
        );
    }
}
