package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The recorded player's camera perspective (F5): 0 = first person, 1 = third person back, 2 = third person front. */
public record FlashbackRemoteCameraType(int perspective) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteCameraType> TYPE = new Type<>(Flashback.createIdentifier("remote_camera_type"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteCameraType> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.perspective),
        buf -> new FlashbackRemoteCameraType(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
