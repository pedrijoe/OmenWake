package com.pedrijoe.omenwake.encounter;

import net.minecraft.resources.Identifier;

import java.util.UUID;

public interface CooldownService {
    CooldownCheck check(UUID playerId, Identifier encounterId, long now);

    void commit(UUID playerId, Identifier encounterId, long globalDurationTicks,
                long encounterDurationTicks, long now);

    void clear(UUID playerId);
}