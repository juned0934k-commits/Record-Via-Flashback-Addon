package com.rvf.mixin;

import com.rvf.client.AddonVisualsUi;
import com.moulberry.flashback.editor.ui.windows.VisualsWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the addon's checkboxes to the bottom of Flashback's Visuals window (just before the window is closed). */
@Mixin(value = VisualsWindow.class, remap = false)
public abstract class MixinVisualsWindow {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;end()V"), remap = false, require = 0)
    private static void rvf$addonCheckboxes(CallbackInfo ci) {
        AddonVisualsUi.render();
    }

}
