package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import com.rvf.client.InventoryScreenState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlashbackRemoteInventoryScreen(int entityId, InventoryScreenState state) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteInventoryScreen> TYPE = new Type<>(Flashback.createResourceLocation("remote_inventory_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteInventoryScreen> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entityId);
            payload.state.encode(buf);
        },
        buf -> new FlashbackRemoteInventoryScreen(buf.readVarInt(), InventoryScreenState.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
