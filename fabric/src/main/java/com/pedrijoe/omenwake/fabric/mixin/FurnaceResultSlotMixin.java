package com.pedrijoe.omenwake.fabric.mixin;

import com.pedrijoe.omenwake.fabric.FabricTriggerHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public final class FurnaceResultSlotMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void omenwake$onFurnaceOutputTaken(Player player, ItemStack output, CallbackInfo callbackInfo) {
        FabricTriggerHooks.furnaceOutputTaken(player, output.copy());
    }
}