package com.pedrijoe.omenwake.catalog;

import java.util.UUID;

public interface PlayerEncounterDataRepository {
    PlayerEncounterData get(UUID playerId);

    void set(UUID playerId, PlayerEncounterData data);
}