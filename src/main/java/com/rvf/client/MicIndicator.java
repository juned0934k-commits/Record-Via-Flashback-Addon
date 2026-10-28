package com.rvf.client;

import com.moulberry.flashback.Flashback;
import com.rvf.voice.VoiceCapture;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/**
 * Small red "voice bars" icon to the right of the hotbar. Shown only while a recording is capturing the microphone
 * and the player is speaking. Never shown in a replay. The icon is drawn with rectangles (no texture needed).
 */
public final class MicIndicator {

    private MicIndicator() {}

    private static final int COLOUR = 0xFFFF0000;
    private static final int BAR_WIDTH = 1;
    private static final int BAR_GAP = 1;
    private static final int ICON_HEIGHT = 16;

    /** Bar heights as a fraction of the icon height, measured from the reference icon. */
    private static final float[] BARS = {0.26f, 0.46f, 0.60f, 0.40f, 0.80f, 1.00f, 0.53f, 0.40f};

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui) {
                return;
            }
            if (Flashback.isInReplay() || !VoiceCapture.isSpeaking()) {
                return;
            }

            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();

            // The hotbar is 182 wide and 22 high at the bottom; its slots are 20 high, starting 1 below its top
            int x = screenWidth / 2 + 91 + 6;
            int top = screenHeight - 19;
            int centerY = top + ICON_HEIGHT / 2;

            for (int i = 0; i < BARS.length; i++) {
                int height = Math.max(2, Math.round(ICON_HEIGHT * BARS[i]));
                int barTop = centerY - height / 2;
                int barX = x + i * (BAR_WIDTH + BAR_GAP);
                graphics.fill(barX, barTop, barX + BAR_WIDTH, barTop + height, COLOUR);
            }
        });
    }

}
