package com.recipetree.jeiexport112;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;

final class ExportCommand extends CommandBase {
    private final ExportCoordinator coordinator;

    ExportCommand(ExportCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public String getName() {
        return "jeiexport";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jeiexport [output-directory]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 1) {
            throw new CommandException(getUsage(sender));
        }
        try {
            ExportRequest request = ExportRequest.fromCommand(args.length == 0 ? null : args[0],
                    Minecraft.getMinecraft());
            coordinator.enqueue(request, "client command");
            sender.sendMessage(new TextComponentString("[jeiexport] Export queued: " + request.output));
        } catch (IOException e) {
            throw new CommandException("Invalid export request: " + e.getMessage());
        }
    }
}
