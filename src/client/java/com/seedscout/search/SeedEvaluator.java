package com.seedscout.search;

import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureHit;
import com.seedscout.worldgen.StructureLocator;
import com.seedscout.worldgen.WorldgenSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Judges one seed against a preset, cheapest checks first so most seeds are rejected quickly:
 * <ol>
 *   <li>spawn (climate based, like the server),</li>
 *   <li>biome rules – a spiral of single biome samples around spawn (no terrain needed),</li>
 *   <li>structure rules – placement first (random numbers only), then selection + validity,</li>
 *   <li>terrain – a grid of noise heights, only for seeds that passed everything else.</li>
 * </ol>
 * Thread-safe as long as each thread uses its own instance.
 */
public final class SeedEvaluator {
	/** Distance between biome samples. Biomes are stored per 4 blocks; 16 keeps small biomes visible. */
	private static final int BIOME_STEP = 16;
	/** Biomes at y=64 under high mountains can be cave biomes: then the column is sampled higher up. */
	private static final int[] BIOME_SAMPLE_Y = {64, 112, 160};
	private static final int TERRAIN_STEP = 16;

	private final WorldgenSettings settings;
	private final List<ResolvedRule> rules;
	private final List<ResolvedRule> structureRules;
	private final List<ResolvedRule> biomeRules;
	private final @Nullable TerrainGoals terrain;
	private final Set<Biome> caveBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
	private final int maxBiomeRadius;

	public SeedEvaluator(WorldgenSettings settings, SearchPreset preset) {
		this.settings = settings;
		this.rules = preset.rules().stream().map(r -> ResolvedRule.resolve(r, settings.registries())).toList();
		// Required and forbidden structure rules first: they can reject the seed.
		this.structureRules = this.rules.stream().filter(r -> r.rule().target() == Rule.Target.STRUCTURE)
			.sorted((a, b) -> Integer.compare(order(a.rule().kind()), order(b.rule().kind()))).toList();
		this.biomeRules = this.rules.stream().filter(r -> r.rule().target() == Rule.Target.BIOME).toList();
		this.maxBiomeRadius = this.biomeRules.stream().mapToInt(r -> r.rule().maxDistance()).max().orElse(0);
		this.terrain = preset.terrain();
		var biomes = settings.registries().lookupOrThrow(Registries.BIOME);
		for (String id : List.of("minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark")) {
			biomes.get(ResourceKey.create(Registries.BIOME, Identifier.parse(id))).ifPresent(h -> this.caveBiomes.add(h.value()));
		}
	}

	private static int order(RuleKind kind) {
		return switch (kind) {
			case REQUIRED -> 0;
			case FORBIDDEN -> 1;
			case PREFERRED -> 2;
		};
	}

	/** @return the result, or null when the seed breaks a required / forbidden rule */
	public @Nullable SeedResult evaluate(long seed) {
		SeedWorld world = new SeedWorld(this.settings, seed);
		BlockPos spawn = world.spawnXZ();
		double score = 0;
		int[] distances = new int[this.rules.size()];
		Arrays.fill(distances, -1);

		// --- biomes
		if (!this.biomeRules.isEmpty()) {
			int[] biomeDistances = scanBiomes(world, spawn);
			if (biomeDistances == null) {
				return null;
			}
			for (int i = 0; i < this.biomeRules.size(); i++) {
				distances[this.rules.indexOf(this.biomeRules.get(i))] = biomeDistances[i];
			}
		}

		// --- structures
		if (!this.structureRules.isEmpty()) {
			StructureLocator locator = new StructureLocator(world);
			for (ResolvedRule rule : this.structureRules) {
				StructureHit hit = rule.structures().isEmpty() ? null : locator.nearest(rule.structures(), spawn, rule.rule().maxDistance());
				boolean found = hit != null;
				if (rule.rule().kind() == RuleKind.REQUIRED && !found || rule.rule().kind() == RuleKind.FORBIDDEN && found) {
					return null;
				}
				if (found && rule.rule().kind() != RuleKind.FORBIDDEN) {
					distances[this.rules.indexOf(rule)] = (int) Math.round(hit.distanceTo(spawn));
				}
			}
		}

		List<SeedResult.Match> matches = new ArrayList<>();
		for (int i = 0; i < this.rules.size(); i++) {
			Rule rule = this.rules.get(i).rule();
			if (distances[i] >= 0 && rule.kind() != RuleKind.FORBIDDEN) {
				score += rule.score(distances[i]);
				matches.add(new SeedResult.Match(rule.label(), distances[i]));
			}
		}

		// --- terrain (most expensive, last)
		TerrainStats stats = null;
		if (this.terrain != null) {
			stats = measureTerrain(world, spawn, this.terrain.radius());
			score += terrainScore(stats, this.terrain);
		}
		BlockPos spawnPos = new BlockPos(spawn.getX(), world.surfaceHeight(spawn.getX(), spawn.getZ()), spawn.getZ());
		return new SeedResult(seed, score, spawnPos, List.copyOf(matches), stats);
	}

	/**
	 * Walks a square spiral around spawn. Each biome rule takes the distance of the first matching
	 * sample. Returns null as soon as a forbidden biome is seen or a required one cannot be found any more.
	 */
	private int @Nullable [] scanBiomes(SeedWorld world, BlockPos spawn) {
		int count = this.biomeRules.size();
		int[] found = new int[count];
		Arrays.fill(found, -1);
		int remaining = count;
		int rings = this.maxBiomeRadius / BIOME_STEP + 1;
		int currentRing = 0;
		for (BlockPos.MutableBlockPos p : BlockPos.spiralAround(BlockPos.ZERO, rings, Direction.EAST, Direction.SOUTH)) {
			int ring = Math.max(Math.abs(p.getX()), Math.abs(p.getZ()));
			if (ring != currentRing) {
				// A full ring is done: everything closer than (ring - 1) * step has been seen.
				currentRing = ring;
				int covered = (ring - 1) * BIOME_STEP;
				for (int i = 0; i < count; i++) {
					Rule rule = this.biomeRules.get(i).rule();
					if (found[i] < 0 && rule.kind() == RuleKind.REQUIRED && covered > rule.maxDistance()) {
						return null;
					}
				}
				if (remaining == 0 || covered > this.maxBiomeRadius) {
					break;
				}
			}
			int dx = p.getX() * BIOME_STEP;
			int dz = p.getZ() * BIOME_STEP;
			int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
			Holder<Biome> biome = null;
			for (int i = 0; i < count; i++) {
				ResolvedRule rule = this.biomeRules.get(i);
				if (found[i] >= 0 || distance > rule.rule().maxDistance()) {
					continue;
				}
				if (biome == null) {
					biome = sampleBiome(world, spawn.getX() + dx, spawn.getZ() + dz);
				}
				if (rule.matchesBiome(biome)) {
					if (rule.rule().kind() == RuleKind.FORBIDDEN) {
						return null;
					}
					found[i] = distance;
					remaining--;
				}
			}
		}
		for (int i = 0; i < count; i++) {
			if (found[i] < 0 && this.biomeRules.get(i).rule().kind() == RuleKind.REQUIRED) {
				return null;
			}
		}
		return found;
	}

	private Holder<Biome> sampleBiome(SeedWorld world, int x, int z) {
		Holder<Biome> biome = null;
		for (int y : BIOME_SAMPLE_Y) {
			biome = world.biome(x, y, z);
			if (!this.caveBiomes.contains(biome.value())) {
				return biome;
			}
		}
		return biome;
	}

	/** Grid of noise heights (no chunks) in a circle around spawn. */
	public static TerrainStats measureTerrain(SeedWorld world, BlockPos spawn, int radius) {
		int seaLevel = world.settings().generator().getSeaLevel();
		int n = radius / TERRAIN_STEP;
		int size = 2 * n + 1;
		int[][] heights = new int[size][size];
		List<Integer> land = new ArrayList<>();
		int water = 0;
		int total = 0;
		int max = Integer.MIN_VALUE;
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int dx = (i - n) * TERRAIN_STEP;
				int dz = (j - n) * TERRAIN_STEP;
				if (dx * dx + dz * dz > radius * radius) {
					heights[i][j] = Integer.MIN_VALUE;
					continue;
				}
				int h = world.surfaceHeight(spawn.getX() + dx, spawn.getZ() + dz);
				heights[i][j] = h;
				total++;
				if (h <= seaLevel) {
					water++;
				} else {
					land.add(h);
				}
				max = Math.max(max, h);
			}
		}
		int spawnHeight = heights[n][n];
		int relief = 0;
		if (land.size() >= 5) {
			Collections.sort(land);
			relief = land.get(land.size() * 9 / 10) - land.get(land.size() / 10);
		}
		int flatCount = 0;
		int landCount = 0;
		for (int i = 1; i < size - 1; i++) {
			for (int j = 1; j < size - 1; j++) {
				int h = heights[i][j];
				if (h == Integer.MIN_VALUE || h <= seaLevel) {
					continue;
				}
				landCount++;
				boolean flat = true;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int o = heights[i + d[0]][j + d[1]];
					if (o != Integer.MIN_VALUE && Math.abs(o - h) > 2) {
						flat = false;
						break;
					}
				}
				if (flat) {
					flatCount++;
				}
			}
		}
		return new TerrainStats(
			total == 0 ? 0 : (double) water / total,
			relief,
			Math.max(0, max - spawnHeight),
			landCount == 0 ? 0 : (double) flatCount / landCount
		);
	}

	/** 0..1 per goal, multiplied by its weight. */
	public static double terrainScore(TerrainStats t, TerrainGoals goals) {
		double water = t.water() < 0.05 ? t.water() / 0.05 : t.water() <= 0.35 ? 1 : Math.max(0, (0.7 - t.water()) / 0.35);
		double relief = t.relief() < 20 ? t.relief() / 20.0 : t.relief() <= 80 ? 1 : Math.max(0, (140 - t.relief()) / 60.0);
		double mountain = Math.min(1, t.mountain() / 40.0);
		double flat = Math.min(1, t.flat() / 0.4);
		return goals.water() * water + goals.relief() * relief + goals.mountains() * mountain + goals.flat() * flat;
	}
}
