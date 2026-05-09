package me.mariz.antique_transport;

import me.mariz.antique_transport.client.compat.ModCompat;
import me.mariz.antique_transport.server.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class AntiqueTransport implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("antique_transport");

    @Override
    public void onInitialize() {
        registerPayloads();
        registerPersistence();
        registerUpdateReceiver();
        registerJoinSync();

        if (ModCompat.SABLE) {
            ServerTickEvents.END_SERVER_TICK.register(AntiqueTransportServer::tick);
        }
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ShipDataPacket.TYPE, ShipDataPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ShipSyncPacket.TYPE, ShipSyncPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ShipUpdatePacket.TYPE, ShipUpdatePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportToShipPacket.TYPE, TeleportToShipPacket.CODEC);
    }

    private void registerPersistence() {
        ServerLifecycleEvents.SERVER_STARTING.register(AntiqueTransportServer::loadPositionCache);
        ServerLifecycleEvents.AFTER_SAVE.register((server, flush, savingAll) ->
                AntiqueTransportServer.savePositionCache(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(AntiqueTransportServer::savePositionCache);
    }

    private void registerUpdateReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ShipUpdatePacket.TYPE, (payload, ctx) ->
                ctx.server().execute(() -> AntiqueTransportServer.handleShipUpdate(ctx.server(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TeleportToShipPacket.TYPE, (payload, ctx) -> ctx.server().execute(() -> {
            ServerPlayer player = ctx.player();
            // Teloport to sublevel
            UUID shipId = payload.shipId();
            ShipDataPacket.ShipEntry pos = AntiqueTransportServer.lastKnownPositions.get(shipId);
            if (pos != null) {
                player.teleportTo(player.serverLevel(), pos.x(), pos.y() + 1, pos.z(), player.getYRot(), player.getXRot());
            }
        }));
    }

    private void registerJoinSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                AntiqueTransportServer.syncAllKnownShipsTo(handler.getPlayer()));
    }
}