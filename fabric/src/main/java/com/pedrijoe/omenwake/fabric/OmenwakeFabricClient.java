package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.catalog.CatalogSnapshotPayload;
import com.pedrijoe.omenwake.catalog.OpenCatalogPayload;
import com.pedrijoe.omenwake.catalog.ProtectionConsumedPayload;
import com.pedrijoe.omenwake.encounter.EncounterFinishedPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.Locale;

public final class OmenwakeFabricClient implements ClientModInitializer {
        private static final KeyMapping.Category CATALOG_CATEGORY = KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath("omenwake", "catalog"));
        private static final KeyMapping OPEN_CATALOG_KEY = KeyMappingHelper.registerKeyMapping(
                        new KeyMapping("key.omenwake.open_catalog", InputConstants.UNKNOWN.getValue(), CATALOG_CATEGORY));
        private static final SystemToast.SystemToastId ENCOUNTER_FINISHED_TOAST_ID = new SystemToast.SystemToastId();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CatalogSnapshotPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> ClientCatalogStore.getInstance().replace(payload.snapshot())));
        ClientPlayNetworking.registerGlobalReceiver(OpenCatalogPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().player != null && context.client().level != null) {
                        context.client().setScreenAndShow(new EncounterCatalogScreen());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(ProtectionConsumedPayload.TYPE,
                (payload, context) -> context.client().execute(() -> presentProtection(payload, context)));
        ClientPlayNetworking.registerGlobalReceiver(EncounterFinishedPayload.TYPE,
                (payload, context) -> context.client().execute(() -> presentEncounterFinished(payload, context)));

                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        while (OPEN_CATALOG_KEY.consumeClick()) {
                                if (client.player == null || client.level == null) {
                                        continue;
                                }
                                client.setScreenAndShow(new EncounterCatalogScreen());
                        }
                });

        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
                ClientCatalogStore.getInstance().clear());
    }

    private static void presentProtection(ProtectionConsumedPayload payload,
                                          ClientPlayNetworking.Context context) {
                if (context.client().player == null) {
                        return;
                }
        String key = payload.remainingCharges() == 0
                ? "message.omenwake.protection.depleted"
                : payload.remainingCharges() == 1
                ? "message.omenwake.protection.remaining.one"
                : "message.omenwake.protection.remaining.many";
        context.client().player.sendOverlayMessage(Component.translatable(
                key, Component.translatable(payload.titleKey()), payload.remainingCharges()));
    }

    private static void presentEncounterFinished(EncounterFinishedPayload payload,
                                                ClientPlayNetworking.Context context) {
        if (context.client().player == null) {
            return;
        }
        Component title = Component.translatable(payload.titleKey());
        Component subtitle = Component.translatable("message.omenwake.encounter.toast."
                + payload.outcome().name().toLowerCase(Locale.ROOT));
        SystemToast.add(context.client().gui.toastManager(), ENCOUNTER_FINISHED_TOAST_ID, title, subtitle);
    }
}
