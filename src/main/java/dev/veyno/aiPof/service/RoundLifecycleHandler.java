package dev.veyno.aiPof.service;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.config.ConfigService;
import dev.veyno.aiPof.domain.Round;
import dev.veyno.aiPof.domain.RoundId;
import dev.veyno.aiPof.domain.RoundState;
import dev.veyno.aiPof.infrastructure.WorldService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class RoundLifecycleHandler {
    private final AiPof plugin;
    private final ConfigService config;
    private final WorldService worldService;
    private final SpawnService spawnService;
    private final ItemService itemService;
    private final Map<String, Round> rounds;
    private final Map<UUID, String> playerRounds;
    private final Map<String, Set<UUID>> pendingParticipants;
    private final Map<String, BukkitTask> restartTasks;
    private final Map<String, Round> endedRounds;
    private final Map<String, Integer> roundCounters;
    private final Map<String, RoundTasks> roundTasks = new HashMap<>();

    public RoundLifecycleHandler(
        AiPof plugin,
        ConfigService config,
        WorldService worldService,
        SpawnService spawnService,
        ItemService itemService,
        Map<String, Round> rounds,
        Map<UUID, String> playerRounds,
        Map<String, Set<UUID>> pendingParticipants,
        Map<String, BukkitTask> restartTasks,
        Map<String, Round> endedRounds,
        Map<String, Integer> roundCounters
    ) {
        this.plugin = plugin;
        this.config = config;
        this.worldService = worldService;
        this.spawnService = spawnService;
        this.itemService = itemService;
        this.rounds = rounds;
        this.playerRounds = playerRounds;
        this.pendingParticipants = pendingParticipants;
        this.restartTasks = restartTasks;
        this.endedRounds = endedRounds;
        this.roundCounters = roundCounters;
    }

    public void maybeStartCountdown(Round round) {
        if (round.getParticipants().size() < config.getMinPlayers() || !round.isWaitingForStart() || round.isCountingDown()) {
            return;
        }
        if (!round.canTransitionTo(RoundState.COUNTDOWN)) {
            return;
        }
        String roundId = findRoundId(round);
        if (roundId == null) {
            return;
        }
        round.transitionTo(RoundState.COUNTDOWN);
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
                if (round.getParticipants().size() < config.getMinPlayers()) {
                    tasks.countdownTask.cancel();
                    tasks.countdownTask = null;
                    if (round.canTransitionTo(RoundState.WAITING)) {
                        round.transitionTo(RoundState.WAITING);
                    }
                    return;
                }
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

    public void startRound(Round round) {
        if (!round.isWaitingForStart() || !round.canTransitionTo(RoundState.STARTING)) {
            return;
        }
        String roundId = findRoundId(round);
        if (roundId == null) {
            return;
        }
        round.transitionTo(RoundState.STARTING);
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
            }
        }
        Map<UUID, Location> startSpawns = spawnService.teleportParticipantsToStart(round, round.getParticipants());
        spawnService.clearWaitingBoxes(round);
        scheduleStartTeleportCheck(round, startSpawns);
        for (UUID uuid : round.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 10, 0, true, false, true));
            }
        }
        round.markAllParticipantsActive();
        round.transitionTo(RoundState.RUNNING);
        scheduleItemDrops(round);
        broadcast(round, "round-started");
    }

    public void checkForWinner(Round round) {
        if (!round.isRunning() || round.isEnded()) {
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

    public void endRound(Round round, Player winner) {
        endRound(round, winner, false);
    }

    public void endRoundImmediate(Round round, Player winner) {
        endRound(round, winner, true);
    }

    public void cancelRoundTasks(Round round) {
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

    public void resetPlayerState(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
    }

    public void applySpectatorSettings(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setInvulnerable(true);
    }

    public void buildWaitingBoxes(Round round) {
        spawnService.buildWaitingBoxes(round, round.getParticipants());
        for (UUID uuid : round.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            Location spawn = round.getWaitingBoxSpawns().get(uuid);
            if (player != null && spawn != null) {
                player.teleport(spawn);
            }
        }
    }

    public void handleRoundEnded(Round round) {
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
                int restartDelay = config.getRoundRestartCooldownSeconds();
                plugin.sendMessage(player, "round-restart", Map.of("seconds", Integer.toString(restartDelay)));
            }
        }
        int restartDelay = config.getRoundRestartCooldownSeconds();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> restartRound(baseId), restartDelay * 20L);
        restartTasks.put(baseId, task);
    }

    public void restartRound(String baseId) {
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
        Set<Player> addedPlayers = new HashSet<>();
        for (UUID uuid : remaining) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                newRound.addParticipant(uuid);
                resetPlayerState(player);
                addedPlayers.add(player);
                plugin.sendMessage(player, "joined");
                playerRounds.put(uuid, nextRoundId);
            } else {
                playerRounds.remove(uuid);
            }
        }
        if (!addedPlayers.isEmpty()) {
            buildWaitingBoxes(newRound);
            maybeStartCountdown(newRound);
            for (Player player : addedPlayers) {
                applySpectatorSettings(player);
            }
        }
        if (endedRound != null) {
            worldService.cleanupWorld(endedRound.getWorld());
        }
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

    private void scheduleStartTeleportCheck(Round round, Map<UUID, Location> startSpawns) {
        if (startSpawns.isEmpty()) {
            return;
        }
        int delayTicks = config.getStartTeleportDelayTicks();
        if (delayTicks <= 0) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!round.isRunning() && !round.isWaitingForStart()) {
                return;
            }
            spawnService.teleportParticipantsToStartDelayed(round.getParticipants(), startSpawns);
        }, delayTicks);
    }

    private void endRound(Round round, Player winner, boolean immediateCleanup) {
        if (round.isEnded()) {
            if (immediateCleanup) {
                worldService.cleanupWorld(round.getWorld());
            }
            return;
        }
        if (round.canTransitionTo(RoundState.ENDING)) {
            round.transitionTo(RoundState.ENDING);
        }
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
            round.transitionTo(RoundState.ENDED);
            return;
        }
        handleRoundEnded(round);
        round.clearParticipants();
        if (round.canTransitionTo(RoundState.ENDED)) {
            round.transitionTo(RoundState.ENDED);
        }
    }

    private String findRoundId(Round round) {
        for (Map.Entry<String, Round> entry : rounds.entrySet()) {
            if (entry.getValue() == round) {
                return entry.getKey();
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

    private static class RoundTasks {
        private BukkitTask itemTask;
        private BukkitTask itemDelayTask;
        private BukkitTask countdownTask;
    }
}
