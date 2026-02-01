package dev.veyno.aiPof.repository;

import dev.veyno.aiPof.domain.Round;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface RoundRepository {
    Map<String, Round> rounds();

    Map<UUID, String> playerRounds();

    Map<String, Set<UUID>> pendingParticipants();
}
