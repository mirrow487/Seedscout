package com.seedscout.map;

import com.seedscout.SeedscoutClient;
import com.seedscout.util.Workers;
import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureLocator;
import com.seedscout.worldgen.WorldgenSettings;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * All background work of one map: biome / height tiles on a small thread pool (each thread has its own
 * {@link SeedWorld}, the samplers are not thread-safe) and a structure scan that grows outwards from
 * spawn, so near structures appear first. Nothing generates chunks.
 */
public final class MapSession implements AutoCloseable {
	/** Rings of the structure scan (blocks from spawn); the last one is the map radius. */
	private static final int[] SCAN_STEPS = {1500, 4000, 7000};
	/** Sets with very small spacing (mineshafts: every chunk is a candidate) are only scanned this close. */
	private static final int MAX_CANDIDATES_PER_SET = 6000;

	private final WorldgenSettings settings;
	private final long seed;
	private final int radius;
	private final ExecutorService tilePool;
	private final ExecutorService structurePool;
	private final ThreadLocal<SeedWorld> worlds;
	private final Long2ObjectOpenHashMap<MapTile> tiles = new Long2ObjectOpenHashMap<>();
	private final AtomicInteger inFlight = new AtomicInteger();
	private final int maxInFlight;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final List<MapStructure> structures = new CopyOnWriteArrayList<>();
	private final BlockPos spawn;
	private volatile int scannedRadius;
	private volatile boolean scanFinished;

	public MapSession(WorldgenSettings settings, long seed, int radius, int threads) {
		this.settings = settings;
		this.seed = seed;
		this.radius = radius;
		this.tilePool = Executors.newFixedThreadPool(threads, Workers.factory("Seedscout Map"));
		this.structurePool = Executors.newSingleThreadExecutor(Workers.factory("Seedscout Map Structures"));
		this.worlds = ThreadLocal.withInitial(() -> new SeedWorld(settings, seed));
		this.maxInFlight = threads * 2;
		SeedWorld world = new SeedWorld(settings, seed);
		this.spawn = world.spawnPos();
		this.structurePool.execute(this::scanStructures);
	}

	public long seed() {
		return this.seed;
	}

	public int radius() {
		return this.radius;
	}

	public BlockPos spawn() {
		return this.spawn;
	}

	public List<MapStructure> structures() {
		return this.structures;
	}

	public int scannedRadius() {
		return this.scannedRadius;
	}

	public boolean scanFinished() {
		return this.scanFinished;
	}

	// ------------------------------------------------------------------ tiles

	/** The tile (created empty if needed). Render thread only. */
	public MapTile tile(int level, int tileX, int tileZ) {
		return this.tiles.computeIfAbsent(MapTile.key(level, tileX, tileZ), k -> new MapTile(level, tileX, tileZ));
	}

	/** Existing tile or null. Render thread only. */
	public MapTile existingTile(int level, int tileX, int tileZ) {
		return this.tiles.get(MapTile.key(level, tileX, tileZ));
	}

	public Iterable<MapTile> allTiles() {
		return this.tiles.values();
	}

	/** Drops tiles matching {@code removable} (oldest drawn first) until at most {@code keep} remain. Render thread only. */
	public void removeTiles(java.util.function.Predicate<MapTile> removable, int keep) {
		List<MapTile> candidates = new ArrayList<>();
		for (MapTile tile : this.tiles.values()) {
			if (removable.test(tile)) {
				candidates.add(tile);
			}
		}
		candidates.sort(java.util.Comparator.comparingLong(t -> t.lastDrawn));
		int toRemove = this.tiles.size() - keep;
		for (int i = 0; i < candidates.size() && i < toRemove; i++) {
			MapTile tile = candidates.get(i);
			this.tiles.remove(MapTile.key(tile.level, tile.tileX, tile.tileZ));
		}
	}

	/**
	 * Asks for a tile's biomes (and heights when {@code shading}). Returns false when the worker queue is
	 * full; the caller asks again next frame, so the most wanted tiles (the caller's order) go first.
	 */
	public boolean request(MapTile tile, boolean shading) {
		boolean needBiomes = !tile.biomeRequested;
		boolean needHeights = shading && !tile.heightRequested;
		if (!needBiomes && !needHeights) {
			return true;
		}
		if (this.inFlight.get() >= this.maxInFlight) {
			return false;
		}
		if (needBiomes) {
			tile.biomeRequested = true;
		}
		if (needHeights) {
			tile.heightRequested = true;
		}
		this.inFlight.incrementAndGet();
		this.tilePool.execute(() -> {
			try {
				if (!this.closed.get()) {
					if (needBiomes) {
						computeBiomes(tile);
					}
					if (needHeights) {
						computeHeights(tile);
					}
				}
			} catch (Throwable t) {
				SeedscoutClient.LOGGER.error("Map tile failed", t);
			} finally {
				this.inFlight.decrementAndGet();
			}
		});
		return true;
	}

	public int busyWorkers() {
		return this.inFlight.get();
	}

	private void computeBiomes(MapTile tile) {
		SeedWorld world = this.worlds.get();
		int step = 1 << tile.level;
		long x0 = tile.tileX * MapTile.blocks(tile.level);
		long z0 = tile.tileZ * MapTile.blocks(tile.level);
		int[] colors = new int[MapTile.SIZE * MapTile.SIZE];
		Holder<Biome> last = null;
		int lastColor = 0;
		for (int j = 0; j < MapTile.SIZE; j++) {
			for (int i = 0; i < MapTile.SIZE; i++) {
				// Pixel centre; biomes are stored per 4 blocks, so the finest levels reuse samples.
				int x = (int) (x0 + (long) i * step + step / 2);
				int z = (int) (z0 + (long) j * step + step / 2);
				Holder<Biome> biome = world.mapBiome(x, z);
				if (biome != last) {
					last = biome;
					lastColor = BiomeColors.argb(biome);
				}
				colors[j * MapTile.SIZE + i] = lastColor;
			}
			if (this.closed.get()) {
				return;
			}
		}
		tile.biomeColors = colors;
	}

	private void computeHeights(MapTile tile) {
		SeedWorld world = this.worlds.get();
		int step = 1 << tile.level;
		long x0 = tile.tileX * MapTile.blocks(tile.level);
		long z0 = tile.tileZ * MapTile.blocks(tile.level);
		int[] heights = new int[MapTile.SIZE * MapTile.SIZE];
		for (int j = 0; j < MapTile.SIZE; j++) {
			for (int i = 0; i < MapTile.SIZE; i++) {
				heights[j * MapTile.SIZE + i] = world.surfaceHeight((int) (x0 + (long) i * step + step / 2), (int) (z0 + (long) j * step + step / 2));
			}
			if (this.closed.get()) {
				return;
			}
		}
		tile.heights = heights;
	}

	// ------------------------------------------------------------- structures

	/** Scans structure sets in growing rings around spawn and publishes hits as they are found. */
	private void scanStructures() {
		try {
			SeedWorld world = new SeedWorld(this.settings, this.seed);
			StructureLocator locator = new StructureLocator(world);
			BlockPos center = world.spawnXZ();
			List<Integer> steps = new ArrayList<>();
			for (int step : SCAN_STEPS) {
				if (step < this.radius) {
					steps.add(step);
				}
			}
			steps.add(this.radius);
			int previous = -1;
			for (int step : steps) {
				for (Holder<StructureSet> setHolder : locator.structureSets()) {
					StructureSet set = setHolder.value();
					if (set.structures().stream().noneMatch(e -> locator.isPossible(e.structure()))) {
						continue;
					}
					StructurePlacement placement = set.placement();
					int spacing = placement instanceof RandomSpreadStructurePlacement spread ? spread.spacing() * 16 : 0;
					int setRadius = Math.min(step, maxRadiusFor(spacing));
					int setPrevious = Math.min(previous, maxRadiusFor(spacing));
					if (setRadius <= setPrevious) {
						continue;
					}
					long r2 = (long) setRadius * setRadius;
					long p2 = setPrevious < 0 ? -1 : (long) setPrevious * setPrevious;
					for (ChunkPos chunk : locator.candidateChunks(placement, center, setRadius)) {
						if (this.closed.get()) {
							return;
						}
						BlockPos pos = placement.getLocatePos(chunk);
						long dx = pos.getX() - center.getX();
						long dz = pos.getZ() - center.getZ();
						long d2 = dx * dx + dz * dz;
						if (d2 > r2 || d2 <= p2) {
							continue;
						}
						Holder<Structure> structure = locator.structureAt(set, chunk);
						if (structure != null) {
							this.structures.add(new MapStructure(new com.seedscout.worldgen.StructureHit(structure, chunk, pos), spacing));
						}
					}
				}
				previous = step;
				this.scannedRadius = step;
			}
		} catch (Throwable t) {
			SeedscoutClient.LOGGER.error("Map structure scan failed", t);
		} finally {
			this.scanFinished = true;
		}
	}

	/** Radius that keeps the number of candidate regions of a set at most {@link #MAX_CANDIDATES_PER_SET}. */
	private int maxRadiusFor(int spacingBlocks) {
		if (spacingBlocks <= 0) {
			return this.radius;
		}
		return (int) Math.min(this.radius, spacingBlocks * Math.sqrt(MAX_CANDIDATES_PER_SET) / 2);
	}

	@Override
	public void close() {
		if (this.closed.getAndSet(true)) {
			return;
		}
		this.tilePool.shutdownNow();
		this.structurePool.shutdownNow();
		// Release the data packs once the workers are gone (never blocks the render thread for long).
		Workers.analysis().execute(() -> {
			try {
				this.tilePool.awaitTermination(30, TimeUnit.SECONDS);
				this.structurePool.awaitTermination(30, TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			this.settings.close();
		});
	}
}
