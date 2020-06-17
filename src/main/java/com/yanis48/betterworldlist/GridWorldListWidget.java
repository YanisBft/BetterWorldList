package com.yanis48.betterworldlist;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yanis48.betterworldlist.mixin.EntryListWidgetAccessor;
import com.yanis48.betterworldlist.mixin.SelectWorldScreenAccessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.BackupPromptScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.FatalErrorScreen;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.EditWorldScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringRenderable;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;

@Environment(EnvType.CLIENT)
public class GridWorldListWidget extends AlwaysSelectedEntryListWidget<GridWorldListWidget.Entry> {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat();
	private static final Identifier UNKNOWN_SERVER_TEXTURE = new Identifier("textures/misc/unknown_server.png");
	private static final Identifier WORLD_SELECTION_TEXTURE = new Identifier("textures/gui/world_selection.png");
	private final SelectWorldScreen parent;
	private List<LevelSummary> levels;
	private boolean scrolling;
	
	public GridWorldListWidget(SelectWorldScreen parent, MinecraftClient client, int width, int height, int top, int bottom, int itemHeight, Supplier<String> searchFilter, GridWorldListWidget list) {
		super(client, width, height, top, bottom, itemHeight);
		this.parent = parent;
		
		if (list != null) {
			this.levels = list.levels;
		}
		
		this.filter(searchFilter, false);
	}
	
	public void filter(Supplier<String> supplier, boolean load) {
		this.clearEntries();
		LevelStorage levelStorage = this.client.getLevelStorage();
		if (this.levels == null || load) {
			try {
				this.levels = levelStorage.getLevelList();
			} catch (LevelStorageException e) {
				LOGGER.error("Couldn't load level list", e);
				this.client.openScreen(new FatalErrorScreen(new TranslatableText("selectWorld.unable_to_load"), new LiteralText(e.getMessage())));
				return;
			}
			
			Collections.sort(this.levels);
		}
		
		if (this.levels.isEmpty()) {
			this.client.openScreen(new CreateWorldScreen((Screen)null));
		} else {
			String string = supplier.get().toLowerCase(Locale.ROOT);
			Iterator<?> var5 = this.levels.iterator();
			
			while(true) {
				LevelSummary levelSummary;
				do {
					if (!var5.hasNext()) {
						return;
					}
					
					levelSummary = (LevelSummary)var5.next();
				} while(!levelSummary.getDisplayName().toLowerCase(Locale.ROOT).contains(string) && !levelSummary.getName().toLowerCase(Locale.ROOT).contains(string));
				
				this.addEntry(new GridWorldListWidget.Entry(this, levelSummary, this.client.getLevelStorage()));
			}
		}
	}
	
	@Override
	protected void renderList(MatrixStack matrices, int x, int y, int mouseX, int mouseY, float delta) {
		int count = this.getItemCount();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		for (int index = 0; index < count; index++) {
			int rowTop = this.getRowTop(index);
			int rowBottom = this.getRowBottom(index);
			if (rowBottom >= this.top && rowTop <= this.bottom) {
				int q;
				if (index % 2 == 0) {
					q = y + (index / 2) * this.itemHeight + this.headerHeight;
				} else {
					q = y + ((index - 1) / 2) * this.itemHeight + this.headerHeight;
				}
				int height = this.itemHeight - 4;
				GridWorldListWidget.Entry entry = this.getEntry(index);
				int width = this.getRowWidth();
				int v;
				int u;
				if (((EntryListWidgetAccessor) this).getRenderSelection() && this.isSelectedItem(index)) {
					if (index % 2 == 0) {
						v = this.left + this.width / 2 - width / 2;
						u = this.left + this.width / 2 - 4;
					} else {
						v = this.left + this.width / 2 + 4;
						u = this.left + this.width / 2 + width / 2;
					}
					RenderSystem.disableTexture();
					float f = this.isFocused() ? 1.0F : 0.5F;
					RenderSystem.color4f(f, f, f, 1.0F);
					bufferBuilder.begin(7, VertexFormats.POSITION);
					bufferBuilder.vertex(v, q + height + 2, 0.0D).next();
					bufferBuilder.vertex(u, q + height + 2, 0.0D).next();
					bufferBuilder.vertex(u, q - 2, 0.0D).next();
					bufferBuilder.vertex(v, q - 2, 0.0D).next();
					tessellator.draw();
					RenderSystem.color4f(0.0F, 0.0F, 0.0F, 1.0F);
					bufferBuilder.begin(7, VertexFormats.POSITION);
					bufferBuilder.vertex(v + 1, q + height + 1, 0.0D).next();
					bufferBuilder.vertex(u - 1, q + height + 1, 0.0D).next();
					bufferBuilder.vertex(u - 1, q - 1, 0.0D).next();
					bufferBuilder.vertex(v + 1, q - 1, 0.0D).next();
					tessellator.draw();
					RenderSystem.enableTexture();
				}
				
				v = this.getRowLeft(index);
				entry.render(matrices, index, rowTop, v, width, height, mouseX, mouseY, this.isMouseOver(mouseX, mouseY) && Objects.equals(this.getEntryAt(mouseX, mouseY), entry), delta);
			}
		}
	}
	
	protected int getRowLeft(int index) {
		int rowLeft;
		if (index % 2 == 0) {
			rowLeft = this.left + this.width / 2 - this.getRowWidth() / 2 + 2;
		} else {
			rowLeft = this.left + this.width / 2 + 6;
		}
		return rowLeft;
	}
	
	@Override
	protected int getRowTop(int index) {
		int rowTop;
		if (index % 2 == 0) {
			rowTop = this.top + 4 - (int) this.getScrollAmount() + (index / 2) * this.itemHeight + this.headerHeight;
		} else {
			rowTop = this.top + 4 - (int) this.getScrollAmount() + ((index - 1) / 2) * this.itemHeight + this.headerHeight;
		}
		return rowTop;
	}
	
	private int getRowBottom(int i) {
		return this.getRowTop(i) + this.itemHeight;
	}
	
	@Override
	public int getRowWidth() {
		return super.getRowWidth() + 52;
	}
	
	@Override
	protected int getScrollbarPositionX() {
		return super.getScrollbarPositionX() + 20;
	}
	
	@Override
	protected int getMaxPosition() {
		if (this.getItemCount() % 2 == 0) {
			return (this.getItemCount() / 2) * this.itemHeight + this.headerHeight;
		} else {
			return (this.getItemCount() / 2 + 1) * this.itemHeight + this.headerHeight;
		}
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		this.updateScrollingState(mouseX, mouseY, button);
		if (!this.isMouseOver(mouseX, mouseY)) {
			return false;
		} else {
			GridWorldListWidget.Entry entry = this.getEntryAt(mouseX, mouseY);
			if (entry != null) {
				if (entry.mouseClicked(mouseX, mouseY, button)) {
					this.setFocused(entry);
					this.setDragging(true);
					return true;
				}
			} else if (button == 0) {
				this.clickedHeader((int)(mouseX - (this.left + this.width / 2 - this.getRowWidth() / 2)), (int)(mouseY - this.top) + (int)this.getScrollAmount() - 4);
				return true;
			}
			return this.scrolling;
		}
	}
	
	protected final GridWorldListWidget.Entry getEntryAt(double x, double y) {
		int rowMiddle = this.getRowWidth() / 2;
		int middle = this.left + this.width / 2;
		int left = middle - rowMiddle;
		int right = middle + rowMiddle;
		int height = MathHelper.floor(y - this.top) - this.headerHeight + (int) this.getScrollAmount() - 4;
		int n = height / this.itemHeight;
		
		if (x < this.getScrollbarPositionX() && n >= 0 && height >= 0) {
			if (x >= left && x <= middle) {
				return (n + n) < this.getItemCount() ? (GridWorldListWidget.Entry) this.children().get(n * 2) : null;
			} else if (x >= middle && x <= right) {
				return (n + n + 1) < this.getItemCount() ? (GridWorldListWidget.Entry) this.children().get(n * 2 + 1) : null;
			}
		}
		return null;
	}
	
	@Override
	protected boolean isFocused() {
		return this.parent.getFocused() == this;
	}
	
	@Override
	public void setSelected(GridWorldListWidget.Entry entry) {
		super.setSelected(entry);
		if (entry != null) {
			LevelSummary levelSummary = entry.level;
			NarratorManager.INSTANCE.narrate((new TranslatableText("narrator.select", new Object[]{new TranslatableText("narrator.select.world", new Object[]{levelSummary.getDisplayName(), new Date(levelSummary.getLastPlayed()), levelSummary.isHardcore() ? new TranslatableText("gameMode.hardcore") : new TranslatableText("gameMode." + levelSummary.getGameMode().getName()), levelSummary.hasCheats() ? new TranslatableText("selectWorld.cheats") : LiteralText.EMPTY, levelSummary.getVersion()})})).getString());
		}
	}
	
	@Override
	protected void moveSelection(EntryListWidget.class_5403 arg) {
		this.method_30013(arg, (entry) -> {
			return !entry.level.isLocked();
		});
	}
	
	public Optional<GridWorldListWidget.Entry> method_20159() {
		return Optional.ofNullable(this.getSelected());
	}
	
	public SelectWorldScreen getParent() {
		return this.parent;
	}
	
	@Environment(EnvType.CLIENT)
	public final class Entry extends AlwaysSelectedEntryListWidget.Entry<GridWorldListWidget.Entry> implements AutoCloseable {
		private final MinecraftClient client;
		private final SelectWorldScreen screen;
		private final LevelSummary level;
		private final Identifier iconLocation;
		private File iconFile;
		private final NativeImageBackedTexture icon;
		private long time;
		
		public Entry(GridWorldListWidget levelList, LevelSummary level, LevelStorage levelStorage) {
			this.screen = levelList.getParent();
			this.level = level;
			this.client = MinecraftClient.getInstance();
			this.iconLocation = new Identifier("worlds/" + Hashing.sha1().hashUnencodedChars(level.getName()) + "/icon");
			this.iconFile = level.getFile();
			if (!this.iconFile.isFile()) {
				this.iconFile = null;
			}
			
			this.icon = this.getIconTexture();
		}
		
		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return Objects.equals(GridWorldListWidget.this.getEntryAt(mouseX, mouseY), this);
		}
		
		@Override
		public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			String displayName = this.level.getDisplayName();
			String name = this.level.getName();
			String lastPlayed = "(" + GridWorldListWidget.DATE_FORMAT.format(new Date(this.level.getLastPlayed())) + ")";
			Text gameMode = level.isHardcore() ? new TranslatableText("gameMode.hardcore").formatted(Formatting.DARK_RED) : level.getGameMode().getTranslatableName();
			Text cheats = new TranslatableText("selectWorld.cheats").formatted(Formatting.LIGHT_PURPLE);
			Text version = this.getVersionText();
			
			if (StringUtils.isEmpty(displayName)) {
				displayName = I18n.translate("selectWorld.world") + " " + (index + 1);
			}
			
			RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
			this.client.getTextureManager().bindTexture(this.icon != null ? this.iconLocation : GridWorldListWidget.UNKNOWN_SERVER_TEXTURE);
			RenderSystem.enableBlend();
			DrawableHelper.drawTexture(matrices, x, y, 0.0F, 0.0F, 128, 64, 128, 64);
			RenderSystem.disableBlend();
			
			TextRenderer textRenderer = this.client.textRenderer;
			
			int x2 = x + 3;
			int y2 = y + 64 + 3;
			textRenderer.draw(matrices, displayName, x2, y2, 16777215);
			
			y2 += 9;
			textRenderer.draw(matrices, name, x2, y2, 8421504);
			
			y2 += 9;
			textRenderer.draw(matrices, lastPlayed, x2, y2, 8421504);
			
			y2 += 9;
			textRenderer.draw(matrices, gameMode, x2, y2, 16777045);
			
			if (level.hasCheats()) {
				y2 += 9;
				textRenderer.draw(matrices, cheats, x2, y2, 8421504);
			}
			
			y2 += 9;
			textRenderer.draw(matrices, version, x2, y2, 8421504);
			
			if (this.client.options.touchscreen || hovered) {
				this.client.getTextureManager().bindTexture(GridWorldListWidget.WORLD_SELECTION_TEXTURE);
				
				int arrowX = mouseX - x;
				int arrowY = mouseY - y;
				// Checks if the mouse hovers the arrow
				boolean arrowHovered = (arrowX > 0) && (arrowX < 32) && (arrowY > 0) && (arrowY < 32);
				// If it does, the arrow texture is blue instead of gray
				int k = arrowHovered ? 32 : 0;
				
				if (this.level.isLocked()) {
					DrawableHelper.drawTexture(matrices, x, y, 96.0F, k, 32, 32, 256, 256);
					if (arrowHovered) {
						StringRenderable tooltipText = new TranslatableText("selectWorld.locked").formatted(Formatting.RED);
						this.screen.setTooltip(this.client.textRenderer.wrapLines(tooltipText, 175));
					}
				} else if (this.level.isDifferentVersion()) {
					DrawableHelper.drawTexture(matrices, x, y, 32.0F, k, 32, 32, 256, 256);
					if (this.level.isFutureLevel()) {
						DrawableHelper.drawTexture(matrices, x, y, 96.0F, k, 32, 32, 256, 256);
						if (arrowHovered) {
							this.screen.setTooltip(Arrays.asList(new TranslatableText("selectWorld.tooltip.fromNewerVersion1").formatted(Formatting.RED), new TranslatableText("selectWorld.tooltip.fromNewerVersion2").formatted(Formatting.RED)));
						}
					} else if (!SharedConstants.getGameVersion().isStable()) {
						DrawableHelper.drawTexture(matrices, x, y, 64.0F, k, 32, 32, 256, 256);
						if (arrowHovered) {
							this.screen.setTooltip(Arrays.asList(new TranslatableText("selectWorld.tooltip.snapshot1").formatted(Formatting.GOLD), new TranslatableText("selectWorld.tooltip.snapshot2").formatted(Formatting.GOLD)));
						}
					}
				} else {
					DrawableHelper.drawTexture(matrices, x, y, 0.0F, k, 32, 32, 256, 256);
				}
			}
		}
		
		private MutableText getVersionText() {
			MutableText version = level.getVersion();
			MutableText text = new TranslatableText("selectWorld.version").append(" ");
			
			if (level.isDifferentVersion()) {
				text.append(version.formatted(level.isFutureLevel() ? Formatting.RED : Formatting.AQUA, Formatting.ITALIC));
			} else {
				text.append(version.formatted(Formatting.AQUA));
			}
			
			return text;
		}
		
		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (this.level.isLocked()) {
				return true;
			} else {
				GridWorldListWidget.this.setSelected(this);
				this.screen.worldSelected(GridWorldListWidget.this.method_20159().isPresent());
				
				int rowMiddle = GridWorldListWidget.this.getRowWidth() / 2;
				int middle = GridWorldListWidget.this.left + GridWorldListWidget.this.width / 2;
				int left = middle - rowMiddle;
				int right = middle + rowMiddle;
				int height = MathHelper.floor(mouseY - GridWorldListWidget.this.top) - GridWorldListWidget.this.headerHeight + (int) GridWorldListWidget.this.getScrollAmount() - 4;
				int n = height / GridWorldListWidget.this.itemHeight;
				boolean validX = false;
				boolean validY = false;
				
				if (mouseX >= left && mouseX <= middle) {
					validX = mouseX - GridWorldListWidget.this.getRowLeft(n * 2) <= 32.0D;
					validY = mouseY - GridWorldListWidget.this.getRowTop(n * 2) <= 32.0D;
				} else if (mouseX >= middle && mouseX <= right) {
					validX = mouseX - GridWorldListWidget.this.getRowLeft(n * 2 + 1) <= 32.0D;
					validY = mouseY - GridWorldListWidget.this.getRowTop(n * 2 + 1) <= 32.0D;
				}
				
				if (validX && validY) {
					this.play();
					return true;
				} else if (Util.getMeasuringTimeMs() - this.time < 250L) {
					this.play();
					return true;
				} else {
					this.time = Util.getMeasuringTimeMs();
					return false;
				}
			}
		}
		
		public void play() {
			if (!this.level.isLocked()) {
				if (this.level.isOutdatedLevel()) {
					Text text = new TranslatableText("selectWorld.backupQuestion");
					Text text2 = new TranslatableText("selectWorld.backupWarning", new Object[]{this.level.getVersion(), SharedConstants.getGameVersion().getName()});
					this.client.openScreen(new BackupPromptScreen(this.screen, (bl, bl2) -> {
						if (bl) {
							String string = this.level.getName();
							
							try {
								LevelStorage.Session session = this.client.getLevelStorage().createSession(string);
								Throwable var5 = null;
								
								try {
									EditWorldScreen.backupLevel(session);
								} catch (Throwable e) {
									var5 = e;
									throw e;
								} finally {
									if (session != null) {
										if (var5 != null) {
											try {
												session.close();
											} catch (Throwable var14) {
												var5.addSuppressed(var14);
											}
										} else {
											session.close();
										}
									}
								}
							} catch (IOException e) {
								SystemToast.addWorldAccessFailureToast(this.client, string);
								GridWorldListWidget.LOGGER.error("Failed to backup level {}", string, e);
							}
						}
						
						this.start();
					}, text, text2, false));
				} else if (this.level.isFutureLevel()) {
					this.client.openScreen(new ConfirmScreen((bl) -> {
						if (bl) {
							try {
								this.start();
							} catch (Exception e) {
								GridWorldListWidget.LOGGER.error("Failure to open 'future world'", e);
								this.client.openScreen(new NoticeScreen(() -> {
									this.client.openScreen(this.screen);
								}, new TranslatableText("selectWorld.futureworld.error.title"), new TranslatableText("selectWorld.futureworld.error.text")));
							}
						} else {
							this.client.openScreen(this.screen);
						}
					}, new TranslatableText("selectWorld.versionQuestion"), new TranslatableText("selectWorld.versionWarning", new Object[]{this.level.getVersion(), new TranslatableText("selectWorld.versionJoinButton"), ScreenTexts.CANCEL})));
				} else {
					this.start();
				}
			}
		}
		
		public void delete() {
			this.client.openScreen(new ConfirmScreen((bl) -> {
				if (bl) {
					this.client.openScreen(new ProgressScreen());
					LevelStorage levelStorage = this.client.getLevelStorage();
					String string = this.level.getName();
					
					try {
						LevelStorage.Session session = levelStorage.createSession(string);
						Throwable var5 = null;
						
						try {
							session.deleteSessionLock();
						} catch (Throwable var15) {
							var5 = var15;
							throw var15;
						} finally {
							if (session != null) {
								if (var5 != null) {
									try {
										session.close();
									} catch (Throwable e) {
										var5.addSuppressed(e);
									}
								} else {
									session.close();
								}
							}
						}
					} catch (IOException e) {
						SystemToast.addWorldDeleteFailureToast(this.client, string);
						GridWorldListWidget.LOGGER.error("Failed to delete world {}", string, e);
					}
					
					GridWorldListWidget.this.filter(() -> {
						return ((SelectWorldScreenAccessor) this.screen).getSearchBox().getText();
					}, true);
				}
				
				this.client.openScreen(this.screen);
			}, new TranslatableText("selectWorld.deleteQuestion"), new TranslatableText("selectWorld.deleteWarning", new Object[]{this.level.getDisplayName()}), new TranslatableText("selectWorld.deleteButton"), ScreenTexts.CANCEL));
		}
		
		public void edit() {
			String levelName = this.level.getName();
			
			try {
				LevelStorage.Session session = this.client.getLevelStorage().createSession(levelName);
				this.client.openScreen(new EditWorldScreen((bl) -> {
					try {
						session.close();
					} catch (IOException e) {
						GridWorldListWidget.LOGGER.error("Failed to unlock level {}", levelName, e);
					}
					
					if (bl) {
						GridWorldListWidget.this.filter(() -> {
							return ((SelectWorldScreenAccessor) this.screen).getSearchBox().getText();
						}, true);
					}
					
					this.client.openScreen(this.screen);
				}, session));
			} catch (IOException e) {
				SystemToast.addWorldAccessFailureToast(this.client, levelName);
				GridWorldListWidget.LOGGER.error("Failed to access level {}", levelName, e);
				GridWorldListWidget.this.filter(() -> {
					return ((SelectWorldScreenAccessor) this.screen).getSearchBox().getText();
				}, true);
			}
		}
		
		public void recreate() {
			this.method_29990();
			RegistryTracker.Modifiable tracker = RegistryTracker.create();
			
			try {
				LevelStorage.Session session = this.client.getLevelStorage().createSession(this.level.getName());
				Throwable var3 = null;
				
				try {
					MinecraftClient.IntegratedResourceManager integratedResourceManager = this.client.method_29604(tracker, MinecraftClient::method_29598, MinecraftClient::createSaveProperties, false, session);
					Throwable var5 = null;
					
					try {
						LevelInfo levelInfo = integratedResourceManager.getSaveProperties().getLevelInfo();
						GeneratorOptions generatorOptions = integratedResourceManager.getSaveProperties().getGeneratorOptions();
						Path path = CreateWorldScreen.method_29685(session.getDirectory(WorldSavePath.DATAPACKS), this.client);
						if (generatorOptions.isLegacyCustomizedType()) {
							this.client.openScreen(new ConfirmScreen((bl) -> {
								this.client.openScreen((Screen)(bl ? new CreateWorldScreen(this.screen, levelInfo, generatorOptions, path, tracker) : this.screen));
							}, new TranslatableText("selectWorld.recreate.customized.title"), new TranslatableText("selectWorld.recreate.customized.text"), ScreenTexts.PROCEED, ScreenTexts.CANCEL));
						} else {
							this.client.openScreen(new CreateWorldScreen(this.screen, levelInfo, generatorOptions, path, tracker));
						}
					} catch (Throwable e) {
						var5 = e;
						throw e;
					} finally {
						if (integratedResourceManager != null) {
							if (var5 != null) {
								try {
									integratedResourceManager.close();
								} catch (Throwable var31) {
									var5.addSuppressed(var31);
								}
							} else {
								integratedResourceManager.close();
							}
						}
					}
				} catch (Throwable e) {
					var3 = e;
					throw e;
				} finally {
					if (session != null) {
						if (var3 != null) {
							try {
								session.close();
							} catch (Throwable e) {
								var3.addSuppressed(e);
							}
						} else {
							session.close();
						}
					}
				}
			} catch (Exception e) {
				GridWorldListWidget.LOGGER.error("Unable to recreate world", e);
				this.client.openScreen(new NoticeScreen(() -> {
					this.client.openScreen(this.screen);
				}, new TranslatableText("selectWorld.recreate.error.title"), new TranslatableText("selectWorld.recreate.error.text")));
			}
		}
		
		private void start() {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			if (this.client.getLevelStorage().levelExists(this.level.getName())) {
				this.method_29990();
				this.client.startIntegratedServer(this.level.getName());
			}
		}
		
		private void method_29990() {
			this.client.method_29970(new SaveLevelScreen(new TranslatableText("selectWorld.data_read")));
		}
		
		private NativeImageBackedTexture getIconTexture() {
			boolean validFile = this.iconFile != null && this.iconFile.isFile();
			if (validFile) {
				try {
					InputStream inputStream = new FileInputStream(this.iconFile);
					Throwable var3 = null;
					
					NativeImageBackedTexture var6;
					try {
						NativeImage nativeImage = NativeImage.read(inputStream);
						Validate.validState(nativeImage.getWidth() == 64, "Must be 64 pixels wide", new Object[0]);
						Validate.validState(nativeImage.getHeight() == 64, "Must be 64 pixels high", new Object[0]);
						NativeImageBackedTexture nativeImageBackedTexture = new NativeImageBackedTexture(nativeImage);
						this.client.getTextureManager().registerTexture(this.iconLocation, nativeImageBackedTexture);
						var6 = nativeImageBackedTexture;
					} catch (Throwable var16) {
						var3 = var16;
						throw var16;
					} finally {
						if (inputStream != null) {
							if (var3 != null) {
								try {
									inputStream.close();
								} catch (Throwable var15) {
									var3.addSuppressed(var15);
								}
							} else {
								inputStream.close();
							}
						}
					}
					
					return var6;
				} catch (Throwable var18) {
					GridWorldListWidget.LOGGER.error("Invalid icon for world {}", this.level.getName(), var18);
					this.iconFile = null;
					return null;
				}
			} else {
				this.client.getTextureManager().destroyTexture(this.iconLocation);
				return null;
			}
		}
		
		@Override
		public void close() {
			if (this.icon != null) {
				this.icon.close();
			}
		}
	}
}
