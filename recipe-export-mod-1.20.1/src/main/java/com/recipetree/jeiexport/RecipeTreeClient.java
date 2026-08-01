package com.recipetree.jeiexport;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public final class RecipeTreeClient {
    private static final KeyMapping OPEN_PLANNER = new KeyMapping(
            "key.jeiexport.open_recipe_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.jeiexport");

    private RecipeTreeClient() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PLANNER);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        while (OPEN_PLANNER.consumeClick()) {
            openForCurrentItem(minecraft);
        }
    }

    private static void openForCurrentItem(Minecraft minecraft) {
        IJeiRuntime runtime = JeiExportPlugin.runtime();
        if (runtime == null) {
            minecraft.player.displayClientMessage(
                    Component.literal("Recipe Tree is waiting for JEI to finish loading."), true);
            return;
        }

        Optional<ItemStack> target = Optional.ofNullable(
                        runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK))
                .filter(stack -> !stack.isEmpty());
        if (target.isEmpty()) {
            target = runtime.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
                    .filter(stack -> !stack.isEmpty());
        }
        if (target.isEmpty()) {
            target = Optional.of(minecraft.player.getMainHandItem()).filter(stack -> !stack.isEmpty());
        }
        if (target.isEmpty()) {
            target = Optional.of(minecraft.player.getOffhandItem()).filter(stack -> !stack.isEmpty());
        }

        if (target.isEmpty()) {
            minecraft.player.displayClientMessage(
                    Component.literal("Hover a JEI item or hold an item, then press G."), true);
            return;
        }
        minecraft.setScreen(new RecipeTreeScreen(target.get().copyWithCount(1), runtime));
    }
}
