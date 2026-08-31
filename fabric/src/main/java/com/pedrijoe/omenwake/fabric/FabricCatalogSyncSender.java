package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.catalog.CatalogProjectionService;
import com.pedrijoe.omenwake.catalog.CatalogSnapshotPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class FabricCatalogSyncSender {
    private final CatalogProjectionService projection;

    public FabricCatalogSyncSender(CatalogProjectionService projection) {
        this.projection = projection;
    }

    public void syncOwnCatalog(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, CatalogSnapshotPayload.TYPE)) {
            ServerPlayNetworking.send(player, new CatalogSnapshotPayload(
                    projection.projectFor(player.getUUID())));
        }
    }
}
