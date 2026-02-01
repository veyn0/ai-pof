package dev.veyno.aiPof.command;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.service.RoundService;
import java.util.Map;
import org.bukkit.command.CommandSender;

public class CleanupCommand implements CommandHandler {
    private final AiPof plugin;
    private final RoundService roundService;
    private final CommandService commandService;

    public CleanupCommand(AiPof plugin, RoundService roundService, CommandService commandService) {
        this.plugin = plugin;
        this.roundService = roundService;
        this.commandService = commandService;
    }

    @Override
    public String getName() {
        return "cleanup";
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        if (!commandService.requirePermission(sender, "pof.admin", "no-permission")) {
            return true;
        }
        int deleted = roundService.cleanupUnusedWorldFolders();
        plugin.sendMessage(sender, "cleanup-done", Map.of("count", Integer.toString(deleted)));
        return true;
    }
}
