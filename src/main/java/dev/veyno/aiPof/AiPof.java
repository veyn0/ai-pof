package dev.veyno.aiPof;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AiPof extends JavaPlugin {
    private RoundManager roundManager;

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
}
