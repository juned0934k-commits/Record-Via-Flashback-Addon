package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Whether the recorded player is currently looking through a spyglass. */
public record FlashbackRemoteSpyglass(int entityId, boolean scoping) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteSpyglass> TYPE = new Type<>(Flashback.createResourceLocation("remote_spyglass"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteSpyglass> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entityId);
            buf.writeBoolean(payload.scoping);
        },
        buf -> new FlashbackRemoteSpyglass(buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
