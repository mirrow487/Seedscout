package com.seedscout.search;

import java.util.List;

/**
 * "[structure/biome] at most {@code maxDistance} blocks from spawn".
 *
 * @param target      {@link Target#STRUCTURE} or {@link Target#BIOME}
 * @param ids         registry ids ({@code minecraft:village_plains}), tags ({@code #minecraft:village})
 *                    or id prefixes ending in {@code *} ({@code minecraft:abandoned_camp_*}); any of them matches
 * @param maxDistance horizontal distance from the world spawn, in blocks
 * @param kind        required / preferred / forbidden
 * @param weight      score for a match at distance 0 (linearly less with distance, half at maxDistance)
 * @param label       translation key of the short name shown in results
 */
public record Rule(Target target, List<String> ids, int maxDistance, RuleKind kind, double weight, String label) {
	public enum Target {
		STRUCTURE,
		BIOME
	}

	public static Rule structure(String label, int maxDistance, RuleKind kind, double weight, String... ids) {
		return new Rule(Target.STRUCTURE, List.of(ids), maxDistance, kind, weight, label);
	}

	public static Rule biome(String label, int maxDistance, RuleKind kind, double weight, String... ids) {
		return new Rule(Target.BIOME, List.of(ids), maxDistance, kind, weight, label);
	}

	/** Score for a match at {@code distance} blocks: weight at 0, weight/2 at maxDistance. */
	public double score(double distance) {
		return this.weight * (1.0 - 0.5 * Math.min(1.0, distance / Math.max(1, this.maxDistance)));
	}
}
