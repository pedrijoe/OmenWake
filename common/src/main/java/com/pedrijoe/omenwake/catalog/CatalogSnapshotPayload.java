package com.pedrijoe.omenwake.catalog;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CatalogSnapshotPayload(VisibleCatalogSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<CatalogSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("omenwake", "catalog_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CatalogSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(CatalogSnapshotPayload::encode, CatalogSnapshotPayload::decode);

    @Override
    public Type<CatalogSnapshotPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CatalogSnapshotPayload payload) {
        VisibleCatalogSnapshot snapshot = payload.snapshot();
        buffer.writeVarInt(snapshot.protocolVersion());
        buffer.writeVarLong(snapshot.revision());
        buffer.writeVarInt(snapshot.totalEntries());
        buffer.writeVarInt(snapshot.discoveredCount());
        buffer.writeVarInt(snapshot.completedCount());
        buffer.writeVarInt(snapshot.lifetimePoints());
        buffer.writeVarInt(snapshot.availablePoints());
        buffer.writeVarInt(snapshot.entries().size());
        for (VisibleEncounterEntry entry : snapshot.entries()) {
            if (entry instanceof VisibleEncounterEntry.Unknown unknown) {
                buffer.writeBoolean(false);
                buffer.writeVarInt(unknown.slot());
                continue;
            }
            VisibleEncounterEntry.Known known = (VisibleEncounterEntry.Known) entry;
            buffer.writeBoolean(true);
            writeString(buffer, known.encounterId());
            buffer.writeVarInt(known.state().ordinal());
            writeString(buffer, known.titleKey());
            writeString(buffer, known.descriptionKey());
            writeString(buffer, known.objectiveKey());
            writeString(buffer, known.triggerKey());
            buffer.writeVarInt(known.activationChancePercent());
            buffer.writeVarInt(known.participationCount());
            buffer.writeVarInt(known.completionCount());
            buffer.writeVarInt(known.protectionCharges());
            buffer.writeVarInt(known.completedVariantKeys().size());
            for (String variant : known.completedVariantKeys()) {
                writeString(buffer, variant);
            }
        }
    }

    private static CatalogSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        int protocol = buffer.readVarInt();
        long revision = buffer.readVarLong();
        int total = readBoundedCount(buffer, VisibleCatalogSnapshot.MAX_ENTRIES);
        int discovered = buffer.readVarInt();
        int completed = buffer.readVarInt();
        int lifetime = buffer.readVarInt();
        int available = buffer.readVarInt();
        int entryCount = readBoundedCount(buffer, VisibleCatalogSnapshot.MAX_ENTRIES);
        java.util.List<VisibleEncounterEntry> entries = new java.util.ArrayList<>();
        for (int index = 0; index < entryCount; index++) {
            if (!buffer.readBoolean()) {
                entries.add(new VisibleEncounterEntry.Unknown(buffer.readVarInt()));
                continue;
            }
            String id = readString(buffer);
            int stateOrdinal = buffer.readVarInt();
            if (stateOrdinal < 0 || stateOrdinal >= DiscoveryState.values().length) {
                throw new IllegalArgumentException("Invalid catalog state ordinal");
            }
            String title = readString(buffer);
            String description = readString(buffer);
            String objective = readString(buffer);
            String trigger = readString(buffer);
            int activationChancePercent = readBoundedCount(buffer, 100);
            int participation = readNonNegative(buffer);
            int completions = readNonNegative(buffer);
            int protection = readNonNegative(buffer);
            int variantCount = readBoundedCount(buffer, 64);
            java.util.List<String> variants = new java.util.ArrayList<>();
            for (int variant = 0; variant < variantCount; variant++) {
                variants.add(readString(buffer));
            }
            entries.add(new VisibleEncounterEntry.Known(id, DiscoveryState.values()[stateOrdinal], title,
                    description, objective, trigger, activationChancePercent, participation, completions, protection, variants));
        }
        return new CatalogSnapshotPayload(new VisibleCatalogSnapshot(protocol, revision, total, discovered,
                completed, lifetime, available, entries));
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value) {
        buffer.writeUtf(value, 256);
    }

    private static String readString(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf(256);
    }

    private static int readNonNegative(RegistryFriendlyByteBuf buffer) {
        int value = buffer.readVarInt();
        if (value < 0) {
            throw new IllegalArgumentException("Negative catalog value");
        }
        return value;
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buffer, int maximum) {
        int value = readNonNegative(buffer);
        if (value > maximum) {
            throw new IllegalArgumentException("Catalog payload count exceeds limit");
        }
        return value;
    }
}
