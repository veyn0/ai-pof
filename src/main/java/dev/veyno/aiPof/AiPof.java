package dev.veyno.aiPof;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class AiPof extends JavaPlugin {
    private RoundManager roundManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        roundManager = new RoundManager(this);
        PofCommand command = new PofCommand(this, roundManager);
        getCommand("pof").setExecutor(command);
        getCommand("pof").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(roundManager, this);
    }

    @Override
    public void onDisable() {
        if (roundManager != null) {
            roundManager.shutdown();
        }
    }

    public RoundManager getRoundManager() {
        return roundManager;
    }

    public Component message(String key, TagResolver... resolvers) {
        String path = "messages." + key;
        String template = getConfig().getString(path);
        if (template == null) {
            return Component.text("Missing message: " + path);
        }
        return miniMessage.deserialize(template, resolvers);
    }

    public void sendMessage(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(message(key, resolvers));
    }
}
