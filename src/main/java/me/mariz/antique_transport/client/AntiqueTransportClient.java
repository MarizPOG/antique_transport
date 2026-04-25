package me.mariz.antique_transport.client;

import folk.sisby.antique_atlas.gui.AtlasRenderer;
import me.mariz.antique_transport.client.compat.ModCompat;
import me.mariz.antique_transport.client.compat.create.CreateClientCompat;
import me.mariz.antique_transport.client.compat.sable.SableClientCompat;
import net.fabricmc.api.ClientModInitializer;
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
    }
}