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
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/**
 * Builds the extra row that sits right under the seed box in the "World" tab of the Create World screen:
 * <pre>
 *   [ Analyze ] [ Seed Finder ] [ Mode: Random/Speedrun/Survival ] [ Specify ]
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

	private CreateWorldControls() {
	}

	public static LinearLayout createRow(CreateWorldScreen screen) {
		SeedscoutConfig config = SeedscoutConfig.get();
		WorldCreationUiState uiState = screen.getUiState();
		LinearLayout row = LinearLayout.horizontal().spacing(SPACING);

		// Analyze: only for a seed the player typed (it only reads, it never changes the world settings).
		Button analyze = Button.builder(Component.translatable("seedscout.button.analyze"),
				b -> open(screen, SeedAnalysisScreen.forSeedBox(screen)))
			.width(ANALYZE_WIDTH)
			.build();
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
				refresh(uiState, value, analyze, finder, button, customize);
			});

		row.addChild(analyze);
		row.addChild(finder);
		row.addChild(mode);
		row.addChild(customize);
		// Typing in the seed box changes which controls make sense.
		uiState.addListener(ui -> refresh(ui, mode.getValue(), analyze, finder, mode, customize));
		refresh(uiState, config.mode, analyze, finder, mode, customize);
		return row;
	}

	/**
	 * <ul>
	 *   <li>A seed is typed: the world is decided, so the finder, the mode and "Specify" are hidden;
	 *       only "Analyze" is available.</li>
	 *   <li>Seed box empty: "Analyze" is disabled (there is no seed to analyse yet). "Random" = vanilla:
	 *       the finder is disabled and "Specify" is hidden.</li>
	 * </ul>
	 */
	private static void refresh(WorldCreationUiState uiState, SearchMode value, Button analyze, Button finder, CycleButton<SearchMode> mode, Button customize) {
		boolean seedTyped = !uiState.getSeed().isBlank();
		analyze.active = seedTyped;
		analyze.setTooltip(Tooltip.create(Component.translatable(seedTyped ? "seedscout.button.analyze.info" : "seedscout.button.analyze.disabled")));
		finder.visible = !seedTyped;
		mode.visible = !seedTyped;
		customize.visible = !seedTyped && value.searches();
		finder.active = value.searches();
		finder.setTooltip(Tooltip.create(Component.translatable(
			value.searches() ? "seedscout.button.finder.info" : "seedscout.button.finder.disabled")));
	}

	/** Opens one of our screens on top of the Create World screen. */
	public static void open(CreateWorldScreen parent, Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	/**
	 * Goes back to the Create World screen. Since 26.x a screen keeps its widgets (and the selected tab)
	 * when it is shown again, so the player lands on the "World" tab they came from.
	 */
	public static void back(CreateWorldScreen parent) {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
