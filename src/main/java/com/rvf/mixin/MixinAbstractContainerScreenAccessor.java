package com.rvf.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface MixinAbstractContainerScreenAccessor {
    @Accessor("leftPos") int rvf$getLeftPos();
    @Accessor("topPos") int rvf$getTopPos();
    @Accessor("imageWidth") int rvf$getImageWidth();
    @Accessor("imageHeight") int rvf$getImageHeight();
}
