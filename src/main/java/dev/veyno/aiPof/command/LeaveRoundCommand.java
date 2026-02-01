package dev.veyno.aiPof.command;

import dev.veyno.aiPof.service.RoundService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LeaveRoundCommand implements CommandHandler {
    private final RoundService roundService;
    private final CommandService commandService;

    public LeaveRoundCommand(RoundService roundService, CommandService commandService) {
        this.roundService = roundService;
        this.commandService = commandService;
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        if (!commandService.requirePlayer(sender, "only-players-leave")) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 2) {
            roundService.leave(player, args[1]);
        } else {
            roundService.leave(player);
        }
        return true;
    }
}
