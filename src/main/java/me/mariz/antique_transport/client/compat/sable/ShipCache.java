package me.mariz.antique_transport.client.compat.sable;

import me.mariz.antique_transport.client.AntiqueTransportConfig;
import me.mariz.antique_transport.client.AtlasOverlay;
import me.mariz.antique_transport.server.ShipDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages client-side ship state.
 * Works in two modes:
 *  - SERVER_MOD: server sends ShipDataPacket every second → live cache updates
 *  - CLIENT_ONLY: no packet for >10s → file data with last-seen timestamps
 */
public class ShipCache {

    public static final Logger LOGGER = LoggerFactory.getLogger("antique_transport");

    private static final long SERVER_TIMEOUT_MS = 10_000;

    public static final Map<UUID, String> shipNames = new HashMap<>();
    public static final Map<UUID, ResourceLocation> shipIcons = new HashMap<>();
    public static final Set<UUID> hiddenShips = new HashSet<>();
    public static final List<ResourceLocation> SHIP_TEXTURES = new ArrayList<>();

    public static final Map<UUID, int[]> shipScreenPositions = new HashMap<>();

    public static final Map<UUID, ShipDataPacket.ShipEntry> positionCache = new HashMap<>();
    public static final Map<UUID, Long> cacheTimestamps = new HashMap<>();
    public static final Map<UUID, Float> lastMovingYaw = new HashMap<>();

    public static long currentTick() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null ? level.getGameTime() : 0L;
    }
    private static int saveTicker = 0;

    public static void tick() {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        if (++saveTicker >= 20) {
            saveTicker = 0;
            save();
        }
    }
    public static long lastServerPacketTime = -1;

    public static boolean isServerModPresent() {
        return lastServerPacketTime >= 0
                && currentTick() - lastServerPacketTime < SERVER_TIMEOUT_MS;
    }

    public static void updateFromServer(List<ShipDataPacket.ShipEntry> ships) {
        Set<UUID> liveShips = new HashSet<>();

        for (ShipDataPacket.ShipEntry entry : ships) {
            liveShips.add(entry.uuid());
            positionCache.put(entry.uuid(), entry);
            cacheTimestamps.put(entry.uuid(), currentTick());
        }

        lastServerPacketTime = currentTick();
    }

    public static void updateFromLocal(UUID uuid, double x, double y, double z, float yaw) {
        positionCache.put(uuid, new ShipDataPacket.ShipEntry(uuid, x, y, z, yaw));
        cacheTimestamps.put(uuid, currentTick());
    }

    public static UUID pendingModalShipId = null;

    public static String getDisplayName(UUID uuid) {
        return shipNames.getOrDefault(uuid, "Ship " + uuid.toString().substring(0, 8));
    }

    public static ResourceLocation getDisplayIcon(UUID uuid) {
        if (shipIcons.containsKey(uuid)) return shipIcons.get(uuid);
        if (!SHIP_TEXTURES.isEmpty()) {
            int idx = Math.abs(uuid.hashCode()) % SHIP_TEXTURES.size();
            return SHIP_TEXTURES.get(idx);
        }
        return AtlasOverlay.SHIP_ICON;
    }

    public static boolean shouldRender(UUID uuid) {
        if (hiddenShips.contains(uuid)) return false;
        if (AntiqueTransportConfig.get().aeronautics.autoShowAllShips) return true;
        return shipNames.containsKey(uuid);
    }

    public static void onDisconnect() {
        save();
        lastServerPacketTime = -1;
        // Keep positionCache/cacheTimestamps as historical data
        shipScreenPositions.clear();
    }

    public static Path getSaveFile() {
        Minecraft mc = Minecraft.getInstance();

        // Singleplayer: store inside the world save
        if (mc.getSingleplayerServer() != null) {
            Path worldRoot = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            return worldRoot.resolve("data").resolve("antique_transport_ships.dat");
        }

        // Multiplayer: store in global client folder per server
        String serverName = "unknown";
        if (mc.getCurrentServer() != null) {
            serverName = mc.getCurrentServer().ip.replace(":", "_").replace(".", "_");
        }

        return mc.gameDirectory.toPath()
                .resolve("antique_transport")
                .resolve(serverName)
                .resolve("ships.dat");
    }

    public static void save() {
        try {
            Path file = getSaveFile();
            Files.createDirectories(file.getParent());
            CompoundTag tag = new CompoundTag();

            CompoundTag names = new CompoundTag();
            shipNames.forEach((id, name) -> names.putString(id.toString(), name));
            tag.put("names", names);

            CompoundTag icons = new CompoundTag();
            shipIcons.forEach((id, rl) -> icons.putString(id.toString(), rl.toString()));
            tag.put("icons", icons);

            ListTag hidden = new ListTag();
            hiddenShips.forEach(id -> hidden.add(StringTag.valueOf(id.toString())));
            tag.put("hidden", hidden);

            // Always save position cache, regardless of mode
            CompoundTag positions = new CompoundTag();
            for (Map.Entry<UUID, ShipDataPacket.ShipEntry> e : positionCache.entrySet()) {
                CompoundTag pos = new CompoundTag();
                pos.putDouble("x", e.getValue().x());
                pos.putDouble("y", e.getValue().y());
                pos.putDouble("z", e.getValue().z());
                pos.putFloat("yaw", e.getValue().yaw());
                positions.put(e.getKey().toString(), pos);
            }
            tag.put("cachedPositions", positions);

            CompoundTag timestamps = new CompoundTag();
            cacheTimestamps.forEach((id, ts) -> timestamps.putLong(id.toString(), ts));
            tag.put("cacheTimestamps", timestamps);

            NbtIo.write(tag, file);
        } catch (IOException e) {
            LOGGER.error("[Antique Transport] Failed to save ship data", e);
        }
    }

    public static void load() {
        Path file = getSaveFile();
        if (!Files.exists(file)) return;
        try {
            CompoundTag tag = NbtIo.read(file);
            if (tag == null) return;

            shipNames.clear();
            CompoundTag names = tag.getCompound("names");
            for (String key : names.getAllKeys()) {
                shipNames.put(UUID.fromString(key), names.getString(key));
            }

            shipIcons.clear();
            CompoundTag icons = tag.getCompound("icons");
            for (String key : icons.getAllKeys()) {
                shipIcons.put(UUID.fromString(key), ResourceLocation.tryParse(icons.getString(key)));
            }

            hiddenShips.clear();
            ListTag hidden = tag.getList("hidden", 8);
            for (int i = 0; i < hidden.size(); i++) {
                hiddenShips.add(UUID.fromString(hidden.getString(i)));
            }

            positionCache.clear();
            lastMovingYaw.clear();

            CompoundTag positions = tag.getCompound("cachedPositions");
            for (String key : positions.getAllKeys()) {
                CompoundTag pos = positions.getCompound(key);
                UUID uuid = UUID.fromString(key);
                float yaw = pos.getFloat("yaw");

                positionCache.put(uuid, new ShipDataPacket.ShipEntry(
                        uuid,
                        pos.getDouble("x"),
                        pos.getDouble("y"),
                        pos.getDouble("z"),
                        yaw
                ));

                lastMovingYaw.put(uuid, yaw);
            }

            cacheTimestamps.clear();
            CompoundTag timestamps = tag.getCompound("cacheTimestamps");
            for (String key : timestamps.getAllKeys()) {
                cacheTimestamps.put(UUID.fromString(key), timestamps.getLong(key));
            }

            // Ships with names but no timestamp → set load time
            long loadTime = currentTick();
            for (UUID id : shipNames.keySet()) {
                cacheTimestamps.putIfAbsent(id, loadTime);
            }

        } catch (IOException e) {
            LOGGER.error("[Antique Transport] Failed to load ship data", e);
        }
    }

    public static String formatAge(long ticks) {
        long seconds = ticks / 20L;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }
}