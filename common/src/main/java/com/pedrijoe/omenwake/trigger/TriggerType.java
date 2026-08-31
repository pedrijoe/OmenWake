package com.pedrijoe.omenwake.trigger;

import com.mojang.serialization.Codec;

public enum TriggerType {
    BLOCK_BROKEN,
    CONTAINER_OPENED,
    FURNACE_OUTPUT_TAKEN,
    ENTITY_KILLED,
    SLEEP_STARTED,
    SLEEP_ENDED,
    CROP_HARVESTED,
    FISH_CAUGHT,
    ITEM_CRAFTED,
    ITEM_PICKED_UP,
    TRADE_COMPLETED,
    ANVIL_OUTPUT_TAKEN,
    BIOME_ENTERED,
    ACTION_THRESHOLD_REACHED;

    public static final Codec<TriggerType> CODEC = Codec.STRING.xmap(
            name -> TriggerType.valueOf(name.toUpperCase(java.util.Locale.ROOT)),
            triggerType -> triggerType.name().toLowerCase(java.util.Locale.ROOT));
}
