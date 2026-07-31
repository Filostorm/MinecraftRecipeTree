package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2RegistryRepairContractTest {
    private static final Path MM2_INSTANCE = Path.of(
            "..", "export-instances", "multiblock-madness-2");
    private static final Path MM2_MODS = Path.of(
            "..", "export-instances", "multiblock-madness-2", "mods");

    @Test
    void exactIdentifiersUseTheActualPinnedRegistryPaths() {
        assertEquals(
                "projectred_fabrication:fabricated_gate",
                Mm2RegistryRepairs.PROJECT_RED_FABRICATION_FABRICATED_GATE.toString());
        assertEquals(
                "projectred_integration:fabricated_gate",
                Mm2RegistryRepairs.PROJECT_RED_INTEGRATION_FABRICATED_GATE.toString());
        assertEquals(
                "naturesaura:bottle_two_the_rebottling",
                Mm2RegistryRepairs.NATURES_AURA_REBOTTLING.toString());
        assertEquals("thermal:fluid_reservoir", Mm2EntryCanonicalization.FLUID_RESERVOIR_ID);
        assertEquals("thermal:xp_crystal", Mm2EntryCanonicalization.XP_CRYSTAL_ID);
    }

    @Test
    void runtimeRegistryProbeAndKubeJsHideIntentDistinguishBothProjectRedItems()
            throws IOException {
        String runtimeItemIds = Files.readString(
                MM2_INSTANCE.resolve("kubejs/probe/generated/globals.d.ts"));
        assertEquals(
                1,
                occurrences(runtimeItemIds, "projectred_fabrication:fabricated_gate"),
                "the runtime-generated KubeJS Item ID union must contain the Fabrication item");
        assertEquals(
                1,
                occurrences(runtimeItemIds, "projectred_integration:fabricated_gate"),
                "the runtime-generated KubeJS Item ID union must contain the Integration item");

        String hideScript = Files.readString(
                MM2_INSTANCE.resolve("kubejs/server_scripts/global.js"));
        assertEquals(
                1,
                occurrences(hideScript, "'projectred_integration:fabricated_gate'"),
                "MM2's authoritative hide list must target the Integration entry");
        assertEquals(
                0,
                occurrences(hideScript, "'projectred_fabrication:fabricated_gate'"),
                "the distinct Fabrication item is not the KubeJS hide-list target");
    }

    @Test
    void auditedJarsAndClassResourcesStillMatchEveryBinaryPin() throws IOException {
        assertEquals(6, Mm2RegistryRepairContract.MOD_PINS.size());
        assertEquals(15, Mm2RegistryRepairContract.CLASS_PINS.size());

        for (Mm2RegistryRepairContract.ModPin pin : Mm2RegistryRepairContract.MOD_PINS) {
            assertTrue(pin.jarSha256().matches("[0-9a-f]{64}"), pin.modId());
            Path jar = MM2_MODS.resolve(pin.jarFileName());
            assertFalse(Files.isSymbolicLink(jar), jar.toString());
            assertTrue(Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS), jar.toString());
            try (InputStream input = Files.newInputStream(jar)) {
                assertEquals(pin.jarSha256(), sha256(input), pin.jarFileName());
            }

            try (JarFile archive = new JarFile(jar.toFile())) {
                for (Mm2RegistryRepairContract.ClassPin classPin
                        : Mm2RegistryRepairContract.CLASS_PINS) {
                    if (!pin.modId().equals(classPin.modId())) {
                        continue;
                    }
                    assertTrue(classPin.sha256().matches("[0-9a-f]{64}"), classPin.resource());
                    JarEntry entry = archive.getJarEntry(classPin.resource());
                    assertNotNull(entry, classPin.resource());
                    try (InputStream input = archive.getInputStream(entry)) {
                        assertEquals(classPin.sha256(), sha256(input), classPin.resource());
                    }
                }
            }
        }
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }
}
