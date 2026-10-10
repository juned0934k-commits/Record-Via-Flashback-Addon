package com.rvf.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface MixinCreativeModeInventoryScreenAccessor {

    /** The tab that is currently selected (a static field in vanilla). */
    @Accessor("selectedTab")
    static CreativeModeTab rvf$getSelectedTab() {
        throw new AssertionError();
    }

    @Accessor("scrollOffs")
    float rvf$getScrollOffs();

    @Accessor("searchBox")
    EditBox rvf$getSearchBox();

    @Invoker("getTabX")
    int rvf$getTabX(CreativeModeTab tab);

    @Invoker("canScroll")
    boolean rvf$canScroll();
}
