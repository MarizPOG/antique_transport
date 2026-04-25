package me.mariz.antique_transport.server;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ShipUpdatePacket(
        UUID shipId,
        @Nullable String name,
        @Nullable String iconId
) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.tryBuild("antique_transport", "shipupdate");

    public static final Type<ShipUpdatePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ShipUpdatePacket> CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.shipId());

                buf.writeBoolean(pkt.name() != null);
                if (pkt.name() != null) {
                    buf.writeUtf(pkt.name());
                }

                buf.writeBoolean(pkt.iconId() != null);
                if (pkt.iconId() != null) {
                    buf.writeUtf(pkt.iconId());
                }
            },
            buf -> new ShipUpdatePacket(
                    buf.readUUID(),
                    buf.readBoolean() ? buf.readUtf() : null,
                    buf.readBoolean() ? buf.readUtf() : null
            )
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}