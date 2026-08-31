package com.pedrijoe.omenwake.encounter;

import net.minecraft.server.level.ServerPlayer;

public interface ProtectionNotifier {
    void notify(ServerPlayer player, String titleKey, int remainingCharges);
}