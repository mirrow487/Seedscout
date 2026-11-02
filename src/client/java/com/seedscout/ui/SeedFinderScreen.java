package com.seedscout.ui;

import com.seedscout.config.SearchMode;
import com.seedscout.config.SeedscoutConfig;
import com.seedscout.search.Rule;
import com.seedscout.search.RuleKind;
import com.seedscout.search.SearchPreset;
import com.seedscout.search.SeedResult;
import com.seedscout.search.SeedSearch;
import com.seedscout.search.TerrainStats;
import com.seedscout.worldgen.WorldgenSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The seed finder: searches random seeds on background threads with the preset of the selected mode,
 * shows live statistics and the best seeds (by score). A found seed can be analysed, copied or put
 * into the seed box of the Create World screen.
 */
public class SeedFinderScreen extends Screen {
	private static final int ROW_HEIGHT = 12;

	private final CreateWorldScreen parent;
	private final SearchMode mode;
	private final SearchPreset preset;
	private @Nullable SeedSearch search;
	private @Nullable Long selected;
	private double scroll;
	private Button searchButton;
	private Button useButton;
	private Button analyzeButton;
	private Button copyButton;
	private int listTop;

	public SeedFinderScreen(CreateWorldScreen parent) {
		super(Component.translatable("seedscout.finder.title"));
		this.parent = parent;
		this.mode = SeedscoutConfig.get().mode;
		this.preset = SearchPreset.defaults(this.mode);
	}

	@Override
	protected void init() {
		this.listTop = 78;
		this.searchButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> toggleSearch())
			.bounds(10, 50, 90, 20).build());
		int y = this.height - 28;
		int w = 74;
		int x = this.width / 2 - (4 * w + 3 * 4) / 2;
		this.useButton = this.addRenderableWidget(Button.builder(Component.translatable("seedscout.finder.use"), b -> useSelected())
			.bounds(x, y, w, 20).build());
		this.analyzeButton = this.addRenderableWidget(Button.builder(Component.translatable("seedscout.button.analyze"), b -> analyzeSelected())
			.bounds(x + (w + 4), y, w, 20).build());
		this.copyButton = this.addRenderableWidget(Button.builder(Component.translatable("seedscout.analysis.copy_seed"), b -> copySelected())
			.bounds(x + 2 * (w + 4), y, w, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
			.bounds(x + 3 * (w + 4), y, w, 20).build());
		updateButtons();
	}

	private void toggleSearch() {
		if (this.search != null && this.search.isRunning()) {
			this.search.stop();
		} else {
			this.search = SeedSearch.start(WorldgenSettings.fromScreen(this.parent), this.preset, SeedscoutConfig.get().effectiveThreads());
			this.scroll = 0;
		}
		updateButtons();
	}

	private void updateButtons() {
		boolean running = this.search != null && this.search.isRunning();
		this.searchButton.setMessage(Component.translatable(running ? "seedscout.finder.stop" : "seedscout.finder.start"));
		this.searchButton.active = !(this.search != null && this.search.isStopping());
		boolean hasSelection = this.selected != null;
		this.useButton.active = hasSelection;
		this.analyzeButton.active = hasSelection;
		this.copyButton.active = hasSelection;
	}

	@Override
	public void tick() {
		updateButtons();
	}

	private void useSelected() {
		if (this.selected != null) {
			stopSearch();
			this.parent.getUiState().setSeed(Long.toString(this.selected));
			CreateWorldControls.back(this.parent);
		}
	}

	private void analyzeSelected() {
		if (this.selected != null) {
			this.minecraft.gui.setScreen(new SeedAnalysisScreen(this.parent, this, this.selected));
		}
	}

	private void copySelected() {
		if (this.selected != null) {
			this.minecraft.keyboardHandler.setClipboard(Long.toString(this.selected));
		}
	}

	private void stopSearch() {
		if (this.search != null) {
			this.search.stop();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, Component.translatable("seedscout.finder.title_mode", this.mode.displayName()), this.width / 2, 8, 0xFFFFFFFF);
		graphics.textWithWordWrap(this.font, rulesSummary(), 10, 22, this.width - 20, 0xFFC0C0C0);

		// Live statistics
		Component stats;
		if (this.search == null) {
			stats = Component.translatable("seedscout.finder.idle", SeedscoutConfig.get().effectiveThreads());
		} else {
			stats = Component.translatable("seedscout.finder.stats",
				String.format("%,d", this.search.tried()), String.format("%,.0f", this.search.rate()), this.search.threads(), this.search.results().size());
			if (this.search.error() != null) {
				stats = Component.translatable("seedscout.analysis.error", String.valueOf(this.search.error().getMessage()));
			}
		}
		graphics.text(this.font, stats, 108, 56, 0xFFFFFF55);

		// Results, best first
		List<SeedResult> results = this.search == null ? List.of() : this.search.results();
		int bottom = this.height - 34;
		int visible = (bottom - this.listTop) / ROW_HEIGHT;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, results.size() - visible));
		int first = (int) this.scroll;
		graphics.enableScissor(0, this.listTop, this.width, bottom);
		for (int i = first; i < results.size() && i < first + visible + 1; i++) {
			SeedResult r = results.get(i);
			int y = this.listTop + (i - first) * ROW_HEIGHT;
			boolean isSelected = this.selected != null && this.selected == r.seed();
			boolean hovered = mouseY >= y && mouseY < y + ROW_HEIGHT && mouseY < bottom;
			if (isSelected) {
				graphics.fill(6, y - 1, this.width - 6, y + ROW_HEIGHT - 1, 0x60FFFFFF);
			} else if (hovered) {
				graphics.fill(6, y - 1, this.width - 6, y + ROW_HEIGHT - 1, 0x30FFFFFF);
			}
			if (hovered) {
				graphics.setComponentTooltipForNextFrame(this.font, tooltip(r), mouseX, mouseY);
			}
			// columns: rank, score, seed (room for the longest possible seed), what was found
			graphics.text(this.font, (i + 1) + ".", 10, y, 0xFFA0A0A0);
			graphics.text(this.font, String.format("%.1f", r.score()), 30, y, 0xFF80FF80);
			graphics.text(this.font, Long.toString(r.seed()), 58, y, 0xFFFFFFFF);
			graphics.text(this.font, details(r), 58 + this.font.width("-9223372036854775808") + 8, y, 0xFFB0E0FF);
		}
		graphics.disableScissor();
		if (this.search != null && results.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("seedscout.finder.nothing_yet"), this.width / 2, this.listTop + 20, 0xFFA0A0A0);
		}
	}

	/** "Required: Village ≤400, Ruined Portal ≤400 · Preferred: …" */
	private Component rulesSummary() {
		MutableComponent out = Component.empty();
		for (RuleKind kind : RuleKind.values()) {
			MutableComponent part = Component.empty();
			for (Rule rule : this.preset.rules()) {
				if (rule.kind() == kind) {
					if (!part.getSiblings().isEmpty()) {
						part.append(", ");
					}
					part.append(Component.translatable(rule.label())).append(" ≤" + rule.maxDistance());
				}
			}
			if (!part.getSiblings().isEmpty()) {
				if (!out.getSiblings().isEmpty()) {
					out.append("  ·  ");
				}
				out.append(Component.translatable("seedscout.kind." + kind.name().toLowerCase())).append(": ").append(part);
			}
		}
		if (this.preset.terrain() != null) {
			out.append("  ·  ").append(Component.translatable("seedscout.finder.terrain_scored"));
		}
		return out;
	}

	/** "Village 120 b · Ruined Portal 80 b · water 12% …" */
	private static Component details(SeedResult r) {
		MutableComponent out = Component.empty();
		for (SeedResult.Match m : r.matches()) {
			if (!out.getSiblings().isEmpty()) {
				out.append(" · ");
			}
			out.append(Component.translatable(m.label())).append(" " + m.distance());
		}
		TerrainStats t = r.terrain();
		if (t != null) {
			if (!out.getSiblings().isEmpty()) {
				out.append(" · ");
			}
			out.append(Component.translatable("seedscout.finder.terrain",
				Math.round(t.water() * 100), t.relief(), t.mountain(), Math.round(t.flat() * 100)));
		}
		return out;
	}

	/** Full details of a result, one line each (the row itself may be cut off on small windows). */
	private static List<Component> tooltip(SeedResult r) {
		List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.translatable("seedscout.analysis.seed", r.seed()));
		for (SeedResult.Match m : r.matches()) {
			lines.add(Component.translatable(m.label()).append(" — ").append(Component.translatable("seedscout.finder.blocks", m.distance())));
		}
		TerrainStats t = r.terrain();
		if (t != null) {
			lines.add(Component.translatable("seedscout.finder.terrain", Math.round(t.water() * 100), t.relief(), t.mountain(), Math.round(t.flat() * 100)));
		}
		lines.add(Component.translatable("seedscout.finder.row_hint"));
		return lines;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll -= scrollY * 3;
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (this.search != null && event.y() >= this.listTop && event.y() < this.height - 34) {
			int index = (int) this.scroll + (int) ((event.y() - this.listTop) / ROW_HEIGHT);
			List<SeedResult> results = this.search.results();
			if (index >= 0 && index < results.size()) {
				this.selected = results.get(index).seed();
				if (doubleClick) {
					analyzeSelected();
				}
				updateButtons();
				return true;
			}
		}
		return false;
	}

	/** For the automated client tests. */
	public @Nullable SeedSearch searchForTests() {
		return this.search;
	}

	public CreateWorldScreen parentForTests() {
		return this.parent;
	}

	public void selectForTests(long seed) {
		this.selected = seed;
		updateButtons();
	}

	@Override
	public void onClose() {
		stopSearch();
		CreateWorldControls.back(this.parent);
	}
}
