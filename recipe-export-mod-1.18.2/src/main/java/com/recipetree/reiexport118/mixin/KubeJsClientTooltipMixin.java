package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.KubeJsTooltipConcurrencyContract;
import com.recipetree.reiexport118.compat.KubeJsTooltipPublicationRepair;
import dev.architectury.event.Event;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.latvian.mods.kubejs.item.ItemTooltipEventJS;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Pseudo
@Mixin(targets = KubeJsTooltipConcurrencyContract.TARGET_CLASS, remap = false)
public abstract class KubeJsClientTooltipMixin {
    @Shadow(remap = false)
    public static Map<Item, List<ItemTooltipEventJS.StaticTooltipHandler>> staticItemTooltips;

    @Redirect(
            method = KubeJsTooltipConcurrencyContract.HANDLER_INIT_METHOD_SELECTOR,
            at = @At(
                    value = "INVOKE",
                    target = KubeJsTooltipConcurrencyContract.ARCHITECTURY_EVENT_REGISTER_TARGET,
                    ordinal = KubeJsTooltipConcurrencyContract.TOOLTIP_REGISTER_ORDINAL
            ),
            require = 1,
            remap = false
    )
    private void reiexport$registerReloadCoordinatedTooltipHandler(
            Event<?> receiver,
            Object listener
    ) {
        if (receiver != ClientTooltipEvent.ITEM) {
            throw new IllegalStateException(
                    "KubeJS tooltip callback registration ordinal no longer targets "
                            + "ClientTooltipEvent.ITEM"
            );
        }
        if (!(listener instanceof ClientTooltipEvent.Item original)) {
            throw new IllegalStateException(
                    "KubeJS tooltip callback registration no longer supplies ClientTooltipEvent.Item"
            );
        }
        ClientTooltipEvent.Item coordinated = (stack, text, flag) ->
                KubeJsTooltipPublicationRepair.invokeTooltip(
                        original, stack, text, flag);
        registerExact(receiver, coordinated);
    }

    @Inject(
            method = KubeJsTooltipConcurrencyContract.TARGET_METHOD_SELECTOR,
            at = @At("HEAD"),
            require = 1,
            remap = false
    )
    private void reiexport$publishCompleteTooltipHandlers(
            ItemStack stack,
            List<Component> text,
            TooltipFlag flag,
            CallbackInfo callbackInfo
    ) {
        KubeJsTooltipPublicationRepair.ensureInitialized(
                () -> KubeJsTooltipPublicationRepair.asExactMapOrNull(staticItemTooltips),
                handlers -> staticItemTooltips = handlers
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerExact(Event<?> receiver, ClientTooltipEvent.Item listener) {
        ((Event) receiver).register(listener);
    }
}
