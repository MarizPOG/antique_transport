package me.mariz.antique_transport.client;

import folk.sisby.kaleido.api.WrappedConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import net.fabricmc.loader.api.FabricLoader;

@SuppressWarnings("CanBeFinal")
public class AntiqueTransportConfig extends WrappedConfig {
    @Comment("Create mod integration settings")
    public CreateSection create = new CreateSection();

    @Comment("Aeronautics/Sable integration settings")
    public AeronauticsSection aeronautics = new AeronauticsSection();

    public static final class CreateSection implements Section {
        @Comment("Show train tracks on the atlas map")
        public boolean showTracks = true;

        @Comment("Show trains on the atlas map")
        public boolean showTrains = true;

        @Comment("Show stations on the atlas map")
        public boolean showStations = true;
    }

    public static final class AeronauticsSection implements Section {
        @Comment("Show ships on the atlas map")
        public boolean showShips = true;

        @Comment("Automatically show all ships without manual marking")
        public boolean autoShowAllShips = false;

        @Comment("Show altitude in the ship tooltip")
        public boolean showShipHeight = true;

        @Comment("Show last seen time in the ship tooltip")
        public boolean showLastSeen = true;
    }

    public static final AntiqueTransportConfig INSTANCE =
            WrappedConfig.createToml(
                    FabricLoader.getInstance().getConfigDir(),
                    "",
                    "antique_transport",
                    AntiqueTransportConfig.class
            );

    public static AntiqueTransportConfig get() {
        return INSTANCE;
    }

    public static void register() {
        // Kaleido loads the config inside createToml().
        // This method exists only to keep the call site explicit.
    }
}