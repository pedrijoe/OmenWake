package com.pedrijoe.omenwake.encounter;

import com.pedrijoe.omenwake.EncounterTestingMode;
import com.pedrijoe.omenwake.trigger.TriggerContext;
import com.pedrijoe.omenwake.trigger.TriggerType;
import com.pedrijoe.omenwake.catalog.EncounterProgressionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class EncounterService {
    public static final long DEFAULT_GLOBAL_COOLDOWN_TICKS = 1200;
    private static final Logger LOGGER = LoggerFactory.getLogger("omenwake");
    private static final Identifier DEBUG_BLOCK = Identifier.withDefaultNamespace("diamond_block");
    private static final Identifier ZOMBIE_ID = Identifier.withDefaultNamespace("zombie");
    private static final double AGGRESSIVE_PARROT_ATTACK_DISTANCE_SQUARED = 2.25D;
    private static final long AGGRESSIVE_PARROT_ATTACK_INTERVAL_TICKS = 20L;
    private static final double AGGRESSIVE_CHICKEN_ATTACK_DISTANCE_SQUARED = 2.25D;
    private static final long AGGRESSIVE_CHICKEN_ATTACK_INTERVAL_TICKS = 20L;
    private final EncounterDefinitionRepository definitionRepository;
    private final CooldownService cooldownService;
    private final EncounterProtectionGate protectionGate;
    private final ProtectionNotifier protectionNotifier;
    private final EncounterFinishedNotifier finishedNotifier;
    private final EncounterProgressionService progressionService;
    private final EncounterTestingMode testingMode;
    private final EncounterSpawnPlanner spawnPlanner = new EncounterSpawnPlanner();
    private final List<EncounterInstance> activeInstances = new ArrayList<>();
    private final java.util.Map<UUID, UUID> entityToInstance = new java.util.HashMap<>();
    private final java.util.Map<Identifier, Double> probabilityOverrides = new java.util.HashMap<>();
    private boolean debugDiagnostics;

    public EncounterService(EncounterDefinitionRepository definitionRepository, CooldownService cooldownService) {
        this(definitionRepository, cooldownService, new NoOpEncounterProtectionGate(),
                new NoOpProtectionNotifier(), new NoOpEncounterFinishedNotifier(), null, new EncounterTestingMode());
    }

    public EncounterService(EncounterDefinitionRepository definitionRepository, CooldownService cooldownService,
                            EncounterProtectionGate protectionGate, ProtectionNotifier protectionNotifier,
                            EncounterProgressionService progressionService) {
        this(definitionRepository, cooldownService, protectionGate, protectionNotifier, new NoOpEncounterFinishedNotifier(), progressionService);
    }

    public EncounterService(EncounterDefinitionRepository definitionRepository, CooldownService cooldownService,
                            EncounterProtectionGate protectionGate, ProtectionNotifier protectionNotifier,
                            EncounterFinishedNotifier finishedNotifier,
                            EncounterProgressionService progressionService) {
        this(definitionRepository, cooldownService, protectionGate, protectionNotifier, finishedNotifier,
            progressionService, new EncounterTestingMode());
        }

        public EncounterService(EncounterDefinitionRepository definitionRepository, CooldownService cooldownService,
                    EncounterProtectionGate protectionGate, ProtectionNotifier protectionNotifier,
                    EncounterFinishedNotifier finishedNotifier,
                    EncounterProgressionService progressionService, EncounterTestingMode testingMode) {
        this.definitionRepository = Objects.requireNonNull(definitionRepository);
        this.cooldownService = Objects.requireNonNull(cooldownService);
        this.protectionGate = Objects.requireNonNull(protectionGate);
        this.protectionNotifier = Objects.requireNonNull(protectionNotifier);
        this.finishedNotifier = Objects.requireNonNull(finishedNotifier);
        this.progressionService = progressionService;
        this.testingMode = Objects.requireNonNull(testingMode);
    }

    public EncounterEvaluationResult evaluate(TriggerContext context) {
        Objects.requireNonNull(context);
        List<EncounterDefinitionEntry> candidates = definitionRepository.entriesByTrigger(context.type());
        if (candidates.isEmpty()) {
            return result(EvaluationOutcome.NO_CANDIDATES, ActivationOutcome.NOT_STARTED, null, null, List.of());
        }

        long now = context.level().getGameTime();
        List<CandidateEvaluation> evaluations = new ArrayList<>();
        List<EncounterDefinitionEntry> probabilitySuccesses = new ArrayList<>();
        for (EncounterDefinitionEntry candidate : candidates) {
            EncounterDefinition definition = candidate.definition();
            if (!definition.subject().equals(context.subject())) {
                evaluations.add(rejected(candidate.id(), RejectionReason.SUBJECT_MISMATCH, definition.baseProbability(), 0));
                continue;
            }
            if (activeForPlayer(context.player().getUUID()) > 0) {
                evaluations.add(rejected(candidate.id(), RejectionReason.PLAYER_ACTIVE_LIMIT, definition.baseProbability(), 0));
                continue;
            }
            if (!testingMode.enabled()) {
                CooldownCheck cooldown = cooldownService.check(context.player().getUUID(), candidate.id(), now);
                if (!cooldown.allowed()) {
                    evaluations.add(new CandidateEvaluation(candidate.id(), CandidateStatus.REJECTED,
                            cooldown.rejectionReason(), definition.baseProbability(), 0, cooldown.remainingTicks()));
                    continue;
                }
            }
            double probability = testingMode.enabled() ? 1.0D : probabilityOverrides.getOrDefault(candidate.id(),
                    (double) definition.baseProbability());
            double roll = context.level().getRandom().nextDouble();
            if (roll >= probability) {
                evaluations.add(new CandidateEvaluation(candidate.id(), CandidateStatus.REJECTED,
                        RejectionReason.PROBABILITY_FAILED, probability, roll, 0));
                continue;
            }
            evaluations.add(new CandidateEvaluation(candidate.id(), CandidateStatus.PROBABILITY_SUCCESS,
                    null, probability, roll, 0));
            probabilitySuccesses.add(candidate);
        }

        if (probabilitySuccesses.isEmpty()) {
            EvaluationOutcome outcome = evaluations.stream().allMatch(evaluation ->
                    evaluation.rejectionReason() != RejectionReason.PROBABILITY_FAILED)
                    ? EvaluationOutcome.NO_ELIGIBLE_CANDIDATES : EvaluationOutcome.NO_PROBABILITY_SUCCESS;
            logDiagnostics(context, outcome, evaluations);
            return result(outcome, ActivationOutcome.NOT_STARTED, null, null, evaluations);
        }

        EncounterDefinitionEntry selected = probabilitySuccesses.get(
                context.level().getRandom().nextInt(probabilitySuccesses.size()));
        int selectedIndex = evaluations.indexOf(evaluations.stream()
                .filter(evaluation -> evaluation.encounterId().equals(selected.id()))
                .findFirst().orElseThrow());
        evaluations.set(selectedIndex, new CandidateEvaluation(selected.id(), CandidateStatus.SELECTED,
                null, selected.definition().baseProbability(), evaluations.get(selectedIndex).randomRoll(), 0));
        ProtectionDecision protectionDecision = protectionGate.checkAndConsume(
            context.player().getUUID(), selected.id());
        if (protectionDecision instanceof ProtectionDecision.Consumed consumed) {
            protectionNotifier.notify(context.player(), selected.definition().catalogTitleKey(), consumed.remainingCharges());
            logDiagnostics(context, EvaluationOutcome.PROTECTION_CONSUMED, evaluations);
            return result(EvaluationOutcome.PROTECTION_CONSUMED, ActivationOutcome.PROTECTION_CONSUMED,
                selected.id(), null, evaluations);
        }
        EncounterInstance instance = startEncounter(context.level(), context.position(), context.player().getUUID(),
            selected.id(), selected.definition(), false);
        if (instance == null) {
            evaluations.set(selectedIndex, new CandidateEvaluation(selected.id(), CandidateStatus.START_REJECTED,
                RejectionReason.START_NOT_VIABLE, selected.definition().baseProbability(),
                evaluations.get(selectedIndex).randomRoll(), 0));
            logDiagnostics(context, EvaluationOutcome.START_REJECTED, evaluations);
            return result(EvaluationOutcome.START_REJECTED, ActivationOutcome.NOT_STARTED,
                selected.id(), null, evaluations);
        }
        cooldownService.commit(context.player().getUUID(), selected.id(), DEFAULT_GLOBAL_COOLDOWN_TICKS,
                selected.definition().playerCooldownTicks(), now);
        logDiagnostics(context, EvaluationOutcome.STARTED, evaluations);
        return result(EvaluationOutcome.STARTED, ActivationOutcome.STARTED,
            selected.id(), instance.id(), evaluations);
    }

    public void startDebugEncounter(ServerLevel level, BlockPos position, UUID triggeringPlayer) {
        EncounterDefinition definition = new EncounterDefinition(TriggerType.BLOCK_BROKEN, DEBUG_BLOCK,
            1.0F, 0, new EncounterDuration(2400), List.of(new EncounterMobEntry(ZOMBIE_ID, 2)),
                2, 0, 1.0F, EncounterObjectiveType.PLAYER_KILL_COUNT, "", "", 0, 0);
        EncounterInstance instance = startEncounter(level, position, triggeringPlayer,
                Identifier.fromNamespaceAndPath("omenwake", "debug"), definition, true);
        if (instance != null) {
            LOGGER.info("Debug encounter {} started for owner {}", instance.id(), triggeringPlayer);
        }
    }

    private EncounterInstance startEncounter(ServerLevel level, BlockPos position, UUID triggeringPlayer,
                                             Identifier definitionId, EncounterDefinition definition, boolean debug) {
        EncounterInstance instance = new EncounterInstance(level, triggeringPlayer, definitionId,
            definition.catalogTitleKey(), definition.discoveryPoints(), definition.completionPoints(), debug);
        int previousAttempts = progressionService == null || debug ? 0
                : progressionService.participationCount(triggeringPlayer, definitionId);
        if (progressionService != null) {
            progressionService.onActivated(instance);
        }
        List<EncounterMobEntry> mobs = definition.mobsForAttempt(previousAttempts);
        int requiredSpawns = mobs.stream().mapToInt(EncounterMobEntry::weight).sum();
        List<BlockPos> spawnPositions = spawnPlanner.findGroundPositions(level, position, requiredSpawns);
        if (spawnPositions.isEmpty()) {
            LOGGER.warn("Encounter {} has no safe complete spawn plan", definitionId);
            return null;
        }
        List<Entity> spawnedEntities = new ArrayList<>();
        int spawnIndex = 0;
        for (EncounterMobEntry mobEntry : mobs) {
            for (int count = 0; count < mobEntry.weight(); count++) {
                BlockPos spawnPosition = spawnPositions.get(spawnIndex++);
                Entity spawnedEntity = BuiltInRegistries.ENTITY_TYPE.get(mobEntry.entityType())
                        .map(holder -> holder.value().create(level, EntitySpawnReason.TRIGGERED))
                        .orElse(null);
                if (!(spawnedEntity instanceof Mob mob)) {
                    spawnedEntities.forEach(Entity::discard);
                    LOGGER.warn("Encounter {} could not spawn its complete mob group", definitionId);
                    return null;
                }
                mob.setPos(spawnPosition.getX() + 0.5D, spawnPosition.getY(), spawnPosition.getZ() + 0.5D);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPosition), EntitySpawnReason.TRIGGERED, null);
                if (mobEntry.equipment().isPresent() && !applyEquipment(mob, mobEntry.equipment().get())) {
                    spawnedEntities.forEach(Entity::discard);
                    mob.discard();
                    LOGGER.warn("Encounter {} could not apply configured equipment to {}", definitionId,
                            mobEntry.entityType());
                    return null;
                }
                mob.addTag(instance.entityTag());
                mob.setPersistenceRequired();
                if (!level.addFreshEntity(mob)) {
                    spawnedEntities.forEach(Entity::discard);
                    mob.discard();
                    LOGGER.warn("Encounter {} could not add its complete mob group to the level", definitionId);
                    return null;
                }
                spawnedEntities.add(mob);
                instance.addEntity(mob.getUUID(), EncounterEntityRole.HOSTILE);
            }
        }
        if (definition.objective() != EncounterObjectiveType.PLAYER_KILL_COUNT) {
            spawnedEntities.forEach(Entity::discard);
            LOGGER.warn("Encounter {} uses an objective runtime that is not implemented", definitionId);
            return null;
        }
        instance.setObjective(new PlayerKillCountObjective(instance.id(), definition.requiredKillsForAttempt(previousAttempts),
                new HashSet<>(instance.entityIds())));
        instance.activate(level.getGameTime(), definition.duration());
        activeInstances.add(instance);
        instance.entityIds().forEach(entityId -> entityToInstance.put(entityId, instance.id()));
        
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(triggeringPlayer);
        if (owner != null && !debug) {
            owner.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            owner.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(definition.catalogTitleKey())));
            owner.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("objective.omenwake." + definition.objective().name().toLowerCase(Locale.ROOT))));
        }
        return instance;
    }

    private static boolean applyEquipment(Mob mob, EntityEquipmentDefinition equipment) {
        for (var configured : equipment.slots().entrySet()) {
            EquipmentSlot slot = configured.getKey().vanillaSlot();
            EquippedItemDefinition itemDefinition = configured.getValue();
            var itemHolder = BuiltInRegistries.ITEM.get(itemDefinition.itemId());
            if (itemHolder.isEmpty()) {
                return false;
            }
            ItemStack stack = new ItemStack(itemHolder.get().value());
            mob.setItemSlot(slot, stack);
            mob.setDropChance(slot, itemDefinition.dropChance());
        }
        return true;
    }

    public int activeForPlayer(UUID playerId) {
        return (int) activeInstances.stream()
                .filter(instance -> instance.ownerId().equals(playerId))
                .count();
    }

    public UUID instanceForEntity(UUID entityId) {
        return entityToInstance.get(entityId);
    }

    public void onEncounterEntityDeath(UUID entityId, boolean playerAttributed, long serverTick) {
        UUID instanceId = entityToInstance.remove(entityId);
        if (instanceId == null) {
            return;
        }
        for (EncounterInstance instance : activeInstances) {
            if (instance.id().equals(instanceId)) {
                instance.onSignal(new ObjectiveSignal.EncounterEntityDied(instanceId, entityId,
                        playerAttributed, serverTick));
                instance.updateBossEvent();
                updateActionBar(instance);
                return;
            }
        }
    }

    public void onOwnerDeath(UUID ownerId) {
        for (EncounterInstance instance : activeInstances) {
            if (instance.ownerId().equals(ownerId) && instance.state() == EncounterState.ACTIVE) {
                instance.finish(EncounterOutcome.FAILED);
            }
        }
    }

    public void cancelAllForServerStop() {
        for (EncounterInstance instance : activeInstances) {
            instance.finish(EncounterOutcome.CANCELLED);
            instance.entityIds().forEach(entityId -> entityToInstance.remove(entityId, instance.id()));
        }
        activeInstances.clear();
    }

    public boolean debugDiagnostics() {
        return debugDiagnostics;
    }

    public void setDebugDiagnostics(boolean enabled) {
        debugDiagnostics = enabled;
    }

    public void setProbabilityOverride(Identifier encounterId, double probability) {
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Probability override must be finite and within [0, 1]");
        }
        probabilityOverrides.put(encounterId, probability);
    }

    public int maximizeProbabilityOverrides() {
        int count = 0;
        for (EncounterDefinitionEntry entry : definitionRepository.allEntries()) {
            probabilityOverrides.put(entry.id(), 1.0D);
            count++;
        }
        return count;
    }

    public void clearProbabilityOverride(Identifier encounterId) {
        probabilityOverrides.remove(encounterId);
    }

    public void clearCooldowns(UUID playerId) {
        cooldownService.clear(playerId);
    }

    private void logDiagnostics(TriggerContext context, EvaluationOutcome outcome,
                                List<CandidateEvaluation> evaluations) {
        if (!debugDiagnostics) {
            return;
        }
        LOGGER.debug("Evaluation for player {} trigger {} outcome {} candidates {}",
                context.player().getUUID(), context.type(), outcome, evaluations);
    }

    private static CandidateEvaluation rejected(Identifier id, RejectionReason reason,
                                                 double probability, long remainingTicks) {
        return new CandidateEvaluation(id, CandidateStatus.REJECTED, reason, probability, 0, remainingTicks);
    }

    private static EncounterEvaluationResult result(EvaluationOutcome outcome, ActivationOutcome activationOutcome,
                                                    Identifier id, UUID instanceId,
                                                    List<CandidateEvaluation> evaluations) {
        return new EncounterEvaluationResult(outcome, activationOutcome, java.util.Optional.ofNullable(id),
                java.util.Optional.ofNullable(instanceId), evaluations);
    }

    public void tick() {
        Iterator<EncounterInstance> instances = activeInstances.iterator();
        while (instances.hasNext()) {
            EncounterInstance instance = instances.next();
            updateEncounterHostility(instance);
            instance.updateBossEvent();
            if (instance.update()) {
                instance.entityIds().forEach(entityId -> entityToInstance.remove(entityId, instance.id()));
                if (progressionService != null) {
                    progressionService.onFinished(instance, instance.outcome());
                }
                notifyOwner(instance, outcomeMessageKey(instance.outcome()), Component.translatable(instance.catalogTitleKey()));
                ServerPlayer owner = instance.level().getServer().getPlayerList().getPlayer(instance.ownerId());
                if (owner != null && !instance.debug()) {
                    finishedNotifier.notifyFinished(owner, instance.catalogTitleKey(), instance.outcome());
                }
                LOGGER.info("Encounter {} finished with outcome {} for player {}",
                    instance.id(), instance.outcome(), instance.ownerId());
                instances.remove();
            } else {
                updateActionBar(instance);
            }
        }
    }

    private void updateActionBar(EncounterInstance instance) {
        if (instance.debug() || instance.state() != EncounterState.ACTIVE) {
            return;
        }
        ServerPlayer owner = instance.level().getServer().getPlayerList().getPlayer(instance.ownerId());
        if (owner != null && instance.objective() != null) {
            owner.sendOverlayMessage(Component.translatable(
                    "message.omenwake.encounter.progress.kills",
                    instance.objective().currentProgress(),
                    instance.objective().requiredProgress()));
        }
    }

    private void updateEncounterHostility(EncounterInstance instance) {
        ServerPlayer owner = instance.level().getServer().getPlayerList().getPlayer(instance.ownerId());
        if (owner == null) {
            return;
        }
        for (UUID entityId : instance.entityIds()) {
            if (!(instance.level().getEntity(entityId) instanceof Mob mob)
                    || instance.roleFor(entityId) != EncounterEntityRole.HOSTILE) {
                continue;
            }
            mob.setTarget(owner);
            if (mob instanceof Parrot parrot) {
                updateAggressiveParrot(instance, parrot, owner);
            } else if (mob instanceof Chicken chicken) {
                updateAggressiveChicken(instance, chicken, owner);
            }
        }
    }

    private static void updateAggressiveParrot(EncounterInstance instance, Parrot parrot, ServerPlayer owner) {
        parrot.getNavigation().moveTo(owner, 1.35D);
        if (parrot.distanceToSqr(owner) <= AGGRESSIVE_PARROT_ATTACK_DISTANCE_SQUARED
                && instance.level().getGameTime() % AGGRESSIVE_PARROT_ATTACK_INTERVAL_TICKS == 0L) {
            owner.hurtServer(instance.level(), owner.damageSources().mobAttack(parrot), 2.0F);
        }
    }

    private static void updateAggressiveChicken(EncounterInstance instance, Chicken chicken, ServerPlayer owner) {
        chicken.getNavigation().moveTo(owner, 1.25D);
        if (chicken.distanceToSqr(owner) <= AGGRESSIVE_CHICKEN_ATTACK_DISTANCE_SQUARED
                && instance.level().getGameTime() % AGGRESSIVE_CHICKEN_ATTACK_INTERVAL_TICKS == 0L) {
            owner.hurtServer(instance.level(), owner.damageSources().mobAttack(chicken), 1.0F);
        }
    }

    private static String outcomeMessageKey(EncounterOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> "message.omenwake.encounter.outcome.completed";
            case EVADED -> "message.omenwake.encounter.outcome.evaded";
            case FAILED -> "message.omenwake.encounter.outcome.failed";
            case CANCELLED -> "message.omenwake.encounter.outcome.cancelled";
        };
    }

    private static void notifyOwner(EncounterInstance instance, String translationKey, Object... arguments) {
        if (instance.debug()) {
            return;
        }
        ServerPlayer owner = instance.level().getServer().getPlayerList().getPlayer(instance.ownerId());
        if (owner != null) {
            owner.sendOverlayMessage(Component.translatable(translationKey, arguments));
        }
    }

}