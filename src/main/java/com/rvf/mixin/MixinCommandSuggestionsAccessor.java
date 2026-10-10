package com.rvf.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.class)
public interface MixinCommandSuggestionsAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList rvf$getSuggestionsList();
}
