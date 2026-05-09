package me.mariz.antique_transport.client;

import folk.sisby.kaleido.api.WrappedConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.IntegerRange;
import net.fabricmc.loader.api.FabricLoader;

@SuppressWarnings("CanBeFinal")
public class AntiqueTransportConfig extends WrappedConfig {
    @Comment("Settings for Create mod integration (trains, tracks, stations)")
    public CreateSection create = new CreateSection();

    @Comment("Settings for Sable mod integration (sub-levels)")
    public SableSection sable = new SableSection();

    @Comment("Settings for Simulated mod integration (sub-level previews, nameplates)")
    public SimulatedSection simulated = new SimulatedSection();

    public static final class CreateSection implements Section {
        @Comment("Show train tracks on the atlas map")
        public boolean showTracks = true;

        @Comment("Show trains on the atlas map")
        public boolean showTrains = true;

        @Comment("Show stations on the atlas map")
        public boolean showStations = true;
    }

    public static final class SableSection implements Section {
        @Comment("Show sublevels on the atlas map")
        public boolean showShips = true;

        @Comment("Show all sublevels automatically, without requiring manual marking via right-click or /markship")
        public boolean autoShowAllShips = false;

        @Comment("Show the sublevel's current altitude in the hover tooltip")
        public boolean showShipHeight = true;

        @Comment("Show how long ago the sublevel was last seen in the hover tooltip (only for sub-levels outside render distance)")
        public boolean showLastSeen = true;
    }

    public static final class SimulatedSection implements Section {
        @Comment("Use the sublevel's nameplate name as the default display name if no manual name has been set")
        public boolean preferNameplateNames = true;
        @Comment("Render a live top-down 3D preview of each sublevel on the atlas map")
        public boolean enableShipPreviews = true;
        @Comment("Keep showing the last rendered diagram for sublevels that have left render distance")
        public boolean keepDiagramOutsideRenderDistance = true;
        @Comment("How many times per second the sublevel diagram is re-rendered")
        @IntegerRange(min= 1L,max= 60L)
        public int diagramFps = 20;
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