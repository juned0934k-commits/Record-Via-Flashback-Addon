package com.rvf.mixin;

import com.rvf.client.ReplaySpyglass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla draws the spyglass scope overlay only when the local player (in a replay: the "Replay Viewer")
 * is scoping. This makes it draw the overlay when the spectated player was scoping in the recording.
 */
@Mixin(Hud.class)
public abstract class MixinGuiSpyglass {

    @Shadow
    @Nullable
    protected abstract Player getCameraPlayer();

    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isScoping()Z"), require = 0)
    public boolean rvf$cameraPlayerScoping(LocalPlayer instance, Operation<Boolean> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null && player != instance) {
                if (!ReplaySpyglass.visible()) {
                    return false;
                }
                return ReplaySpyglass.isScoping(player.getId()) || player.isScoping();
            }
        }
        return original.call(instance);
    }

}
