package com.seedscout.gametest;

import com.seedscout.config.SearchMode;
import com.seedscout.config.SeedscoutConfig;
import com.seedscout.search.SeedSearch;
import com.seedscout.ui.SeedFinderScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import static com.seedscout.gametest.TestUtil.check;
import static com.seedscout.gametest.TestUtil.click;
import static com.seedscout.gametest.TestUtil.require;

/**
 * Long-running search: the finder must stay open, keep finding seeds and not leak memory.
 * Only runs with SEEDSCOUT_ONLY=SoakClientGameTest (duration: SEEDSCOUT_SOAK_SECONDS, default 240).
 */
public class SoakClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!getClass().getSimpleName().equals(System.getenv("SEEDSCOUT_ONLY"))) {
			return;
		}
		int seconds = Integer.parseInt(System.getenv().getOrDefault("SEEDSCOUT_SOAK_SECONDS", "240"));
		SearchMode mode = SearchMode.valueOf(System.getenv().getOrDefault("SEEDSCOUT_SOAK_MODE", "SPEEDRUN"));
		SeedscoutConfig.get().mode = mode;
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));
		ctx.waitForScreen(CreateWorldScreen.class);
		TestUtil.selectWorldTab(ctx);
		click(ctx, require(ctx, "seedscout.button.finder"));
		ctx.waitForScreen(SeedFinderScreen.class);
		click(ctx, require(ctx, "seedscout.finder.start"));
		for (int t = 0; t < seconds; t += 10) {
			ctx.waitTicks(200);
			String screen = ctx.computeOnClient(mc -> String.valueOf(mc.gui.screen()));
			SeedSearch search = ctx.computeOnClient(mc -> mc.gui.screen() instanceof SeedFinderScreen f ? f.searchForTests() : null);
			Runtime rt = Runtime.getRuntime();
			System.out.printf("[Seedscout soak] t=%ds screen=%s tried=%s rate=%s found=%s running=%s error=%s heap=%dMB/%dMB%n",
				t + 10, screen.replaceAll(".*\\.", ""),
				search == null ? "-" : search.tried(), search == null ? "-" : String.format("%.0f", search.rate()),
				search == null ? "-" : search.results().size(), search == null ? "-" : search.isRunning(),
				search == null ? "-" : search.error(),
				(rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20);
			check(search != null, "finder screen still open at " + (t + 10) + "s");
		}
		ctx.runOnClient(mc -> mc.gui.screen().onClose());
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
	}
}
