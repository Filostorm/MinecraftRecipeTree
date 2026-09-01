package com.recipetree.jeiexport112;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Creates, grants, and opens the vanilla-backed Recipe Tree guide book. */
final class RecipeTreeBook {
    static final RecipeTreeBook INSTANCE = new RecipeTreeBook();

    private static final String MARKER_KEY = "jeiexportRecipeTreeBook";

    private RecipeTreeBook() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onCreateSpawnPosition(WorldEvent.CreateSpawnPosition event) {
        World world = event.getWorld();
        if (!isIntegratedOverworld(world)) return;

        armNewWorld(world, "spawn creation");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldLoaded(WorldEvent.Load event) {
        World world = event.getWorld();
        if (!isIntegratedOverworld(world)
                || world.getWorldInfo().getTerrainType() != WorldType.DEBUG_ALL_BLOCK_STATES
                || world.getWorldInfo().getWorldTotalTime() != 0L) return;

        // Vanilla bypasses CreateSpawnPosition for debug worlds. A zero total time at their first
        // load is the narrow equivalent signal, and existing debug saves are deliberately ignored.
        armNewWorld(world, "initial debug-world load");
    }

    private void armNewWorld(World world, String source) {
        if (!JeiExportMod.CONFIGURATION.spawnBookInNewWorlds()) return;

        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            JeiExportMod.LOGGER.error(
                    "[jeiexport] Could not arm the new-world Recipe Tree book grant because "
                            + "the overworld has no persistent map storage");
            return;
        }

        RecipeTreeBookGrantData data = load(storage);
        if (data == null) {
            data = new RecipeTreeBookGrantData(RecipeTreeBookGrantData.DATA_ID);
            storage.setData(RecipeTreeBookGrantData.DATA_ID, data);
        }
        if (data.isGranted() || data.isPending()) return;
        data.armForNewWorld();
        JeiExportMod.LOGGER.info(
                "[jeiexport] Armed the one-time Recipe Tree book grant from {}",
                source);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        World world = player == null ? null : player.world;
        if (!isIntegratedOverworld(world)
                || !JeiExportMod.CONFIGURATION.spawnBookInNewWorlds()) return;

        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            JeiExportMod.LOGGER.error(
                    "[jeiexport] Could not check the Recipe Tree book grant because the "
                            + "overworld has no persistent map storage");
            return;
        }
        RecipeTreeBookGrantData data = load(storage);
        if (data == null || !data.isPending()) return;

        if (inventoryContainsBook(player)) {
            data.markGranted();
            JeiExportMod.LOGGER.info(
                    "[jeiexport] Completed the pending Recipe Tree book grant because the "
                            + "player already has the marked book");
            return;
        }

        ItemStack book = createBook();
        if (!player.inventory.addItemStackToInventory(book)) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] The new-world Recipe Tree book could not fit in {}'s "
                            + "inventory; the grant remains pending for the next login",
                    player.getName());
            return;
        }

        data.markGranted();
        player.inventoryContainer.detectAndSendChanges();
        JeiExportMod.LOGGER.info(
                "[jeiexport] Granted the one-time Recipe Tree book to {} in the new world",
                player.getName());
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        if (!isRecipeTreeBook(held)) return;

        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getWorld().isRemote) {
            RecipeTreeClient.INSTANCE.openFromBook(held);
        }
    }

    static ItemStack createBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(MARKER_KEY, true);
        tag.setString("title", "Recipe Tree Book");
        tag.setString("author", "Filostorm");
        tag.setInteger("generation", 0);
        tag.setBoolean("resolved", true);

        NBTTagList pages = new NBTTagList();
        pages.appendTag(page(
                "Recipe Tree\n\nRight-click this book to reopen your latest recipe tree.\n\n"
                        + "If you have not made a tree yet, hover an item in JEI/HEI or your "
                        + "inventory and use the Open Recipe Tree keybind (G by default)."));
        pages.appendTag(page(
                "Planner controls\n\nPan: drag\nZoom: mouse wheel\n\n"
                        + "Hover nodes to inspect recipes. Use the on-screen mouse hints to "
                        + "choose recipes, outputs, and item alternatives."));
        tag.setTag("pages", pages);
        book.setTagCompound(tag);
        return book;
    }

    static boolean isRecipeTreeBook(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() == Items.WRITTEN_BOOK
                && hasMarker(stack.getTagCompound());
    }

    static boolean hasMarker(NBTTagCompound tag) {
        return tag != null && tag.getBoolean(MARKER_KEY);
    }

    private static NBTTagString page(String text) {
        ITextComponent component = new TextComponentString(text);
        return new NBTTagString(ITextComponent.Serializer.componentToJson(component));
    }

    private static RecipeTreeBookGrantData load(MapStorage storage) {
        return (RecipeTreeBookGrantData) storage.getOrLoadData(
                RecipeTreeBookGrantData.class,
                RecipeTreeBookGrantData.DATA_ID);
    }

    private static boolean inventoryContainsBook(EntityPlayer player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            if (isRecipeTreeBook(player.inventory.getStackInSlot(slot))) return true;
        }
        return false;
    }

    private static boolean isIntegratedOverworld(World world) {
        if (world == null || world.isRemote || world.provider.getDimension() != 0) return false;
        MinecraftServer server = world.getMinecraftServer();
        return server != null && !server.isDedicatedServer();
    }
}
