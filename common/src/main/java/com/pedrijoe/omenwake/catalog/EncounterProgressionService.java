package com.pedrijoe.omenwake.catalog;

import com.pedrijoe.omenwake.encounter.EncounterDefinition;
import com.pedrijoe.omenwake.encounter.EncounterInstance;
import com.pedrijoe.omenwake.encounter.EncounterOutcome;
import com.pedrijoe.omenwake.encounter.EncounterProtectionGate;
import com.pedrijoe.omenwake.encounter.ProtectionDecision;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EncounterProgressionService implements EncounterProtectionGate {
    private static final Identifier DISCOVERED_SUFFIX = Identifier.parse("omenwake:milestone/discovered");
    private static final Identifier COMPLETED_SUFFIX = Identifier.parse("omenwake:milestone/completed");
    private final PlayerEncounterDataRepository repository;
    private ProgressionChangeListener changeListener = ownerId -> {
    };

    public EncounterProgressionService(PlayerEncounterDataRepository repository) {
        this.repository = repository;
    }

    public void setChangeListener(ProgressionChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    public ProgressionChange onActivated(EncounterInstance instance) {
        if (instance.debug()) {
            PlayerEncounterData data = repository.get(instance.ownerId());
            return unchanged(data);
        }
        PlayerEncounterData before = repository.get(instance.ownerId());
        EncounterProgress current = before.encounters().getOrDefault(instance.definitionId(), EncounterProgress.EMPTY);
        Set<Identifier> claimed = new HashSet<>(before.points().claimedMilestones());
        int points = 0;
        DiscoveryState state = current.state();
        if (state == DiscoveryState.UNDISCOVERED) {
            Identifier milestone = milestone(instance.definitionId(), DISCOVERED_SUFFIX);
            points = claimPoints(claimed, milestone, instanceDefinitionPoints(instance, true));
            state = DiscoveryState.DISCOVERED;
        }
        EncounterProgress activated = new EncounterProgress(
                state, current.participationCount() + 1, current.completionCount(),
                current.evasionCount(), current.completedVariants(), current.protectionCharges());
        Map<Identifier, EncounterProgress> encounters = new HashMap<>(before.encounters());
        encounters.put(instance.definitionId(), activated);
        PlayerEncounterData after = updated(before, encounters, claimed, points);
        repository.set(instance.ownerId(), after);
        changeListener.onChanged(instance.ownerId());
        return change(before, after, claimed, points);
    }

    public ProgressionChange onFinished(EncounterInstance instance, EncounterOutcome outcome) {
        if (instance.debug() || outcome == EncounterOutcome.CANCELLED) {
            return unchanged(repository.get(instance.ownerId()));
        }
        PlayerEncounterData before = repository.get(instance.ownerId());
        if (before.appliedInstanceResults().contains(instance.id())) {
            return unchanged(before);
        }
        EncounterProgress current = before.encounters().getOrDefault(instance.definitionId(), EncounterProgress.EMPTY);
        DiscoveryState state = current.state() == DiscoveryState.UNDISCOVERED
                ? DiscoveryState.DISCOVERED : current.state();
        int participation = current.participationCount();
        int completion = current.completionCount();
        int evasions = current.evasionCount();
        int protection = current.protectionCharges();
        Set<Identifier> claimed = new HashSet<>(before.points().claimedMilestones());
        int points = 0;
        if (outcome == EncounterOutcome.COMPLETED) {
            completion++;
            state = DiscoveryState.COMPLETED;
            protection = completion;
            points += claimPoints(claimed, milestone(instance.definitionId(), COMPLETED_SUFFIX),
                    instanceDefinitionPoints(instance, false));
        } else if (outcome == EncounterOutcome.EVADED) {
            evasions++;
        }
        EncounterProgress updatedProgress = new EncounterProgress(
                state, participation, completion, evasions, current.completedVariants(), protection);
        Map<Identifier, EncounterProgress> encounters = new HashMap<>(before.encounters());
        encounters.put(instance.definitionId(), updatedProgress);
        List<UUID> applied = new ArrayList<>(before.appliedInstanceResults());
        applied.add(instance.id());
        if (applied.size() > 256) {
            applied.remove(0);
        }
        PlayerEncounterData after = new PlayerEncounterData(encounters,
                new PointsLedger(before.points().lifetimePoints() + points,
                        before.points().spentPoints(), claimed), applied, before.schemaVersion());
        repository.set(instance.ownerId(), after);
        changeListener.onChanged(instance.ownerId());
        return change(before, after, claimed, points);
    }

    @Override
    public ProtectionDecision checkAndConsume(UUID playerId, Identifier encounterId) {
        PlayerEncounterData before = repository.get(playerId);
        EncounterProgress current = before.encounters().get(encounterId);
        if (current == null || current.protectionCharges() <= 0) {
            return new ProtectionDecision.NotProtected();
        }
        EncounterProgress consumed = new EncounterProgress(current.state(), current.participationCount(),
                current.completionCount(), current.evasionCount(), current.completedVariants(),
                current.protectionCharges() - 1);
        Map<Identifier, EncounterProgress> encounters = new HashMap<>(before.encounters());
        encounters.put(encounterId, consumed);
        repository.set(playerId, new PlayerEncounterData(encounters, before.points(),
                before.appliedInstanceResults(), before.schemaVersion()));
        changeListener.onChanged(playerId);
        return new ProtectionDecision.Consumed(consumed.protectionCharges());
    }

    public PlayerEncounterData get(UUID playerId) {
        return repository.get(playerId);
    }

    public int participationCount(UUID playerId, Identifier encounterId) {
        return repository.get(playerId).encounters()
                .getOrDefault(encounterId, EncounterProgress.EMPTY)
                .participationCount();
    }

    public void reset(UUID playerId) {
        repository.set(playerId, PlayerEncounterData.empty());
        changeListener.onChanged(playerId);
    }

    public int discoverAll(UUID playerId, Collection<Identifier> encounterIds) {
        PlayerEncounterData before = repository.get(playerId);
        Map<Identifier, EncounterProgress> encounters = new HashMap<>(before.encounters());
        int changed = 0;
        for (Identifier encounterId : encounterIds) {
            EncounterProgress current = encounters.getOrDefault(encounterId, EncounterProgress.EMPTY);
            if (current.state() == DiscoveryState.UNDISCOVERED) {
                encounters.put(encounterId, new EncounterProgress(DiscoveryState.DISCOVERED, 0, 0, 0,
                        Set.of(), 0));
                changed++;
            }
        }
        if (changed > 0) {
            repository.set(playerId, new PlayerEncounterData(encounters, before.points(),
                    before.appliedInstanceResults(), before.schemaVersion()));
            changeListener.onChanged(playerId);
        }
        return changed;
    }

    public int undiscoverUnattempted(UUID playerId, Collection<Identifier> encounterIds) {
        PlayerEncounterData before = repository.get(playerId);
        Map<Identifier, EncounterProgress> encounters = new HashMap<>(before.encounters());
        int changed = 0;
        for (Identifier encounterId : encounterIds) {
            EncounterProgress current = encounters.get(encounterId);
            if (current != null && current.participationCount() == 0) {
                encounters.remove(encounterId);
                changed++;
            }
        }
        if (changed > 0) {
            repository.set(playerId, new PlayerEncounterData(encounters, before.points(),
                    before.appliedInstanceResults(), before.schemaVersion()));
            changeListener.onChanged(playerId);
        }
        return changed;
    }

    public void setProtection(UUID playerId, Identifier encounterId, int charges) {
        if (charges < 0) {
            throw new IllegalArgumentException("Protection charges must be non-negative");
        }
        PlayerEncounterData before = repository.get(playerId);
        EncounterProgress current = before.encounters().getOrDefault(encounterId, EncounterProgress.EMPTY);
        if (current.state() == DiscoveryState.UNDISCOVERED) {
            current = new EncounterProgress(DiscoveryState.DISCOVERED, 0, 0, 0,
                    Set.of(), charges);
        } else {
            current = new EncounterProgress(current.state(), current.participationCount(),
                    current.completionCount(), current.evasionCount(), current.completedVariants(), charges);
        }
        Map<Identifier, EncounterProgress> encounters = new HashMap<>(before.encounters());
        encounters.put(encounterId, current);
        repository.set(playerId, new PlayerEncounterData(encounters, before.points(),
                before.appliedInstanceResults(), before.schemaVersion()));
        changeListener.onChanged(playerId);
    }

    public void addPoints(UUID playerId, int points) {
        if (points < 0) {
            throw new IllegalArgumentException("Points must be non-negative");
        }
        PlayerEncounterData before = repository.get(playerId);
        int lifetime = Math.addExact(before.points().lifetimePoints(), points);
        repository.set(playerId, new PlayerEncounterData(before.encounters(),
                new PointsLedger(lifetime, before.points().spentPoints(), before.points().claimedMilestones()),
                before.appliedInstanceResults(), before.schemaVersion()));
        changeListener.onChanged(playerId);
    }

    private static int claimPoints(Set<Identifier> claimed, Identifier milestone, int points) {
        return claimed.add(milestone) ? points : 0;
    }

    private static Identifier milestone(Identifier encounterId, Identifier suffix) {
        return Identifier.parse(encounterId + "/" + suffix.getPath());
    }

    private static int instanceDefinitionPoints(EncounterInstance instance, boolean discovery) {
        return discovery ? instance.discoveryPoints() : instance.completionPoints();
    }

    private static PlayerEncounterData updated(PlayerEncounterData before,
                                               Map<Identifier, EncounterProgress> encounters,
                                               Set<Identifier> claimed, int points) {
        return new PlayerEncounterData(encounters,
                new PointsLedger(before.points().lifetimePoints() + points,
                        before.points().spentPoints(), claimed), before.appliedInstanceResults(), before.schemaVersion());
    }

    private static ProgressionChange unchanged(PlayerEncounterData data) {
        return new ProgressionChange(false, data, data, Set.of(), 0);
    }

    private static ProgressionChange change(PlayerEncounterData before, PlayerEncounterData after,
                                            Set<Identifier> claimed, int points) {
        Set<Identifier> newClaims = new HashSet<>(after.points().claimedMilestones());
        newClaims.removeAll(before.points().claimedMilestones());
        return new ProgressionChange(!before.equals(after), before, after, newClaims, points);
    }
}