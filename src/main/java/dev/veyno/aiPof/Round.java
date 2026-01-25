package dev.veyno.aiPof;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class Round {
    private final AiPof plugin;
    private final String worldName;
    private final Random random = new Random();
    private final Consumer<Round> endListener;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Map<UUID, Integer> pillarAssignments = new HashMap<>();
    private final List<Material> itemPool;
    private World world;
    private Location waitingSpawn;
    private Location waitingPlatformCenter;
    private int waitingPlatformRadius;
    private int waitingPlatformY;
    private BukkitTask itemTask;
    private BukkitTask countdownTask;
    private boolean started;
    private boolean ended;

    public Round(AiPof plugin, Consumer<Round> endListener) {
        this.plugin = plugin;
        this.endListener = endListener;
        this.worldName = "pof_round_" + System.currentTimeMillis();
        this.itemPool = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .filter(material -> material != Material.AIR)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public void initializeWorld() {
        WorldCreator creator = new WorldCreator(worldName)
            .generator(new ChunkGenerator() {
                @Override
                public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
                    return createChunkData(world);
                }
            })
            .generateStructures(false);
        world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Konnte Welt nicht erstellen.");
        }
        world.setDifficulty(Difficulty.EASY);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000);
        int pillarHeight = plugin.getConfig().getInt("pillar-height", 64);
        waitingSpawn = new Location(world, 0.5, pillarHeight + 6, 0.5);
        buildWaitingPlatform(pillarHeight + 5);
    }

    public void addPlayer(Player player) {
        if (participants.contains(player.getUniqueId())) {
            player.sendMessage("§eDu bist bereits im Wartebereich.");
            return;
        }
        participants.add(player.getUniqueId());
        alivePlayers.add(player.getUniqueId());
        player.getInventory().clear();
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.teleport(waitingSpawn);
        player.sendMessage("§aDu bist dem Wartebereich beigetreten.");
        maybeStartCountdown();
    }

    public void removePlayer(Player player, boolean teleportOut) {
        participants.remove(player.getUniqueId());
        alivePlayers.remove(player.getUniqueId());
        pillarAssignments.remove(player.getUniqueId());
        player.setInvulnerable(false);
        if (teleportOut) {
            World mainWorld = Bukkit.getWorlds().getFirst();
            player.teleport(mainWorld.getSpawnLocation());
            player.sendMessage("§eDu hast die Runde verlassen.");
        }
        checkForWinner();
    }

    public void handleDeath(Player player) {
        if (!alivePlayers.contains(player.getUniqueId())) {
            return;
        }
        alivePlayers.remove(player.getUniqueId());
        player.sendMessage("§cDu bist ausgeschieden.");
        player.getInventory().clear();
        checkForWinner();
    }

    public void forceStart(Player player) {
        if (started) {
            player.sendMessage("§eDie Runde läuft bereits.");
            return;
        }
        if (participants.size() < 1) {
            player.sendMessage("§cKeine Spieler im Wartebereich.");
            return;
        }
        startRound();
    }

    public void startRound() {
        if (started) {
            return;
        }
        started = true;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        buildPillars();
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20f);
            }
        }
        teleportPlayersToPillars();
        clearWaitingPlatform();
        scheduleItemDrops();
        broadcast("§6Pillars of Fortune gestartet! Viel Glück.");
    }

    public void endRound(Player winner) {
        if (ended) {
            cleanupWorld();
            return;
        }
        ended = true;
        if (itemTask != null) {
            itemTask.cancel();
            itemTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (winner != null) {
            broadcast("§a" + winner.getName() + " hat die Runde gewonnen!");
        } else {
            broadcast("§eRunde beendet.");
        }
        World mainWorld = Bukkit.getWorlds().getFirst();
        for (UUID uuid : new HashSet<>(participants)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().clear();
                player.setInvulnerable(false);
                player.teleport(mainWorld.getSpawnLocation());
            }
        }
        if (endListener != null) {
            endListener.accept(this);
        }
        participants.clear();
        alivePlayers.clear();
        pillarAssignments.clear();
        cleanupWorld();
    }

    void cleanupWorld() {
        if (world == null) {
            return;
        }
        String name = world.getName();
        Bukkit.unloadWorld(world, false);
        File folder = new File(Bukkit.getWorldContainer(), name);
        deleteWorldFolder(folder);
        world = null;
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

    private void checkForWinner() {
        if (!started || ended) {
            return;
        }
        if (alivePlayers.size() == 1) {
            UUID winnerId = alivePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerId);
            endRound(winner);
        } else if (alivePlayers.isEmpty()) {
            endRound(null);
        }
    }

    private void buildWaitingPlatform(int y) {
        waitingPlatformRadius = 3;
        waitingPlatformY = y;
        waitingPlatformCenter = new Location(world, 0, y, 0);
        for (int x = -waitingPlatformRadius; x <= waitingPlatformRadius; x++) {
            for (int z = -waitingPlatformRadius; z <= waitingPlatformRadius; z++) {
                Block block = world.getBlockAt(x, y, z);
                block.setType(Material.SMOOTH_STONE);
            }
        }
    }

    private void clearWaitingPlatform() {
        if (waitingPlatformCenter == null) {
            return;
        }
        int centerX = waitingPlatformCenter.getBlockX();
        int centerZ = waitingPlatformCenter.getBlockZ();
        for (int x = -waitingPlatformRadius; x <= waitingPlatformRadius; x++) {
            for (int z = -waitingPlatformRadius; z <= waitingPlatformRadius; z++) {
                Block block = world.getBlockAt(centerX + x, waitingPlatformY, centerZ + z);
                block.setType(Material.AIR);
            }
        }
    }

    private void maybeStartCountdown() {
        int minPlayers = plugin.getConfig().getInt("min-players", 2);
        if (participants.size() < minPlayers || started) {
            return;
        }
        if (countdownTask != null) {
            return;
        }
        int countdownSeconds = plugin.getConfig().getInt("start-countdown-seconds", 10);
        broadcast("§eGenügend Spieler erreicht. Start in " + countdownSeconds + " Sekunden.");
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = countdownSeconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    countdownTask.cancel();
                    countdownTask = null;
                    startRound();
                    return;
                }
                if (remaining <= 5 || remaining % 5 == 0) {
                    broadcast("§eStart in " + remaining + " Sekunden.");
                }
                remaining--;
            }
        }, 20L, 20L);
    }

    private void buildPillars() {
        int height = plugin.getConfig().getInt("pillar-height", 64);
        int spacing = plugin.getConfig().getInt("pillar-spacing", 6);
        int count = participants.size();
        if (count == 0) {
            return;
        }
        double circumference = count * spacing;
        double radius = Math.max(spacing, circumference / (2 * Math.PI));
        double angleStep = (2 * Math.PI) / count;
        int index = 0;
        for (UUID uuid : participants) {
            double angle = angleStep * index;
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            for (int y = 0; y <= height; y++) {
                world.getBlockAt(x, y, z).setType(Material.BEDROCK);
            }
            pillarAssignments.put(uuid, index);
            index++;
        }
    }

    private void teleportPlayersToPillars() {
        int height = plugin.getConfig().getInt("pillar-height", 64);
        int spacing = plugin.getConfig().getInt("pillar-spacing", 6);
        int count = participants.size();
        double circumference = count * spacing;
        double radius = Math.max(spacing, circumference / (2 * Math.PI));
        double angleStep = (2 * Math.PI) / count;
        int index = 0;
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                index++;
                continue;
            }
            double angle = angleStep * index;
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            Location spawn = new Location(world, x + 0.5, height + 1, z + 0.5);
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(false);
            index++;
        }
    }

    private void scheduleItemDrops() {
        int intervalSeconds = plugin.getConfig().getInt("item-interval-seconds", 10);
        itemTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : new HashSet<>(alivePlayers)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    alivePlayers.remove(uuid);
                    continue;
                }
                Material material = itemPool.get(random.nextInt(itemPool.size()));
                ItemStack stack = new ItemStack(material, 1);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                leftover.values().forEach(item -> world.dropItemNaturally(player.getLocation(), item));
            }
            checkForWinner();
        }, 20L * intervalSeconds, 20L * intervalSeconds);
    }

    private void broadcast(String message) {
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public Set<UUID> getParticipants() {
        return new HashSet<>(participants);
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isEnded() {
        return ended;
    }

    public World getWorld() {
        return world;
    }
}
