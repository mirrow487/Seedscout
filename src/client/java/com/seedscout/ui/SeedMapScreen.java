package com.seedscout.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.seedscout.config.SeedscoutConfig;
import com.seedscout.map.MapSession;
import com.seedscout.map.MapStructure;
import com.seedscout.map.MapTile;
import com.seedscout.map.MapTileTextures;
import com.seedscout.map.StructureIcons;
import com.seedscout.util.Names;
import com.seedscout.worldgen.SeedAnalysis;
import com.seedscout.worldgen.StructureHit;
import com.seedscout.worldgen.WorldgenSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunkbase-style preview of a seed: coloured biome map with optional height shading, structure icons
 * (hover for name and distance), spawn marker, distance rings, and a panel with the nearest structures
 * and biomes (click one to move the map there; right click copies its /locate command).
 * <p>
 * Everything is computed in the background ({@link MapSession}, {@link SeedAnalysis}); the screen only
 * draws what is ready. Zoomed out it uses coarse tiles, zoomed in it asks for finer ones, and until those
 * arrive the coarser tiles stay visible underneath.
 */
public class SeedMapScreen extends Screen {
	private static final int TOP = 25;
	private static final int BOTTOM_BAR = 30;
	private static final int PANEL_ROW = 21;
	private static final int[] RINGS = {100, 300, 500, 1000};
	/** Finest and coarsest tile levels (2 and 256 blocks per pixel). */
	private static final int MIN_LEVEL = 1;
	private static final int MAX_LEVEL = 8;
	private static final int MAX_UPLOADS_PER_FRAME = 10;
	private static final int MAX_TEXTURES = 320;

	private final CreateWorldScreen createWorldScreen;
	private final Screen back;
	private final @Nullable Runnable useSeed;
	private final MapSession session;
	private final SeedAnalysis analysis;
	private final MapTileTextures textures;
	private final Component worldType;

	// View: world position at the map centre, and blocks per GUI pixel.
	private double centerX;
	private double centerZ;
	private double blocksPerPixel = 4;
	private boolean shading;
	private boolean rings = true;
	private boolean dragging;
	private double panelScroll;
	private long frame;
	/** Structures sorted by importance (rarest first), rebuilt when new ones arrive. */
	private List<MapStructure> byImportance = List.of();
	private @Nullable Component message;
	private long messageUntil;

	/** One panel row: a structure or a biome with its position. */
	private record PanelRow(Component name, BlockPos pos, @Nullable String command, boolean header) {
	}

	/**
	 * @param createWorldScreen whose settings (world type, data packs, structures) are used
	 * @param back              screen to return to
	 * @param seed              the seed to show
	 * @param useSeed           "Use Seed" action, or null when the seed already is the one in the seed box
	 */
	public SeedMapScreen(CreateWorldScreen createWorldScreen, Screen back, long seed, @Nullable Runnable useSeed) {
		super(Component.translatable("seedscout.map.title"));
		this.createWorldScreen = createWorldScreen;
		this.back = back;
		this.useSeed = useSeed;
		SeedscoutConfig config = SeedscoutConfig.get();
		this.session = new MapSession(WorldgenSettings.fromScreen(createWorldScreen), seed, config.mapRadius, config.effectiveThreads());
		this.analysis = SeedAnalysis.start(WorldgenSettings.fromScreen(createWorldScreen), seed);
		this.textures = new MapTileTextures(seed);
		this.worldType = createWorldScreen.getUiState().getWorldType().describePreset();
		this.centerX = this.session.spawn().getX();
		this.centerZ = this.session.spawn().getZ();
	}

	/** Map of the seed typed into the seed box. */
	public static SeedMapScreen forSeedBox(CreateWorldScreen screen) {
		return new SeedMapScreen(screen, screen, screen.getUiState().getSettings().options().seed(), null);
	}

	// ------------------------------------------------------------------ layout

	private int panelWidth() {
		return Mth.clamp(this.width / 3, 120, 180);
	}

	private int mapLeft() {
		return 0;
	}

	private int mapRight() {
		return this.width - panelWidth();
	}

	private int mapTop() {
		return TOP;
	}

	private int mapBottom() {
		return this.height - BOTTOM_BAR;
	}

	private double mapMidX() {
		return (mapLeft() + mapRight()) / 2.0;
	}

	private double mapMidY() {
		return (mapTop() + mapBottom()) / 2.0;
	}

	private double toScreenX(double blockX) {
		return mapMidX() + (blockX - this.centerX) / this.blocksPerPixel;
	}

	private double toScreenY(double blockZ) {
		return mapMidY() + (blockZ - this.centerZ) / this.blocksPerPixel;
	}

	private double toBlockX(double screenX) {
		return this.centerX + (screenX - mapMidX()) * this.blocksPerPixel;
	}

	private double toBlockZ(double screenY) {
		return this.centerZ + (screenY - mapMidY()) * this.blocksPerPixel;
	}

	private boolean inMap(double x, double y) {
		return x >= mapLeft() && x < mapRight() && y >= mapTop() && y < mapBottom();
	}

	private double maxBlocksPerPixel() {
		return Math.max(4, this.session.radius() * 2.2 / Math.max(1, mapRight() - mapLeft()));
	}

	@Override
	protected void init() {
		int y = this.height - 25;
		int x = 6;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.shading)
			.create(x, y, 96, 20, Component.translatable("seedscout.map.shading"), (b, v) -> this.shading = v));
		x += 100;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.rings)
			.create(x, y, 96, 20, Component.translatable("seedscout.map.rings"), (b, v) -> this.rings = v));
		x += 100;
		if (this.useSeed != null) {
			this.addRenderableWidget(Button.builder(Component.translatable("seedscout.finder.use"), b -> {
					dispose();
					this.useSeed.run();
				})
				.bounds(x, y, 80, 20).build());
			x += 84;
		}
		this.addRenderableWidget(Button.builder(Component.translatable("seedscout.analysis.copy_seed"), b -> copy(Long.toString(this.session.seed())))
			.bounds(x, y, 80, 20).build());
		x += 84;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
			.bounds(Math.max(x, this.width - 66), y, 60, 20).build());
	}

	// --------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.frame++;
		this.blocksPerPixel = Mth.clamp(this.blocksPerPixel, 0.25, maxBlocksPerPixel());

		graphics.fill(mapLeft(), mapTop(), mapRight(), mapBottom(), 0xFF101418);
		graphics.enableScissor(mapLeft(), mapTop(), mapRight(), mapBottom());
		drawTiles(graphics);
		if (this.rings) {
			drawRings(graphics);
		}
		drawRadiusLimit(graphics);
		MapStructure hovered = drawStructures(graphics, mouseX, mouseY);
		drawSpawn(graphics);
		graphics.disableScissor();
		this.textures.evict(this.session, MAX_TEXTURES, this.frame);

		drawHeader(graphics, mouseX, mouseY);
		drawPanel(graphics, mouseX, mouseY);

		if (hovered != null) {
			StructureHit hit = hovered.hit();
			BlockPos spawn = this.session.spawn();
			graphics.setComponentTooltipForNextFrame(this.font, List.of(
				Names.structure(hit.structure()),
				distanceText(spawn, hit.pos()),
				Component.literal("x " + hit.pos().getX() + ", z " + hit.pos().getZ()).withStyle(s -> s.withColor(0xA0A0A0))
			), mouseX, mouseY);
		} else if (inMap(mouseX, mouseY) && !this.dragging) {
			// Cursor coordinates in the corner
			graphics.text(this.font, "x " + (int) Math.floor(toBlockX(mouseX)) + ", z " + (int) Math.floor(toBlockZ(mouseY)),
				mapLeft() + 4, mapBottom() - 11, 0xFFFFFFFF, true);
		}
		if (this.message != null && System.currentTimeMillis() < this.messageUntil) {
			graphics.centeredText(this.font, this.message, (mapLeft() + mapRight()) / 2, mapBottom() - 12, 0xFF80FF80);
		}
	}

	/** Level whose pixels are about one screen (framebuffer) pixel. */
	private int wantedLevel() {
		double guiScale = this.minecraft.getWindow().getGuiScale();
		double blocksPerScreenPixel = this.blocksPerPixel / guiScale;
		int level = (int) Math.floor(Math.log(Math.max(1e-6, blocksPerScreenPixel)) / Math.log(2));
		return Mth.clamp(level, MIN_LEVEL, MAX_LEVEL);
	}

	private void drawTiles(GuiGraphicsExtractor graphics) {
		int fine = wantedLevel();
		int coarse = Math.min(MAX_LEVEL, fine + 3);
		double minX = toBlockX(mapLeft());
		double maxX = toBlockX(mapRight());
		double minZ = toBlockZ(mapTop());
		double maxZ = toBlockZ(mapBottom());
		int uploads = 0;

		// 1) Ask for tiles: coarse first (fast, gives a full picture quickly), then fine ones from the centre out.
		for (int level : new int[]{coarse, fine}) {
			for (MapTile tile : visibleTiles(level, minX, maxX, minZ, maxZ)) {
				if (!this.session.request(tile, this.shading)) {
					break;
				}
			}
		}
		// 2) Draw: coarse levels underneath, finer on top.
		for (int level = coarse; level >= fine; level--) {
			for (MapTile tile : visibleTiles(level, minX, maxX, minZ, maxZ)) {
				if (!tile.hasBiomes()) {
					continue;
				}
				if (this.textures.needsUpload(tile, this.shading) && uploads < MAX_UPLOADS_PER_FRAME) {
					this.textures.upload(tile, this.shading);
					uploads++;
				}
				if (this.textures.id(tile) == null) {
					continue;
				}
				long blocks = MapTile.blocks(level);
				double sx = toScreenX((double) tile.tileX * blocks);
				double sy = toScreenY((double) tile.tileZ * blocks);
				float scale = (float) (blocks / this.blocksPerPixel / MapTile.SIZE);
				graphics.pose().pushMatrix();
				graphics.pose().translate((float) sx, (float) sy);
				graphics.pose().scale(scale, scale);
				graphics.blit(RenderPipelines.GUI_TEXTURED, this.textures.id(tile), 0, 0, 0, 0, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE);
				graphics.pose().popMatrix();
				this.textures.markDrawn(tile, this.frame);
			}
		}
	}

	/** Visible tiles of a level, nearest to the map centre first. */
	private List<MapTile> visibleTiles(int level, double minX, double maxX, double minZ, double maxZ) {
		long blocks = MapTile.blocks(level);
		int radius = this.session.radius() + (int) blocks;
		BlockPos spawn = this.session.spawn();
		int tx0 = (int) Math.floorDiv((long) Math.floor(Math.max(minX, spawn.getX() - radius)), blocks);
		int tx1 = (int) Math.floorDiv((long) Math.floor(Math.min(maxX, spawn.getX() + radius)), blocks);
		int tz0 = (int) Math.floorDiv((long) Math.floor(Math.max(minZ, spawn.getZ() - radius)), blocks);
		int tz1 = (int) Math.floorDiv((long) Math.floor(Math.min(maxZ, spawn.getZ() + radius)), blocks);
		List<MapTile> list = new ArrayList<>();
		for (int tx = tx0; tx <= tx1; tx++) {
			for (int tz = tz0; tz <= tz1; tz++) {
				list.add(this.session.tile(level, tx, tz));
			}
		}
		double cx = this.centerX / blocks - 0.5;
		double cz = this.centerZ / blocks - 0.5;
		list.sort((p, q) -> Double.compare(Mth.square(p.tileX - cx) + Mth.square(p.tileZ - cz), Mth.square(q.tileX - cx) + Mth.square(q.tileZ - cz)));
		return list;
	}

	private void drawRings(GuiGraphicsExtractor graphics) {
		BlockPos spawn = this.session.spawn();
		double cx = toScreenX(spawn.getX());
		double cy = toScreenY(spawn.getZ());
		double lastLabel = -100;
		for (int ring : RINGS) {
			double r = ring / this.blocksPerPixel;
			if (r < 6) {
				continue;
			}
			int points = (int) Mth.clamp(r * 2, 32, 720);
			for (int i = 0; i < points; i += 2) {
				double angle = 2 * Math.PI * i / points;
				int px = (int) Math.round(cx + Math.cos(angle) * r);
				int py = (int) Math.round(cy + Math.sin(angle) * r);
				graphics.fill(px, py, px + 1, py + 1, 0xC0FFFFFF);
			}
			// Label only when it does not overlap the label of the previous (smaller) ring.
			if (r - lastLabel >= 10) {
				graphics.text(this.font, Integer.toString(ring), (int) cx + 2, (int) Math.round(cy - r) - 9, 0xFFFFFFFF, true);
				lastLabel = r;
			}
		}
	}

	/** Dims everything outside the map radius. */
	private void drawRadiusLimit(GuiGraphicsExtractor graphics) {
		BlockPos spawn = this.session.spawn();
		int r = this.session.radius();
		int x0 = (int) Math.round(toScreenX(spawn.getX() - r));
		int x1 = (int) Math.round(toScreenX(spawn.getX() + r));
		int y0 = (int) Math.round(toScreenY(spawn.getZ() - r));
		int y1 = (int) Math.round(toScreenY(spawn.getZ() + r));
		int dim = 0xA0000000;
		graphics.fill(mapLeft(), mapTop(), mapRight(), Math.max(mapTop(), y0), dim);
		graphics.fill(mapLeft(), Math.min(mapBottom(), y1), mapRight(), mapBottom(), dim);
		graphics.fill(mapLeft(), Math.max(mapTop(), y0), Math.max(mapLeft(), x0), Math.min(mapBottom(), y1), dim);
		graphics.fill(Math.min(mapRight(), x1), Math.max(mapTop(), y0), mapRight(), Math.min(mapBottom(), y1), dim);
	}

	/**
	 * Draws the structure icons; returns the one under the cursor. To keep the map readable when zoomed
	 * out, at most one icon is drawn per small screen cell, and rarer structures (larger spacing:
	 * strongholds, mansions, monuments, villages…) win over common ones (ruined portals, mineshafts).
	 */
	private @Nullable MapStructure drawStructures(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<MapStructure> all = this.session.structures();
		if (all.size() != this.byImportance.size()) {
			List<MapStructure> sorted = new ArrayList<>(all);
			sorted.sort((p, q) -> Integer.compare(importance(q), importance(p)));
			this.byImportance = sorted;
		}
		// Bigger cells when zoomed out: fewer, more important icons.
		int cell = (int) Mth.clamp(11 + this.blocksPerPixel / 3, 11, 26);
		it.unimi.dsi.fastutil.longs.LongOpenHashSet used = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		MapStructure hovered = null;
		double best = 7 * 7;
		float iconScale = this.blocksPerPixel < 2 ? 1.5f : 1.25f;
		for (MapStructure s : this.byImportance) {
			// Common structures (mineshafts, then portals…) only once they are not packed together on screen.
			if (s.spacingBlocks() > 0 && s.spacingBlocks() / this.blocksPerPixel < 12) {
				continue;
			}
			BlockPos pos = s.hit().pos();
			double sx = toScreenX(pos.getX() + 0.5);
			double sy = toScreenY(pos.getZ() + 0.5);
			if (sx < mapLeft() - 8 || sx > mapRight() + 8 || sy < mapTop() - 8 || sy > mapBottom() + 8) {
				continue;
			}
			long key = ((long) Math.floorDiv((int) sx, cell) << 32) ^ (Math.floorDiv((int) sy, cell) & 0xFFFFFFFFL);
			if (!used.add(key)) {
				continue;
			}
			drawIcon(graphics, StructureIcons.icon(s.hit().structure()), sx, sy, iconScale);
			double d = Mth.square(sx - mouseX) + Mth.square(sy - mouseY);
			if (d < best && inMap(mouseX, mouseY)) {
				best = d;
				hovered = s;
			}
		}
		return hovered;
	}

	/** Larger = rarer. Stronghold rings / origin placements (spacing 0) count as rarest. */
	private static int importance(MapStructure s) {
		return s.spacingBlocks() <= 0 ? Integer.MAX_VALUE : s.spacingBlocks();
	}

	private void drawSpawn(GuiGraphicsExtractor graphics) {
		BlockPos spawn = this.session.spawn();
		drawIcon(graphics, StructureIcons.SPAWN, toScreenX(spawn.getX() + 0.5), toScreenY(spawn.getZ() + 0.5), 1.75f);
	}

	private static void drawIcon(GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier icon, double x, double y, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate((float) x, (float) y);
		graphics.pose().scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, -4, -4, 0, 0, 8, 8, 8, 8);
		graphics.pose().popMatrix();
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable("seedscout.map.header", this.session.seed(), this.worldType), 6, 3, 0xFFFFFFFF);
		Component status = this.session.scanFinished()
			? Component.translatable("seedscout.map.status_done", this.session.structures().size(), this.session.radius())
			: Component.translatable("seedscout.map.status_scanning", this.session.structures().size(), this.session.scannedRadius());
		graphics.text(this.font, status, 6, 14, 0xFFA0A0A0);
	}

	// ----------------------------------------------------------------- panel

	private List<PanelRow> panelRows() {
		List<PanelRow> rows = new ArrayList<>();
		BlockPos spawn = this.session.spawn();
		String at = "/execute positioned " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ() + " run ";
		rows.add(new PanelRow(Component.translatable("seedscout.map.panel_structures"), spawn, null, true));
		for (SeedAnalysis.StructureEntry e : this.analysis.structures()) {
			if (e.hit() != null && e.hit().distanceTo(spawn) <= this.session.radius()) {
				rows.add(new PanelRow(Names.structure(e.structure()), e.hit().pos(), at + "locate structure " + Names.id(e.structure()), false));
			}
		}
		if (!this.analysis.biomes().isEmpty()) {
			rows.add(new PanelRow(Component.translatable("seedscout.map.panel_biomes"), spawn, null, true));
			for (SeedAnalysis.BiomeEntry e : this.analysis.biomes()) {
				rows.add(new PanelRow(Names.biome(e.biome()), e.pos(), at + "locate biome " + Names.id(e.biome()), false));
			}
		}
		return rows;
	}

	private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = mapRight();
		int top = mapTop();
		int bottom = mapBottom();
		graphics.fill(left, top, this.width, bottom, 0xD0101010);
		graphics.verticalLine(left, top, bottom, 0xFF505050);
		List<PanelRow> rows = panelRows();
		if (!this.analysis.finished() && this.analysis.structures().isEmpty()) {
			graphics.text(this.font, Component.translatable("seedscout.map.panel_loading"), left + 6, top + 6, 0xFFA0A0A0);
			return;
		}
		int visible = (bottom - top - 4) / PANEL_ROW;
		this.panelScroll = Mth.clamp(this.panelScroll, 0, Math.max(0, rows.size() - visible));
		int first = (int) this.panelScroll;
		BlockPos spawn = this.session.spawn();
		graphics.enableScissor(left, top, this.width, bottom);
		for (int i = first; i < rows.size() && i < first + visible + 1; i++) {
			PanelRow row = rows.get(i);
			int y = top + 3 + (i - first) * PANEL_ROW;
			if (row.header()) {
				graphics.text(this.font, row.name(), left + 5, y + 6, 0xFFFFD27F);
				continue;
			}
			boolean hovered = mouseX >= left && mouseY >= y && mouseY < y + PANEL_ROW && mouseY < bottom;
			if (hovered) {
				graphics.fill(left + 2, y - 1, this.width - 2, y + PANEL_ROW - 1, 0x40FFFFFF);
				graphics.setComponentTooltipForNextFrame(this.font, List.of(
					Component.translatable("seedscout.map.panel_click"),
					Component.translatable("seedscout.map.panel_right_click")), mouseX, mouseY);
			}
			graphics.text(this.font, this.font.plainSubstrByWidth(row.name().getString(), panelWidth() - 10), left + 5, y, 0xFFFFFFFF);
			graphics.text(this.font, distanceText(spawn, row.pos()), left + 5, y + 10, 0xFFB0E0FF);
		}
		graphics.disableScissor();
	}

	private static Component distanceText(BlockPos from, BlockPos to) {
		int blocks = (int) Math.round(Math.sqrt(Mth.square((double) to.getX() - from.getX()) + Mth.square((double) to.getZ() - from.getZ())));
		return Component.translatable("seedscout.analysis.distance", blocks, Names.direction(from, to));
	}

	/** Moves the map to a position; zooms in when the view is very far out. */
	public void centerOn(BlockPos pos) {
		this.centerX = pos.getX();
		this.centerZ = pos.getZ();
		this.blocksPerPixel = Math.min(this.blocksPerPixel, 4);
	}

	// ----------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (inMap(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			this.dragging = true;
			return true;
		}
		if (event.x() >= mapRight() && event.y() >= mapTop() && event.y() < mapBottom()) {
			int index = (int) this.panelScroll + (int) ((event.y() - mapTop() - 3) / PANEL_ROW);
			List<PanelRow> rows = panelRows();
			if (index >= 0 && index < rows.size() && !rows.get(index).header()) {
				PanelRow row = rows.get(index);
				if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && row.command() != null) {
					copy(row.command());
				} else {
					centerOn(row.pos());
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			this.centerX -= dx * this.blocksPerPixel;
			this.centerZ -= dy * this.blocksPerPixel;
			clampCenter();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= mapRight()) {
			this.panelScroll -= scrollY * 2;
			return true;
		}
		if (inMap(x, y) && scrollY != 0) {
			// Zoom around the cursor: the block under the mouse stays under the mouse.
			double bx = toBlockX(x);
			double bz = toBlockZ(y);
			double factor = Math.pow(1.25, -scrollY);
			this.blocksPerPixel = Mth.clamp(this.blocksPerPixel * factor, 0.25, maxBlocksPerPixel());
			this.centerX = bx - (x - mapMidX()) * this.blocksPerPixel;
			this.centerZ = bz - (y - mapMidY()) * this.blocksPerPixel;
			clampCenter();
			return true;
		}
		return false;
	}

	private void clampCenter() {
		BlockPos spawn = this.session.spawn();
		int r = this.session.radius();
		this.centerX = Mth.clamp(this.centerX, spawn.getX() - r, spawn.getX() + r);
		this.centerZ = Mth.clamp(this.centerZ, spawn.getZ() - r, spawn.getZ() + r);
	}

	private void copy(String text) {
		this.minecraft.keyboardHandler.setClipboard(text);
		this.message = Component.translatable("seedscout.analysis.copied");
		this.messageUntil = System.currentTimeMillis() + 2000;
	}

	// -------------------------------------------------------------- lifecycle

	/** For the automated client tests. */
	public MapSession sessionForTests() {
		return this.session;
	}

	public SeedAnalysis analysisForTests() {
		return this.analysis;
	}

	/** GUI y of the panel row whose /locate command ends with {@code id} (scrolled into view), or -1. */
	public int panelRowYForTests(String id) {
		List<PanelRow> rows = panelRows();
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).command() != null && rows.get(i).command().endsWith(" " + id)) {
				this.panelScroll = Math.max(0, i - 1);
				return mapTop() + 3 + (i - (int) this.panelScroll) * PANEL_ROW;
			}
		}
		return -1;
	}

	public double blocksPerPixelForTests() {
		return this.blocksPerPixel;
	}

	public BlockPos centerForTests() {
		return new BlockPos((int) Math.floor(this.centerX), 0, (int) Math.floor(this.centerZ));
	}

	public void setViewForTests(double centerX, double centerZ, double blocksPerPixel, boolean shading) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.blocksPerPixel = blocksPerPixel;
		this.shading = shading;
	}

	/** True when every tile needed for the current view is computed (and heights when shading). */
	public boolean viewCompleteForTests() {
		int level = wantedLevel();
		for (MapTile tile : visibleTiles(level, toBlockX(mapLeft()), toBlockX(mapRight()), toBlockZ(mapTop()), toBlockZ(mapBottom()))) {
			if (!tile.hasBiomes() || this.shading && !this.textures.hasHeights(tile)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void onClose() {
		dispose();
		if (this.back == this.createWorldScreen) {
			CreateWorldControls.back(this.createWorldScreen);
		} else {
			this.minecraft.gui.setScreen(this.back);
		}
	}

	/** Stops the background work and frees the textures. */
	private void dispose() {
		this.analysis.cancel();
		this.session.close();
		this.textures.releaseAll(this.session);
	}
}
