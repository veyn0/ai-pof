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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

public class Round {
    private final AiPof plugin;
    private final String worldName;
    private final Random random = new Random();
    private final Consumer<Round> endListener;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Map<UUID, Integer> pillarAssignments = new HashMap<>();
    private final List<Material> itemPool;
    private final Map<UUID, Location> waitingBoxSpawns = new HashMap<>();
    private final List<WaitingBox> waitingBoxes = new ArrayList<>();
    private World world;
    private BukkitTask itemTask;
    private BukkitTask itemDelayTask;
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
        buildWaitingBoxes();
        Location spawn = waitingBoxSpawns.get(player.getUniqueId());
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.sendMessage("§aDu bist dem Wartebereich beigetreten.");
        maybeStartCountdown();
    }

    public void removePlayer(Player player, boolean teleportOut) {
        participants.remove(player.getUniqueId());
        alivePlayers.remove(player.getUniqueId());
        pillarAssignments.remove(player.getUniqueId());
        player.setInvulnerable(false);
        if (!started) {
            buildWaitingBoxes();
        }
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
        buildWaitingBoxes();
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvulnerable(false);
                Location spawn = waitingBoxSpawns.get(uuid);
                if (spawn != null) {
                    player.teleport(spawn);
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 10, 0, true, false, true));
            }
        }
        clearWaitingBoxes();
        scheduleItemDropsWithDelay();
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
        if (itemDelayTask != null) {
            itemDelayTask.cancel();
            itemDelayTask = null;
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

    private void buildWaitingBoxes() {
        clearWaitingBoxes();
        waitingBoxSpawns.clear();
        int count = participants.size();
        if (count == 0) {
            return;
        }
        int height = plugin.getConfig().getInt("pillar-height", 64);
        int spacing = plugin.getConfig().getInt("pillar-spacing", 6);
        int baseY = height + 3;
        List<UUID> orderedParticipants = getOrderedParticipants();
        for (int index = 0; index < orderedParticipants.size(); index++) {
            UUID uuid = orderedParticipants.get(index);
            Location pillarLocation = getPillarLocation(index, count, spacing);
            int x = pillarLocation.getBlockX();
            int z = pillarLocation.getBlockZ();
            int minX = x - 1;
            int maxX = x + 1;
            int minZ = z - 1;
            int maxZ = z + 1;
            int minY = baseY;
            int maxY = baseY + 2;
            WaitingBox box = new WaitingBox(minX, maxX, minY, maxY, minZ, maxZ);
            waitingBoxes.add(box);
            for (int bx = minX; bx <= maxX; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        Block block = world.getBlockAt(bx, by, bz);
                        boolean border = bx == minX || bx == maxX || by == minY || by == maxY || bz == minZ || bz == maxZ;
                        block.setType(border ? Material.GLASS : Material.AIR);
                    }
                }
            }
            Location spawn = new Location(world, x + 0.5, baseY + 1, z + 0.5);
            waitingBoxSpawns.put(uuid, spawn);
        }
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            Location spawn = waitingBoxSpawns.get(uuid);
            if (player != null && spawn != null) {
                player.teleport(spawn);
            }
        }
    }

    private void clearWaitingBoxes() {
        if (waitingBoxes.isEmpty()) {
            return;
        }
        for (WaitingBox box : waitingBoxes) {
            for (int bx = box.minX(); bx <= box.maxX(); bx++) {
                for (int by = box.minY(); by <= box.maxY(); by++) {
                    for (int bz = box.minZ(); bz <= box.maxZ(); bz++) {
                        world.getBlockAt(bx, by, bz).setType(Material.AIR);
                    }
                }
            }
        }
        waitingBoxes.clear();
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
        List<UUID> orderedParticipants = getOrderedParticipants();
        for (int index = 0; index < orderedParticipants.size(); index++) {
            UUID uuid = orderedParticipants.get(index);
            Location pillarLocation = getPillarLocation(index, count, spacing);
            int x = pillarLocation.getBlockX();
            int z = pillarLocation.getBlockZ();
            for (int y = 0; y <= height; y++) {
                world.getBlockAt(x, y, z).setType(Material.BEDROCK);
            }
            pillarAssignments.put(uuid, index);
        }
    }

    private void teleportPlayersToPillars() {
        int height = plugin.getConfig().getInt("pillar-height", 64);
        int spacing = plugin.getConfig().getInt("pillar-spacing", 6);
        int count = participants.size();
        List<UUID> orderedParticipants = getOrderedParticipants();
        for (int index = 0; index < orderedParticipants.size(); index++) {
            UUID uuid = orderedParticipants.get(index);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            Location pillarLocation = getPillarLocation(index, count, spacing);
            int x = pillarLocation.getBlockX();
            int z = pillarLocation.getBlockZ();
            Location spawn = new Location(world, x + 0.5, height + 1, z + 0.5);
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(false);
        }
    }

    private List<UUID> getOrderedParticipants() {
        return participants.stream().sorted().toList();
    }

    private Location getPillarLocation(int index, int count, int spacing) {
        double circumference = count * spacing;
        double radius = Math.max(spacing, circumference / (2 * Math.PI));
        double angleStep = (2 * Math.PI) / count;
        double angle = angleStep * index;
        int x = (int) Math.round(Math.cos(angle) * radius);
        int z = (int) Math.round(Math.sin(angle) * radius);
        return new Location(world, x, 0, z);
    }

    private void scheduleItemDropsWithDelay() {
        if (itemDelayTask != null) {
            itemDelayTask.cancel();
        }
        itemDelayTask = Bukkit.getScheduler().runTaskLater(plugin, this::scheduleItemDrops, 20L * 5);
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
                int count = rollItemCount();
                ItemStack stack = new ItemStack(material, count);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                leftover.values().forEach(item -> world.dropItemNaturally(player.getLocation(), item));
            }
            checkForWinner();
        }, 20L * intervalSeconds, 20L * intervalSeconds);
    }

    private int rollItemCount() {
        if (!plugin.getConfig().getBoolean("item-count.enabled", true)) {
            return 1;
        }
        int max = Math.max(1, plugin.getConfig().getInt("item-count.max", 1));
        double baseWeight = plugin.getConfig().getDouble("item-count.base-weight", 1.0);
        double totalWeight = 0.0;
        for (int count = 1; count <= max; count++) {
            totalWeight += baseWeight / (count * count);
        }
        double roll = random.nextDouble() * totalWeight;
        double running = 0.0;
        for (int count = 1; count <= max; count++) {
            running += baseWeight / (count * count);
            if (roll <= running) {
                return count;
            }
        }
        return 1;
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

    public boolean isWaitingBoxBlock(Block block) {
        if (block == null || world == null || !block.getWorld().equals(world)) {
            return false;
        }
        for (WaitingBox box : waitingBoxes) {
            if (box.boundingBox().contains(block.getX(), block.getY(), block.getZ())) {
                return true;
            }
        }
        return false;
    }

    private record WaitingBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        BoundingBox boundingBox() {
            return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
    }
}
