package me.mariz.antique_transport.client.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.UUID;

public final class ShipClickResolver {
    private ShipClickResolver() {
    }

    public static UUID findShipAt(AtlasScreen screen, int mouseX, int mouseY, ClientLevel level) {
        int worldX = screen.screenXToWorldX(mouseX);
        int worldZ = screen.screenYToWorldZ(mouseY);

        BoundingBox3d clickArea = new BoundingBox3d(
                worldX - 16, -64, worldZ - 16,
                worldX + 16, 320, worldZ + 16
        );

        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, clickArea)) {
            return ship.getUniqueId();
        }

        return null;
    }
}