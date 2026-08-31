package com.pedrijoe.omenwake.trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record TriggerContext(
        ServerPlayer player,
        TriggerType type,
        Identifier subject,
        BlockPos position,
        ServerLevel level,
        TriggerData data
) {
        public TriggerContext {
                if (player == null || type == null || subject == null || position == null || level == null || data == null) {
                        throw new IllegalArgumentException("Trigger context fields must be present");
                }
        }
}