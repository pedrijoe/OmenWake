package com.pedrijoe.omenwake.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PlayerEncounterData(
        Map<Identifier, EncounterProgress> encounters,
        PointsLedger points,
        List<UUID> appliedInstanceResults,
        int schemaVersion
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final PlayerEncounterData EMPTY = new PlayerEncounterData(
            Map.of(), PointsLedger.EMPTY, List.of(), CURRENT_SCHEMA_VERSION);
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<PlayerEncounterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, EncounterProgress.CODEC)
                    .fieldOf("encounters").forGetter(PlayerEncounterData::encounters),
            PointsLedger.CODEC.fieldOf("points").forGetter(PlayerEncounterData::points),
            UUID_CODEC.listOf().fieldOf("applied_instance_results")
                    .forGetter(PlayerEncounterData::appliedInstanceResults),
            Codec.INT.fieldOf("schema_version").forGetter(PlayerEncounterData::schemaVersion)
    ).apply(instance, PlayerEncounterData::new));

    public PlayerEncounterData {
        encounters = Map.copyOf(encounters);
        appliedInstanceResults = List.copyOf(appliedInstanceResults);
        if (points == null || schemaVersion <= 0 || appliedInstanceResults.size() > 256
                || Set.copyOf(appliedInstanceResults).size() != appliedInstanceResults.size()) {
            throw new IllegalArgumentException("Player encounter data is invalid");
        }
    }

    public static PlayerEncounterData empty() {
        return EMPTY;
    }
}