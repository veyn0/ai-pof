package dev.veyno.aiPof.command;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.service.RoundService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JoinRoundCommand implements CommandHandler {
    private final AiPof plugin;
    private final RoundService roundService;
    private final CommandService commandService;

    public JoinRoundCommand(AiPof plugin, RoundService roundService, CommandService commandService) {
        this.plugin = plugin;
        this.roundService = roundService;
        this.commandService = commandService;
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        if (!commandService.requirePlayer(sender, "only-players-join")) {
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, "join-usage");
            return true;
        }
        roundService.join((Player) sender, args[1]);
        return true;
    }
}
