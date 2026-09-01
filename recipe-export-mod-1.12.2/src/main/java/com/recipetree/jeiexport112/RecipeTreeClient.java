package com.recipetree.jeiexport112;

import mezz.jei.api.IIngredientListOverlay;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Client-side entry point for the live 1.12.2 Recipe Tree viewer.
 *
 * <p>The normal JEI 4.12 API exposes hovered overlay ingredients but not hovered recipe-GUI
 * ingredients. Had Enough Items adds that latter method to the same interface at runtime. It is
 * deliberately feature-detected through reflection here so this mod still links against the
 * published JEI 4.12 compatibility floor.</p>
 */
public final class RecipeTreeClient {
    static final RecipeTreeClient INSTANCE = new RecipeTreeClient();
    static final KeyBinding OPEN_RECIPE_TREE = new KeyBinding(
            "key.jeiexport.open_recipe_tree",
            Keyboard.KEY_G,
            "key.categories.jeiexport");

    private static final int INVENTORY_DISCOVERY_INTERVAL_TICKS = 20;

    private int inventoryDiscoveryCooldown;
    private RecipeTreeScreen lastViewedTree;
    private String activeWorldKey;
    private Class<?> inspectedRecipesGuiClass;
    private Method recipesGuiHoveredIngredientMethod;
    private boolean missingRecipesGuiHoverMethodLogged;
    private boolean ingredientOverlayFocusFailureLogged;

    private RecipeTreeClient() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null) return;

        bindActiveWorld(minecraft);
        trackInventoryDiscoveries(minecraft);

        // GUI key presses are handled by GuiScreenEvent below. Always drain the KeyBinding while
        // a GUI is open so the same press cannot fire again after replacing that GUI with a tree.
        while (OPEN_RECIPE_TREE.isPressed()) {
            if (minecraft.currentScreen != null) continue;
            if (reopenLastViewedTree(minecraft)) return;
            openForCurrentIngredient(minecraft, null);
            return;
        }
    }

    @SubscribeEvent
    public void onScreenKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!isOpenTreeKeyPress()) return;

        GuiScreen screen = event.getGui();
        if (screen instanceof RecipeTreeScreen || isTyping(screen)) return;

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null) return;

        bindActiveWorld(minecraft);
        Object hovered = findHoveredIngredient(minecraft, screen);
        if (!openForCurrentIngredient(minecraft, hovered)) return;

        // Cancel only after the planner actually accepted the input and replaced the screen.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        lastViewedTree = null;
        activeWorldKey = null;
        RecipeTreeProgress.get().setActiveWorld(null);
        inventoryDiscoveryCooldown = 0;
        resetRecipesGuiReflection();
        RecipeTreeScreen.releaseRuntimeLayouts();
        JeiExportPlugin.clearViewerCaches();
        JeiExportMod.LOGGER.info(
                "[jeiexport] Released live Recipe Tree screens, native layouts, and semantic query caches after disconnect");
    }

    static void rememberTree(RecipeTreeScreen tree) {
        INSTANCE.lastViewedTree = tree;
    }

    void openFromBook(ItemStack book) {
        Minecraft minecraft = Minecraft.getMinecraft();
        bindActiveWorld(minecraft);
        RecipeTreeViewerBridge bridge = JeiExportPlugin.getViewerBridge();
        if (bridge != null) {
            if (lastViewedTree == null) {
                lastViewedTree = RecipeTreeScreen.restoreLastViewed(bridge);
            }
            if (lastViewedTree != null) {
                minecraft.displayGuiScreen(lastViewedTree);
                return;
            }
            JeiExportMod.LOGGER.info(
                    "[jeiexport] Recipe Tree book opened its guide because no saved tree exists");
        } else {
            JeiExportMod.LOGGER.info(
                    "[jeiexport] Recipe Tree book opened its guide because JEI/HEI has not "
                            + "finished initializing the live viewer");
        }
        minecraft.displayGuiScreen(new GuiScreenBook(minecraft.player, book, false));
    }

    private void bindActiveWorld(Minecraft minecraft) {
        String integratedFolder = null;
        if (minecraft.isSingleplayer() && minecraft.getIntegratedServer() != null) {
            integratedFolder = minecraft.getIntegratedServer().getFolderName();
        }
        ServerData server = minecraft.getCurrentServerData();
        String serverAddress = server == null ? null : server.serverIP;
        String resolved = worldScopeKey(
                minecraft.isSingleplayer(),
                integratedFolder,
                serverAddress);

        if (Objects.equals(activeWorldKey, resolved)) return;
        lastViewedTree = null;
        activeWorldKey = resolved;
        RecipeTreeProgress.get().setActiveWorld(resolved);

        if (resolved == null) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Could not identify the active world; Recipe Tree will not load "
                            + "or save recent trees until a stable save/server identity is "
                            + "available");
        }
    }

    /** Pure world identity helper kept package-private for compatibility tests. */
    static String worldScopeKey(
            boolean singleplayer,
            String integratedFolder,
            String serverAddress) {
        if (singleplayer) {
            String folder = normalizedIdentityPart(integratedFolder, false);
            return folder == null ? null : "singleplayer:" + folder;
        }
        String address = normalizedIdentityPart(serverAddress, true);
        return address == null ? null : "multiplayer:" + address;
    }

    private static String normalizedIdentityPart(String value, boolean lowercase) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return lowercase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private boolean reopenLastViewedTree(Minecraft minecraft) {
        RecipeTreeViewerBridge bridge = requireViewerBridge(minecraft);
        // Treat the warning/status message as handled so the same press does not issue it twice by
        // immediately falling through to openForCurrentIngredient.
        if (bridge == null) return true;

        if (lastViewedTree == null) {
            lastViewedTree = RecipeTreeScreen.restoreLastViewed(bridge);
        }
        if (lastViewedTree == null) return false;

        minecraft.displayGuiScreen(lastViewedTree);
        return true;
    }

    private boolean openForCurrentIngredient(Minecraft minecraft, Object alreadyHovered) {
        RecipeTreeViewerBridge bridge = requireViewerBridge(minecraft);
        if (bridge == null) return false;

        Object target = alreadyHovered;
        if (isEmptyItem(target)) target = null;
        if (target == null) target = findHoveredIngredient(minecraft, minecraft.currentScreen);
        if (target == null) target = normalizedHeldItem(minecraft.player.getHeldItemMainhand());
        if (target == null) target = normalizedHeldItem(minecraft.player.getHeldItemOffhand());
        if (target instanceof ItemStack) target = normalizedHeldItem((ItemStack) target);

        if (target == null) {
            minecraft.player.sendStatusMessage(new TextComponentString(
                    "Hover a JEI/HEI or inventory ingredient, or hold an item, then press G."), true);
            return false;
        }

        final RecipeTreeViewerBridge.Ingredient ingredient;
        try {
            ingredient = bridge.ingredient(target);
        } catch (RuntimeException error) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Could not open Recipe Tree for hovered ingredient type {}",
                    target.getClass().getName(),
                    error);
            minecraft.player.sendStatusMessage(new TextComponentString(
                    "Recipe Tree could not identify that JEI/HEI ingredient; see the log."), true);
            return false;
        }
        if (ingredient == null) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] JEI/HEI returned an unsupported hovered ingredient of type {}",
                    target.getClass().getName());
            minecraft.player.sendStatusMessage(new TextComponentString(
                    "Recipe Tree does not support that JEI/HEI ingredient type."), true);
            return false;
        }

        if (lastViewedTree == null) {
            lastViewedTree = RecipeTreeScreen.restoreLastViewed(bridge);
        }
        if (lastViewedTree != null) {
            minecraft.displayGuiScreen(lastViewedTree.screenForOpenedIngredient(ingredient));
            return true;
        }

        RecipeTreeScreen tree = new RecipeTreeScreen(bridge, ingredient);
        lastViewedTree = tree;
        minecraft.displayGuiScreen(tree.initialInputRecipeScreen());
        return true;
    }

    private RecipeTreeViewerBridge requireViewerBridge(Minecraft minecraft) {
        RecipeTreeViewerBridge bridge = JeiExportPlugin.getViewerBridge();
        if (bridge != null) return bridge;

        IJeiRuntime runtime = JeiExportPlugin.getRuntime();
        if (runtime == null) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] G was pressed before the JEI/HEI runtime became available");
            minecraft.player.sendStatusMessage(new TextComponentString(
                    "Recipe Tree is waiting for JEI/HEI to finish loading."), true);
        } else {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] G was pressed after JEI/HEI loaded, but the live viewer bridge "
                            + "is not ready");
            minecraft.player.sendStatusMessage(new TextComponentString(
                    "Recipe Tree viewer initialization failed; see the log."), true);
        }
        return null;
    }

    private Object findHoveredIngredient(Minecraft minecraft, GuiScreen screen) {
        IJeiRuntime runtime = JeiExportPlugin.getRuntime();
        if (runtime != null) {
            try {
                IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
                if (overlay != null) {
                    Object hovered = overlay.getIngredientUnderMouse();
                    if (!isEmptyItem(hovered)) return hovered;
                }
            } catch (RuntimeException error) {
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] JEI/HEI failed while resolving the ingredient-list overlay "
                                + "hover; Recipe Tree will try the recipe GUI and inventory",
                        error);
            }

            try {
                Object recipeHovered = findRecipesGuiHoveredIngredient(runtime.getRecipesGui());
                if (!isEmptyItem(recipeHovered)) return recipeHovered;
            } catch (RuntimeException error) {
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] JEI/HEI failed while exposing its recipe GUI; Recipe Tree "
                                + "will try the inventory instead",
                        error);
            }
        }

        if (screen instanceof GuiContainer) {
            Slot slot = ((GuiContainer) screen).getSlotUnderMouse();
            if (slot != null && slot.getHasStack()) {
                return normalizedHeldItem(slot.getStack());
            }
        }
        return null;
    }

    private Object findRecipesGuiHoveredIngredient(IRecipesGui recipesGui) {
        if (recipesGui == null) return null;

        Method method = recipesGuiHoveredIngredientMethod(recipesGui.getClass());
        if (method == null) return null;
        try {
            return method.invoke(recipesGui);
        } catch (IllegalAccessException error) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] HEI exposed IRecipesGui#getIngredientUnderMouse but it could "
                            + "not be accessed; hovered recipe ingredients cannot open Recipe Tree",
                    error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            FatalErrors.rethrowIfFatal(cause);
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] HEI failed while resolving the ingredient under the recipe-GUI "
                            + "mouse cursor; Recipe Tree was not opened",
                    cause);
        }
        return null;
    }

    private Method recipesGuiHoveredIngredientMethod(Class<?> recipesGuiClass) {
        if (recipesGuiClass != inspectedRecipesGuiClass) {
            inspectedRecipesGuiClass = recipesGuiClass;
            recipesGuiHoveredIngredientMethod = null;
            try {
                recipesGuiHoveredIngredientMethod = recipesGuiClass.getMethod(
                        "getIngredientUnderMouse");
            } catch (NoSuchMethodException error) {
                if (!missingRecipesGuiHoverMethodLogged) {
                    missingRecipesGuiHoverMethodLogged = true;
                    JeiExportMod.LOGGER.warn(
                            "[jeiexport] This JEI/HEI IRecipesGui implementation does not expose "
                                    + "getIngredientUnderMouse; G still works from the ingredient "
                                    + "overlay, inventory slots, and held items, but not directly "
                                    + "inside recipe layouts");
                }
            } catch (SecurityException error) {
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] Security policy blocked HEI recipe-GUI hover detection; G "
                                + "cannot open Recipe Tree directly from recipe layouts",
                        error);
            }
        }
        return recipesGuiHoveredIngredientMethod;
    }

    private boolean isTyping(GuiScreen screen) {
        if (screen != null && screen.isFocused()) return true;

        IJeiRuntime runtime = JeiExportPlugin.getRuntime();
        if (runtime == null) return false;
        try {
            IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
            return overlay != null && overlay.hasKeyboardFocus();
        } catch (RuntimeException error) {
            if (!ingredientOverlayFocusFailureLogged) {
                ingredientOverlayFocusFailureLogged = true;
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] Could not determine whether the JEI/HEI search field has "
                                + "keyboard focus; the G shortcut is disabled for this key event",
                        error);
            }
            // Safer than stealing a character from an unknown search/text field.
            return true;
        }
    }

    private void trackInventoryDiscoveries(Minecraft minecraft) {
        if (inventoryDiscoveryCooldown-- > 0) return;
        inventoryDiscoveryCooldown = INVENTORY_DISCOVERY_INTERVAL_TICKS;

        RecipeTreeViewerBridge bridge = JeiExportPlugin.getViewerBridge();
        if (bridge == null) return;

        Set<String> keys = new LinkedHashSet<String>();
        for (int index = 0; index < minecraft.player.inventory.getSizeInventory(); index++) {
            addDiscoveryKey(bridge, keys, minecraft.player.inventory.getStackInSlot(index));
        }
        addDiscoveryKey(bridge, keys, minecraft.player.inventory.getItemStack());

        int added = RecipeTreeProgress.get().discover(keys);
        if (added > 0) {
            JeiExportMod.LOGGER.debug(
                    "[jeiexport] Discovered {} Recipe Tree ingredient type(s) from inventory",
                    added);
        }
    }

    private void addDiscoveryKey(
            RecipeTreeViewerBridge bridge,
            Set<String> keys,
            ItemStack stack) {
        ItemStack normalized = normalizedHeldItem(stack);
        if (normalized == null) return;
        try {
            RecipeTreeViewerBridge.Ingredient ingredient = bridge.ingredient(normalized);
            if (ingredient != null && ingredient.getKey() != null) keys.add(ingredient.getKey());
        } catch (RuntimeException error) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Could not derive a Recipe Tree discovery identity for inventory "
                            + "item {}",
                    normalized,
                    error);
        }
    }

    private static ItemStack normalizedHeldItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static boolean isEmptyItem(Object value) {
        return value == null || value instanceof ItemStack && ((ItemStack) value).isEmpty();
    }

    private static boolean isOpenTreeKeyPress() {
        if (!Keyboard.getEventKeyState()) return false;
        int eventKey = Keyboard.getEventKey();
        if (eventKey == Keyboard.KEY_NONE) eventKey = Keyboard.getEventCharacter() + 256;
        return OPEN_RECIPE_TREE.isActiveAndMatches(eventKey);
    }

    private void resetRecipesGuiReflection() {
        inspectedRecipesGuiClass = null;
        recipesGuiHoveredIngredientMethod = null;
        missingRecipesGuiHoverMethodLogged = false;
        ingredientOverlayFocusFailureLogged = false;
    }
}
