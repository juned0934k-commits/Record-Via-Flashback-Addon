package com.rvf.mixin;

import com.rvf.client.ReplaySpyglass;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In a replay Flashback returns the field of view itself, skipping vanilla's spyglass zoom.
 * This runs BEFORE Flashback's override (lower priority number = earlier). While the spectated player was
 * looking through a spyglass (or the zoom is still easing back out) it returns the zoomed value itself,
 * otherwise it does nothing and Flashback's normal value is used.
 */
@Mixin(value = GameRenderer.class, priority = 500)
public abstract class MixinGameRendererSpyglass {

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvf$spyglassFov(Camera camera, float partialTicks, boolean useFov, CallbackInfoReturnable<Float> cir) {
        if (!Flashback.isInReplay() || !useFov) {
            return;
        }

        // A fixed FOV chosen in the replay editor wins
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideFov) {
            return;
        }

        // Called every frame so the zoom eases in and out smoothly
        float scale = ReplaySpyglass.fovScale();
        if (scale < 0.999f) {
            int fov = Minecraft.getInstance().options.fov().get().intValue();
            cir.setReturnValue((float) fov * scale);
        }
    }

}
