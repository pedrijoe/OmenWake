package com.pedrijoe.omenwake.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record EncounterMobEntry(Identifier entityType, int weight, Optional<EntityEquipmentDefinition> equipment) {
    public static final Codec<EncounterMobEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("entity_type").forGetter(EncounterMobEntry::entityType),
            Codec.INT.fieldOf("weight").forGetter(EncounterMobEntry::weight),
            EntityEquipmentDefinition.CODEC.optionalFieldOf("equipment")
                    .forGetter(EncounterMobEntry::equipment)
    ).apply(instance, EncounterMobEntry::new));

    public EncounterMobEntry(Identifier entityType, int weight) {
        this(entityType, weight, Optional.empty());
    }

    public EncounterMobEntry {
        equipment = equipment == null ? Optional.empty() : equipment;
    }
}
