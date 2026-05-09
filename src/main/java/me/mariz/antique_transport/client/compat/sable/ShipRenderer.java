package me.mariz.antique_transport.client.compat.sable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import folk.sisby.antique_atlas.gui.AtlasOverlay;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import me.mariz.antique_transport.client.AntiqueTransportConfig;
import me.mariz.antique_transport.client.compat.ModCompat;
import me.mariz.antique_transport.client.compat.simulated.ShipDiagramRenderer;
import me.mariz.antique_transport.server.ShipDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.*;
import java.util.stream.Collectors;

public final class ShipRenderer {

    private ShipRenderer() {
    }

    private record ShipRenderData(UUID uuid, double x, double y, double z, float yawIcon, float yawDiagram, String subLevelName) {
    }
    public static void renderShips(AtlasOverlay.AtlasScreenRenderContext context) {
        AtlasScreen screen = context.screen();
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        final boolean[] hoverConsumed = {false};
        Map<UUID, ShipRenderData> toRender = new HashMap<>();

        // Step 1 – Seed from cached/server positions
        for (UUID uuid : ShipCache.shipNames.keySet()) {
            ShipDataPacket.ShipEntry pos = ShipCache.getRenderPosition(uuid);
            if (pos == null) {
                continue;
            }
            float yawIcon = ShipCache.lastMovingYaw.getOrDefault(uuid, pos.yaw());
            toRender.put(uuid, new ShipRenderData(uuid, pos.x(), pos.y(), pos.z(),
                    yawIcon, yawIcon, null));
        }

        // Step 2: Overwrite with live Sable data when available
        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, ShipUtils.worldBounds(level))) {
            Pose3dc pose = ship.logicalPose();
            UUID id = ship.getUniqueId();

            if (Minecraft.getInstance().screen instanceof AtlasScreen && ModCompat.SIMULATED) {
                ShipDiagramRenderer.renderDiagram(id, (ClientSubLevel) ship, partialTicks);
            }

            // Yaw for diagram
            var q = pose.orientation();
            float yawDiagram = (float) Math.toDegrees(
                    Math.atan2(
                            2.0 * (q.w() * q.y() + q.x() * q.z()),
                            1.0 - 2.0 * (q.y() * q.y() + q.z() * q.z())
                    )
            );

            // Yaw for icon
            ShipDataPacket.ShipEntry lastEntry = ShipCache.positionCache.get(id);
            float yawIcon;
            if (lastEntry != null) {
                double dvx = pose.position().x() - lastEntry.x();
                double dvz = pose.position().z() - lastEntry.z();
                double lenSq = dvx * dvx + dvz * dvz;
                if (lenSq > 1e-8) {
                    yawIcon = ShipUtils.computeCompassYaw(dvx, dvz);
                    ShipCache.lastMovingYaw.put(id, yawIcon);
                } else {
                    yawIcon = ShipCache.lastMovingYaw.getOrDefault(id, 0f);
                }
            } else {
                yawIcon = ShipCache.lastMovingYaw.getOrDefault(id, 0f);
            }

            String subLevelName = null;
            try { subLevelName = ship.getName(); } catch (Exception ignored) {}
            if (subLevelName != null && !subLevelName.isBlank())
                ShipCache.subLevelNames.put(id, subLevelName);

            toRender.put(id, new ShipRenderData(id,
                    pose.position().x(), pose.position().y(), pose.position().z(),
                    yawIcon, yawDiagram, subLevelName));

            ShipCache.updateFromLocal(id, pose.position().x(), pose.position().y(),
                    pose.position().z(), yawIcon);
        }

        // Step 3: Render
        toRender.values().stream()
                .filter(d -> ShipCache.shouldRender(d.uuid()))
                .sorted(Comparator.comparingDouble(ShipRenderData::y))
                .forEach(d -> renderShipMarker(context, d, screen, hoverConsumed));
    }
    private static void renderShipMarker(
            AtlasOverlay.AtlasScreenRenderContext context,
            ShipRenderData data,
            AtlasScreen screen,
            boolean[] hoverConsumed) {

        int screenX = (int) screen.worldXToScreenX(data.x()) - screen.getGuiX();
        int screenY = (int) screen.worldZToScreenY(data.z()) - screen.getGuiY();
        boolean drawnDiagram = false;

        GuiGraphics graphics = context.context();
        ShipCache.shipScreenPositions.put(data.uuid(), new int[]{screenX, screenY});

        // Sublevel diagram
        if (ModCompat.SIMULATED && AntiqueTransportConfig.get().simulated.enableShipPreviews) {
            boolean isLive = ShipCache.isLive(data.uuid());
            boolean diagramAllowed = isLive
                    || AntiqueTransportConfig.get().simulated.keepDiagramOutsideRenderDistance;
            float ppb = (float) screen.getPixelsPerBlock();
            AdvancedFbo fbo = ShipDiagramRenderer.getFinalFbo(data.uuid());
            float[] frustum = ShipDiagramRenderer.getShipFrustumSize(data.uuid());

            if (fbo != null
                    && ShipDiagramRenderer.hasDiagramContent(data.uuid())
                    && ShipCache.isDiagramEnabled(data.uuid())
                    && diagramAllowed
                    && ppb >= ShipCache.getDiagramMinPpb(data.uuid()) * 2
                    && frustum != null && frustum[0] > 0) {

                int dispW = Math.max(1, Math.round(frustum[0] * ppb));
                int dispH = Math.max(1, Math.round(frustum[1] * ppb));

                int mx = context.mouseX() - screen.getGuiX();
                int my = context.mouseY() - screen.getGuiY();
                float rad = (float) Math.toRadians(-data.yawDiagram());
                float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
                float localX = cos * (mx - screenX) - sin * (my - screenY);
                float localZ = sin * (mx - screenX) + cos * (my - screenY);
                boolean physHovered = Math.abs(localX) <= dispW / 2f
                        && Math.abs(localZ) <= dispH / 2f;
                boolean effectivelyHovered = physHovered && !hoverConsumed[0];
                if (effectivelyHovered) hoverConsumed[0] = true;

                int bx = -dispW / 2, by = -dispH / 2;
                int tint = effectivelyHovered ? 0xFFAAAAAA : 0xFFFFFFFF;

                graphics.pose().pushPose();
                graphics.pose().translate(screenX, screenY, 0.1f);
                graphics.pose().mulPose(Axis.ZN.rotationDegrees(data.yawDiagram()));

                // Texture of diagram with hovering tinting
                renderFboTexture(graphics, fbo.getColorTextureAttachment(0).getId(),
                        bx, by, dispW, dispH, tint);
                graphics.pose().popPose();
                drawnDiagram = true;
            }
        }

        // Static icon (fallback)
        if (!drawnDiagram) {
            int hitbox = 16;

            int mx = context.mouseX() - screen.getGuiX();
            int my = context.mouseY() - screen.getGuiY();
            boolean physHovered = Math.abs(mx - screenX) <= hitbox
                    && Math.abs(my - screenY) <= hitbox;
            boolean effectivelyHovered = physHovered && !hoverConsumed[0];
            if (effectivelyHovered) hoverConsumed[0] = true;

            ResourceLocation icon = ShipCache.getDisplayIcon(data.uuid());
            float renderYaw = data.yawIcon() + 90f;
            boolean flipX = ShipUtils.needsFlip(data.yawIcon());

            graphics.pose().pushPose();
            graphics.pose().translate(screenX, screenY, 0.1f);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(renderYaw));

            // Icon
            graphics.pose().scale(0.7f, 0.7f, 1f);
            // Tint
            if (effectivelyHovered) {
                RenderSystem.setShaderColor(0.67f, 0.67f, 0.67f, 1.0f);
            }

            if (flipX) {
                RenderSystem.disableCull();
                graphics.pose().scale(1f, -1f, 1f);
                graphics.blit(icon, -16, -16, 0, 0, 32, 32, 32, 32);
                graphics.pose().scale(1f, -1f, 1f);
                RenderSystem.enableCull();
            } else {
                graphics.blit(icon, -16, -16, 0, 0, 32, 32, 32, 32);
            }

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.pose().popPose();
        }

        if (hoverConsumed[0]) {
            renderTooltipIfHovered(context, data, screenX, screenY, screen);
        }
    }

    // For full render of sublevels
    private static void renderFboTexture(GuiGraphics graphics, int textureId,
                                         int x, int y, int width, int height, int tint) {
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = graphics.pose().last().pose();
        drawQuad(matrix, x, y, width, height, tint);
        RenderSystem.disableBlend();
    }
    private static void drawQuad(Matrix4f matrix, int x, int y,
                                 int width, int height, int color) {
        float x2 = x + width, y2 = y + height;
        var buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.addVertex(matrix, x,  y,  0).setUv(0, 1).setColor(color);
        buf.addVertex(matrix, x,  y2, 0).setUv(0, 0).setColor(color);
        buf.addVertex(matrix, x2, y2, 0).setUv(1, 0).setColor(color);
        buf.addVertex(matrix, x2, y,  0).setUv(1, 1).setColor(color);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
    private static boolean renderTooltipIfHovered(
            AtlasOverlay.AtlasScreenRenderContext context,
            ShipRenderData data,
            int screenX, int screenY,
            AtlasScreen screen
    ) {
        int mx = context.mouseX() - screen.getGuiX();
        int my = context.mouseY() - screen.getGuiY();

        if (Math.abs(mx - screenX) > 16 || Math.abs(my - screenY) > 16) {
            return false;
        }

        // Do not render tooltip while rename modal is open.
        boolean modalOpen = screen.getChildren().stream()
                .anyMatch(child -> child instanceof ShipNameModal);
        if (modalOpen) {
            return false;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(ShipCache.getResolvedShipName(data.uuid(), data.subLevelName)));

        if (AntiqueTransportConfig.get().sable.showShipHeight) {
            tooltip.add(
                    Component.literal("Altitude: " + (int) data.y() + " m")
                            .withStyle(style -> style.withColor(0xAAAAAA))
            );
        }

        // Show "last seen" only for cached (non-live) data.
        if (!ShipCache.isLive(data.uuid()) && AntiqueTransportConfig.get().sable.showLastSeen) {
            Long ts = ShipCache.lastSeenTick.get(data.uuid());
            if (ts != null) {
                var level = Minecraft.getInstance().level;
                long ticksAgo = level != null ? level.getGameTime() - ts : 0L;
                tooltip.add(
                        Component.literal("Last seen: " + ShipCache.formatAge(ticksAgo) + " ago")
                                .withStyle(style -> style.withColor(0xFFAA00))
                );
            }
        }

        context.context().renderTooltip(
                Minecraft.getInstance().font,
                tooltip.stream().map(Component::getVisualOrderText).collect(Collectors.toList()),
                context.mouseX() - screen.getGuiX(),
                context.mouseY() - screen.getGuiY()
        );
        return true;
    }
}