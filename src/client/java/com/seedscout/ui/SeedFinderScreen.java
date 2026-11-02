package com.seedscout.ui;

import com.seedscout.config.SeedscoutConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Seed finder screen. Stage 1: shows the selected mode and the world settings the search will use.
 * The actual multi-threaded search is added in stage 4.
 */
public class SeedFinderScreen extends Screen {
	private final CreateWorldScreen parent;
	private List<Component> summary = List.of();

	public SeedFinderScreen(CreateWorldScreen parent) {
		super(Component.translatable("seedscout.finder.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.summary = WorldSettingsSummary.lines(this.parent.getUiState());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
			.bounds(this.width / 2 - 75, this.height - 30, 150, 20)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
		int y = 40;
		graphics.text(this.font, Component.translatable("seedscout.summary.mode", SeedscoutConfig.get().mode.displayName()), 20, y, 0xFFFFFF55);
		y += 16;
		for (Component line : this.summary) {
			y = 4 + graphics.textWithWordWrap(this.font, line, 20, y, this.width - 40, 0xFFE0E0E0);
		}
		graphics.text(this.font, Component.translatable("seedscout.stage.later"), 20, y + 10, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		CreateWorldControls.back(this.parent);
	}
}
