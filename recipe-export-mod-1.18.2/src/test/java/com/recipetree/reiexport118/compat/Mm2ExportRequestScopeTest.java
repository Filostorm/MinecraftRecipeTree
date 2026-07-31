package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.mixin.ReiExportMixinConfigPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2ExportRequestScopeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void absentRequestDisablesMixinsBeforeTargetOrBytecodeInspection() {
        Mm2ExportRequestScope.Inspection inspection =
                Mm2ExportRequestScope.inspect(temporaryDirectory);
        assertEquals(Mm2ExportRequestScope.State.ABSENT, inspection.state());
        assertFalse(inspection.isExactMm2());

        ReiExportMixinConfigPlugin plugin =
                new ReiExportMixinConfigPlugin(temporaryDirectory);
        assertFalse(plugin.shouldApplyMixin(
                "deliberately.drifted.Target",
                "com.recipetree.reiexport118.mixin.Ae2ColorApplicatorCreativeMixin"));
        assertFalse(plugin.shouldApplyMixin(
                "net.minecraft.client.multiplayer.ClientPacketListener",
                "com.recipetree.reiexport118.mixin.ClientPacketListenerRecipeSyncMixin"));
    }

    @Test
    void exactIdentityEnablesSelectionAndLeavesPackVersionAsMetadata() throws Exception {
        writeRequest(Mm2ExportRequestScope.PROFILE, Mm2ExportRequestScope.PACK_NAME, "2.7.19");
        Mm2ExportRequestScope.Inspection inspection =
                Mm2ExportRequestScope.inspect(temporaryDirectory);
        assertTrue(inspection.isExactMm2());

        ReiExportMixinConfigPlugin plugin =
                new ReiExportMixinConfigPlugin(temporaryDirectory);
        assertTrue(plugin.shouldApplyMixin(
                "net.minecraft.client.multiplayer.ClientPacketListener",
                "com.recipetree.reiexport118.mixin.ClientPacketListenerRecipeSyncMixin"));
        assertTrue(plugin.shouldApplyMixin(
                "me.shedaniel.rei.jeicompat.wrap.JEIRecipeRegistration",
                "com.recipetree.reiexport118.mixin.JeiRecipeRegistrationPigmentMixin"));
        assertTrue(plugin.shouldApplyMixin(
                "me.shedaniel.rei.jeicompat.JEIPluginDetector",
                "com.recipetree.reiexport118.mixin.JeiPluginDetectorTypeCacheMixin"));
        assertTrue(plugin.shouldApplyMixin(
                "blusunrize.immersiveengineering.api.IEApi",
                "com.recipetree.reiexport118.mixin.ImmersiveEngineeringTagCacheMixin"));
        assertThrows(IllegalStateException.class, () -> plugin.shouldApplyMixin(
                "drifted.Target",
                "com.recipetree.reiexport118.mixin.Ae2ColorApplicatorCreativeMixin"));
    }

    @Test
    void existingRequestWithMismatchedOrMalformedIdentityFailsClosed() throws Exception {
        writeRequest("another-profile", Mm2ExportRequestScope.PACK_NAME, "1.0.0");
        assertThrows(
                IllegalStateException.class,
                () -> Mm2ExportRequestScope.inspect(temporaryDirectory));

        writeRequest(Mm2ExportRequestScope.PROFILE, "A different pack", "1.0.0");
        assertThrows(
                IllegalStateException.class,
                () -> Mm2ExportRequestScope.inspect(temporaryDirectory));

        Files.writeString(requestPath(), "{\"profile\":42}");
        assertThrows(
                IllegalStateException.class,
                () -> Mm2ExportRequestScope.inspect(temporaryDirectory));
    }

    @Test
    void nonRegularRequestIsNotMisclassifiedAsAbsent() throws Exception {
        Files.createDirectory(requestPath());
        assertThrows(
                IllegalStateException.class,
                () -> Mm2ExportRequestScope.inspect(temporaryDirectory));
    }

    private void writeRequest(String profile, String packName, String packVersion)
            throws Exception {
        Files.writeString(
                requestPath(),
                "{\"profile\":\"" + profile + "\",\"packName\":\"" + packName
                        + "\",\"packVersion\":\"" + packVersion + "\"}");
    }

    private Path requestPath() {
        return temporaryDirectory.resolve(Mm2ExportRequestScope.REQUEST_NAME);
    }
}
