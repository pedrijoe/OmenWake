package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.catalog.PlayerEncounterData;
import com.pedrijoe.omenwake.catalog.PlayerEncounterDataRepository;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FabricPlayerEncounterDataRepository implements PlayerEncounterDataRepository {
    public static final AttachmentType<PlayerEncounterData> ENCOUNTER_DATA = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("omenwake", "encounter_data"),
            builder -> builder.initializer(PlayerEncounterData::empty)
                    .persistent(PlayerEncounterData.CODEC)
                    .copyOnDeath());

    private final Map<UUID, ServerPlayer> onlinePlayers = new HashMap<>();

    public void track(ServerPlayer player) {
        onlinePlayers.put(player.getUUID(), player);
    }

    public void untrack(ServerPlayer player) {
        onlinePlayers.remove(player.getUUID());
    }

    public ServerPlayer onlinePlayer(UUID playerId) {
        return onlinePlayers.get(playerId);
    }

    @Override
    public PlayerEncounterData get(UUID playerId) {
        ServerPlayer player = requirePlayer(playerId);
        return player.getAttachedOrCreate(ENCOUNTER_DATA);
    }

    @Override
    public void set(UUID playerId, PlayerEncounterData data) {
        requirePlayer(playerId).setAttached(ENCOUNTER_DATA, data);
    }

    private ServerPlayer requirePlayer(UUID playerId) {
        ServerPlayer player = onlinePlayers.get(playerId);
        if (player == null) {
            throw new IllegalStateException("Cannot access Omenwake data for offline player " + playerId);
        }
        return player;
    }
}
