package com.rvf.client;

import com.rvf.AddonConfig;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Remembers the camera perspective (F5) the recorded player had, and applies it to the replay
 * when "autoPerspective" is on and we are spectating a player.
 */
public final class ReplayPerspective {

    private ReplayPerspective() {}

    private static int recorded = -1;

    /** Called whenever the recording says the perspective changed (also on seeking). */
    public static void onRecorded(int type) {
        recorded = type;
        apply();
    }

    public static void apply() {
        if (recorded < 0 || !Flashback.isInReplay() || !AddonConfig.get().autoPerspective) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == null || minecraft.getCameraEntity() == minecraft.player) {
            return; // free camera: there is no player to take the perspective from
        }

        CameraType[] types = CameraType.values();
        if (recorded >= types.length) return;

        // Only touch the camera when it really differs. Setting it again and again every time the same
        // value arrives made the view flicker through the perspectives on 26.2.
        CameraType wanted = types[recorded];
        if (minecraft.options.getCameraType() != wanted) {
            minecraft.options.setCameraType(wanted);
        }
    }

}