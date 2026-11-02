package com.seedscout.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Biome searches for one seed, using the game's biome source and climate sampler. */
public final class BiomeLocator {
	private BiomeLocator() {
	}

	/**
	 * Nearest biome exactly like {@code /locate biome} (same spiral, same sample heights as
	 * {@code BiomeSource.findClosestBiome3d}; {@code /locate} uses radius 6400, steps 32 and 64).
	 */
	public static @Nullable Pair<BlockPos, Holder<Biome>> locate(
		SeedWorld world, BlockPos origin, int searchRadius, int horizontalStep, int verticalStep, Predicate<Holder<Biome>> allowed
	) {
		Set<Holder<Biome>> candidates = world.settings().generator().getBiomeSource().possibleBiomes().stream()
			.filter(allowed)
			.collect(Collectors.toUnmodifiableSet());
		if (candidates.isEmpty()) {
			return null;
		}
		LevelHeightAccessor height = world.settings().heightAccessor();
		int sampleRadius = Math.floorDiv(searchRadius, horizontalStep);
		int[] sampleYs = Mth.outFromOrigin(origin.getY(), height.getMinY() + 1, height.getMaxY() + 1, verticalStep).toArray();
		for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(BlockPos.ZERO, sampleRadius, Direction.EAST, Direction.SOUTH)) {
			int x = origin.getX() + column.getX() * horizontalStep;
			int z = origin.getZ() + column.getZ() * horizontalStep;
			for (int y : sampleYs) {
				Holder<Biome> biome = world.biome(x, y, z);
				if (candidates.contains(biome)) {
					return Pair.of(new BlockPos(x, y, z), biome);
				}
			}
		}
		return null;
	}

	/**
	 * Nearest SURFACE position of every biome around {@code center}: one outward spiral with the given
	 * step, sampling the biome at the terrain height. Returns biomes in the order they were found
	 * (= nearest first).
	 */
	public static Map<Holder<Biome>, BlockPos> nearestSurfaceBiomes(SeedWorld world, BlockPos center, int radius, int step, BooleanSupplier cancelled) {
		Map<Holder<Biome>, BlockPos> found = new LinkedHashMap<>();
		int sampleRadius = Math.floorDiv(radius, step);
		long radiusSqr = (long) radius * radius;
		for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(BlockPos.ZERO, sampleRadius, Direction.EAST, Direction.SOUTH)) {
			if (cancelled.getAsBoolean()) {
				break;
			}
			long dx = (long) column.getX() * step;
			long dz = (long) column.getZ() * step;
			if (dx * dx + dz * dz > radiusSqr) {
				continue;
			}
			int x = center.getX() + (int) dx;
			int z = center.getZ() + (int) dz;
			int y = world.surfaceHeight(x, z);
			Holder<Biome> biome = world.biome(x, y - 1, z);
			found.putIfAbsent(biome, new BlockPos(x, y, z));
		}
		return found;
	}
}
