package dev.veyno.aiPof.command;

import java.util.List;
import org.bukkit.command.CommandSender;

public class TabCompleteRoundCommand implements CommandHandler {
    private final String name;
    private final CommandHandler delegate;
    private final CommandTabHelper tabHelper;

    public TabCompleteRoundCommand(String name, CommandHandler delegate, CommandTabHelper tabHelper) {
        this.name = name;
        this.delegate = delegate;
        this.tabHelper = tabHelper;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean handle(CommandSender sender, String[] args) {
        return delegate.handle(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = tabHelper.completeRoundIds(sender, args);
        if (!completions.isEmpty()) {
            return completions;
        }
        return delegate.tabComplete(sender, args);
    }
}
