package com.pedrijoe.omenwake.encounter;

import net.minecraft.server.level.ServerPlayer;

public interface EncounterFinishedNotifier {
    void notifyFinished(ServerPlayer player, String titleKey, EncounterOutcome outcome);
}
