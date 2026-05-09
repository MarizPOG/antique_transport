package me.mariz.antique_transport.client.compat.simulated;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import me.mariz.antique_transport.client.AntiqueTransportConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_COLOR_BUFFER_BIT;

public final class ShipDiagramRenderer {

    private static final int MIN_FBO = 16;
    private static final int PPB = 32;

    private static final ResourceLocation RL_PIPELINE =
            ResourceLocation.fromNamespaceAndPath("antique_transport", "ship_diagram");
    private static final ResourceLocation RL_IN  =
            ResourceLocation.fromNamespaceAndPath("antique_transport", "ship_in");
    private static final ResourceLocation RL_OUT =
            ResourceLocation.fromNamespaceAndPath("antique_transport", "ship_out");

    private static final Quaternionf ANGLED = new Quaternionf()
            // can be used with any angle: .rotateZ, rotateY
            .rotateX((float) Math.toRadians(-90));
    public static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private ShipDiagramRenderer() {}

    public static final class Entry {
        AdvancedFbo baseFbo    = null;
        AdvancedFbo finalFbo   = null;
        int fboW = 0, fboH = 0;
        float frustumHalfX = 0, frustumHalfZ = 0;
        long lastRenderMs = 0L;
        boolean hasContent = false;
        long lastFrame = -1L;

        void ensureSize(int w, int h) {
            if (fboW == w && fboH == h) return;
            free();
            fboW = w; fboH = h;
            baseFbo = AdvancedFbo.withSize(w, h)
                    .addColorTextureBuffer()
                    .setDepthTextureBuffer()
                    .build(true);
            finalFbo = AdvancedFbo.withSize(w, h)
                    .addColorTextureBuffer()
                    .build(true);
            applyNearestFilter(finalFbo);
        }

        public void free() {
            if (baseFbo    != null) { baseFbo.free();    baseFbo    = null; }
            if (finalFbo   != null) { finalFbo.free();   finalFbo   = null; }
            hasContent = false;
        }
    }

    private static void applyNearestFilter(AdvancedFbo fbo) {
        int texId = fbo.getColorTextureAttachment(0).getId();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }
    public static float[] getShipFrustumSize(UUID id) {
        Entry e = ENTRIES.get(id);
        return e != null ? new float[]{ e.frustumHalfX * 2, e.frustumHalfZ * 2 } : null;
    }

    public static void renderDiagram(UUID id, ClientSubLevel subLevel, float partialTicks) {
        if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) return;

        Entry entry = ENTRIES.computeIfAbsent(id, k -> new Entry());

        // Guard — sublevel can only render once per tick
        long currentFrame = Minecraft.getInstance().gui.getGuiTicks();
        if (entry.lastFrame == currentFrame) return;
        entry.lastFrame = currentFrame;

        long nowMs = System.currentTimeMillis();
        if (nowMs - entry.lastRenderMs < 1000L / (long) AntiqueTransportConfig.get().simulated.diagramFps) return;
        entry.lastRenderMs = nowMs;
        // Size of sublevel
        var bounds = subLevel.getPlot().getBoundingBox();
        int shipX = bounds.maxX() - bounds.minX() + 1;
        int shipZ = bounds.maxZ() - bounds.minZ() + 1;
        entry.ensureSize(
                Math.max(MIN_FBO, shipX * PPB),
                Math.max(MIN_FBO, shipZ * PPB));

        // Camera top-down
        CameraData cam = computeCamera(subLevel, partialTicks, ANGLED, entry.fboW, entry.fboH);

        entry.frustumHalfX = cam.frustumHalfX();
        entry.frustumHalfZ = cam.frustumHalfZ();

        // Render 3D -> baseFbo
        entry.baseFbo.bind(true);
        entry.baseFbo.clear();
        SimpleSubLevelGroupRenderer.renderGroup(
                subLevel.getLevel(),
                Collections.singletonList(subLevel),
                entry.baseFbo,
                new Matrix4f(),
                cam.projection(),
                cam.worldCam(),
                cam.orientation(),
                partialTicks,
                false);

        PostProcessingManager pm = VeilRenderSystem.renderer().getPostProcessingManager();
        PostPipeline pipeline = pm.getPipeline(RL_PIPELINE);
        if (pipeline != null) {
            pipeline.getUniformSafe("TintColor").setVector(0.92f, 0.82f, 0.63f);
            pipeline.getUniformSafe("TintStrength").setFloat(0.8f);
            pipeline.getUniformSafe("Brightness").setFloat(1.4f);

            PostPipeline.Context ctx = pm.getPostPipelineContext();
            ctx.setFramebuffer(RL_IN,  entry.baseFbo);
            ctx.setFramebuffer(RL_OUT, entry.finalFbo);
            pm.runPipeline(pipeline, false);
        } else {
            entry.baseFbo.resolveToAdvancedFbo(entry.finalFbo, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        }
        entry.hasContent=true;
    }

    public static CameraData computeCamera(ClientSubLevel subLevel, float pt,
                                           Quaternionf localOrientation, int fboW, int fboH) {
        var bounds = subLevel.getPlot().getBoundingBox();

        float halfX = (bounds.maxX() - bounds.minX() + 1) * 0.5f;
        float halfY = (bounds.maxY() - bounds.minY() + 1) * 0.5f;
        float halfZ = (bounds.maxZ() - bounds.minZ() + 1) * 0.5f;
        float radius = (float) Math.sqrt(halfX*halfX + halfY*halfY + halfZ*halfZ) * 1.1f;
        radius = Math.max(radius, 2.0f);

        float aspect = (float) fboW / fboH;

        Matrix4f proj = new Matrix4f().ortho(
                -radius * aspect, radius * aspect,
                -radius, radius,
                0.1f, radius * 2.0f);

        // Center of sublevel
        Vector3d localCenter = new Vector3d(
                (bounds.minX() + bounds.maxX() + 1) / 2.0,
                (bounds.minY() + bounds.maxY() + 1) / 2.0,
                (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
        Vector3d worldCenter = new Vector3d();
        subLevel.renderPose(pt).transformPosition(worldCenter.set(localCenter));

        Vector3d worldCam = new Vector3d(worldCenter).add(0, radius, 0);
        Quaterniondc shipOrient = subLevel.renderPose(pt).orientation();
        double yaw = Math.atan2(
                2.0 * (shipOrient.w() * shipOrient.y() + shipOrient.x() * shipOrient.z()),
                1.0 - 2.0 * (shipOrient.y() * shipOrient.y() + shipOrient.z() * shipOrient.z())
        );
        Quaternionf yawOnly = new Quaternionf().rotateY((float) yaw);
        Quaternionf orient = new Quaternionf(yawOnly)
                .conjugate()
                .premul(localOrientation.conjugate(new Quaternionf()));

        return new CameraData(worldCam, orient, proj, radius * aspect, radius);
    }

    public record CameraData(Vector3d worldCam, Quaternionf orientation, Matrix4f projection, float frustumHalfX, float frustumHalfZ) {}

    // -------------------------------------------------------------------------

    @Nullable
    public static AdvancedFbo getFinalFbo(UUID id) {
        Entry e = ENTRIES.get(id);
        return e != null ? e.finalFbo : null;
    }

    public static boolean hasDiagramContent(UUID id) {
        Entry e = ENTRIES.get(id);
        return e != null && e.hasContent;
    }

    public static void freeShip(UUID id) {
        Entry e = ENTRIES.remove(id);
        if (e != null) e.free();
    }

    public static void freeAll() {
        ENTRIES.values().forEach(Entry::free);
        ENTRIES.clear();
    }
}