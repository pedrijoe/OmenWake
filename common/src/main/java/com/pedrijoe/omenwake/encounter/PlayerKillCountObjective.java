package com.pedrijoe.omenwake.encounter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerKillCountObjective implements EncounterObjectiveRuntime {
    private final UUID instanceId;
    private final int requiredKills;
    private final Set<UUID> eligibleEntityIds;
    private final Set<UUID> processedEntityIds = new HashSet<>();
    private int qualifyingKills;

    public PlayerKillCountObjective(UUID instanceId, int requiredKills, Set<UUID> eligibleEntityIds) {
        if (requiredKills <= 0 || requiredKills > eligibleEntityIds.size()) {
            throw new IllegalArgumentException("Player kill count objective has invalid required kills");
        }
        this.instanceId = instanceId;
        this.requiredKills = requiredKills;
        this.eligibleEntityIds = Set.copyOf(eligibleEntityIds);
    }

    @Override
    public void onSignal(ObjectiveSignal signal) {
        if (!(signal instanceof ObjectiveSignal.EncounterEntityDied death)
                || !instanceId.equals(death.instanceId())
                || !eligibleEntityIds.contains(death.entityId())
                || !processedEntityIds.add(death.entityId())) {
            return;
        }
        if (death.playerAttributed() && qualifyingKills < requiredKills) {
            qualifyingKills++;
        }
    }

    @Override
    public boolean isComplete() {
        return qualifyingKills >= requiredKills;
    }

    @Override
    public boolean isExhausted() {
        return processedEntityIds.size() >= eligibleEntityIds.size();
    }

    @Override
    public int currentProgress() {
        return qualifyingKills;
    }

    @Override
    public int requiredProgress() {
        return requiredKills;
    }

    @Override
    public int totalMobs() {
        return eligibleEntityIds.size();
    }

    @Override
    public int remainingMobs() {
        return Math.max(0, eligibleEntityIds.size() - processedEntityIds.size());
    }
}