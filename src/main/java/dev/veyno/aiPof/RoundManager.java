package dev.veyno.aiPof;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RoundManager implements Listener {
    private final AiPof plugin;
    private final Map<String, Round> rounds = new HashMap<>();
    private final Map<UUID, String> playerRounds = new HashMap<>();
    private final Map<String, Set<UUID>> pendingParticipants = new HashMap<>();
    private final Map<String, org.bukkit.scheduler.BukkitTask> restartTasks = new HashMap<>();

    public RoundManager(AiPof plugin) {
        this.plugin = plugin;
    }

    public Optional<Round> getRound(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rounds.get(normalizeId(id)));
    }

    public List<String> getRoundIds() {
        return rounds.keySet().stream().sorted().toList();
    }

    public Round createRound(String id) {
        String roundId = requireId(id);
        Round existing = rounds.get(roundId);
        if (existing != null && !existing.isEnded()) {
            throw new IllegalStateException("Eine Runde mit dieser ID läuft bereits.");
        }
        rounds.remove(roundId);
        Round round = new Round(plugin, this::handleRoundEnded);
        round.initializeWorld();
        rounds.put(roundId, round);
        return round;
    }

    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
        for (Round round : List.copyOf(rounds.values())) {
            round.endRound(null);
        }
        rounds.clear();
        playerRounds.clear();
        pendingParticipants.clear();
        restartTasks.clear();
    }

    public void forceStart(Player player, String id) {
        Round round = getActiveRound(id, player);
        if (round == null) {
            return;
        }
        round.forceStart(player);
    }

    public void join(Player player, String id) {
        Round round = getActiveRound(id, player);
        if (round == null) {
            return;
        }
        String roundId = normalizeId(id);
        String existingId = playerRounds.get(player.getUniqueId());
        if (existingId != null && !existingId.equals(roundId)) {
            player.sendMessage("§cDu bist bereits in Runde " + existingId + ".");
            return;
        }
        round.addPlayer(player);
        playerRounds.put(player.getUniqueId(), roundId);
    }

    public int cleanupUnusedWorldFolders() {
        File container = Bukkit.getWorldContainer();
        File[] folders = container.listFiles(File::isDirectory);
        if (folders == null) {
            return 0;
        }
        Set<String> activeWorlds = rounds.values().stream()
            .map(Round::getWorld)
            .filter(Objects::nonNull)
            .map(World::getName)
            .collect(Collectors.toSet());
        Set<String> loadedWorlds = Bukkit.getWorlds().stream()
            .map(World::getName)
            .collect(Collectors.toSet());
        int deleted = 0;
        for (File folder : folders) {
            String name = folder.getName();
            if (!name.startsWith(Round.WORLD_PREFIX)) {
                continue;
            }
            if (activeWorlds.contains(name) || loadedWorlds.contains(name)) {
                continue;
            }
            deleteWorldFolder(folder);
            deleted++;
        }
        return deleted;
    }

    public void leave(Player player) {
        String roundId = playerRounds.get(player.getUniqueId());
        if (roundId == null) {
            player.sendMessage("§cDu bist in keiner Runde.");
            return;
        }
        leave(player, roundId);
    }

    public void leave(Player player, String id) {
        String roundId = normalizeId(id);
        Round round = rounds.get(roundId);
        if (round == null || round.isEnded()) {
            if (removePendingParticipant(player, roundId, true)) {
                return;
            }
            playerRounds.remove(player.getUniqueId());
            player.sendMessage("§cDiese Runde existiert nicht mehr.");
            return;
        }
        round.removePlayer(player, true);
        playerRounds.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Round round = getPlayerRound(event.getEntity());
        if (round == null) {
            return;
        }
        if (!round.isStarted() && round.isParticipant(event.getEntity().getUniqueId())) {
            return;
        }
        round.handleDeath(event.getEntity());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Round round = getPlayerRound(player);
        if (round == null) {
            return;
        }
        if (!round.isStarted() && round.isParticipant(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFireTicks(0);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Round round = getPlayerRound(event.getPlayer());
        if (round == null) {
            return;
        }
        if (round.isParticipant(event.getPlayer().getUniqueId()) && round.isStarted()) {
            if (!round.isAlive(event.getPlayer().getUniqueId())) {
                event.setRespawnLocation(round.getSpectatorSpawn());
                Bukkit.getScheduler().runTask(plugin, () -> round.applySpectatorSettings(event.getPlayer()));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Round round = getPlayerRound(event.getPlayer());
        if (round != null) {
            round.removePlayer(event.getPlayer(), false);
            playerRounds.remove(event.getPlayer().getUniqueId());
            return;
        }
        removePendingParticipant(event.getPlayer(), null, false);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Round round = getPlayerRound(event.getPlayer());
        if (round == null || !round.isStarted()) {
            return;
        }
        Player player = event.getPlayer();
        if (!round.isParticipant(player.getUniqueId())) {
            return;
        }
        if (player.getWorld().equals(round.getWorld()) && player.getLocation().getY() < 0) {
            player.setHealth(0.0);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Round round = findRoundByWorld(event.getBlock().getWorld());
        if (round == null) {
            return;
        }
        if (round.isWaitingBoxBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Round round = findRoundByWorld(event.getBlock().getWorld());
        if (round == null) {
            return;
        }
        if (round.isWaitingBoxBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private void handleRoundEnded(Round round) {
        String roundId = findRoundId(round);
        if (roundId == null) {
            return;
        }
        rounds.remove(roundId);
        Set<UUID> participants = round.getParticipants();
        Set<UUID> remaining = participants.stream()
            .filter(uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                return player != null && player.isOnline();
            })
            .collect(Collectors.toSet());
        for (UUID uuid : participants) {
            if (!remaining.contains(uuid)) {
                playerRounds.remove(uuid);
            }
        }
        if (remaining.isEmpty()) {
            return;
        }
        pendingParticipants.put(roundId, remaining);
        int cooldownSeconds = plugin.getConfig().getInt("round-restart-cooldown-seconds", 10);
        for (UUID uuid : remaining) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.sendMessage(player, "round-restart", Map.of("seconds", Integer.toString(cooldownSeconds)));
            }
        }
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> restartRound(roundId), cooldownSeconds * 20L);
        restartTasks.put(roundId, task);
    }

    private void restartRound(String roundId) {
        Set<UUID> remaining = pendingParticipants.remove(roundId);
        restartTasks.remove(roundId);
        if (remaining == null || remaining.isEmpty()) {
            return;
        }
        Round newRound = createRound(roundId);
        for (UUID uuid : remaining) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                newRound.addPlayer(player);
                playerRounds.put(uuid, roundId);
            } else {
                playerRounds.remove(uuid);
            }
        }
    }

    private Round getPlayerRound(Player player) {
        String roundId = playerRounds.get(player.getUniqueId());
        if (roundId == null) {
            return null;
        }
        return rounds.get(roundId);
    }

    private Round getActiveRound(String id, Player player) {
        String roundId = normalizeId(id);
        Round round = rounds.get(roundId);
        if (round == null || round.isEnded()) {
            player.sendMessage("§cKeine aktive Runde mit dieser ID. Nutze /pof create <id>.");
            return null;
        }
        return round;
    }

    private boolean removePendingParticipant(Player player, String roundId, boolean notify) {
        String id = roundId != null ? roundId : playerRounds.get(player.getUniqueId());
        if (id == null) {
            return false;
        }
        Set<UUID> pending = pendingParticipants.get(id);
        if (pending == null || !pending.remove(player.getUniqueId())) {
            return false;
        }
        playerRounds.remove(player.getUniqueId());
        if (pending.isEmpty()) {
            pendingParticipants.remove(id);
            org.bukkit.scheduler.BukkitTask task = restartTasks.remove(id);
            if (task != null) {
                task.cancel();
            }
        }
        if (notify) {
            plugin.sendMessage(player, "left");
        }
        return true;
    }

    private String requireId(String id) {
        String roundId = normalizeId(id);
        if (roundId.isBlank()) {
            throw new IllegalArgumentException("ID darf nicht leer sein.");
        }
        return roundId;
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase();
    }

    private String findRoundId(Round round) {
        for (Map.Entry<String, Round> entry : rounds.entrySet()) {
            if (entry.getValue() == round) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Round findRoundByWorld(World world) {
        if (world == null) {
            return null;
        }
        for (Round round : rounds.values()) {
            if (world.equals(round.getWorld())) {
                return round;
            }
        }
        return null;
    }

    private void deleteWorldFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        try {
            Files.walk(folder.toPath())
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }
}
