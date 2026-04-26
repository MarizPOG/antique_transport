package me.mariz.antique_transport.client.compat.sable;

import folk.sisby.antique_atlas.gui.core.Component;
import me.mariz.antique_transport.client.AtlasOverlay;
import me.mariz.antique_transport.server.ShipNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Modal for naming ships and selecting icons, shown on right-click or /markship command.
 */
public class ShipNameModal extends Component {

    private static final int MODAL_W = 300;
    private static final int MODAL_H = 195;

    private static final int TILE_SIZE = 32;
    private static final int ICON_SIZE = 30;
    private static final int TILE_PAD = (TILE_SIZE - ICON_SIZE) / 2;
    private static final int TILE_GAP = 3;
    private static final int ICONS_PER_ROW = 7;
    private static final int TRASH_SIZE = 26;

    // Warm brown tile palette (AA4 style)
    private static final int COLOR_TILE_NORMAL = 0xFF6B5A3E;
    private static final int COLOR_TILE_HOVER = 0xFF8B7A5A;
    private static final int COLOR_TILE_SELECTED = 0xFFA08050;
    private static final int COLOR_BORDER = 0xFF3A2E1E;
    private static final int COLOR_BORDER_SEL = 0xFFD4A843;

    private final UUID shipId;

    private Button btnDone;
    private Button btnCancel;
    private EditBox textField;

    private int iconScrollOffset = 0;
    private ResourceLocation selectedIcon;

    public ShipNameModal(UUID shipId) {
        this.shipId = shipId;
        if (ShipCache.shipIcons.containsKey(shipId)) {
            this.selectedIcon = ShipCache.shipIcons.get(shipId);
        } else if (!ShipCache.SHIP_TEXTURES.isEmpty()) {
            int idx = Math.abs(shipId.hashCode()) % ShipCache.SHIP_TEXTURES.size();
            this.selectedIcon = ShipCache.SHIP_TEXTURES.get(idx);
        } else {
            this.selectedIcon = AtlasOverlay.SHIP_ICON;
        }
    }

    private static final ResourceLocation ICON_HIDE =
            ResourceLocation.tryBuild("antique_atlas", "textures/gui/icons/hide_markers.png");
    private static final ResourceLocation ICON_SHOW =
            ResourceLocation.tryBuild("antique_atlas", "textures/gui/icons/show_markers.png");

    private int modalX() {
        return width / 2 - MODAL_W / 2;
    }

    private int modalY() {
        return height / 2 - MODAL_H / 2;
    }

    private int tileX(int i) {
        return modalX() + 20 + i * (TILE_SIZE + TILE_GAP);
    }

    private int tilesY() {
        return modalY() + 72;
    }

    private int trashX() {
        return modalX() + MODAL_W - TRASH_SIZE - 10;
    }

    private int trashY() {
        return modalY() + 5;
    }

    @Override
    public void init() {
        super.init();
        int mx = modalX(), my = modalY();

        textField = new EditBox(
                font,
                mx + 20, my + 28,
                MODAL_W - 40, 18,
                net.minecraft.network.chat.Component.literal("Ship name")
        );
        // Pre-fill: nameplate name > manual name > empty
        textField.setValue(ShipCache.getInitialEditableShipName(shipId));
        textField.setFocused(true);
        textField.setBordered(true);

        btnDone = Button.builder(
                net.minecraft.network.chat.Component.translatable("gui.done"),
                btn -> commit()
        ).bounds(width / 2 - 82, my + MODAL_H - 26, 76, 18).build();

        btnCancel = Button.builder(
                net.minecraft.network.chat.Component.translatable("gui.cancel"),
                btn -> closeChild()
        ).bounds(width / 2 + 6, my + MODAL_H - 26, 76, 18).build();
    }

    private void commit() {
        ShipCache.shipNames.put(shipId, textField.getValue());
        ShipCache.shipIcons.put(shipId, selectedIcon);
        ShipCache.save();
        ShipNetworking.sendUpdate(shipId, textField.getValue(), selectedIcon.toString());
        closeChild();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int mx = modalX(), my = modalY();

        // Dark vignette overlay
        g.fill(0, 0, width, my, 0x88000000);           // top
        g.fill(0, my + MODAL_H, width, height, 0x88000000); // bottom
        g.fill(0, my, mx, my + MODAL_H, 0x88000000);   // left
        g.fill(mx + MODAL_W, my, width, my + MODAL_H, 0x88000000); // right

        // Native MC blur (1.21+)
        renderBlurredBackground(delta);

        // Title & labels
        g.drawCenteredString(font, "Name your ship", width / 2, my + 10, 0xFFFFFFFF);
        g.drawString(font, "Icon:", mx + 20, my + 58, 0xB0986A, false);

        textField.render(g, mouseX, mouseY, delta);

        // Icon tiles
        int tilesY = tilesY();
        int count = Math.min(ShipCache.SHIP_TEXTURES.size() - iconScrollOffset, ICONS_PER_ROW);

        for (int i = 0; i < count; i++) {
            ResourceLocation tex = ShipCache.SHIP_TEXTURES.get(i + iconScrollOffset);
            int tx = tileX(i);
            boolean selected = tex.equals(selectedIcon);
            boolean hovered = mouseX >= tx && mouseX < tx + TILE_SIZE
                    && mouseY >= tilesY && mouseY < tilesY + TILE_SIZE;

            // Outer border (1px)
            g.fill(tx - 1, tilesY - 1,
                    tx + TILE_SIZE + 1, tilesY + TILE_SIZE + 1,
                    selected ? COLOR_BORDER_SEL : COLOR_BORDER);

            // Tile background
            g.fill(tx, tilesY,
                    tx + TILE_SIZE, tilesY + TILE_SIZE,
                    selected ? COLOR_TILE_SELECTED : hovered ? COLOR_TILE_HOVER : COLOR_TILE_NORMAL);

            // Centered icon
            g.blit(tex,
                    tx + TILE_PAD, tilesY + TILE_PAD,
                    0, 0,
                    ICON_SIZE, ICON_SIZE,
                    ICON_SIZE, ICON_SIZE);
        }

        // Trash button
        int trashX = trashX();
        int trashY = trashY();
        boolean trashHovered = mouseX >= trashX && mouseX < trashX + TRASH_SIZE
                && mouseY >= trashY && mouseY < trashY + TRASH_SIZE;

        if (trashHovered) {
            g.fill(trashX, trashY,
                    trashX + TRASH_SIZE, trashY + TRASH_SIZE,
                    0x44FFFFFF);
        }

        boolean isHidden = ShipCache.hiddenShips.contains(shipId);
        ResourceLocation trashIcon = isHidden ? ICON_SHOW : ICON_HIDE;
        g.blit(trashIcon, trashX, trashY, 0, 0,
                TRASH_SIZE, TRASH_SIZE, TRASH_SIZE, TRASH_SIZE);

        // Scroll arrows
        if (iconScrollOffset > 0) {
            g.drawString(font, "◄", mx + 6, tilesY + TILE_SIZE / 2 - 4, 0xB0986A, false);
        }
        if (iconScrollOffset + ICONS_PER_ROW < ShipCache.SHIP_TEXTURES.size()) {
            g.drawString(font, "►", mx + MODAL_W - 14, tilesY + TILE_SIZE / 2 - 4, 0xB0986A, false);
        }

        // Buttons
        if (btnDone != null) btnDone.render(g, mouseX, mouseY, delta);
        if (btnCancel != null) btnCancel.render(g, mouseX, mouseY, delta);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (btnDone != null && btnDone.mouseClicked(mouseX, mouseY, button)) return true;
        if (btnCancel != null && btnCancel.mouseClicked(mouseX, mouseY, button)) return true;

        // Trash button
        int trashX = trashX();
        int trashY = trashY();
        if (mouseX >= trashX && mouseX < trashX + TRASH_SIZE
                && mouseY >= trashY && mouseY < trashY + TRASH_SIZE) {
            toggleVisibility();
            return true;
        }

        // Icon tiles
        int tilesY = tilesY();
        int count = Math.min(ShipCache.SHIP_TEXTURES.size() - iconScrollOffset, ICONS_PER_ROW);
        for (int i = 0; i < count; i++) {
            int tx = tileX(i);
            if (mouseX >= tx && mouseX < tx + TILE_SIZE
                    && mouseY >= tilesY && mouseY < tilesY + TILE_SIZE) {
                selectedIcon = ShipCache.SHIP_TEXTURES.get(i + iconScrollOffset);
                return true;
            }
        }

        if (textField != null) {
            return textField.mouseClicked(mouseX, mouseY, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int max = Math.max(0, ShipCache.SHIP_TEXTURES.size() - ICONS_PER_ROW);
        iconScrollOffset = (int) Math.max(0, Math.min(max, iconScrollOffset - dy));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            closeChild();
            return true;
        }
        if (key == 257) { // Enter
            commit();
            return true;
        }
        if (textField != null && textField.isFocused()) {
            return textField.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (textField != null) {
            return textField.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    private void toggleVisibility() {
        if (ShipCache.hiddenShips.contains(shipId)) {
            ShipCache.hiddenShips.remove(shipId);
        } else {
            ShipCache.hiddenShips.add(shipId);
        }
        ShipCache.save();
    }
}