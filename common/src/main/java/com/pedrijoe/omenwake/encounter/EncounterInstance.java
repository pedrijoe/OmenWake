package com.pedrijoe.omenwake.encounter;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class EncounterInstance {
    private final UUID id = UUID.randomUUID();
    private final ServerLevel level;
    private final UUID ownerId;
    private final Identifier definitionId;
    private final String catalogTitleKey;
    private final int discoveryPoints;
    private final int completionPoints;
    private final boolean debug;
    private final java.util.Map<UUID, EncounterEntityRole> entityRoles = new java.util.HashMap<>();
    private EncounterObjectiveRuntime objective;
    private EncounterState state = EncounterState.PREPARING;
    private EncounterOutcome outcome;
    private long activatedAtTick = -1;
    private long expiresAtTick = -1;
    private ServerBossEvent bossEvent;

    public EncounterInstance(ServerLevel level, UUID ownerId, Identifier definitionId,
                             String catalogTitleKey, int discoveryPoints, int completionPoints, boolean debug) {
        this.level = level;
        this.ownerId = ownerId;
        this.definitionId = definitionId;
        this.catalogTitleKey = catalogTitleKey;
        this.discoveryPoints = discoveryPoints;
        this.completionPoints = completionPoints;
        this.debug = debug;
    }

    public EncounterInstance(ServerLevel level, UUID ownerId, Identifier definitionId,
                             int discoveryPoints, int completionPoints, boolean debug) {
        this(level, ownerId, definitionId, "screen.omenwake.catalog.unknown", discoveryPoints, completionPoints, debug);
    }

    public UUID id() {
        return id;
    }

    public ServerLevel level() {
        return level;
    }

    public String entityTag() {
        return "omenwake_encounter:" + id;
    }

    public void addEntity(UUID entityId, EncounterEntityRole role) {
        if (entityRoles.putIfAbsent(entityId, role) != null) {
            throw new IllegalStateException("Encounter entity is already registered");
        }
    }

    public void setObjective(EncounterObjectiveRuntime objective) {
        if (this.objective != null) {
            throw new IllegalStateException("Encounter objective is already initialized");
        }
        this.objective = objective;
    }

    public void activate(long currentTick, EncounterDuration duration) {
        if (state != EncounterState.PREPARING) {
            throw new IllegalStateException("Encounter can only be activated from PREPARING");
        }
        activatedAtTick = currentTick;
        expiresAtTick = currentTick + duration.ticks();
        state = EncounterState.ACTIVE;
        updateBossEvent();
    }

    public void updateBossEvent() {
        if (debug || state != EncounterState.ACTIVE) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            if (bossEvent != null) {
                bossEvent.removeAllPlayers();
            }
            return;
        }
        Component name = createBossEventName();
        float progress = calculateProgress();
        if (bossEvent == null) {
            bossEvent = new ServerBossEvent(UUID.randomUUID(), name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            bossEvent.setProgress(progress);
            bossEvent.addPlayer(owner);
        } else {
            if (!bossEvent.getPlayers().contains(owner)) {
                bossEvent.addPlayer(owner);
            }
            bossEvent.setName(name);
            bossEvent.setProgress(progress);
        }
    }

    private Component createBossEventName() {
        if (objective == null || objective.totalMobs() <= 0) {
            return Component.translatable(catalogTitleKey);
        }
        return Component.translatable(catalogTitleKey)
                .append(" (")
                .append(String.valueOf(objective.remainingMobs()))
                .append("/")
                .append(String.valueOf(objective.totalMobs()))
                .append(")");
    }

    private float calculateProgress() {
        if (objective == null || objective.totalMobs() <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, Math.max(0.0F, (float) objective.remainingMobs() / (float) objective.totalMobs()));
    }

    public boolean update(long currentTick) {
        if (state != EncounterState.ACTIVE) {
            return state == EncounterState.FINISHED;
        }
        if (objective != null && objective.isComplete()) {
            finish(EncounterOutcome.COMPLETED);
        } else if (objective != null && objective.isExhausted()) {
            finish(EncounterOutcome.FAILED);
        } else if (currentTick >= expiresAtTick) {
            finish(EncounterOutcome.EVADED);
        }
        return state == EncounterState.FINISHED;
    }

    public boolean update() {
        return update(level.getGameTime());
    }

    public boolean isFinished() {
        return state == EncounterState.FINISHED;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public Set<UUID> entityIds() {
        return Set.copyOf(entityRoles.keySet());
    }

    public EncounterEntityRole roleFor(UUID entityId) {
        return entityRoles.get(entityId);
    }

    public void onSignal(ObjectiveSignal signal) {
        if (state == EncounterState.ACTIVE && objective != null) {
            objective.onSignal(signal);
        }
    }

    public EncounterObjectiveRuntime objective() {
        return objective;
    }

    public Identifier definitionId() {
        return definitionId;
    }

    public String catalogTitleKey() {
        return catalogTitleKey;
    }

    public int discoveryPoints() {
        return discoveryPoints;
    }

    public int completionPoints() {
        return completionPoints;
    }

    public boolean debug() {
        return debug;
    }

    public EncounterState state() {
        return state;
    }

    public EncounterOutcome outcome() {
        return outcome;
    }

    public long activatedAtTick() {
        return activatedAtTick;
    }

    public long expiresAtTick() {
        return expiresAtTick;
    }

    public boolean finish(EncounterOutcome requestedOutcome) {
        if (state == EncounterState.FINISHED) {
            return false;
        }
        if (state != EncounterState.ACTIVE && requestedOutcome != EncounterOutcome.CANCELLED) {
            return false;
        }
        state = EncounterState.CLEANING_UP;
        outcome = requestedOutcome;
        cleanup();
        state = EncounterState.FINISHED;
        return true;
    }

    private void cleanup() {
        if (bossEvent != null) {
            bossEvent.removeAllPlayers();
            bossEvent.setVisible(false);
            bossEvent = null;
        }
        for (UUID entityId : entityRoles.keySet()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }
}