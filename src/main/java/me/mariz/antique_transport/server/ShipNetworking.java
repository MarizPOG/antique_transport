package me.mariz.antique_transport.server;

import me.mariz.antique_transport.client.compat.sable.ShipCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Registers client-side packet handlers for ship synchronization.
 * Call {@link #register()} in client initializer.
 */
public class ShipNetworking {

    public static void register() {

        // Ship positions from server (SERVER_MOD mode)
        ClientPlayNetworking.registerGlobalReceiver(ShipDataPacket.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> ShipCache.updateFromServer(payload.ships()));
        });

        // Ship name/icon sync from server
        ClientPlayNetworking.registerGlobalReceiver(ShipSyncPacket.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                UUID id = payload.shipId();
                if (payload.removed()) {
                    ShipCache.shipNames.remove(id);
                    ShipCache.shipIcons.remove(id);
                } else {
                    if (payload.name() != null) {
                        ShipCache.shipNames.put(id, payload.name());
                    }
                    if (payload.iconId() != null) {
                        ResourceLocation rl = ResourceLocation.tryParse(payload.iconId());
                        if (rl != null) {
                            ShipCache.shipIcons.put(id, rl);
                        }
                    }
                }
            });
        });

        // JOIN/DISCONNECT events
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(ShipCache::load)
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ShipCache.onDisconnect()
        );
    }


    /**
     * Sends ship name/icon update to server from ShipNameModal.
     */
    public static void sendUpdate(UUID shipId, String name, String iconId) {
        if (!canSendToServer()) return;
        ClientPlayNetworking.send(new ShipUpdatePacket(shipId, name, iconId));
    }

    /**
     * Sends ship removal signal to server.
     */
    public static void sendRemove(UUID shipId) {
        if (!canSendToServer()) return;
        ClientPlayNetworking.send(new ShipUpdatePacket(shipId, null, null));
    }

    /**
     * Checks if server connection is ready for sending packets.
     */
    private static boolean canSendToServer() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return false;
        return connection.isAcceptingMessages()
                && ClientPlayNetworking.canSend(ShipUpdatePacket.TYPE);
    }
}