package dev.veyno.aiPof.repository;

import dev.veyno.aiPof.domain.Round;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InMemoryRoundRepository implements RoundRepository {
    private final Map<String, Round> rounds = new HashMap<>();
    private final Map<UUID, String> playerRounds = new HashMap<>();
    private final Map<String, Set<UUID>> pendingParticipants = new HashMap<>();

    @Override
    public Map<String, Round> rounds() {
        return rounds;
    }

    @Override
    public Map<UUID, String> playerRounds() {
        return playerRounds;
    }

    @Override
    public Map<String, Set<UUID>> pendingParticipants() {
        return pendingParticipants;
    }
}
