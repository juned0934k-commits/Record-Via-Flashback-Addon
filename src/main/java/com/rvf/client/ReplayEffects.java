package com.rvf.client;

import net.minecraft.world.effect.MobEffectInstance;

import java.util.Collection;
import java.util.List;

/** Holds the recorded potion effects of the recorded player while in a replay. */
public final class ReplayEffects {

    private ReplayEffects() {}

    private static int entityId = -1;
    private static List<MobEffectInstance> effects = List.of();

    public static void set(int id, List<MobEffectInstance> newEffects) {
        entityId = id;
        effects = newEffects;
    }

    public static void clear() {
        entityId = -1;
        effects = List.of();
    }

    /** Returns the recorded effects if the given entity is the recorded player, otherwise null. */
    public static Collection<MobEffectInstance> getFor(int id) {
        return id == entityId ? effects : null;
    }

}
