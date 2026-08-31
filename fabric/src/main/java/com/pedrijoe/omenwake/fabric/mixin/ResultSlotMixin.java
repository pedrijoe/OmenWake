package com.pedrijoe.omenwake.fabric.mixin;

import com.pedrijoe.omenwake.fabric.FabricTriggerHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public final class ResultSlotMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void omenwake$onCraftingResultTaken(Player player, ItemStack output, CallbackInfo callbackInfo) {
        FabricTriggerHooks.craftingResultTaken(player, output.copy());
    }
}