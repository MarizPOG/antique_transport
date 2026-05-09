package me.mariz.antique_transport.server;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record TeleportToShipPacket(UUID shipId)
        implements CustomPacketPayload {

    public static final Type<TeleportToShipPacket> TYPE =
            new Type<>(ResourceLocation.tryBuild("antique_transport", "teleport_to_ship"));

    public static final StreamCodec<FriendlyByteBuf, TeleportToShipPacket> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC.cast(), TeleportToShipPacket::shipId,
                    TeleportToShipPacket::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
