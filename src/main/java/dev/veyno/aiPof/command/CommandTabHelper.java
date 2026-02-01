package dev.veyno.aiPof.command;

import dev.veyno.aiPof.service.RoundService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;

public class CommandTabHelper {
    private static final List<String> ROUND_ID_COMMANDS = List.of("join", "leave", "start", "status");

    private final RoundService roundService;

    public CommandTabHelper(RoundService roundService) {
        this.roundService = roundService;
    }

    public List<String> completeRoundIds(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return List.of();
        }
        if (!ROUND_ID_COMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return List.of();
        }
        return new ArrayList<>(roundService.getRoundIds());
    }
}
