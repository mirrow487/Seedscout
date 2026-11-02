package com.seedscout.ui;

import com.seedscout.config.SeedscoutConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * "Customize" screen for the presets of the Speedrun and Survival modes.
 * Stage 1 only has a placeholder; the rule editor is added in stage 4.
 */
public class CustomizeScreen extends Screen {
	private final CreateWorldScreen parent;

	public CustomizeScreen(CreateWorldScreen parent) {
		super(Component.translatable("seedscout.customize.title", SeedscoutConfig.get().mode.displayName()));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
			.bounds(this.width / 2 - 75, this.height - 30, 150, 20)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("seedscout.stage.later"), this.width / 2, this.height / 2, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		CreateWorldControls.back(this.parent);
	}
}
