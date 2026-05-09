package me.mariz.antique_transport.server;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import me.mariz.antique_transport.client.compat.sable.ShipUtils;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public final class SableShipCollector {

    private SableShipCollector() {}

    public static List<ShipDataPacket.ShipEntry> collectShipsFor(ServerLevel level) {

        List<ShipDataPacket.ShipEntry> result = new ArrayList<>();
        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, ShipUtils.worldBounds(level))) {
            Pose3dc pose = ship.logicalPose();
            Vector3dc pos = pose.position();
            Quaterniondc q = pose.orientation();
            float yaw = ShipUtils.computeYaw(q);
            result.add(new ShipDataPacket.ShipEntry(
                    ship.getUniqueId(),
                    pos.x(), pos.y(), pos.z(), yaw
            ));
        }
        return result;
    }
}