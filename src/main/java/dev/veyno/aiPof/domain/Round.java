package dev.veyno.aiPof.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

public class Round {
    public static final String WORLD_PREFIX = "pof_round_";
    private final String worldName;
    private final Random random = new Random();
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Map<UUID, Integer> pillarAssignments = new HashMap<>();
    private final List<Material> itemPool;
    private final Map<UUID, Location> waitingBoxSpawns = new HashMap<>();
    private final List<WaitingBox> waitingBoxes = new ArrayList<>();
    private final RoundStateMachine stateMachine = new RoundStateMachine();
    private RoundState state = RoundState.WAITING;
    private World world;

    public Round(String worldName) {
        this.worldName = worldName;
        this.itemPool = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .filter(material -> material != Material.AIR)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    public boolean transitionTo(RoundState target) {
        if (state == target) {
            return false;
        }
        stateMachine.assertTransition(state, target);
        state = target;
        return true;
    }

    public boolean canTransitionTo(RoundState target) {
        return stateMachine.canTransition(state, target);
    }

    public boolean isRunning() {
        return state == RoundState.RUNNING;
    }

    public boolean isEnded() {
        return state == RoundState.ENDED;
    }

    public boolean isWaitingForStart() {
        return state == RoundState.WAITING || state == RoundState.COUNTDOWN;
    }

    public boolean isCountingDown() {
        return state == RoundState.COUNTDOWN;
    }

    public RoundState getState() {
        return state;
    }

    public void addParticipant(UUID uuid) {
        participants.add(uuid);
        alivePlayers.add(uuid);
    }

    public void removeParticipant(UUID uuid) {
        participants.remove(uuid);
        alivePlayers.remove(uuid);
        pillarAssignments.remove(uuid);
    }

    public void removeAlive(UUID uuid) {
        alivePlayers.remove(uuid);
    }

    public void clearParticipants() {
        participants.clear();
        alivePlayers.clear();
        pillarAssignments.clear();
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public boolean isAlive(UUID uuid) {
        return alivePlayers.contains(uuid);
    }

    public Set<UUID> getParticipants() {
        return new HashSet<>(participants);
    }

    public Set<UUID> getAlivePlayers() {
        return new HashSet<>(alivePlayers);
    }

    public Set<UUID> getAlivePlayersMutable() {
        return alivePlayers;
    }

    public Map<UUID, Integer> getPillarAssignments() {
        return pillarAssignments;
    }

    public Map<UUID, Location> getWaitingBoxSpawns() {
        return waitingBoxSpawns;
    }

    public List<WaitingBox> getWaitingBoxes() {
        return waitingBoxes;
    }

    public List<Material> getItemPool() {
        return itemPool;
    }

    public Random getRandom() {
        return random;
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

    public void clearWaitingBoxes() {
        waitingBoxes.clear();
        waitingBoxSpawns.clear();
    }

    public record WaitingBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        BoundingBox boundingBox() {
            return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
    }
}
