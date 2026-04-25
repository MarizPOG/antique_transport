package me.mariz.antique_transport.client.compat.sable;

import folk.sisby.antique_atlas.gui.AtlasOverlay;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import me.mariz.antique_transport.client.AntiqueTransportConfig;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;

public final class SableAtlasOverlayCompat {
    private SableAtlasOverlayCompat() {
    }

    public static void onScreenRender(AtlasOverlay.AtlasScreenRenderContext context) {
        if (!AntiqueTransportConfig.get().aeronautics.showShips) {
            return;
        }

        ShipRenderer.renderShips(context);
    }

    public static boolean handleShipClick(AtlasScreen screen, int mouseX, int mouseY) {
        int mx = mouseX - screen.getGuiX();
        int my = mouseY - screen.getGuiY();

        for (Map.Entry<UUID, int[]> entry : ShipCache.shipScreenPositions.entrySet()) {
            int[] pos = entry.getValue();
            if (Math.abs(mx - pos[0]) <= 8 && Math.abs(my - pos[1]) <= 8) {
                screen.addChild(new ShipNameModal(entry.getKey()));
                return true;
            }
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }

        UUID clicked = ShipClickResolver.findShipAt(screen, mouseX, mouseY, level);
        if (clicked == null) {
            return false;
        }

        screen.addChild(new ShipNameModal(clicked));
        return true;
    }
}