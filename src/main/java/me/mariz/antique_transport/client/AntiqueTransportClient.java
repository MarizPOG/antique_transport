package me.mariz.antique_transport.client;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import folk.sisby.antique_atlas.gui.AtlasRenderer;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import me.mariz.antique_transport.client.compat.ModCompat;
import me.mariz.antique_transport.client.compat.create.CreateClientCompat;
import me.mariz.antique_transport.client.compat.sable.SableClientCompat;
import me.mariz.antique_transport.client.compat.sable.ShipCache;
import me.mariz.antique_transport.client.compat.sable.ShipUtils;
import me.mariz.antique_transport.client.compat.simulated.ShipDiagramRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntiqueTransportClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("antiquetrains");

    public static final ResourceLocation OVERLAY_ID =
            ResourceLocation.tryBuild("antiquetrains", "tracks");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Antique Trains client");

        AntiqueTransportConfig.register();
        AtlasRenderer.registerOverlay(OVERLAY_ID, new AtlasOverlay());

        if (ModCompat.CREATE) {
            CreateClientCompat.init();
        }

        if (ModCompat.SABLE) {
            SableClientCompat.init();
        }
        if(ModCompat.SIMULATED) {
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                    client.execute(() -> {
                        ShipDiagramRenderer.freeAll();
                        ShipCache.onDisconnect();
                    }));
            HudRenderCallback.EVENT.register((graphics, tickDeltaManager) -> {
                if (!(Minecraft.getInstance().screen instanceof AtlasScreen)) return;
                var level = Minecraft.getInstance().level;
                if (level == null) return;
                float pt = tickDeltaManager.getGameTimeDeltaPartialTick(true);
                for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, ShipUtils.worldBounds(level))) {
                    if (ship instanceof ClientSubLevel sub) {
                        ShipDiagramRenderer.renderDiagram(sub.getUniqueId(), sub, pt);
                    }
                }
            });
        }
    }
}