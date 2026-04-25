package me.mariz.antique_transport.client;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.antique_atlas.gui.BookmarkButton;
import me.mariz.antique_transport.client.compat.create.CreateClientCompat;
import me.mariz.antique_transport.client.compat.ModCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public class TrainBookmarkButton extends BookmarkButton {
    private static final ResourceLocation ICON_SHOW_TRACKS =
            ResourceLocation.tryBuild("antique_transport", "textures/gui/station_hide.png");
    private static final ResourceLocation ICON_HIDE_TRACKS =
            ResourceLocation.tryBuild("antique_transport", "textures/atlas/marker/station.png");

    private static final MutableComponent HIDE_TRACKS_TEXT =
            Component.translatable("gui.antiquetrains.hideTracks");
    private static final MutableComponent SHOW_TRACKS_TEXT =
            Component.translatable("gui.antiquetrains.showTracks");

    private static boolean temporarilyHidden = false;

    public TrainBookmarkButton() {
        super(SHOW_TRACKS_TEXT, ICON_SHOW_TRACKS, 4408131, null, 16, 16, false, false);
    }

    public static boolean isTemporarilyHidden() {
        return temporarilyHidden;
    }

    public static void create(AtlasScreen screen) {
        if (!ModCompat.CREATE) {
            return;
        }
        TrainBookmarkButton button = new TrainBookmarkButton();
        screen.addChild(button);
        button.offsetGuiCoords(screen.bookWidth - 10, 90);
    }

    @Override
    public boolean isSelected() {
        return AntiqueTransportConfig.get().create.showTracks && !temporarilyHidden;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
        if (temporarilyHidden) {
            iconTexture = ICON_SHOW_TRACKS;
            title = SHOW_TRACKS_TEXT;
        } else {
            iconTexture = ICON_HIDE_TRACKS;
            title = HIDE_TRACKS_TEXT;
        }
        super.render(context, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick() {
        super.onClick();
        temporarilyHidden = !temporarilyHidden;

        if (ModCompat.CREATE) {
            AntiqueTransportConfig config = AntiqueTransportConfig.get();
            boolean showStations = !temporarilyHidden && config.create.showStations;
            CreateClientCompat.syncStations(showStations);
        }
    }
}