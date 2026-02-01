package dev.veyno.aiPof.command;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.service.RoundService;
import java.util.Map;
import org.bukkit.command.CommandSender;

public class CreateRoundCommand implements CommandHandler {
    private final AiPof plugin;
    private final RoundService roundService;
    private final CommandService commandService;

    public CreateRoundCommand(AiPof plugin, RoundService roundService, CommandService commandService) {
        this.plugin = plugin;
        this.roundService = roundService;
        this.commandService = commandService;
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        if (!commandService.requirePermission(sender, "pof.admin", "no-permission")) {
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, "create-usage");
            return true;
        }
        try {
            String id = args[1];
            roundService.createRound(id);
            plugin.sendMessage(sender, "created", Map.of("id", id));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            commandService.sendError(sender, "error", ex);
        }
        return true;
    }
}
