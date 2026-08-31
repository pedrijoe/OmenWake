package com.pedrijoe.omenwake.encounter;

import net.minecraft.server.level.ServerPlayer;

public final class NoOpProtectionNotifier implements ProtectionNotifier {
    @Override
    public void notify(ServerPlayer player, String titleKey, int remainingCharges) {
    }
}