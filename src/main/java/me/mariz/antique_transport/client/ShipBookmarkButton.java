package me.mariz.antique_transport.client;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.antique_atlas.gui.BookmarkButton;
import me.mariz.antique_transport.client.compat.ModCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public class ShipBookmarkButton extends BookmarkButton {
    private static final ResourceLocation ICON_SHOW_SHIPS =
            ResourceLocation.tryBuild("antique_transport", "textures/gui/show_ships.png");
    private static final ResourceLocation ICON_HIDE_SHIPS =
            ResourceLocation.tryBuild("antique_transport", "textures/gui/ship_textures/airship_marker.png");

    private static final MutableComponent HIDE_SHIPS_TEXT =
            Component.translatable("gui.antiquetrains.hideShips");
    private static final MutableComponent SHOW_SHIPS_TEXT =
            Component.translatable("gui.antiquetrains.showShips");

    public ShipBookmarkButton() {
        super(SHOW_SHIPS_TEXT, ICON_SHOW_SHIPS, 4408131, null, 16, 16, false, false);
    }

    public static void create(AtlasScreen screen) {
        if (!ModCompat.SABLE) {
            return;
        }
        ShipBookmarkButton button = new ShipBookmarkButton();
        screen.addChild(button);
        button.offsetGuiCoords(screen.bookWidth - 10, 110);
    }

    @Override
    public boolean isSelected() {
        return AntiqueTransportConfig.get().sable.showShips;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
        if (AntiqueTransportConfig.get().sable.showShips) {
            iconTexture = ICON_HIDE_SHIPS;
            title = HIDE_SHIPS_TEXT;
        } else {
            iconTexture = ICON_SHOW_SHIPS;
            title = SHOW_SHIPS_TEXT;
        }
        super.render(context, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick() {
        super.onClick();
        AntiqueTransportConfig.get().sable.showShips =
                !AntiqueTransportConfig.get().sable.showShips;
    }
}