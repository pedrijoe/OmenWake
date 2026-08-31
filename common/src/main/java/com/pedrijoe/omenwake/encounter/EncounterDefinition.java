package com.pedrijoe.omenwake.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pedrijoe.omenwake.trigger.TriggerType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Immutable, data-driven template for a possible encounter loaded from {@code data/omenwake/encounters}.
 */
public record EncounterDefinition(
        TriggerType trigger,
        Identifier subject,
        float baseProbability,
        int playerCooldownTicks,
        EncounterDuration duration,
        java.util.List<EncounterMobEntry> mobs,
        int initialMobCount,
        int mobCountIncrease,
        float requiredKillRatio,
        EncounterObjectiveType objective,
        String catalogTitleKey,
        String catalogDescriptionKey,
        int discoveryPoints,
        int completionPoints
) {
        public EncounterDefinition {
                if (!Float.isFinite(baseProbability) || baseProbability < 0.0F || baseProbability > 1.0F) {
                        throw new IllegalArgumentException("base_probability must be finite and within [0, 1]");
                }
                if (playerCooldownTicks < 0) {
                        throw new IllegalArgumentException("player_cooldown_ticks must be non-negative");
                }
                if (duration == null) {
                        throw new IllegalArgumentException("duration must be present");
                }
                if (mobs.isEmpty() || mobs.stream().anyMatch(mob -> mob.weight() <= 0)) {
                        throw new IllegalArgumentException("mobs must contain entries with positive counts");
                }
                int configuredMobCount = mobs.stream().mapToInt(EncounterMobEntry::weight).sum();
                if (initialMobCount <= 0 || initialMobCount < mobs.size()) {
                        throw new IllegalArgumentException("initial_mob_count must cover every mob type");
                }
                if (mobCountIncrease < 0) {
                        throw new IllegalArgumentException("mob_count_increase must be non-negative");
                }
                if (!Float.isFinite(requiredKillRatio) || requiredKillRatio <= 0.0F
                        || requiredKillRatio > 1.0F) {
                        throw new IllegalArgumentException("required_kill_ratio must be within (0, 1]");
                }
                if (configuredMobCount <= 0) {
                        throw new IllegalArgumentException("Encounter mob and kill configuration is invalid");
                }
        }

    public static final Codec<EncounterDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TriggerType.CODEC.fieldOf("trigger").forGetter(EncounterDefinition::trigger),
            Identifier.CODEC.fieldOf("subject").forGetter(EncounterDefinition::subject),
            Codec.FLOAT.fieldOf("base_probability").forGetter(EncounterDefinition::baseProbability),
            Codec.INT.fieldOf("player_cooldown_ticks").forGetter(EncounterDefinition::playerCooldownTicks),
            Codec.LONG.xmap(EncounterDuration::new, EncounterDuration::ticks)
                    .fieldOf("duration_ticks").forGetter(EncounterDefinition::duration),
            EncounterMobEntry.CODEC.listOf().fieldOf("mobs").forGetter(EncounterDefinition::mobs),
            Codec.INT.fieldOf("initial_mob_count").forGetter(EncounterDefinition::initialMobCount),
            Codec.INT.fieldOf("mob_count_increase").forGetter(EncounterDefinition::mobCountIncrease),
            Codec.FLOAT.fieldOf("required_kill_ratio").forGetter(EncounterDefinition::requiredKillRatio),
            EncounterObjectiveType.CODEC.fieldOf("objective").forGetter(EncounterDefinition::objective),
            Codec.STRING.fieldOf("catalog_title_key").forGetter(EncounterDefinition::catalogTitleKey),
            Codec.STRING.fieldOf("catalog_description_key").forGetter(EncounterDefinition::catalogDescriptionKey),
            Codec.INT.fieldOf("discovery_points").forGetter(EncounterDefinition::discoveryPoints),
            Codec.INT.fieldOf("completion_points").forGetter(EncounterDefinition::completionPoints)
    ).apply(instance, EncounterDefinition::new));

        public List<EncounterMobEntry> mobsForAttempt(int previousAttempts) {
                if (previousAttempts < 0) {
                        throw new IllegalArgumentException("Previous attempts must be non-negative");
                }
                int targetCount = Math.addExact(initialMobCount, Math.multiplyExact(mobCountIncrease, previousAttempts));
                int configuredTotal = mobs.stream().mapToInt(EncounterMobEntry::weight).sum();
                List<ScaledMobEntry> scaled = new ArrayList<>(mobs.size());
                int assigned = 0;
                for (EncounterMobEntry entry : mobs) {
                        double exact = (double) targetCount * entry.weight() / configuredTotal;
                        int count = (int) Math.floor(exact);
                        scaled.add(new ScaledMobEntry(entry.entityType(), entry.equipment(), count, exact - count));
                        assigned += count;
                }
                scaled.sort(Comparator.comparingDouble(ScaledMobEntry::remainder).reversed());
                for (int index = 0; index < targetCount - assigned; index++) {
                        ScaledMobEntry entry = scaled.get(index % scaled.size());
                        entry.count++;
                }
                return scaled.stream()
                                .map(entry -> new EncounterMobEntry(entry.entityType(), entry.count(), entry.equipment()))
                                .toList();
        }

        public int requiredKillsForAttempt(int previousAttempts) {
                int total = mobsForAttempt(previousAttempts).stream()
                                .mapToInt(EncounterMobEntry::weight)
                                .sum();
                return Math.max(1, Math.min(total, (int) Math.floor(total * requiredKillRatio)));
        }

        private static final class ScaledMobEntry {
                private final Identifier entityType;
                private final Optional<EntityEquipmentDefinition> equipment;
                private int count;
                private final double remainder;

                private ScaledMobEntry(Identifier entityType, Optional<EntityEquipmentDefinition> equipment, int count, double remainder) {
                        this.entityType = entityType;
                        this.equipment = equipment;
                        this.count = count;
                        this.remainder = remainder;
                }

                private Identifier entityType() {
                        return entityType;
                }

                private Optional<EntityEquipmentDefinition> equipment() {
                        return equipment;
                }

                private int count() {
                        return count;
                }

                private double remainder() {
                        return remainder;
                }
        }
}
