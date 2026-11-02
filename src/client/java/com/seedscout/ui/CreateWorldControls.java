package com.seedscout.ui;

import com.seedscout.config.SearchMode;
import com.seedscout.config.SeedscoutConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/**
 * Builds the extra row that sits right under the seed box in the "World" tab of the Create World screen:
 * <pre>
 *   [ Seed Finder ] [ Mode: Random/Speedrun/Survival ] [ Specify ] [ Analyze ]
 * </pre>
 * The row is 310 px wide, exactly like the vanilla rows of that tab.
 */
public final class CreateWorldControls {
	private static final int ROW_WIDTH = 310;
	private static final int SPACING = 5;
	private static final int FINDER_WIDTH = 80;
	private static final int CUSTOMIZE_WIDTH = 55;
	private static final int ANALYZE_WIDTH = 50;
	private static final int MODE_WIDTH = ROW_WIDTH - FINDER_WIDTH - CUSTOMIZE_WIDTH - ANALYZE_WIDTH - 3 * SPACING;

	/**
	 * Set when we leave the Create World screen for one of our screens. Vanilla rebuilds its tabs on
	 * every {@code init()} and selects the first tab; this flag lets us jump back to the "World" tab.
	 */
	private static boolean returnToWorldTab;

	private CreateWorldControls() {
	}

	public static LinearLayout createRow(CreateWorldScreen screen) {
		SeedscoutConfig config = SeedscoutConfig.get();
		LinearLayout row = LinearLayout.horizontal().spacing(SPACING);

		Button finder = Button.builder(Component.translatable("seedscout.button.finder"), b -> open(screen, new SeedFinderScreen(screen)))
			.width(FINDER_WIDTH)
			.build();
		Button customize = Button.builder(Component.translatable("seedscout.button.customize"), b -> open(screen, new CustomizeScreen(screen)))
			.width(CUSTOMIZE_WIDTH)
			.tooltip(Tooltip.create(Component.translatable("seedscout.button.customize.info")))
			.build();

		CycleButton<SearchMode> mode = CycleButton.builder(SearchMode::displayName, config.mode)
			.withValues(Arrays.asList(SearchMode.values()))
			.withTooltip(m -> Tooltip.create(m.description()))
			.create(0, 0, MODE_WIDTH, 20, Component.translatable("seedscout.button.mode"), (button, value) -> {
				config.mode = value;
				config.save();
				refresh(value, finder, customize);
			});

		// Works in every mode (also "Random"): it only reads, it never changes the world settings.
		Button analyze = Button.builder(Component.translatable("seedscout.button.analyze"), b -> open(screen, new SeedAnalysisScreen(screen)))
			.width(ANALYZE_WIDTH)
			.tooltip(Tooltip.create(Component.translatable("seedscout.button.analyze.info")))
			.build();

		row.addChild(finder);
		row.addChild(mode);
		row.addChild(customize);
		row.addChild(analyze);
		refresh(config.mode, finder, customize);
		return row;
	}

	/** "Random" = vanilla: the finder is disabled and the customize button is hidden. */
	private static void refresh(SearchMode mode, Button finder, Button customize) {
		finder.active = mode.searches();
		finder.setTooltip(Tooltip.create(Component.translatable(
			mode.searches() ? "seedscout.button.finder.info" : "seedscout.button.finder.disabled")));
		customize.visible = mode.searches();
	}

	/** Opens one of our screens on top of the Create World screen. */
	public static void open(CreateWorldScreen parent, Screen screen) {
		returnToWorldTab = true;
		Minecraft.getInstance().gui.setScreen(screen);
	}

	/** Goes back to the Create World screen (its "World" tab). */
	public static void back(CreateWorldScreen parent) {
		returnToWorldTab = true;
		Minecraft.getInstance().gui.setScreen(parent);
	}

	/** Called at the end of {@code CreateWorldScreen.init()}; true once after returning from our screens. */
	public static boolean consumeReturnToWorldTab() {
		boolean value = returnToWorldTab;
		returnToWorldTab = false;
		return value;
	}
}
