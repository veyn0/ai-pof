package dev.veyno.aiPof;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
                try {
                    roundManager.createRound();
                    sender.sendMessage("§aNeue Runde erstellt. /pof join zum Beitreten.");
                } catch (IllegalStateException ex) {
                    sender.sendMessage("§c" + ex.getMessage());
                }
            }
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cNur Spieler können beitreten.");
                    return true;
                }
                roundManager.join(player);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cNur Spieler können die Runde verlassen.");
                    return true;
                }
                roundManager.leave(player);
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
                roundManager.forceStart(player);
            }
            case "status" -> {
                Optional<Round> current = roundManager.getCurrentRound();
                if (current.isEmpty()) {
                    sender.sendMessage("§eKeine aktive Runde.");
                    return true;
                }
                Round round = current.get();
                sender.sendMessage("§eRunde: " + (round.isStarted() ? "läuft" : "wartet"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6Pillars of Fortune Befehle:");
        sender.sendMessage("§e/pof create §7- Neue Runde erstellen");
        sender.sendMessage("§e/pof join §7- Wartebereich betreten");
        sender.sendMessage("§e/pof leave §7- Runde verlassen");
        sender.sendMessage("§e/pof start §7- Runde sofort starten");
        sender.sendMessage("§e/pof status §7- Rundenstatus anzeigen");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "join", "leave", "start", "status");
        }
        return List.of();
    }
}
