package com.seedscout.gametest;

import com.seedscout.config.SearchMode;
import com.seedscout.config.SeedscoutConfig;
import com.seedscout.ui.CustomizeScreen;
import com.seedscout.ui.SeedFinderScreen;
import com.seedscout.ui.SeedAnalysisScreen;
import com.seedscout.worldgen.SeedAnalysis;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import static com.seedscout.gametest.TestUtil.check;
import static com.seedscout.gametest.TestUtil.click;
import static com.seedscout.gametest.TestUtil.require;

/**
 * Stage 1: the Seedscout row on the "World" tab of the Create World screen.
 * Random mode = finder disabled and customize hidden; other modes enable both;
 * our screens open and return to the "World" tab.
 */
public class CreateWorldClientGameTest implements FabricClientGameTest {
	/** Everything that would end up in the created world. */
	private static String settingsFingerprint(CreateWorldScreen screen) {
		var ui = screen.getUiState();
		return ui.getSeed() + "|" + ui.getSettings().options().seed() + "|" + ui.getWorldType() + "|" + ui.isGenerateStructures()
			+ "|" + ui.getSettings().dataConfiguration().dataPacks().getEnabled();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String only = System.getenv("SEEDSCOUT_ONLY");
		if (only != null && !only.equals(getClass().getSimpleName())) {
			return;
		}
		SeedscoutConfig.get().mode = SearchMode.RANDOM;
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));
		ctx.waitForScreen(CreateWorldScreen.class);
		TestUtil.selectWorldTab(ctx);

		AbstractWidget finder = require(ctx, "seedscout.button.finder");
		AbstractWidget customize = require(ctx, "seedscout.button.customize");
		AbstractWidget mode = require(ctx, "seedscout.mode.random");
		ctx.takeScreenshot("stage1_world_tab_random");
		check(!finder.active, "Random mode: finder button disabled");
		check(!customize.visible, "Random mode: customize button hidden");

		click(ctx, mode);
		check(SeedscoutConfig.get().mode == SearchMode.SPEEDRUN, "mode button cycles to Speedrun");
		check(finder.active && customize.visible, "Speedrun mode: finder enabled, customize visible");
		ctx.takeScreenshot("stage1_world_tab_speedrun");

		click(ctx, finder);
		ctx.waitForScreen(SeedFinderScreen.class);
		ctx.takeScreenshot("stage1_finder_screen");
		click(ctx, require(ctx, "gui.back"));
		ctx.waitForScreen(CreateWorldScreen.class);
		check(TestUtil.widget(ctx, "seedscout.button.finder").isPresent(), "back from finder lands on the World tab");

		click(ctx, require(ctx, "seedscout.button.customize"));
		ctx.waitForScreen(CustomizeScreen.class);
		ctx.takeScreenshot("stage1_customize_screen");
		click(ctx, require(ctx, "gui.back"));
		ctx.waitForScreen(CreateWorldScreen.class);

		click(ctx, require(ctx, "seedscout.mode.speedrun"));
		check(SeedscoutConfig.get().mode == SearchMode.SURVIVAL, "mode button cycles to Survival");
		click(ctx, require(ctx, "seedscout.mode.survival"));
		check(SeedscoutConfig.get().mode == SearchMode.RANDOM, "mode button cycles back to Random");
		ctx.takeScreenshot("stage1_world_tab_back_to_random");

		// Stage 2: the analysis works in Random mode too and does not change any setting.
		ctx.runOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().setSeed("8675309"));
		String settingsBefore = ctx.computeOnClient(mc -> settingsFingerprint((CreateWorldScreen) mc.gui.screen()));
		click(ctx, require(ctx, "seedscout.button.analyze"));
		ctx.waitForScreen(SeedAnalysisScreen.class);
		ctx.waitFor(mc -> mc.gui.screen() instanceof SeedAnalysisScreen s && s.analysisForTests().finished(), 20 * 120);
		SeedAnalysis analysis = ctx.computeOnClient(mc -> ((SeedAnalysisScreen) mc.gui.screen()).analysisForTests());
		check(analysis.error() == null, "analysis finished without error");
		check(analysis.seed() == 8675309L, "analysis uses the seed of the seed box");
		check(new BlockPos(16, 64, 80).equals(analysis.spawn()), "analysis spawn = real spawn of seed 8675309 (16 64 80)");
		check(analysis.structures().stream().anyMatch(e -> e.hit() != null && e.hit().pos().getX() == -304 && e.hit().pos().getZ() == -320),
			"analysis lists village_plains at -304 -320 like /locate");
		check(!analysis.biomes().isEmpty(), "analysis lists surface biomes");
		ctx.takeScreenshot("stage2_analysis");
		click(ctx, require(ctx, "gui.back"));
		ctx.waitForScreen(CreateWorldScreen.class);
		check(settingsBefore.equals(ctx.computeOnClient(mc -> settingsFingerprint((CreateWorldScreen) mc.gui.screen()))),
			"analysis did not change the world settings");

		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitForScreen(TitleScreen.class);
	}
}
