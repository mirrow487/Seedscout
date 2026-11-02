package com.seedscout.search;

import com.seedscout.config.SearchMode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A complete set of search rules. Built-in defaults per mode; in stage 4 they can be edited in the
 * "Specify" screen and saved as JSON presets.
 */
public record SearchPreset(List<Rule> rules, @Nullable TerrainGoals terrain) {
	public static SearchPreset defaults(SearchMode mode) {
		return switch (mode) {
			case SPEEDRUN -> new SearchPreset(List.of(
				// Spawn on land.
				Rule.biome("seedscout.rule.ocean", 32, RuleKind.FORBIDDEN, 0, "#minecraft:is_ocean"),
				// Surface loot right next to spawn.
				Rule.structure("seedscout.rule.village", 400, RuleKind.REQUIRED, 3, "#minecraft:village"),
				Rule.structure("seedscout.rule.ruined_portal", 400, RuleKind.REQUIRED, 3, "#minecraft:ruined_portal"),
				Rule.structure("seedscout.rule.shipwreck", 350, RuleKind.PREFERRED, 2, "#minecraft:shipwreck"),
				Rule.structure("seedscout.rule.buried_treasure", 350, RuleKind.PREFERRED, 2, "minecraft:buried_treasure"),
				Rule.structure("seedscout.rule.temple", 450, RuleKind.PREFERRED, 2, "minecraft:desert_pyramid", "minecraft:jungle_pyramid"),
				Rule.structure("seedscout.rule.abandoned_camp", 350, RuleKind.PREFERRED, 1, "#minecraft:abandoned_camp"),
				Rule.structure("seedscout.rule.trail_ruins", 350, RuleKind.PREFERRED, 0.5, "minecraft:trail_ruins")
			), null);
			case SURVIVAL -> new SearchPreset(List.of(
				Rule.biome("seedscout.rule.ocean", 64, RuleKind.FORBIDDEN, 0, "#minecraft:is_ocean"),
				Rule.biome("seedscout.rule.forest", 250, RuleKind.REQUIRED, 2, "#minecraft:is_forest"),
				Rule.biome("seedscout.rule.river", 300, RuleKind.PREFERRED, 2, "#minecraft:is_river"),
				Rule.biome("seedscout.rule.mountain", 500, RuleKind.PREFERRED, 2, "#minecraft:is_mountain"),
				Rule.biome("seedscout.rule.cherry_grove", 600, RuleKind.PREFERRED, 1, "minecraft:cherry_grove"),
				Rule.biome("seedscout.rule.meadow", 400, RuleKind.PREFERRED, 1, "minecraft:meadow"),
				Rule.structure("seedscout.rule.village", 500, RuleKind.PREFERRED, 1.5, "#minecraft:village")
			), TerrainGoals.DEFAULT);
			case RANDOM -> new SearchPreset(List.of(), null);
		};
	}
}
