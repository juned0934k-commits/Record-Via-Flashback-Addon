package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import com.rvf.client.ReplayCreativeScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The tabs, background, scroll bar and search text of the creative inventory the recorded player had open. */
public record FlashbackRemoteCreativeScreen(int entityId, ReplayCreativeScreen.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteCreativeScreen> TYPE = new Type<>(Flashback.createIdentifier("remote_creative_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteCreativeScreen> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entityId);
            ReplayCreativeScreen.encode(buf, payload.snapshot);
        },
        buf -> new FlashbackRemoteCreativeScreen(buf.readVarInt(), ReplayCreativeScreen.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
