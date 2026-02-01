package dev.veyno.aiPof.config;

import org.bukkit.plugin.java.JavaPlugin;

public class ConfigService {
    private final JavaPlugin plugin;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int getMinPlayers() {
        return plugin.getConfig().getInt("min-players", 2);
    }

    public int getStartCountdownSeconds() {
        return plugin.getConfig().getInt("start-countdown-seconds", 10);
    }

    public int getPillarHeight() {
        return plugin.getConfig().getInt("pillar-height", 64);
    }

    public int getPillarSpacing() {
        return plugin.getConfig().getInt("pillar-spacing", 6);
    }

    public int getItemIntervalSeconds() {
        return plugin.getConfig().getInt("item-interval-seconds", 10);
    }

    public boolean isItemCountEnabled() {
        return plugin.getConfig().getBoolean("item-count.enabled", true);
    }

    public int getItemCountMax() {
        return plugin.getConfig().getInt("item-count.max", 1);
    }

    public double getItemCountBaseWeight() {
        return plugin.getConfig().getDouble("item-count.base-weight", 1.0);
    }

    public int getWaitingBoxRadius() {
        return plugin.getConfig().getInt("waiting-box.radius", 6);
    }

    public int getWaitingBoxHeight() {
        return plugin.getConfig().getInt("waiting-box.height", 3);
    }

    public int getWaitingBoxYOffset() {
        return plugin.getConfig().getInt("waiting-box.y-offset", 4);
    }

    public int getWaitingBoxCenterX() {
        return plugin.getConfig().getInt("waiting-box.center-x", 0);
    }

    public int getWaitingBoxCenterZ() {
        return plugin.getConfig().getInt("waiting-box.center-z", 0);
    }

    public int getStartBoxRadius() {
        return plugin.getConfig().getInt("start-box.radius", 1);
    }

    public int getStartBoxHeight() {
        return plugin.getConfig().getInt("start-box.height", 2);
    }

    public int getStartBoxYOffset() {
        return plugin.getConfig().getInt("start-box.y-offset", 2);
    }
}
