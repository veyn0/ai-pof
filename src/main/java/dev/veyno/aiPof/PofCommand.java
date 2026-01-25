package dev.veyno.aiPof;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
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
                    sender.sendMessage("§cKeine Berechtigung.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cBitte eine ID angeben: /pof create <id>");
                    return true;
                }
                try {
                    String id = args[1];
                    roundManager.createRound(id);
                    sender.sendMessage("§aNeue Runde erstellt: " + id + ". /pof join " + id + " zum Beitreten.");
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage("§c" + ex.getMessage());
                } catch (IllegalStateException ex) {
                    sender.sendMessage("§c" + ex.getMessage());
                }
            }
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cNur Spieler können beitreten.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cBitte eine ID angeben: /pof join <id>");
                    return true;
                }
                roundManager.join(player, args[1]);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cNur Spieler können die Runde verlassen.");
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
                    sender.sendMessage("§cNur Spieler können starten.");
                    return true;
                }
                if (!sender.hasPermission("pof.admin")) {
                    sender.sendMessage("§cKeine Berechtigung.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cBitte eine ID angeben: /pof start <id>");
                    return true;
                }
                roundManager.forceStart(player, args[1]);
            }
            case "status" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cBitte eine ID angeben: /pof status <id>");
                    return true;
                }
                String id = args[1];
                var round = roundManager.getRound(id);
                if (round.isEmpty() || round.get().isEnded()) {
                    sender.sendMessage("§eKeine aktive Runde mit dieser ID.");
                    return true;
                }
                Round roundInstance = round.get();
                sender.sendMessage("§eRunde: " + (roundInstance.isStarted() ? "läuft" : "wartet"));
            }
            case "list" -> {
                List<String> ids = roundManager.getRoundIds();
                if (ids.isEmpty()) {
                    sender.sendMessage("§eKeine aktiven Runden.");
                    return true;
                }
                sender.sendMessage("§6Aktive Runden:");
                for (String id : ids) {
                    var round = roundManager.getRound(id).orElse(null);
                    if (round == null) {
                        continue;
                    }
                    sender.sendMessage("§e- " + id + " §7(" + (round.isStarted() ? "läuft" : "wartet") + ")");
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6Pillars of Fortune Befehle:");
        sender.sendMessage("§e/pof create <id> §7- Neue Runde erstellen");
        sender.sendMessage("§e/pof join <id> §7- Wartebereich betreten");
        sender.sendMessage("§e/pof leave [id] §7- Runde verlassen");
        sender.sendMessage("§e/pof start <id> §7- Runde sofort starten");
        sender.sendMessage("§e/pof status <id> §7- Rundenstatus anzeigen");
        sender.sendMessage("§e/pof list §7- Aktive Runden anzeigen");
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
