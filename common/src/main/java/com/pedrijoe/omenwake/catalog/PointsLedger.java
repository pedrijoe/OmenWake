package com.pedrijoe.omenwake.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

public record PointsLedger(int lifetimePoints, int spentPoints, Set<Identifier> claimedMilestones) {
    public static final PointsLedger EMPTY = new PointsLedger(0, 0, Set.of());
    public static final Codec<PointsLedger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("lifetime").forGetter(PointsLedger::lifetimePoints),
            Codec.INT.fieldOf("spent").forGetter(PointsLedger::spentPoints),
                Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                    .fieldOf("claimed_milestones").forGetter(PointsLedger::claimedMilestones)
    ).apply(instance, PointsLedger::new));

    public PointsLedger {
        claimedMilestones = Set.copyOf(claimedMilestones);
        if (lifetimePoints < 0 || spentPoints < 0 || spentPoints > lifetimePoints) {
            throw new IllegalArgumentException("Point totals must be non-negative and spent points cannot exceed lifetime points");
        }
    }

    public int availablePoints() {
        return lifetimePoints - spentPoints;
    }
}
