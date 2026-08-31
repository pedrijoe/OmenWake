package com.pedrijoe.omenwake.fabric;

import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class FabricTriggerHooks {
    private static FabricTriggerAdapter adapter;

    private FabricTriggerHooks() {
    }

    public static void initialize(FabricTriggerAdapter incoming) {
        adapter = incoming;
    }

    public static void furnaceOutputTaken(Player player, ItemStack output) {
        if (adapter != null) {
            adapter.onFurnaceOutputTaken(player, output);
        }
    }

    public static void craftingResultTaken(Player player, ItemStack output) {
        if (adapter != null) {
            adapter.onCraftingResultTaken(player, output);
        }
    }

    public static void fishCaught(Player player) {
        if (adapter != null) {
            adapter.onFishCaught(player);
        }
    }

    public static void itemPickedUp(Player player, Identifier itemId, int amount) {
        if (adapter != null) {
            adapter.onItemPickedUp(player, itemId, amount);
        }
    }
}