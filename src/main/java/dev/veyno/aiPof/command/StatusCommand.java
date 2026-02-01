package dev.veyno.aiPof.command;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.domain.Round;
import dev.veyno.aiPof.service.RoundService;
import java.util.Map;
import org.bukkit.command.CommandSender;

public class StatusCommand implements CommandHandler {
    private final AiPof plugin;
    private final RoundService roundService;

    public StatusCommand(AiPof plugin, RoundService roundService) {
        this.plugin = plugin;
        this.roundService = roundService;
    }

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, "status-usage");
            return true;
        }
        String id = args[1];
        var round = roundService.getRound(id);
        if (round.isEmpty() || round.get().isEnded()) {
            plugin.sendMessage(sender, "status-no-active");
            return true;
        }
        Round roundInstance = round.get();
        String statusKey = roundInstance.isRunning() ? "status-running" : "status-waiting";
        String statusTemplate = plugin.messageTemplate(statusKey);
        plugin.sendMessage(sender, "status-line", Map.of("status", statusTemplate));
        return true;
    }
}
