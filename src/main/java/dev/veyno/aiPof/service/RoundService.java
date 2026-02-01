package dev.veyno.aiPof.service;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.config.ConfigService;
import dev.veyno.aiPof.domain.Round;
import dev.veyno.aiPof.domain.RoundId;
import dev.veyno.aiPof.infrastructure.WorldService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class RoundService implements Listener {
    private static final int RESTART_DELAY_SECONDS = 5;

    private final AiPof plugin;
    private final ConfigService config;
    private final WorldService worldService;
    private final SpawnService spawnService;
    private final ItemService itemService;
    private final Map<String, Round> rounds = new HashMap<>();
    private final Map<UUID, String> playerRounds = new HashMap<>();
    private final Map<String, Set<UUID>> pendingParticipants = new HashMap<>();
    private final Map<String, BukkitTask> restartTasks = new HashMap<>();
    private final Map<String, Round> endedRounds = new HashMap<>();
    private final Map<String, Integer> roundCounters = new HashMap<>();
    private final Map<String, RoundTasks> roundTasks = new HashMap<>();

    public RoundService(AiPof plugin, ConfigService config, WorldService worldService, SpawnService spawnService, ItemService itemService) {
        this.plugin = plugin;
        this.config = config;
        this.worldService = worldService;
        this.spawnService = spawnService;
        this.itemService = itemService;
    }

    public Optional<Round> getRound(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rounds.get(RoundId.normalize(id)));
    }

    public List<String> getRoundIds() {
        return rounds.keySet().stream().sorted().toList();
    }

    public Round createRound(String id) {
        RoundId roundId = RoundId.fromRaw(id);
        Round existing = rounds.get(roundId.value());
        if (existing != null && !existing.isEnded()) {
            throw new IllegalStateException("Eine Runde mit dieser ID läuft bereits.");
        }
        rounds.remove(roundId.value());
        Round round = new Round(Round.WORLD_PREFIX + System.currentTimeMillis());
        round.setWorld(worldService.createWorld(round.getWorldName()));
        rounds.put(roundId.value(), round);
        registerRoundId(roundId);
        return round;
    }

    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
        for (Round round : List.copyOf(rounds.values())) {
            endRoundImmediate(round, null);
        }
        rounds.clear();
        playerRounds.clear();
        pendingParticipants.clear();
        restartTasks.clear();
        endedRounds.clear();
        roundCounters.clear();
        roundTasks.clear();
    }

    public void forceStart(Player player, String id) {
        Round round = getActiveRound(id, player);
        if (round == null) {
            return;
        }
        if (round.isStarted()) {
            plugin.sendMessage(player, "round-already-started");
            return;
        }
        if (round.getParticipants().isEmpty()) {
            plugin.sendMessage(player, "no-players-waiting");
            return;
        }
        startRound(round);
    }

    public void join(Player player, String id) {
        Round round = getActiveRound(id, player);
        if (round == null) {
            return;
        }
        String roundId = RoundId.normalize(id);
        String existingId = playerRounds.get(player.getUniqueId());
        if (existingId != null && !existingId.equals(roundId)) {
            player.sendMessage("§cDu bist bereits in Runde " + existingId + ".");
            return;
        }
        addPlayer(round, player);
        playerRounds.put(player.getUniqueId(), roundId);
    }

    public int cleanupUnusedWorldFolders() {
        Set<World> activeWorlds = rounds.values().stream()
            .map(Round::getWorld)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return worldService.cleanupUnusedWorldFolders(activeWorlds);
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
        String roundId = RoundId.normalize(id);
        Round round = rounds.get(roundId);
        if (round == null || round.isEnded()) {
            if (removePendingParticipant(player, roundId, true)) {
                return;
            }
            playerRounds.remove(player.getUniqueId());
            player.sendMessage("§cDiese Runde existiert nicht mehr.");
            return;
        }
        removePlayer(round, player, true);
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
        handleDeath(round, event.getEntity());
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
                Location spectatorSpawn = spawnService.getSpectatorSpawn(round);
                if (spectatorSpawn != null) {
                    event.setRespawnLocation(spectatorSpawn);
                }
                Bukkit.getScheduler().runTask(plugin, () -> applySpectatorSettings(event.getPlayer()));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Round round = getPlayerRound(event.getPlayer());
        if (round != null) {
            removePlayer(round, event.getPlayer(), false);
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

    private void addPlayer(Round round, Player player) {
        if (round.isParticipant(player.getUniqueId())) {
            plugin.sendMessage(player, "already-waiting");
            return;
        }
        round.addParticipant(player.getUniqueId());
        resetPlayerState(player);
        buildWaitingBoxes(round);
        Location spawn = round.getWaitingBoxSpawns().get(player.getUniqueId());
        if (spawn != null) {
            player.teleport(spawn);
        }
        plugin.sendMessage(player, "joined");
        maybeStartCountdown(round);
    }

    private void removePlayer(Round round, Player player, boolean teleportOut) {
        round.removeParticipant(player.getUniqueId());
        player.setInvulnerable(false);
        player.setGameMode(GameMode.SURVIVAL);
        if (!round.isStarted()) {
            buildWaitingBoxes(round);
        }
        if (teleportOut) {
            World mainWorld = Bukkit.getWorlds().getFirst();
            player.teleport(mainWorld.getSpawnLocation());
            plugin.sendMessage(player, "left");
        }
        checkForWinner(round);
    }

    private void handleDeath(Round round, Player player) {
        if (!round.isAlive(player.getUniqueId())) {
            return;
        }
        round.removeAlive(player.getUniqueId());
        plugin.sendMessage(player, "eliminated");
        player.getInventory().clear();
        player.setInvulnerable(true);
        applySpectatorSettings(player);
        checkForWinner(round);
    }

    private void maybeStartCountdown(Round round) {
        if (round.getParticipants().size() < config.getMinPlayers() || round.isStarted()) {
            return;
        }
        String roundId = findRoundId(round);
        if (roundId == null) {
            return;
        }
        RoundTasks tasks = roundTasks.computeIfAbsent(roundId, key -> new RoundTasks());
        if (tasks.countdownTask != null) {
            return;
        }
        int countdownSeconds = config.getStartCountdownSeconds();
        broadcast(round, "countdown-start", Map.of("seconds", Integer.toString(countdownSeconds)));
        tasks.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = countdownSeconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    tasks.countdownTask.cancel();
                    tasks.countdownTask = null;
                    startRound(round);
                    return;
                }
                if (remaining <= 5 || remaining % 5 == 0) {
                    broadcast(round, "countdown-tick", Map.of("seconds", Integer.toString(remaining)));
                }
                remaining--;
            }
        }, 20L, 20L);
    }

    private void startRound(Round round) {
        if (round.isStarted()) {
            return;
        }
        round.markStarted();
        String roundId = findRoundId(round);
        if (roundId == null) {
            return;
        }
        RoundTasks tasks = roundTasks.computeIfAbsent(roundId, key -> new RoundTasks());
        if (tasks.countdownTask != null) {
            tasks.countdownTask.cancel();
            tasks.countdownTask = null;
        }
        List<UUID> orderedParticipants = getOrderedParticipants(round);
        spawnService.buildPillars(round, orderedParticipants);
        spawnService.buildStartBoxes(round, orderedParticipants);
        for (UUID uuid : round.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                resetPlayerState(player);
                player.setInvulnerable(false);
                Location spawn = round.getWaitingBoxSpawns().get(uuid);
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
        }
        spawnService.clearWaitingBoxes(round);
        for (UUID uuid : round.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 10, 0, true, false, true));
            }
        }
        scheduleItemDrops(round);
        broadcast(round, "round-started");
    }

    private void scheduleItemDrops(Round round) {
        String roundId = findRoundId(round);
        if (roundId == null) {
            return;
        }
        RoundTasks tasks = roundTasks.computeIfAbsent(roundId, key -> new RoundTasks());
        if (tasks.itemDelayTask != null) {
            tasks.itemDelayTask.cancel();
        }
        tasks.itemDelayTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.itemTask = itemService.scheduleItemDrops(plugin, round, () -> checkForWinner(round));
        }, 20L * 5);
    }

    private void checkForWinner(Round round) {
        if (!round.isStarted() || round.isEnded()) {
            return;
        }
        if (round.getAlivePlayers().size() == 1) {
            UUID winnerId = round.getAlivePlayers().iterator().next();
            Player winner = Bukkit.getPlayer(winnerId);
            endRound(round, winner);
        } else if (round.getAlivePlayers().isEmpty()) {
            endRound(round, null);
        }
    }

    private void endRound(Round round, Player winner) {
        endRound(round, winner, false);
    }

    private void endRoundImmediate(Round round, Player winner) {
        endRound(round, winner, true);
    }

    private void endRound(Round round, Player winner, boolean immediateCleanup) {
        if (round.isEnded()) {
            if (immediateCleanup) {
                worldService.cleanupWorld(round.getWorld());
            }
            return;
        }
        round.markEnded();
        cancelRoundTasks(round);
        if (winner != null) {
            broadcast(round, "winner", Map.of("player", winner.getName()));
        } else {
            broadcast(round, "round-ended");
        }
        if (immediateCleanup) {
            World mainWorld = Bukkit.getWorlds().getFirst();
            for (UUID uuid : new HashSet<>(round.getParticipants())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.getInventory().clear();
                    player.setInvulnerable(false);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.teleport(mainWorld.getSpawnLocation());
                }
            }
        } else {
            Location spectatorSpawn = spawnService.getSpectatorSpawn(round);
            for (UUID uuid : new HashSet<>(round.getParticipants())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.getInventory().clear();
                    applySpectatorSettings(player);
                    if (spectatorSpawn != null) {
                        player.teleport(spectatorSpawn);
                    }
                }
            }
        }
        if (immediateCleanup) {
            String roundId = findRoundId(round);
            if (roundId != null) {
                rounds.remove(roundId);
                roundTasks.remove(roundId);
            }
            for (UUID uuid : round.getParticipants()) {
                playerRounds.remove(uuid);
            }
            round.clearParticipants();
            worldService.cleanupWorld(round.getWorld());
            return;
        }
        handleRoundEnded(round);
        round.clearParticipants();
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
            worldService.cleanupWorld(round.getWorld());
            return;
        }
        String baseId = new RoundId(roundId).baseId();
        pendingParticipants.put(baseId, remaining);
        endedRounds.put(baseId, round);
        for (UUID uuid : remaining) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                playerRounds.put(uuid, baseId);
                plugin.sendMessage(player, "round-restart", Map.of("seconds", Integer.toString(RESTART_DELAY_SECONDS)));
            }
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> restartRound(baseId), RESTART_DELAY_SECONDS * 20L);
        restartTasks.put(baseId, task);
    }

    private void restartRound(String baseId) {
        Set<UUID> remaining = pendingParticipants.remove(baseId);
        restartTasks.remove(baseId);
        Round endedRound = endedRounds.remove(baseId);
        if (remaining == null || remaining.isEmpty()) {
            if (endedRound != null) {
                worldService.cleanupWorld(endedRound.getWorld());
            }
            return;
        }
        String nextRoundId = nextRoundId(baseId);
        Round newRound = createRound(nextRoundId);
        for (UUID uuid : remaining) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                addPlayer(newRound, player);
                applySpectatorSettings(player);
                playerRounds.put(uuid, nextRoundId);
            } else {
                playerRounds.remove(uuid);
            }
        }
        if (endedRound != null) {
            worldService.cleanupWorld(endedRound.getWorld());
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
        String roundId = RoundId.normalize(id);
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
            BukkitTask task = restartTasks.remove(id);
            if (task != null) {
                task.cancel();
            }
            Round endedRound = endedRounds.remove(id);
            if (endedRound != null) {
                worldService.cleanupWorld(endedRound.getWorld());
            }
        }
        if (notify) {
            plugin.sendMessage(player, "left");
        }
        return true;
    }

    private void registerRoundId(RoundId roundId) {
        String normalized = roundId.value();
        String baseId = roundId.baseId();
        int counter = roundId.counter();
        int current = roundCounters.getOrDefault(baseId, 1);
        if (counter > current) {
            roundCounters.put(baseId, counter);
        } else {
            roundCounters.putIfAbsent(baseId, current);
        }
    }

    private String nextRoundId(String baseId) {
        int next = roundCounters.getOrDefault(baseId, 1) + 1;
        roundCounters.put(baseId, next);
        return baseId + "-" + next;
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

    private List<UUID> getOrderedParticipants(Round round) {
        return round.getParticipants().stream().sorted().toList();
    }

    private void broadcast(Round round, String key) {
        broadcast(round, key, Map.of());
    }

    private void broadcast(Round round, String key, Map<String, String> placeholders) {
        Component message = plugin.message(key, placeholders);
        for (UUID uuid : round.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void buildWaitingBoxes(Round round) {
        spawnService.buildWaitingBoxes(round, round.getParticipants());
        for (UUID uuid : round.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            Location spawn = round.getWaitingBoxSpawns().get(uuid);
            if (player != null && spawn != null) {
                player.teleport(spawn);
            }
        }
    }

    private void resetPlayerState(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
    }

    private void applySpectatorSettings(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setInvulnerable(true);
    }

    private void cancelRoundTasks(Round round) {
        String roundId = findRoundId(round);
        if (roundId == null) {
            return;
        }
        RoundTasks tasks = roundTasks.remove(roundId);
        if (tasks == null) {
            return;
        }
        if (tasks.itemTask != null) {
            tasks.itemTask.cancel();
        }
        if (tasks.itemDelayTask != null) {
            tasks.itemDelayTask.cancel();
        }
        if (tasks.countdownTask != null) {
            tasks.countdownTask.cancel();
        }
    }

    private static class RoundTasks {
        private BukkitTask itemTask;
        private BukkitTask itemDelayTask;
        private BukkitTask countdownTask;
    }
}
