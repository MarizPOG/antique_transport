package me.mariz.antique_transport.client.compat.sable;

import me.mariz.antique_transport.client.AntiqueTransportConfig;
import me.mariz.antique_transport.client.AtlasOverlay;
import me.mariz.antique_transport.client.compat.simulated.ShipDiagramRenderer;
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
 *
 * Lifecycle:
 *  - onJoin ->load()- restores persistent data from disk
 *  - onDisconnect ->save()-writes persistent data, clears runtime state
 *
 * Two operating modes:
 *  - SERVER_MOD: server sends ShipDataPacket every second → updateFromServer()
 *  - CLIENT_ONLY: no packet received → updateFromLocal() called from renderShips
 *                 for ships within the client's render distance
 *
 * Persistent data (written to .dat):
 *   shipNames, shipIcons, hiddenShips, shipDiagramEnabled, shipDiagramMinPpb,
 *   shipDiagramCameraOffsetY, lastKnownPositions
 *
 * Runtime data (not persisted):
 *   positionCache (live positions), cacheTimestamps, lastMovingYaw,
 *   subLevelNames, shipScreenPositions
 *
 * Visibility logic (CLIENT_ONLY):
 *   - Ship in render distance   → positionCache is current → rendered as diagram
 *   - Ship outside render distance → positionCache expired → rendered as icon
 *     using lastKnownPositions; "last seen" countdown active
 *   - Ship removed from the world → deleteShip() → disappears from the map
 */
public class ShipCache {

    public static final Logger LOGGER = LoggerFactory.getLogger("antique_transport");

    private static final long SERVER_TIMEOUT_TICKS  = 200L; // 10 seconds
    private static final long POSITION_EXPIRE_TICKS =  40L; //  2 seconds

    // Persistent
    public static final Map<UUID, String> shipNames  = new HashMap<>();
    public static final Map<UUID, ResourceLocation> shipIcons = new HashMap<>();
    public static final Set<UUID> hiddenShips = new HashSet<>();
    public static final Map<UUID, Boolean> shipDiagramEnabled = new HashMap<>();
    public static final Map<UUID, Float> shipDiagramMinPpb = new HashMap<>();
    public static final Map<UUID, Integer> shipDiagramCameraOffsetY = new HashMap<>();
    public static final Map<UUID, ShipDataPacket.ShipEntry> lastKnownPositions = new HashMap<>();

    // Runtime
    public static final Map<UUID, Long> lastSeenTick = new HashMap<>();
    public static final Map<UUID, ShipDataPacket.ShipEntry> positionCache = new HashMap<>();
    public static final Map<UUID, Long> cacheTimestamps = new HashMap<>();
    public static final Map<UUID, Float> lastMovingYaw = new HashMap<>();
    public static final Map<UUID, String> subLevelNames = new HashMap<>();
    public static final Map<UUID, int[]> shipScreenPositions = new HashMap<>();
    public static final Map<UUID, Double> lastSeenPlayerDistance = new HashMap<>();

    public static final List<ResourceLocation> SHIP_TEXTURES = new ArrayList<>();

    public static long lastServerPacketTime = -1L;
    public static UUID pendingModalShipId = null;

    private static int saveTicker = 0;

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        if (!isServerModPresent()) {
            // CLIENTONLY — disable runtime position of ships in unloaded chunks
            long now = currentTick();
            List<UUID> expired = new ArrayList<>();
            for (Map.Entry<UUID, Long> e : cacheTimestamps.entrySet()) {
                if (now - e.getValue() > POSITION_EXPIRE_TICKS) expired.add(e.getKey());
            }

            // Look for removed ships: lately in positionCache, disappeared near player
            for (UUID id : expired) {
                Double distWhenLastSeen = lastSeenPlayerDistance.get(id);
                if (distWhenLastSeen != null && distWhenLastSeen < 32.0) {
                    // Ship closer than 32 blocks gets deleted
                    deleteShip(id);
                } else {
                    // Assuming that the ship was unloaded but still exists
                    evictFromLive(id);
                }
            }
        }

        if (++saveTicker >= 200) { // auto-save every 10 seconds
            saveTicker = 0;
            save();
        }
    }

    // Position updates

    /**
     * SERVER_MOD: update positions from ships visible to the server.
     * Ships absent from the packet have their live position evicted, but lastKnown is kept.
     */
    public static void updateFromServer(List<ShipDataPacket.ShipEntry> ships) {
        Set<UUID> liveShips = new HashSet<>();
        for (ShipDataPacket.ShipEntry entry : ships) {
            liveShips.add(entry.uuid());
            updateLivePosition(entry.uuid(), entry);
        }
        lastServerPacketTime = currentTick();

        // Sublevels outside the server's render distance → evict live, keep lastKnown
        Set<UUID> outOfRange = new HashSet<>(positionCache.keySet());
        outOfRange.removeAll(liveShips);
        for (UUID id : outOfRange) evictFromLive(id);
    }

    /**
     * CLIENT_ONLY: update position from a local sublevel within the client's render distance.
     */
    public static void updateFromLocal(UUID uuid, double x, double y, double z, float yaw) {
        ShipDataPacket.ShipEntry entry = new ShipDataPacket.ShipEntry(uuid, x, y, z, yaw);
        updateLivePosition(uuid, entry);

        // Distance from player to ship
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double dx = x - mc.player.getX();
            double dz = z - mc.player.getZ();
            lastSeenPlayerDistance.put(uuid, Math.sqrt(dx * dx + dz * dz));
        }
    }

    private static void updateLivePosition(UUID uuid, ShipDataPacket.ShipEntry entry) {
        positionCache.put(uuid, entry);
        cacheTimestamps.put(uuid, currentTick());
        lastKnownPositions.put(uuid, entry);
        lastMovingYaw.put(uuid, entry.yaw());
        lastSeenTick.put(uuid, currentTick());
    }

    /**
     * Evict the live position of a ship that has left render distance.
     * Preserves lastKnownPositions, shipNames, diagram settings, etc.
     */
    private static void evictFromLive(UUID id) {
        positionCache.remove(id);
        if (!AntiqueTransportConfig.get().simulated.keepDiagramOutsideRenderDistance) {
            ShipDiagramRenderer.freeShip(id);
        }
    }

    /**
     * Fully remove a ship that has been disassembled or destroyed.
     * Clears all data, including persistent entries.
     */
    public static void deleteShip(UUID id) {
        positionCache.remove(id);
        cacheTimestamps.remove(id);
        lastMovingYaw.remove(id);
        subLevelNames.remove(id);
        shipScreenPositions.remove(id);
        shipNames.remove(id);
        shipIcons.remove(id);
        hiddenShips.remove(id);
        shipDiagramEnabled.remove(id);
        shipDiagramMinPpb.remove(id);
        shipDiagramCameraOffsetY.remove(id);
        lastKnownPositions.remove(id);
        lastSeenTick.remove(id);
        ShipDiagramRenderer.freeShip(id);
    }

    /**
     * Returns the position to use for map rendering.
     * Live (in render distance) → positionCache
     * Outside render distance   → lastKnownPositions
     */
    public static ShipDataPacket.ShipEntry getRenderPosition(UUID uuid) {
        ShipDataPacket.ShipEntry live = positionCache.get(uuid);
        if (live != null) return live;
        return lastKnownPositions.get(uuid);
    }

    /** Returns true if the ship is currently within render distance (has a live position). */
    public static boolean isLive(UUID uuid) {
        return positionCache.containsKey(uuid);
    }
    // Visibility and display

    public static boolean shouldRender(UUID uuid) {
        if (hiddenShips.contains(uuid)) return false;
        if (AntiqueTransportConfig.get().sable.autoShowAllShips) return true;
        return shipNames.containsKey(uuid);
    }

    public static boolean isDiagramEnabled(UUID uuid) {
        return shipDiagramEnabled.getOrDefault(uuid, true);
    }

    public static float getDiagramMinPpb(UUID uuid) {
        return shipDiagramMinPpb.getOrDefault(uuid, 0.25f);
    }

    public static int getDiagramCameraOffsetY(UUID uuid) {
        return shipDiagramCameraOffsetY.getOrDefault(uuid, 0);
    }

    public static ResourceLocation getDisplayIcon(UUID uuid) {
        if (shipIcons.containsKey(uuid)) return shipIcons.get(uuid);
        if (!SHIP_TEXTURES.isEmpty()) {
            int idx = Math.abs(uuid.hashCode()) % SHIP_TEXTURES.size();
            return SHIP_TEXTURES.get(idx);
        }
        return AtlasOverlay.SHIP_ICON;
    }

    /**
     * Resolves the display name for a ship, in priority order:
     * manual name → nameplate name (if enabled) → truncated UUID fallback.
     */
    public static String getResolvedShipName(UUID uuid, String subLevelName) {
        String manualName = shipNames.get(uuid);
        if (manualName != null && !manualName.isBlank()) return manualName;

        if (AntiqueTransportConfig.get().simulated.preferNameplateNames) {
            String resolved = (subLevelName != null && !subLevelName.isBlank())
                    ? subLevelName : subLevelNames.get(uuid);
            if (resolved != null && !resolved.isBlank()) return resolved;
        }

        return "Ship " + uuid.toString().substring(0, 8);
    }

    /**
     * Returns the initial value to pre-fill the ship name text field with.
     * Uses the manual name if set, otherwise falls back to the nameplate name.
     */
    public static String getInitialEditableShipName(UUID uuid) {
        if (shipNames.containsKey(uuid)) return shipNames.get(uuid);
        if (AntiqueTransportConfig.get().simulated.preferNameplateNames) {
            String resolved = subLevelNames.get(uuid);
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        return "";
    }

    public static void onDisconnect() {
        save();
        // Clear runtime state
        positionCache.clear();
        cacheTimestamps.clear();
        lastMovingYaw.clear();
        lastSeenPlayerDistance.clear();
        subLevelNames.clear();
        shipScreenPositions.clear();
        lastServerPacketTime = -1L;
        pendingModalShipId   = null;
        lastSeenTick.clear();
        // FBOs must be released on the render thread
        Minecraft.getInstance().execute(ShipDiagramRenderer::freeAll);
    }

    public static void save() {
        try {
            Path file = getSaveFile();
            Files.createDirectories(file.getParent());
            CompoundTag tag = new CompoundTag();

            // Sublevel names
            CompoundTag names = new CompoundTag();
            shipNames.forEach((id, name) -> names.putString(id.toString(), name));
            tag.put("names", names);

            // Sublevel icons
            CompoundTag icons = new CompoundTag();
            shipIcons.forEach((id, rl) -> icons.putString(id.toString(), rl.toString()));
            tag.put("icons", icons);

            // Hidden Sublevels
            ListTag hidden = new ListTag();
            hiddenShips.forEach(id -> hidden.add(StringTag.valueOf(id.toString())));
            tag.put("hidden", hidden);

            // Last known positions
            CompoundTag positions = new CompoundTag();
            for (Map.Entry<UUID, ShipDataPacket.ShipEntry> e : lastKnownPositions.entrySet()) {
                CompoundTag pos = new CompoundTag();
                pos.putDouble("x", e.getValue().x());
                pos.putDouble("y", e.getValue().y());
                pos.putDouble("z", e.getValue().z());
                pos.putFloat("yaw", e.getValue().yaw());
                positions.put(e.getKey().toString(), pos);
            }
            tag.put("lastKnownPositions", positions);

            // Diagram settings (stored per ship)
            CompoundTag diagramSettings = new CompoundTag();
            Set<UUID> allDiagramIds = new HashSet<>();
            allDiagramIds.addAll(shipDiagramEnabled.keySet());
            allDiagramIds.addAll(shipDiagramCameraOffsetY.keySet());
            for (UUID id : allDiagramIds) {
                CompoundTag ds = new CompoundTag();
                ds.putBoolean("enabled", shipDiagramEnabled.getOrDefault(id, true));
                ds.putFloat("minPpb", shipDiagramMinPpb.getOrDefault(id, 0.25f));
                ds.putInt("cameraOffsetY", shipDiagramCameraOffsetY.getOrDefault(id, 0));
                diagramSettings.put(id.toString(), ds);
            }
            tag.put("diagramSettings", diagramSettings);

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
            for (String key : names.getAllKeys())
                shipNames.put(UUID.fromString(key), names.getString(key));

            shipIcons.clear();
            CompoundTag icons = tag.getCompound("icons");
            for (String key : icons.getAllKeys())
                shipIcons.put(UUID.fromString(key), ResourceLocation.tryParse(icons.getString(key)));

            hiddenShips.clear();
            ListTag hidden = tag.getList("hidden", 8);
            for (int i = 0; i < hidden.size(); i++)
                hiddenShips.add(UUID.fromString(hidden.getString(i)));

            // Last known positions -> also seed lastMovingYaw for immediate rendering
            lastKnownPositions.clear();
            lastMovingYaw.clear();
            CompoundTag positions = tag.getCompound("lastKnownPositions");
            for (String key : positions.getAllKeys()) {
                CompoundTag pos  = positions.getCompound(key);
                UUID        uuid = UUID.fromString(key);
                float       yaw  = pos.getFloat("yaw");
                ShipDataPacket.ShipEntry entry = new ShipDataPacket.ShipEntry(
                        uuid, pos.getDouble("x"), pos.getDouble("y"), pos.getDouble("z"), yaw);
                positionCache.put(uuid, entry);
                lastKnownPositions.put(uuid, entry);
                lastMovingYaw.put(uuid, yaw);
            }

            shipDiagramEnabled.clear();
            shipDiagramMinPpb.clear();
            shipDiagramCameraOffsetY.clear();
            CompoundTag diagramSettings = tag.getCompound("diagramSettings");
            for (String key : diagramSettings.getAllKeys()) {
                UUID uuid = UUID.fromString(key);
                CompoundTag ds = diagramSettings.getCompound(key);
                shipDiagramEnabled.put(uuid, ds.getBoolean("enabled"));
                shipDiagramMinPpb.put(uuid, ds.getFloat("minPpb"));
            }

        } catch (IOException e) {
            LOGGER.error("[Antique Transport] Failed to load ship data", e);
        }
    }

    public static long currentTick() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null ? level.getGameTime() : 0L;
    }

    public static boolean isServerModPresent() {
        return lastServerPacketTime >= 0
                && currentTick() - lastServerPacketTime < SERVER_TIMEOUT_TICKS;
    }

    /** Returns the path to the persistent ship data file for the current world or server. */
    public static Path getSaveFile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            Path worldRoot = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            return worldRoot.resolve("data").resolve("antique_transport_ships.dat");
        }
        String serverName = "unknown";
        if (mc.getCurrentServer() != null)
            serverName = mc.getCurrentServer().ip.replace(":", "_").replace(".", "_");
        return mc.gameDirectory.toPath()
                .resolve("antique_transport")
                .resolve(serverName)
                .resolve("ships.dat");
    }

    /** Formats a tick duration as a human-readable age string (e.g. "2m 35s"). */
    public static String formatAge(long ticks) {
        long seconds = ticks / 20L;
        if (seconds < 60)   return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }
}