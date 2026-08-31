package com.pedrijoe.omenwake.encounter;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EncounterFinishedPayload(String titleKey, EncounterOutcome outcome) implements CustomPacketPayload {
    public static final Type<EncounterFinishedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("omenwake", "encounter_finished"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncounterFinishedPayload> STREAM_CODEC =
            StreamCodec.of(EncounterFinishedPayload::encode, EncounterFinishedPayload::decode);

    public EncounterFinishedPayload {
        if (titleKey == null || titleKey.length() > 256 || outcome == null) {
            throw new IllegalArgumentException("Invalid encounter finished payload arguments");
        }
    }

    @Override
    public Type<EncounterFinishedPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, EncounterFinishedPayload payload) {
        buffer.writeUtf(payload.titleKey(), 256);
        buffer.writeEnum(payload.outcome());
    }

    private static EncounterFinishedPayload decode(RegistryFriendlyByteBuf buffer) {
        String titleKey = buffer.readUtf(256);
        EncounterOutcome outcome = buffer.readEnum(EncounterOutcome.class);
        return new EncounterFinishedPayload(titleKey, outcome);
    }
}
