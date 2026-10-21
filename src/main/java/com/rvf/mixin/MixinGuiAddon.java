package com.rvf.mixin;

import com.rvf.client.ReplayChatTyping;
import com.rvf.client.ReplayInventoryOverlay;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the recorded inventory screen and the chat input bar at the very end of the in-game GUI. */
@Mixin(Hud.class)
public abstract class MixinGuiAddon {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    public void rvf$renderOverlays(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ReplayInventoryOverlay.render(guiGraphics);
            ReplayChatTyping.render(guiGraphics);
        }
    }

}
