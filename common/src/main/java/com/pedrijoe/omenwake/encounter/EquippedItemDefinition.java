package com.pedrijoe.omenwake.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record EquippedItemDefinition(Identifier itemId, float dropChance) {
    public static final Codec<EquippedItemDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(EquippedItemDefinition::itemId),
            Codec.FLOAT.fieldOf("drop_chance").forGetter(EquippedItemDefinition::dropChance)
    ).apply(instance, EquippedItemDefinition::new));

    public EquippedItemDefinition {
        if (!Float.isFinite(dropChance) || dropChance < 0.0F || dropChance > 1.0F) {
            throw new IllegalArgumentException("drop_chance must be finite and within [0, 1]");
        }
    }
}
