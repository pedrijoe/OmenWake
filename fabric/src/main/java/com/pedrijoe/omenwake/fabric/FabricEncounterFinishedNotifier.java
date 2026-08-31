package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.encounter.EncounterFinishedNotifier;
import com.pedrijoe.omenwake.encounter.EncounterFinishedPayload;
import com.pedrijoe.omenwake.encounter.EncounterOutcome;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class FabricEncounterFinishedNotifier implements EncounterFinishedNotifier {
    @Override
    public void notifyFinished(ServerPlayer player, String titleKey, EncounterOutcome outcome) {
        if (ServerPlayNetworking.canSend(player, EncounterFinishedPayload.TYPE)) {
            ServerPlayNetworking.send(player, new EncounterFinishedPayload(titleKey, outcome));
        }
    }
}
