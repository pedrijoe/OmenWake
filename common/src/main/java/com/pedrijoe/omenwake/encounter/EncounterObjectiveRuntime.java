package com.pedrijoe.omenwake.encounter;

public interface EncounterObjectiveRuntime {
    void onSignal(ObjectiveSignal signal);

    boolean isComplete();

    default boolean isExhausted() {
        return false;
    }

    int currentProgress();

    int requiredProgress();

    default int totalMobs() {
        return 0;
    }

    default int remainingMobs() {
        return 0;
    }
}