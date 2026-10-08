package com.rvf.mixin;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla only plays the floating-totem animation when the popping entity is the local player.
 * In a replay the local player is the "Replay Viewer", so the recorded player's pop only made a sound.
 * This shows the animation when the player we are spectating pops a totem.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListenerTotem {

@Shadow
    private ClientLevel level;

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void rvf$totemPopForSpectatedPlayer(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (!Flashback.isInReplay() || packet.getEventId() != 35) {
            return;
        }

        Entity entity = packet.getEntity(this.level);
        if (!(entity instanceof Player player)) {
            return;
        }
        if (entity == Minecraft.getInstance().player || entity != Minecraft.getInstance().getCameraEntity()) {
            return; // vanilla already handles the local player; only show it for who we're spectating
        }

        Minecraft.getInstance().gameRenderer.displayItemActivation(rvf$findTotem(player));
    }

    private static ItemStack rvf$findTotem(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.has(DataComponents.DEATH_PROTECTION)) {
                return stack;
            }
        }
        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }
}
