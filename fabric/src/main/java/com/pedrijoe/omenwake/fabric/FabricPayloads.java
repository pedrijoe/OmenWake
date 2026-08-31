package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.catalog.CatalogSnapshotPayload;
import com.pedrijoe.omenwake.catalog.OpenCatalogPayload;
import com.pedrijoe.omenwake.catalog.ProtectionConsumedPayload;
import com.pedrijoe.omenwake.encounter.EncounterFinishedPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class FabricPayloads {
    private FabricPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(
                CatalogSnapshotPayload.TYPE, CatalogSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                OpenCatalogPayload.TYPE, OpenCatalogPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                ProtectionConsumedPayload.TYPE, ProtectionConsumedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                EncounterFinishedPayload.TYPE, EncounterFinishedPayload.STREAM_CODEC);
    }
}
