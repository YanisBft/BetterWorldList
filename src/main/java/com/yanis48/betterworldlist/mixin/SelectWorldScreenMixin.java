package com.yanis48.betterworldlist.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.yanis48.betterworldlist.GridWorldListWidget;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

@Mixin(SelectWorldScreen.class)
public class SelectWorldScreenMixin extends Screen {
	private final SelectWorldScreen selectWorldScreen = (SelectWorldScreen) (Object) this;
	private static final Identifier GRID_ICON_TEXTURE = new Identifier("better-world-list", "textures/grid.png");
	private static final Identifier HORIZONTAL_ICON_TEXTURE = new Identifier("better-world-list", "textures/horizontal.png");
	private static boolean grid = false;
	@Shadow protected Screen parent;
	@Shadow private String tooltipText;
	@Shadow private ButtonWidget deleteButton;
	@Shadow private ButtonWidget selectButton;
	@Shadow private ButtonWidget editButton;
	@Shadow private ButtonWidget recreateButton;
	@Shadow protected TextFieldWidget searchBox;
	@Shadow private WorldListWidget levelList;
	private GridWorldListWidget gridLevelList;
	
	protected SelectWorldScreenMixin() {
		super(null);
	}
	
	@Inject(method = "init", at = @At(value = "HEAD"), cancellable = true)
	private void init(CallbackInfo ci) {
		SelectWorldScreen selectWorldScreen = (SelectWorldScreen) (Object) this;
		
		this.minecraft.keyboard.enableRepeatEvents(true);
		
		// Layout button
		this.addButton(new TexturedButtonWidget(this.width / 2 - 126, 22, 20, 20, 0, 0, 20, grid ? HORIZONTAL_ICON_TEXTURE : GRID_ICON_TEXTURE, 32, 64, (buttonWidget) -> {
			this.refreshScreen();
		}, ""));
		
		// Search box
		this.searchBox = new TextFieldWidget(this.font, this.width / 2 - 100, 22, 200, 20, this.searchBox, I18n.translate("selectWorld.search"));
		this.searchBox.setChangedListener((string) -> {
			if (grid) {
				this.gridLevelList.filter(() -> {
					return string;
				}, false);
			} else {
				this.levelList.filter(() -> {
					return string;
				}, false);
			}
		});
		this.children.add(this.searchBox);
		
		// World List widget
		if (grid) {
			this.gridLevelList = new GridWorldListWidget(selectWorldScreen, this.minecraft, this.width, this.height, 48, this.height - 64, 128, () -> {
				return this.searchBox.getText();
			}, this.gridLevelList);
			this.children.add(this.gridLevelList);
		} else {
			this.levelList = new WorldListWidget(selectWorldScreen, this.minecraft, this.width, this.height, 48, this.height - 64, 36, () -> {
				return this.searchBox.getText();
			}, this.levelList);
			this.children.add(this.levelList);
		}
		
		// Play Selected World button
		this.selectButton = this.addButton(new ButtonWidget(this.width / 2 - 154, this.height - 52, 150, 20, I18n.translate("selectWorld.select"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::edit);
			} else {
				this.levelList.method_20159().ifPresent(WorldListWidget.Entry::edit);
			}
		}));
		
		// Create New World button
		this.addButton(new ButtonWidget(this.width / 2 + 4, this.height - 52, 150, 20, I18n.translate("selectWorld.create"), (buttonWidget) -> {
			this.minecraft.openScreen(new CreateWorldScreen(this));
		}));
		
		// Edit button
		this.editButton = this.addButton(new ButtonWidget(this.width / 2 - 154, this.height - 28, 72, 20, I18n.translate("selectWorld.edit"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::edit);
			} else {
				this.levelList.method_20159().ifPresent(WorldListWidget.Entry::edit);
			}
		}));
		
		// Delete button
		this.deleteButton = this.addButton(new ButtonWidget(this.width / 2 - 76, this.height - 28, 72, 20, I18n.translate("selectWorld.delete"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::edit);
			} else {
				this.levelList.method_20159().ifPresent(WorldListWidget.Entry::edit);
			}
		}));
		
		// Recreate button
		this.recreateButton = this.addButton(new ButtonWidget(this.width / 2 + 4, this.height - 28, 72, 20, I18n.translate("selectWorld.recreate"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::edit);
			} else {
				this.levelList.method_20159().ifPresent(WorldListWidget.Entry::edit);
			}
		}));
		
		// Cancel button
		this.addButton(new ButtonWidget(this.width / 2 + 82, this.height - 28, 72, 20, I18n.translate("gui.cancel"), (buttonWidget) -> {
			this.minecraft.openScreen(this.parent);
		}));
		
		selectWorldScreen.worldSelected(false);
		this.setInitialFocus(this.searchBox);
		
		ci.cancel();
	}
	
	private void refreshScreen() {
		grid = !grid;
		this.minecraft.openScreen(this.parent);
		this.minecraft.openScreen(selectWorldScreen);
	}
	
	@Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
	private void render(int mouseX, int mouseY, float delta, CallbackInfo ci) {
		this.tooltipText = null;
		List<String> gridLayout = Lists.newArrayList(Splitter.on("\n").split(new TranslatableText("better-world-list.layout.grid").asFormattedString()));
		List<String> horizontalLayout = Lists.newArrayList(Splitter.on("\n").split(new TranslatableText("better-world-list.layout.horizontal").asFormattedString()));
		
		if (grid) {
			this.gridLevelList.render(mouseX, mouseY, delta);
		} else {
			this.levelList.render(mouseX, mouseY, delta);
		}
		
		this.searchBox.render(mouseX, mouseY, delta);
		this.drawCenteredString(this.font, this.title.asFormattedString(), this.width / 2, 8, 16777215);
		super.render(mouseX, mouseY, delta);
		
		if (this.tooltipText != null) {
			this.renderTooltip(Lists.newArrayList(Splitter.on("\n").split(this.tooltipText)), mouseX, mouseY);
		}
		
		int x = this.width / 2 - 126;
		int y = 22;
		if ((mouseX >= x) && (mouseX <= x + 20) && (mouseY >= y) && (mouseY <= y + 20)) {
			this.renderTooltip(grid ? horizontalLayout : gridLayout, mouseX, mouseY);
		}
		
		ci.cancel();
	}
	
	@Inject(method = "removed", at = @At(value = "HEAD"), cancellable = true)
	private void removed(CallbackInfo ci) {
		if (grid) {
			if (this.gridLevelList != null) {
				this.gridLevelList.children().forEach(GridWorldListWidget.Entry::close);
			}
		} else {
			if (this.levelList != null) {
				this.levelList.children().forEach(WorldListWidget.Entry::close);
			}
		}
		
		ci.cancel();
	}
}
