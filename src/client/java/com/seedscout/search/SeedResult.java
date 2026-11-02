package com.seedscout.search;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A seed that passed all required / forbidden rules.
 *
 * @param matches what was found near spawn (rule label + distance), in rule order
 * @param terrain terrain numbers (null when the mode does not measure terrain)
 */
public record SeedResult(long seed, double score, BlockPos spawn, List<Match> matches, TerrainStats terrain) {
	public record Match(String label, int distance) {
	}
}
