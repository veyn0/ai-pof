package dev.veyno.aiPof;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.Map;

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

    public String messageTemplate(String key) {
        String path = "messages." + key;
        String template = getConfig().getString(path);
        if (template == null) {
            return "Missing message: " + path;
        }
        return template;
    }

    public Component message(String key, TagResolver... resolvers) {
        return message(key, Map.of(), resolvers);
    }

    public void sendMessage(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(message(key, Map.of(), resolvers));
    }

    public Component message(String key, Map<String, String> placeholders, TagResolver... resolvers) {
        String template = messageTemplate(key);
        String interpolated = applyPlaceholders(template, placeholders);
        return miniMessage.deserialize(interpolated, resolvers);
    }

    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders, TagResolver... resolvers) {
        sender.sendMessage(message(key, placeholders, resolvers));
    }

    private String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        String interpolated = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            interpolated = interpolated.replace("{" + entry.getKey() + "}", value);
        }
        return interpolated;
    }
}
