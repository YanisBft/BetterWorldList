package com.yanis48.betterworldlist.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.widget.EntryListWidget;

@Mixin(EntryListWidget.class)
public interface EntryListWidgetAccessor {

	@Accessor
	boolean getRenderSelection();
}
