package com.pedrijoe.omenwake.fabric.mixin;

import com.pedrijoe.omenwake.fabric.FabricTriggerHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingHook.class)
public final class FishingHookMixin {
    @Redirect(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/Identifier;I)V"))
    private void omenwake$onFishCaught(Player player, Identifier stat, int amount) {
        player.awardStat(stat, amount);
        FabricTriggerHooks.fishCaught(player);
    }
}