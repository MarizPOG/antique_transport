package me.mariz.antique_transport.client.compat.create;

import com.mojang.blaze3d.platform.NativeImage;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TrackSpriteRenderer {
    private static final int COLOR_BALLAST = 0xFF9BC1D6;
    private static final int COLOR_BALLAST_OUTLINE = 0xFF111748;

    private static final float BALLAST_HALF_WIDTH = 2.4f;
    private static final float OUTLINE_EXTRA = 0.8f;

    private static final ResourceLocation TEXTURE_ID =
            ResourceLocation.tryBuild("antique_transport", "trackoverlay");

    private static DynamicTexture texture;

    private static double cachedScale = Double.NaN;
    private static ResourceKey<Level> cachedDimension;
    private static double cachedScrollX = Double.MIN_VALUE;
    private static double cachedScrollZ = Double.MIN_VALUE;
    private static int cachedWidth;
    private static int cachedHeight;

    private TrackSpriteRenderer() {
    }

    private record EdgeWithGraph(TrackEdge edge, TrackGraph graph) {
    }

    public static void render(GuiGraphics context, AtlasScreen screen) {
        double scale = screen.getPixelsPerBlock();
        if (scale < 0.05) {
            return;
        }

        int mapWidth = screen.mapWidth;
        int mapHeight = screen.mapHeight;
        ResourceKey<Level> dimension = screen.dim();
        double scrollX = screen.worldXToScreenX(0);
        double scrollZ = screen.worldZToScreenY(0);

        boolean dirty = scale != cachedScale
                || !dimension.equals(cachedDimension)
                || scrollX != cachedScrollX
                || scrollZ != cachedScrollZ
                || mapWidth != cachedWidth
                || mapHeight != cachedHeight;

        if (dirty) {
            cachedScale = scale;
            cachedDimension = dimension;
            cachedScrollX = scrollX;
            cachedScrollZ = scrollZ;
            cachedWidth = mapWidth;
            cachedHeight = mapHeight;
            rebuildTexture(screen, scale, dimension, mapWidth, mapHeight);
        }

        if (texture != null) {
            context.blit(TEXTURE_ID, 0, 0, 0, 0, mapWidth, mapHeight, mapWidth, mapHeight);
        }
    }

    private static void rebuildTexture(
            AtlasScreen screen,
            double scale,
            ResourceKey<Level> dimension,
            int mapWidth,
            int mapHeight
    ) {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
            texture.close();
            texture = null;
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, mapWidth, mapHeight, true);

        int ballastRadius = Math.max(1, (int) (BALLAST_HALF_WIDTH * scale));
        int outlineRadius = Math.max(ballastRadius + 1, (int) ((BALLAST_HALF_WIDTH + OUTLINE_EXTRA) * scale));

        List<EdgeWithGraph> allEdges = collectEdges(dimension);

        for (EdgeWithGraph edge : allEdges) {
            paintEdge(image, screen, scale, edge, outlineRadius, COLOR_BALLAST_OUTLINE, mapWidth, mapHeight);
        }

        for (EdgeWithGraph edge : allEdges) {
            paintEdge(image, screen, scale, edge, ballastRadius, COLOR_BALLAST, mapWidth, mapHeight);
        }

        texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
        texture.upload();
    }

    private static List<EdgeWithGraph> collectEdges(ResourceKey<Level> dimension) {
        List<EdgeWithGraph> result = new ArrayList<>();

        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            Set<TrackEdge> visited = Collections.newSetFromMap(new IdentityHashMap<>());

            for (TrackNodeLocation loc : graph.getNodes()) {
                if (!loc.dimension.equals(dimension)) {
                    continue;
                }

                TrackNode node = graph.locateNode(loc);
                Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
                if (connections == null) {
                    continue;
                }

                for (TrackEdge edge : connections.values()) {
                    if (!visited.add(edge)) {
                        continue;
                    }
                    if (edge.isInterDimensional()) {
                        continue;
                    }

                    result.add(new EdgeWithGraph(edge, graph));
                }
            }
        }

        return result;
    }

    private static void paintEdge(
            NativeImage image,
            AtlasScreen screen,
            double scale,
            EdgeWithGraph edgeWithGraph,
            int radius,
            int color,
            int mapWidth,
            int mapHeight
    ) {
        double length = edgeWithGraph.edge.getLength();
        if (length < 0.001) {
            return;
        }

        double stepInBlocks = Math.max(0.5, 1.0 / scale);
        int steps = Math.max(2, (int) Math.ceil(length / stepInBlocks));
        steps = Math.min(steps, 512);

        int previousX = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;

        for (int i = 0; i <= steps; i++) {
            Vec3 pos = edgeWithGraph.edge.getPosition(edgeWithGraph.graph, (double) i / steps);
            int screenX = toScreenX(screen, pos.x);
            int screenZ = toScreenZ(screen, pos.z);

            if (previousX != Integer.MIN_VALUE) {
                int x1 = Math.min(previousX, screenX) - radius;
                int z1 = Math.min(previousZ, screenZ) - radius;
                int x2 = Math.max(previousX, screenX) + radius + 1;
                int z2 = Math.max(previousZ, screenZ) + radius + 1;
                fillImage(image, x1, z1, x2, z2, color, mapWidth, mapHeight);
            }

            previousX = screenX;
            previousZ = screenZ;
        }
    }

    private static void fillImage(NativeImage image, int x1, int y1, int x2, int y2, int color, int mapWidth, int mapHeight) {
        x1 = Math.max(0, x1);
        y1 = Math.max(0, y1);
        x2 = Math.min(mapWidth, x2);
        y2 = Math.min(mapHeight, y2);

        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                image.setPixelRGBA(x, y, color);
            }
        }
    }

    private static int toScreenX(AtlasScreen screen, double worldX) {
        int floorX = (int) Math.floor(worldX);
        return (int) (
                screen.worldXToScreenX(floorX) - screen.getGuiX()
                        + (worldX - floorX) * screen.getPixelsPerBlock()
        );
    }

    private static int toScreenZ(AtlasScreen screen, double worldZ) {
        int floorZ = (int) Math.floor(worldZ);
        return (int) (
                screen.worldZToScreenY(floorZ) - screen.getGuiY()
                        + (worldZ - floorZ) * screen.getPixelsPerBlock()
        );
    }
}