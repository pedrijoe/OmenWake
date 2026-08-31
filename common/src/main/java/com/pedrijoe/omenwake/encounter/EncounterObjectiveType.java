package com.pedrijoe.omenwake.encounter;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum EncounterObjectiveType {
    PLAYER_KILL_COUNT,
    DEFEAT_TARGET,
    CLEAR_WAVES,
    DELIVER_ITEMS,
    DEFEND_ENTITY,
    CHAINED_DELIVERY;

    public static final Codec<EncounterObjectiveType> CODEC = Codec.STRING.xmap(
            name -> EncounterObjectiveType.valueOf(name.toUpperCase(Locale.ROOT)),
            objective -> objective.name().toLowerCase(Locale.ROOT));
}
