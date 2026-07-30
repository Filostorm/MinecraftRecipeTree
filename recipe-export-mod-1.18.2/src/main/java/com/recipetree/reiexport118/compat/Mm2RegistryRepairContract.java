package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Exact binary contract for the MM2 registry-census repairs. */
final class Mm2RegistryRepairContract {
    record ModPin(
            String modId,
            String version,
            String jarFileName,
            String jarSha256
    ) {
    }

    record ClassPin(String modId, String resource, String sha256) {
    }

    static final ModPin COFH_CORE = new ModPin(
            "cofh_core",
            "9.2.3",
            "cofh_core-1.18.2-9.2.3.47.jar",
            "e0208bf4524841e0a28c791f070d5d36dfd3d7e61720385bdd24c098046ed38e");
    static final ModPin THERMAL = new ModPin(
            "thermal",
            "9.2.2",
            "thermal_foundation-1.18.2-9.2.2.58.jar",
            "2ebb58af57b0e64482b7809cf7f4c19c5725dc671317893302694660110def8f");
    static final ModPin NATURES_AURA = new ModPin(
            "naturesaura",
            "36.3",
            "NaturesAura-36.3.jar",
            "5496d2d38a797f327cf739f73bed0375ad5bcedbfd05f56d697ccbc7e2ea7eb7");
    static final ModPin PROJECT_RED_FABRICATION = new ModPin(
            "projectred_fabrication",
            "4.16.0",
            "ProjectRed-1.18.2-4.16.0-fabrication.jar",
            "f42edab2dabedbd6d7007b1131a38deb7feda36abb471487bf8c37771a460085");
    static final ModPin PROJECT_RED_INTEGRATION = new ModPin(
            "projectred_integration",
            "4.16.0",
            "ProjectRed-1.18.2-4.16.0-integration.jar",
            "d22aaf567b25ddd74cdb2e190349ec5a8c9a66b8412d3bbf1973b6fc9b39b3dd");
    static final ModPin MEKANISM = new ModPin(
            "mekanism",
            "10.2.5",
            "Mekanism-1.18.2-10.2.5.465.jar",
            "ae5f3818940aa99cf3bf51786a9b66284327d14120924bd99f864e6b5457e523");

    static final List<ModPin> MOD_PINS = List.of(
            COFH_CORE, THERMAL, NATURES_AURA, PROJECT_RED_FABRICATION,
            PROJECT_RED_INTEGRATION, MEKANISM);
    static final List<ClassPin> CLASS_PINS = List.of(
            new ClassPin(
                    COFH_CORE.modId(),
                    "cofh/lib/api/item/IFluidContainerItem.class",
                    "a6c59902f84182cf685f045f6863f20a3e8f1ca2eda752da950129c700c7a695"),
            new ClassPin(
                    COFH_CORE.modId(),
                    "cofh/lib/api/item/IXpContainerItem.class",
                    "e8b48aea16970f548c7a64e96562771b6516a4e6fbd3196657c352150d052037"),
            new ClassPin(
                    COFH_CORE.modId(),
                    "cofh/core/item/FluidContainerItem.class",
                    "bb83b2a9db1bbdea88760d031f8b72b59aff2fc7e4ef508e6a8ba9ba59b4b169"),
            new ClassPin(
                    THERMAL.modId(),
                    "cofh/thermal/core/item/XpCrystalItem.class",
                    "3c0d8b4f03a8781c9bfa68e01f62a6bc8b1769f0f64a2d894c10f4ae05180e64"),
            new ClassPin(
                    THERMAL.modId(),
                    "cofh/thermal/core/init/TCoreItems.class",
                    "7c836cfe140e8a742465490d560a624552ed11d475c7bc95b8d75c378079d40c"),
            new ClassPin(
                    NATURES_AURA.modId(),
                    "de/ellpeck/naturesaura/items/ModItems.class",
                    "cd6c319aa92f6f5c12df27bf6cee1dd969a0f87de85d14384a4394ecac472e3b"),
            new ClassPin(
                    PROJECT_RED_FABRICATION.modId(),
                    "mrtjp/projectred/fabrication/init/FabricationParts.class",
                    "d0feb8a311a8ba92dcecad120a62f9e4fafc21abdd01f85ac59290236e57b35f"),
            new ClassPin(
                    PROJECT_RED_FABRICATION.modId(),
                    "mrtjp/projectred/fabrication/item/FabricatedGatePartItem.class",
                    "e8e16fafde29ac12eb5837806b0337acc1e07adb8cc4411a74d2aa512267892e"),
            new ClassPin(
                    PROJECT_RED_FABRICATION.modId(),
                    "mrtjp/projectred/fabrication/init/FabricationReferences.class",
                    "df1bf6eb86760aa465a37b1718051f35eaf78b2a2d9d4e48e12447b60c35d0dd"),
            new ClassPin(
                    PROJECT_RED_INTEGRATION.modId(),
                    "mrtjp/projectred/integration/ProjectRedIntegration.class",
                    "96574c7341b067930f4e1f9014d27b3e2ca5e9ea58d91dbf3ea2e684ff083d70"),
            new ClassPin(
                    PROJECT_RED_INTEGRATION.modId(),
                    "mrtjp/projectred/integration/init/IntegrationParts.class",
                    "d53c647843bd57b565a0d760b5d3c8695820f9d7c0adf30e071f96135220ccbe"),
            new ClassPin(
                    PROJECT_RED_INTEGRATION.modId(),
                    "mrtjp/projectred/integration/GateType.class",
                    "bb548098c40ebe375d293bb5c1d933ed3e5698f3044849352c89c8b23c476be3"),
            new ClassPin(
                    MEKANISM.modId(),
                    "mekanism/client/jei/RecipeRegistryHelper.class",
                    "46bca80871c9a7e8370d791fef3c3a83b3d139748feac2a9914a3ed62ed4a3bc"),
            new ClassPin(
                    MEKANISM.modId(),
                    "mekanism/common/recipe/MekanismRecipeType.class",
                    "64e270888dc3a0f4bb0888ebfb0e815b7b1c0240352af273fc7cae8947f179f3"),
            new ClassPin(
                    MEKANISM.modId(),
                    "mekanism/client/jei/MekanismJEI.class",
                    "b8efb894bab3b6dc5079fbd11c52fdd7c4894657746bc6f7e7290d4bf071832e"));

    private static volatile boolean armed;

    private Mm2RegistryRepairContract() {
    }

    static synchronized void validateAndArm() {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            throw new IllegalStateException(
                    "MM2 registry-repair contract cannot arm before the MM2 lifecycle contract");
        }
        if (armed) {
            throw new IllegalStateException("MM2 registry-repair contract was armed more than once");
        }

        List<String> failures = new ArrayList<>();
        for (ModPin pin : MOD_PINS) {
            validateMod(pin, failures);
        }
        for (ClassPin pin : CLASS_PINS) {
            validateClass(pin, failures);
        }
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                ReiExportMod.LOGGER.error(
                        "[reiexport] MM2 registry-repair contract failure: {}", failure);
            }
            throw new IllegalStateException(
                    "MM2 registry-repair contract rejected " + failures.size()
                            + " exact binary pin(s)");
        }

        armed = true;
        ReiExportMod.LOGGER.info(
                "[reiexport] Armed exact MM2 registry-repair binaries mods={} classes={}",
                MOD_PINS.size(), CLASS_PINS.size());
    }

    static boolean isArmed() {
        return armed;
    }

    static void requireArmed() {
        if (!armed) {
            throw new IllegalStateException(
                    "MM2 exporter-owned entry canonicalization ran before registry repairs armed");
        }
    }

    private static void validateMod(ModPin pin, List<String> failures) {
        String actualVersion = ModList.get().getModContainerById(pin.modId())
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
        if (!pin.version().equals(actualVersion)) {
            failures.add("mod version drift mod=" + pin.modId()
                    + ", expected=" + pin.version() + ", actual=" + actualVersion);
            return;
        }

        IModFileInfo fileInfo = ModList.get().getModFileById(pin.modId());
        Path path = fileInfo == null || fileInfo.getFile() == null
                ? null : fileInfo.getFile().getFilePath();
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("mod JAR is not a plain file mod=" + pin.modId() + ", path=" + path);
            return;
        }
        if (!pin.jarFileName().equals(path.getFileName().toString())) {
            failures.add("mod JAR filename drift mod=" + pin.modId()
                    + ", expected=" + pin.jarFileName() + ", actual=" + path.getFileName());
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            String actualSha256 = sha256(input);
            if (!pin.jarSha256().equals(actualSha256)) {
                failures.add("mod JAR drift mod=" + pin.modId() + ", path=" + path
                        + ", expectedSha256=" + pin.jarSha256()
                        + ", actualSha256=" + actualSha256);
            }
        } catch (IOException exception) {
            failures.add("mod JAR validation failed mod=" + pin.modId()
                    + ", path=" + path + ", cause=" + exception);
        }
    }

    private static void validateClass(ClassPin pin, List<String> failures) {
        try (InputStream input = Mm2RegistryRepairContract.class.getClassLoader()
                .getResourceAsStream(pin.resource())) {
            if (input == null) {
                failures.add("missing class resource mod=" + pin.modId()
                        + ", resource=" + pin.resource());
                return;
            }
            String actualSha256 = sha256(input);
            if (!pin.sha256().equals(actualSha256)) {
                failures.add("class drift mod=" + pin.modId() + ", resource=" + pin.resource()
                        + ", expectedSha256=" + pin.sha256()
                        + ", actualSha256=" + actualSha256);
            }
        } catch (IOException exception) {
            failures.add("class validation failed mod=" + pin.modId()
                    + ", resource=" + pin.resource() + ", cause=" + exception);
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
}
