package dev.veyno.aiPof.command;

import dev.veyno.aiPof.AiPof;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandHandler {
    private final AiPof plugin;

    public HelpCommand(AiPof plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        plugin.sendMessage(sender, "help-header");
        plugin.sendMessage(sender, "help-create");
        plugin.sendMessage(sender, "help-join");
        plugin.sendMessage(sender, "help-leave");
        plugin.sendMessage(sender, "help-start");
        plugin.sendMessage(sender, "help-status");
        plugin.sendMessage(sender, "help-list");
        plugin.sendMessage(sender, "help-cleanup");
        return true;
    }
}
