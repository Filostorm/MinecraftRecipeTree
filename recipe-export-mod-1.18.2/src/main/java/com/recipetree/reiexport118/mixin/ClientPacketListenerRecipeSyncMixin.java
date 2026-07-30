package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2ReiLifecycleGate;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the owned REI reload only after Minecraft has fully installed the synced recipes. */
@Mixin(value = ClientPacketListener.class, remap = false)
public abstract class ClientPacketListenerRecipeSyncMixin {
    @Inject(
            method = "m_6327_(Lnet/minecraft/network/protocol/game/ClientboundUpdateRecipesPacket;)V",
            at = @At("RETURN"),
            require = 1,
            remap = false)
    private void reiexport$reloadReiAfterAuthoritativeRecipeSync(
            ClientboundUpdateRecipesPacket packet,
            CallbackInfo callback
    ) {
        Mm2ReiLifecycleGate.reloadAfterRecipeSync((ClientPacketListener) (Object) this);
    }
}
