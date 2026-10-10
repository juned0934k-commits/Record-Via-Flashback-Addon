package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import com.rvf.client.ReplayChatSuggestions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The suggestions popup the recorded player had open while typing in chat. */
public record FlashbackRemoteChatSuggestions(int entityId, ReplayChatSuggestions.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteChatSuggestions> TYPE = new Type<>(Flashback.createIdentifier("remote_chat_suggestions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteChatSuggestions> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entityId);
            ReplayChatSuggestions.encode(buf, payload.snapshot);
        },
        buf -> new FlashbackRemoteChatSuggestions(buf.readVarInt(), ReplayChatSuggestions.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
