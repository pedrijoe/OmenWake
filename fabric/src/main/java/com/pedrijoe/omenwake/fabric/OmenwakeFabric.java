package com.pedrijoe.omenwake.fabric;
import com.pedrijoe.omenwake.Omenwake;
import com.pedrijoe.omenwake.OmenwakeServices;
import com.pedrijoe.omenwake.catalog.PlayerEncounterData;
import com.pedrijoe.omenwake.catalog.EncounterProgress;
import com.pedrijoe.omenwake.catalog.OpenCatalogPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.pedrijoe.omenwake.trigger.TriggerContext;
import com.pedrijoe.omenwake.trigger.TriggerData;
import com.pedrijoe.omenwake.trigger.TriggerType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import com.pedrijoe.omenwake.encounter.EncounterDefinitionReloadListener;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.permissions.Permissions;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
public final class OmenwakeFabric implements ModInitializer {
    private OmenwakeServices services;
    private FabricPlayerEncounterDataRepository playerDataRepository;
    private FabricCatalogSyncSender catalogSyncSender;
    private FabricTriggerAdapter triggerAdapter;
    private MinecraftServer server;
    private final Set<UUID> pendingCatalogSyncs = new HashSet<>();

    @Override
    public void onInitialize() {
        playerDataRepository = new FabricPlayerEncounterDataRepository();
        services = Omenwake.createServices(playerDataRepository, new FabricProtectionNotifier(), new FabricEncounterFinishedNotifier());
        FabricPayloads.register();
        catalogSyncSender = new FabricCatalogSyncSender(services.catalogProjectionService());
        triggerAdapter = new FabricTriggerAdapter(services.triggerDispatcher(), services.encounterService());
        FabricTriggerHooks.initialize(triggerAdapter);
        services.progressionService().setChangeListener(ownerId -> {
            ServerPlayer player = playerDataRepository.onlinePlayer(ownerId);
            if (player != null) {
                catalogSyncSender.syncOwnCatalog(player);
            }
        });
        ServerPlayerEvents.JOIN.register(playerDataRepository::track);
        ServerPlayerEvents.LEAVE.register(playerDataRepository::untrack);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
            playerDataRepository.track(newPlayer));
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ServerPlayer player = listener.getPlayer();
            playerDataRepository.track(player);
            pendingCatalogSyncs.add(player.getUUID());
        });
        PlayerBlockBreakEvents.AFTER.register(this::afterBlockBreak);
        UseBlockCallback.EVENT.register(this::onUseBlock);
        ServerLivingEntityEvents.AFTER_DEATH.register(triggerAdapter::onLivingEntityDeath);
        EntitySleepEvents.START_SLEEPING.register(triggerAdapter::onSleepStarted);
        EntitySleepEvents.STOP_SLEEPING.register(triggerAdapter::onSleepEnded);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            this.server = server;
            services.tick();
            syncPendingCatalogs(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
            services.encounterService().cancelAllForServerStop());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                Identifier.fromNamespaceAndPath(Omenwake.MOD_ID, "encounters"),
                new EncounterDefinitionReloadListener(services.encounterDefinitionRepository(), this::syncAllPlayers));
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) -> {
                var debugCommand = Commands.literal("omenwake")
                .then(Commands.literal("debug")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                    .then(Commands.literal("selection")
                        .then(Commands.literal("on")
                            .executes(context -> setSelectionDiagnostics(context.getSource(), true)))
                        .then(Commands.literal("off")
                            .executes(context -> setSelectionDiagnostics(context.getSource(), false))))
                    .then(Commands.literal("probability")
                        .then(Commands.literal("set")
                            .then(Commands.argument("encounter", StringArgumentType.string())
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0))
                                    .executes(context -> setProbabilityOverride(context)))))
                        .then(Commands.literal("max_all")
                            .executes(context -> maximizeProbabilityOverrides(context)))
                        .then(Commands.literal("clear")
                            .then(Commands.argument("encounter", StringArgumentType.string())
                                .executes(context -> clearProbabilityOverride(context)))))
                    .then(Commands.literal("start")
                        .executes(context -> startDebugEncounter(context.getSource())))
                    .then(Commands.literal("cooldown")
                        .then(Commands.literal("clear")
                            .executes(context -> clearCooldown(context.getSource(), context.getSource().getPlayerOrException()))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> clearCooldown(context.getSource(), EntityArgument.getPlayer(context, "player")))))));
                dispatcher.register(debugCommand);
            dispatcher.register(Commands.literal("omenwake")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.literal("testing")
                    .then(Commands.literal("on")
                        .executes(context -> setTestingMode(context.getSource(), true)))
                    .then(Commands.literal("off")
                        .executes(context -> setTestingMode(context.getSource(), false)))));
            registerProgressionCommands(dispatcher);
        });
    }

    private void syncAllPlayers() {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                catalogSyncSender.syncOwnCatalog(player);
            }
        }
    }

    private void syncPendingCatalogs(MinecraftServer server) {
        if (pendingCatalogSyncs.isEmpty()) {
            return;
        }
        Set<UUID> pending = Set.copyOf(pendingCatalogSyncs);
        pendingCatalogSyncs.removeAll(pending);
        for (UUID playerId : pending) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                catalogSyncSender.syncOwnCatalog(player);
            }
        }
    }

    private InteractionResult onUseBlock(net.minecraft.world.entity.player.Player player, Level level,
                                         InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
            var position = hitResult.getBlockPos();
            triggerAdapter.onContainerOpened(serverPlayer, serverLevel, position, level.getBlockState(position));
        }
        return InteractionResult.PASS;
    }

                private void registerProgressionCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("omenwake")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                    .then(Commands.literal("progress")
                        .then(Commands.literal("get")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> getProgress(context))))
                        .then(Commands.literal("reset")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> resetProgress(context))))
                        .then(Commands.literal("discover_all")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> discoverAllProgress(context))))
                        .then(Commands.literal("undiscover_unattempted")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> undiscoverUnattemptedProgress(context)))))
                    .then(Commands.literal("catalog")
                        .executes(context -> openCatalog(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> openCatalog(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("protection")
                        .then(Commands.literal("get")
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("encounter", StringArgumentType.string())
                                    .executes(context -> getProtection(context)))))
                        .then(Commands.literal("set")
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("encounter", StringArgumentType.string())
                                    .then(Commands.argument("charges", IntegerArgumentType.integer(0))
                                        .executes(context -> setProtection(context)))))))
                    .then(Commands.literal("points")
                        .then(Commands.literal("get")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::getPoints)))
                        .then(Commands.literal("add")
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes(this::addPoints))))));
                }

                private int openCatalog(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
                catalogSyncSender.syncOwnCatalog(player);
                if (!ServerPlayNetworking.canSend(player, OpenCatalogPayload.TYPE)) {
                    source.sendFailure(net.minecraft.network.chat.Component.translatable(
                        "commands.omenwake.catalog.open_unavailable", player.getName()));
                    return 0;
                }
                ServerPlayNetworking.send(player, new OpenCatalogPayload());
                source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.catalog.opened", player.getName()), true);
                return 1;
                }

                private int getProgress(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                PlayerEncounterData data = services.progressionService().get(player.getUUID());
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.progress.summary", player.getName(), data.encounters().size(),
                    data.points().lifetimePoints(), data.points().availablePoints()), false);
                return 1;
                }

                private int resetProgress(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                services.progressionService().reset(player.getUUID());
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.progress.reset", player.getName()), true);
                return 1;
                }

                private int discoverAllProgress(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                int changed = services.progressionService().discoverAll(player.getUUID(), services
                    .encounterDefinitionRepository().allEntries().stream().map(entry -> entry.id()).toList());
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.progress.discover_all", player.getName(), changed), true);
                return changed;
                }

                private int undiscoverUnattemptedProgress(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                int changed = services.progressionService().undiscoverUnattempted(player.getUUID(), services
                    .encounterDefinitionRepository().allEntries().stream().map(entry -> entry.id()).toList());
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.progress.undiscover_unattempted", player.getName(), changed), true);
                return changed;
                }

                private int getProtection(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                net.minecraft.resources.Identifier encounterId = parseEncounterId(context);
                if (encounterId == null) return 0;
                EncounterProgress progress = services.progressionService().get(player.getUUID())
                    .encounters().getOrDefault(encounterId, EncounterProgress.EMPTY);
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.protection.value", encounterId, progress.protectionCharges()), false);
                return 1;
                }

                private int setProtection(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                net.minecraft.resources.Identifier encounterId = parseEncounterId(context);
                if (encounterId == null) return 0;
                int charges = IntegerArgumentType.getInteger(context, "charges");
                services.progressionService().setProtection(player.getUUID(), encounterId, charges);
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.protection.set", encounterId, charges), true);
                return 1;
                }

                private int getPoints(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.points.value", player.getName(),
                    services.progressionService().get(player.getUUID()).points().lifetimePoints()), false);
                return 1;
                }

                private int addPoints(CommandContext<net.minecraft.commands.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                int amount = IntegerArgumentType.getInteger(context, "amount");
                services.progressionService().addPoints(player.getUUID(), amount);
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.omenwake.points.added", player.getName(), amount), true);
                return 1;
                }

                private static net.minecraft.resources.Identifier parseEncounterId(
                    CommandContext<net.minecraft.commands.CommandSourceStack> context) {
                String value = StringArgumentType.getString(context, "encounter");
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(value);
                if (id == null) {
                    context.getSource().sendFailure(net.minecraft.network.chat.Component.translatable(
                        "commands.omenwake.invalid_encounter_id", value));
                }
                return id;
                }

    private int setSelectionDiagnostics(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        services.encounterService().setDebugDiagnostics(enabled);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                enabled ? "commands.omenwake.selection_debug_enabled" : "commands.omenwake.selection_debug_disabled"), true);
        return 1;
    }

    private int setTestingMode(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        services.testingMode().setEnabled(enabled);
        syncAllPlayers();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                enabled ? "commands.omenwake.testing.enabled" : "commands.omenwake.testing.disabled"), true);
        return 1;
    }

    private int setProbabilityOverride(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        Identifier encounterId = parseEncounterId(context);
        if (encounterId == null) return 0;
        double probability = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "value");
        services.encounterService().setProbabilityOverride(encounterId, probability);
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.omenwake.probability.set", encounterId, probability), true);
        return 1;
    }

    private int maximizeProbabilityOverrides(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        int changed = services.encounterService().maximizeProbabilityOverrides();
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.omenwake.probability.max_all", changed), true);
        return changed;
    }

    private int clearProbabilityOverride(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        Identifier encounterId = parseEncounterId(context);
        if (encounterId == null) return 0;
        services.encounterService().clearProbabilityOverride(encounterId);
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.omenwake.probability.cleared", encounterId), true);
        return 1;
    }

        private int clearCooldown(net.minecraft.commands.CommandSourceStack source, ServerPlayer targetPlayer) {
        services.encounterService().clearCooldowns(targetPlayer.getUUID());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                "commands.omenwake.cooldown_cleared", targetPlayer.getName()), true);
        return 1;
    }

    private int startDebugEncounter(net.minecraft.commands.CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        services.encounterService().startDebugEncounter(
            player.level(), player.blockPosition(), player.getUUID());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
            "commands.omenwake.debug_started"), true);
        return 1;
        }

    private void afterBlockBreak(Level level, net.minecraft.world.entity.player.Player player,
                                 net.minecraft.core.BlockPos position,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
            || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        triggerAdapter.onBlockBroken(serverPlayer, serverLevel, position, state);
    }
}
