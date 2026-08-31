package com.pedrijoe.omenwake.encounter;

public record EncounterDuration(long ticks) {
    public EncounterDuration {
        if (ticks <= 0) {
            throw new IllegalArgumentException("Encounter duration must be positive");
        }
    }
}