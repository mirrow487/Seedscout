package com.seedscout.ui;

import com.seedscout.util.Names;
import com.seedscout.worldgen.SeedAnalysis;
import com.seedscout.worldgen.StructureHit;
import com.seedscout.worldgen.WorldgenSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 2: text analysis of the seed that the Create World screen will use — spawn, nearest structure
 * of every type and nearest surface biomes. Clicking a row copies the matching {@code /locate} command
 * (run from spawn), so the numbers can be checked in the created world.
 * The map preview of stage 3 replaces/extends this screen.
 */
public class SeedAnalysisScreen extends Screen {
	private static final int ROW_HEIGHT = 12;
	private static final int LIST_TOP = 62;

	private final CreateWorldScreen parent;
	private final SeedAnalysis analysis;
	private final Component worldType;
	private final boolean structuresOn;
	private final boolean randomSeed;
	private double scroll;
	private @Nullable Component copiedMessage;
	private long copiedUntil;

	/** One line of the list. */
	private record Row(Component name, Component distance, Component coords, @Nullable String command, boolean header) {
	}

	public SeedAnalysisScreen(CreateWorldScreen parent) {
		super(Component.translatable("seedscout.analysis.title"));
		this.parent = parent;
		WorldCreationUiState uiState = parent.getUiState();
		this.worldType = uiState.getWorldType().describePreset();
		this.structuresOn = uiState.isGenerateStructures();
		this.randomSeed = uiState.getSeed().isBlank();
		// The exact seed the new world will get (also when the seed box is empty: vanilla already picked one).
		long seed = uiState.getSettings().options().seed();
		this.analysis = SeedAnalysis.start(WorldgenSettings.fromScreen(parent), seed);
	}

	@Override
	protected void init() {
		int y = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.translatable("seedscout.analysis.copy_seed"), b -> copy(Long.toString(this.analysis.seed())))
			.bounds(this.width / 2 - 154, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("seedscout.analysis.copy_report"), b -> copy(report()))
			.bounds(this.width / 2 - 50, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
			.bounds(this.width / 2 + 54, y, 100, 20).build());
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		BlockPos spawn = this.analysis.spawn();
		if (spawn == null) {
			return rows;
		}
		String at = "/execute positioned " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ() + " run ";
		rows.add(new Row(Component.translatable("seedscout.analysis.structures"), Component.empty(), Component.empty(), null, true));
		if (!this.structuresOn) {
			rows.add(new Row(Component.translatable("seedscout.analysis.structures_off"), Component.empty(), Component.empty(), null, false));
		}
		for (SeedAnalysis.StructureEntry e : this.analysis.structures()) {
			StructureHit hit = e.hit();
			String command = at + "locate structure " + Names.id(e.structure());
			if (hit == null) {
				rows.add(new Row(Names.structure(e.structure()), Component.translatable("seedscout.analysis.not_found"), Component.empty(), command, false));
			} else {
				rows.add(new Row(Names.structure(e.structure()), distance(spawn, hit.pos()), coords(hit.pos()), command, false));
			}
		}
		if (!this.analysis.biomes().isEmpty()) {
			rows.add(new Row(Component.translatable("seedscout.analysis.biomes", SeedAnalysis.BIOME_RADIUS), Component.empty(), Component.empty(), null, true));
			for (SeedAnalysis.BiomeEntry e : this.analysis.biomes()) {
				rows.add(new Row(Names.biome(e.biome()), distance(spawn, e.pos()), coords(e.pos()), at + "locate biome " + Names.id(e.biome()), false));
			}
		}
		return rows;
	}

	private static Component distance(BlockPos from, BlockPos to) {
		int blocks = (int) Math.round(Math.sqrt(Mth.square((double) to.getX() - from.getX()) + Mth.square((double) to.getZ() - from.getZ())));
		return Component.translatable("seedscout.analysis.distance", blocks, Names.direction(from, to));
	}

	private static Component coords(BlockPos pos) {
		return Component.literal("x " + pos.getX() + ", z " + pos.getZ());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

		Component seedText = this.randomSeed
			? Component.translatable("seedscout.analysis.seed_random", this.analysis.seed())
			: Component.translatable("seedscout.analysis.seed", this.analysis.seed());
		graphics.text(this.font, seedText, 10, 22, 0xFFFFFF55);
		graphics.text(this.font, Component.translatable("seedscout.summary.world_type", this.worldType), this.width / 2 + 10, 22, 0xFFE0E0E0);

		BlockPos spawn = this.analysis.spawn();
		if (spawn != null) {
			Component biome = this.analysis.spawnBiome() == null ? Component.empty() : Names.biome(this.analysis.spawnBiome());
			graphics.text(this.font, Component.translatable("seedscout.analysis.spawn", spawn.getX(), spawn.getY(), spawn.getZ(), biome), 10, 34, 0xFFE0E0E0);
		}
		graphics.text(this.font, status(), 10, 46, 0xFFA0A0A0);

		// Scrollable list
		List<Row> rows = rows();
		int bottom = this.height - 34;
		int visible = (bottom - LIST_TOP) / ROW_HEIGHT;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, rows.size() - visible));
		int first = (int) this.scroll;
		int distX = Math.max(170, this.width * 45 / 100);
		int coordX = Math.max(290, this.width * 72 / 100);
		graphics.enableScissor(0, LIST_TOP, this.width, bottom);
		for (int i = first; i < rows.size() && i < first + visible + 1; i++) {
			Row row = rows.get(i);
			int y = LIST_TOP + (i - first) * ROW_HEIGHT;
			boolean hovered = row.command() != null && mouseY >= y && mouseY < y + ROW_HEIGHT && mouseY < bottom;
			if (hovered) {
				graphics.fill(6, y - 1, this.width - 6, y + ROW_HEIGHT - 1, 0x40FFFFFF);
				graphics.setComponentTooltipForNextFrame(this.font,
					List.of(Component.translatable("seedscout.analysis.click_to_copy"), Component.literal(row.command())), mouseX, mouseY);
			}
			if (row.header()) {
				graphics.text(this.font, row.name(), 10, y, 0xFFFFD27F);
			} else {
				graphics.text(this.font, row.name(), 16, y, 0xFFFFFFFF);
				graphics.text(this.font, row.distance(), distX, y, 0xFFB0E0FF);
				graphics.text(this.font, row.coords(), coordX, y, 0xFFA0A0A0);
			}
		}
		graphics.disableScissor();

		if (this.copiedMessage != null && System.currentTimeMillis() < this.copiedUntil) {
			graphics.centeredText(this.font, this.copiedMessage, this.width / 2, this.height - 42, 0xFF80FF80);
		}
	}

	private Component status() {
		if (this.analysis.error() != null) {
			return Component.translatable("seedscout.analysis.error", String.valueOf(this.analysis.error().getMessage()));
		}
		if (this.analysis.finished()) {
			return Component.translatable("seedscout.analysis.done", String.format("%.1f", this.analysis.millis() / 1000.0));
		}
		return Component.translatable("seedscout.analysis.working", this.analysis.structuresDone(), this.analysis.structuresTotal());
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
		int bottom = this.height - 34;
		if (event.y() >= LIST_TOP && event.y() < bottom) {
			int index = (int) this.scroll + (int) ((event.y() - LIST_TOP) / ROW_HEIGHT);
			List<Row> rows = rows();
			if (index >= 0 && index < rows.size() && rows.get(index).command() != null) {
				copy(rows.get(index).command());
				return true;
			}
		}
		return false;
	}

	private void copy(String text) {
		this.minecraft.keyboardHandler.setClipboard(text);
		this.copiedMessage = Component.translatable("seedscout.analysis.copied");
		this.copiedUntil = System.currentTimeMillis() + 2000;
	}

	/** Plain text report of everything on the screen (for sharing / comparing). */
	private String report() {
		StringBuilder out = new StringBuilder();
		out.append("Seed: ").append(this.analysis.seed()).append('\n');
		out.append(Component.translatable("seedscout.summary.world_type", this.worldType).getString()).append('\n');
		BlockPos spawn = this.analysis.spawn();
		if (spawn != null) {
			out.append("Spawn: ").append(spawn.getX()).append(' ').append(spawn.getY()).append(' ').append(spawn.getZ()).append('\n');
		}
		for (Row row : rows()) {
			if (row.header()) {
				out.append('\n').append(row.name().getString()).append('\n');
			} else {
				out.append(row.name().getString()).append(" — ").append(row.distance().getString()).append(" (").append(row.coords().getString()).append(")\n");
			}
		}
		return out.toString();
	}

	/** For the automated client tests. */
	public SeedAnalysis analysisForTests() {
		return this.analysis;
	}

	@Override
	public void removed() {
		this.analysis.cancel();
	}

	@Override
	public void onClose() {
		CreateWorldControls.back(this.parent);
	}
}
