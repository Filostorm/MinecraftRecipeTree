package com.recipetree.neiexport1710;

import cpw.mods.fml.common.InjectedModContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.Locale;

/** Hash- and version-pins every external artifact used by an exporter-specific semantic adapter. */
final class PinnedRuntimePolicy {
    private static final ModPin[] MOD_PINS = {
            new ModPin("appliedenergistics2", "rv3-beta-695-GTNH",
                    "1601d33565f40478a073327ab50f76a127063b8917e3a192e302d54e0ac70a44"),
            new ModPin("ae2fc", "1.4.120-gtnh",
                    "eb28d4cd24934a1d69d4fc34d1e8022d968184d57f27a6706e54c1ba53a53b9a"),
            new ModPin("AWWayofTime", "1.7.52",
                    "f1b23b49034bbf1f4ef942b56818119a20035e9da933134c16c4b2f4e14fd855"),
            new ModPin("ArchitectureCraft", "1.11.6",
                    "cd9747b7b3055a2e4e8b0d807894b29311fadb1d7ea6c7ba03f4b9b09b12fe9f"),
            new ModPin("Avaritia", "1.77",
                    "ef4ad2efcbbe88dfa2673921781988781f106f52a0be4e1d80edcfeb0609f656"),
            new ModPin("AdvancedSolarPanel", "1.7.10-3.5.1",
                    "a8c76322d88ff67cdd374e390f03281ff29b38aef7ffa5ac82e323c9524436eb",
                    307727L),
            new ModPin("littletiles", "1.5.14-GTNH",
                    "74eccef8e2fdc74e059037868347a0a3697b84ef8d75ced1c86d58df5ad9afca",
                    585187L),
            new ModPin("malisisdoors", "1.18.2-GTNH",
                    "4293712bda3c1c73bd629354a06f52c00c884a664d56bbba4a52ed89a01d29c4",
                    2218297L),
            new ModPin("modernmarkings", "0.3.12-1.7.10",
                    "bcd5fd1740b16c373072b2a7e971d9f30971c5c11c14fd77c6f8fa61c04b080a",
                    101067L),
            new ModPin("NotEnoughItems", "2.8.44-GTNH",
                    "c3f0136f68a74c010593a51ecd3414c4eb8d861bebfe357a19e518a033aca92b"),
            new ModPin("gregtech_nh", "5.09.51.482",
                    "4ab7ce174a8f6fb7a90d8d11d56056aab2de577c36c6084b37ce890d7b1d67bf",
                    55427943L),
            new ModPin("bq_standard", "3.7.15-GTNH",
                    "9f72317dda06cd883109e9852f2536625412e874c39aa0cb8b70663b716bdbb5"),
            new ModPin("beebetteratbees", "0.4.3-GTNH",
                    "fd23426e7fdb3bc40114aa6a3d8d0186f9a0904a27fc5b9e2d54b88614d940cc"),
            new ModPin("Botania", "1.12.28-GTNH",
                    "6b56a5e96a4cba8e639da98a1a0fcc56a8b90600c6a0fbdf401c3f1c212d4712"),
            new ModPin("ProjectBlue", "1.2.1-GTNH",
                    "9c438068f97bb42e1d86f5671b206931216b1296b5ce4cf0066f5a09fcdd7160"),
            new ModPin("ProjRed|Core", "4.12.6-GTNH",
                    "124cea32199608cf5490cb2a04d9e054e1d88449934e2248c24940d7b0423ebe"),
            new ModPin("gendustry", "1.9.4-GTNH",
                    "6690a0768748c3d6d477ef6d9db062e59e57dd52b7a1f7c3a6137b8f6f3ab76c"),
            new ModPin("ForgeMultipart", "1.7.2",
                    "6c6b862219e4e5fc5a6de996a6d4f47cab062477619911b7e6b06af3e4e85eb6"),
            new ModPin("LogisticsPipes", "1.4.24-GTNH",
                    "ade88fd46bd851ead0ce55b988b900dcbde262aa11f549e0bd06cee9a0f601c4",
                    2917350L),
            new ModPin("NEIAddons|Forestry", "1.17.0",
                    "bfac8b5ff2c2b67a3a0d6e6eea791279f97c7de65fc9488e77f974e7bf75967a",
                    145396L),
            new ModPin("Forestry", "4.10.17",
                    "b537738e29c242726ce7f356985bfd21214f7c8948ea0526e951dbe2f419a3fd",
                    3394143L),
            new ModPin("ae2wct", "1.12.7",
                    "39f823036dce7030bd4e68762a1e2a0469852255e3d40e0cc6e1f3bc264b42ae",
                    290829L),
            new ModPin("Railcraft", "9.16.33",
                    "6bd57ff9f788310971ee0acb08128004494bffd292563514a3b1244d18141306",
                    3435323L),
            new ModPin("CodeChickenCore", "1.4.10",
                    "eb55946d997ba92e4533ef2b88e6f633fd5aee862bcbcaa3a030ea53aeb20398",
                    "codechicken.core.asm.CodeChickenCoreModContainer",
                    "mods/CodeChickenCore-1.4.10.jar"),
            new ModPin("CarpentersBlocks", "3.7.0-GTNH",
                    "61d7adbb94e57063f9e1279d557bd1986d0a32e6e4f9787060a0841d76afe387"),
            new ModPin("StevesCarts", "2.3.12",
                    "cb561db0bed4a106b483a739718f148aee26ec28724d6f831042c159a3f17af6"),
            new ModPin("Ic2Nei", "1.2.1",
                    "29dffc972a56d3de3a92c8cd67fdaab3b4b91825ff0951369d109329a7a16c9f"),
            new ModPin("neicustomdiagram", "1.7.5",
                    "0d5764a3695d45f1043aa59f95ae4d8762ced0b48c9c1e652c20fa4c8cc347c8"),
            new ModPin("neiintegration", "1.5.0",
                    "15f319778db853336427848473174b1093fc607cd13b60e215344bd07edfb258"),
            new ModPin("GalacticraftCore", "3.3.13-GTNH",
                    "e382339878a3dea2ab9ccb359bc37096f129802fff4e3a88de8db9fb7e1d4fa9"),
            new ModPin("GalaxySpace", "1.1.121-GTNH",
                    "bb0ccf7f54cd73cee83ea8a8ce9e63e1d20ba754a5fb4ebb342d1e81498b7ca8"),
            new ModPin("GalacticraftAmunRa", "0.8.2",
                    "bcd0ca636d545a0f361f6583fd667bd40ca4b79198fc06d1552038310a7b1978"),
            new ModPin("ExtraUtilities", "1.2.12",
                    "a1941ee4d4b965c4d2ddc6ec0e0b1eb3f3ff8c8b492a18a25b5fcf27c67ba58b"),
            new ModPin("IC2", "2.2.828-experimental",
                    "de1d4597972be036eccd1c3b37e9980c3c9d9cdb92f52df2bf470971873893f6"),
            new ModPin("tcneiadditions", "1.5.4",
                    "e7d14bd6b2ca948e79adfb4bcb519d877649ff8aab9f5030fcd08fc4301d6c09"),
            new ModPin("TConstruct", "1.13.57-GTNH",
                    "f2cb53b94f135b7523bcb7afea5ef53b9afb40c0bc034fc5ec5018ad0502bc61"),
            new ModPin("thaumcraftneiplugin", "1.7.10-1.7a",
                    "d39e6c8fc27709b04d58ec4c1b625648776dbf01cdf7af2d51388ee215d5d4e8"),
            new ModPin("gadomancy", "1.4.8",
                    "90fb7e230a18a0eac65645aedc5c205acbc53844d95154850f90a6161beeaa26"),
            new ModPin("ThaumicHorizons", "1.7.9",
                    "996f9c1d3679cd56f9b82caba7853a004b4cf1e82c449121802552a7b55ee412"),
            new ModPin("TwilightForest", "2.7.13",
                    "3f5c14c79c74824e354cc9817d03d2c603dca2a993db2a612b12c16fc381582d"),
            new ModPin("WitchingGadgets", "1.7.25-GTNH",
                    "7c5f6af01aabe2c22814a5c0a6241eaafe226e2b29a0c425e0b937d09cca5e26"),
            new ModPin("WR-CBE|Addons", "1.7.1",
                    "56bf1c9672840e42049ec796d7c119b1b532317d2074207acae4d6884f75f394"),
            new ModPin("Thaumcraft", "4.2.3.5",
                    "587b5a084643e617e9d87319ad34b341ebb920e09d060e5a5fbedecb96553f89")
    };
    private static final String TCNA_CONFIG_SHA256 =
            "edb26bf031cf98ac514a0365622efc25ff6e756a126e095030d1bf4391a69e00";
    private static final String AE2_CONFIG_SHA256 =
            "48416c9e4cda63f44ec089e2e60d44c1d5c1a2e6baea7b7831777046cff1326d";
    private static final String EXTRAUTILITIES_CONFIG_SHA256 =
            "7b113b11e98246071b1c8afe7576b9f4064686d1ec5f0d128f3c3bba211447fc";

    private PinnedRuntimePolicy() {
    }

    static void verify() throws ExportFailure {
        String minecraft = Loader.MC_VERSION;
        String forge = net.minecraftforge.common.ForgeVersion.getVersion();
        if (!"1.7.10".equals(minecraft)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "runtime Minecraft pin mismatch: " + minecraft);
        }
        if (!"10.13.4.1614".equals(forge)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "runtime Forge pin mismatch: " + forge);
        }
        for (ModPin pin : MOD_PINS) {
            verifyMod(pin);
        }
        Path config = Minecraft.getMinecraft().mcDataDir.toPath()
                .resolve("config").resolve("tcneiadditions.cfg");
        verifyRegularDigest(config, TCNA_CONFIG_SHA256,
                "pinned tcneiadditions.cfg");
        Path ae2Config = Minecraft.getMinecraft().mcDataDir.toPath()
                .resolve("config").resolve("AppliedEnergistics2")
                .resolve("AppliedEnergistics2.cfg");
        verifyRegularDigest(ae2Config, AE2_CONFIG_SHA256,
                "pinned AppliedEnergistics2.cfg");
        Path extraUtilitiesConfig = Minecraft.getMinecraft().mcDataDir.toPath()
                .resolve("config").resolve("ExtraUtilities.cfg");
        verifyRegularDigest(extraUtilitiesConfig, EXTRAUTILITIES_CONFIG_SHA256,
                "pinned ExtraUtilities.cfg");
    }

    private static void verifyMod(ModPin pin) throws ExportFailure {
        ModContainer container = Loader.instance().getIndexedModList().get(pin.modId);
        if (container == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "required pinned mod is absent: " + pin.modId);
        }
        if (!pin.version.equals(container.getVersion())) {
            throw new ExportFailure("HANDLER_UNLOADED", pin.modId
                    + " version mismatch; expected " + pin.version + ", got "
                    + container.getVersion());
        }
        Path sourceArtifact = resolveSourceArtifact(container, pin);
        verifyRegularDigest(sourceArtifact, pin.sha256,
                pin.modId + " " + pin.version + " source artifact");
        if (pin.expectedBytes >= 0L) {
            verifyRegularSize(sourceArtifact, pin.expectedBytes,
                    pin.modId + " " + pin.version + " source artifact");
        }
    }

    private static Path resolveSourceArtifact(ModContainer container, ModPin pin)
            throws ExportFailure {
        if (pin.instanceRelativeArtifact == null) {
            if (container.getSource() == null) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        pin.modId + " has no source artifact path");
            }
            return container.getSource().toPath();
        }

        Path gameDirectory = Minecraft.getMinecraft().mcDataDir.toPath()
                .toAbsolutePath().normalize();
        return resolveExplicitInjectedCoremodSource(container, pin.modId, pin.version,
                pin.expectedWrappedContainerClass, pin.instanceRelativeArtifact,
                gameDirectory);
    }

    static Path resolveExplicitInjectedCoremodSource(ModContainer container, String modId,
                                                      String version,
                                                      String expectedWrappedContainerClass,
                                                      String instanceRelativeArtifact,
                                                      Path gameDirectory)
            throws ExportFailure {
        if (container.getClass() != InjectedModContainer.class) {
            throw new ExportFailure("HANDLER_UNLOADED", modId
                    + " explicit coremod source policy requires exact outer container "
                    + "cpw.mods.fml.common.InjectedModContainer; got "
                    + container.getClass().getName());
        }
        ModContainer wrapped = ((InjectedModContainer) container).wrappedContainer;
        String wrappedClass = wrapped == null ? "null" : wrapped.getClass().getName();
        if (!expectedWrappedContainerClass.equals(wrappedClass)) {
            throw new ExportFailure("HANDLER_UNLOADED", modId
                    + " wrapped container mismatch; expected "
                    + expectedWrappedContainerClass + ", got " + wrappedClass);
        }
        if (container.getSource() == null
                || !"minecraft.jar".equals(container.getSource().getPath())) {
            throw new ExportFailure("HANDLER_UNLOADED", modId
                    + " legacy injected source sentinel mismatch; expected minecraft.jar, got "
                    + container.getSource());
        }

        Path normalizedGameDirectory = gameDirectory.toAbsolutePath().normalize();
        Path expected = normalizedGameDirectory.resolve(instanceRelativeArtifact).normalize();
        if (!expected.startsWith(normalizedGameDirectory)) {
            throw new ExportFailure("HANDLER_UNLOADED", modId
                    + " explicit source artifact escapes the game directory: " + expected);
        }

        CodeSource codeSource = wrapped.getClass().getProtectionDomain().getCodeSource();
        URL location = codeSource == null ? null : codeSource.getLocation();
        if (location == null || !"file".equals(location.getProtocol())) {
            throw new ExportFailure("HANDLER_UNLOADED", modId
                    + " wrapped container has no exact file: code source: " + location);
        }
        Path loaded;
        try {
            loaded = Paths.get(location.toURI()).toAbsolutePath().normalize();
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED", modId
                    + " wrapped-container code source is not a local filesystem path: "
                    + location, error);
        }
        if (!expected.equals(loaded)) {
            throw new ExportFailure("HANDLER_UNLOADED", modId
                    + " wrapped-container code source mismatch; expected " + expected
                    + ", got " + loaded);
        }
        GtnhNeiExportMod.LOGGER.info("[gtnh-nei-export] Applying explicit pinned "
                + "legacy-coremod source policy for {} {}: wrappedContainer={}, "
                + "syntheticSource=minecraft.jar, loadedCodeSource={}", modId, version,
                expectedWrappedContainerClass, loaded);
        return loaded;
    }

    static void verifyRegularDigest(Path file, String expected, String label)
            throws ExportFailure {
        try {
            Path normalized = file.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        label + " must be a regular non-symlink file: " + normalized);
            }
            String actual = sha256(normalized);
            if (!expected.equals(actual)) {
                throw new ExportFailure("HANDLER_UNLOADED", label
                        + " SHA-256 mismatch; expected " + expected + ", got "
                        + actual + ": " + normalized);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not verify " + label, error);
        }
    }

    static void verifyRegularSize(Path file, long expectedBytes, String label)
            throws ExportFailure {
        try {
            Path normalized = file.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        label + " must be a regular non-symlink file: " + normalized);
            }
            long actualBytes = Files.size(normalized);
            if (actualBytes != expectedBytes) {
                throw new ExportFailure("HANDLER_UNLOADED", label
                        + " byte-size mismatch; expected " + expectedBytes + ", got "
                        + actualBytes + ": " + normalized);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not verify " + label + " byte size", error);
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        StringBuilder value = new StringBuilder(64);
        for (byte part : digest.digest()) {
            value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        }
        return value.toString();
    }

    private static final class ModPin {
        final String modId;
        final String version;
        final String sha256;
        final long expectedBytes;
        final String expectedWrappedContainerClass;
        final String instanceRelativeArtifact;

        ModPin(String modId, String version, String sha256) {
            this(modId, version, sha256, -1L, null, null);
        }

        ModPin(String modId, String version, String sha256, long expectedBytes) {
            this(modId, version, sha256, expectedBytes, null, null);
        }

        ModPin(String modId, String version, String sha256,
               String expectedWrappedContainerClass, String instanceRelativeArtifact) {
            this(modId, version, sha256, -1L,
                    expectedWrappedContainerClass, instanceRelativeArtifact);
        }

        private ModPin(String modId, String version, String sha256, long expectedBytes,
               String expectedWrappedContainerClass, String instanceRelativeArtifact) {
            this.modId = modId;
            this.version = version;
            this.sha256 = sha256;
            this.expectedBytes = expectedBytes;
            if ((expectedWrappedContainerClass == null)
                    != (instanceRelativeArtifact == null)) {
                throw new IllegalArgumentException(
                        "explicit artifact fields must either both be set or both be absent");
            }
            this.expectedWrappedContainerClass = expectedWrappedContainerClass;
            this.instanceRelativeArtifact = instanceRelativeArtifact;
        }
    }
}
