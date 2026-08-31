package com.pedrijoe.omenwake.fabric.mixin;

import com.pedrijoe.omenwake.fabric.FabricTriggerHooks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntity.class)
public final class ItemEntityMixin {
    @Redirect(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
    private void omenwake$onItemPickedUp(Player player, Entity entity, int originalCount) {
        player.take(entity, originalCount);
        if (entity instanceof ItemEntity itemEntity) {
            int pickedUpCount = originalCount - itemEntity.getItem().getCount();
            FabricTriggerHooks.itemPickedUp(player, BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem()), pickedUpCount);
        }
    }
}