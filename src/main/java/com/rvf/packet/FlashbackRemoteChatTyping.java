package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Whether the recorded player has the chat input open, and what they have typed so far. */
public record FlashbackRemoteChatTyping(int entityId, boolean open, String text, int cursor) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteChatTyping> TYPE = new Type<>(Flashback.createIdentifier("remote_chat_typing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteChatTyping> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entityId);
            buf.writeBoolean(payload.open);
            buf.writeUtf(payload.text);
            buf.writeVarInt(payload.cursor);
        },
        buf -> new FlashbackRemoteChatTyping(buf.readVarInt(), buf.readBoolean(), buf.readUtf(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
