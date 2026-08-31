package com.pedrijoe.omenwake.catalog;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InMemoryPlayerEncounterDataRepository implements PlayerEncounterDataRepository {
    private final Map<UUID, PlayerEncounterData> dataByPlayer = new HashMap<>();

    @Override
    public PlayerEncounterData get(UUID playerId) {
        return dataByPlayer.getOrDefault(playerId, PlayerEncounterData.empty());
    }

    @Override
    public void set(UUID playerId, PlayerEncounterData data) {
        dataByPlayer.put(playerId, data);
    }
}