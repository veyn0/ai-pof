package dev.veyno.aiPof.service;

import dev.veyno.aiPof.AiPof;
import dev.veyno.aiPof.config.ConfigService;
import dev.veyno.aiPof.domain.BlockExclusions;
import dev.veyno.aiPof.domain.Round;
import dev.veyno.aiPof.domain.RoundId;
import dev.veyno.aiPof.infrastructure.WorldService;
import dev.veyno.aiPof.repository.InMemoryRoundRepository;
import dev.veyno.aiPof.repository.RoundRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Logger;
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
import org.bukkit.scheduler.BukkitTask;

public class RoundService implements Listener {
    private final AiPof plugin;
    private final WorldService worldService;
    private final SpawnService spawnService;
    private final RoundLifecycleHandler lifecycleHandler;
    private final BlockExclusions blockExclusions;
    private final RoundRepository roundRepository;
    private final Map<String, BukkitTask> restartTasks = new HashMap<>();
    private final Map<String, Round> endedRounds = new HashMap<>();
    private final Map<String, Integer> roundCounters = new HashMap<>();
    private final Logger logger;

    public RoundService(AiPof plugin, ConfigService config, WorldService worldService, SpawnService spawnService, ItemService itemService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.worldService = worldService;
        this.spawnService = spawnService;
        this.blockExclusions = config.getBlockExclusions();
        this.roundRepository = new InMemoryRoundRepository();
        this.lifecycleHandler = new RoundLifecycleHandler(
            plugin,
            config,
            worldService,
            spawnService,
            itemService,
            roundRepository,
            restartTasks,
            endedRounds,
            roundCounters
        );
    }

    public Optional<Round> getRound(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roundRepository.rounds().get(RoundId.normalize(id)));
    }

    public List<String> getRoundIds() {
        return roundRepository.rounds().keySet().stream().sorted().toList();
    }

    public Round createRound(String id) {
        return lifecycleHandler.createRound(id);
    }

    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
        for (Round round : List.copyOf(roundRepository.rounds().values())) {
            lifecycleHandler.endRoundImmediate(round, null);
        }
        roundRepository.rounds().clear();
        roundRepository.playerRounds().clear();
        roundRepository.pendingParticipants().clear();
        restartTasks.clear();
        endedRounds.clear();
        roundCounters.clear();
    }

    public void forceStart(Player player, String id) {
        Round round = getActiveRound(id, player);
        if (round == null) {
            return;
        }
        if (!round.isWaitingForStart()) {
            plugin.sendMessage(player, "round-already-started");
            return;
        }
        if (round.getParticipants().isEmpty()) {
            plugin.sendMessage(player, "no-players-waiting");
            return;
        }
        lifecycleHandler.startRound(round);
    }

    public void join(Player player, String id) {
        Round round = getActiveRound(id, player);
        if (round == null) {
            return;
        }
        String roundId = RoundId.normalize(id);
        String existingId = roundRepository.playerRounds().get(player.getUniqueId());
        if (existingId != null && !existingId.equals(roundId)) {
            player.sendMessage("§cDu bist bereits in Runde " + existingId + ".");
            return;
        }
        addPlayer(round, player);
        roundRepository.playerRounds().put(player.getUniqueId(), roundId);
    }

    public int cleanupUnusedWorldFolders() {
        Set<World> activeWorlds = roundRepository.rounds().values().stream()
            .map(Round::getWorld)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return worldService.cleanupUnusedWorldFolders(activeWorlds);
    }

    public void leave(Player player) {
        String roundId = roundRepository.playerRounds().get(player.getUniqueId());
        if (roundId == null) {
            player.sendMessage("§cDu bist in keiner Runde.");
            return;
        }
        leave(player, roundId);
    }

    public void leave(Player player, String id) {
        String roundId = RoundId.normalize(id);
        Round round = roundRepository.rounds().get(roundId);
        if (round == null || round.isEnded()) {
            if (removePendingParticipant(player, roundId, true)) {
                return;
            }
            roundRepository.playerRounds().remove(player.getUniqueId());
            player.sendMessage("§cDiese Runde existiert nicht mehr.");
            return;
        }
        removePlayer(round, player, true);
        roundRepository.playerRounds().remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Round round = getPlayerRound(event.getEntity());
        if (round == null) {
            return;
        }
        if (!round.isRunning() && round.isParticipant(event.getEntity().getUniqueId())) {
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
        if (!round.isRunning() && round.isParticipant(player.getUniqueId())) {
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
        if (round.isParticipant(event.getPlayer().getUniqueId()) && !round.isAlive(event.getPlayer().getUniqueId())) {
            Location spectatorSpawn = spawnService.getSpectatorSpawn(round);
            if (spectatorSpawn != null) {
                event.setRespawnLocation(spectatorSpawn);
            }
            Bukkit.getScheduler().runTask(plugin, () -> lifecycleHandler.applySpectatorSettings(event.getPlayer()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Round round = getPlayerRound(event.getPlayer());
        if (round != null) {
            removePlayer(round, event.getPlayer(), false);
            roundRepository.playerRounds().remove(event.getPlayer().getUniqueId());
            return;
        }
        removePendingParticipant(event.getPlayer(), null, false);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Round round = getPlayerRound(event.getPlayer());
        if (round == null || !round.isRunning()) {
            return;
        }
        Player player = event.getPlayer();
        if (!round.isParticipant(player.getUniqueId())) {
            logger.fine(() -> "player-move ignored reason=not-participant player=" + player.getName());
            return;
        }
        if (player.getWorld().equals(round.getWorld()) && player.getLocation().getY() < 0) {
            logger.info(() -> "player-move void-fall player=" + player.getName() + " roundWorld=" + round.getWorldName());
            player.setHealth(0.0);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Round round = findRoundByWorld(event.getBlock().getWorld());
        if (round == null) {
            return;
        }
        if (round.isWaitingBoxBlock(event.getBlock(), blockExclusions)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Round round = findRoundByWorld(event.getBlock().getWorld());
        if (round == null) {
            return;
        }
        if (round.isWaitingBoxBlock(event.getBlock(), blockExclusions)) {
            event.setCancelled(true);
        }
    }

    private void addPlayer(Round round, Player player) {
        if (round.isParticipant(player.getUniqueId())) {
            plugin.sendMessage(player, "already-waiting");
            return;
        }
        round.addParticipant(player.getUniqueId());
        lifecycleHandler.resetPlayerState(player);
        lifecycleHandler.buildWaitingBoxes(round);
        Location spawn = round.getWaitingBoxSpawns().get(player.getUniqueId());
        if (spawn != null) {
            boolean success = player.teleport(spawn);
            logger.info(() -> "teleport result=" + success
                + " reason=join-waiting-box player=" + player.getName()
                + " world=" + spawn.getWorld().getName()
                + " x=" + spawn.getBlockX()
                + " y=" + spawn.getBlockY()
                + " z=" + spawn.getBlockZ());
        } else {
            logger.warning(() -> "teleport skipped reason=missing-waiting-box player=" + player.getName());
        }
        plugin.sendMessage(player, "joined");
        lifecycleHandler.maybeStartCountdown(round);
    }

    private void removePlayer(Round round, Player player, boolean teleportOut) {
        round.removeParticipant(player.getUniqueId());
        player.setInvulnerable(false);
        player.setGameMode(GameMode.SURVIVAL);
        if (round.isWaitingForStart()) {
            lifecycleHandler.buildWaitingBoxes(round);
        }
        if (teleportOut) {
            World mainWorld = Bukkit.getWorlds().getFirst();
            Location spawn = mainWorld.getSpawnLocation();
            boolean success = player.teleport(spawn);
            logger.info(() -> "teleport result=" + success
                + " reason=leave-world player=" + player.getName()
                + " world=" + spawn.getWorld().getName()
                + " x=" + spawn.getBlockX()
                + " y=" + spawn.getBlockY()
                + " z=" + spawn.getBlockZ());
            plugin.sendMessage(player, "left");
        }
        lifecycleHandler.checkForWinner(round);
    }

    private void handleDeath(Round round, Player player) {
        if (!round.isAlive(player.getUniqueId())) {
            return;
        }
        round.removeAlive(player.getUniqueId());
        plugin.sendMessage(player, "eliminated");
        player.getInventory().clear();
        player.setInvulnerable(true);
        lifecycleHandler.applySpectatorSettings(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Round currentRound = getPlayerRound(player);
            if (currentRound == null) {
                return;
            }
            if (!currentRound.isParticipant(player.getUniqueId())) {
                return;
            }
            if (currentRound.isAlive(player.getUniqueId())) {
                return;
            }
            Location spectatorSpawn = spawnService.getSpectatorSpawn(currentRound);
            if (spectatorSpawn != null) {
                boolean success = player.teleport(spectatorSpawn);
                logger.info(() -> "teleport result=" + success
                    + " reason=death-spectator-spawn player=" + player.getName()
                    + " world=" + spectatorSpawn.getWorld().getName()
                    + " x=" + spectatorSpawn.getBlockX()
                    + " y=" + spectatorSpawn.getBlockY()
                    + " z=" + spectatorSpawn.getBlockZ());
            }
            lifecycleHandler.applySpectatorSettings(player);
        });
        lifecycleHandler.checkForWinner(round);
    }


    private Round getPlayerRound(Player player) {
        String roundId = roundRepository.playerRounds().get(player.getUniqueId());
        if (roundId == null) {
            return null;
        }
        return roundRepository.rounds().get(roundId);
    }

    private Round getActiveRound(String id, Player player) {
        String roundId = RoundId.normalize(id);
        Round round = roundRepository.rounds().get(roundId);
        if (round == null || round.isEnded()) {
            player.sendMessage("§cKeine aktive Runde mit dieser ID. Nutze /pof create <id>.");
            return null;
        }
        return round;
    }

    private boolean removePendingParticipant(Player player, String roundId, boolean notify) {
        String id = roundId != null ? roundId : roundRepository.playerRounds().get(player.getUniqueId());
        if (id == null) {
            return false;
        }
        Set<UUID> pending = roundRepository.pendingParticipants().get(id);
        if (pending == null || !pending.remove(player.getUniqueId())) {
            return false;
        }
        roundRepository.playerRounds().remove(player.getUniqueId());
        if (pending.isEmpty()) {
            roundRepository.pendingParticipants().remove(id);
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

    private Round findRoundByWorld(World world) {
        if (world == null) {
            return null;
        }
        for (Round round : roundRepository.rounds().values()) {
            if (world.equals(round.getWorld())) {
                return round;
            }
        }
        return null;
    }

}
