package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.trigger.TriggerContext;
import com.pedrijoe.omenwake.trigger.TriggerData;
import com.pedrijoe.omenwake.trigger.TriggerDispatcher;
import com.pedrijoe.omenwake.trigger.TriggerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.UUID;

public final class FabricTriggerAdapter {
    private final TriggerDispatcher dispatcher;
    private final com.pedrijoe.omenwake.encounter.EncounterService encounterService;

    public FabricTriggerAdapter(TriggerDispatcher dispatcher,
                                com.pedrijoe.omenwake.encounter.EncounterService encounterService) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.encounterService = Objects.requireNonNull(encounterService);
    }

    public void onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos position, BlockState state) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        TriggerType type = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                ? TriggerType.CROP_HARVESTED
                : TriggerType.BLOCK_BROKEN;
        dispatch(player, level, position, type, BuiltInRegistries.BLOCK.getKey(state.getBlock()), 1);
    }

    public void onLivingEntityDeath(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Entity causingEntity = source.getEntity();
        UUID encounterInstanceId = encounterService.instanceForEntity(victim.getUUID());
        if (encounterInstanceId != null) {
            encounterService.onEncounterEntityDeath(victim.getUUID(), causingEntity instanceof ServerPlayer,
                    level.getGameTime());
            return;
        }
        if (victim instanceof ServerPlayer owner) {
            encounterService.onOwnerDeath(owner.getUUID());
            return;
        }
        if (!(causingEntity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            return;
        }
        dispatch(player, level, victim.blockPosition(), TriggerType.ENTITY_KILLED,
                BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), 1);
    }

    public void onSleepStarted(LivingEntity entity, BlockPos bedPosition) {
        if (entity instanceof ServerPlayer player && !player.isSpectator()) {
            dispatch(player, player.level(), bedPosition, TriggerType.SLEEP_STARTED,
                    bedSubject(player.level().getBlockState(bedPosition).getBlock()), 1);
        }
    }

    public void onSleepEnded(LivingEntity entity, BlockPos bedPosition) {
        if (entity instanceof ServerPlayer player && !player.isSpectator()) {
            dispatch(player, player.level(), bedPosition, TriggerType.SLEEP_ENDED,
                    bedSubject(player.level().getBlockState(bedPosition).getBlock()), 1);
        }
    }

    private static Identifier bedSubject(net.minecraft.world.level.block.Block block) {
        return block instanceof BedBlock ? Identifier.withDefaultNamespace("bed") : BuiltInRegistries.BLOCK.getKey(block);
    }

    public void onContainerOpened(ServerPlayer player, ServerLevel level, BlockPos position, BlockState state) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId.equals(Identifier.withDefaultNamespace("chest"))
                || blockId.equals(Identifier.withDefaultNamespace("trapped_chest"))
                || blockId.equals(Identifier.withDefaultNamespace("barrel"))
                || state.getBlock() instanceof ShulkerBoxBlock) {
            dispatch(player, level, position, TriggerType.CONTAINER_OPENED, blockId, 1);
        }
    }

    public void onFurnaceOutputTaken(net.minecraft.world.entity.player.Player player, ItemStack output) {
        if (player instanceof ServerPlayer serverPlayer && !output.isEmpty()) {
            dispatch(serverPlayer, serverPlayer.level(), serverPlayer.blockPosition(), TriggerType.FURNACE_OUTPUT_TAKEN,
                    BuiltInRegistries.ITEM.getKey(output.getItem()), output.getCount());
        }
    }

    public void onCraftingResultTaken(net.minecraft.world.entity.player.Player player, ItemStack output) {
        if (player instanceof ServerPlayer serverPlayer && !output.isEmpty()) {
            dispatch(serverPlayer, serverPlayer.level(), serverPlayer.blockPosition(), TriggerType.ITEM_CRAFTED,
                    BuiltInRegistries.ITEM.getKey(output.getItem()), output.getCount());
        }
    }

    public void onFishCaught(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            dispatch(serverPlayer, serverPlayer.level(), serverPlayer.blockPosition(), TriggerType.FISH_CAUGHT,
                    Identifier.withDefaultNamespace("fish"), 1);
        }
    }

    public void onItemPickedUp(net.minecraft.world.entity.player.Player player, Identifier itemId, int amount) {
        if (player instanceof ServerPlayer serverPlayer && amount > 0) {
            dispatch(serverPlayer, serverPlayer.level(), serverPlayer.blockPosition(), TriggerType.ITEM_PICKED_UP,
                    itemId, amount);
        }
    }

    private void dispatch(ServerPlayer player, ServerLevel level, BlockPos position, TriggerType type,
                          Identifier subject, int amount) {
        dispatcher.dispatch(new TriggerContext(player, type, subject, position, level, TriggerData.normal(amount)));
    }
}