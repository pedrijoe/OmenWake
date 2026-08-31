package com.pedrijoe.omenwake.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

public record EncounterProgress(
        DiscoveryState state,
        int participationCount,
        int completionCount,
        int evasionCount,
        Set<Identifier> completedVariants,
        int protectionCharges
) {
    public static final EncounterProgress EMPTY = new EncounterProgress(
            DiscoveryState.UNDISCOVERED, 0, 0, 0, Set.of(), 0);
    public static final Codec<EncounterProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(name -> DiscoveryState.valueOf(name.toUpperCase(java.util.Locale.ROOT)),
                    state -> state.name().toLowerCase(java.util.Locale.ROOT))
                    .fieldOf("state").forGetter(EncounterProgress::state),
            Codec.INT.fieldOf("participation_count").forGetter(EncounterProgress::participationCount),
            Codec.INT.fieldOf("completion_count").forGetter(EncounterProgress::completionCount),
            Codec.INT.fieldOf("evasion_count").forGetter(EncounterProgress::evasionCount),
            Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                    .fieldOf("completed_variants").forGetter(EncounterProgress::completedVariants),
            Codec.INT.fieldOf("protection_charges").forGetter(EncounterProgress::protectionCharges)
    ).apply(instance, EncounterProgress::new));

    public EncounterProgress {
        completedVariants = Set.copyOf(completedVariants);
        if (participationCount < 0 || completionCount < 0 || evasionCount < 0
                || completionCount > participationCount || evasionCount > participationCount
                || protectionCharges < 0) {
            throw new IllegalArgumentException("Encounter progress counters must be non-negative and consistent");
        }
        if (state == DiscoveryState.UNDISCOVERED
                && (participationCount != 0 || completionCount != 0 || evasionCount != 0
                || !completedVariants.isEmpty() || protectionCharges != 0)) {
            throw new IllegalArgumentException("Undiscovered encounter progress must be empty");
        }
        if (completionCount > 0 && state != DiscoveryState.COMPLETED) {
            throw new IllegalArgumentException("Completed encounters must have COMPLETED state");
        }
    }
}