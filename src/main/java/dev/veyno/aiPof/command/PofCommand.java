package dev.veyno.aiPof.command;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class PofCommand implements CommandExecutor, TabCompleter {
    private final Map<String, CommandHandler> handlers;

    public PofCommand(List<CommandHandler> handlers) {
        Map<String, CommandHandler> handlerMap = new HashMap<>();
        for (CommandHandler handler : handlers) {
            handlerMap.put(handler.getName(), handler);
        }
        this.handlers = Map.copyOf(handlerMap);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        CommandHandler handler = handlers.get(sub);
        if (handler == null) {
            sendHelp(sender);
            return true;
        }
        return handler.handle(sender, args);
    }

    private void sendHelp(CommandSender sender) {
        CommandHandler helpHandler = handlers.get("help");
        if (helpHandler != null) {
            helpHandler.handle(sender, new String[] {"help"});
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return handlers.keySet().stream()
                    .filter(key -> !key.equals("help"))
                    .sorted()
                    .toList();
        }
        CommandHandler handler = handlers.get(args[0].toLowerCase(Locale.ROOT));
        if (handler == null) {
            return List.of();
        }
        return handler.tabComplete(sender, args);
    }
}
