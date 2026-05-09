package me.mariz.antique_transport.client.compat.sable;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.antique_atlas.gui.core.Component;
import me.mariz.antique_transport.client.AtlasOverlay;
import me.mariz.antique_transport.client.compat.ModCompat;
import me.mariz.antique_transport.server.ShipDataPacket;
import me.mariz.antique_transport.server.ShipNetworking;
import net.minecraft.client.Minecraft;
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
    private static final int MODAL_H = 235;

    private static final int TILE_SIZE = 32;
    private static final int ICON_SIZE = 30;
    private static final int TILE_PAD = (TILE_SIZE - ICON_SIZE) / 2;
    private static final int TILE_GAP = 3;
    private static final int ICONS_PER_ROW = 7;
    private static final int HIDE_SIZE = 26;

    // Warm brown tile palette (AA4 style)
    private static final int COLOR_TILE_NORMAL = 0xFF6B5A3E;
    private static final int COLOR_TILE_HOVER = 0xFF8B7A5A;
    private static final int COLOR_TILE_SELECTED = 0xFFA08050;
    private static final int COLOR_BORDER = 0xFF3A2E1E;
    private static final int COLOR_BORDER_SEL = 0xFFD4A843;

    private final UUID shipId;

    private Button btnDone;
    private Button btnCancel;
    private Button btnTeleport;
    private EditBox textField;

    private int iconScrollOffset = 0;
    private ResourceLocation selectedIcon;

    private boolean diagramEnabled;
    private int diagramZoomIndex; // 0 = 8 blocks, 6 = 32 chunks

    private static final float[] ZOOM_LEVELS = {
            1.0f,       // 8 blocks (tileChunks=1)
            0.5f,       // 1 chunk (tileChunks=2)
            0.25f,      // 2 chunks (tileChunks=4)
            0.125f,     // 4 chunks (tileChunks=8)
            0.0625f,    // 8 chunks (tileChunks=16)
            0.03125f,   // 16 chunks (tileChunks=32)
            0.015625f   // 32 chunks (tileChunks=64)
    };
    private static final String[] ZOOM_LABELS = {
            "8 blocks", "1 chunk", "2 chunks", "4 chunks",
            "8 chunks", "16 chunks", "32 chunks"
    };

    
    private int diagramRowY() { return tilesY() + TILE_SIZE + 12; }

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
        this.diagramEnabled = ShipCache.isDiagramEnabled(shipId);
        this.diagramZoomIndex = ppbToIndex(ShipCache.getDiagramMinPpb(shipId));
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

    private int hideX() {
        return modalX() + MODAL_W - HIDE_SIZE - 10;
    }

    private int hideY() {
        return modalY() + 5;
    }

    public static boolean isOpenOn(folk.sisby.antique_atlas.gui.AtlasScreen screen) {
        return screen.getChildren().stream()
                .anyMatch(child -> child instanceof ShipNameModal);
    }
    public static boolean shouldBlockAtlasKey(AtlasScreen screen) {
        return screen.getChildren().stream()
                .filter(child -> child instanceof ShipNameModal)
                .map(child -> (ShipNameModal) child)
                .anyMatch(modal -> modal.textField != null && modal.textField.isFocused());
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
        // Teleport – only for ops
        var mc = Minecraft.getInstance();
        boolean isOp = mc.player != null && mc.player.hasPermissions(2);
        boolean hasPos = ShipCache.getRenderPosition(shipId) != null;

        if (isOp && hasPos) {
            btnTeleport = Button.builder(
                            net.minecraft.network.chat.Component.literal("Teleport"),
                            btn -> {
                                ShipNetworking.sendTeleportToShip(shipId);
                                closeChild();
                            })
                    .bounds(modalX() + 10, modalY() + 10, 76, 18)
                    .build();
        }
    }

    private void commit() {
        String name = textField.getValue().trim();
        ShipCache.shipNames.put(shipId, name);
        ShipCache.shipIcons.put(shipId, selectedIcon);
        ShipDataPacket.ShipEntry currentPos = ShipCache.positionCache.get(shipId);
        if (currentPos != null) {
            ShipCache.lastKnownPositions.put(shipId, currentPos);
        }
        ShipCache.save();
        ShipNetworking.sendUpdate(shipId, textField.getValue(), selectedIcon.toString());
        ShipCache.shipDiagramEnabled.put(shipId, diagramEnabled);
        ShipCache.shipDiagramMinPpb.put(shipId, ZOOM_LEVELS[diagramZoomIndex]);
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
        g.drawString(font, "Icon:", mx + 20, my + 58, 0xFFFFFFFF, false);

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

        // Hide button
        int hideX = hideX();
        int hideY = hideY();
        boolean hideHovered = mouseX >= hideX && mouseX < hideX + HIDE_SIZE
                && mouseY >= hideY && mouseY < hideY + HIDE_SIZE;

        if (hideHovered) {
            g.fill(hideX, hideY,
                    hideX + HIDE_SIZE, hideY + HIDE_SIZE,
                    0x44FFFFFF);
        }

        boolean isHidden = ShipCache.hiddenShips.contains(shipId);
        ResourceLocation trashIcon = isHidden ? ICON_SHOW : ICON_HIDE;
        g.blit(trashIcon, hideX, hideY, 0, 0,
                HIDE_SIZE, HIDE_SIZE, HIDE_SIZE, HIDE_SIZE);

        // Scroll arrows
        if (iconScrollOffset > 0) {
            g.drawString(font, "◄", mx + 6, tilesY + TILE_SIZE / 2 - 4, 0xB0986A, false);
        }
        if (iconScrollOffset + ICONS_PER_ROW < ShipCache.SHIP_TEXTURES.size()) {
            g.drawString(font, "►", mx + MODAL_W - 14, tilesY + TILE_SIZE / 2 - 4, 0xB0986A, false);
        }
        if(ModCompat.SIMULATED)
        {
            // Checkbox
            int drY  = diagramRowY();
            int drX  = modalX() + 20;
            int drMx = modalX() + MODAL_W - 20;
            boolean chkHov = mouseX >= drX && mouseX < drX + 12
                    && mouseY >= drY && mouseY < drY + 12;
            g.fill(drX - 1, drY - 1, drX + 13, drY + 13, COLOR_BORDER);
            g.fill(drX, drY, drX + 12, drY + 12,
                    chkHov ? COLOR_TILE_HOVER : COLOR_TILE_NORMAL);
            if (diagramEnabled) {
                g.drawCenteredString(font, "✔", drX + 6, drY + 2, 0xFFFFFFFF);
            }
            g.drawString(font, "Ship preview", drX + 16, drY + 2, 0xFFFFFFFF, false);

            // Slider
            if (diagramEnabled) {
                int sliderX = drX;
                int sliderY = drY + 18;

                // Left button
                boolean leftHov = mouseX >= sliderX && mouseX < sliderX + 12
                        && mouseY >= sliderY && mouseY < sliderY + 12;
                g.fill(sliderX - 1, sliderY - 1, sliderX + 13, sliderY + 13, COLOR_BORDER);
                g.fill(sliderX, sliderY, sliderX + 12, sliderY + 12,
                        leftHov ? COLOR_TILE_HOVER : COLOR_TILE_NORMAL);
                g.drawCenteredString(font, "◄", sliderX + 6, sliderY + 2, 0xFFFFFF);

                // Value
                String ppbLabel = "Min zoom: " + ZOOM_LABELS[diagramZoomIndex];
                g.drawCenteredString(font, ppbLabel, modalX() + MODAL_W / 2, sliderY + 2, 0xFFFFFF);

                // Right button
                int rightX = drMx - 12;
                boolean rightHov = mouseX >= rightX && mouseX < rightX + 12
                        && mouseY >= sliderY && mouseY < sliderY + 12;
                g.fill(rightX - 1, sliderY - 1, rightX + 13, sliderY + 13, COLOR_BORDER);
                g.fill(rightX, sliderY, rightX + 12, sliderY + 12,
                        rightHov ? COLOR_TILE_HOVER : COLOR_TILE_NORMAL);
                g.drawCenteredString(font, "►", rightX + 6, sliderY + 2, 0xFFFFFF);
            }
        }


        // Buttons
        if (btnDone != null) btnDone.render(g, mouseX, mouseY, delta);
        if (btnCancel != null) btnCancel.render(g, mouseX, mouseY, delta);
        if (btnTeleport != null) btnTeleport.render(g, mouseX, mouseY, delta);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (btnDone != null && btnDone.mouseClicked(mouseX, mouseY, button)) return true;
        if (btnCancel != null && btnCancel.mouseClicked(mouseX, mouseY, button)) return true;
        if (btnTeleport != null) btnTeleport.mouseClicked(mouseX, mouseY, button);

        // Hide button
        int hideX = hideX();
        int hideY = hideY();
        if (mouseX >= hideX && mouseX < hideX + HIDE_SIZE
                && mouseY >= hideY && mouseY < hideY + HIDE_SIZE) {
            toggleVisibility();
            return true;
        }
        if(ModCompat.SIMULATED)
        {
            // Checkbox
            int drY = diagramRowY();
            int drX = modalX() + 20;
            if (mouseX >= drX && mouseX < drX + 12
                    && mouseY >= drY && mouseY < drY + 12) {
                diagramEnabled = !diagramEnabled;
                return true;
            }

            // Slider
            if (diagramEnabled) {
                int sliderY = drY + 18;
                int drMx    = modalX() + MODAL_W - 20;

                // Left
                if (mouseX >= drX && mouseX < drX + 12
                        && mouseY >= sliderY && mouseY < sliderY + 12) {
                    diagramZoomIndex = Math.max(0, diagramZoomIndex - 1);
                    return true;
                }
                // Right
                int rightX = drMx - 12;
                if (mouseX >= rightX && mouseX < rightX + 12
                        && mouseY >= sliderY && mouseY < sliderY + 12) {
                    diagramZoomIndex = Math.min(ZOOM_LEVELS.length - 1, diagramZoomIndex + 1);
                    return true;
                }
            }
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
    private static int ppbToIndex(float ppb) {
        for (int i = 0; i < ZOOM_LEVELS.length; i++) {
            if (ppb >= ZOOM_LEVELS[i]) return i;
        }
        return ZOOM_LEVELS.length - 1;
    }
    @Override
    public void closeChild() {
        super.closeChild();
    }
}