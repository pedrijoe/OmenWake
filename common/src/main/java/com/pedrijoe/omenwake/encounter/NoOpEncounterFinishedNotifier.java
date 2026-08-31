package com.pedrijoe.omenwake.encounter;

import net.minecraft.server.level.ServerPlayer;

public final class NoOpEncounterFinishedNotifier implements EncounterFinishedNotifier {
    @Override
    public void notifyFinished(ServerPlayer player, String titleKey, EncounterOutcome outcome) {
    }
}
