package me.mariz.antique_transport.server;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ShipDataPacket(List<ShipEntry> ships) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.tryBuild("antique_transport", "shipdata");

    public static final Type<ShipDataPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ShipDataPacket> CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeInt(pkt.ships.size());
                for (ShipEntry e : pkt.ships) {
                    buf.writeUUID(e.uuid());
                    buf.writeDouble(e.x());
                    buf.writeDouble(e.y());
                    buf.writeDouble(e.z());
                    buf.writeFloat(e.yaw());
                }
            },
            buf -> {
                int size = buf.readInt();
                List<ShipEntry> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(new ShipEntry(
                            buf.readUUID(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readFloat()
                    ));
                }
                return new ShipDataPacket(list);
            }
    );

    public record ShipEntry(UUID uuid, double x, double y, double z, float yaw) {
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}