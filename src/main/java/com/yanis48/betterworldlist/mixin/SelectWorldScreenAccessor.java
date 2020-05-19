package com.yanis48.betterworldlist.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

@Mixin(SelectWorldScreen.class)
public interface SelectWorldScreenAccessor {
	
	@Accessor("searchBox")
	TextFieldWidget getSearchBox();
}
