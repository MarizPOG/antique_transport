package me.mariz.antique_transport.client.compat.create;

import folk.sisby.antique_atlas.gui.AtlasOverlay;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import me.mariz.antique_transport.client.AntiqueTransportClient;
import me.mariz.antique_transport.client.AntiqueTransportConfig;
import me.mariz.antique_transport.client.TrainBookmarkButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

public final class CreateAtlasOverlayCompat {
    private CreateAtlasOverlayCompat() {}

    public static void onScreenInit(AtlasScreen screen) {
        try {
            // Respect temporarilyHidden on screen open — don't show stations
            // if the user had them hidden before closing the atlas.
            AntiqueTransportConfig config = AntiqueTransportConfig.get();
            boolean showStations = config.create.showStations && !TrainBookmarkButton.isTemporarilyHidden();
            StationLandmarkSync.sync(showStations);
        } catch (Exception e) {
            AntiqueTransportClient.LOGGER.error("Create station sync failed", e);
        }
    }

    public static void onScreenRender(AtlasOverlay.AtlasScreenRenderContext context) {
        AntiqueTransportConfig config = AntiqueTransportConfig.get();
        boolean hidden = TrainBookmarkButton.isTemporarilyHidden();

        if (config.create.showTracks && !hidden) {
            TrackSpriteRenderer.render(context.context(), context.screen());
        }

        if (config.create.showTrains && !hidden) {
            renderTrains(context);
        }
    }

    private static void renderTrains(AtlasOverlay.AtlasScreenRenderContext context) {
        AtlasScreen screen = context.screen();
        var dimension = screen.dim();

        com.simibubi.create.compat.trainmap.TrainMapManager.tick(dimension);

        int mapMinX = screen.getGuiX();
        int mapMinY = screen.getGuiY();
        int mapMaxX = mapMinX + screen.bookWidth;
        int mapMaxY = mapMinY + screen.bookHeight;

        int worldMinX = screen.screenXToWorldX(mapMinX);
        int worldMinZ = screen.screenYToWorldZ(mapMinY);
        int worldMaxX = screen.screenXToWorldX(mapMaxX);
        int worldMaxZ = screen.screenYToWorldZ(mapMaxY);

        Rect2i worldBounds = new Rect2i(
                worldMinX - 64,
                worldMinZ - 64,
                worldMaxX - worldMinX + 128,
                worldMaxZ - worldMinZ + 128
        );

        int worldMouseX = screen.screenXToWorldX(context.mouseX());
        int worldMouseZ = screen.screenYToWorldZ(context.mouseY());

        var matrices = context.context().pose();
        matrices.pushPose();
        matrices.translate(screen.bookWidth / 2f, screen.bookHeight / 2f, 0.03f);

        double scale = screen.getPixelsPerBlock();
        matrices.scale((float) scale, (float) scale, 1f);
        matrices.translate(-(worldMinX + worldMaxX) / 2.0, -(worldMinZ + worldMaxZ) / 2.0, 0);

        TrainRenderer.RenderResult result = TrainRenderer.render(
                context.context(),
                worldMouseX,
                worldMouseZ,
                0f,
                worldBounds,
                scale,
                dimension
        );

        matrices.popPose();

        if (result != null && result.hasTooltip()) {
            context.context().renderComponentTooltip(
                    Minecraft.getInstance().font,
                    result.tooltip(),
                    context.mouseX() - screen.getGuiX(),
                    context.mouseY() - screen.getGuiY()
            );
        }
    }
}