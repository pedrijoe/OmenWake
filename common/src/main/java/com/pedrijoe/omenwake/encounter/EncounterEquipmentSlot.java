package com.pedrijoe.omenwake.encounter;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Locale;

public enum EncounterEquipmentSlot {
    HEAD,
    CHEST,
    LEGS,
    FEET,
    MAINHAND,
    OFFHAND;

    public static final Codec<EncounterEquipmentSlot> CODEC = Codec.STRING.xmap(
            name -> valueOf(name.toUpperCase(Locale.ROOT)),
            slot -> slot.name().toLowerCase(Locale.ROOT));

    public EquipmentSlot vanillaSlot() {
        return EquipmentSlot.valueOf(name());
    }
}
