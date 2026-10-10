package com.rvf.mixin;

import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface MixinSuggestionsListAccessor {
    @Accessor("suggestionList")
    List<Suggestion> rvf$getSuggestionList();

    @Accessor("offset")
    int rvf$getOffset();

    @Accessor("current")
    int rvf$getCurrent();
}
