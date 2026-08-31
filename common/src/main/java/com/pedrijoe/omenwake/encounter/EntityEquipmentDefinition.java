package com.pedrijoe.omenwake.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record EntityEquipmentDefinition(Map<EncounterEquipmentSlot, EquippedItemDefinition> slots) {
    public static final Codec<EntityEquipmentDefinition> CODEC = Codec.unboundedMap(
            EncounterEquipmentSlot.CODEC, EquippedItemDefinition.CODEC)
            .xmap(EntityEquipmentDefinition::new, EntityEquipmentDefinition::slots);

    public EntityEquipmentDefinition {
        slots = Map.copyOf(slots);
        if (slots.size() > 1) {
            throw new IllegalArgumentException("An encounter mob may have only one equipment slot");
        }
    }
}
