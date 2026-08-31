package com.pedrijoe.omenwake.encounter;

import java.util.UUID;

public sealed interface ObjectiveSignal permits ObjectiveSignal.EncounterEntityDied {
    UUID instanceId();

    long serverTick();

    record EncounterEntityDied(UUID instanceId, UUID entityId, boolean playerAttributed,
                               long serverTick) implements ObjectiveSignal {
    }
}