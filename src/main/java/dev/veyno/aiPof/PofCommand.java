package dev.veyno.aiPof;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PofCommand implements CommandExecutor, TabCompleter {
    private final AiPof plugin;
    private final RoundManager roundManager;

    public PofCommand(AiPof plugin, RoundManager roundManager) {
        this.plugin = plugin;
        this.roundManager = roundManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (!sender.hasPermission("pof.admin")) {
                    plugin.sendMessage(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendMessage(sender, "create-usage");
                    return true;
                }
                try {
                    String id = args[1];
                    roundManager.createRound(id);
                    plugin.sendMessage(sender, "created", Map.of("id", id));
                } catch (IllegalArgumentException ex) {
                    plugin.sendMessage(sender, "error", Map.of("message", Objects.toString(ex.getMessage(), "")));
                } catch (IllegalStateException ex) {
                    plugin.sendMessage(sender, "error", Map.of("message", Objects.toString(ex.getMessage(), "")));
                }
            }
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, "only-players-join");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendMessage(sender, "join-usage");
                    return true;
                }
                roundManager.join(player, args[1]);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, "only-players-leave");
                    return true;
                }
                if (args.length >= 2) {
                    roundManager.leave(player, args[1]);
                } else {
                    roundManager.leave(player);
                }
            }
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, "only-players-start");
                    return true;
                }
                if (!sender.hasPermission("pof.admin")) {
                    plugin.sendMessage(sender, "start-no-permission");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendMessage(sender, "start-usage");
                    return true;
                }
                roundManager.forceStart(player, args[1]);
            }
            case "status" -> {
                if (args.length < 2) {
                    plugin.sendMessage(sender, "status-usage");
                    return true;
                }
                String id = args[1];
                var round = roundManager.getRound(id);
                if (round.isEmpty() || round.get().isEnded()) {
                    plugin.sendMessage(sender, "status-no-active");
                    return true;
                }
                Round roundInstance = round.get();
                String statusKey = roundInstance.isStarted() ? "status-running" : "status-waiting";
                String statusTemplate = plugin.messageTemplate(statusKey);
                plugin.sendMessage(sender, "status-line", Map.of("status", statusTemplate));
            }
            case "list" -> {
                List<String> ids = roundManager.getRoundIds();
                if (ids.isEmpty()) {
                    plugin.sendMessage(sender, "list-none");
                    return true;
                }
                plugin.sendMessage(sender, "list-header");
                for (String id : ids) {
                    var round = roundManager.getRound(id).orElse(null);
                    if (round == null) {
                        continue;
                    }
                    String statusKey = round.isStarted() ? "status-running" : "status-waiting";
                    String statusTemplate = plugin.messageTemplate(statusKey);
                    plugin.sendMessage(sender, "list-entry", Map.of("id", id, "status", statusTemplate));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.sendMessage(sender, "help-header");
        plugin.sendMessage(sender, "help-create");
        plugin.sendMessage(sender, "help-join");
        plugin.sendMessage(sender, "help-leave");
        plugin.sendMessage(sender, "help-start");
        plugin.sendMessage(sender, "help-status");
        plugin.sendMessage(sender, "help-list");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "join", "leave", "start", "status", "list");
        }
        if (args.length == 2 && Arrays.asList("join", "leave", "start", "status").contains(args[0].toLowerCase())) {
            return new ArrayList<>(roundManager.getRoundIds());
        }
        return List.of();
    }
}
