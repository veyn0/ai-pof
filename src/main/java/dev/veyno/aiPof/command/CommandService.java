package dev.veyno.aiPof.command;

import dev.veyno.aiPof.AiPof;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandService {
    private final AiPof plugin;

    public CommandService(AiPof plugin) {
        this.plugin = plugin;
    }

    public boolean requirePlayer(CommandSender sender, String messageKey) {
        if (sender instanceof Player) {
            return true;
        }
        plugin.sendMessage(sender, messageKey);
        return false;
    }

    public boolean requirePermission(CommandSender sender, String permission, String messageKey) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.sendMessage(sender, messageKey);
        return false;
    }

    public void sendError(CommandSender sender, String messageKey, Exception exception) {
        String message = Optional.ofNullable(exception)
                .map(Throwable::getMessage)
                .orElse("");
        plugin.sendMessage(sender, messageKey, Map.of("message", Objects.toString(message, "")));
    }
}
