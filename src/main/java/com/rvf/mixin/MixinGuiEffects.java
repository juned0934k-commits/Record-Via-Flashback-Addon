package com.rvf.mixin;

import com.rvf.AddonConfig;
import com.rvf.client.ReplayEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * Vanilla draws the status-effect icons of the local player, which in a replay is the "Replay Viewer".
 * This draws the recorded effects of the player being spectated instead, and lets the config hide them.
 */
@Mixin(Gui.class)
public abstract class MixinGuiEffects {

    @Shadow
    @Nullable
    protected abstract Player getCameraPlayer();

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
    public void rvf$hideEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Flashback.isInReplay() && !AddonConfig.get().showEffects) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"), require = 0)
    public Collection<MobEffectInstance> rvf$cameraPlayerEffects(LocalPlayer instance, Operation<Collection<MobEffectInstance>> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                Collection<MobEffectInstance> recorded = ReplayEffects.getFor(player.getId());
                if (recorded != null) {
                    return recorded;
                }
                if (player != instance) {
                    return player.getActiveEffects();
                }
            }
        }
        return original.call(instance);
    }

}
