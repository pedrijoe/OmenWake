package com.pedrijoe.omenwake.catalog;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ProtectionConsumedPayload(String titleKey, int remainingCharges) implements CustomPacketPayload {
    public static final Type<ProtectionConsumedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("omenwake", "protection_consumed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProtectionConsumedPayload> STREAM_CODEC =
            StreamCodec.of(ProtectionConsumedPayload::encode, ProtectionConsumedPayload::decode);

    public ProtectionConsumedPayload {
        if (remainingCharges < 0 || titleKey.length() > 256) {
            throw new IllegalArgumentException("Protection notification is invalid");
        }
    }

    @Override
    public Type<ProtectionConsumedPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProtectionConsumedPayload payload) {
        buffer.writeUtf(payload.titleKey(), 256);
        buffer.writeVarInt(payload.remainingCharges());
    }

    private static ProtectionConsumedPayload decode(RegistryFriendlyByteBuf buffer) {
        String titleKey = buffer.readUtf(256);
        int remainingCharges = buffer.readVarInt();
        if (remainingCharges < 0) {
            throw new IllegalArgumentException("Negative protection charge count");
        }
        return new ProtectionConsumedPayload(titleKey, remainingCharges);
    }
}
