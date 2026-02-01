package dev.veyno.aiPof.command;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.service.RoundService;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;

public class ListRoundsCommand implements CommandHandler {
    private final AiPof plugin;
    private final RoundService roundService;

    public ListRoundsCommand(AiPof plugin, RoundService roundService) {
        this.plugin = plugin;
        this.roundService = roundService;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        List<String> ids = roundService.getRoundIds();
        if (ids.isEmpty()) {
            plugin.sendMessage(sender, "list-none");
            return true;
        }
        plugin.sendMessage(sender, "list-header");
        for (String id : ids) {
            var round = roundService.getRound(id).orElse(null);
            if (round == null) {
                continue;
            }
            String statusKey = round.isRunning() ? "status-running" : "status-waiting";
            String statusTemplate = plugin.messageTemplate(statusKey);
            plugin.sendMessage(sender, "list-entry", Map.of("id", id, "status", statusTemplate));
        }
        return true;
    }
}
