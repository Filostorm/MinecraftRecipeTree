package com.recipetree.neiexport1710;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.InjectedModContainer;
import cpw.mods.fml.common.ModMetadata;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PinnedRuntimePolicyTest {
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void pinsEveryNewCompositePolicyArtifactExactly() throws Exception {
        Map<String, Pin> pins = pinsByModId();
        assertEquals(43, pins.size());

        assertPin(pins, "ae2fc", "1.4.120-gtnh",
                "eb28d4cd24934a1d69d4fc34d1e8022d968184d57f27a6706e54c1ba53a53b9a");
        assertPin(pins, "AWWayofTime", "1.7.52",
                "f1b23b49034bbf1f4ef942b56818119a20035e9da933134c16c4b2f4e14fd855");
        assertPin(pins, "ArchitectureCraft", "1.11.6",
                "cd9747b7b3055a2e4e8b0d807894b29311fadb1d7ea6c7ba03f4b9b09b12fe9f");
        assertPin(pins, "Avaritia", "1.77",
                "ef4ad2efcbbe88dfa2673921781988781f106f52a0be4e1d80edcfeb0609f656");
        assertPin(pins, "AdvancedSolarPanel", "1.7.10-3.5.1",
                "a8c76322d88ff67cdd374e390f03281ff29b38aef7ffa5ac82e323c9524436eb");
        assertEquals(307727L, pins.get("AdvancedSolarPanel").expectedBytes);
        assertPin(pins, "littletiles", "1.5.14-GTNH",
                "74eccef8e2fdc74e059037868347a0a3697b84ef8d75ced1c86d58df5ad9afca");
        assertEquals(585187L, pins.get("littletiles").expectedBytes);
        assertPin(pins, "malisisdoors", "1.18.2-GTNH",
                "4293712bda3c1c73bd629354a06f52c00c884a664d56bbba4a52ed89a01d29c4");
        assertEquals(2218297L, pins.get("malisisdoors").expectedBytes);
        assertPin(pins, "modernmarkings", "0.3.12-1.7.10",
                "bcd5fd1740b16c373072b2a7e971d9f30971c5c11c14fd77c6f8fa61c04b080a");
        assertEquals(101067L, pins.get("modernmarkings").expectedBytes);
        assertPin(pins, "Botania", "1.12.28-GTNH",
                "6b56a5e96a4cba8e639da98a1a0fcc56a8b90600c6a0fbdf401c3f1c212d4712");
        assertPin(pins, "ProjectBlue", "1.2.1-GTNH",
                "9c438068f97bb42e1d86f5671b206931216b1296b5ce4cf0066f5a09fcdd7160");
        assertPin(pins, "ProjRed|Core", "4.12.6-GTNH",
                "124cea32199608cf5490cb2a04d9e054e1d88449934e2248c24940d7b0423ebe");
        assertPin(pins, "gendustry", "1.9.4-GTNH",
                "6690a0768748c3d6d477ef6d9db062e59e57dd52b7a1f7c3a6137b8f6f3ab76c");
        assertPin(pins, "ForgeMultipart", "1.7.2",
                "6c6b862219e4e5fc5a6de996a6d4f47cab062477619911b7e6b06af3e4e85eb6");
        assertPin(pins, "LogisticsPipes", "1.4.24-GTNH",
                "ade88fd46bd851ead0ce55b988b900dcbde262aa11f549e0bd06cee9a0f601c4");
        assertEquals(2917350L, pins.get("LogisticsPipes").expectedBytes);
        assertPin(pins, "NEIAddons|Forestry", "1.17.0",
                "bfac8b5ff2c2b67a3a0d6e6eea791279f97c7de65fc9488e77f974e7bf75967a");
        assertEquals(145396L, pins.get("NEIAddons|Forestry").expectedBytes);
        assertPin(pins, "Forestry", "4.10.17",
                "b537738e29c242726ce7f356985bfd21214f7c8948ea0526e951dbe2f419a3fd");
        assertEquals(3394143L, pins.get("Forestry").expectedBytes);
        assertPin(pins, "ae2wct", "1.12.7",
                "39f823036dce7030bd4e68762a1e2a0469852255e3d40e0cc6e1f3bc264b42ae");
        assertEquals(290829L, pins.get("ae2wct").expectedBytes);
        assertPin(pins, "Railcraft", "9.16.33",
                "6bd57ff9f788310971ee0acb08128004494bffd292563514a3b1244d18141306");
        assertEquals(3435323L, pins.get("Railcraft").expectedBytes);
        assertPin(pins, "gregtech_nh", "5.09.51.482",
                "4ab7ce174a8f6fb7a90d8d11d56056aab2de577c36c6084b37ce890d7b1d67bf");
        assertEquals(55427943L, pins.get("gregtech_nh").expectedBytes);
        assertPin(pins, "CodeChickenCore", "1.4.10",
                "eb55946d997ba92e4533ef2b88e6f633fd5aee862bcbcaa3a030ea53aeb20398");
        Pin codeChickenCore = pins.get("CodeChickenCore");
        assertEquals("codechicken.core.asm.CodeChickenCoreModContainer",
                codeChickenCore.expectedWrappedContainerClass);
        assertEquals("mods/CodeChickenCore-1.4.10.jar",
                codeChickenCore.instanceRelativeArtifact);
        assertPin(pins, "CarpentersBlocks", "3.7.0-GTNH",
                "61d7adbb94e57063f9e1279d557bd1986d0a32e6e4f9787060a0841d76afe387");
        assertPin(pins, "StevesCarts", "2.3.12",
                "cb561db0bed4a106b483a739718f148aee26ec28724d6f831042c159a3f17af6");
        assertPin(pins, "TConstruct", "1.13.57-GTNH",
                "f2cb53b94f135b7523bcb7afea5ef53b9afb40c0bc034fc5ec5018ad0502bc61");
        assertPin(pins, "Thaumcraft", "4.2.3.5",
                "587b5a084643e617e9d87319ad34b341ebb920e09d060e5a5fbedecb96553f89");
        assertPin(pins, "gadomancy", "1.4.8",
                "90fb7e230a18a0eac65645aedc5c205acbc53844d95154850f90a6161beeaa26");
        assertPin(pins, "ThaumicHorizons", "1.7.9",
                "996f9c1d3679cd56f9b82caba7853a004b4cf1e82c449121802552a7b55ee412");
        assertPin(pins, "TwilightForest", "2.7.13",
                "3f5c14c79c74824e354cc9817d03d2c603dca2a993db2a612b12c16fc381582d");
        assertPin(pins, "WitchingGadgets", "1.7.25-GTNH",
                "7c5f6af01aabe2c22814a5c0a6241eaafe226e2b29a0c425e0b937d09cca5e26");
        assertPin(pins, "WR-CBE|Addons", "1.7.1",
                "56bf1c9672840e42049ec796d7c119b1b532317d2074207acae4d6884f75f394");
        assertPin(pins, "neicustomdiagram", "1.7.5",
                "0d5764a3695d45f1043aa59f95ae4d8762ced0b48c9c1e652c20fa4c8cc347c8");
        assertPin(pins, "GalaxySpace", "1.1.121-GTNH",
                "bb0ccf7f54cd73cee83ea8a8ce9e63e1d20ba754a5fb4ebb342d1e81498b7ca8");
        assertPin(pins, "GalacticraftAmunRa", "0.8.2",
                "bcd0ca636d545a0f361f6583fd667bd40ca4b79198fc06d1552038310a7b1978");
    }

    @Test
    public void pinTableHasUniqueModIdsAndCanonicalDigests() throws Exception {
        Object[] rawPins = rawPins();
        Map<String, Pin> pins = pinsByModId();
        assertEquals(rawPins.length, pins.size());
        for (Pin pin : pins.values()) {
            assertTrue(pin.sha256.matches("[0-9a-f]{64}"));
        }
    }

    @Test
    public void explicitInjectedCoremodSourceRequiresTheLoadedCodeSourceExactly()
            throws Exception {
        TestCoremodContainer wrapped = new TestCoremodContainer();
        InjectedModContainer injected = new InjectedModContainer(wrapped, null);
        Path loaded = Paths.get(TestCoremodContainer.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        Path gameDirectory = loaded.getParent();

        assertEquals(loaded, PinnedRuntimePolicy.resolveExplicitInjectedCoremodSource(
                injected, "test-coremod", "1.0", TestCoremodContainer.class.getName(),
                loaded.getFileName().toString(), gameDirectory));

        ExportFailure wrongOuter = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.resolveExplicitInjectedCoremodSource(
                        wrapped, "test-coremod", "1.0",
                        TestCoremodContainer.class.getName(),
                        loaded.getFileName().toString(), gameDirectory));
        assertTrue(wrongOuter.getMessage().contains("exact outer container"));

        ExportFailure wrongWrapped = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.resolveExplicitInjectedCoremodSource(
                        injected, "test-coremod", "1.0", "wrong.Container",
                        loaded.getFileName().toString(), gameDirectory));
        assertTrue(wrongWrapped.getMessage().contains("wrapped container mismatch"));

        InjectedModContainer wrongSentinel = new InjectedModContainer(wrapped,
                new File("other.jar"));
        ExportFailure sentinelFailure = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.resolveExplicitInjectedCoremodSource(
                        wrongSentinel, "test-coremod", "1.0",
                        TestCoremodContainer.class.getName(),
                        loaded.getFileName().toString(), gameDirectory));
        assertTrue(sentinelFailure.getMessage().contains("sentinel mismatch"));

        ExportFailure wrongPath = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.resolveExplicitInjectedCoremodSource(
                        injected, "test-coremod", "1.0",
                        TestCoremodContainer.class.getName(), "not-the-code-source.jar",
                        gameDirectory));
        assertTrue(wrongPath.getMessage().contains("code source mismatch"));

        ExportFailure escape = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.resolveExplicitInjectedCoremodSource(
                        injected, "test-coremod", "1.0",
                        TestCoremodContainer.class.getName(), "../escaped.jar",
                        gameDirectory));
        assertTrue(escape.getMessage().contains("escapes the game directory"));
    }

    @Test
    public void regularDigestGateRejectsDigestDriftAndSymlinks() throws Exception {
        Path root = temporary.newFolder("runtime-pin").toPath();
        Path artifact = root.resolve("artifact.jar");
        Files.write(artifact, "abc".getBytes(StandardCharsets.UTF_8));
        PinnedRuntimePolicy.verifyRegularDigest(artifact, ABC_SHA256, "test artifact");
        PinnedRuntimePolicy.verifyRegularSize(artifact, 3L, "test artifact");

        ExportFailure digestDrift = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.verifyRegularDigest(artifact,
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        "test artifact"));
        assertTrue(digestDrift.getMessage().contains("SHA-256 mismatch"));

        ExportFailure sizeDrift = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.verifyRegularSize(
                        artifact, 4L, "test artifact"));
        assertTrue(sizeDrift.getMessage().contains("byte-size mismatch"));

        Path link = root.resolve("artifact-link.jar");
        Files.createSymbolicLink(link, artifact.getFileName());
        ExportFailure symlink = assertThrows(ExportFailure.class,
                () -> PinnedRuntimePolicy.verifyRegularDigest(link, ABC_SHA256,
                        "test artifact"));
        assertTrue(symlink.getMessage().contains("regular non-symlink file"));
    }

    private static void assertPin(Map<String, Pin> pins, String modId,
                                  String version, String sha256) {
        Pin pin = pins.get(modId);
        assertEquals(version, pin.version);
        assertEquals(sha256, pin.sha256);
    }

    private static Map<String, Pin> pinsByModId() throws Exception {
        Map<String, Pin> pins = new HashMap<String, Pin>();
        for (Object rawPin : rawPins()) {
            Class<?> type = rawPin.getClass();
            Field modId = field(type, "modId");
            Field version = field(type, "version");
            Field sha256 = field(type, "sha256");
            Field expectedBytes = field(type, "expectedBytes");
            Field expectedWrappedContainerClass = field(type,
                    "expectedWrappedContainerClass");
            Field instanceRelativeArtifact = field(type, "instanceRelativeArtifact");
            Pin pin = new Pin((String) modId.get(rawPin),
                    (String) version.get(rawPin), (String) sha256.get(rawPin),
                    (Long) expectedBytes.get(rawPin),
                    (String) expectedWrappedContainerClass.get(rawPin),
                    (String) instanceRelativeArtifact.get(rawPin));
            assertNull("duplicate mod pin " + pin.modId, pins.put(pin.modId, pin));
        }
        return pins;
    }

    private static Object[] rawPins() throws Exception {
        Field field = field(PinnedRuntimePolicy.class, "MOD_PINS");
        return (Object[]) field.get(null);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Pin {
        final String modId;
        final String version;
        final String sha256;
        final long expectedBytes;
        final String expectedWrappedContainerClass;
        final String instanceRelativeArtifact;

        Pin(String modId, String version, String sha256, long expectedBytes,
            String expectedWrappedContainerClass, String instanceRelativeArtifact) {
            this.modId = modId;
            this.version = version;
            this.sha256 = sha256;
            this.expectedBytes = expectedBytes;
            this.expectedWrappedContainerClass = expectedWrappedContainerClass;
            this.instanceRelativeArtifact = instanceRelativeArtifact;
        }
    }

    private static final class TestCoremodContainer extends DummyModContainer {
        TestCoremodContainer() {
            super(metadata());
        }

        private static ModMetadata metadata() {
            ModMetadata metadata = new ModMetadata();
            metadata.modId = "test-coremod";
            metadata.name = "Test Coremod";
            metadata.version = "1.0";
            return metadata;
        }
    }
}
