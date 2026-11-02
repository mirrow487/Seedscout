package com.seedscout.worldgen;

import com.seedscout.SeedscoutClient;
import com.seedscout.util.Workers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Analyses one seed on a background thread: spawn, the nearest structure of every type
 * (same result as {@code /locate structure} from spawn) and the nearest surface biomes.
 * The screen polls the volatile fields; nothing blocks the render thread.
 */
public final class SeedAnalysis {
	/** {@code /locate structure} searches 100 spread regions. */
	public static final int LOCATE_RADIUS = 100;
	public static final int BIOME_RADIUS = 2048;
	public static final int BIOME_STEP = 32;

	public record StructureEntry(Holder<Structure> structure, @Nullable StructureHit hit) {
	}

	public record BiomeEntry(Holder<Biome> biome, BlockPos pos) {
	}

	private final long seed;
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private volatile @Nullable BlockPos spawn;
	private volatile @Nullable Holder<Biome> spawnBiome;
	private volatile List<StructureEntry> structures = List.of();
	private volatile List<BiomeEntry> biomes = List.of();
	private volatile int structuresDone;
	private volatile int structuresTotal;
	private volatile boolean finished;
	private volatile long millis;
	private volatile @Nullable Throwable error;

	private SeedAnalysis(long seed) {
		this.seed = seed;
	}

	/** Starts the analysis. {@code settings} is closed by the worker when it is done. */
	public static SeedAnalysis start(WorldgenSettings settings, long seed) {
		SeedAnalysis analysis = new SeedAnalysis(seed);
		Workers.analysis().execute(() -> analysis.run(settings));
		return analysis;
	}

	private void run(WorldgenSettings settings) {
		long start = System.currentTimeMillis();
		try (settings) {
			SeedWorld world = new SeedWorld(settings, this.seed);
			StructureLocator locator = new StructureLocator(world);
			BlockPos spawnPos = world.spawnPos();
			this.spawn = spawnPos;
			this.spawnBiome = world.biome(spawnPos.getX(), spawnPos.getY() - 1, spawnPos.getZ());

			// Every structure that this world's structure sets can place (duplicates removed, set order kept).
			Set<Holder<Structure>> all = new LinkedHashSet<>();
			for (Holder<StructureSet> set : locator.structureSets()) {
				set.value().structures().forEach(e -> all.add(e.structure()));
			}
			all.removeIf(s -> !locator.isPossible(s));
			this.structuresTotal = all.size();
			List<StructureEntry> found = new ArrayList<>();
			for (Holder<Structure> structure : all) {
				if (this.cancelled.get()) {
					return;
				}
				found.add(new StructureEntry(structure, locator.locate(structure, spawnPos, LOCATE_RADIUS)));
				this.structures = sorted(found, spawnPos);
				this.structuresDone = found.size();
			}

			Map<Holder<Biome>, BlockPos> nearest = BiomeLocator.nearestSurfaceBiomes(world, spawnPos, BIOME_RADIUS, BIOME_STEP, this.cancelled::get);
			List<BiomeEntry> biomeList = new ArrayList<>();
			nearest.forEach((biome, pos) -> biomeList.add(new BiomeEntry(biome, pos)));
			this.biomes = List.copyOf(biomeList);
		} catch (Throwable t) {
			SeedscoutClient.LOGGER.error("Seed analysis failed for {}", this.seed, t);
			this.error = t;
		} finally {
			this.millis = System.currentTimeMillis() - start;
			this.finished = true;
		}
	}

	/** Found ones by distance, then the ones not found within the search radius. */
	private static List<StructureEntry> sorted(List<StructureEntry> entries, BlockPos from) {
		List<StructureEntry> copy = new ArrayList<>(entries);
		copy.sort(Comparator.comparingDouble(e -> e.hit() == null ? Double.MAX_VALUE : e.hit().distanceTo(from)));
		return List.copyOf(copy);
	}

	public void cancel() {
		this.cancelled.set(true);
	}

	public long seed() {
		return this.seed;
	}

	public @Nullable BlockPos spawn() {
		return this.spawn;
	}

	public @Nullable Holder<Biome> spawnBiome() {
		return this.spawnBiome;
	}

	public List<StructureEntry> structures() {
		return this.structures;
	}

	public List<BiomeEntry> biomes() {
		return this.biomes;
	}

	public int structuresDone() {
		return this.structuresDone;
	}

	public int structuresTotal() {
		return this.structuresTotal;
	}

	public boolean finished() {
		return this.finished;
	}

	public long millis() {
		return this.millis;
	}

	public @Nullable Throwable error() {
		return this.error;
	}
}
