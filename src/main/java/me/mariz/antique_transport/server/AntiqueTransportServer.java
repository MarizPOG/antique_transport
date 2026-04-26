package me.mariz.antique_transport.server;

import me.mariz.antique_transport.AntiqueTransport;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.util.stream.Collectors.toSet;

public final class AntiqueTransportServer {
    public static final Map<UUID, ShipDataPacket.ShipEntry> lastKnownPositions = new HashMap<>();
    public static final Map<UUID, String> serverShipNames = new HashMap<>();
    public static final Map<UUID, String> serverShipIcons = new HashMap<>();

    private static int shipSyncTicker = 0;

    private AntiqueTransportServer() {
    }

    public static void register() {
        SableShipCollector.registerChunkTracking();
    }

    public static void tick(MinecraftServer server) {
        if (++shipSyncTicker < 20) return;
        shipSyncTicker = 0;

        List<ShipDataPacket.ShipEntry> allKnownShips = new ArrayList<>(lastKnownPositions.values());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, ShipDataPacket.TYPE)) continue;

            var visibleShips = SableShipCollector.collectShipsFor(player);
            Set<UUID> visibleIds = visibleShips.stream().map(ShipDataPacket.ShipEntry::uuid).collect(toSet());

            List<ShipDataPacket.ShipEntry> shipsForPlayer = new ArrayList<>(visibleShips);

            for (ShipDataPacket.ShipEntry cached : allKnownShips) {
                if (!visibleIds.contains(cached.uuid())) {
                    shipsForPlayer.add(cached);
                }
            }

            ServerPlayNetworking.send(player, new ShipDataPacket(shipsForPlayer));
        }
    }

    public static void handleShipUpdate(MinecraftServer server, ShipUpdatePacket payload) {
        UUID id = payload.shipId();

        if (payload.name() == null && payload.iconId() == null) {
            serverShipNames.remove(id);
            serverShipIcons.remove(id);
        } else {
            if (payload.name() != null) {
                serverShipNames.put(id, payload.name());
            }
            if (payload.iconId() != null) {
                serverShipIcons.put(id, payload.iconId());
            }
        }

        ShipSyncPacket sync = new ShipSyncPacket(
                id,
                serverShipNames.get(id),
                serverShipIcons.get(id),
                payload.name() == null && payload.iconId() == null
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, ShipSyncPacket.TYPE)) {
                ServerPlayNetworking.send(player, sync);
            }
        }
    }

    public static void syncAllKnownShipsTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ShipSyncPacket.TYPE)) {
            return;
        }

        for (Map.Entry<UUID, String> entry : serverShipNames.entrySet()) {
            ServerPlayNetworking.send(
                    player,
                    new ShipSyncPacket(
                            entry.getKey(),
                            entry.getValue(),
                            serverShipIcons.get(entry.getKey()),
                            false
                    )
            );
        }
    }

    public static void savePositionCache(MinecraftServer server) {
        try {
            Path file = getPositionCacheFile(server);
            Files.createDirectories(file.getParent());
            CompoundTag tag = new CompoundTag();

            ListTag list = new ListTag();
            for (ShipDataPacket.ShipEntry e : lastKnownPositions.values()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("uuid", e.uuid());
                entry.putDouble("x", e.x());
                entry.putDouble("y", e.y());
                entry.putDouble("z", e.z());
                entry.putFloat("yaw", e.yaw());
                list.add(entry);
            }
            tag.put("positions", list);

            CompoundTag namesTag = new CompoundTag();
            serverShipNames.forEach((uuid, name) -> namesTag.putString(uuid.toString(), name));
            tag.put("shipNames", namesTag);

            CompoundTag iconsTag = new CompoundTag();
            serverShipIcons.forEach((uuid, icon) -> iconsTag.putString(uuid.toString(), icon));
            tag.put("shipIcons", iconsTag);

            NbtIo.write(tag, file);
            AntiqueTransport.LOGGER.info("[Antique Transport] Saved {} ship positions", lastKnownPositions.size());
        } catch (IOException e) {
            AntiqueTransport.LOGGER.error("[Antique Transport] Failed to save position cache", e);
        }
    }

    public static void loadPositionCache(MinecraftServer server) {
        try {
            Path file = getPositionCacheFile(server);
            if (!Files.exists(file)) return;
            CompoundTag tag = NbtIo.read(file);
            if (tag == null) return;

            lastKnownPositions.clear();
            ListTag list = tag.getList("positions", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                UUID uuid = entry.getUUID("uuid");
                lastKnownPositions.put(uuid, new ShipDataPacket.ShipEntry(
                        uuid,
                        entry.getDouble("x"),
                        entry.getDouble("y"),
                        entry.getDouble("z"),
                        entry.getFloat("yaw")
                ));
            }

            CompoundTag namesTag = tag.getCompound("shipNames");
            serverShipNames.clear();
            for (String key : namesTag.getAllKeys()) {
                serverShipNames.put(UUID.fromString(key), namesTag.getString(key));
            }

            CompoundTag iconsTag = tag.getCompound("shipIcons");
            serverShipIcons.clear();
            for (String key : iconsTag.getAllKeys()) {
                serverShipIcons.put(UUID.fromString(key), iconsTag.getString(key));
            }

            AntiqueTransport.LOGGER.info("[Antique Transport] Loaded {} ship positions", lastKnownPositions.size());
        } catch (IOException e) {
            AntiqueTransport.LOGGER.error("[Antique Transport] Failed to load position cache", e);
        }
    }

    private static Path getPositionCacheFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("antique_transport_positions.dat");
    }
}