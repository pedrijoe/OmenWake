package com.pedrijoe.omenwake.encounter;

import net.minecraft.resources.Identifier;

import java.util.UUID;

public interface EncounterProtectionGate {
    ProtectionDecision checkAndConsume(UUID playerId, Identifier encounterId);
}