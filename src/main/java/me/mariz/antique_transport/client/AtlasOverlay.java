package me.mariz.antique_transport.client;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import me.mariz.antique_transport.client.compat.ModCompat;
import me.mariz.antique_transport.client.compat.create.CreateAtlasOverlayCompat;
import me.mariz.antique_transport.client.compat.create.TrainRenderer;
import me.mariz.antique_transport.client.compat.sable.SableAtlasOverlayCompat;
import me.mariz.antique_transport.client.compat.sable.ShipCache;
import me.mariz.antique_transport.client.compat.sable.ShipNameModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class AtlasOverlay implements folk.sisby.antique_atlas.gui.AtlasOverlay {
    public static final ResourceLocation SHIP_ICON =
            ResourceLocation.tryBuild("antique_transport", "textures/gui/ship_marker.png");

    @Override
    public void onScreenInit(AtlasScreen screen) {
        TrainBookmarkButton.create(screen);
        ShipBookmarkButton.create(screen);

        if (ModCompat.CREATE) {
            CreateAtlasOverlayCompat.onScreenInit(screen);
        }
    }

    @Override
    public void onScreenRender(folk.sisby.antique_atlas.gui.AtlasOverlay.AtlasScreenRenderContext context) {
        if (ShipCache.pendingModalShipId != null) {
            context.screen().addChild(new ShipNameModal(ShipCache.pendingModalShipId));
            ShipCache.pendingModalShipId = null;
        }

        if (ModCompat.CREATE) {
            CreateAtlasOverlayCompat.onScreenRender(context);
        }

        if (ModCompat.SABLE) {
            SableAtlasOverlayCompat.onScreenRender(context);
        }

        renderPriorityTooltip(context);
    }

    @Override
    public void onRender(folk.sisby.antique_atlas.gui.AtlasOverlay.AtlasRenderContext context) {
        // Low-level atlas pass — no screen context available here.
        // Rendering is handled in onScreenRender above.
    }

    public static boolean handleShipClickStatic(AtlasScreen screen, int mouseX, int mouseY) {
        if (!ModCompat.SABLE) {
            return false;
        }
        return SableAtlasOverlayCompat.handleShipClick(screen, mouseX, mouseY);
    }

    private void renderPriorityTooltip(AtlasScreenRenderContext context) {
        AtlasScreen screen = context.screen();

        if (hasShipNameModal(screen)) {
            return;
        }

        AntiqueTransportConfig config = AntiqueTransportConfig.get();
        boolean trainHidden = TrainBookmarkButton.isTemporarilyHidden();

        // Trains — respect both config and temporary hide
        if (ModCompat.CREATE && config.create.showTrains && !trainHidden) {
            List<Component> trainTooltip = getTrainTooltip(context);
            if (trainTooltip != null) {
                renderTooltip(context, trainTooltip);
                return;
            }
        }

        // Ships — config is the sole source of truth (no temporarilyHidden for ships)
        if (ModCompat.SABLE && config.sable.showShips) {
            List<Component> shipTooltip = getShipTooltip(context);
            if (shipTooltip != null) {
                renderTooltip(context, shipTooltip);
                return;
            }
        }

        // Stations — respect both config and temporary hide
        if (ModCompat.CREATE && config.create.showStations && !trainHidden) {
            List<Component> stationTooltip = getStationTooltip(context);
            if (stationTooltip != null) {
                renderTooltip(context, stationTooltip);
            }
        }
    }

    private boolean hasShipNameModal(AtlasScreen screen) {
        return screen.getChildren().stream()
                .anyMatch(child -> child instanceof ShipNameModal);
    }

    private void renderTooltip(AtlasScreenRenderContext context, List<Component> tooltip) {
        context.context().renderComponentTooltip(
                Minecraft.getInstance().font,
                tooltip,
                context.mouseX() - context.screen().getGuiX(),
                context.mouseY() - context.screen().getGuiY()
        );
    }

    private List<Component> getTrainTooltip(AtlasScreenRenderContext context) {
        AtlasScreen screen = context.screen();
        int worldMouseX = screen.screenXToWorldX(context.mouseX());
        int worldMouseZ = screen.screenYToWorldZ(context.mouseY());

        TrainRenderer.RenderResult result = TrainRenderer.render(
                context.context(),
                worldMouseX, worldMouseZ, 0f,
                getWorldBounds(screen),
                screen.getPixelsPerBlock(),
                screen.dim()
        );

        return result != null && result.hasTooltip() ? result.tooltip() : null;
    }

    private List<Component> getShipTooltip(AtlasScreenRenderContext context) {
        return null; // TODO
    }

    private List<Component> getStationTooltip(AtlasScreenRenderContext context) {
        return null; // TODO
    }

    private Rect2i getWorldBounds(AtlasScreen screen) {
        int guiX = screen.getGuiX(), guiY = screen.getGuiY();
        int worldMinX = screen.screenXToWorldX(guiX);
        int worldMinZ = screen.screenYToWorldZ(guiY);
        int worldMaxX = screen.screenXToWorldX(guiX + screen.bookWidth);
        int worldMaxZ = screen.screenYToWorldZ(guiY + screen.bookHeight);
        return new Rect2i(
                worldMinX - 64, worldMinZ - 64,
                worldMaxX - worldMinX + 128, worldMaxZ - worldMinZ + 128
        );
    }
}