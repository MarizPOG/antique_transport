package me.mariz.antique_transport.client.compat.create;

import com.simibubi.create.CreateClient;
import com.simibubi.create.compat.trainmap.TrainMapSync;
import com.simibubi.create.compat.trainmap.TrainMapSyncClient;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
// Similar to the Create class
public final class TrainRenderer {
    private static final int SPRITE_Y_OFFSET = -3;
    private static final int[] SLICE_X_SHIFT = {0, 1, 2, 2, 3, -2, -2, -1};
    private static final int[] SLICE_Y_SHIFT = {3, 2, 2, 1, 0, 1, 2, 2};

    private static final float MIN_TRAIN_PIXELS = 14f;
    private static final float SPRITE_BASE_UNITS = 16f;
    private static final float MAX_TRAIN_PIXELS = 28f;

    private TrainRenderer() {
    }

    public record RenderResult(Train hoveredTrain, List<Component> tooltip) {
        public boolean hasTooltip() {
            return tooltip != null && !tooltip.isEmpty();
        }
    }

    public static RenderResult render(
            GuiGraphics graphics,
            int worldMouseX,
            int worldMouseZ,
            float partialTick,
            Rect2i bounds,
            double mapScale,
            ResourceKey<Level> dimension
    ) {
        Object hoveredElement = null;

        double time = AnimationTickHolder.getTicks();
        time += AnimationTickHolder.getPartialTicks();
        time -= TrainMapSyncClient.lastPacket;
        time /= TrainMapSync.lightPacketInterval;
        time = Mth.clamp(time, 0, 1);

        float currentSpritePixels = (float) (SPRITE_BASE_UNITS * mapScale);
        float targetPixels = Math.max(MIN_TRAIN_PIXELS, Math.min(MAX_TRAIN_PIXELS, currentSpritePixels));

        for (Train train : CreateClient.RAILWAYS.trains.values()) {
            TrainMapSync.TrainMapSyncEntry entry = TrainMapSyncClient.currentData.get(train.id);
            if (entry == null) {
                continue;
            }

            List<Carriage> carriages = train.carriages;
            boolean otherDimension = true;
            double avgY = 0;
            Vec3 frontPos = Vec3.ZERO;

            for (int i = 0; i < carriages.size(); i++) {
                for (boolean firstBogey : Iterate.trueAndFalse) {
                    avgY += entry.getPosition(i, firstBogey, time).y;
                }
            }
            avgY /= (carriages.size() * 2.0);

            for (int i = 0; i < carriages.size(); i++) {
                Carriage carriage = carriages.get(i);

                Vec3 pos1 = entry.getPosition(i, true, time);
                Vec3 pos2 = entry.getPosition(i, false, time);
                ResourceKey<Level> dim = entry.dimensions.get(i);

                if (dim == null || dim != dimension) {
                    continue;
                }

                if (!bounds.contains(Mth.floor(pos1.x), Mth.floor(pos1.z))
                        && !bounds.contains(Mth.floor(pos2.x), Mth.floor(pos2.z))) {
                    continue;
                }

                otherDimension = false;

                if (!entry.backwards && i == 0) {
                    frontPos = pos1;
                }
                if (entry.backwards && i == carriages.size() - 1) {
                    frontPos = pos2;
                }

                Vec3 diff = pos2.subtract(pos1);
                int size = carriage.bogeySpacing + 1;
                Vec3 center = pos1.add(pos2).scale(0.5);

                double pX = center.x;
                double pY = center.z;

                int rotation = Mth.positiveModulo(
                        Mth.floor(0.5 + Math.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG / 22.5),
                        8
                );

                if (entry.state == TrainMapSync.TrainState.DERAILED) {
                    rotation = Mth.positiveModulo(
                            (int) AnimationTickHolder.getTicks() / 8 + i / 3 + ((i % 2 == 0) ? 1 : -1),
                            8
                    );
                }

                int slices = 2;
                if (rotation == 0 || rotation == 4) {
                    slices = Mth.floor((size - 2) / 3.0 + 0.5);
                } else if (rotation == 2 || rotation == 6) {
                    slices = Mth.floor((size - 5 - 2 * Mth.SQRT_OF_TWO) / (2 * Mth.SQRT_OF_TWO) + 0.5);
                } else {
                    slices = Mth.floor((size - 5 - Math.sqrt(5)) / Math.sqrt(5) + 0.5);
                }
                slices = Math.max(2, slices);

                AllGuiTextures sprite = AllGuiTextures.TRAINMAP_SPRITES;
                sprite.bind();

                float pivotX = 7.5f + (slices - 3) * SLICE_X_SHIFT[rotation] / 2.0f;
                float pivotY = 6.5f + (slices - 3) * SLICE_Y_SHIFT[rotation] / 2.0f;

                float extraScale = targetPixels / currentSpritePixels;

                graphics.pose().pushPose();
                graphics.pose().translate(pX, pY, 0);
                graphics.pose().scale(extraScale, extraScale, 1f);
                graphics.pose().translate(-pX, -pY, 0);
                graphics.pose().translate(
                        pX - pivotX,
                        pY - pivotY,
                        10 + avgY / 512.0 + center.z / 8192.0 / 1024.0
                );

                int colorRow = train.mapColorIndex / 4;
                int colorCol = train.mapColorIndex % 4;

                for (int slice = 0; slice < slices; slice++) {
                    int row = slice == 0 ? 1 : slice == slices - 1 ? 2 : 3;
                    int sliceShifts = slice == 0 ? 0 : slice == slices - 1 ? slice - 2 : slice - 1;

                    int positionX = sliceShifts * SLICE_X_SHIFT[rotation];
                    int positionY = sliceShifts * SLICE_Y_SHIFT[rotation] + SPRITE_Y_OFFSET;

                    int sheetX = rotation * 16 + colorCol * 128;
                    int sheetY = row * 16 + colorRow * 64;

                    graphics.blit(
                            sprite.location,
                            positionX,
                            positionY,
                            sheetX,
                            sheetY,
                            16,
                            16,
                            sprite.getWidth(),
                            sprite.getHeight()
                    );
                }

                graphics.pose().popPose();

                int sizeX = 8 + (slices - 3) * SLICE_X_SHIFT[rotation];
                int sizeY = 12 + (slices - 3) * SLICE_Y_SHIFT[rotation];

                double pXm = pX - sizeX / 2.0;
                double pYm = pY - sizeY / 2.0 + SPRITE_Y_OFFSET;

                if (hoveredElement == null
                        && worldMouseX < pXm + 1 + sizeX
                        && worldMouseX > pXm - 1
                        && worldMouseZ < pYm + 1 + sizeY
                        && worldMouseZ > pYm - 1) {
                    hoveredElement = train;
                }

                if (entry.signalState != TrainMapSync.SignalState.NOT_WAITING) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(
                            frontPos.x - 0.5,
                            frontPos.z - 0.5,
                            20 / 1024.0 + frontPos.z / 8192.0 / 1024.0
                    );
                    AllGuiTextures.TRAINMAP_SIGNAL.render(graphics, 0, -3);
                    graphics.pose().popPose();
                }
            }
        }

        graphics.bufferSource().endBatch();

        if (!(hoveredElement instanceof Train hoveredTrain)) {
            return null;
        }

        return new RenderResult(hoveredTrain, buildTooltip(hoveredTrain));
    }

    private static List<Component> buildTooltip(Train train) {
        TrainMapSync.TrainMapSyncEntry entry = TrainMapSyncClient.currentData.get(train.id);
        if (entry == null) {
            return List.of(train.name);
        }

        return switch (entry.state) {
            case DERAILED -> List.of(train.name, Component.translatable("create.train_map.derailed"));
            case CONDUCTOR_MISSING -> List.of(train.name, Component.translatable("create.train_map.conductor_missing"));
            case NAVIGATION_FAILED -> List.of(train.name, Component.translatable("create.train_map.navigation_failed"));
            default -> List.of(train.name);
        };
    }
}