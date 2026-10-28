package com.rvf.client;

import com.rvf.AddonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

/** Remembers whether the recorded player was looking through a spyglass, so the replay can show the scope view. */
public final class ReplaySpyglass {

    private ReplaySpyglass() {}

    private static int entityId = -1;
    private static boolean scoping = false;
    private static boolean hasData = false;

    private static float smoothedScale = 1.0f;
    private static long lastNanos = 0;

    public static void set(int id, boolean isScoping) {
        entityId = id;
        scoping = isScoping;
        hasData = true;
    }

    public static void clear() {
        hasData = false;
        entityId = -1;
        scoping = false;
    }

    /**
     * Whether the scope should be shown for this player. Only when the recording contains spyglass data from this addon
     * (so turning "Spyglass" off while recording really removes it). The vanilla state is only used for other players.
     */
    public static boolean shouldScope(int id, boolean vanillaScoping) {
        if (!hasData) {
            return false;
        }
        return isScoping(id) || (id != entityId && vanillaScoping);
    }

    /** True if the recording says the given entity is using a spyglass right now. */
    public static boolean isScoping(int id) {
        return scoping && id == entityId;
    }

    /** False if the spyglass view is switched off in the addon config. */
    public static boolean visible() {
        return AddonConfig.get().showSpyglass;
    }

    private static boolean cameraIsScoping() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (!(camera instanceof AbstractClientPlayer player) || camera == minecraft.player) {
            return false;
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (!visible()) {
            return false;
        }
        return shouldScope(player.getId(), player.isScoping());
    }

    /**
     * Multiplier for the field of view: 1.0 normally, easing down to 0.1 while the spectated player is
     * looking through a spyglass (like vanilla). Called every frame from the getFov override.
     */
    public static float fovScale() {
        long now = System.nanoTime();
        float dtTicks = lastNanos == 0 ? 0.0f : (now - lastNanos) / 50_000_000.0f;
        lastNanos = now;
        if (dtTicks > 5.0f) {
            dtTicks = 5.0f;
        }

        float target = cameraIsScoping() ? 0.1f : 1.0f;
        float factor = 1.0f - (float) Math.pow(0.5, dtTicks);
        smoothedScale += (target - smoothedScale) * factor;
        if (Math.abs(target - smoothedScale) < 0.001f) {
            smoothedScale = target;
        }
        return smoothedScale;
    }

}
