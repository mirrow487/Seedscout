package com.seedscout.gametest;

import com.seedscout.config.SearchMode;
import com.seedscout.config.SeedscoutConfig;
import com.seedscout.ui.CustomizeScreen;
import com.seedscout.ui.SeedFinderScreen;
import com.seedscout.ui.SeedAnalysisScreen;
import com.seedscout.worldgen.SeedAnalysis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import com.seedscout.search.ResolvedRule;
import com.seedscout.search.Rule;
import com.seedscout.search.RuleKind;
import com.seedscout.search.SeedResult;
import com.seedscout.search.SeedSearch;
import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureLocator;
import com.seedscout.worldgen.WorldgenSettings;
import java.util.Set;
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
		check(!require(ctx, "seedscout.button.analyze").active, "empty seed box: Analyze disabled");

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
		ctx.waitTicks(1);
		check(require(ctx, "seedscout.button.analyze").active, "seed typed: Analyze enabled");
		check(!require(ctx, "seedscout.button.finder").visible && !require(ctx, "seedscout.mode.random").visible
			&& !require(ctx, "seedscout.button.customize").visible, "seed typed: finder, mode and Specify hidden");
		ctx.takeScreenshot("stage2_seed_typed");
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

		// Seed search: Speedrun and Nice Terrain must find seeds that really fulfil their required rules.
		ctx.runOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().setSeed(""));
		ctx.waitTicks(1);
		check(require(ctx, "seedscout.button.finder").visible, "seed box cleared: finder visible again");
		click(ctx, require(ctx, "seedscout.mode.random"));
		check(SeedscoutConfig.get().mode == SearchMode.SPEEDRUN, "Speedrun selected for the search test");
		long speedrunSeed = runSearch(ctx, "stage2_search_speedrun", (world, spawn, registries) -> {
			StructureLocator locator = new StructureLocator(world);
			Set<Structure> villages = ResolvedRule.resolve(Rule.structure("x", 400, RuleKind.REQUIRED, 1, "#minecraft:village"), registries).structures();
			Set<Structure> portals = ResolvedRule.resolve(Rule.structure("x", 400, RuleKind.REQUIRED, 1, "#minecraft:ruined_portal"), registries).structures();
			return locator.nearest(villages, spawn, 400) != null && locator.nearest(portals, spawn, 400) != null;
		}, "village and ruined portal within 400 blocks of spawn");

		// "Use Seed" writes it into the seed box (and the mode controls disappear).
		ctx.runOnClient(mc -> ((SeedFinderScreen) mc.gui.screen()).selectForTests(speedrunSeed));
		click(ctx, require(ctx, "seedscout.finder.use"));
		ctx.waitForScreen(CreateWorldScreen.class);
		check(ctx.computeOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().getSeed()).equals(Long.toString(speedrunSeed)),
			"Use Seed puts the seed into the world settings");
		check(ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof EditBox).map(c -> ((EditBox) c).getValue()).toList()).contains(Long.toString(speedrunSeed)),
			"Use Seed shows the seed in the seed box");
		check(!require(ctx, "seedscout.button.finder").visible, "after Use Seed the finder is hidden");
		ctx.takeScreenshot("stage2_seed_used");

		ctx.runOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().setSeed(""));
		ctx.waitTicks(1);
		click(ctx, require(ctx, "seedscout.mode.speedrun"));
		check(SeedscoutConfig.get().mode == SearchMode.SURVIVAL, "Nice Terrain selected for the search test");
		runSearch(ctx, "stage2_search_survival", (world, spawn, registries) -> {
			Set<Biome> forests = ResolvedRule.resolve(Rule.biome("x", 250, RuleKind.REQUIRED, 1, "#minecraft:is_forest"), registries).biomes();
			for (BlockPos.MutableBlockPos p : BlockPos.spiralAround(BlockPos.ZERO, 16, Direction.EAST, Direction.SOUTH)) {
				int x = spawn.getX() + p.getX() * 16;
				int z = spawn.getZ() + p.getZ() * 16;
				for (int y : new int[]{64, 112, 160}) {
					if (forests.contains(world.biome(x, y, z).value())) {
						return true;
					}
				}
			}
			return false;
		}, "a forest within 250 blocks of spawn");

		SeedscoutConfig.get().mode = SearchMode.RANDOM;
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitForScreen(TitleScreen.class);
	}

	interface SeedCheck {
		boolean test(SeedWorld world, BlockPos spawn, net.minecraft.core.RegistryAccess registries);
	}

	/** Opens the finder, searches until a few seeds are found, checks the best one independently. */
	private long runSearch(ClientGameTestContext ctx, String screenshot, SeedCheck seedCheck, String what) {
		click(ctx, require(ctx, "seedscout.button.finder"));
		ctx.waitForScreen(SeedFinderScreen.class);
		click(ctx, require(ctx, "seedscout.finder.start"));
		ctx.waitFor(mc -> mc.gui.screen() instanceof SeedFinderScreen f && f.searchForTests() != null
			&& f.searchForTests().results().size() >= 3, 20 * 180);
		SeedSearch search = ctx.computeOnClient(mc -> ((SeedFinderScreen) mc.gui.screen()).searchForTests());
		ctx.takeScreenshot(screenshot);
		System.out.printf("[Seedscout test] %s: %d seeds tried, %.0f seeds/s on %d threads, %d found%n",
			screenshot, search.tried(), search.rate(), search.threads(), search.results().size());
		click(ctx, require(ctx, "seedscout.finder.stop"));
		ctx.waitFor(mc -> !search.isRunning(), 20 * 30);
		check(search.error() == null, screenshot + ": search ran without error");

		SeedResult best = search.results().getFirst();
		WorldgenSettings settings = ctx.computeOnClient(mc -> {
			Screen finder = mc.gui.screen();
			return WorldgenSettings.fromScreen(((SeedFinderScreen) finder).parentForTests());
		});
		try (settings) {
			SeedWorld world = new SeedWorld(settings, best.seed());
			check(seedCheck.test(world, world.spawnXZ(), settings.registries()), screenshot + ": best seed " + best.seed() + " has " + what);
		}
		return best.seed();
	}
}
