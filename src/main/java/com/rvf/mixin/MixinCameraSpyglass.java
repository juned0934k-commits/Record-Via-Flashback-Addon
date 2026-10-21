package com.rvf.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.rvf.client.ReplaySpyglass;
import net.minecraft.client.Camera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In Minecraft 26.2 Camera.update() stores the result of calculateFov() in the "fov" field and then builds the
 * projection from that field. Camera.getFov() is not used for rendering, so scaling getFov() did nothing.
 * Flashback replaces the vanilla FOV calculation inside calculateFov() while in a replay, which skips vanilla's
 * spyglass zoom. This scales the value at the call site in update(), after Flashback's calculation, while the
 * spectated player was looking through a spyglass (and while the zoom eases back out).
 */
@Mixin(Camera.class)
public abstract class MixinCameraSpyglass {

    private static final Logger RVF_SPYGLASS_LOGGER = LoggerFactory.getLogger("RVF");
    private static boolean rvfZoomLogged = false;

    @ModifyExpressionValue(
        method = "update(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"),
        require = 0
    )
    private float rvf$spyglassFov(float original) {
        if (!Flashback.isInReplay()) {
            return original;
        }

        // A fixed FOV chosen in the replay editor wins
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideFov) {
            return original;
        }

        // Called every frame, so the zoom eases in and out smoothly
        float scale = ReplaySpyglass.fovScale();
        if (scale != 1.0f && !rvfZoomLogged) {
            rvfZoomLogged = true;
            RVF_SPYGLASS_LOGGER.info("RVF spyglass zoom hook is active (scale {})", scale);
        }
        return original * scale;
    }

}
