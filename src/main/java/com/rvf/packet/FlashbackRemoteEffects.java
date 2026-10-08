package com.rvf.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** The active potion effects of the recorded player. */
public record FlashbackRemoteEffects(int entityId, List<MobEffectInstance> effects) implements CustomPacketPayload {
    public static final Type<FlashbackRemoteEffects> TYPE = new Type<>(Flashback.createIdentifier("remote_effects"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashbackRemoteEffects> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entityId);
            buf.writeVarInt(payload.effects.size());
            for (MobEffectInstance effect : payload.effects) {
                MobEffectInstance.STREAM_CODEC.encode(buf, effect);
            }
        },
        buf -> {
            int entityId = buf.readVarInt();
            int count = buf.readVarInt();
            List<MobEffectInstance> effects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                effects.add(MobEffectInstance.STREAM_CODEC.decode(buf));
            }
            return new FlashbackRemoteEffects(entityId, effects);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}