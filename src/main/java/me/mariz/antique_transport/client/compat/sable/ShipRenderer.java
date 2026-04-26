package me.mariz.antique_transport.client.compat.sable;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import folk.sisby.antique_atlas.gui.AtlasOverlay;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import me.mariz.antique_transport.client.AntiqueTransportConfig;
import me.mariz.antique_transport.server.ShipDataPacket;
import me.mariz.antique_transport.server.ShipUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

public final class ShipRenderer {

    private ShipRenderer() {
    }

    private record ShipRenderData(UUID uuid, double x, double y, double z, float yaw, String subLevelName) {
    }

    public static void renderShips(AtlasOverlay.AtlasScreenRenderContext context) {
        AtlasScreen screen = context.screen();
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Map<UUID, ShipRenderData> toRender = new HashMap<>();

        // Step 1: Seed from cached/server positions
        for (ShipDataPacket.ShipEntry entry : ShipCache.positionCache.values()) {
            float worldYaw = ShipCache.lastMovingYaw.getOrDefault(entry.uuid(), entry.yaw());
            toRender.put(entry.uuid(), new ShipRenderData(
                    entry.uuid(),
                    entry.x(), entry.y(), entry.z(),
                    worldYaw,
                    null
            ));
        }

        // Step 2: Overwrite with live Sable data when available
        BoundingBox3d hugeBounds = new BoundingBox3d(
                -30_000_000, -64, -30_000_000,
                30_000_000, 320, 30_000_000
        );

        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, hugeBounds)) {
            Pose3dc pose = ship.logicalPose();
            Pose3dc lastPose = ship.lastPose();
            var pos = pose.position();
            UUID id = ship.getUniqueId();

            // Pure translational velocity — no rotational contamination.
            double dvx = pose.position().x() - lastPose.position().x();
            double dvz = pose.position().z() - lastPose.position().z();
            double lenSq = dvx * dvx + dvz * dvz;

            float yaw;
            if (lenSq > 1e-8) {
                yaw = ShipUtils.computeCompassYaw(dvx, dvz);
                ShipCache.lastMovingYaw.put(id, yaw);
            } else yaw = ShipCache.lastMovingYaw.getOrDefault(id, 0f);
            String subLevelName = null;
            try {
                subLevelName = ship.getName();
            } catch (Exception ignored) {
            }
            if (subLevelName != null && !subLevelName.isBlank()) {
                ShipCache.subLevelNames.put(id, subLevelName);
            }
            toRender.put(id, new ShipRenderData(id, pos.x(), pos.y(), pos.z(), yaw, subLevelName));

            if (!ShipCache.isServerModPresent()) {
                ShipCache.updateFromLocal(id, pos.x(), pos.y(), pos.z(), yaw);
            }
        }

        // Step 3: Render
        for (ShipRenderData data : toRender.values()) {
            if (!ShipCache.shouldRender(data.uuid())) {
                continue;
            }
            renderShipMarker(context, data, screen);
        }
    }

    private static void renderShipMarker(
            AtlasOverlay.AtlasScreenRenderContext context,
            ShipRenderData data,
            AtlasScreen screen
    ) {
        int screenX = (int) screen.worldXToScreenX(data.x()) - screen.getGuiX();
        int screenY = (int) screen.worldZToScreenY(data.z()) - screen.getGuiY();
        ShipCache.shipScreenPositions.put(data.uuid(), new int[]{screenX, screenY});

        GuiGraphics graphics = context.context();
        ResourceLocation icon = ShipCache.getDisplayIcon(data.uuid());

        float renderYaw = data.yaw()+90f;
        boolean flipX=ShipUtils.needsFlip(data.yaw());
        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, 0.1f);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(renderYaw));
        if (flipX) {
            RenderSystem.disableCull();
            graphics.pose().scale(1f, -1f, 1f);
            graphics.blit(icon, -16, -16, 0, 0, 32, 32, 32, 32);
            graphics.pose().scale(1f, -1f, 1f); RenderSystem.enableCull();
        } else {
            graphics.blit(icon, -16, -16, 0, 0, 32, 32, 32, 32); }
        graphics.pose().popPose();
        renderTooltipIfHovered(context, data, screenX, screenY, screen);
    }

    private static void renderTooltipIfHovered(
            AtlasOverlay.AtlasScreenRenderContext context,
            ShipRenderData data,
            int screenX, int screenY,
            AtlasScreen screen
    ) {
        int mx = context.mouseX() - screen.getGuiX();
        int my = context.mouseY() - screen.getGuiY();

        if (Math.abs(mx - screenX) > 16 || Math.abs(my - screenY) > 16) {
            return;
        }

        // Do not render tooltip while rename modal is open.
        boolean modalOpen = screen.getChildren().stream()
                .anyMatch(child -> child instanceof ShipNameModal);
        if (modalOpen) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(ShipCache.getDisplayName(data.uuid())));

        if (AntiqueTransportConfig.get().aeronautics.showShipHeight) {
            tooltip.add(
                    Component.literal("Altitude: " + (int) data.y() + " m")
                            .withStyle(style -> style.withColor(0xAAAAAA))
            );
        }

        // Show "last seen" only for cached (non-live) data.
        if (!ShipCache.isServerModPresent() && AntiqueTransportConfig.get().aeronautics.showLastSeen) {
            Long ts = ShipCache.cacheTimestamps.get(data.uuid());
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
    }
}