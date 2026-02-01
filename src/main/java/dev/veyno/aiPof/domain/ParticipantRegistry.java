package dev.veyno.aiPof.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ParticipantRegistry {
    private final Map<UUID, Participant> participants = new HashMap<>();

    public Participant add(UUID id) {
        return participants.computeIfAbsent(id, Participant::new);
    }

    public void remove(UUID id) {
        participants.remove(id);
    }

    public void clear() {
        participants.clear();
    }

    public boolean contains(UUID id) {
        return participants.containsKey(id);
    }

    public Optional<Participant> find(UUID id) {
        return Optional.ofNullable(participants.get(id));
    }

    public int size() {
        return participants.size();
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    public boolean isAlive(UUID id) {
        Participant participant = participants.get(id);
        return participant != null && participant.isAlive();
    }

    public void markAlive(UUID id, boolean alive) {
        Participant participant = participants.get(id);
        if (participant == null) {
            return;
        }
        participant.setAlive(alive);
        participant.setStatus(alive ? ParticipantStatus.ACTIVE : ParticipantStatus.ELIMINATED);
    }

    public void markAllActive() {
        participants.values().forEach(participant -> participant.setStatus(ParticipantStatus.ACTIVE));
    }

    public void setPillarIndex(UUID id, Integer index) {
        Participant participant = participants.get(id);
        if (participant != null) {
            participant.setPillarIndex(index);
        }
    }

    public Set<UUID> getParticipantIds() {
        return new HashSet<>(participants.keySet());
    }

    public Set<UUID> getAliveParticipantIds() {
        return participants.values().stream()
            .filter(Participant::isAlive)
            .map(Participant::getId)
            .collect(Collectors.toSet());
    }

    public Collection<Participant> getParticipants() {
        return participants.values();
    }
}
