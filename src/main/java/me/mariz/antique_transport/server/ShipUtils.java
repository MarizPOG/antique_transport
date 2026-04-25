package me.mariz.antique_transport.server;

import org.joml.Quaterniondc;

public final class ShipUtils {
    private ShipUtils() {
    }

    /**
     * Computes ship yaw from quaternion orientation.
     * Standard Y-axis rotation formula for Minecraft atlas rendering.
     */
    public static float computeYaw(Quaterniondc q) {
        return (float) Math.toDegrees(Math.atan2(
                2.0 * (q.w() * q.y() + q.x() * q.z()),
                1.0 - 2.0 * (q.y() * q.y() + q.z() * q.z())
        ));
    }


    /**
     * Computes ship yaw from velocity vector (moving ship).
     * Uses horizontal movement direction.
     */
    public static float computeYawFromVelocity(double vx, double vz) {
        return (float) Math.toDegrees(Math.atan2(-vx, vz));
    }
    /**
     * Returns true if the sprite should be mirrored horizontally.
     */
    public static boolean needsFlip(float yaw) {
        float normalized = normalizeDegrees(yaw);
        return normalized > 0f && normalized < 180f;
    }
    /**
     * Normalizes angle to range [0, 360).
     */
    public static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360f;
        if (normalized < 0f) {
            normalized += 360f;
        }
        return normalized;
    }
    public static float computeCompassYaw(double vx, double vz) {
        // North=0°, East=90°, South=180°, West=270°
        // Minecraft: North=-Z, East=+X, so atan2(vx, -vz) gives compass bearing.
        float degrees = (float) Math.toDegrees(Math.atan2(vx, -vz));
        return (degrees + 360f) % 360f; // normalize to [0, 360)
    }
}
