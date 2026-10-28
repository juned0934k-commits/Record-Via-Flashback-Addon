package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A chunk of recorded microphone audio for the replay viewer (16-bit mono PCM). */
public record FlashbackRemoteVoice(int sampleRate, byte[] pcm) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteVoice> TYPE = new Type<>(Flashback.createIdentifier("remote_voice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteVoice> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.sampleRate);
            buf.writeByteArray(payload.pcm);
        },
        buf -> new FlashbackRemoteVoice(buf.readVarInt(), buf.readByteArray())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}