package com.pedrijoe.omenwake.catalog;

import net.minecraft.resources.Identifier;

import java.util.Set;

public record ProgressionChange(
        boolean changed,
        PlayerEncounterData before,
        PlayerEncounterData after,
        Set<Identifier> claimedMilestones,
        int pointsAwarded
) {
    public ProgressionChange {
        claimedMilestones = Set.copyOf(claimedMilestones);
    }
}