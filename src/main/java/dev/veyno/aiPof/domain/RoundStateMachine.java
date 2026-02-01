package dev.veyno.aiPof.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class RoundStateMachine {
    private final Map<RoundState, Set<RoundState>> transitions = new EnumMap<>(RoundState.class);

    public RoundStateMachine() {
        transitions.put(RoundState.WAITING, EnumSet.of(RoundState.COUNTDOWN, RoundState.STARTING, RoundState.ENDING));
        transitions.put(RoundState.COUNTDOWN, EnumSet.of(RoundState.WAITING, RoundState.STARTING, RoundState.ENDING));
        transitions.put(RoundState.STARTING, EnumSet.of(RoundState.RUNNING, RoundState.ENDING));
        transitions.put(RoundState.RUNNING, EnumSet.of(RoundState.ENDING));
        transitions.put(RoundState.ENDING, EnumSet.of(RoundState.ENDED));
        transitions.put(RoundState.ENDED, EnumSet.noneOf(RoundState.class));
    }

    public boolean canTransition(RoundState from, RoundState to) {
        if (from == to) {
            return true;
        }
        return transitions.getOrDefault(from, EnumSet.noneOf(RoundState.class)).contains(to);
    }

    public void assertTransition(RoundState from, RoundState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid round state transition from " + from + " to " + to + ".");
        }
    }
}
