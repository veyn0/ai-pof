package dev.veyno.aiPof;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RoundManager implements Listener {
    private final AiPof plugin;
    private Round currentRound;

    public RoundManager(AiPof plugin) {
        this.plugin = plugin;
    }

    public Optional<Round> getCurrentRound() {
        return Optional.ofNullable(currentRound);
    }

    public Round createRound() {
        if (currentRound != null && !currentRound.isEnded()) {
            throw new IllegalStateException("Eine Runde läuft bereits.");
        }
        currentRound = new Round(plugin);
        currentRound.initializeWorld();
        return currentRound;
    }

    public void shutdown() {
        if (currentRound != null) {
            currentRound.endRound(null);
        }
    }

    public void forceStart(Player player) {
        if (currentRound == null) {
            player.sendMessage("§cKeine aktive Runde. Nutze /pof create.");
            return;
        }
        currentRound.forceStart(player);
    }

    public void join(Player player) {
        if (currentRound == null) {
            player.sendMessage("§cKeine aktive Runde. Nutze /pof create.");
            return;
        }
        currentRound.addPlayer(player);
    }

    public void leave(Player player) {
        if (currentRound == null) {
            player.sendMessage("§cDu bist in keiner Runde.");
            return;
        }
        currentRound.removePlayer(player, true);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (currentRound == null) {
            return;
        }
        currentRound.handleDeath(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (currentRound == null) {
            return;
        }
        if (currentRound.isParticipant(event.getPlayer().getUniqueId()) && currentRound.isStarted()) {
            World mainWorld = Bukkit.getWorlds().getFirst();
            event.setRespawnLocation(mainWorld.getSpawnLocation());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentRound == null) {
            return;
        }
        currentRound.removePlayer(event.getPlayer(), false);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (currentRound == null || !currentRound.isStarted()) {
            return;
        }
        Player player = event.getPlayer();
        if (!currentRound.isParticipant(player.getUniqueId())) {
            return;
        }
        if (player.getWorld().equals(currentRound.getWorld()) && player.getLocation().getY() < 0) {
            player.setHealth(0.0);
        }
    }
}
