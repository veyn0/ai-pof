package dev.veyno.aiPof;

import java.util.Map;
import dev.veyno.aiPof.command.PofCommand;
import dev.veyno.aiPof.config.ConfigService;
import dev.veyno.aiPof.config.GameConfig;
import dev.veyno.aiPof.infrastructure.WorldService;
import dev.veyno.aiPof.service.ItemService;
import dev.veyno.aiPof.service.ProjectileService;
import dev.veyno.aiPof.service.RoundService;
import dev.veyno.aiPof.service.SpawnService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class AiPof extends JavaPlugin {
    private RoundService roundService;
    private ConfigService configService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        GameConfig gameConfig = GameConfig.load(this);
        configService = new ConfigService(gameConfig);
        WorldService worldService = new WorldService();
        SpawnService spawnService = new SpawnService(configService);
        ItemService itemService = new ItemService(configService);
        roundService = new RoundService(this, configService, worldService, spawnService, itemService);
        ProjectileService projectileService = new ProjectileService(configService);
        PofCommand command = new PofCommand(this, roundService);
        getCommand("pof").setExecutor(command);
        getCommand("pof").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(roundService, this);
        Bukkit.getPluginManager().registerEvents(projectileService, this);
    }

    @Override
    public void onDisable() {
        if (roundService != null) {
            roundService.shutdown();
        }
    }

    public RoundService getRoundService() {
        return roundService;
    }

    public ConfigService getConfigService() {
        return configService;
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
