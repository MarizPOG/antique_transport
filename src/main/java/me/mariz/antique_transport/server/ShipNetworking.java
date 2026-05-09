package me.mariz.antique_transport.server;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import me.mariz.antique_transport.client.compat.sable.ShipCache;
import me.mariz.antique_transport.client.compat.sable.ShipUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Registers client-side packet handlers for ship synchronization.
 * Call {@link #register()} in client initializer.
 */
public class ShipNetworking {

    public static void register() {

        // Sublevel positions from server (SERVER_MOD mode)
        ClientPlayNetworking.registerGlobalReceiver(ShipDataPacket.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> ShipCache.updateFromServer(payload.ships())));

        // Sublevel name/icon sync from server
        ClientPlayNetworking.registerGlobalReceiver(ShipSyncPacket.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> {
            UUID id = payload.shipId();
            if (payload.removed()) {
                ShipCache.deleteShip(id);
            } else {
                if (payload.name() != null) ShipCache.shipNames.put(id, payload.name());
                if (payload.iconId() != null) {
                    ResourceLocation rl = ResourceLocation.tryParse(payload.iconId());
                    if (rl != null) ShipCache.shipIcons.put(id, rl);
                }
            }
        }));

        // JOIN/DISCONNECT events
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(ShipCache::load)
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ShipCache.onDisconnect()
        );
        // Teleport
        ServerPlayNetworking.registerGlobalReceiver(TeleportToShipPacket.TYPE,
                (payload, ctx) -> {
                    ServerPlayer player = ctx.player();
                    // Tylko OP
                    if (!player.hasPermissions(2)) return;

                    UUID shipId = payload.shipId();
                    ctx.server().execute(() -> {
                        // Szukaj statku we wszystkich wymiarach
                        for (ServerLevel level : ctx.server().getAllLevels()) {
                            for (SubLevelAccess ship :
                                    SableCompanion.INSTANCE.getAllIntersecting(level, ShipUtils.worldBounds(level))) {
                                if (!ship.getUniqueId().equals(shipId)) continue;

                                var pos = ship.logicalPose().position();
                                // +2 żeby wylądować na pokładzie, nie w środku
                                player.teleportTo(
                                        pos.x(), pos.y() + 2.0, pos.z()
                                );
                                player.sendSystemMessage(
                                        Component.literal("§aTeleported to ship."));
                                return;
                            }
                        }
                        // Fallback – statek poza render distance serwera, użyj lastKnown
                        // (ShipAntiqueTransportServer trzyma ostatnią pozycję)
                        ShipDataPacket.ShipEntry last = AntiqueTransportServer.lastKnownPositions.get(shipId);
                        if (last != null) {
                            player.teleportTo(
                                    last.x(), last.y() + 2.0, last.z()
                            );
                            player.sendSystemMessage(
                                    Component.literal("§eShip not loaded — teleported to last known position."));
                        } else {
                            player.sendSystemMessage(
                                    Component.literal("§cShip position unknown."));
                        }
                    });
                });
    }


    /**
     * Sends sublevel name/icon update to server from ShipNameModal.
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
    public static void sendTeleportToShip(UUID shipId) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;

        if (ClientPlayNetworking.canSend(TeleportToShipPacket.TYPE)) {
            // Serwer ma moda — wyślij pakiet
            ClientPlayNetworking.send(new TeleportToShipPacket(shipId));
        } else {
            // Fallback — wpisz komendę w chat
            ShipDataPacket.ShipEntry pos = ShipCache.lastKnownPositions.get(shipId);
            if (pos != null) {
                connection.sendCommand("tp " + pos.x() + " " + (pos.y() + 1) + " " + pos.z());
            }
        }
    }
}