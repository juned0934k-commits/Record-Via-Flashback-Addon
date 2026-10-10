package com.rvf.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface MixinChatScreenAccessor {
    @Accessor("commandSuggestions")
    CommandSuggestions rvf$getCommandSuggestions();
}
