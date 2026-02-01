package dev.veyno.aiPof.domain;

import java.util.UUID;

public class Participant {
    private final UUID id;
    private ParticipantStatus status;
    private Integer pillarIndex;
    private boolean alive;

    public Participant(UUID id) {
        this.id = id;
        this.status = ParticipantStatus.WAITING;
        this.alive = true;
    }

    public UUID getId() {
        return id;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public void setStatus(ParticipantStatus status) {
        this.status = status;
    }

    public Integer getPillarIndex() {
        return pillarIndex;
    }

    public void setPillarIndex(Integer pillarIndex) {
        this.pillarIndex = pillarIndex;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
}
