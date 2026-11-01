package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A chunk of recorded system audio (for example Discord voices) for the replay viewer (16-bit mono PCM). */
public record FlashbackRemoteSystemAudio(int sampleRate, byte[] pcm) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteSystemAudio> TYPE = new Type<>(Flashback.createIdentifier("remote_system_audio"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteSystemAudio> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.sampleRate);
            buf.writeByteArray(payload.pcm);
        },
        buf -> new FlashbackRemoteSystemAudio(buf.readVarInt(), buf.readByteArray())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
