package me.mariz.antique_transport.client.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.UUID;

public final class SableShipLocator {
    private SableShipLocator() {
    }

    public static UUID findNearestShipId(LocalPlayer player, ClientLevel level) {
        BoundingBox3d searchBounds = new BoundingBox3d(
                player.getX() - 16, player.getY() - 16, player.getZ() - 16,
                player.getX() + 16, player.getY() + 16, player.getZ() + 16
        );

        SubLevelAccess nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, searchBounds)) {
            var pos = ship.logicalPose().position();
            double dist = pos.distanceSquared(player.getX(), player.getY(), player.getZ());

            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = ship;
            }
        }

        return nearest != null ? nearest.getUniqueId() : null;
    }
}