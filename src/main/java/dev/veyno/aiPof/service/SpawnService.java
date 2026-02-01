package dev.veyno.aiPof.service;

import dev.veyno.aiPof.config.ConfigService;
import dev.veyno.aiPof.domain.Round;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SpawnService {
    private static final double START_TELEPORT_Y_TOLERANCE = 0.1;
    private final ConfigService config;

    public SpawnService(ConfigService config) {
        this.config = config;
    }

    public Map<UUID, Location> teleportParticipantsToStart(Round round, Set<UUID> participants) {
        Map<UUID, Location> spawns = snapshotStartSpawns(round, participants);
        teleportToSpawns(participants, spawns);
        return spawns;
    }

    public void teleportParticipantsToStartDelayed(Set<UUID> participants, Map<UUID, Location> spawns) {
        teleportToSpawns(participants, spawns);
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            Location spawn = spawns.get(uuid);
            if (player == null || spawn == null) {
                continue;
            }
            double expectedY = spawn.getY();
            if (player.getLocation().getY() + START_TELEPORT_Y_TOLERANCE < expectedY) {
                player.teleport(spawn);
            }
        }
    }

    public void buildWaitingBoxes(Round round, Set<UUID> participants) {
        clearWaitingBoxes(round);
        if (participants.isEmpty()) {
            return;
        }
        World world = round.getWorld();
        if (world == null) {
            return;
        }
        int pillarHeight = config.getPillarHeight();
        int radius = config.getWaitingBoxRadius();
        int height = config.getWaitingBoxHeight();
        int yOffset = config.getWaitingBoxYOffset();
        int centerX = config.getWaitingBoxCenterX();
        int centerZ = config.getWaitingBoxCenterZ();
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;
        int minY = pillarHeight + yOffset;
        int maxY = minY + height + 1;
        buildGlassBox(round, world, minX, maxX, minY, maxY, minZ, maxZ);
        Location spawn = new Location(world, centerX + 0.5, minY + 1, centerZ + 0.5);
        for (UUID uuid : participants) {
            round.getWaitingBoxSpawns().put(uuid, spawn);
        }
    }

    public void buildStartBoxes(Round round, List<UUID> orderedParticipants) {
        clearWaitingBoxes(round);
        World world = round.getWorld();
        if (world == null || orderedParticipants.isEmpty()) {
            return;
        }
        int pillarHeight = config.getPillarHeight();
        int spacing = config.getPillarSpacing();
        int radius = config.getStartBoxRadius();
        int height = config.getStartBoxHeight();
        int yOffset = config.getStartBoxYOffset();
        int count = orderedParticipants.size();
        for (int index = 0; index < orderedParticipants.size(); index++) {
            UUID uuid = orderedParticipants.get(index);
            Location pillarLocation = getPillarLocation(world, index, count, spacing);
            int x = pillarLocation.getBlockX();
            int z = pillarLocation.getBlockZ();
            int minX = x - radius;
            int maxX = x + radius;
            int minZ = z - radius;
            int maxZ = z + radius;
            int minY = pillarHeight + yOffset;
            int maxY = minY + height + 1;
            buildGlassBox(round, world, minX, maxX, minY, maxY, minZ, maxZ);
            Location spawn = new Location(world, x + 0.5, minY + 1, z + 0.5);
            round.getWaitingBoxSpawns().put(uuid, spawn);
        }
    }

    public void clearWaitingBoxes(Round round) {
        if (round.getWaitingBoxes().isEmpty()) {
            round.clearWaitingBoxes();
            return;
        }
        World world = round.getWorld();
        if (world == null) {
            round.clearWaitingBoxes();
            return;
        }
        for (Round.WaitingBox box : round.getWaitingBoxes()) {
            for (int bx = box.minX(); bx <= box.maxX(); bx++) {
                for (int by = box.minY(); by <= box.maxY(); by++) {
                    for (int bz = box.minZ(); bz <= box.maxZ(); bz++) {
                        world.getBlockAt(bx, by, bz).setType(Material.AIR);
                    }
                }
            }
        }
        round.clearWaitingBoxes();
    }

    public void buildPillars(Round round, List<UUID> orderedParticipants) {
        World world = round.getWorld();
        if (world == null || orderedParticipants.isEmpty()) {
            return;
        }
        int height = config.getPillarHeight();
        int spacing = config.getPillarSpacing();
        int count = orderedParticipants.size();
        for (int index = 0; index < orderedParticipants.size(); index++) {
            UUID uuid = orderedParticipants.get(index);
            Location pillarLocation = getPillarLocation(world, index, count, spacing);
            int x = pillarLocation.getBlockX();
            int z = pillarLocation.getBlockZ();
            for (int y = 0; y <= height; y++) {
                world.getBlockAt(x, y, z).setType(Material.BEDROCK);
            }
            round.assignPillar(uuid, index);
        }
    }

    public Location getSpectatorSpawn(Round round) {
        World world = round.getWorld();
        if (world == null) {
            return Bukkit.getWorlds().getFirst().getSpawnLocation();
        }
        int pillarHeight = config.getPillarHeight();
        int yOffset = config.getWaitingBoxYOffset();
        int height = config.getWaitingBoxHeight();
        int centerX = config.getWaitingBoxCenterX();
        int centerZ = config.getWaitingBoxCenterZ();
        int y = pillarHeight + yOffset + height + 3;
        return new Location(world, centerX + 0.5, y, centerZ + 0.5);
    }

    private void buildGlassBox(Round round, World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        Round.WaitingBox box = new Round.WaitingBox(minX, maxX, minY, maxY, minZ, maxZ);
        round.getWaitingBoxes().add(box);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    boolean border = bx == minX || bx == maxX || by == minY || by == maxY || bz == minZ || bz == maxZ;
                    block.setType(border ? Material.GLASS : Material.AIR);
                }
            }
        }
    }

    private Location getPillarLocation(World world, int index, int count, int spacing) {
        double circumference = count * spacing;
        double radius = Math.max(spacing, circumference / (2 * Math.PI));
        double angleStep = (2 * Math.PI) / count;
        double angle = angleStep * index;
        int x = (int) Math.round(Math.cos(angle) * radius);
        int z = (int) Math.round(Math.sin(angle) * radius);
        return new Location(world, x, 0, z);
    }

    private Map<UUID, Location> snapshotStartSpawns(Round round, Set<UUID> participants) {
        Map<UUID, Location> spawns = new HashMap<>();
        for (UUID uuid : participants) {
            Location spawn = round.getWaitingBoxSpawns().get(uuid);
            if (spawn != null) {
                spawns.put(uuid, spawn.clone());
            }
        }
        return spawns;
    }

    private void teleportToSpawns(Set<UUID> participants, Map<UUID, Location> spawns) {
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            Location spawn = spawns.get(uuid);
            if (player != null && spawn != null) {
                player.teleport(spawn);
            }
        }
    }
}
