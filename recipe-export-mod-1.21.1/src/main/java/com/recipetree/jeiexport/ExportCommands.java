package com.recipetree.jeiexport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;

/**
 * /jeiexport all [iconScale]     - items + recipes + mobs
 * /jeiexport items [iconScale]   - just the ingredient catalog + icons
 * /jeiexport recipes [iconScale] - recipes (includes the item catalog, recipes reference it)
 * /jeiexport mobs                - renders of every living entity
 * /jeiexport status | cancel
 */
public final class ExportCommands {
    private ExportCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jeiexport")
                .then(startLiteral("all", EnumSet.allOf(ExportJob.Phase.class)))
                .then(startLiteral("items", EnumSet.of(ExportJob.Phase.ITEMS)))
                .then(startLiteral("recipes", EnumSet.of(
                        ExportJob.Phase.ITEMS, ExportJob.Phase.RECIPES, ExportJob.Phase.EMC)))
                .then(startLiteral("mobs", EnumSet.of(ExportJob.Phase.MOBS)))
                .then(startLiteral("blockdrops", EnumSet.of(ExportJob.Phase.BLOCK_DROPS)))
                .then(startLiteral("trades", EnumSet.of(ExportJob.Phase.ITEMS, ExportJob.Phase.TRADES)))
                .then(Commands.literal("status").executes(ctx -> {
                    ExportJob job = ExportJob.current();
                    if (job == null) {
                        ctx.getSource().sendSuccess(() -> prefixed("No export is running.", ChatFormatting.GRAY), false);
                    } else {
                        ctx.getSource().sendSuccess(() -> prefixed("Running: " + job.statusLine(), ChatFormatting.AQUA), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(ctx -> {
                    if (ExportJob.cancel()) {
                        ctx.getSource().sendSuccess(() -> prefixed("Export cancelled.", ChatFormatting.YELLOW), false);
                        return 1;
                    }
                    ctx.getSource().sendFailure(prefixed("No export is running.", ChatFormatting.RED));
                    return 0;
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> startLiteral(String name, EnumSet<ExportJob.Phase> phases) {
        return Commands.literal(name)
                .executes(ctx -> start(
                        ctx.getSource(), phases, ExportManifestContract.DEFAULT_ICON_SCALE))
                .then(Commands.argument("iconScale", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> start(ctx.getSource(), phases, IntegerArgumentType.getInteger(ctx, "iconScale"))));
    }

    private static int start(CommandSourceStack source, EnumSet<ExportJob.Phase> phases, int iconScale) {
        if (ExportJob.current() != null) {
            source.sendFailure(prefixed("An export is already running. Use /jeiexport cancel first.", ChatFormatting.RED));
            return 0;
        }

        boolean needsJei = phases.contains(ExportJob.Phase.ITEMS)
                || phases.contains(ExportJob.Phase.RECIPES)
                || phases.contains(ExportJob.Phase.EMC);
        IJeiRuntime runtime = JeiExportPlugin.runtime();
        if (needsJei && runtime == null) {
            source.sendFailure(prefixed("JEI runtime is not available. Is JEI installed and has the world finished loading?", ChatFormatting.RED));
            return 0;
        }

        try {
            ExportJob.start(runtime, phases, iconScale);
        } catch (Exception e) {
            JeiExportMod.LOGGER.error("Failed to start export", e);
            source.sendFailure(prefixed("Failed to start export: " + e.getMessage(), ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> prefixed("Export started (" + phases + ", icon scale " + iconScale
                + "). Writing to <gameDir>/jei-exports. The game will run slower until it finishes.", ChatFormatting.GREEN), false);
        return 1;
    }

    private static Component prefixed(String message, ChatFormatting color) {
        return Component.literal("[JEI Export] " + message).withStyle(color);
    }
}
