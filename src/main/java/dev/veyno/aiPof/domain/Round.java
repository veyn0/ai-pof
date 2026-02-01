package dev.veyno.aiPof.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
    private final ParticipantRegistry participantRegistry = new ParticipantRegistry();
    private final List<Material> itemPool;
    private final Map<UUID, Location> waitingBoxSpawns = new java.util.HashMap<>();
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
        Participant participant = participantRegistry.add(uuid);
        participant.setAlive(true);
        participant.setStatus(ParticipantStatus.WAITING);
        participant.setPillarIndex(null);
    }

    public void removeParticipant(UUID uuid) {
        participantRegistry.remove(uuid);
    }

    public void removeAlive(UUID uuid) {
        participantRegistry.markAlive(uuid, false);
    }

    public void clearParticipants() {
        participantRegistry.clear();
    }

    public boolean isParticipant(UUID uuid) {
        return participantRegistry.contains(uuid);
    }

    public boolean isAlive(UUID uuid) {
        return participantRegistry.isAlive(uuid);
    }

    public Set<UUID> getParticipants() {
        return participantRegistry.getParticipantIds();
    }

    public Set<UUID> getAlivePlayers() {
        return participantRegistry.getAliveParticipantIds();
    }

    public void markAllParticipantsActive() {
        participantRegistry.markAllActive();
    }

    public void assignPillar(UUID uuid, int index) {
        participantRegistry.setPillarIndex(uuid, index);
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

    public boolean isWaitingBoxBlock(Block block, BlockExclusions exclusions) {
        if (block == null || world == null || !block.getWorld().equals(world)) {
            return false;
        }
        if (exclusions != null && exclusions.matches(block.getType().name())) {
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
