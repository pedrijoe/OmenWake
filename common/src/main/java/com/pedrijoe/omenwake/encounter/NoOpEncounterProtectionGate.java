package com.pedrijoe.omenwake.encounter;

import net.minecraft.resources.Identifier;

import java.util.UUID;

public final class NoOpEncounterProtectionGate implements EncounterProtectionGate {
    @Override
    public ProtectionDecision checkAndConsume(UUID playerId, Identifier encounterId) {
        return new ProtectionDecision.NotProtected();
    }
}