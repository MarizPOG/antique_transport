package me.mariz.antique_transport.client.compat.create;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import me.mariz.antique_transport.client.AntiqueTransportConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class CreateClientCompat {
    private CreateClientCompat() {
    }

    public static void init() {
        registerTickHandlers();
    }

    public static void syncStations(boolean show) {
        StationLandmarkSync.sync(show);
    }

    private static void registerTickHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                return;
            }

            boolean atlasOpen = client.screen instanceof AtlasScreen;
            if (!atlasOpen) {
                com.simibubi.create.compat.trainmap.TrainMapSyncClient.stopRequesting();
                return;
            }

            if (AntiqueTransportConfig.get().create.showTracks) {
                com.simibubi.create.compat.trainmap.TrainMapSyncClient.requestData();
            }
        });
    }
}