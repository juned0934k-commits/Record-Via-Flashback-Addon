package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import com.rvf.client.ReplayInventoryExtras;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Recipe book state (and later other extras) of the inventory screen the recorded player had open. */
public record FlashbackRemoteInventoryExtras(int entityId, ReplayInventoryExtras.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteInventoryExtras> TYPE = new Type<>(Flashback.createResourceLocation("remote_inventory_extras"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteInventoryExtras> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entityId);
            ReplayInventoryExtras.encode(buf, payload.snapshot);
        },
        buf -> new FlashbackRemoteInventoryExtras(buf.readVarInt(), ReplayInventoryExtras.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
