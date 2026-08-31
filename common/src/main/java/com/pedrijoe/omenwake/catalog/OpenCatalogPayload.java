package com.pedrijoe.omenwake.catalog;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenCatalogPayload() implements CustomPacketPayload {
    public static final Type<OpenCatalogPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("omenwake", "open_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCatalogPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenCatalogPayload());

    @Override
    public Type<OpenCatalogPayload> type() {
        return TYPE;
    }
}