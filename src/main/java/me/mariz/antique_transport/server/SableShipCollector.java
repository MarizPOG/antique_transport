package me.mariz.antique_transport.server;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SableShipCollector {
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new HashMap<>();

    private SableShipCollector() {
    }

    public static void registerChunkTracking() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            LOADED_CHUNKS
                    .computeIfAbsent(world.dimension(), key -> new HashSet<>())
                    .add(ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z));
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            Set<Long> loaded = LOADED_CHUNKS.get(world.dimension());
            if (loaded != null) {
                loaded.remove(ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z));
                if (loaded.isEmpty()) {
                    LOADED_CHUNKS.remove(world.dimension());
                }
            }
        });
    }

    public static List<ShipDataPacket.ShipEntry> collectShipsFor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return collectShipsFor(level);
    }

    public static List<ShipDataPacket.ShipEntry> collectShipsFor(ServerLevel level) {
        Set<Long> loaded = LOADED_CHUNKS.get(level.dimension());
        if (loaded == null || loaded.isEmpty()) {
            return List.of();
        }

        List<ShipDataPacket.ShipEntry> result = new ArrayList<>();

        BoundingBox3dc queryBounds = buildLoadedChunksBounds(loaded);
        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, queryBounds)) {
            if (!intersectsLoadedChunks(ship.boundingBox(), loaded)) {
                continue;
            }

            Pose3dc pose = ship.logicalPose();
            Vector3dc pos = pose.position();
            Quaterniondc q = pose.orientation();
            float yaw = ShipUtils.computeYaw(q);

            result.add(new ShipDataPacket.ShipEntry(
                    ship.getUniqueId(),
                    pos.x(),
                    pos.y(),
                    pos.z(),
                    yaw
            ));
        }

        return result;
    }

    private static BoundingBox3dc buildLoadedChunksBounds(Set<Long> loaded) {
        int minChunkX = Integer.MAX_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        for (long packed : loaded) {
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            if (x < minChunkX) minChunkX = x;
            if (z < minChunkZ) minChunkZ = z;
            if (x > maxChunkX) maxChunkX = x;
            if (z > maxChunkZ) maxChunkZ = z;
        }

        double minX = minChunkX * 16.0;
        double minZ = minChunkZ * 16.0;
        double maxX = maxChunkX * 16.0 + 16.0;
        double maxZ = maxChunkZ * 16.0 + 16.0;

        return new BoundingBox3d(minX, -4096.0, minZ, maxX, 4096.0, maxZ);
    }

    private static boolean intersectsLoadedChunks(BoundingBox3dc box, Set<Long> loaded) {
        int minChunkX = Mth.floor(box.minX()) >> 4;
        int maxChunkX = Mth.floor(box.maxX()) >> 4;
        int minChunkZ = Mth.floor(box.minZ()) >> 4;
        int maxChunkZ = Mth.floor(box.maxZ()) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (loaded.contains(ChunkPos.asLong(chunkX, chunkZ))) {
                    return true;
                }
            }
        }

        return false;
    }
}