package dev.veyno.aiPof.command;

import java.util.List;
import org.bukkit.command.CommandSender;

public interface CommandHandler {
    String getName();

    boolean handle(CommandSender sender, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
