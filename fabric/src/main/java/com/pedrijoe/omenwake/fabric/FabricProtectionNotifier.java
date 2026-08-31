package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.encounter.ProtectionNotifier;
import com.pedrijoe.omenwake.catalog.ProtectionConsumedPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class FabricProtectionNotifier implements ProtectionNotifier {
    @Override
    public void notify(ServerPlayer player, String titleKey, int remainingCharges) {
        if (ServerPlayNetworking.canSend(player, ProtectionConsumedPayload.TYPE)) {
            ServerPlayNetworking.send(player, new ProtectionConsumedPayload(titleKey, remainingCharges));
        }
    }
}
