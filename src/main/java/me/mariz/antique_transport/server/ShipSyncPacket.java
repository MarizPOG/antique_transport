package me.mariz.antique_transport.server;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ShipSyncPacket(
        UUID shipId,
        @Nullable String name,
        @Nullable String iconId,
        boolean removed
) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.tryBuild("antique_transport", "shipsync");

    public static final Type<ShipSyncPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ShipSyncPacket> CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.shipId());
                buf.writeBoolean(pkt.removed());

                if (!pkt.removed()) {
                    buf.writeBoolean(pkt.name() != null);
                    if (pkt.name() != null) {
                        buf.writeUtf(pkt.name());
                    }

                    buf.writeBoolean(pkt.iconId() != null);
                    if (pkt.iconId() != null) {
                        buf.writeUtf(pkt.iconId());
                    }
                }
            },
            buf -> {
                UUID id = buf.readUUID();
                boolean removed = buf.readBoolean();

                if (removed) {
                    return new ShipSyncPacket(id, null, null, true);
                }

                String name = buf.readBoolean() ? buf.readUtf() : null;
                String iconId = buf.readBoolean() ? buf.readUtf() : null;
                return new ShipSyncPacket(id, name, iconId, false);
            }
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}